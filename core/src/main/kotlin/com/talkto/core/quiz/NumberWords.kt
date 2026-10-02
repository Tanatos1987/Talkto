package com.talkto.core.quiz

import java.util.Locale

/**
 * Reads a number from what was typed or said: "17", "-5", "минус пет", "двадесет и три", "сто и два",
 * "two hundred and five", "negative seven". Null when there is no number in it.
 */
object NumberWords {
    private val BG = mapOf(
        "нула" to 0, "един" to 1, "една" to 1, "едно" to 1, "два" to 2, "две" to 2, "три" to 3, "четири" to 4, "пет" to 5,
        "шест" to 6, "седем" to 7, "осем" to 8, "девет" to 9, "десет" to 10,
        "единадесет" to 11, "единайсет" to 11, "дванадесет" to 12, "дванайсет" to 12, "тринадесет" to 13, "тринайсет" to 13,
        "четиринадесет" to 14, "четиринайсет" to 14, "петнадесет" to 15, "петнайсет" to 15, "шестнадесет" to 16, "шестнайсет" to 16,
        "седемнадесет" to 17, "седемнайсет" to 17, "осемнадесет" to 18, "осемнайсет" to 18, "деветнадесет" to 19, "деветнайсет" to 19,
        "двадесет" to 20, "двайсет" to 20, "тридесет" to 30, "трийсет" to 30, "четиридесет" to 40, "четирийсет" to 40,
        "петдесет" to 50, "шестдесет" to 60, "седемдесет" to 70, "осемдесет" to 80, "деветдесет" to 90,
        "сто" to 100, "двеста" to 200, "триста" to 300, "четиристотин" to 400, "петстотин" to 500, "шестстотин" to 600,
        "седемстотин" to 700, "осемстотин" to 800, "деветстотин" to 900,
    )
    private val EN = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val MINUS = setOf("минус", "minus", "negative", "-", "−")
    private val DIGITS = Regex("(?<![\\d])([-−]?)\\s?(\\d{1,6})(?![\\d])")

    fun parse(text: String): Int? {
        val t = text.lowercase(Locale.ROOT).replace('−', '-').trim()
        DIGITS.find(t)?.let { m ->
            val negative = m.groupValues[1].isNotEmpty() || Regex("(?:минус|minus|negative)\\s*$").containsMatchIn(t.substring(0, m.range.first))
            val n = m.groupValues[2].toInt()
            return if (negative) -n else n
        }
        var total = 0
        var current = 0
        var found = false
        var negative = false
        for (raw in t.split(Regex("[\\s,-]+"))) {
            val w = raw.trim('.', '!', '?')
            when {
                w.isEmpty() || w == "и" || w == "and" -> Unit
                w in MINUS -> negative = true
                w == "хиляда" || w == "thousand" -> { total += (if (current == 0) 1 else current) * 1000; current = 0; found = true }
                w == "хиляди" -> { total += current * 1000; current = 0; found = true }
                w == "hundred" -> { current = (if (current == 0) 1 else current) * 100; found = true }
                BG.containsKey(w) -> { current += BG.getValue(w); found = true }
                EN.containsKey(w) -> { current += EN.getValue(w); found = true }
            }
        }
        if (!found) return null
        val n = total + current
        return if (negative) -n else n
    }
}
