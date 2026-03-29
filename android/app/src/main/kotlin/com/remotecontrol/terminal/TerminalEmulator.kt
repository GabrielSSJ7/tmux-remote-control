package com.remotecontrol.terminal

data class TerminalCell(
    var char: Char = ' ',
    var fg: Int = DEFAULT_FG,
    var bg: Int = DEFAULT_BG,
    var bold: Boolean = false,
    var underline: Boolean = false,
) {
    companion object {
        const val DEFAULT_FG = 0xFFE0E0E0.toInt()
        const val DEFAULT_BG = 0xFF1E1E1E.toInt()
    }
}

class TerminalEmulator(
    var cols: Int = 80,
    var rows: Int = 24,
) {
    private var buffer: Array<Array<TerminalCell>> = makeBuffer(rows, cols)
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    private var currentFg = TerminalCell.DEFAULT_FG
    private var currentBg = TerminalCell.DEFAULT_BG
    private var currentBold = false
    private var currentUnderline = false
    private var state = ParseState.NORMAL
    private val csiParams = StringBuilder()
    var version = 0L
        private set

    private enum class ParseState { NORMAL, ESCAPE, CSI }

    fun getCell(row: Int, col: Int): TerminalCell {
        if (row in 0 until rows && col in 0 until cols) return buffer[row][col]
        return TerminalCell()
    }

    fun process(data: ByteArray) {
        for (b in data) {
            val c = (b.toInt() and 0xFF).toChar()
            when (state) {
                ParseState.NORMAL -> processNormal(c)
                ParseState.ESCAPE -> processEscape(c)
                ParseState.CSI -> processCsi(c)
            }
        }
        version++
    }

    fun resize(newCols: Int, newRows: Int) {
        val newBuf = makeBuffer(newRows, newCols)
        for (r in 0 until minOf(rows, newRows)) {
            for (c in 0 until minOf(cols, newCols)) {
                newBuf[r][c] = buffer[r][c]
            }
        }
        buffer = newBuf
        cols = newCols
        rows = newRows
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        version++
    }

    private fun processNormal(c: Char) {
        when (c) {
            '\u001b' -> state = ParseState.ESCAPE
            '\n' -> { cursorCol = 0; cursorRow++; if (cursorRow >= rows) scrollUp() }
            '\r' -> cursorCol = 0
            '\b' -> if (cursorCol > 0) cursorCol--
            '\t' -> cursorCol = minOf(cursorCol + (8 - cursorCol % 8), cols - 1)
            '\u0007' -> {}
            else -> if (c >= ' ') {
                if (cursorCol >= cols) { cursorCol = 0; cursorRow++; if (cursorRow >= rows) scrollUp() }
                buffer[cursorRow][cursorCol] = TerminalCell(char = c, fg = currentFg, bg = currentBg, bold = currentBold, underline = currentUnderline)
                cursorCol++
            }
        }
    }

    private fun processEscape(c: Char) {
        when (c) {
            '[' -> { state = ParseState.CSI; csiParams.clear() }
            else -> state = ParseState.NORMAL
        }
    }

    private fun processCsi(c: Char) {
        if (c in '0'..'9' || c == ';') { csiParams.append(c); return }
        state = ParseState.NORMAL
        val params = csiParams.toString().split(';').mapNotNull { it.toIntOrNull() }
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
            'J' -> eraseDisplay(params.getOrElse(0) { 0 })
            'K' -> eraseLine(params.getOrElse(0) { 0 })
        }
    }

    private fun processSgr(params: List<Int>) {
        val ps = params.ifEmpty { listOf(0) }
        var i = 0
        while (i < ps.size) {
            when (ps[i]) {
                0 -> { currentFg = TerminalCell.DEFAULT_FG; currentBg = TerminalCell.DEFAULT_BG; currentBold = false; currentUnderline = false }
                1 -> currentBold = true
                4 -> currentUnderline = true
                22 -> currentBold = false
                24 -> currentUnderline = false
                in 30..37 -> currentFg = ANSI_COLORS[ps[i] - 30]
                39 -> currentFg = TerminalCell.DEFAULT_FG
                in 40..47 -> currentBg = ANSI_COLORS[ps[i] - 40]
                49 -> currentBg = TerminalCell.DEFAULT_BG
                in 90..97 -> currentFg = ANSI_BRIGHT_COLORS[ps[i] - 90]
                in 100..107 -> currentBg = ANSI_BRIGHT_COLORS[ps[i] - 100]
                38 -> { if (i + 2 < ps.size && ps[i + 1] == 5) { currentFg = color256(ps[i + 2]); i += 2 } }
                48 -> { if (i + 2 < ps.size && ps[i + 1] == 5) { currentBg = color256(ps[i + 2]); i += 2 } }
            }
            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { for (c in cursorCol until cols) buffer[cursorRow][c] = TerminalCell(); for (r in cursorRow + 1 until rows) for (c in 0 until cols) buffer[r][c] = TerminalCell() }
            1 -> { for (r in 0 until cursorRow) for (c in 0 until cols) buffer[r][c] = TerminalCell(); for (c in 0..cursorCol) buffer[cursorRow][c] = TerminalCell() }
            2 -> for (r in 0 until rows) for (c in 0 until cols) buffer[r][c] = TerminalCell()
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until cols) buffer[cursorRow][c] = TerminalCell()
            1 -> for (c in 0..cursorCol) buffer[cursorRow][c] = TerminalCell()
            2 -> for (c in 0 until cols) buffer[cursorRow][c] = TerminalCell()
        }
    }

    private fun scrollUp() {
        for (r in 1 until rows) buffer[r - 1] = buffer[r]
        buffer[rows - 1] = Array(cols) { TerminalCell() }
        cursorRow = rows - 1
    }

    companion object {
        private fun makeBuffer(rows: Int, cols: Int) = Array(rows) { Array(cols) { TerminalCell() } }
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
            if (n < 232) { val idx = n - 16; val r = (idx / 36) * 51; val g = ((idx / 6) % 6) * 51; val b = (idx % 6) * 51; return (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
            val gray = 8 + (n - 232) * 10; return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
    }
}
