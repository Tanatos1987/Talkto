package com.talkto.core.quiz

import com.talkto.core.i18n.Lang
import kotlin.math.abs
import kotlin.random.Random

/**
 * One maths task. [display] is what the screen shows ("7 + 5 = ?", "3x + 4 = 19" or a story), [spoken] the same in
 * words for the voice, [explanation] the worked solution shown after answering. Answers are always whole numbers.
 */
data class MathTask(
    val display: String,
    val prompt: String,
    val spoken: String,
    val answer: Int,
    val explanation: String,
    val grade: Int,
    val algebra: Boolean = false,
    val story: Boolean = false,
) {
    /** True or false for an answer that holds a number ("12", "минус 3", "twenty one"), null when it holds none. */
    fun check(input: String): Boolean? = NumberWords.parse(input)?.let { it == answer }
}

/**
 * Tasks for school years 1 to 7:
 * 1: + and − to 20; 2: to 100 and the 2-5 and 10 times tables; 3: the whole times table, exact division, to 1000;
 * 4: bigger products, order of operations, fractions of a number; 5: negative numbers, percentages, squares;
 * 6: first equations (x + a = b, a·x + b = c); 7: x on both sides, brackets, squares and cubes.
 * Years 1 to 4 also get story problems.
 */
class MathTasks(private val random: Random = Random.Default) {

    /** A task for [grade]; [algebraOnly] asks for an equation whatever the year (years 6 and 7 level). */
    fun next(grade: Int, lang: Lang, algebraOnly: Boolean = false): MathTask {
        val g = grade.coerceIn(1, MAX_GRADE)
        val w = Words(lang)
        if (algebraOnly) return if (g >= 7 && random.nextBoolean()) equation2(g, w) else equation1(maxOf(g, 6), w)
        if (g <= 4 && random.nextFloat() < STORY_SHARE) return story(g, w)
        return when (g) {
            1 -> if (random.nextBoolean()) add(rng(1, 10), rng(1, 10), g, w) else sub(20, g, w)
            2 -> when (random.nextInt(3)) {
                0 -> { val a = rng(10, 90); add(a, rng(1, 100 - a), g, w) }
                1 -> sub(99, g, w)
                else -> mul(listOf(2, 3, 4, 5, 10).random(random), rng(1, 10), g, w)
            }
            3 -> when (random.nextInt(3)) {
                0 -> mul(rng(2, 10), rng(2, 10), g, w)
                1 -> div(rng(2, 10), rng(2, 10), g, w)
                else -> if (random.nextBoolean()) { val a = rng(100, 800); add(a, rng(10, 999 - a), g, w) } else sub(999, g, w)
            }
            4 -> when (random.nextInt(3)) {
                0 -> mul(rng(12, 99), rng(2, 9), g, w)
                1 -> order(g, w)
                else -> fraction(g, w)
            }
            5 -> when (random.nextInt(4)) {
                0 -> negative(g, w)
                1 -> percent(g, w)
                2 -> square(g, w)
                else -> div(rng(11, 15), rng(6, 12), g, w)
            }
            6 -> equation1(g, w)
            else -> if (random.nextInt(3) == 0) equation1(g, w) else equation2(g, w)
        }
    }

    // ------------------------------------------------------------------ arithmetic

    private fun add(a: Int, b: Int, g: Int, w: Words) = arith("$a + $b", "$a ${w.plus} $b", a + b, g, w)

    private fun sub(max: Int, g: Int, w: Words): MathTask {
        val a = rng(if (max <= 20) 5 else 20, max)
        val b = rng(1, a)
        return arith("$a − $b", "$a ${w.minus} $b", a - b, g, w)
    }

    private fun mul(a: Int, b: Int, g: Int, w: Words) = arith("$a × $b", "$a ${w.times} $b", a * b, g, w)

    private fun div(b: Int, q: Int, g: Int, w: Words) = arith("${b * q} ${w.divSign} $b", "${b * q} ${w.dividedBy} $b", q, g, w)

    private fun arith(expr: String, words: String, answer: Int, g: Int, w: Words) =
        MathTask("$expr = ?", w.howMuch, w.spokenQuestion(words), answer, "$expr = $answer", g)

