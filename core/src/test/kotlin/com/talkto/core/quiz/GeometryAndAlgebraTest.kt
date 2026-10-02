package com.talkto.core.quiz

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import org.junit.Test
import kotlin.random.Random

class GeometryAndAlgebraTest {
    private val tasks = MathTasks(Random(7))

    @Test fun `geometry has a figure and a whole positive answer for every year`() {
        for (g in 1..MathTasks.MAX_GRADE) repeat(60) {
            for (lang in Lang.entries) {
                val t = tasks.next(g, lang, MathTopic.GEOMETRY)
                assertThat(t.geometry).isTrue()
                assertThat(t.figure).isNotNull()
                assertThat(t.answer).isGreaterThan(0)
                assertThat(t.check(t.answer.toString())).isTrue()
                if (lang == Lang.BG) assertThat(t.spoken).doesNotContainMatch("[A-Za-z]{2,}")
            }
        }
    }

    @Test fun `algebra answers are right`() {
        for (g in 1..MathTasks.MAX_GRADE) repeat(80) {
            val t = tasks.next(g, Lang.BG, MathTopic.ALGEBRA)
            assertThat(t.algebra).isTrue()
            assertThat(t.spoken).doesNotContain("x")
            assertThat(t.explanation).contains(t.answer.toString())
        }
    }

    @Test fun `known answers`() {
        // A right triangle with legs 3 and 4 always has the hypotenuse 5, whatever triple is drawn; spot check the rule.
        repeat(100) {
            val t = tasks.next(7, Lang.BG, MathTopic.GEOMETRY)
            if (t.figure?.shape == Figure.Shape.RIGHT && t.figure!!.labels[2] == "?") {
                val a = t.figure!!.labels[0].toInt(); val b = t.figure!!.labels[1].toInt()
                assertThat(t.answer * t.answer).isEqualTo(a * a + b * b)
            }
            if (t.figure?.shape == Figure.Shape.ANGLES && t.figure!!.labels.count { it == "?" } == 1) {
                val known = t.figure!!.labels.filter { it != "?" }.sumOf { it.removeSuffix("°").toInt() }
                assertThat(known + t.answer).isEqualTo(180)
            }
        }
    }

    @Test fun `mixed years 3 and up sometimes bring geometry`() {
        val seen = (1..200).map { tasks.next(4, Lang.BG, MathTopic.MIXED) }
        assertThat(seen.any { it.geometry }).isTrue()
        assertThat(seen.count { it.geometry }).isLessThan(100)
    }
}
