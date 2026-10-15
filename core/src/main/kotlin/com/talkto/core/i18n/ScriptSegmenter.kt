package com.talkto.core.i18n

/**
 * Splits mixed text into runs of one language, so each run can be spoken by a voice that knows it:
 * Cyrillic letters are Bulgarian, Latin letters English. Everything else (digits, spaces, punctuation, emoji)
 * stays with the run it sits in; text before the first letter joins the first run.
 *
 * "Куче на английски е dog." -> ["Куче на английски е " (BG), "dog." (EN)]
 */
object ScriptSegmenter {

    data class Segment(val text: String, val lang: Lang)

    fun scriptOf(c: Char): Lang? = when {
        c in 'Ѐ'..'ӿ' -> Lang.BG
        c in 'a'..'z' || c in 'A'..'Z' || c in 'À'..'ɏ' -> Lang.EN
        else -> null
    }

    /**
     * [fallback] is used for text without any letters. Short Latin runs of one or two letters inside Bulgarian
     * ("в 5 h", "Wi-Fi" stays English) are kept apart only when they are real words of 2+ letters; a single stray
     * Latin letter stays in the Bulgarian run, so the voice does not switch for it.
     */
    fun segments(text: String, fallback: Lang = Lang.BG): List<Segment> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<Segment>()
        val buf = StringBuilder()
        var current: Lang? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val script = scriptOf(c)
            if (script == null || script == current) {
                buf.append(c); i++; continue
            }
            // A run of letters of the other script: how long is it?
            var j = i
            while (j < text.length && (scriptOf(text[j]) == script || text[j] == '-' || text[j] == '\'')) j++
            val word = text.substring(i, j)
            val letters = word.count { scriptOf(it) == script }
            if (current != null && letters < 2) {
                buf.append(word); i = j; continue
            }
            if (current == null) {
                current = script
            } else {
                // Trailing spaces stay with the run they follow, leading ones with the new run.
                out += Segment(buf.toString(), current)
                buf.clear()
                current = script
            }
            buf.append(word)
            i = j
        }
        out += Segment(buf.toString(), current ?: fallback)
        return merge(out.filter { it.text.isNotEmpty() })
    }

    /** The language most of the letters are in, or [fallback] with no letters. */
    fun dominant(text: String, fallback: Lang = Lang.BG): Lang {
        var bg = 0
        var en = 0
        text.forEach { c -> when (scriptOf(c)) { Lang.BG -> bg++; Lang.EN -> en++; null -> Unit } }
        return when {
            bg == 0 && en == 0 -> fallback
            bg >= en -> Lang.BG
            else -> Lang.EN
        }
    }

    private fun merge(list: List<Segment>): List<Segment> {
        val out = ArrayList<Segment>()
        for (s in list) {
            val last = out.lastOrNull()
            if (last != null && last.lang == s.lang) out[out.lastIndex] = Segment(last.text + s.text, s.lang) else out += s
        }
        return out
    }
}