    private fun order(g: Int, w: Words): MathTask {
        val a = rng(2, 20)
        val b = rng(2, 9)
        val c = rng(2, 9)
        return when (random.nextInt(3)) {
            0 -> MathTask(
                "$a + $b × $c = ?", w.howMuch, w.spokenQuestion("$a ${w.plus} $b ${w.times} $c"), a + b * c,
                "$b × $c = ${b * c}, $a + ${b * c} = ${a + b * c}. ${w.multiplyFirst}", g,
            )
            1 -> MathTask(
                "($a + $b) × $c = ?", w.howMuch, w.spokenQuestion(w.bracketsSum(a, b, c)), (a + b) * c,
                "$a + $b = ${a + b}, ${a + b} × $c = ${(a + b) * c}. ${w.bracketsFirst}", g,
            )
            else -> {
                val p = b * c
                val big = p + rng(1, 30)
                MathTask(
                    "$big − $b × $c = ?", w.howMuch, w.spokenQuestion("$big ${w.minus} $b ${w.times} $c"), big - p,
                    "$b × $c = $p, $big − $p = ${big - p}. ${w.multiplyFirst}", g,
                )
            }
        }
    }

    private fun fraction(g: Int, w: Words): MathTask {
        val d = listOf(2, 3, 4, 5, 10).random(random)
        val n = d * rng(2, 12)
        return MathTask(
            "1/$d ${w.of} $n = ?", w.howMuch, w.spokenQuestion(w.fractionWords(d, n)), n / d,
            "$n ${w.divSign} $d = ${n / d}", g,
        )
    }

    private fun negative(g: Int, w: Words): MathTask {
        val a = rng(1, 20)
        val b = rng(a + 1, a + 20)
        return if (random.nextBoolean()) {
            arith("$a − $b", "$a ${w.minus} $b", a - b, g, w)
        } else {
            val x = rng(2, 15)
            MathTask("−$x + $b = ?", w.howMuch, w.spokenQuestion("${w.minus} $x ${w.plus} $b"), b - x, "−$x + $b = ${b - x}", g)
        }
    }

    private fun percent(g: Int, w: Words): MathTask {
        val p = listOf(10, 20, 25, 50).random(random)
        val step = 100 / p
        val result = rng(1, 12) * (if (p == 25) 1 else 2)
        val n = result * step
        return MathTask(
            "$p% ${w.of} $n = ?", w.howMuch, w.spokenQuestion("$p ${w.percentOf} $n"), result,
            "$n ${w.divSign} $step = $result", g,
        )
    }

    private fun square(g: Int, w: Words): MathTask {
        val n = rng(2, 15)
        return MathTask("$n² = ?", w.howMuch, w.spokenQuestion(w.squared(n)), n * n, "$n × $n = ${n * n}", g)
    }

    // ------------------------------------------------------------------ algebra

    private fun equation1(g: Int, w: Words): MathTask {
        val x = rng(1, 15)
        val a = rng(2, 9)
        return when (random.nextInt(5)) {
            0 -> { val b = x + a; eq("x + $a = $b", "x ${w.plus} $a ${w.equals} $b", x, "x = $b − $a = $x", g, w) }
            1 -> {
                val c = rng(1, 9)
                val y = c + rng(1, 15)
                eq("x − $c = ${y - c}", "x ${w.minus} $c ${w.equals} ${y - c}", y, "x = ${y - c} + $c = $y", g, w)
            }
            2 -> eq("${a}x = ${a * x}", "$a x ${w.equals} ${a * x}", x, "x = ${a * x} ${w.divSign} $a = $x", g, w)
            3 -> eq("x ${w.divSign} $a = $x", "x ${w.dividedBy} $a ${w.equals} $x", a * x, "x = $x × $a = ${a * x}", g, w)
            else -> {
                val b = rng(1, 20)
                val c = a * x + b
                eq("${a}x + $b = $c", "$a x ${w.plus} $b ${w.equals} $c", x, "${a}x = $c − $b = ${c - b}, x = ${c - b} ${w.divSign} $a = $x", g, w)
            }
        }
    }

