package com.talkto.core.learn

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import org.junit.Test
import kotlin.random.Random

class VocabularyTest {
    @Test fun `ids are unique, every topic has enough words, picture topics have distinct pictures`() {
        assertThat(Vocabulary.words.map { it.id }.toSet()).hasSize(Vocabulary.words.size)
        assertThat(Vocabulary.words.size).isAtLeast(200)
        for (t in Topic.entries) {
            val ws = Vocabulary.of(t)
            assertThat(ws.size).isAtLeast(8)
            if (t.pictures) assertThat(ws.map { it.emoji }.toSet()).hasSize(ws.size)
            assertThat(ws.map { it.en.lowercase() }.toSet()).hasSize(ws.size)
            assertThat(ws.map { it.bg.lowercase() }.toSet()).hasSize(ws.size)
        }
        // English words are Latin, Bulgarian ones Cyrillic, so speech picks the right voice.
        assertThat(Vocabulary.words.none { w -> w.en.any { it in 'Ѐ'..'ӿ' } }).isTrue()
        assertThat(Vocabulary.words.none { w -> w.bg.any { it in 'a'..'z' || it in 'A'..'Z' } }).isTrue()
    }
}

class LeitnerTest {
    @Test fun `right answers climb the boxes, a wrong one starts over`() {
        var s = WordState()
        s = Leitner.next(s, true, 10); assertThat(s).isEqualTo(WordState(1, 11, 1, 1))
        s = Leitner.next(s, true, 11); assertThat(s.box).isEqualTo(2); assertThat(s.dueDay).isEqualTo(13)
        s = Leitner.next(s, false, 13); assertThat(s.box).isEqualTo(0); assertThat(s.dueDay).isEqualTo(13)
        repeat(10) { s = Leitner.next(s, true, 20) }
        assertThat(s.box).isEqualTo(5)
    }

    @Test fun `learned words, due words, lessons and streak`() {
        val dog = Vocabulary.of(Topic.ANIMALS)[0]
        var st = LearningState()
        repeat(3) { st = st.record(dog, Lang.EN, true, 100) }
        assertThat(st.learned(Lang.EN)).isEqualTo(1)
        assertThat(st.learned(Lang.BG)).isEqualTo(0)
        assertThat(st.learnedIn(Topic.ANIMALS, Lang.EN)).isEqualTo(1)
        assertThat(st.due(Lang.EN, 100)).isEqualTo(0)
        assertThat(st.due(Lang.EN, 104)).isEqualTo(1)
        st = st.finishLesson(3, 100).finishLesson(2, 101).finishLesson(1, 101)
        assertThat(st.lessons).isEqualTo(3)
        assertThat(st.stars).isEqualTo(6)
        assertThat(st.streak).isEqualTo(2)
        assertThat(st.finishLesson(1, 105).streak).isEqualTo(1)
        assertThat(starsFor(9, 10)).isEqualTo(3)
        assertThat(starsFor(7, 10)).isEqualTo(2)
        assertThat(starsFor(2, 10)).isEqualTo(1)
    }
}

class LessonPlannerTest {
    private val planner = LessonPlanner(Random(4))

    @Test fun `a first lesson introduces three new words and asks about each twice`() {
        val lesson = planner.plan(Lang.EN, Topic.ANIMALS, LearningState(), today = 1, speaking = true)
        val intros = lesson.steps.filterIsInstance<Step.Intro>()
        assertThat(intros).hasSize(3)
        lesson.steps.forEach { step ->
            if (step is Step.Choice) {
                assertThat(step.options).hasSize(4)
                assertThat(step.options[step.answer]).isEqualTo(step.word)
                assertThat(step.options.toSet()).hasSize(4)
                // Every word is introduced before it is asked, when it is new.
                val introAt = lesson.steps.indexOfFirst { it is Step.Intro && it.word == step.word }
                if (introAt >= 0) assertThat(lesson.steps.indexOf(step)).isGreaterThan(introAt)
            }
        }
        intros.forEach { i -> assertThat(lesson.steps.filterIsInstance<Step.Choice>().count { it.word == i.word }).isEqualTo(2) }
        assertThat(lesson.steps.last()).isInstanceOf(Step.Speak::class.java)
        assertThat(lesson.words.size).isEqualTo(3)
    }

    @Test fun `due words come back, picture questions have distinct pictures, greetings add dialogues`() {
        val animals = Vocabulary.of(Topic.ANIMALS)
        var st = LearningState()
        animals.take(6).forEach { st = st.record(it, Lang.EN, false, 5) }
        val lesson = planner.plan(Lang.EN, Topic.ANIMALS, st, today = 5, speaking = false)
        assertThat(lesson.steps.none { it is Step.Speak }).isTrue()
        assertThat(lesson.words.count { it in animals.take(6) }).isAtLeast(4)
        lesson.steps.filterIsInstance<Step.Choice>().filter { it.kind == QuizKind.PICTURE_TO_WORD }.forEach { c ->
            assertThat(c.options.map { it.emoji }.toSet()).hasSize(4)
        }
        val greet = planner.plan(Lang.BG, Topic.GREETINGS, LearningState(), today = 1)
        assertThat(greet.steps.filterIsInstance<Step.Dialogue>()).hasSize(2)
        assertThat(greet.steps.filterIsInstance<Step.Choice>().none { it.kind == QuizKind.PICTURE_TO_WORD }).isTrue()
        greet.steps.filterIsInstance<Step.Dialogue>().forEach { d -> assertThat(d.options[d.answer]).isEqualTo(d.exchange) }
    }

