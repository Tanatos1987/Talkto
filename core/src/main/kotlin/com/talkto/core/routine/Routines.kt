package com.talkto.core.routine

import com.talkto.core.i18n.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

enum class RoutineKind { MORNING, EVENING }

/**
 * ZnaiKo's morning and evening words: a friendly start to the day (what day it is, teeth, breakfast, school bag) and
 * the bedtime routine (teeth, pyjamas, the bag for tomorrow on school nights). Times are minutes after midnight;
 * [OFF] switches a routine off.
 */
object Routines {
    const val OFF = -1
    val MORNING_CHOICES = listOf(OFF, 6 * 60 + 45, 7 * 60, 7 * 60 + 30, 8 * 60, 9 * 60)
    val EVENING_CHOICES = listOf(OFF, 19 * 60 + 30, 20 * 60, 20 * 60 + 30, 21 * 60)

    fun label(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /** The next time a routine at [minuteOfDay] is due: later today, or tomorrow when that time has passed. */
    fun nextAt(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        val today = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minuteOfDay.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private val DAYS_BG = listOf("понеделник", "вторник", "сряда", "четвъртък", "петък", "събота", "неделя")
    private val DAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun dayName(date: LocalDate, lang: Lang): String = (if (lang == Lang.BG) DAYS_BG else DAYS_EN)[date.dayOfWeek.value - 1]

    private fun weekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** What ZnaiKo says; [name] is the child's first name when known. */
    fun line(kind: RoutineKind, date: LocalDate, lang: Lang, name: String? = null): String {
        val hi = name?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
        val day = dayName(date, lang)
        return when (kind) {
            RoutineKind.MORNING -> if (weekend(date)) {
                lang.pick(
                    "Добро утро$hi! Днес е $day, почивен ден. Измий си зъбките, закуси хубаво и да си измислим нещо весело!",
                    "Good morning$hi! It's $day, a day off. Brush your teeth, have a good breakfast, and let's think of something fun!",
                )
            } else {
                lang.pick(
                    "Добро утро$hi! Днес е $day. Зъбки, закуска и не забравяй раницата. Хубав ден!",
                    "Good morning$hi! It's $day. Teeth, breakfast, and don't forget your school bag. Have a lovely day!",
                )
            }
            RoutineKind.EVENING -> {
                // Sunday to Thursday evenings come before a school day.
                val schoolTomorrow = !weekend(date.plusDays(1))
                val bag = if (schoolTomorrow) lang.pick(" Приготви си раницата за утре.", " Get your school bag ready for tomorrow.") else ""
                lang.pick(
                    "Време е за вечерните неща$hi: зъбки, пижама и приказка.$bag Лека нощ!",
                    "Time for the evening things$hi: teeth, pyjamas and a story.$bag Good night!",
                )
            }
        }
    }
}