    private fun equation2(g: Int, w: Words): MathTask {
        val x = rng(-9, 12).let { if (it == 0) 3 else it }
        return when (random.nextInt(4)) {
            0 -> {
                val c = rng(1, 5)
                val a = c + rng(1, 5)
                val b = rng(-10, 10).let { if (it == 0) 5 else it }
                val d = a * x + b - c * x
                eq(
                    "${linear(a, b)} = ${linear(c, d)}", "${w.linearWords(a, b)} ${w.equals} ${w.linearWords(c, d)}", x,
                    "${coef(a)} − ${coef(c)} = ${withConst(d, -b)}, ${coef(a - c)} = ${d - b}, x = $x", g, w,
                )
            }
            1 -> {
                val a = rng(2, 6)
                val b = rng(1, 9)
                val xx = abs(x)
                val c = a * (xx + b)
                eq("$a(x + $b) = $c", w.bracketsEq(a, b, c), xx, "x + $b = $c ${w.divSign} $a = ${xx + b}, x = $xx", g, w)
            }
            2 -> {
                val n = rng(2, 12)
                eq("x² = ${n * n}, x > 0", w.squareEq(n * n), n, "$n × $n = ${n * n}, x = $n", g, w)
            }
            else -> {
                val n = rng(2, 5)
                eq("x³ = ${n * n * n}", w.cubeEq(n * n * n), n, "$n × $n × $n = ${n * n * n}, x = $n", g, w)
            }
        }
    }

    private fun eq(display: String, words: String, answer: Int, explanation: String, g: Int, w: Words) =
        MathTask(display, "x = ?", w.findX(words), answer, explanation, g, algebra = true)

    // ------------------------------------------------------------------ story problems

    private fun story(g: Int, w: Words): MathTask {
        val name = w.names.random(random)
        val (objBg, objEn) = OBJECTS.random(random)
        val o = w.lang.pick(objBg, objEn)
        val (text, answer, explanation) = when {
            g == 1 && random.nextBoolean() -> { val a = rng(2, 10); val b = rng(2, 10); Triple(w.storyAdd(name, a, b, o), a + b, "$a + $b = ${a + b}") }
            g == 1 -> { val a = rng(5, 15); val b = rng(2, a - 1); Triple(w.storySub(name, a, b, o), a - b, "$a − $b = ${a - b}") }
            g == 2 && random.nextBoolean() -> { val a = rng(20, 60); val b = rng(5, 39); Triple(w.storyAdd(name, a, b, o), a + b, "$a + $b = ${a + b}") }
            g == 2 -> { val a = rng(2, 5); val b = rng(2, 10); Triple(w.storyBoxes(a, b, o), a * b, "$a × $b = ${a * b}") }
            g == 3 && random.nextBoolean() -> { val a = rng(3, 9); val b = rng(3, 9); Triple(w.storyBoxes(a, b, o), a * b, "$a × $b = ${a * b}") }
            g == 3 -> { val kids = rng(2, 8); val each = rng(2, 9); Triple(w.storyShare(kids * each, kids, o), each, "${kids * each} ${w.divSign} $kids = $each") }
            random.nextBoolean() -> { val n = rng(2, 9); val price = rng(2, 9); Triple(w.storyShop(name, n, price), n * price, "$n × $price = ${n * price}") }
            else -> { val a = rng(15, 40); val off = rng(3, 12); val on = rng(2, 12); Triple(w.storyBus(a, off, on), a - off + on, "$a − $off + $on = ${a - off + on}") }
        }
        return MathTask(text, w.answerPrompt, text, answer, explanation, g, story = true)
    }

    private fun rng(from: Int, to: Int) = if (to <= from) from else random.nextInt(from, to + 1)

    /** Text of the task in one language. */
    private class Words(val lang: Lang) {
        private fun t(bg: String, en: String) = lang.pick(bg, en)
        val plus = t("плюс", "plus")
        val minus = t("минус", "minus")
        val times = t("по", "times")
        val dividedBy = t("делено на", "divided by")
        val equals = t("е равно на", "equals")
        val divSign = t(":", "÷")
        val of = t("от", "of")
        val percentOf = t("процента от", "percent of")
        val howMuch = t("Колко е?", "What is it?")
        val answerPrompt = t("Отговор:", "Answer:")
        val multiplyFirst = t("Първо умножението, после събирането и изваждането.", "Multiply first, then add or subtract.")
        val bracketsFirst = t("Първо сметката в скобите.", "Work out the brackets first.")
        val names = if (lang == Lang.BG) listOf("Мария", "Иван", "Ани", "Петър", "Ели", "Митко", "Ния", "Боби")
        else listOf("Maria", "Ivan", "Anna", "Peter", "Ellie", "Tom", "Nia", "Ben")

