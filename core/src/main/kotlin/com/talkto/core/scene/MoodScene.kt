package com.talkto.core.scene

import com.talkto.core.avatar.Expression
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * The world behind the pet. Each mood has a place: the beach when it is happy, rain when it is sad,
 * a night sky when it is sleepy, a storm when it is angry, a sunset with hearts when it is in love.
 */
@Serializable
enum class MoodScene(val bg: String, val keywords: Set<String>) {
    BEACH("Плаж", setOf("beach", "sea", "sand", "swimwear", "surfboard", "summer", "pool", "palm tree", "sun", "lake", "coast", "shore", "vacation")),
    MEADOW("Поляна", setOf("grass", "field", "garden", "tree", "plant", "forest", "park", "mountain", "hill", "meadow", "leaf", "nature", "countryside")),
    SUNSET("Залез", setOf("sunset", "dusk", "flower", "rose", "petal", "heart", "wedding", "bride", "couple", "romance", "sunrise")),
    RAIN("Дъжд", setOf("rain", "umbrella", "puddle", "cloud", "overcast", "wet", "drizzle", "window")),
    STORM("Буря", setOf("storm", "lightning", "thunder", "fire", "flame", "volcano", "tornado", "wave")),
    NIGHT("Нощ", setOf("night", "moon", "darkness", "candle", "lamp", "bedroom", "bed", "sleep", "lights")),
    SPACE("Космос", setOf("space", "planet", "galaxy", "astronomy", "star", "universe", "telescope", "book", "library")),
    FOG("Мъгла", setOf("fog", "mist", "smoke", "haze", "cloud")),
    FIREWORKS("Фойерверки", setOf("fireworks", "party", "balloon", "festival", "concert", "celebration", "cake", "confetti")),
    ;

    companion object {
        /** Scene for what the pet feels right now. Sleep always wins: a sleeping pet dreams under the stars. */
        fun forMood(expression: Expression, sleeping: Boolean): MoodScene = when {
            sleeping -> NIGHT
            else -> when (expression) {
                Expression.HAPPY, Expression.TONGUE -> BEACH
                Expression.SAD -> RAIN
                Expression.SLEEPY -> NIGHT
                Expression.ANGRY -> STORM
                Expression.LOVE -> SUNSET
                Expression.SURPRISED -> FIREWORKS
                Expression.THINKING -> SPACE
                Expression.CONFUSED -> FOG
                Expression.NEUTRAL -> MEADOW
            }
        }

        /**
         * Picks the scene a photo fits best from on-device image labels (label -> confidence 0..1).
         * Returns null when nothing matches well enough; a photo of a receipt should not become "the beach".
         */
        fun classify(labels: Map<String, Float>, minScore: Float = 0.55f): Pair<MoodScene, Float>? {
            val normalized = labels.mapKeys { it.key.lowercase(Locale.ROOT).trim() }
            return entries.map { scene ->
                // Exact label matches count fully, partial ones ("sunset" contains "sun") at 80%.
                // Best match plus a small bonus for each extra supporting label.
                val hits = normalized.mapNotNull { (label, conf) ->
                    when {
                        label in scene.keywords -> conf
                        scene.keywords.any { k -> label.contains(k) } -> conf * 0.8f
                        else -> null
                    }
                }.sortedDescending()
                val score = (hits.firstOrNull() ?: 0f) + hits.drop(1).take(3).sumOf { it.toDouble() * 0.1 }.toFloat()
                scene to score
            }.maxByOrNull { it.second }?.takeIf { it.second >= minScore }
        }
    }
}