    @Test fun `word of the day is stable for a day and changes`() {
        assertThat(wordOfTheDay(20_000, Lang.EN)).isEqualTo(wordOfTheDay(20_000, Lang.EN))
        assertThat((20_000L..20_006L).map { wordOfTheDay(it, Lang.EN) }.toSet().size).isAtLeast(5)
        assertThat(wordOfTheDay(20_000, Lang.EN).topic.pictures).isTrue()
    }
}

class AnswerMatcherTest {
    private fun word(topic: Topic, en: String) = Vocabulary.of(topic).first { it.en == en }

    @Test fun `forgiving where it can be`() {
        val dog = word(Topic.ANIMALS, "dog")
        assertThat(AnswerMatcher.matches("A dog!", dog, Lang.EN)).isTrue()
        assertThat(AnswerMatcher.matches("Кучето", dog, Lang.BG)).isTrue()
        assertThat(AnswerMatcher.matches("dock", dog, Lang.EN)).isFalse() // short words must be exact
        val elephant = word(Topic.ANIMALS, "elephant")
        assertThat(AnswerMatcher.matches("elefant", elephant, Lang.EN)).isTrue()
        assertThat(AnswerMatcher.matches("слонът", elephant, Lang.BG)).isTrue()
        val seven = word(Topic.NUMBERS, "seven")
        assertThat(AnswerMatcher.matches("7", seven, Lang.EN)).isTrue()
        assertThat(AnswerMatcher.matches("7", seven, Lang.BG)).isTrue()
        val red = word(Topic.COLORS, "red")
        assertThat(AnswerMatcher.matches("червено", red, Lang.BG)).isTrue()
        val trousers = word(Topic.CLOTHES, "trousers")
        assertThat(AnswerMatcher.matches("pants", trousers, Lang.EN)).isTrue()
        val cat = word(Topic.ANIMALS, "cat")
        assertThat(AnswerMatcher.matches("котката", cat, Lang.BG)).isTrue()
        assertThat(AnswerMatcher.matches("dog", cat, Lang.EN)).isFalse()
        val morning = word(Topic.GREETINGS, "Good morning")
        assertThat(AnswerMatcher.matches("good morning ZnaiKo", morning, Lang.EN)).isTrue()
        assertThat(AnswerMatcher.matches("", morning, Lang.EN)).isFalse()
    }

    @Test fun `edit distance`() {
        assertThat(AnswerMatcher.distance("kitten", "sitting")).isEqualTo(3)
        assertThat(AnswerMatcher.distance("", "abc")).isEqualTo(3)
    }
}

class DictionaryTest {
    @Test fun `questions in both languages`() {
        assertThat(Dictionary.parse("Как е куче на английски?")).isEqualTo(Dictionary.Query("куче", Lang.EN))
        assertThat(Dictionary.parse("how do you say dog in Bulgarian")).isEqualTo(Dictionary.Query("dog", Lang.BG))
        assertThat(Dictionary.parse("котка на английски")).isEqualTo(Dictionary.Query("котка", Lang.EN))
        assertThat(Dictionary.parse("what is „ябълка“ in English")).isEqualTo(Dictionary.Query("ябълка", Lang.EN))
        assertThat(Dictionary.parse("какво значи rainbow")).isEqualTo(Dictionary.Query("rainbow", null))
        assertThat(Dictionary.parse("отвори камерата")).isNull()
    }

    @Test fun `lookup finds words, definite forms and both meanings of orange`() {
        assertThat(Dictionary.lookup("кучето").map { it.en }).containsExactly("dog")
        assertThat(Dictionary.lookup("Rainbow").map { it.bg }).containsExactly("дъга")
        assertThat(Dictionary.lookup("orange").map { it.bg }).containsExactly("оранжев", "портокал")
        assertThat(Dictionary.lookup("ксилофон")).isEmpty()
        assertThat(Dictionary.into(Dictionary.Query("rainbow", null))).isEqualTo(Lang.BG)
        assertThat(Dictionary.into(Dictionary.Query("куче", Lang.EN))).isEqualTo(Lang.EN)
    }
}

class LearnCommandsTest {
    @Test fun `language switch, lessons, practice and word of the day`() {
        assertThat(LearnCommands.parse("Говори на английски")).isEqualTo(LearnCommand.SwitchLanguage(Lang.EN))
        assertThat(LearnCommands.parse("speak Bulgarian please")).isEqualTo(LearnCommand.SwitchLanguage(Lang.BG))
        assertThat(LearnCommands.parse("switch to english")).isEqualTo(LearnCommand.SwitchLanguage(Lang.EN))
        assertThat(LearnCommands.parse("научи ме на английски")).isEqualTo(LearnCommand.OpenLessons(Lang.EN))
        assertThat(LearnCommands.parse("teach me Bulgarian")).isEqualTo(LearnCommand.OpenLessons(Lang.BG))
        assertThat(LearnCommands.parse("урок")).isEqualTo(LearnCommand.OpenLessons(null))
        assertThat(LearnCommands.parse("да си говорим на английски")).isEqualTo(LearnCommand.Practice(Lang.EN))
        assertThat(LearnCommands.parse("let's practise English")).isEqualTo(LearnCommand.Practice(Lang.EN))
        assertThat(LearnCommands.parse("край на упражнението")).isEqualTo(LearnCommand.StopPractice)
        assertThat(LearnCommands.parse("Дума на деня")).isEqualTo(LearnCommand.WordOfTheDay)
        assertThat(LearnCommands.parse("отвори камерата")).isNull()
    }
}
