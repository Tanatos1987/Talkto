package com.talkto.core.voice

import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable

/**
 * How ZnaiKo sounds. Android TTS voices are adult voices; pitch and rate turn them into characters.
 * Pitch above ~1.4 reads as a child or a cartoon, above ~1.8 as a tiny creature; below 0.8 as a big bear.
 */
@Serializable
enum class VoicePreset(val bg: String, val pitch: Float, val rate: Float, val sample: String, val en: String, val sampleEn: String) {
    FAIRY(
        "Приказен герой", 1.6f, 1.05f, "Здравей! Аз съм ZnaiKo, твоят приказен приятел. Хайде да играем!",
        "Fairy-tale hero", "Hello! I'm ZnaiKo, your fairy-tale friend. Let's play!",
    ),
    CHILD("Малко дете", 1.4f, 1.0f, "Хи-хи! Аз съм ZnaiKo и много обичам да помагам!", "Little child", "Hee-hee! I'm ZnaiKo and I love to help!"),
    SQUIRREL(
        "Катеричка", 2.0f, 1.2f, "Бързо-бързо! Имам цял куп жълъди и още повече идеи!",
        "Squirrel", "Quick, quick! I have a whole pile of acorns and even more ideas!",
    ),
    ELF(
        "Горско духче", 1.8f, 0.92f, "Шшш… В гората има тайни. Искаш ли да ти кажа една?",
        "Forest sprite", "Shhh… The forest has secrets. Shall I tell you one?",
    ),
    ROBOT("Роботче", 1.15f, 0.85f, "Бип-буп. Аз съм роботчето ZnaiKo. Готов за задачи.", "Little robot", "Beep-boop. I am ZnaiKo the little robot. Ready for tasks."),
    BEAR("Добрият мечок", 0.75f, 0.9f, "Ммм… Мед и прегръдки. Аз съм голям, но съм добър.", "Kind bear", "Mmm… Honey and hugs. I'm big, but I'm kind."),
    NORMAL("Обикновен", 1.0f, 1.05f, "Здравей, аз съм ZnaiKo. С какво да помогна?", "Plain", "Hello, I'm ZnaiKo. How can I help?"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)
    fun sample(lang: Lang) = lang.pick(sample, sampleEn)

    companion object {
        val DEFAULT = FAIRY

        fun parse(name: String?): VoicePreset = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
