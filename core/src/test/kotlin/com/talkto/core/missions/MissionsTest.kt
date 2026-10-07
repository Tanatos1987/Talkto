package com.talkto.core.missions

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.parent.Activity
import com.talkto.core.parent.ActivityLog
import com.talkto.core.parent.DayActivity
import org.junit.Test
import java.time.LocalDate

class MissionsTest {

    @Test fun `three missions a day, one from each group, the same all day`() {
        val day = LocalDate.of(2026, 10, 7).toEpochDay()
        val missions = Missions.forDay(day)
        assertThat(missions).hasSize(3)
        assertThat(missions[0]).isAnyOf(MissionKind.LESSON, MissionKind.WORDS)
        assertThat(missions[1]).isAnyOf(MissionKind.MATHS, MissionKind.TRIVIA)
        assertThat(missions[2]).isAnyOf(MissionKind.STORY, MissionKind.GAME, MissionKind.CHAT)
        assertThat(Missions.forDay(day)).isEqualTo(missions)
        // Not the same every day.
        assertThat((0L until 14L).map { Missions.forDay(day + it) }.toSet().size).isGreaterThan(1)
    }

    @Test fun `a holiday brings its own mission`() {
        val day = LocalDate.of(2026, 5, 24).toEpochDay()
        assertThat(Missions.forDay(day, SeasonEvent.LETTERS_DAY)).contains(MissionKind.WORDS)
        assertThat(Missions.forDay(day, SeasonEvent.CHRISTMAS)).contains(MissionKind.STORY)
        assertThat(Missions.rewardTimes(SeasonEvent.CHRISTMAS)).isEqualTo(2)
        assertThat(Missions.rewardTimes(null)).isEqualTo(1)
    }

    @Test fun `missions count from the day's activity`() {
        val day = 20_000L
        var log = ActivityLog()
        repeat(4) { log = log.record(day, Activity.MATH_RIGHT) }
        val today = log.on(day)
        assertThat(MissionKind.MATHS.done(today)).isFalse()
        assertThat(MissionKind.MATHS.progress(today)).isEqualTo(4)
        log = log.record(day, Activity.MATH_RIGHT, 3)
        assertThat(MissionKind.MATHS.done(log.on(day))).isTrue()
        assertThat(MissionKind.MATHS.progress(log.on(day))).isEqualTo(5)
        val all = MissionsToday(day, null, listOf(MissionKind.MATHS, MissionKind.STORY), log.on(day), claimed = false)
        assertThat(all.allDone).isFalse()
        assertThat(all.done).containsExactly(MissionKind.MATHS)
        assertThat(log.missionsDone(day).on(day).missionsDone).isTrue()
    }

    @Test fun `orthodox easter dates`() {
        assertThat(SeasonEvent.orthodoxEaster(2025)).isEqualTo(LocalDate.of(2025, 4, 20))
        assertThat(SeasonEvent.orthodoxEaster(2026)).isEqualTo(LocalDate.of(2026, 4, 12))
        assertThat(SeasonEvent.orthodoxEaster(2027)).isEqualTo(LocalDate.of(2027, 5, 2))
    }

    @Test fun `holidays fall on their days`() {
        assertThat(SeasonEvent.on(LocalDate.of(2026, 3, 1))).isEqualTo(SeasonEvent.BABA_MARTA)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 4, 11))).isEqualTo(SeasonEvent.EASTER)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 4, 13))).isEqualTo(SeasonEvent.EASTER)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 4, 14))).isNull()
        assertThat(SeasonEvent.on(LocalDate.of(2026, 5, 24))).isEqualTo(SeasonEvent.LETTERS_DAY)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 6, 1))).isEqualTo(SeasonEvent.CHILDREN_DAY)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 9, 15))).isEqualTo(SeasonEvent.SCHOOL_START)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 12, 25))).isEqualTo(SeasonEvent.CHRISTMAS)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 12, 31))).isEqualTo(SeasonEvent.NEW_YEAR)
        assertThat(SeasonEvent.on(LocalDate.of(2027, 1, 1))).isEqualTo(SeasonEvent.NEW_YEAR)
        assertThat(SeasonEvent.on(LocalDate.of(2026, 10, 7))).isNull()
    }

    @Test fun `greetings name the child and mention the double coins`() {
        val line = SeasonEvent.CHILDREN_DAY.greeting(Lang.BG, "Мария")
        assertThat(line).startsWith("Мария, честит 1 юни")
        assertThat(line).contains("двойно")
        assertThat(SeasonEvent.CHRISTMAS.greeting(Lang.EN)).startsWith("Merry Christmas!")
        assertThat(Missions.doneLine(MissionKind.STORY, 0, Lang.BG)).contains("Всички")
        assertThat(Missions.doneLine(MissionKind.STORY, 2, Lang.EN)).contains("2 more")
    }

    @Test fun `old saved days read as missions not done`() {
        assertThat(DayActivity(1L).missionsDone).isFalse()
    }
}
