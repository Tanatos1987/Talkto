package com.talkto.core.quiz

import com.talkto.core.i18n.Lang
import kotlin.random.Random

/** Which kind of maths: the mix of the school year, only algebra, or only geometry. */
enum class MathTopic(val bg: String, val en: String, val emoji: String) {
    MIXED("Смятане", "Sums", "🔢"),
    ALGEBRA("Алгебра", "Algebra", "𝑥"),
    GEOMETRY("Геометрия", "Geometry", "📐"),
    ;

    fun label(lang: Lang) = "$emoji " + lang.pick(bg, en)
}

/**
 * The figure drawn next to a geometry task. [labels] are the texts written on it, "?" for the one to find:
 * RECT (width, height), SQUARE (side), TRIANGLE (three sides), RIGHT (two legs, hypotenuse),
 * ANGLES (three angles), CIRCLE (radius), CUBE (edge), BOX (length, width, height), POLYGON ([sides] corners),
 * STRAIGHT (two angles on a straight line).
 */
data class Figure(val shape: Shape, val labels: List<String> = emptyList(), val sides: Int = 0) {
    enum class Shape { RECT, SQUARE, TRIANGLE, RIGHT, ANGLES, CIRCLE, CUBE, BOX, POLYGON, STRAIGHT }
}

/** Geometry for school years 1 to 7, with whole-number answers and a figure to look at. */
internal class GeometryTasks(private val random: Random) {

    fun next(grade: Int, lang: Lang): MathTask {
        val t = { bg: String, en: String -> lang.pick(bg, en) }
        val cm = t("см", "cm")
        return when (grade) {
            1, 2 -> if (grade == 1 || random.nextBoolean()) corners(grade, t) else squarePerimeter(grade, t, cm, small = true)
            3 -> when (random.nextInt(3)) {
                0 -> rectPerimeter(grade, t, cm)
                1 -> trianglePerimeter(grade, t, cm)
                else -> squarePerimeter(grade, t, cm, small = false)
            }
            4 -> when (random.nextInt(3)) {
                0 -> rectArea(grade, t, cm)
                1 -> squareArea(grade, t, cm)
                else -> sideFromPerimeter(grade, t, cm)
            }
            5 -> when (random.nextInt(3)) {
                0 -> thirdAngle(grade, t)
                1 -> triangleArea(grade, t, cm)
                else -> boxVolume(grade, t, cm)
            }
            6 -> when (random.nextInt(3)) {
                0 -> straightAngle(grade, t)
                1 -> diameter(grade, t, cm)
                else -> cubeSurface(grade, t, cm)
            }
            else -> when (random.nextInt(3)) {
                0 -> hypotenuse(grade, t, cm)
                1 -> leg(grade, t, cm)
                else -> isoscelesAngle(grade, t)
            }
        }
    }

    private fun rng(from: Int, to: Int) = random.nextInt(from, to + 1)

    private fun task(q: String, spoken: String, answer: Int, explanation: String, grade: Int, figure: Figure, prompt: String) =
        MathTask(q, prompt, sayable(spoken), answer, explanation, grade, geometry = true, figure = figure)

    /** "см²" and "°" in words, so the voice never meets a symbol. */
    private fun sayable(s: String) = s
        .replace("см²", "квадратни сантиметри").replace("см³", "кубични сантиметри")
        .replace("cm²", "square centimetres").replace("cm³", "cubic centimetres")
        .replace(Regex("(\\d+)°")) { "${it.groupValues[1]} " + if (s.any { c -> c in 'а'..'я' }) "градуса" else "degrees" }

    private fun corners(g: Int, t: (String, String) -> String): MathTask {
        val (n, bg, en) = listOf(Triple(3, "триъгълникът", "a triangle"), Triple(4, "квадратът", "a square"), Triple(5, "петоъгълникът", "a pentagon"), Triple(6, "шестоъгълникът", "a hexagon")).random(random)
        val sides = random.nextBoolean()
        val q = if (sides) t("Колко страни има $bg?", "How many sides does $en have?") else t("Колко ъгъла има $bg?", "How many corners does $en have?")
        return task(q, q, n, t("Преброй ги: $n.", "Count them: $n."), g, Figure(Figure.Shape.POLYGON, sides = n), t("Отговор:", "Answer:"))
    }

