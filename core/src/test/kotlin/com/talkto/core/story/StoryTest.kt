package com.talkto.core.story

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.voice.BulgarianSpeech
import com.talkto.core.voice.Speakable
import org.junit.Test

class StoryTest {
    @Test fun `every page has a picture and both languages`() {
        assertThat(Story.PAGES.size).isAtLeast(4)
        Story.PAGES.forEach { p ->
            assertThat(p.art).isNotEmpty()
            assertThat(p.text(Lang.BG)).contains(" ")
            assertThat(p.text(Lang.EN)).contains(" ")
            assertThat(p.bg).doesNotContain("—")
        }
        assertThat(Story.PAGES.any { Story.TEACHER_BG in it.bg }).isTrue()
        assertThat(Story.PAGES.any { Story.TEACHER_EN in it.en }).isTrue()
    }

    @Test fun `the voice reads the sum in words`() {
        val first = BulgarianSpeech.normalize(Speakable.clean(Story.PAGES[0].bg))
        assertThat(first).contains("колко е 7 по 8")
        assertThat(Speakable.clean(Story.PAGES[3].bg)).doesNotContain("„")
    }
}
