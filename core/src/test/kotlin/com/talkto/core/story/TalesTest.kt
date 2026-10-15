package com.talkto.core.story

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import org.junit.Test
import kotlin.random.Random

class TalesTest {

    @Test fun `every tale and riddle has both languages and a unique id`() {
        assertThat(Tales.all.map { it.id }.toSet()).hasSize(Tales.all.size)
        assertThat(Tales.riddles.map { it.id }.toSet()).hasSize(Tales.riddles.size)
        assertThat(Tales.of(TaleKind.FABLE).size).isAtLeast(10)
        assertThat(Tales.of(TaleKind.FAIRY_TALE).size).isAtLeast(8)
        assertThat(Tales.riddles.size).isAtLeast(20)
        Tales.all.forEach { t ->
            assertThat(t.textBg).isNotEmpty()
            assertThat(t.textEn).isNotEmpty()
            // Bulgarian text is Cyrillic, English text Latin, so the voice picks the right language.
            assertThat(t.textEn.none { it in 'Ѐ'..'ӿ' }).isTrue()
            assertThat(t.titleBg.none { it in 'a'..'z' }).isTrue()
            // Every fable ends with its lesson.
            if (t.kind == TaleKind.FABLE) assertThat(t.moralBg).isNotEmpty()
            // Short enough for the phone's voice in one go.
            assertThat(t.spoken(Lang.BG).length).isLessThan(3_500)
        }
        assertThat(Tales.all.count { it.bedtime }).isAtLeast(5)
    }

    @Test fun `picking avoids the tales heard lately`() {
        val recent = Tales.of(TaleKind.FABLE).drop(1).map { it.id }
        val t = Tales.pick(TaleKind.FABLE, recent, Random(1))
        assertThat(t.id).isEqualTo(Tales.of(TaleKind.FABLE).first().id)
        assertThat(Tales.pick(null, emptyList(), Random(2), bedtimeOnly = true).bedtime).isTrue()
    }

    @Test fun `story requests in both languages`() {
        assertThat(StoryCommands.parse("Разкажи ми приказка")).isEqualTo(StoryRequest(TaleKind.FAIRY_TALE))
        assertThat(StoryCommands.parse("кажи ми една басня")).isEqualTo(StoryRequest(TaleKind.FABLE))
        assertThat(StoryCommands.parse("задай ми гатанка")).isEqualTo(StoryRequest(riddle = true))
        assertThat(StoryCommands.parse("искам приказка за лека нощ")).isEqualTo(StoryRequest(TaleKind.FAIRY_TALE, bedtime = true))
        assertThat(StoryCommands.parse("Tell me a story")).isEqualTo(StoryRequest())
        assertThat(StoryCommands.parse("a riddle please")).isEqualTo(StoryRequest(riddle = true))
        assertThat(StoryCommands.parse("разкажи ми за динозаврите")).isNull()
        assertThat(StoryCommands.parse("каква е столицата на Франция")).isNull()
    }
}
