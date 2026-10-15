package com.talkto.core.routine

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutinesTest {
    private val zone = ZoneId.of("Europe/Sofia")

    @Test fun `the next time is later today or tomorrow`() {
        val morning = ZonedDateTime.of(2026, 10, 6, 6, 0, 0, 0, zone)
        assertThat(Routines.nextAt(morning, 7 * 60 + 30)).isEqualTo(ZonedDateTime.of(2026, 10, 6, 7, 30, 0, 0, zone))
        val late = ZonedDateTime.of(2026, 10, 6, 21, 0, 0, 0, zone)
        assertThat(Routines.nextAt(late, 7 * 60 + 30)).isEqualTo(ZonedDateTime.of(2026, 10, 7, 7, 30, 0, 0, zone))
        assertThat(Routines.label(7 * 60 + 5)).isEqualTo("07:05")
    }

    @Test fun `lines know the day, the weekend and school nights`() {
        val tuesday = LocalDate.of(2026, 10, 6)
        assertThat(Routines.line(RoutineKind.MORNING, tuesday, Lang.BG, "Ана")).contains("Добро утро, Ана! Днес е вторник.")
        assertThat(Routines.line(RoutineKind.MORNING, tuesday.plusDays(4), Lang.EN)).contains("Saturday, a day off")
        assertThat(Routines.line(RoutineKind.EVENING, tuesday.plusDays(5), Lang.BG)).contains("раницата") // Sunday night
        assertThat(Routines.line(RoutineKind.EVENING, tuesday.plusDays(3), Lang.BG)).doesNotContain("раницата") // Friday night
    }
}
