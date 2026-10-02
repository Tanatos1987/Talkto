package com.talkto.core.learn

import com.talkto.core.i18n.Lang
import java.util.Locale

/**
 * Checks a spoken (or typed) answer. Speech recognisers add articles ("a dog", "кучето"), capital letters, digits
 * for numbers and small slips, so the check is forgiving where it can be and exact where it has to be:
 * short words must match exactly, longer ones may be one or two letters off.
 */
object AnswerMatcher {

    fun matches(heard: String, word: Word, target: Lang): Boolean {
        val said = normalize(heard, target)
        if (said.isEmpty()) return false
        val candidates = (listOf(word.text(target)) + word.alternatives(target)).map { normalize(it, target) }.filter { it.isNotEmpty() }
        val tokens = said.split(' ')
        return candidates.any { c ->
            when {
                said == c -> true
                (" $said ").contains(" $c ") -> true
                c.contains(' ') -> distance(said, c) <= c.length / 5
                else -> tokens.any { t -> t == c || distance(t, c) <= allowed(c.length) || (target == Lang.BG && isArticleForm(t, c)) }
            }
        }
    }

    fun normalize(s: String, lang: Lang): String {
        var t = s.lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N} ']"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (lang == Lang.EN) t = t.replace(Regex("^(?:a|an|the|to|it's|it is|this is) "), "")
        return t
    }

    /** "кучето", "котката", "стола": the word plus a Bulgarian definite article. */
    private fun isArticleForm(t: String, c: String): Boolean =
        t.length > c.length && t.startsWith(c) && t.substring(c.length) in BG_ARTICLES ||
            (c.endsWith("а") || c.endsWith("я")) && t == c + "та" ||
            c.endsWith("е") && t == c + "то"

    private fun allowed(n: Int) = when {
        n <= 3 -> 0
        n <= 6 -> 1
        else -> 2
    }

    /** Levenshtein edit distance. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    private val BG_ARTICLES = setOf("ът", "а", "я", "ят", "та", "то", "те", "та", "о")
}
