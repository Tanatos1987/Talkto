package com.talkto.core.parent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParentTest {

    @Test fun `pin hash matches only the same pin`() {
        val salt = ParentPin.newSalt()
        val stored = ParentPin.hash("2580", salt)
        assertThat(stored).doesNotContain("2580")
        assertThat(ParentPin.matches("2580", salt, stored)).isTrue()
        assertThat(ParentPin.matches("2581", salt, stored)).isFalse()
        assertThat(ParentPin.matches("2580", ParentPin.newSalt(), stored)).isFalse()
        assertThat(ParentPin.matches("2580", null, stored)).isFalse()
        assertThat(ParentPin.valid("12a4")).isFalse()
        assertThat(ParentPin.valid("12345")).isFalse()
    }

    @Test fun `activity is counted per day and the week fills empty days`() {
        var log = ActivityLog()
        log = log.record(100, Activity.MINUTE, 20).record(100, Activity.LESSON).record(102, Activity.MATH_RIGHT, 3)
        assertThat(log.on(100).minutes).isEqualTo(20)
        assertThat(log.on(101).active).isFalse()
        val week = log.week(102)
        assertThat(week.map { it.day }).containsExactly(96L, 97L, 98L, 99L, 100L, 101L, 102L).inOrder()
        val total = log.weekTotal(102)
        assertThat(total.minutes).isEqualTo(20)
        assertThat(total.lessons).isEqualTo(1)
        assertThat(total.answersRight).isEqualTo(3)
    }

    @Test fun `old days are forgotten`() {
        val log = ActivityLog().record(1, Activity.GAME).record(1L + ActivityLog.KEEP_DAYS + 5, Activity.GAME)
        assertThat(log.days.map { it.day }).containsExactly(1L + ActivityLog.KEEP_DAYS + 5)
    }

    @Test fun `screen time limit, warning and bonus`() {
        val today = DayActivity(10, minutes = 25)
        assertThat(ScreenTime.timeUp(0, today)).isFalse()
        assertThat(ScreenTime.left(0, today)).isNull()
        assertThat(ScreenTime.warnNow(30, today)).isTrue()
        assertThat(ScreenTime.timeUp(30, today.copy(minutes = 30))).isTrue()
        assertThat(ScreenTime.timeUp(30, today.copy(minutes = 30, bonusMinutes = 15))).isFalse()
        assertThat(ScreenTime.left(30, today.copy(minutes = 40, bonusMinutes = 15))).isEqualTo(5)
    }
}
