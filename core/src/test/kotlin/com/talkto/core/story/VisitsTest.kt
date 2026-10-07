package com.talkto.core.story

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.parent.DayActivity
import org.junit.Test

class VisitsTest {

    @Test fun `ten chapters in order, each friend visits and every text has both languages`() {
        assertThat(Visits.CHAPTERS.map { it.number }).isEqualTo((1..10).toList())
        assertThat(Visits.CHAPTERS.map { it.friend }.toSet()).isEqualTo(Friend.entries.toSet())
        assertThat(Visits.CHAPTERS.map { it.challenge }.toSet()).isEqualTo(Challenge.entries.toSet())
        // UTF-8 Bulgarian read as cp1251 ("Р‘СЂР°") must never end up in a chapter.
        val mojibake = Regex("[РС][ЂЃ‚ѓ„…†‡€‰Љ‹ЊЌЋЏђ‘’“”•–—™љ›њќћџ°ѕ]")
        Visits.CHAPTERS.forEach { ch ->
            Lang.entries.forEach { l ->
                listOf(ch.text(l), ch.thanks(l), ch.title(l), ch.challenge.button(l), ch.friend.knock(l)).forEach { s ->
                    assertThat(s).isNotEmpty()
                    assertThat(mojibake.containsMatchIn(s)).isFalse()
                }
            }
            assertThat(ch.art).isNotEmpty()
        }
    }

    @Test fun `one friend a day until the book is finished`() {
        assertThat(Visits.due(0, 0, 100)).isTrue()
        assertThat(Visits.due(3, 100, 100)).isFalse()
        assertThat(Visits.due(3, 100, 101)).isTrue()
        assertThat(Visits.due(Visits.CHAPTERS.size, 0, 500)).isFalse()
        assertThat(Visits.next(0)?.number).isEqualTo(1)
        assertThat(Visits.next(9)?.number).isEqualTo(10)
        assertThat(Visits.next(10)).isNull()
        assertThat(Visits.done(0)).isEmpty()
        assertThat(Visits.done(3).map { it.number }).isEqualTo(listOf(1, 2, 3))
        assertThat(Visits.done(99)).hasSize(10)
        assertThat(Visits.done(-1)).isEmpty()
    }

    @Test fun `the help counts only what was done after saying yes`() {
        val before = DayActivity(day = 1, mathRight = 4)
        val start = Challenge.MATHS.count(before)
        assertThat(Challenge.MATHS.done(start, before)).isFalse()
        assertThat(Challenge.MATHS.done(start, before.copy(mathRight = 6))).isFalse()
        assertThat(Challenge.MATHS.done(start, before.copy(mathRight = 7))).isTrue()

        assertThat(Challenge.TALE.done(0, DayActivity(day = 1, stories = 1))).isTrue()
        assertThat(Challenge.LESSON.done(2, DayActivity(day = 1, lessons = 2))).isFalse()
        // The fox is happy with any answer, right or wrong.
        assertThat(Challenge.RIDDLE.done(0, DayActivity(day = 1, triviaWrong = 1))).isTrue()
    }
}
