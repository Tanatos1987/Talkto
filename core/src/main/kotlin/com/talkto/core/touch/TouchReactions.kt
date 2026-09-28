package com.talkto.core.touch

import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** How the user touched the pet. */
enum class TouchKind { POKE, HIT, SLAP, GENTLE, PAT }

/** One pointer sample, in dp and milliseconds, relative to the avatar stage (0,0 top-left). */
data class TouchSample(val tMs: Long, val x: Float, val y: Float, val pressure: Float = 0f)

/** A classified touch plus where it landed, normalised to the stage (0..1). */
data class Touch(val kind: TouchKind, val nx: Float, val ny: Float, val strength: Float)

/**
 * Turns raw pointer tracks into touch gestures. Thresholds are in dp, so they feel the same on every screen.
 *
 * - SLAP: a fast, mostly horizontal swipe across the pet.
 * - HIT: a very short, sharp tap (or a hard press where the screen reports pressure).
 * - PAT: two or more light taps in quick succession.
 * - GENTLE: a slow stroke, or holding the finger still on the pet.
 * - POKE: an ordinary single tap.
 *
 * Pats are detected on the second tap; the first one is still reported as a POKE, so the pet reacts at once.
 */
class TouchClassifier(
    private val stageWidthDp: Float,
    private val stageHeightDp: Float,
    private val config: Config = Config(),
) {
    data class Config(
        val slapMinDistanceDp: Float = 70f,
        val slapMinSpeedDpPerS: Float = 900f,
        val strokeMinDistanceDp: Float = 30f,
        val strokeMaxSpeedDpPerS: Float = 500f,
        val holdMinMs: Long = 450,
        val tapMaxMs: Long = 220,
        val tapMaxMoveDp: Float = 12f,
        val hitMaxMs: Long = 60,
        val hardPressure: Float = 0.75f,
        val patWindowMs: Long = 650,
    )

    private var lastTapEndMs = Long.MIN_VALUE / 2
    private var tapStreak = 0

    /** Classifies one completed pointer track (down .. up). Null for tracks too short to mean anything. */
    fun classify(track: List<TouchSample>): Touch? {
        if (track.isEmpty()) return null
        val first = track.first()
        val last = track.last()
        val duration = (last.tMs - first.tMs).coerceAtLeast(1)
        val dx = last.x - first.x
        val dy = last.y - first.y
        val distance = hypot(dx, dy)
        val peakSpeed = peakSpeed(track)
        val pressure = track.maxOf { it.pressure }
        val cx = (track.sumOf { it.x.toDouble() } / track.size).toFloat()
        val cy = (track.sumOf { it.y.toDouble() } / track.size).toFloat()
        fun touch(kind: TouchKind, strength: Float) =
            Touch(kind, (cx / stageWidthDp).coerceIn(0f, 1f), (cy / stageHeightDp).coerceIn(0f, 1f), strength.coerceIn(0f, 1f))

        if (distance >= config.slapMinDistanceDp && peakSpeed >= config.slapMinSpeedDpPerS && abs(dx) > abs(dy) * 1.3f) {
            tapStreak = 0
            return touch(TouchKind.SLAP, peakSpeed / (config.slapMinSpeedDpPerS * 3))
        }
        if (distance >= config.strokeMinDistanceDp && peakSpeed <= config.strokeMaxSpeedDpPerS) {
            tapStreak = 0
            return touch(TouchKind.GENTLE, 0.5f)
        }
        if (distance <= config.tapMaxMoveDp && duration >= config.holdMinMs) {
            tapStreak = 0
            return touch(TouchKind.GENTLE, (duration / 2000f))
        }
        if (distance <= config.tapMaxMoveDp * 2 && duration <= config.tapMaxMs) {
            val quickFollowUp = first.tMs - lastTapEndMs <= config.patWindowMs
            tapStreak = if (quickFollowUp) tapStreak + 1 else 1
            lastTapEndMs = last.tMs
            return when {
                // Many screens report a constant 1.0; only a pressure that actually varies counts as "hard".
                pressure in config.hardPressure..0.99f || duration <= config.hitMaxMs && !quickFollowUp -> touch(TouchKind.HIT, max(pressure, 0.7f))
                tapStreak >= 2 -> touch(TouchKind.PAT, 0.4f)
                else -> touch(TouchKind.POKE, 0.3f)
            }
        }
        return null
    }

    private fun peakSpeed(track: List<TouchSample>): Float {
        var peak = 0f
        track.zipWithNext().forEach { (a, b) ->
            val dt = (b.tMs - a.tMs).coerceAtLeast(1) / 1000f
            peak = max(peak, hypot(b.x - a.x, b.y - a.y) / dt)
        }
        return peak
    }
}

