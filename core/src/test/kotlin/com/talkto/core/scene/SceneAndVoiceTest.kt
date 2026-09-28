package com.talkto.core.scene

import com.google.common.truth.Truth.assertThat
import com.talkto.core.avatar.Expression
import com.talkto.core.voice.SpeechText
import org.junit.Test

class MoodSceneTest {
    @Test fun `every mood has a place`() {
        assertThat(MoodScene.forMood(Expression.HAPPY, sleeping = false)).isEqualTo(MoodScene.BEACH)
        assertThat(MoodScene.forMood(Expression.SAD, false)).isEqualTo(MoodScene.RAIN)
        assertThat(MoodScene.forMood(Expression.ANGRY, false)).isEqualTo(MoodScene.STORM)
        assertThat(MoodScene.forMood(Expression.LOVE, false)).isEqualTo(MoodScene.SUNSET)
        assertThat(MoodScene.forMood(Expression.HAPPY, sleeping = true)).isEqualTo(MoodScene.NIGHT)
        Expression.entries.forEach { MoodScene.forMood(it, false) } // exhaustive, never throws
    }

    @Test fun `photos are classified from image labels`() {
        assertThat(MoodScene.classify(mapOf("Beach" to 0.92f, "Sky" to 0.8f, "Sand" to 0.7f))!!.first).isEqualTo(MoodScene.BEACH)
        assertThat(MoodScene.classify(mapOf("Sunset" to 0.81f, "Cloud" to 0.4f))!!.first).isEqualTo(MoodScene.SUNSET)
        assertThat(MoodScene.classify(mapOf("Umbrella" to 0.7f, "Rain" to 0.66f))!!.first).isEqualTo(MoodScene.RAIN)
        assertThat(MoodScene.classify(mapOf("Fireworks" to 0.9f))!!.first).isEqualTo(MoodScene.FIREWORKS)
    }

    @Test fun `weak or unrelated labels are not used`() {
        assertThat(MoodScene.classify(mapOf("Paper" to 0.9f, "Text" to 0.8f))).isNull()
        assertThat(MoodScene.classify(mapOf("Beach" to 0.3f))).isNull()
    }
}

class SpeechTextTest {
    @Test fun `wake word and punctuation are removed`() {
        assertThat(SpeechText.clean("Толкто, отвори камерата.")).isEqualTo("отвори камерата")
        assertThat(SpeechText.clean("Hey Talkto open Spotify")).isEqualTo("open Spotify")
        assertThat(SpeechText.clean("  Колко е   часът  ")).isEqualTo("колко е часът")
    }

    @Test fun `first usable hypothesis wins`() {
        assertThat(SpeechText.pick(listOf("Токто", "токто нахрани се"))).isEqualTo("нахрани се")
        assertThat(SpeechText.pick(emptyList())).isNull()
    }
}
