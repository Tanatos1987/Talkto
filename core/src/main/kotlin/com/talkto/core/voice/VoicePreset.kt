package com.talkto.core.voice

import kotlinx.serialization.Serializable

/**
 * How ZnaiKo sounds. Android TTS voices are adult voices; pitch and rate turn them into characters.
 * Pitch above ~1.4 reads as a child or a cartoon, above ~1.8 as a tiny creature; below 0.8 as a big bear.
 */
@Serializable
enum class VoicePreset(val bg: String, val pitch: Float, val rate: Float, val sample: String) {
    FAIRY("Приказен герой", 1.6f, 1.05f, "Здравей! Аз съм ZnaiKo, твоят приказен приятел. Хайде да играем!"),
    CHILD("Малко дете", 1.4f, 1.0f, "Хи-хи! Аз съм ZnaiKo и много обичам да помагам!"),
    SQUIRREL("Катеричка", 2.0f, 1.2f, "Бързо-бързо! Имам цял куп жълъди и още повече идеи!"),
    ELF("Горско духче", 1.8f, 0.92f, "Шшш… В гората има тайни. Искаш ли да ти кажа една?"),
    ROBOT("Роботче", 1.15f, 0.85f, "Бип-буп. Аз съм роботчето ZnaiKo. Готов за задачи."),
    BEAR("Добрият мечок", 0.75f, 0.9f, "Ммм… Мед и прегръдки. Аз съм голям, но съм добър."),
    NORMAL("Обикновен", 1.0f, 1.05f, "Здравей, аз съм ZnaiKo. С какво да помогна?"),
    ;

    companion object {
        val DEFAULT = FAIRY

        fun parse(name: String?): VoicePreset = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
