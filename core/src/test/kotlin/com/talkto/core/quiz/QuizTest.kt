package com.talkto.core.quiz

import com.talkto.core.i18n.Lang
import com.talkto.core.voice.Speakable
import org.junit.Assert
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private fun assertEquals(expected: Any?, actual: Any?, message: String? = null) = Assert.assertEquals(message, expected, actual)
private fun assertTrue(value: Boolean, message: String? = null) = Assert.assertTrue(message, value)
private fun assertFalse(value: Boolean, message: String? = null) = Assert.assertFalse(message, value)
private fun assertNull(value: Any?, message: String? = null) = Assert.assertNull(message, value)
private fun fail(message: String): Nothing = throw AssertionError(message)

class NumberWordsTest {
    @Test
    fun digitsAndWords() {
        val cases = mapOf(
            "17" to 17, "-5" to -5, "−8" to -8, "минус 12" to -12, "минус пет" to -5, "двадесет и три" to 23, "сто и два" to 102,
            "двеста тридесет и пет" to 235, "хиляда" to 1000, "две хиляди" to 2000, "деветнайсет" to 19, "нула" to 0,
            "two hundred and five" to 205, "negative seven" to -7, "twenty-one" to 21, "minus 3" to -3, "отговорът е 42" to 42,
            "Мисля, че е осем." to 8, "one thousand two hundred" to 1200,
        )
        cases.forEach { (text, n) -> assertEquals(n, NumberWords.parse(text), text) }
        assertNull(NumberWords.parse("не знам"))
        assertNull(NumberWords.parse("I don't know"))
    }
}

class MathTasksTest {

    @Test
    fun everyAnswerIsRight() {
        val gen = MathTasks(Random(7))
        for (lang in Lang.entries) for (grade in 1..MathTasks.MAX_GRADE) repeat(400) {
            val task = gen.next(grade, lang)
            verify(task)
            assertEquals(grade, task.grade)
        }
        repeat(300) { verify(MathTasks(Random(it)).next(7, Lang.BG, algebraOnly = true).also { t -> assertTrue(t.algebra) }) }
    }

    @Test
    fun gradesStayInTheirRange() {
        val gen = MathTasks(Random(3))
        repeat(500) {
            val one = gen.next(1, Lang.BG)
            assertTrue(one.answer in 0..20, one.display)
            assertTrue(gen.next(2, Lang.EN).answer in 0..100)
            assertTrue(gen.next(3, Lang.BG).answer in 0..999)
        }
        val six = List(300) { gen.next(6, Lang.BG) }
        assertTrue(six.all { it.algebra })
        assertTrue(List(300) { gen.next(4, Lang.BG) }.none { it.algebra })
        assertTrue(List(300) { gen.next(2, Lang.BG) }.any { it.story })
    }

    @Test
    fun spokenTextHasOnlyWordsForTheVoice() {
        val gen = MathTasks(Random(11))
        for (lang in Lang.entries) for (grade in 1..MathTasks.MAX_GRADE) repeat(200) {
            val s = gen.next(grade, lang).spoken
            assertFalse(s.any { it in "×÷−²³=:()" }, s)
            if (lang == Lang.BG) assertFalse(s.contains('x'), s)
            // Cleaning for the voice must not lose a single word or number.
            assertEquals(s.filter { it.isLetterOrDigit() }, Speakable.clean(s).filter { it.isLetterOrDigit() }, s)
        }
    }

    @Test
    fun answersAreChecked() {
        val task = MathTask("7 + 5 = ?", "", "", 12, "", 1)
        assertEquals(true, task.check("12"))
        assertEquals(true, task.check("дванадесет"))
        assertEquals(true, task.check("twelve"))
        assertEquals(false, task.check("11"))
        assertNull(task.check("не знам"))
        assertEquals(true, MathTask("x = ?", "", "", -4, "", 7).check("минус четири"))
    }

    @Test
    fun startingGrade() {
        assertEquals(3, MathTasks.gradeFor(3, 12))
        assertEquals(2, MathTasks.gradeFor(null, 8))
        assertEquals(1, MathTasks.gradeFor(null, 5))
        assertEquals(7, MathTasks.gradeFor(null, 15))
        assertEquals(2, MathTasks.gradeFor(null, null))
    }

    @Test
    fun score() {
        val s = QuizScore().answer(true).answer(true).answer(false).answer(true)
        assertEquals(3, s.correct)
        assertEquals(4, s.total)
        assertEquals(1, s.streak)
        assertEquals(2, s.bestStreak)
        assertEquals(75, s.percent)
    }

    // ------------------------------------------------------------------ an independent checker

    private fun verify(t: MathTask) {
        when {
            t.algebra -> {
                val eq = t.display.substringBefore(", x > 0")
                val (left, right) = eq.split(" = ").also { if (it.size != 2) fail("not an equation: ${t.display}") }
                val l = Expr(norm(left), t.answer.toDouble()).value()
                val r = Expr(norm(right), t.answer.toDouble()).value()
                assertTrue(abs(l - r) < 1e-9, "${t.display} with x = ${t.answer}: $l != $r")
                if (t.display.contains("x > 0")) assertTrue(t.answer > 0)
            }
            t.story -> {
                val (expr, result) = t.explanation.split(" = ")
                assertEquals(t.answer.toDouble(), Expr(norm(expr)).value(), t.explanation)
                assertEquals(t.answer, result.trim().toInt(), t.explanation)
                assertTrue(t.display.endsWith("?"))
            }
            else -> {
                assertTrue(t.display.endsWith(" = ?"), t.display)
                val v = Expr(norm(t.display.removeSuffix(" = ?"))).value()
                assertEquals(t.answer.toDouble(), v, t.display)
                assertEquals(t.answer.toString(), t.explanation.substringBefore(".").substringAfterLast("= ").trim(), t.explanation)
            }
        }
    }

