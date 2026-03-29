package com.remotecontrol.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalEmulatorTest {
    @Test
    fun writePlainText() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("Hello".toByteArray())
        assertEquals('H', emu.getCell(0, 0).char)
        assertEquals('o', emu.getCell(0, 4).char)
        assertEquals(0, emu.cursorRow)
        assertEquals(5, emu.cursorCol)
    }

    @Test
    fun newlineMovesToNextRow() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("AB\nCD".toByteArray())
        assertEquals('A', emu.getCell(0, 0).char)
        assertEquals('C', emu.getCell(1, 0).char)
    }

    @Test
    fun carriageReturnResetsColumn() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("ABCDE\rXY".toByteArray())
        assertEquals('X', emu.getCell(0, 0).char)
        assertEquals('Y', emu.getCell(0, 1).char)
        assertEquals('C', emu.getCell(0, 2).char)
    }

    @Test
    fun lineWrapAtEndOfRow() {
        val emu = TerminalEmulator(cols = 5, rows = 3)
        emu.process("ABCDEFGH".toByteArray())
        assertEquals('F', emu.getCell(1, 0).char)
    }

    @Test
    fun sgrSetsForegroundColor() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("\u001b[31mR".toByteArray())
        val cell = emu.getCell(0, 0)
        assertEquals('R', cell.char)
        assertEquals(0xFFCC0000.toInt(), cell.fg)
    }

    @Test
    fun sgrResetClearsStyles() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("\u001b[1;31mA\u001b[0mB".toByteArray())
        assertTrue(emu.getCell(0, 0).bold)
        assertFalse(emu.getCell(0, 1).bold)
    }

    @Test
    fun cursorMovementCsiH() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("\u001b[3;5H*".toByteArray())
        assertEquals('*', emu.getCell(2, 4).char)
    }

    @Test
    fun eraseDisplayCsiJ() {
        val emu = TerminalEmulator(cols = 5, rows = 3)
        emu.process("AAAAA\nBBBBB\nCCCCC".toByteArray())
        emu.process("\u001b[1;1H\u001b[2J".toByteArray())
        assertEquals(' ', emu.getCell(0, 0).char)
        assertEquals(' ', emu.getCell(2, 4).char)
    }

    @Test
    fun resizePreservesContent() {
        val emu = TerminalEmulator(cols = 10, rows = 5)
        emu.process("Hello".toByteArray())
        emu.resize(20, 10)
        assertEquals(20, emu.cols)
        assertEquals(10, emu.rows)
        assertEquals('H', emu.getCell(0, 0).char)
    }
}
