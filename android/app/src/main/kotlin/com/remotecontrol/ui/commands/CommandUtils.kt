package com.remotecontrol.ui.commands

object CommandUtils {
    private val HEX_ESCAPE = Regex("""\\x([0-9a-fA-F]{2})""")
    private val CTRL_SHORTHAND = Regex("""<C-([a-zA-Z])>""")

    /**
     * Resolves escape sequences in a saved command string:
     * - `\xHH` → byte value (e.g. `\x13` = Ctrl+S)
     * - `<C-s>` → control character (e.g. Ctrl+S = 0x13)
     * - `\n`, `\t`, `\e` → newline, tab, escape
     */
    fun resolveCommand(raw: String): String {
        var result = raw
        result = CTRL_SHORTHAND.replace(result) { match ->
            val ch = match.groupValues[1].uppercase()[0]
            val code = ch.code - 64
            if (code in 0..31) code.toChar().toString() else match.value
        }
        result = HEX_ESCAPE.replace(result) { match ->
            val byte = match.groupValues[1].toInt(16)
            byte.toChar().toString()
        }
        result = result.replace("\\e", "\u001b").replace("\\t", "\t").replace("\\n", "\n")
        return result
    }

    fun hasControlChars(resolved: String): Boolean = resolved.any { it.code < 0x20 && it != '\n' }
}