    private fun squarePerimeter(g: Int, t: (String, String) -> String, cm: String, small: Boolean): MathTask {
        val a = if (small) rng(2, 5) else rng(3, 12)
        val q = t("Страната на квадрата е $a $cm. Колко е обиколката му?", "The side of the square is $a $cm. What is its perimeter?")
        return task(q, q, 4 * a, "P = 4 × $a = ${4 * a} $cm", g, Figure(Figure.Shape.SQUARE, listOf("$a")), "P =")
    }

    private fun rectPerimeter(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(4, 15); val b = rng(2, a - 1)
        val q = t("Правоъгълникът е $a $cm на $b $cm. Колко е обиколката му?", "The rectangle is $a $cm by $b $cm. What is its perimeter?")
        return task(q, q, 2 * (a + b), "P = 2 × ($a + $b) = ${2 * (a + b)} $cm", g, Figure(Figure.Shape.RECT, listOf("$a", "$b")), "P =")
    }

    private fun trianglePerimeter(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(3, 9); val b = rng(3, 9); val c = rng(maxOf(2, kotlin.math.abs(a - b) + 1), a + b - 1)
        val q = t("Страните на триъгълника са $a, $b и $c $cm. Колко е обиколката му?", "The sides of the triangle are $a, $b and $c $cm. What is its perimeter?")
        return task(q, q, a + b + c, "P = $a + $b + $c = ${a + b + c} $cm", g, Figure(Figure.Shape.TRIANGLE, listOf("$a", "$b", "$c")), "P =")
    }

    private fun rectArea(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(3, 12); val b = rng(2, 9)
        val q = t("Правоъгълникът е $a $cm на $b $cm. Колко е лицето му в $cm²?", "The rectangle is $a $cm by $b $cm. What is its area in $cm²?")
        return task(q, q, a * b, "S = $a × $b = ${a * b} $cm²", g, Figure(Figure.Shape.RECT, listOf("$a", "$b")), "S =")
    }

    private fun squareArea(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(2, 12)
        val q = t("Страната на квадрата е $a $cm. Колко е лицето му в $cm²?", "The side of the square is $a $cm. What is its area in $cm²?")
        return task(q, q, a * a, "S = $a × $a = ${a * a} $cm²", g, Figure(Figure.Shape.SQUARE, listOf("$a")), "S =")
    }

    private fun sideFromPerimeter(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(3, 15)
        val q = t("Обиколката на квадрата е ${4 * a} $cm. Колко е страната му?", "The perimeter of the square is ${4 * a} $cm. How long is its side?")
        return task(q, q, a, "a = ${4 * a} : 4 = $a $cm", g, Figure(Figure.Shape.SQUARE, listOf("?")), "a =")
    }

    private fun thirdAngle(g: Int, t: (String, String) -> String): MathTask {
        val a = rng(3, 10) * 10; val b = rng(2, (170 - a) / 10) * 10; val c = 180 - a - b
        val q = t("Два ъгъла на триъгълник са $a° и $b°. Колко градуса е третият?", "Two angles of a triangle are $a° and $b°. How many degrees is the third?")
        return task(q, q, c, t("Сборът на ъглите е 180°: 180 − $a − $b = $c°", "The angles add up to 180°: 180 − $a − $b = $c°"), g, Figure(Figure.Shape.ANGLES, listOf("$a°", "$b°", "?")), "γ =")
    }

    private fun triangleArea(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(2, 10) * 2; val h = rng(2, 9)
        val q = t("Основата на триъгълника е $a $cm, а височината към нея е $h $cm. Колко е лицето му?", "A triangle has a base of $a $cm and a height of $h $cm. What is its area?")
        return task(q, q, a * h / 2, "S = $a × $h : 2 = ${a * h / 2} $cm²", g, Figure(Figure.Shape.TRIANGLE, listOf("$a", "h = $h", "")), "S =")
    }

