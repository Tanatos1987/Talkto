package com.talkto.core.tools

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Offline calculator. Recursive-descent parser over + - * / ^ %, parentheses and sqrt, with
 * Bulgarian and English words ("плюс", "по", "делено на", "15% от 200", "корен от 81").
 */
object Calculator {

    private val PREFIX = Regex("^(колко (прави|е|са)|сметни|пресметни|изчисли|calculate|compute|what is|what's|=)\\s*", RegexOption.IGNORE_CASE)

    /**
     * Returns a pure arithmetic expression if [text] is a calculation, otherwise null.
     * Plain numbers ("5") are not calculations; there must be an operator or a function.
     */
    fun extract(text: String): String? {
        var t = text.trim().trimEnd('?', '!', '.').lowercase()
        t = PREFIX.replace(t, "")
        // "6:45" is a clock time, not 6 divided by 45.
        if (Regex("^\\d{1,2}:\\d{2}$").matches(t)) return null
        t = t.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:от|of)\\s*"), "($1/100)*")
            .replace(Regex("корен(?: квадратен)? от\\s*(\\d+(?:[.,]\\d+)?)"), "sqrt($1)")
            .replace(Regex("\\bsqrt\\s*(\\d+(?:[.,]\\d+)?)"), "sqrt($1)")
            .replace("√", "sqrt")
            .replace(word("на квадрат|squared"), "^2")
            .replace(word("на степен|to the power of"), "^")
            .replace(word("плюс|plus"), "+")
            .replace(word("минус|minus"), "-")
            .replace(word("умножено по|по|times|multiplied by"), "*")
            .replace(word("делено на|divided by"), "/")
            .replace('×', '*').replace('÷', '/').replace('x', '*').replace(':', '/')
            .replace(Regex("(?<=\\d),(?=\\d)"), ".")
            .trim()
        if (t.isEmpty() || !t.any(Char::isDigit)) return null
        if (!Regex("^[0-9+\\-*/^().%\\s]*(sqrt[0-9+\\-*/^().%\\s]*)*$").matches(t)) return null
        val hasOperator = Regex("\\d\\s*[+\\-*/^%]|sqrt|\\)\\s*[+\\-*/^]").containsMatchIn(t)
        return if (hasOperator) t else null
    }

    /** Whole-word match that also works for Cyrillic (Java's \b is ASCII-only since JDK 19). */
    private fun word(alternatives: String) = Regex("(?<!\\p{L})(?:$alternatives)(?!\\p{L})")

    fun evaluate(expression: String): Double {
        val p = Parser(expression.replace(" ", ""))
        val v = p.expr()
        require(p.done()) { "Unexpected '${p.rest()}'" }
        require(v.isFinite()) { "Result is not a finite number" }
        return v
    }

    /** Bulgarian formatting: decimal comma, at most 10 significant digits, no trailing zeros. */
    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        val s = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
        return s.replace('.', ',')
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun done() = i >= s.length
        fun rest() = s.substring(i)

        fun expr(): Double {
            var v = term()
            while (!done()) v = when (s[i]) {
                '+' -> { i++; v + term() }
                '-' -> { i++; v - term() }
                else -> return v
            }
            return v
        }

        fun term(): Double {
            var v = power()
            while (!done()) v = when (s[i]) {
                '*' -> { i++; v * power() }
                '/' -> {
                    i++
                    val d = power()
                    require(d != 0.0) { "Division by zero" }
                    v / d
                }
                else -> return v
            }
            return v
        }

        fun power(): Double {
            val base = unary()
            if (!done() && s[i] == '^') {
                i++
                return base.pow(power()) // right-associative
            }
            return base
        }

        fun unary(): Double = when {
            !done() && s[i] == '-' -> { i++; -unary() }
            !done() && s[i] == '+' -> { i++; unary() }
            else -> postfix(primary())
        }

        fun postfix(v: Double): Double {
            var r = v
            while (!done() && s[i] == '%') {
                i++; r /= 100.0
            }
            return r
        }

        fun primary(): Double {
            require(!done()) { "Unexpected end" }
            if (s.startsWith("sqrt", i)) {
                i += 4
                val v = primary()
                require(v >= 0) { "Square root of a negative number" }
                return sqrt(v)
            }
            if (s[i] == '(') {
                i++
                val v = expr()
                require(!done() && s[i] == ')') { "Missing ')'" }
                i++
                return v
            }
            val start = i
            while (!done() && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start) { "Expected a number at '${rest()}'" }
            return s.substring(start, i).toDouble()
        }
    }
}
