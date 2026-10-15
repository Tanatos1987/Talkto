package com.talkto.core.age

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.LearningState
import com.talkto.core.learn.LessonPlanner
import com.talkto.core.learn.QuizKind
import com.talkto.core.learn.Step
import com.talkto.core.learn.Topic
import com.talkto.core.missions.MissionKind
import com.talkto.core.missions.Missions
import com.talkto.core.quiz.AdaptiveGrade
import com.talkto.core.quiz.MathTasks
import com.talkto.core.story.Challenge
import com.talkto.core.story.Visits
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class AgeTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test fun `the age counts from the first day of the birth month`() {
        assertThat(Birth(2021, 10).age(today)).isEqualTo(5)
        assertThat(Birth(2021, 11).age(today)).isEqualTo(4)
        assertThat(Birth(2021, 1).age(today)).isEqualTo(5)
        assertThat(Birth(2026, 1).age(today)).isEqualTo(0)
        assertThat(Birth(2021, 10).birthdayMonth(today)).isTrue()
        assertThat(Birth(2021, 11).birthdayMonth(today)).isFalse()
        assertThat(Birth.of(2021, 13)).isNull()
        assertThat(Birth.of(null, 3)).isNull()
        assertThat(Birth.years(today).first()).isEqualTo(2023)
        assertThat(Birth.years(today).last()).isEqualTo(2013)
    }

    @Test fun `groups follow kindergarten and school`() {
        assertThat((2..13).map { AgeGroup.of(it) }).containsExactly(
            AgeGroup.LITTLE, AgeGroup.LITTLE, AgeGroup.LITTLE,
            AgeGroup.PRESCHOOL, AgeGroup.PRESCHOOL,
            AgeGroup.JUNIOR, AgeGroup.JUNIOR, AgeGroup.JUNIOR,
            AgeGroup.SENIOR, AgeGroup.SENIOR, AgeGroup.SENIOR, AgeGroup.SENIOR,
        ).inOrder()
        // A child grows into the next group by the calendar alone.
        val birth = Birth(2021, 11)
        assertThat(birth.group(today)).isEqualTo(AgeGroup.LITTLE)
        assertThat(birth.group(today.withMonth(11))).isEqualTo(AgeGroup.PRESCHOOL)
    }

    @Test fun `a three-year-old gets pictures and voice, a parent can open more`() {
        val little = AgeRules.of(Birth(2023, 3), today)
        assertThat(little.group).isEqualTo(AgeGroup.LITTLE)
        assertThat(little.allows(Feature.TRIVIA)).isFalse()
        assertThat(little.allows(Feature.MATHS)).isFalse()
        assertThat(little.allows(Feature.TYPING)).isFalse()
        assertThat(little.reads).isFalse()
        assertThat(little.forOlder).containsExactlyElementsIn(Feature.entries)
        assertThat(little.copy(unlocked = setOf(Feature.CHESS)).allows(Feature.CHESS)).isTrue()
        assertThat(little.memoryPairs).isEqualTo(4)
        assertThat(little.claudeNote()).contains("cannot read")

        val preschool = AgeRules.of(Birth(2020, 5), today)
        assertThat(preschool.allows(Feature.MATHS)).isTrue()
        assertThat(preschool.allows(Feature.RIDDLES)).isTrue()
        assertThat(preschool.allows(Feature.TRIVIA)).isFalse()
        assertThat(preschool.mathGrades).isEqualTo(0..1)
        assertThat(preschool.startGrade).isEqualTo(0)

        val junior = AgeRules.of(Birth(2018, 9), today)
        assertThat(junior.forOlder).isEmpty()
        assertThat(junior.startGrade).isEqualTo(2)
        assertThat(junior.mathGrades).isEqualTo(1..4)

        // Before a parent enters the birth month nothing is limited, as before.
        assertThat(AgeRules.of(null, today).forOlder).isEmpty()
        assertThat(AgeRules.of(null, today).claudeNote()).isEmpty()
        assertThat(Feature.parse(listOf("CHESS", "GONE"))).containsExactly(Feature.CHESS)
    }

    @Test fun `missions only ask for what the age can do`() {
        val little = AgeRules(AgeGroup.LITTLE)
        val preschool = AgeRules(AgeGroup.PRESCHOOL)
        val days = (0L until 60L).map { today.toEpochDay() + it }
        days.flatMap { Missions.forDay(it, rules = little) }.let {
            assertThat(it).containsNoneOf(MissionKind.MATHS, MissionKind.TRIVIA)
            assertThat(it).contains(MissionKind.DRAW)
        }
        assertThat(days.flatMap { Missions.forDay(it, rules = preschool) }).doesNotContain(MissionKind.TRIVIA)
        // Older children keep exactly the missions they had.
        days.forEach { assertThat(Missions.forDay(it, rules = AgeRules(AgeGroup.SENIOR))).isEqualTo(Missions.forDay(it)) }
    }

    @Test fun `friends ask a little child for a drawing or a tale`() {
        val little = AgeRules(AgeGroup.LITTLE)
        assertThat(Challenge.MATHS.forAge(little)).isEqualTo(Challenge.DRAW)
        assertThat(Challenge.RIDDLE.forAge(little)).isEqualTo(Challenge.TALE)
        assertThat(Challenge.LESSON.forAge(little)).isEqualTo(Challenge.LESSON)
        assertThat(Challenge.MATHS.forAge(AgeRules(AgeGroup.PRESCHOOL))).isEqualTo(Challenge.MATHS)
        val owl = Visits.CHAPTERS.first { it.challenge == Challenge.MATHS }
        assertThat(owl.textFor(little, Lang.BG)).contains("рисунка")
        assertThat(owl.textFor(AgeRules.ALL, Lang.BG)).isEqualTo(owl.text(Lang.BG))
        assertThat(Challenge.DRAW.done(0, com.talkto.core.parent.DayActivity(day = 1, drawings = 1))).isTrue()
    }

    @Test fun `a lesson for a child who cannot read is heard and answered with pictures`() {
        val planner = LessonPlanner(Random(5))
        repeat(20) { seed ->
            val lesson = LessonPlanner(Random(seed)).plan(Lang.EN, Topic.GREETINGS, LearningState(), today = 1, writing = true, reads = false)
            assertThat(lesson.steps).isNotEmpty()
            lesson.steps.forEach { step ->
                assertThat(step).isNotInstanceOf(Step.Type::class.java)
                assertThat(step).isNotInstanceOf(Step.Dialogue::class.java)
                if (step is Step.Choice) assertThat(step.kind).isEqualTo(QuizKind.LISTEN)
                step.word?.let { assertThat(it.topic.pictures).isTrue() }
            }
        }
        val reader = planner.plan(Lang.EN, Topic.ANIMALS, LearningState(), today = 1, writing = true)
        assertThat(reader.steps.any { it is Step.Type }).isTrue()
    }

    @Test fun `counting with pictures for five and six`() {
        val tasks = MathTasks(Random(1))
        repeat(200) {
            val t = tasks.next(0, Lang.BG)
            assertThat(t.grade).isEqualTo(0)
            assertThat(t.answer).isIn(0..10)
            assertThat(t.display).doesNotContainMatch("[0-9]")
            assertThat(t.spoken).isNotEmpty()
        }
        assertThat(MathTasks.gradeLabel(0, Lang.BG)).contains("Броим")
        assertThat(MathTasks.gradeFor(null, 5, 0..1)).isEqualTo(0)
        assertThat(MathTasks.gradeFor(5, null, 0..1)).isEqualTo(1)
        assertThat(AdaptiveGrade.next(1, rightRun = 9, wrongRun = 0, range = 0..1)).isNull()
        assertThat(AdaptiveGrade.next(1, rightRun = 0, wrongRun = 2, range = 0..1)).isEqualTo(0)
        assertThat(AdaptiveGrade.next(0, rightRun = 5, wrongRun = 0, range = 0..1)).isEqualTo(1)
    }
}
