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
    val geometry: Boolean = false,
    /** The shape drawn for a geometry task. */
    val figure: Figure? = null,
) {
    /** The screen shows a sentence, not a short sum. */
    val wordy: Boolean get() = story || geometry || display.length > 22

    /** True or false for an answer that holds a number ("12", "минус 3", "twenty one"), null when it holds none. */
    fun check(input: String): Boolean? = NumberWords.parse(input)?.let { it == answer }
}

/**
 * Tasks for school years 1 to 7, and year 0 for children of 5-6 who cannot read yet:
 * 0: counting pictures, + and − to 10 with pictures, everything also said aloud; 1: + and − to 20; 2: to 100 and the 2-5 and 10 times tables; 3: the whole times table, exact division, to 1000;
 * 4: bigger products, order of operations, fractions of a number; 5: negative numbers, percentages, squares;
 * 6: first equations (x + a = b, a·x + b = c); 7: x on both sides, brackets, squares and cubes.
 * Years 1 to 4 also get story problems.
 */
class MathTasks(private val random: Random = Random.Default) {

    private val geometry = GeometryTasks(random)

    /** A task for [grade]; [algebraOnly] asks for algebra whatever the year. */
    fun next(grade: Int, lang: Lang, algebraOnly: Boolean = false): MathTask {
        if (!algebraOnly || grade < 1) return next(grade, lang, MathTopic.MIXED)
        val g = grade.coerceIn(1, MAX_GRADE)
        val w = Words(lang)
        return if (g >= 7 && random.nextBoolean()) equation2(g, w) else equation1(maxOf(g, 6), w)
    }

