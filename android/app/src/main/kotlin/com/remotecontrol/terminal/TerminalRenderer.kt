package com.remotecontrol.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun TerminalRenderer(
    emulator: TerminalEmulator,
    version: Long,
    fontSize: Float,
    modifier: Modifier = Modifier,
    onSizeChanged: ((cols: Int, rows: Int) -> Unit)? = null,
) {
    val paint = remember {
        Paint().apply { typeface = Typeface.MONOSPACE; isAntiAlias = true }
    }
    var currentFontSize by remember { mutableFloatStateOf(fontSize) }

    val gestureModifier = modifier.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            currentFontSize = (currentFontSize * zoom).coerceIn(8f, 32f)
        }
    }

    Canvas(modifier = gestureModifier) {
        paint.textSize = currentFontSize * density
        val charWidth = paint.measureText("M")
        val charHeight = paint.fontSpacing
        val cols = (size.width / charWidth).toInt().coerceAtLeast(1)
        val rows = (size.height / charHeight).toInt().coerceAtLeast(1)
        if (cols != emulator.cols || rows != emulator.rows) {
            onSizeChanged?.invoke(cols, rows)
        }
        drawTerminal(emulator, paint, charWidth, charHeight)
    }
}

private fun DrawScope.drawTerminal(emulator: TerminalEmulator, paint: Paint, charWidth: Float, charHeight: Float) {
    val canvas = drawContext.canvas.nativeCanvas
    for (row in 0 until emulator.rows) {
        for (col in 0 until emulator.cols) {
            val cell = emulator.getCell(row, col)
            val x = col * charWidth
            val y = row * charHeight
            if (cell.bg != TerminalCell.DEFAULT_BG) {
                paint.color = cell.bg; paint.style = Paint.Style.FILL
                canvas.drawRect(x, y, x + charWidth, y + charHeight, paint)
            }
            if (cell.char != ' ') {
                paint.color = cell.fg; paint.style = Paint.Style.FILL
                paint.isFakeBoldText = cell.bold; paint.isUnderlineText = cell.underline
                canvas.drawText(cell.char.toString(), x, y + charHeight - paint.descent(), paint)
                paint.isFakeBoldText = false; paint.isUnderlineText = false
            }
        }
    }
    val cursorX = emulator.cursorCol * charWidth
    val cursorY = emulator.cursorRow * charHeight
    drawRect(color = Color(0xAAFFFFFF), topLeft = Offset(cursorX, cursorY), size = Size(charWidth, charHeight))
}