    private fun norm(s: String): String {
        var e = s.replace('×', '*').replace('÷', '/').replace(':', '/').replace('−', '-')
            .replace("²", "^2").replace("³", "^3")
        e = e.replace(Regex("1/(\\d+) (?:от|of) (\\d+)"), "(1/$1)*$2")
        e = e.replace(Regex("(\\d+)% (?:от|of) (\\d+)"), "($1/100)*$2")
        e = e.replace(Regex("(\\d)(x|\\()"), "$1*$2")
        return e.replace(" ", "")
    }

    /** + - * / ^, brackets, unary minus and x. */
    private class Expr(private val s: String, private val x: Double = Double.NaN) {
        private var i = 0
        fun value(): Double = sum().also { if (i != s.length) fail("unparsed '${s.substring(i)}' in $s") }
        private fun sum(): Double {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = product(); v = if (op == '+') v + r else v - r }
            return v
        }
        private fun product(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) { val op = s[i++]; val r = power(); v = if (op == '*') v * r else v / r }
            return v
        }
        private fun power(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') { i++; return Math.pow(b, unary()) }
            return b
        }
        private fun unary(): Double = if (s[i] == '-') { i++; -unary() } else atom()
        private fun atom(): Double {
            if (s[i] == '(') { i++; val v = sum(); if (s[i++] != ')') fail("no ) in $s"); return v }
            if (s[i] == 'x') { i++; return x }
            val start = i
            while (i < s.length && s[i].isDigit()) i++
            if (start == i) fail("number expected at $i in $s")
            return s.substring(start, i).toDouble()
        }
    }
}

class TriviaTest {

    @Test
    fun bankIsWellFormed() {
        val all = TriviaBank.ALL
        assertTrue(all.size >= 130, "only ${all.size} questions")
        assertEquals(all.size, all.map { it.id }.toSet().size)
        TriviaCategory.entries.forEach { c -> assertTrue(TriviaBank.of(c).size >= 12, "$c has ${TriviaBank.of(c).size}") }
        for (q in all) for (lang in Lang.entries) {
            val answers = q.answers(lang)
            assertEquals(4, answers.size, "${q.id} $lang: $answers")
            assertEquals(4, answers.toSet().size, "${q.id} $lang repeats an answer")
            assertTrue(answers.all { it.isNotBlank() } && q.question(lang).endsWith("?"), "${q.id} $lang")
        }
        // The Bulgarian and English texts must not be swapped.
        for (q in all) {
            assertTrue(q.question(Lang.BG).any { it in 'а'..'я' || it in 'А'..'Я' }, q.id)
            assertFalse(q.question(Lang.EN).any { it in 'а'..'я' || it in 'А'..'Я' }, q.id)
        }
    }

    @Test
    fun roundsPreferFreshQuestions() {
        val trivia = Trivia(Random(5))
        val space = TriviaBank.of(TriviaCategory.SPACE)
        val recent = space.take(space.size - 3).map { it.id }
        val round = trivia.round(5, TriviaCategory.SPACE, recent)
        assertEquals(5, round.size)
        assertTrue(round.take(3).all { it.question.id !in recent })
        assertTrue(round.all { it.question.category == TriviaCategory.SPACE })
        round.forEach { card -> assertEquals(card.question.answers(Lang.BG)[0], card.options(Lang.BG)[card.correct]) }
        assertEquals(10, trivia.round().map { it.question.id }.toSet().size)
    }

    @Test
    fun spokenAndTypedAnswers() {
        val whale = TriviaBank.ALL.first { it.id == "animals_1" }
        val card = TriviaCard(whale, listOf(0, 1, 2, 3))
        assertEquals(0, card.match("син кит", Lang.BG))
        assertEquals(0, card.match("Синият кит!", Lang.BG))
        assertEquals(1, card.match("б", Lang.BG))
        assertEquals(2, card.match("третото", Lang.BG))
        assertEquals(2, card.match("the whale shark", Lang.EN))
        assertEquals(3, card.match("D", Lang.EN))
        assertEquals(1, card.match("2", Lang.EN))
        assertNull(card.match("не знам", Lang.BG))

        val spider = TriviaBank.ALL.first { it.id == "animals_3" }
        val shuffled = TriviaCard(spider, listOf(3, 0, 1, 2)) // 4, 8, 6, 10
        assertEquals(0, shuffled.match("4", Lang.EN))
        assertEquals(1, shuffled.match("осем", Lang.BG))
        assertEquals(1, shuffled.match("the second one", Lang.EN))
        assertEquals(1, shuffled.correct)
        assertNull(shuffled.match("5", Lang.EN))

        val light = TriviaBank.ALL.first { it.question(Lang.EN).startsWith("How fast does light") }
        assertEquals(0, TriviaCard(light, listOf(0, 1, 2, 3)).match("около 300 000", Lang.BG))
    }
}

class SpeakableNumbersTest {
    @Test
    fun decimalsAndUnitsStay() {
        assertEquals("Болт пробяга 100 метра за 9,58 секунди.", Speakable.clean("Болт пробяга 100 метра за 9,58 секунди."))
        assertEquals("Over 1,600 metres, deep.", Speakable.clean("Over 1,600 metres,deep."))
        assertEquals("Тича с 100 километра в час.", Speakable.clean("Тича с 100 км/ч."))
    }
}