    /** A task for [grade] on [topic]. Algebra starts at year 4 level (letters for numbers), geometry fits every year. */
    fun next(grade: Int, lang: Lang, topic: MathTopic): MathTask {
        val g = grade.coerceIn(0, MAX_GRADE)
        val w = Words(lang)
        if (g == 0) return pictures(w)
        when (topic) {
            MathTopic.ALGEBRA -> return algebra(g, w)
            MathTopic.GEOMETRY -> return geometry.next(g, lang)
            MathTopic.MIXED -> if (g >= 3 && random.nextFloat() < GEOMETRY_SHARE) return geometry.next(g, lang)
        }
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

    // ------------------------------------------------------------------ year 0: pictures

    /** "How many?", "and two more" or "eats two": pictures instead of digits, numbers up to ten, the question said aloud. */
    private fun pictures(w: Words): MathTask {
        val (emoji, objBg, objEn) = PICTURES.random(random)
        val o = w.lang.pick(objBg, objEn)
        fun row(n: Int) = emoji.repeat(n)
        return when (random.nextInt(3)) {
            0 -> {
                val n = rng(2, 8)
                val counted = (1..n).joinToString(", ")
                MathTask(
                    "${row(n)}\n= ?", w.howMany, w.t("Колко $o виждаш?", "How many $o can you see?"), n,
                    w.t("Да ги преброим: $counted. Те са $n.", "Let's count them: $counted. That's $n."), 0,
                )
            }
            1 -> {
                val a = rng(1, 5)
                val b = rng(1, 10 - a).coerceAtMost(5)
                MathTask(
                    "${row(a)} + ${row(b)}\n= ?", w.howMany,
                    w.t("Тук има $a, идват още $b. Колко $o стават?", "Here are $a, and $b more come. How many $o is that?"),
                    a + b, "$a + $b = ${a + b}", 0,
                )
            }
            else -> {
                val a = rng(3, 8)
                val b = rng(1, a - 1).coerceAtMost(4)
                MathTask(
                    "${row(a)} − ${row(b)}\n= ?", w.howMany,
                    w.t("Имаш $a $o и $b си отиват. Колко остават?", "You have $a $o and $b go away. How many are left?"),
                    a - b, "$a − $b = ${a - b}", 0,
                )
            }
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

    private fun algebra(g: Int, w: Words): MathTask = when {
        g <= 5 -> if (random.nextBoolean()) substitute(g, w) else equation1(maxOf(g, 6), w)
        g == 6 -> when (random.nextInt(4)) {
            0 -> likeTerms(g, w)
            1 -> inequality(g, w)
            2 -> substitute(g, w)
            else -> equation1(g, w)
        }
        else -> when (random.nextInt(6)) {
            0 -> expand(g, w)
            1 -> system(g, w)
            2 -> binomial(g, w)
            3 -> power(g, w)
            4 -> equation1(g, w)
            else -> equation2(g, w)
        }
    }

    /** "2a + 3 при a = 4" */
    private fun substitute(g: Int, w: Words): MathTask {
        val a = rng(2, 9); val k = rng(2, 6); val b = rng(1, 12)
        return MathTask(
            w.t("${k}a + $b = ?\na = $a", "${k}a + $b = ?\na = $a"), w.howMuch,
            w.t("Колко е $k по а плюс $b, ако а е равно на $a?", "What is $k a plus $b, when a is $a?"),
            k * a + b, "$k × $a + $b = ${k * a} + $b = ${k * a + b}", g, algebra = true,
        )
    }

    /** "3x + 5x = ?x" */
    private fun likeTerms(g: Int, w: Words): MathTask {
        val a = rng(2, 9); val b = rng(2, 9); val minus = random.nextBoolean() && a != b
        val big = maxOf(a, b); val small = minOf(a, b)
        val display = if (minus) "${big}x − ${small}x = ?x" else "${a}x + ${b}x = ?x"
        val answer = if (minus) big - small else a + b
        val spoken = if (minus) w.t("$big хикс минус $small хикс е равно на колко хикс?", "$big x minus $small x equals how many x?")
        else w.t("$a хикс плюс $b хикс е равно на колко хикс?", "$a x plus $b x equals how many x?")
        return MathTask(display, "? =", spoken, answer, display.replace("?x", "${answer}x"), g, algebra = true)
    }

    /** "x + 3 < 10: the biggest whole x" */
    private fun inequality(g: Int, w: Words): MathTask {
        val a = rng(1, 9); val c = a + rng(2, 12)
        return MathTask(
            "x + $a < $c", w.t("Най-голямото цяло x:", "The biggest whole x:"),
            w.t("Кое е най-голямото цяло число хикс, за което хикс плюс $a е по-малко от $c?", "What is the biggest whole number x with x plus $a less than $c?"),
            c - a - 1, "x < $c − $a = ${c - a}, x = ${c - a - 1}", g, algebra = true,
        )
    }

    /** "2(x + 3) = 2x + ?" */
    private fun expand(g: Int, w: Words): MathTask {
        val k = rng(2, 9); val b = rng(1, 9)
        return MathTask(
            "$k(x + $b) = ${k}x + ?", "? =",
            w.t("$k по, скоба, хикс плюс $b, е равно на $k хикс плюс колко?", "$k times, bracket, x plus $b, equals $k x plus what?"),
            k * b, "$k × x + $k × $b = ${k}x + ${k * b}", g, algebra = true,
        )
    }

    /** "x + y = 10, x − y = 2" */
    private fun system(g: Int, w: Words): MathTask {
        val y = rng(1, 9); val x = y + rng(1, 9)
        return MathTask(
            "x + y = ${x + y}\nx − y = ${x - y}", "x =",
            w.t("Хикс плюс игрек е ${x + y}, а хикс минус игрек е ${x - y}. Колко е хикс?", "x plus y is ${x + y}, and x minus y is ${x - y}. What is x?"),
            x, w.t("Събери двете: 2x = ${2 * x}, x = $x, y = $y", "Add them: 2x = ${2 * x}, x = $x, y = $y"), g, algebra = true,
        )
    }

    /** "(x + 3)² = x² + ?x + 9" */
    private fun binomial(g: Int, w: Words): MathTask {
        val b = rng(2, 9)
        return MathTask(
            "(x + $b)² = x² + ?x + ${b * b}", "? =",
            w.t("Скоба хикс плюс $b на квадрат е хикс на квадрат плюс колко хикс плюс ${b * b}?", "x plus $b, squared, is x squared plus how many x plus ${b * b}?"),
            2 * b, "(x + $b)² = x² + 2 × $b × x + ${b * b}, 2 × $b = ${2 * b}", g, algebra = true,
        )
    }

    /** "2⁵", "(−3)²" */
    private fun power(g: Int, w: Words): MathTask {
        if (random.nextBoolean()) {
            val base = rng(2, 3); val e = if (base == 2) rng(3, 6) else rng(2, 4)
            var r = 1; repeat(e) { r *= base }
            val sup = "⁰¹²³⁴⁵⁶⁷⁸⁹"[e]
            return MathTask("$base$sup = ?", w.howMuch, w.t("Колко е $base на степен $e?", "What is $base to the power $e?"), r, List(e) { "$base" }.joinToString(" × ") + " = $r", g, algebra = true)
        }
        val x = rng(2, 9)
        return MathTask(
            "x² = ?\nx = −$x", w.howMuch, w.t("Колко е хикс на квадрат, ако хикс е минус $x?", "What is x squared when x is minus $x?"),
            x * x, "(−$x) × (−$x) = ${x * x}", g, algebra = true,
        )
    }

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
        fun t(bg: String, en: String) = lang.pick(bg, en)
        val plus = t("плюс", "plus")
        val minus = t("минус", "minus")
        val times = t("по", "times")
        val dividedBy = t("делено на", "divided by")
        val equals = t("е равно на", "equals")
        val divSign = t(":", "÷")
        val of = t("от", "of")
        val percentOf = t("процента от", "percent of")
        val howMuch = t("Колко е?", "What is it?")
        val howMany = t("Колко са?", "How many?")
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
        private const val GEOMETRY_SHARE = 0.15f

        /** (Bulgarian form after a number, English plural). */
        private val OBJECTS = listOf(
            "ябълки" to "apples", "бонбона" to "sweets", "балона" to "balloons", "книги" to "books", "молива" to "pencils",
            "стикера" to "stickers", "круши" to "pears", "топки" to "balls", "звездички" to "stars", "мъфина" to "muffins",
        )

        /** Pictures to count in year 0: (emoji, Bulgarian form after a number, English plural). */
        private val PICTURES = listOf(
            Triple("🍎", "ябълки", "apples"), Triple("⭐", "звездички", "stars"), Triple("🎈", "балона", "balloons"),
            Triple("🐟", "рибки", "fish"), Triple("🌸", "цветя", "flowers"), Triple("🍪", "бисквитки", "cookies"),
            Triple("🐥", "пиленца", "chicks"), Triple("🚗", "колички", "cars"),
        )

        fun gradeLabel(grade: Int, lang: Lang) = if (grade <= 0) lang.pick("🌱 Броим", "🌱 Counting") else lang.pick("$grade клас", "Year $grade")

        /** School year to start with: the one the child told ZnaiKo, else guessed from the age, else year 2; within [range]. */
        fun gradeFor(grade: Int?, age: Int?, range: IntRange = 1..MAX_GRADE): Int = when {
            grade != null -> grade
            age != null -> age - 6
            else -> 2
        }.coerceIn(range)

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
