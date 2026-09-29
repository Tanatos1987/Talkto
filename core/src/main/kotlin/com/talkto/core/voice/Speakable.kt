package com.talkto.core.voice

/**
 * Turns a reply into what the voice should actually say. Text-to-speech engines read out emoji ("dog face"),
 * bullets, quotes and brackets; this keeps the words, the numbers and the sentence punctuation that only shapes
 * the intonation (. , ! ? ;), plus the few symbols that carry meaning between numbers (14:10, 1/2, 5 + 3 = 8, 50%, 20 °C).
 */
object Speakable {

    fun clean(text: String): String {
        val cps = UNITS.entries.fold(text) { acc, (unit, words) -> acc.replace(unit, words) }.codePoints().toArray()
        val out = StringBuilder(text.length)
        for (i in cps.indices) {
            val cp = cps[i]
            val prev = cps.getOrNull(i - 1) ?: 0
            val next = cps.getOrNull(i + 1) ?: 0
            when {
                isPictograph(cp) || isInvisible(cp) -> out.append(' ')
                cp == '\n'.code -> out.append(". ")
                cp == '\r'.code || cp == '\t'.code -> out.append(' ')
                // Apostrophes inside words stay ("don't", "d'Italia"), all other quote marks go.
                (cp == '\''.code || cp == 0x2019) && isLetter(prev) && isLetter(next) -> out.append('\'')
                cp in QUOTES -> out.append(' ')
                cp in BRACKETS -> out.append(", ")
                cp in BULLETS -> out.append(' ')
                cp in DROP -> out.append(' ')
                cp == 0x2014 || cp == 0x2013 -> out.append(", ") // em and en dash
                cp == '-'.code -> out.append(if (isWordChar(prev) && isWordChar(next)) "-" else if (next.isDigitCp() && !isWordChar(prev)) "-" else ", ")
                cp == '/'.code -> out.append(if (prev.isDigitCp() && next.isDigitCp()) "/" else " ")
                cp == ':'.code -> out.append(if (prev.isDigitCp() && next.isDigitCp()) ":" else ",")
                cp == 0x2026 -> out.append('.') // …
                else -> out.appendCodePoint(cp)
            }
        }
        return tidy(out.toString())
    }

    private fun tidy(s: String): String {
        var t = s.replace(Regex("\\.{2,}"), ".")
        t = t.replace(Regex("\\s+"), " ")
        // No space before a pause mark ("хартия ." -> "хартия."), except after a math sign ("5 = ?").
        t = t.replace(Regex("(?<![=+×÷−-])\\s+([,.!?;])"), "$1")
        // A pause right before a full stop or another pause is one pause.
        t = t.replace(Regex(",(?:\\s*,)+"), ",")
        t = t.replace(Regex(",\\s*([.!?;])"), "$1").replace(Regex("([.!?;])\\s*,"), "$1")
        t = t.replace(Regex("([.!?;])(?:\\s*[.;])+"), "$1")
        // A space after each pause, but a decimal or thousands comma stays inside its number (9,58 or 1,600).
        t = t.replace(Regex("(?<!\\d),(?=\\S)|,(?=[^\\s\\d])"), ", ")
        return t.trim().trimStart(',', '.', ';', ' ').trim()
    }

    private fun Int.isDigitCp() = this in '0'.code..'9'.code
    private fun isLetter(cp: Int) = cp != 0 && Character.isLetter(cp)
    private fun isWordChar(cp: Int) = cp != 0 && Character.isLetterOrDigit(cp)

    /** Emoji, pictographs, dingbats, arrows, shapes, flags, keycaps, chess and die symbols. */
    fun isPictograph(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2190..0x21FF ||
            cp in 0x2300..0x23FF || cp in 0x25A0..0x25FF || cp in 0x2460..0x24FF || cp in 0xE0020..0xE007F ||
            cp == 0x00A9 || cp == 0x00AE || cp == 0x2122 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299

    private fun isInvisible(cp: Int) = cp in 0xFE00..0xFE0F || cp == 0x200D || cp == 0x20E3 || cp == 0x200B

    /** Units the voice would otherwise spell letter by letter. */
    private val UNITS = mapOf(
        Regex("(?<=\\d)\\s?км/ч") to " километра в час",
        Regex("(?<=\\d)\\s?km/h") to " kilometres an hour",
    )

    private val QUOTES = setOf('"'.code, '\''.code, 0x201E, 0x201C, 0x201D, 0x2018, 0x2019, 0x201A, 0x00AB, 0x00BB, 0x2039, 0x203A, '`'.code)
    private val BRACKETS = setOf('('.code, ')'.code, '['.code, ']'.code, '{'.code, '}'.code)
    private val BULLETS = setOf(0x2022, 0x00B7, 0x2023, 0x2043, 0x25E6, 0x2219)
    private val DROP = setOf('*'.code, '#'.code, '_'.code, '~'.code, '^'.code, '|'.code, '<'.code, '>'.code, '\\'.code, '@'.code)
}
