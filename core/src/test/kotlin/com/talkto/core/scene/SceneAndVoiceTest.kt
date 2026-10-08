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
        assertThat(SpeechText.clean("Знайко, отвори камерата")).isEqualTo("отвори камерата")
        assertThat(SpeechText.clean("hey ZnaiKo open Spotify")).isEqualTo("open Spotify")
        assertThat(SpeechText.clean("  Колко е   часът  ")).isEqualTo("колко е часът")
    }

    @Test fun `first usable hypothesis wins`() {
        assertThat(SpeechText.pick(listOf("Токто", "токто нахрани се"))).isEqualTo("нахрани се")
        assertThat(SpeechText.pick(emptyList())).isNull()
    }
}

class VoicePresetTest {
    @Test fun `presets stay in the range Android TTS accepts`() {
        com.talkto.core.voice.VoicePreset.entries.forEach {
            assertThat(it.pitch).isIn(com.google.common.collect.Range.closed(0.5f, 2.0f))
            assertThat(it.rate).isIn(com.google.common.collect.Range.closed(0.5f, 2.0f))
            assertThat(it.sample).isNotEmpty()
        }
    }

    @Test fun `unknown names fall back to the fairy-tale voice`() {
        assertThat(com.talkto.core.voice.VoicePreset.parse("NOPE")).isEqualTo(com.talkto.core.voice.VoicePreset.FAIRY)
        assertThat(com.talkto.core.voice.VoicePreset.parse(null)).isEqualTo(com.talkto.core.voice.VoicePreset.FAIRY)
        assertThat(com.talkto.core.voice.VoicePreset.parse("BEAR")).isEqualTo(com.talkto.core.voice.VoicePreset.BEAR)
    }
}

class SpeakableTest {
    private fun s(t: String) = com.talkto.core.voice.Speakable.clean(t)

    @Test fun `emoji, quotes and brackets are not read aloud`() {
        assertThat(s("„куче“ на английски е \"dog\" 🐶.")).isEqualTo("куче на английски е dog.")
        assertThat(s("Бе-е-е! 🎂 Честит рожден ден!")).isEqualTo("Бе-е-е! Честит рожден ден!")
        assertThat(s("Аз избрах камък (ти хартия).")).isEqualTo("Аз избрах камък, ти хартия.")
        assertThat(s("🇧🇬 Български")).isEqualTo("Български")
        assertThat(s("⭐⭐☆ Урокът е готов!")).isEqualTo("Урокът е готов!")
        assertThat(s("1️⃣ едно")).isEqualTo("1 едно")
    }

    @Test fun `lists become sentences, bullets and dashes become pauses`() {
        assertThat(s("• 3 групи дубликати\n• 2 празни папки")).isEqualTo("3 групи дубликати. 2 празни папки")
        assertThat(s("Ниво 3 · Бебе — растеш!")).isEqualTo("Ниво 3 Бебе, растеш!")
        assertThat(s("Мисля, мисля...")).isEqualTo("Мисля, мисля.")
        assertThat(s("Здрасти - как си?")).isEqualTo("Здрасти, как си?")
    }

    @Test fun `meaningful symbols between numbers and inside words stay`() {
        assertThat(s("Часът е 14:10.")).isEqualTo("Часът е 14:10.")
        assertThat(s("7 + 5 = ?")).isEqualTo("7 + 5 = ?")
        assertThat(s("1/2 от 18 е 9, а 50% от 20 е 10. Навън е 20 °C.")).isEqualTo("1/2 от 18 е 9, а 50% от 20 е 10. Навън е 20 °C.")
        assertThat(s("Включи Wi-Fi, don't worry.")).isEqualTo("Включи Wi-Fi, don't worry.")
        assertThat(s("Температурата е -5 градуса")).isEqualTo("Температурата е -5 градуса")
        assertThat(s("Прочети: първо това")).isEqualTo("Прочети, първо това")
    }

    @Test fun `signs of a sum keep their meaning`() {
        assertThat(s("10 - 4 = 6")).isEqualTo("10 − 4 = 6")
        assertThat(s("x - 2 = 5")).isEqualTo("x − 2 = 5")
        assertThat(s("4 * 5 = 20")).isEqualTo("4 × 5 = 20")
        assertThat(s("4 · 5 = 20")).isEqualTo("4 × 5 = 20")
        assertThat(s("12 : 3 = 4")).isEqualTo("12 ÷ 3 = 4")
        assertThat(s("10 / 2 = 5")).isEqualTo("10 / 2 = 5")
        assertThat(s("5 > 3 и 2 < 4")).isEqualTo("5 > 3 и 2 < 4")
        assertThat(s("x^2 + 1")).isEqualTo("x² + 1")
        assertThat(s("1941–1945")).isEqualTo("1941-1945")
        assertThat(s("**Браво**, 5 *звезди*")).isEqualTo("Браво, 5 звезди")
        assertThat(s("Тича 10 м/с")).isEqualTo("Тича 10 метра в секунда")
    }
}