/** What the pet does in response: face, body, needs, and something to say. */
data class TouchReaction(
    val expression: Expression,
    val gesture: Gesture,
    val happinessDelta: Float,
    val bondDelta: Float,
    val line: String?,
    val holdMs: Long,
    /** Head recoil for the 3D avatar: yaw in degrees (positive = turns right), squash 0..1. */
    val recoilYaw: Float = 0f,
    val squash: Float = 0f,
)

/**
 * The pet's temperament. It remembers recent rough handling: the first slap gets a surprised "hey!",
 * repeated ones make it sad and wary, and gentle touches win it back. Occasionally, when happy,
 * it answers a pat by sticking its tongue out.
 */
class Temperament(private val clock: () -> Long = System::currentTimeMillis, private val random: () -> Float = { Math.random().toFloat() }) {

    private var grievance = 0f // 0 = relaxed .. 1 = very upset
    private var lastMs = clock()

    val upset: Float get() = decayed()

    fun react(touch: Touch, petHappy: Boolean): TouchReaction {
        val g = decayed()
        return when (touch.kind) {
            TouchKind.SLAP -> {
                grievance = (g + 0.35f).coerceAtMost(1f)
                val side = if (touch.nx < 0.5f) 1f else -1f // slapped from the left, the head turns right
                if (g > 0.6f) TouchReaction(Expression.SAD, Gesture.SHAKE, -15f, -4f, pick(SLAP_REPEATED), 3_500, 55f * side, 0.1f)
                else TouchReaction(Expression.ANGRY, Gesture.SHAKE, -10f, -2f, pick(SLAP_FIRST), 2_500, 45f * side, 0.1f)
            }
            TouchKind.HIT -> {
                grievance = (g + 0.25f).coerceAtMost(1f)
                if (g > 0.6f) TouchReaction(Expression.SAD, Gesture.NONE, -12f, -3f, pick(HIT_REPEATED), 3_000, 0f, 0.5f)
                else TouchReaction(Expression.SURPRISED, Gesture.SHAKE, -6f, -1f, pick(HIT_FIRST), 2_000, 0f, 0.45f)
            }
            TouchKind.GENTLE -> {
                grievance = (g - 0.3f).coerceAtLeast(0f)
                if (g > 0.5f) TouchReaction(Expression.NEUTRAL, Gesture.NOD, 4f, 1f, pick(FORGIVE), 2_000)
                else TouchReaction(Expression.LOVE, Gesture.NOD, 5f, 1.5f, pick(GENTLE), 2_500)
            }
            TouchKind.PAT -> {
                grievance = (g - 0.15f).coerceAtLeast(0f)
                if (petHappy && g < 0.2f && random() < 0.35f) TouchReaction(Expression.TONGUE, Gesture.BOUNCE, 4f, 1f, pick(TONGUE), 1_800)
                else TouchReaction(Expression.HAPPY, Gesture.BOUNCE, 3f, 1f, pick(PAT), 1_500, squash = 0.15f)
            }
            TouchKind.POKE -> {
                if (g > 0.5f) TouchReaction(Expression.CONFUSED, Gesture.NONE, 0f, 0f, null, 1_000)
                else TouchReaction(Expression.SURPRISED, Gesture.NOD, 1f, 0.2f, null, 900, squash = 0.12f)
            }
        }
    }

    /** Grievance fades by half every two minutes. */
    private fun decayed(): Float {
        val now = clock()
        val minutes = (now - lastMs).coerceAtLeast(0) / 60_000f
        lastMs = now
        grievance *= Math.pow(0.5, minutes / 2.0).toFloat()
        return grievance
    }

    private fun pick(lines: List<String>) = lines[(random() * lines.size).toInt().coerceIn(0, lines.size - 1)]

    companion object {
        val SLAP_FIRST = listOf("Ей! Защо ме плесна?", "Оууу! Това не беше хубаво.", "Хей, внимавай с ръцете!")
        val SLAP_REPEATED = listOf("Стига... Наистина ме боли.", "Ще се скрия, ако продължаваш.", "Защо си толкова груб с мен?")
        val HIT_FIRST = listOf("Ай! Това боли!", "Уф! Какво направих?", "Олеле, по-леко!")
        val HIT_REPEATED = listOf("Моля те, спри...", "Мислех, че сме приятели.", "Ще се разплача.")
        val GENTLE = listOf("Ммм, колко е приятно.", "Обичам, когато ме галиш.", "Още малко, моля.")
        val FORGIVE = listOf("Добре... прощавам ти.", "Така е по-добре.", "Благодаря, че се извини.")
        val PAT = listOf("Хи-хи, гъделичка!", "Още, още!", "Ура, потупване!")
        val TONGUE = listOf("Бе-е-е!", "Хи-хи, бе-е!", "Ето ти един език!")
    }
}
