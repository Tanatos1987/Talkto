package com.talkto.core.quiz

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import org.junit.Test

class SmartQuizTest {

    @Test fun `parses complete questions and drops broken ones`() {
        val text = """
            Q: Кое животно е най-голямо на Земята?
            R: Синият кит
            W: Слонът
            W: Жирафът
            W: Белата акула
            F: Сърцето на синия кит е голямо колкото кола.

            Q: Колко крака има паякът?
            R: Осем
            W: Шест

            **Q:** Какъв цвят е небето в ясен ден?
            R: Синьо
            W: Зелено
            W: Червено
            W: Жълто
        """.trimIndent()
        val qs = SmartQuiz.parse(text, Lang.BG)
        assertThat(qs).hasSize(2)
        assertThat(qs[0].question(Lang.BG)).isEqualTo("Кое животно е най-голямо на Земята?")
        assertThat(qs[0].answers(Lang.BG).first()).isEqualTo("Синият кит")
        assertThat(qs[0].fact(Lang.BG)).contains("кола")
        assertThat(qs[1].answers(Lang.BG)).containsExactly("Синьо", "Зелено", "Червено", "Жълто").inOrder()
    }

    @Test fun `duplicate answers make a question unusable`() {
        val text = "Q: 2+2?\nR: 4\nW: 4\nW: 5\nW: 6\n"
        assertThat(SmartQuiz.parse(text, Lang.EN)).isEmpty()
    }

    @Test fun `maths moves up after five right and down after two missed`() {
        assertThat(AdaptiveGrade.next(2, rightRun = 5, wrongRun = 0)).isEqualTo(3)
        assertThat(AdaptiveGrade.next(2, rightRun = 4, wrongRun = 0)).isNull()
        assertThat(AdaptiveGrade.next(2, rightRun = 0, wrongRun = 2)).isEqualTo(1)
        assertThat(AdaptiveGrade.next(1, rightRun = 0, wrongRun = 3)).isNull()
        assertThat(AdaptiveGrade.next(MathTasks.MAX_GRADE, rightRun = 9, wrongRun = 0)).isNull()
    }

    @Test fun `the question of the day stays the same all day`() {
        assertThat(DailyQuestion.of(20_000).id).isEqualTo(DailyQuestion.of(20_000).id)
        val week = (20_000L until 20_007L).map { DailyQuestion.of(it).id }.toSet()
        assertThat(week.size).isAtLeast(5)
    }
}