    private fun boxVolume(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(2, 8); val b = rng(2, 6); val c = rng(2, 5)
        val q = t("Кутията е $a на $b на $c $cm. Колко е обемът ѝ в $cm³?", "The box is $a by $b by $c $cm. What is its volume in $cm³?")
        return task(q, q, a * b * c, "V = $a × $b × $c = ${a * b * c} $cm³", g, Figure(Figure.Shape.BOX, listOf("$a", "$b", "$c")), "V =")
    }

    private fun straightAngle(g: Int, t: (String, String) -> String): MathTask {
        val a = rng(2, 16) * 10
        val q = t("Два съседни ъгъла лежат на права линия. Единият е $a°. Колко е другият?", "Two angles lie side by side on a straight line. One is $a°. What is the other?")
        return task(q, q, 180 - a, "180 − $a = ${180 - a}°", g, Figure(Figure.Shape.STRAIGHT, listOf("$a°", "?")), "β =")
    }

    private fun diameter(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val r = rng(2, 20)
        val q = t("Радиусът на кръга е $r $cm. Колко е диаметърът му?", "The radius of the circle is $r $cm. What is its diameter?")
        return task(q, q, 2 * r, "d = 2 × $r = ${2 * r} $cm", g, Figure(Figure.Shape.CIRCLE, listOf("r = $r")), "d =")
    }

    private fun cubeSurface(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val a = rng(2, 7)
        val q = t("Ръбът на куба е $a $cm. Колко е лицето на повърхнината му в $cm²?", "The edge of the cube is $a $cm. What is its surface area in $cm²?")
        return task(q, q, 6 * a * a, t("6 стени по $a × $a: 6 × ${a * a} = ${6 * a * a} $cm²", "6 faces of $a × $a: 6 × ${a * a} = ${6 * a * a} $cm²"), g, Figure(Figure.Shape.CUBE, listOf("$a")), "S =")
    }

    private val TRIPLES = listOf(Triple(3, 4, 5), Triple(6, 8, 10), Triple(5, 12, 13), Triple(8, 15, 17), Triple(9, 12, 15), Triple(12, 16, 20))

    private fun hypotenuse(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val (a, b, c) = TRIPLES.random(random)
        val q = t("Катетите на правоъгълен триъгълник са $a и $b $cm. Колко е хипотенузата?", "The legs of a right triangle are $a and $b $cm. How long is the hypotenuse?")
        return task(q, q, c, "c² = $a² + $b² = ${a * a} + ${b * b} = ${c * c}, c = $c $cm", g, Figure(Figure.Shape.RIGHT, listOf("$a", "$b", "?")), "c =")
    }

    private fun leg(g: Int, t: (String, String) -> String, cm: String): MathTask {
        val (a, b, c) = TRIPLES.random(random)
        val q = t("Хипотенузата е $c $cm, а единият катет е $a $cm. Колко е другият катет?", "The hypotenuse is $c $cm and one leg is $a $cm. How long is the other leg?")
        return task(q, q, b, "b² = $c² − $a² = ${c * c} − ${a * a} = ${b * b}, b = $b $cm", g, Figure(Figure.Shape.RIGHT, listOf("$a", "?", "$c")), "b =")
    }

    private fun isoscelesAngle(g: Int, t: (String, String) -> String): MathTask {
        val apex = rng(2, 16) * 10
        val base = (180 - apex) / 2
        val q = t("Равнобедрен триъгълник има ъгъл при върха $apex°. Колко градуса е всеки ъгъл при основата?", "An isosceles triangle has $apex° at the top. How many degrees is each base angle?")
        return task(q, q, base, "(180 − $apex) : 2 = $base°", g, Figure(Figure.Shape.ANGLES, listOf("?", "?", "$apex°")), "α =")
    }
}
