package com.remotecontrol.terminal

data class TerminalCell(
    var char: Char = ' ',
    var fg: Int = DEFAULT_FG,
    var bg: Int = DEFAULT_BG,
    var bold: Boolean = false,
    var underline: Boolean = false,
    var inverse: Boolean = false,
    var dim: Boolean = false,
    var italic: Boolean = false,
    var strikethrough: Boolean = false,
) {
    companion object {
        const val DEFAULT_FG = 0xFFE0E0E0.toInt()
        const val DEFAULT_BG = 0xFF1E1E1E.toInt()
    }
}

class TerminalEmulator(
    var cols: Int = 80,
    var rows: Int = 24,
    private val maxScrollback: Int = 5000,
) {
    private var mainBuffer: Array<Array<TerminalCell>> = makeBuffer(rows, cols)
    private var altBuffer: Array<Array<TerminalCell>> = makeBuffer(rows, cols)
    private var buffer: Array<Array<TerminalCell>> = mainBuffer
    private var useAltBuffer = false
    val scrollback = mutableListOf<Array<TerminalCell>>()
    val scrollbackSize get() = scrollback.size

    var cursorRow = 0; private set
    var cursorCol = 0; private set
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedMainCursorRow = 0
    private var savedMainCursorCol = 0

    private var currentFg = TerminalCell.DEFAULT_FG
    private var currentBg = TerminalCell.DEFAULT_BG
    private var currentBold = false
    private var currentUnderline = false
    private var currentInverse = false
    private var currentDim = false
    private var currentItalic = false
    private var currentStrikethrough = false

    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private var state = ParseState.NORMAL
    private val csiParams = StringBuilder()
    private val oscData = StringBuilder()
    private val utf8Buf = ByteArray(4)
    private var utf8Len = 0
    private var utf8Expected = 0

    var version = 0L; private set

    private enum class ParseState { NORMAL, ESCAPE, CSI, OSC, OSC_ESC, UTF8 }

    fun getCell(row: Int, col: Int): TerminalCell {
        if (row in 0 until rows && col in 0 until cols) return buffer[row][col]
        return TerminalCell()
    }

    fun getScrollbackCell(line: Int, col: Int): TerminalCell {
        if (line in scrollback.indices && col in 0 until scrollback[line].size) return scrollback[line][col]
        return TerminalCell()
    }

    fun process(data: ByteArray) {
        for (b in data) {
            val byte = b.toInt() and 0xFF
            when (state) {
                ParseState.UTF8 -> processUtf8(byte)
                ParseState.NORMAL -> {
                    if (byte >= 0xC0 && byte <= 0xF7) {
                        utf8Buf[0] = byte.toByte()
                        utf8Len = 1
                        utf8Expected = when {
                            byte < 0xE0 -> 2
                            byte < 0xF0 -> 3
                            else -> 4
                        }
                        state = ParseState.UTF8
                    } else {
                        processNormal(byte)
                    }
                }
                ParseState.ESCAPE -> processEscape(byte)
                ParseState.CSI -> processCsi(byte)
                ParseState.OSC -> processOsc(byte)
                ParseState.OSC_ESC -> {
                    state = ParseState.NORMAL
                    oscData.clear()
                }
            }
        }
        version++
    }

    fun resize(newCols: Int, newRows: Int) {
        mainBuffer = copyBuffer(mainBuffer, rows, cols, newRows, newCols)
        altBuffer = copyBuffer(altBuffer, rows, cols, newRows, newCols)
        buffer = if (useAltBuffer) altBuffer else mainBuffer
        cols = newCols
        rows = newRows
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        version++
    }

    private fun processUtf8(byte: Int) {
        if (byte and 0xC0 != 0x80) {
            state = ParseState.NORMAL
            processNormal(byte)
            return
        }
        utf8Buf[utf8Len++] = byte.toByte()
        if (utf8Len >= utf8Expected) {
            state = ParseState.NORMAL
            val str = String(utf8Buf, 0, utf8Len, Charsets.UTF_8)
            for (ch in str) putChar(ch)
        }
    }

    private fun processNormal(byte: Int) {
        when (byte) {
            0x1B -> state = ParseState.ESCAPE
            0x0A -> linefeed()
            0x0D -> cursorCol = 0
            0x08 -> if (cursorCol > 0) cursorCol--
            0x09 -> cursorCol = minOf(cursorCol + (8 - cursorCol % 8), cols - 1)
            0x07 -> {}
            0x0E, 0x0F -> {}
            in 0x20..0x7E -> putChar(byte.toChar())
            in 0x80..0xBF -> {}
        }
    }

    private fun putChar(c: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            linefeed()
        }
        buffer[cursorRow][cursorCol] = TerminalCell(
            char = c, fg = currentFg, bg = currentBg,
            bold = currentBold, underline = currentUnderline,
            inverse = currentInverse, dim = currentDim,
            italic = currentItalic, strikethrough = currentStrikethrough,
        )
        cursorCol++
    }

    private fun linefeed() {
        if (cursorRow == scrollBottom) {
            scrollUp(scrollTop, scrollBottom)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun processEscape(byte: Int) {
        state = ParseState.NORMAL
        when (byte) {
            '['.code -> { state = ParseState.CSI; csiParams.clear() }
            ']'.code -> { state = ParseState.OSC; oscData.clear() }
            '('.code, ')'.code, '*'.code, '+'.code -> { state = ParseState.NORMAL }
            'D'.code -> linefeed()
            'M'.code -> reverseLinefeed()
            'E'.code -> { cursorCol = 0; linefeed() }
            '7'.code -> saveCursor()
            '8'.code -> restoreCursor()
            'c'.code -> fullReset()
            '='.code, '>'.code -> {}
        }
    }

    private fun processCsi(byte: Int) {
        val c = byte.toChar()
        if (c in '0'..'9' || c == ';' || c == '?' || c == '>' || c == '!' || c == ' ') {
            csiParams.append(c)
            return
        }
        state = ParseState.NORMAL
        val raw = csiParams.toString()
        val isPrivate = raw.startsWith("?")
        val paramStr = raw.removePrefix("?").removePrefix(">").removePrefix("!")
        val params = paramStr.split(';').mapNotNull { it.trim().toIntOrNull() }

        if (isPrivate) {
            processDecPrivateMode(c, params)
            return
        }

        when (c) {
            'm' -> processSgr(params)
            'H', 'f' -> {
                cursorRow = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
                cursorCol = ((params.getOrElse(1) { 1 }) - 1).coerceIn(0, cols - 1)
            }
            'A' -> cursorRow = maxOf(0, cursorRow - maxOf(1, params.getOrElse(0) { 1 }))
            'B' -> cursorRow = minOf(rows - 1, cursorRow + maxOf(1, params.getOrElse(0) { 1 }))
            'C' -> cursorCol = minOf(cols - 1, cursorCol + maxOf(1, params.getOrElse(0) { 1 }))
            'D' -> cursorCol = maxOf(0, cursorCol - maxOf(1, params.getOrElse(0) { 1 }))
            'E' -> { cursorCol = 0; cursorRow = minOf(rows - 1, cursorRow + maxOf(1, params.getOrElse(0) { 1 })) }
            'F' -> { cursorCol = 0; cursorRow = maxOf(0, cursorRow - maxOf(1, params.getOrElse(0) { 1 })) }
            'G' -> cursorCol = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, cols - 1)
            'J' -> eraseDisplay(params.getOrElse(0) { 0 })
            'K' -> eraseLine(params.getOrElse(0) { 0 })
            'L' -> insertLines(maxOf(1, params.getOrElse(0) { 1 }))
            'M' -> deleteLines(maxOf(1, params.getOrElse(0) { 1 }))
            'P' -> deleteChars(maxOf(1, params.getOrElse(0) { 1 }))
            '@' -> insertChars(maxOf(1, params.getOrElse(0) { 1 }))
            'X' -> eraseChars(maxOf(1, params.getOrElse(0) { 1 }))
            'S' -> repeat(maxOf(1, params.getOrElse(0) { 1 })) { scrollUp(scrollTop, scrollBottom) }
            'T' -> repeat(maxOf(1, params.getOrElse(0) { 1 })) { scrollDown(scrollTop, scrollBottom) }
            'd' -> cursorRow = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
            'r' -> {
                scrollTop = ((params.getOrElse(0) { 1 }) - 1).coerceIn(0, rows - 1)
                scrollBottom = ((params.getOrElse(1) { rows }) - 1).coerceIn(0, rows - 1)
                cursorRow = 0; cursorCol = 0
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            'n' -> {}
            'c' -> {}
            't' -> {}
            'h', 'l' -> {}
            'p' -> {}
            'q' -> {}
        }
    }

    private fun processDecPrivateMode(c: Char, params: List<Int>) {
        val enable = c == 'h'
        for (p in params) {
            when (p) {
                1049 -> {
                    if (enable) {
                        savedMainCursorRow = cursorRow; savedMainCursorCol = cursorCol
                        useAltBuffer = true; buffer = altBuffer
                        clearBuffer(buffer)
                        cursorRow = 0; cursorCol = 0
                    } else {
                        useAltBuffer = false; buffer = mainBuffer
                        cursorRow = savedMainCursorRow; cursorCol = savedMainCursorCol
                    }
                }
                1047, 47 -> {
                    if (enable) {
                        useAltBuffer = true; buffer = altBuffer; clearBuffer(buffer)
                    } else {
                        useAltBuffer = false; buffer = mainBuffer
                    }
                }
                1048 -> { if (enable) saveCursor() else restoreCursor() }
                25 -> {}
                1, 7, 12, 1000, 1002, 1003, 1006, 2004 -> {}
            }
        }
    }

    private fun processOsc(byte: Int) {
        when (byte) {
            0x07 -> { state = ParseState.NORMAL; oscData.clear() }
            0x1B -> state = ParseState.OSC_ESC
            else -> oscData.append(byte.toChar())
        }
    }

    private fun processSgr(params: List<Int>) {
        val ps = params.ifEmpty { listOf(0) }
        var i = 0
        while (i < ps.size) {
            when (ps[i]) {
                0 -> resetAttributes()
                1 -> currentBold = true
                2 -> currentDim = true
                3 -> currentItalic = true
                4 -> currentUnderline = true
                7 -> currentInverse = true
                9 -> currentStrikethrough = true
                22 -> { currentBold = false; currentDim = false }
                23 -> currentItalic = false
                24 -> currentUnderline = false
                27 -> currentInverse = false
                29 -> currentStrikethrough = false
                in 30..37 -> currentFg = ANSI_COLORS[ps[i] - 30]
                39 -> currentFg = TerminalCell.DEFAULT_FG
                in 40..47 -> currentBg = ANSI_COLORS[ps[i] - 40]
                49 -> currentBg = TerminalCell.DEFAULT_BG
                in 90..97 -> currentFg = ANSI_BRIGHT_COLORS[ps[i] - 90]
                in 100..107 -> currentBg = ANSI_BRIGHT_COLORS[ps[i] - 100]
                38 -> {
                    if (i + 1 < ps.size) {
                        when (ps[i + 1]) {
                            5 -> { if (i + 2 < ps.size) { currentFg = color256(ps[i + 2]); i += 2 } }
                            2 -> { if (i + 4 < ps.size) { currentFg = rgb(ps[i + 2], ps[i + 3], ps[i + 4]); i += 4 } }
                        }
                        i++
                    }
                }
                48 -> {
                    if (i + 1 < ps.size) {
                        when (ps[i + 1]) {
                            5 -> { if (i + 2 < ps.size) { currentBg = color256(ps[i + 2]); i += 2 } }
                            2 -> { if (i + 4 < ps.size) { currentBg = rgb(ps[i + 2], ps[i + 3], ps[i + 4]); i += 4 } }
                        }
                        i++
                    }
                }
            }
            i++
        }
    }

    private fun resetAttributes() {
        currentFg = TerminalCell.DEFAULT_FG; currentBg = TerminalCell.DEFAULT_BG
        currentBold = false; currentUnderline = false; currentInverse = false
        currentDim = false; currentItalic = false; currentStrikethrough = false
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                for (c in cursorCol until cols) buffer[cursorRow][c] = TerminalCell()
                for (r in cursorRow + 1 until rows) for (c in 0 until cols) buffer[r][c] = TerminalCell()
            }
            1 -> {
                for (r in 0 until cursorRow) for (c in 0 until cols) buffer[r][c] = TerminalCell()
                for (c in 0..cursorCol.coerceAtMost(cols - 1)) buffer[cursorRow][c] = TerminalCell()
            }
            2, 3 -> clearBuffer(buffer)
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until cols) buffer[cursorRow][c] = TerminalCell()
            1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) buffer[cursorRow][c] = TerminalCell()
            2 -> for (c in 0 until cols) buffer[cursorRow][c] = TerminalCell()
        }
    }

    private fun eraseChars(n: Int) {
        for (c in cursorCol until minOf(cursorCol + n, cols)) buffer[cursorRow][c] = TerminalCell()
    }

    private fun insertChars(n: Int) {
        val row = buffer[cursorRow]
        for (c in cols - 1 downTo cursorCol + n) row[c] = row[c - n]
        for (c in cursorCol until minOf(cursorCol + n, cols)) row[c] = TerminalCell()
    }

    private fun deleteChars(n: Int) {
        val row = buffer[cursorRow]
        for (c in cursorCol until cols - n) row[c] = row[c + n]
        for (c in maxOf(cursorCol, cols - n) until cols) row[c] = TerminalCell()
    }

    private fun insertLines(n: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        for (i in 0 until n) scrollDown(cursorRow, scrollBottom)
    }

    private fun deleteLines(n: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        for (i in 0 until n) scrollUp(cursorRow, scrollBottom)
    }

    private fun scrollUp(top: Int, bottom: Int) {
        if (top >= bottom || top < 0 || bottom >= rows) return
        if (top == scrollTop) {
            scrollback.add(buffer[top].copyOf())
            if (scrollback.size > maxScrollback) scrollback.removeAt(0)
        }
        for (r in top until bottom) buffer[r] = buffer[r + 1]
        buffer[bottom] = Array(cols) { TerminalCell() }
    }

    private fun scrollDown(top: Int, bottom: Int) {
        if (top >= bottom || top < 0 || bottom >= rows) return
        val saved = buffer[bottom]
        for (r in bottom downTo top + 1) buffer[r] = buffer[r - 1]
        buffer[top] = Array(cols) { TerminalCell() }
    }

    private fun reverseLinefeed() {
        if (cursorRow == scrollTop) {
            scrollDown(scrollTop, scrollBottom)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun saveCursor() { savedCursorRow = cursorRow; savedCursorCol = cursorCol }
    private fun restoreCursor() { cursorRow = savedCursorRow.coerceIn(0, rows - 1); cursorCol = savedCursorCol.coerceIn(0, cols - 1) }

    private fun fullReset() {
        mainBuffer = makeBuffer(rows, cols); altBuffer = makeBuffer(rows, cols)
        buffer = mainBuffer; useAltBuffer = false
        cursorRow = 0; cursorCol = 0; resetAttributes()
        scrollTop = 0; scrollBottom = rows - 1
        state = ParseState.NORMAL
    }

    private fun clearBuffer(buf: Array<Array<TerminalCell>>) {
        for (r in buf.indices) for (c in buf[r].indices) buf[r][c] = TerminalCell()
    }

    companion object {
        private fun makeBuffer(rows: Int, cols: Int) = Array(rows) { Array(cols) { TerminalCell() } }
        private fun copyBuffer(old: Array<Array<TerminalCell>>, oldRows: Int, oldCols: Int, newRows: Int, newCols: Int): Array<Array<TerminalCell>> {
            val buf = makeBuffer(newRows, newCols)
            for (r in 0 until minOf(oldRows, newRows)) for (c in 0 until minOf(oldCols, newCols)) buf[r][c] = old[r][c]
            return buf
        }
        private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

        val ANSI_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFCC0000.toInt(), 0xFF00CC00.toInt(), 0xFFCCCC00.toInt(),
            0xFF0000CC.toInt(), 0xFFCC00CC.toInt(), 0xFF00CCCC.toInt(), 0xFFCCCCCC.toInt(),
        )
        val ANSI_BRIGHT_COLORS = intArrayOf(
            0xFF555555.toInt(), 0xFFFF5555.toInt(), 0xFF55FF55.toInt(), 0xFFFFFF55.toInt(),
            0xFF5555FF.toInt(), 0xFFFF55FF.toInt(), 0xFF55FFFF.toInt(), 0xFFFFFFFF.toInt(),
        )
        fun color256(n: Int): Int {
            if (n < 8) return ANSI_COLORS[n]
            if (n < 16) return ANSI_BRIGHT_COLORS[n - 8]
            if (n < 232) { val idx = n - 16; val r = (idx / 36) * 51; val g = ((idx / 6) % 6) * 51; val b = (idx % 6) * 51; return rgb(r, g, b) }
            val gray = 8 + (n - 232) * 10; return rgb(gray, gray, gray)
        }
    }
}