        fun spokenQuestion(words: String) = t("Колко е $words?", "What is $words?")
        fun findX(words: String) = t("Намери хикс. ${words.replace("x", "хикс")}.", "Find x. $words.")
        fun linearWords(a: Int, b: Int): String {
            val ax = if (a == 1) "x" else "$a x"
            return when {
                b > 0 -> "$ax $plus $b"
                b < 0 -> "$ax $minus ${-b}"
                else -> ax
            }
        }
        fun bracketsSum(a: Int, b: Int, c: Int) = t("$a плюс $b, в скоби, по $c", "$a plus $b, in brackets, times $c")
        fun bracketsEq(a: Int, b: Int, c: Int) =
            t("$a по, скоба, x плюс $b, затваря скоба, е равно на $c", "$a times, bracket, x plus $b, close bracket, equals $c")
        fun squareEq(n: Int) = t("x на квадрат е равно на $n, а x е положително", "x squared equals $n, and x is positive")
        fun cubeEq(n: Int) = t("x на куб е равно на $n", "x cubed equals $n")
        fun squared(n: Int) = t("$n на квадрат", "$n squared")
        fun fractionWords(d: Int, n: Int) = t("една ${fractionBg(d)} от $n", "one ${fractionEn(d)} of $n")
        private fun fractionBg(d: Int) = when (d) { 2 -> "втора"; 3 -> "трета"; 4 -> "четвърт"; 5 -> "пета"; else -> "десета" }
        private fun fractionEn(d: Int) = when (d) { 2 -> "half"; 3 -> "third"; 4 -> "quarter"; 5 -> "fifth"; else -> "tenth" }

        fun storyAdd(n: String, a: Int, b: Int, o: String) =
            t("$n има $a $o. Получава още $b. Колко $o има сега?", "$n has $a $o and gets $b more. How many $o does $n have now?")
        fun storySub(n: String, a: Int, b: Int, o: String) =
            t("$n има $a $o и дава $b на приятел. Колко $o остават на $n?", "$n has $a $o and gives $b to a friend. How many $o does $n have left?")
        fun storyBoxes(boxes: Int, each: Int, o: String) =
            t("В една кутия има $each $o. Колко $o има в $boxes кутии?", "There are $each $o in one box. How many $o are there in $boxes boxes?")
        fun storyShare(total: Int, kids: Int, o: String) =
            t("$total $o се разделят поравно между $kids деца. Колко $o получава всяко дете?",
                "$total $o are shared equally between $kids children. How many $o does each child get?")
        fun storyShop(n: String, count: Int, price: Int) =
            t("$n купува $count тетрадки по $price евро. Колко евро плаща?", "$n buys $count notebooks at $price euros each. How many euros does $n pay?")
        fun storyBus(on: Int, off: Int, more: Int) =
            t("В автобуса има $on души. На спирката слизат $off, а се качват $more. Колко души има сега в автобуса?",
                "There are $on people on the bus. At the stop $off get off and $more get on. How many people are on the bus now?")
    }

    companion object {
        const val MAX_GRADE = 7
        private const val STORY_SHARE = 0.3f

        /** (Bulgarian form after a number, English plural). */
        private val OBJECTS = listOf(
            "ябълки" to "apples", "бонбона" to "sweets", "балона" to "balloons", "книги" to "books", "молива" to "pencils",
            "стикера" to "stickers", "круши" to "pears", "топки" to "balls", "звездички" to "stars", "мъфина" to "muffins",
        )

        fun gradeLabel(grade: Int, lang: Lang) = lang.pick("$grade клас", "Year $grade")

        /** School year to start with: the one the child told ZnaiKo, else guessed from the age, else year 2. */
        fun gradeFor(grade: Int?, age: Int?): Int = when {
            grade != null -> grade.coerceIn(1, MAX_GRADE)
            age != null -> (age - 6).coerceIn(1, MAX_GRADE)
            else -> 2
        }

        private fun coef(a: Int) = when (a) { 1 -> "x"; -1 -> "−x"; else -> "${a}x" }
        private fun linear(a: Int, b: Int) = withConst(coef(a), b)
        private fun withConst(head: Any, b: Int) = when {
            b > 0 -> "$head + $b"
            b < 0 -> "$head − ${-b}"
            else -> "$head"
        }
    }
}

/** Running score of a maths or trivia round. */
data class QuizScore(val correct: Int = 0, val total: Int = 0, val streak: Int = 0, val bestStreak: Int = 0) {
    fun answer(right: Boolean): QuizScore {
        val s = if (right) streak + 1 else 0
        return QuizScore(correct + (if (right) 1 else 0), total + 1, s, maxOf(bestStreak, s))
    }
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}
