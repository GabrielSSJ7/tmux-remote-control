package com.remotecontrol.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun TerminalRenderer(
    emulator: TerminalEmulator,
    version: Long,
    fontSize: Float,
    modifier: Modifier = Modifier,
    onSizeChanged: ((cols: Int, rows: Int) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
) {
    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textScaleX = 0.85f
        }
    }
    var currentFontSize by remember { mutableFloatStateOf(fontSize) }
    var scrollOffset by remember { mutableIntStateOf(0) }
    var charHeightPx by remember { mutableFloatStateOf(1f) }

    val gestureModifier = modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            var scrollAcc = 0f
            var prevSpan = -1f
            var totalDrag = 0f

            while (true) {
                val event = awaitPointerEvent()
                val pointers = event.changes

                if (pointers.all { it.changedToUp() }) {
                    if (abs(totalDrag) < charHeightPx * 0.5f && prevSpan < 0f) {
                        onTap?.invoke()
                    }
                    pointers.forEach { it.consume() }
                    break
                }

                if (pointers.size >= 2) {
                    val dx = pointers[0].position.x - pointers[1].position.x
                    val dy = pointers[0].position.y - pointers[1].position.y
                    val span = hypot(dx, dy)
                    if (prevSpan > 0f && span > 0f) {
                        val scale = span / prevSpan
                        if (abs(scale - 1f) > 0.01f) {
                            currentFontSize = (currentFontSize * scale).coerceIn(4f, 32f)
                        }
                    }
                    prevSpan = span
                    pointers.forEach { it.consume() }
                } else if (pointers.size == 1 && pointers[0].positionChanged()) {
                    val dy = pointers[0].position.y - pointers[0].previousPosition.y
                    totalDrag += abs(dy)
                    scrollAcc += dy
                    val lines = (scrollAcc / charHeightPx).toInt()
                    if (lines != 0) {
                        scrollAcc -= lines * charHeightPx
                        val maxScroll = emulator.scrollbackSize
                        scrollOffset = (scrollOffset - lines).coerceIn(0, maxScroll)
                    }
                    pointers[0].consume()
                    prevSpan = -1f
                }
            }
        }
    }

    Canvas(modifier = gestureModifier) {
        paint.textSize = currentFontSize * density
        val charWidth = paint.measureText("M")
        val charHeight = paint.fontSpacing
        charHeightPx = charHeight
        val cols = (size.width / charWidth).toInt().coerceAtLeast(1)
        val rows = (size.height / charHeight).toInt().coerceAtLeast(1)
        if (cols != emulator.cols || rows != emulator.rows) {
            onSizeChanged?.invoke(cols, rows)
        }
        drawTerminal(emulator, paint, charWidth, charHeight, scrollOffset)
    }
}

private fun DrawScope.drawTerminal(emulator: TerminalEmulator, paint: Paint, charWidth: Float, charHeight: Float, scrollOffset: Int) {
    val canvas = drawContext.canvas.nativeCanvas
    val totalScrollback = emulator.scrollbackSize

    for (visualRow in 0 until emulator.rows) {
        val absoluteRow = visualRow - scrollOffset
        val inScrollback = absoluteRow < 0
        val scrollbackIdx = totalScrollback + absoluteRow

        for (col in 0 until emulator.cols) {
            val cell = if (inScrollback && scrollbackIdx in 0 until totalScrollback) {
                emulator.getScrollbackCell(scrollbackIdx, col)
            } else if (!inScrollback && absoluteRow in 0 until emulator.rows) {
                emulator.getCell(absoluteRow, col)
            } else {
                TerminalCell()
            }

            val x = col * charWidth
            val y = visualRow * charHeight
            var fg = cell.fg
            var bg = cell.bg
            if (cell.inverse) { val tmp = fg; fg = bg; bg = tmp }
            if (cell.dim) { fg = dimColor(fg) }
            if (bg != TerminalCell.DEFAULT_BG) {
                paint.color = bg; paint.style = Paint.Style.FILL
                canvas.drawRect(x, y, x + charWidth, y + charHeight, paint)
            }
            if (cell.char != ' ') {
                paint.color = fg; paint.style = Paint.Style.FILL
                paint.isFakeBoldText = cell.bold; paint.isUnderlineText = cell.underline
                canvas.drawText(cell.char.toString(), x, y + charHeight - paint.descent(), paint)
                paint.isFakeBoldText = false; paint.isUnderlineText = false
            }
        }
    }

    if (scrollOffset == 0) {
        val cursorX = emulator.cursorCol * charWidth
        val cursorY = emulator.cursorRow * charHeight
        drawRect(color = Color(0xAAFFFFFF), topLeft = Offset(cursorX, cursorY), size = Size(charWidth, charHeight))
    }

    if (scrollOffset > 0) {
        paint.color = 0x80FFFFFF.toInt(); paint.style = Paint.Style.FILL; paint.textSize = 28f
        canvas.drawText("↑ $scrollOffset lines", 10f, 30f, paint)
    }
}

private fun dimColor(color: Int): Int {
    val r = ((color shr 16) and 0xFF) / 2
    val g = ((color shr 8) and 0xFF) / 2
    val b = (color and 0xFF) / 2
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
