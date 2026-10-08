package com.talkto.core.pet

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.LearningState
import com.talkto.core.learn.LessonPlanner
import com.talkto.core.learn.Step
import com.talkto.core.learn.Topic
import com.talkto.core.learn.Vocabulary
import com.talkto.core.parent.DayActivity
import org.junit.Test
import kotlin.random.Random

class BadgesTest {

    @Test fun `badges are earned once`() {
        val stats = BadgeStats(lessons = 1, wordsLearned = 12, visitStreak = 7)
        val first = Badge.newOnes(stats, emptySet())
        assertThat(first).containsExactly(Badge.FIRST_LESSON, Badge.WORDS_10, Badge.VISITS_7)
        assertThat(Badge.newOnes(stats, first.map { it.name }.toSet())).isEmpty()
        assertThat(Badge.newOnes(BadgeStats(), emptySet())).isEmpty()
    }

    @Test fun `weekly praise lists what was done, and nothing for an empty week`() {
        assertThat(WeeklyPraise.text(DayActivity(1), Lang.BG)).isNull()
        val bg = WeeklyPraise.text(DayActivity(1, minutes = 40, wordsRight = 12, mathRight = 5, games = 3), Lang.BG)!!
        assertThat(bg).contains("позна 12 думи")
        assertThat(bg).contains("реши 5 задачи и изигра 3 игри")
        val en = WeeklyPraise.text(DayActivity(1, chats = 4), Lang.EN)!!
        assertThat(en).contains("talked")
    }

    @Test fun `a writing step comes before saying it aloud`() {
        val animals = Vocabulary.of(Topic.ANIMALS)
        var st = LearningState()
        animals.take(2).forEach { st = st.record(it, Lang.EN, true, 1) }
        val lesson = LessonPlanner(Random(3)).plan(Lang.EN, Topic.ANIMALS, st, today = 9, speaking = true, writing = true)
        val type = lesson.steps.filterIsInstance<Step.Type>().single()
        assertThat(lesson.steps.last()).isInstanceOf(Step.Speak::class.java)
        assertThat(lesson.steps.indexOf(type)).isEqualTo(lesson.steps.size - 2)
        assertThat(st.of(type.word, Lang.EN)).isNotNull()
        val none = LessonPlanner(Random(3)).plan(Lang.EN, Topic.ANIMALS, st, today = 9, writing = false)
        assertThat(none.steps.none { it is Step.Type }).isTrue()
    }
}
