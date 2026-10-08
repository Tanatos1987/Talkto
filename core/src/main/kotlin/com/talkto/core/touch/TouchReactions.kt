package com.talkto.core.touch

import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.i18n.Lang
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** How the user touched the pet. TWIRL is a horizontal drag that turned the pet around. */
enum class TouchKind { POKE, HIT, SLAP, GENTLE, PAT, TWIRL }

/** Which part of the pet the finger landed on. MISS is the scenery around it. */
enum class TouchZone { HEAD, EYE, MOUTH, BELLY, FEET, SIDE, MISS }

/** One pointer sample, in dp and milliseconds, relative to the avatar stage (0,0 top-left). */
data class TouchSample(val tMs: Long, val x: Float, val y: Float, val pressure: Float = 0f)

/** A classified touch plus where it landed, normalised to the stage (0..1). */
data class Touch(
    val kind: TouchKind,
    val nx: Float,
    val ny: Float,
    val strength: Float,
    val zone: TouchZone = TouchZone.BELLY,
)

/**
 * Where the pet's body is on the stage, normalised to 0..1 of the stage width ([cx], [rx]) and height ([cy], [ry]).
 * The 3D renderer projects the real body every frame; the 2D stage keeps the default.
 */
data class BodyBox(val cx: Float, val cy: Float, val rx: Float, val ry: Float) {

    /**
     * Zone of a point. u, v are measured in body radii from the centre, v grows downwards.
     * Layout follows the model: eyes at v ≈ -0.2, mouth at v ≈ +0.2, belly at v ≈ +0.45, feet at v ≈ +0.95.
     */
    fun zoneOf(nx: Float, ny: Float): TouchZone {
        val u = (nx - cx) / rx.coerceAtLeast(0.01f)
        val v = (ny - cy) / ry.coerceAtLeast(0.01f)
        val au = abs(u)
        return when {
            hypot(u, v * 0.9f) > 1.3f && v < 0.85f -> TouchZone.MISS
            v > 1.3f || au > 1.5f -> TouchZone.MISS
            v > 0.78f -> TouchZone.FEET
            v < -0.55f -> TouchZone.HEAD
            au > 0.72f -> TouchZone.SIDE
            v < 0.05f && au > 0.12f -> TouchZone.EYE
            v < 0.05f -> TouchZone.HEAD
            v < 0.32f && au < 0.38f -> TouchZone.MOUTH
            else -> TouchZone.BELLY
        }
    }

    companion object {
        val DEFAULT = BodyBox(0.5f, 0.58f, 0.3f, 0.26f)
    }
}

/** Shared between the renderer (writer, GL thread) and the touch layer (reader, UI thread). */
class BodyLocator {
    @Volatile var box: BodyBox = BodyBox.DEFAULT
}

/**
 * Turns raw pointer tracks into touch gestures. Thresholds are in dp, so they feel the same on every screen.
 *
 * - SLAP: a fast, short, mostly horizontal swipe across the pet.
 * - TWIRL: a slower horizontal drag; the pet turns with the finger.
 * - HIT: a very short, sharp tap (or a hard press where the screen reports pressure).
 * - PAT: two or more light taps in quick succession.
 * - GENTLE: a slow stroke (mostly vertical), or holding the finger still on the pet.
 * - POKE: an ordinary single tap.
 *
 * Pats are detected on the second tap; the first one is still reported as a POKE, so the pet reacts at once.
 */
class TouchClassifier(
    private val stageWidthDp: Float,
    private val stageHeightDp: Float,
    private val config: Config = Config(),
    private val body: () -> BodyBox = { BodyBox.DEFAULT },
) {
    data class Config(
        val slapMinDistanceDp: Float = 70f,
        val slapMinSpeedDpPerS: Float = 900f,
        val slapMaxMs: Long = 260,
        val twirlMinDistanceDp: Float = 40f,
        val strokeMinDistanceDp: Float = 30f,
        val strokeMaxSpeedDpPerS: Float = 500f,
        val holdMinMs: Long = 450,
        val tapMaxMs: Long = 220,
        val tapMaxMoveDp: Float = 12f,
        val hitMaxMs: Long = 45,
        val hardPressure: Float = 0.75f,
        val patWindowMs: Long = 650,
    )

    private var lastTapEndMs = Long.MIN_VALUE / 2
    private var tapStreak = 0

    /** True while a drag should turn the pet: far enough and mostly sideways. */
    fun isTwirl(dxDp: Float, dyDp: Float): Boolean =
        abs(dxDp) >= config.twirlMinDistanceDp * 0.5f && abs(dxDp) > abs(dyDp) * 1.3f

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
        // Where it happened: a tap counts where it started, a stroke where it spent most of its time.
        val cx = if (distance <= config.tapMaxMoveDp * 2) first.x else (track.sumOf { it.x.toDouble() } / track.size).toFloat()
        val cy = if (distance <= config.tapMaxMoveDp * 2) first.y else (track.sumOf { it.y.toDouble() } / track.size).toFloat()
        val nx = (cx / stageWidthDp).coerceIn(0f, 1f)
        val ny = (cy / stageHeightDp).coerceIn(0f, 1f)
        val zone = body().zoneOf(nx, ny)
        fun touch(kind: TouchKind, strength: Float) = Touch(kind, nx, ny, strength.coerceIn(0f, 1f), zone)

        val sideways = abs(dx) > abs(dy) * 1.3f
        if (distance >= config.slapMinDistanceDp && peakSpeed >= config.slapMinSpeedDpPerS && sideways && duration <= config.slapMaxMs) {
            tapStreak = 0
            return touch(TouchKind.SLAP, peakSpeed / (config.slapMinSpeedDpPerS * 3))
        }
        if (distance >= config.twirlMinDistanceDp && sideways) {
            tapStreak = 0
            return touch(TouchKind.TWIRL, abs(dx) / (stageWidthDp * 0.8f))
        }
        if (distance >= config.strokeMinDistanceDp && peakSpeed <= config.strokeMaxSpeedDpPerS * 2) {
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

/** A body effect the 3D renderer plays on top of the expression. */
enum class TouchEffect { NONE, WINCE, GIGGLE, HOP, PUSH, DIZZY, LOOK, LEAN, NUZZLE }

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
    val effect: TouchEffect = TouchEffect.NONE,
)

/**
 * The pet's temperament. It remembers recent rough handling: the first slap gets a surprised "hey!",
 * repeated ones make it sad and wary, and gentle touches win it back. Where it is touched matters: the belly
 * tickles, the eye hurts, the feet make it hop, the head likes a stroke. Lines never repeat twice in a row.
 */
class Temperament(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: () -> Float = { Math.random().toFloat() },
    private val lang: () -> Lang = { Lang.BG },
) {

    private var grievance = 0f // 0 = relaxed .. 1 = very upset
    private var lastMs = clock()
    private var dizziness = 0f
    private val lastLine = HashMap<List<String>, String>()

    val upset: Float get() = decayed()

    fun react(touch: Touch, petHappy: Boolean): TouchReaction {
        val g = decayed()
        val z = touch.zone
        val side = if (touch.nx < 0.5f) 1f else -1f // touched from the left, the head turns right
        return when (touch.kind) {
            TouchKind.SLAP -> {
                grievance = (g + 0.35f).coerceAtMost(1f)
                if (g > 0.6f) TouchReaction(Expression.SAD, Gesture.SHAKE, -15f, -4f, pick(SLAP_REPEATED), 3_500, 55f * side, 0.1f, TouchEffect.PUSH)
                else TouchReaction(Expression.ANGRY, Gesture.SHAKE, -10f, -2f, pick(SLAP_FIRST), 2_500, 45f * side, 0.1f, TouchEffect.PUSH)
            }
            TouchKind.HIT -> {
                grievance = (g + 0.25f).coerceAtMost(1f)
                when {
                    g > 0.6f -> TouchReaction(Expression.SAD, Gesture.NONE, -12f, -3f, pick(HIT_REPEATED), 3_000, 0f, 0.5f, TouchEffect.WINCE)
                    z == TouchZone.EYE -> TouchReaction(Expression.SAD, Gesture.SHAKE, -8f, -1.5f, pick(HIT_EYE), 2_500, 20f * side, 0.2f, TouchEffect.WINCE)
                    z == TouchZone.HEAD -> TouchReaction(Expression.SURPRISED, Gesture.NONE, -6f, -1f, pick(HIT_HEAD), 2_000, 0f, 0.6f, TouchEffect.WINCE)
                    z == TouchZone.BELLY -> TouchReaction(Expression.SURPRISED, Gesture.NONE, -6f, -1f, pick(HIT_BELLY), 2_000, 0f, 0.5f, TouchEffect.PUSH)
                    else -> TouchReaction(Expression.SURPRISED, Gesture.SHAKE, -6f, -1f, pick(HIT_FIRST), 2_000, 15f * side, 0.45f, TouchEffect.PUSH)
                }
            }
            TouchKind.TWIRL -> {
                dizziness = (dizziness + touch.strength.coerceAtLeast(0.3f)).coerceAtMost(2f)
                if (dizziness > 1.4f) {
                    dizziness = 0.6f
                    TouchReaction(Expression.CONFUSED, Gesture.NONE, 1f, 0.3f, pick(DIZZY), 3_000, effect = TouchEffect.DIZZY)
                } else {
                    TouchReaction(Expression.HAPPY, Gesture.NONE, 2f, 0.5f, pick(TWIRL), 1_500)
                }
            }
            TouchKind.GENTLE -> {
                grievance = (g - 0.3f).coerceAtLeast(0f)
                when {
                    g > 0.5f -> TouchReaction(Expression.NEUTRAL, Gesture.NOD, 4f, 1f, pick(FORGIVE), 2_000, effect = TouchEffect.LEAN)
                    z == TouchZone.HEAD -> TouchReaction(Expression.LOVE, Gesture.NONE, 5f, 1.5f, pick(GENTLE_HEAD), 2_800, effect = TouchEffect.NUZZLE)
                    z == TouchZone.BELLY -> TouchReaction(Expression.SLEEPY, Gesture.NONE, 5f, 1.5f, pick(GENTLE_BELLY), 3_000, effect = TouchEffect.LEAN)
                    z == TouchZone.MISS -> TouchReaction(Expression.THINKING, Gesture.NONE, 0f, 0f, pickSometimes(MISS, 0.5f), 1_500, effect = TouchEffect.LOOK)
                    else -> TouchReaction(Expression.LOVE, Gesture.NOD, 5f, 1.5f, pick(GENTLE), 2_500, effect = TouchEffect.LEAN)
                }
            }
            TouchKind.PAT -> {
                grievance = (g - 0.15f).coerceAtLeast(0f)
                when {
                    petHappy && g < 0.2f && random() < 0.35f -> TouchReaction(Expression.TONGUE, Gesture.BOUNCE, 4f, 1f, pick(TONGUE), 1_800)
                    z == TouchZone.BELLY -> TouchReaction(Expression.HAPPY, Gesture.NONE, 3f, 1f, pick(PAT_BELLY), 1_500, squash = 0.2f, effect = TouchEffect.GIGGLE)
                    z == TouchZone.HEAD -> TouchReaction(Expression.HAPPY, Gesture.NONE, 3f, 1f, pick(PAT_HEAD), 1_500, squash = 0.3f)
                    else -> TouchReaction(Expression.HAPPY, Gesture.BOUNCE, 3f, 1f, pick(PAT), 1_500, squash = 0.15f)
                }
            }
            TouchKind.POKE -> when {
                g > 0.5f -> TouchReaction(Expression.CONFUSED, Gesture.NONE, 0f, 0f, pickSometimes(POKE_WARY, 0.6f), 1_200, 25f * side, effect = TouchEffect.PUSH)
                z == TouchZone.EYE -> TouchReaction(Expression.CONFUSED, Gesture.NONE, -1f, 0f, pick(POKE_EYE), 1_600, effect = TouchEffect.WINCE)
                z == TouchZone.MOUTH -> if (random() < 0.5f) {
                    TouchReaction(Expression.TONGUE, Gesture.NONE, 1f, 0.3f, pick(POKE_MOUTH), 1_400)
                } else {
                    TouchReaction(Expression.SURPRISED, Gesture.NONE, 1f, 0.3f, pick(POKE_NOSE), 1_200, squash = 0.1f)
                }
                z == TouchZone.BELLY -> TouchReaction(Expression.HAPPY, Gesture.NONE, 2f, 0.5f, pick(POKE_BELLY), 1_600, squash = 0.15f, effect = TouchEffect.GIGGLE)
                z == TouchZone.FEET -> TouchReaction(Expression.SURPRISED, Gesture.BOUNCE, 1f, 0.2f, pick(POKE_FEET), 1_300, effect = TouchEffect.HOP)
                z == TouchZone.HEAD -> TouchReaction(Expression.SURPRISED, Gesture.NONE, 0.5f, 0.2f, pick(POKE_HEAD), 1_100, squash = 0.25f)
                z == TouchZone.SIDE -> TouchReaction(Expression.SURPRISED, Gesture.NONE, 0.5f, 0.2f, pick(POKE_SIDE), 1_100, 30f * side, effect = TouchEffect.PUSH)
                else -> TouchReaction(Expression.THINKING, Gesture.NONE, 0f, 0f, pickSometimes(MISS, 0.4f), 1_200, effect = TouchEffect.LOOK)
            }
        }
    }

    /** Grievance fades by half every two minutes, dizziness in a few seconds. */
    private fun decayed(): Float {
        val now = clock()
        val minutes = (now - lastMs).coerceAtLeast(0) / 60_000f
        lastMs = now
        grievance *= Math.pow(0.5, minutes / 2.0).toFloat()
        dizziness *= Math.pow(0.5, minutes * 12.0).toFloat()
        return grievance
    }

    private fun pickSometimes(lines: List<String>, chance: Float): String? = if (random() < chance) pick(lines) else null

    /** A random line in ZnaiKo's language, never the same one twice in a row for the same situation. */
    private fun pick(bgLines: List<String>): String {
        val lines = if (lang() == Lang.EN) EN.getValue(bgLines) else bgLines
        var i = (random() * lines.size).toInt().coerceIn(0, lines.size - 1)
        if (lines.size > 1 && lines[i] == lastLine[lines]) i = (i + 1) % lines.size
        return lines[i].also { lastLine[lines] = it }
    }

    companion object {
        val SLAP_FIRST = listOf("Ей! Защо ме плесна?", "Оууу! Това не беше хубаво.", "Хей, внимавай с ръцете!")
        val SLAP_REPEATED = listOf("Стига... Наистина ме боли.", "Ще се скрия, ако продължаваш.", "Защо си толкова груб с мен?")
        val HIT_FIRST = listOf("Ай! Това боли!", "Уф! Какво направих?", "Олеле, по-леко!")
        val HIT_HEAD = listOf("Ау, главичката ми!", "Бум! Звезди виждам!", "Ох, по темето ли?")
        val HIT_EYE = listOf("Окото ми! Много боли!", "Ай, нищо не виждам!", "Не в окото, моля те!")
        val HIT_BELLY = listOf("Уф, в коремчето!", "Ох, изкара ми въздуха!", "Ай, коремчето ми!")
        val HIT_REPEATED = listOf("Моля те, спри...", "Мислех, че сме приятели.", "Ще се разплача.")
        val GENTLE = listOf("Ммм, колко е приятно.", "Обичам, когато ме галиш.", "Още малко, моля.")
        val GENTLE_HEAD = listOf("Мррр, по главичката е най-хубаво.", "Ох, ще заспя от кеф.", "Още, по темето!")
        val GENTLE_BELLY = listOf("Мммм, коремчето ми се отпуска.", "Ааах, топличко е.", "Като мама ме галиш.")
        val FORGIVE = listOf("Добре... прощавам ти.", "Така е по-добре.", "Благодаря, че се извини.")
        val PAT = listOf("Хи-хи, гъделичка!", "Още, още!", "Ура, потупване!")
        val PAT_HEAD = listOf("Туп-туп по главичката!", "Хи-хи, като барабан съм!", "Послушен ли съм?")
        val PAT_BELLY = listOf("Бум-бум, коремчето ми!", "Ха-ха-ха, спри, гъдел е!", "Хи-хи-хи, пъпчето ми!")
        val TONGUE = listOf("Бе-е-е!", "Хи-хи, бе-е!", "Ето ти един език!")
        val TWIRL = listOf("Уиии!", "Въртележка!", "Още едно завъртане!", "Ето ме отзад!")
        val DIZZY = listOf("Завъртя ми се главата...", "Уф, всичко се върти!", "Спри, ще падна!")
        val POKE_EYE = listOf("Ой, окото ми!", "Ай, не бъркай в очите!", "Мигнах от изненада!")
        val POKE_MOUTH = listOf("Хам! Ще те ухапя!", "Ням-ням, вкусен пръст!", "Бе-е, хвана ме!")
        val POKE_NOSE = listOf("Пип! Това ми е носът.", "Апчих! Гъделичка.", "Бип-бип!")
        val POKE_BELLY = listOf("Хи-хи, гъдел!", "Ха-ха, коремчето ми!", "Ихи-хи, не там!")
        val POKE_FEET = listOf("Ой, пръстчетата ми!", "Хоп! Подскочих!", "Гъделичкаш ме по петите!")
        val POKE_HEAD = listOf("Туп! Здрасти и на теб.", "Кой чука на главата ми?", "Ей, тук съм!")
        val POKE_SIDE = listOf("Хей, не ме бутай!", "Ехо, ще падна!", "Щипе ме отстрани!")
        val POKE_WARY = listOf("Какво пак?", "Внимавай...", "Хм.")
        val MISS = listOf("Какво има там?", "Тук съм, до мен!", "Търсиш ли ме?")

        /** English lines for each Bulgarian set, same situations. */
        private val EN: Map<List<String>, List<String>> = mapOf(
            SLAP_FIRST to listOf("Hey! Why did you slap me?", "Ouch! That wasn't nice.", "Hey, careful with those hands!"),
            SLAP_REPEATED to listOf("Stop it... It really hurts.", "I'll hide if you keep doing that.", "Why are you so rough with me?"),
            HIT_FIRST to listOf("Ow! That hurts!", "Oof! What did I do?", "Ouch, gently!"),
            HIT_HEAD to listOf("Ow, my little head!", "Bonk! I'm seeing stars!", "Ouch, right on top?"),
            HIT_EYE to listOf("My eye! That really hurts!", "Ow, I can't see a thing!", "Not in the eye, please!"),
            HIT_BELLY to listOf("Oof, right in the tummy!", "Ooh, that knocked the air out of me!", "Ow, my tummy!"),
            HIT_REPEATED to listOf("Please stop...", "I thought we were friends.", "I'm going to cry."),
            GENTLE to listOf("Mmm, that's so nice.", "I love it when you stroke me.", "A little more, please."),
            GENTLE_HEAD to listOf("Purr, head strokes are the best.", "Ooh, I could fall asleep.", "More, on the top!"),
            GENTLE_BELLY to listOf("Mmm, my tummy is so relaxed.", "Aaah, that's warm.", "You stroke me just like mum does."),
            FORGIVE to listOf("All right... I forgive you.", "That's better.", "Thank you for saying sorry."),
            PAT to listOf("Hee-hee, that tickles!", "More, more!", "Hooray, pats!"),
            PAT_HEAD to listOf("Pat-pat on my head!", "Hee-hee, I'm like a drum!", "Am I being good?"),
            PAT_BELLY to listOf("Boom-boom, my tummy!", "Ha-ha-ha, stop, it tickles!", "Hee-hee-hee, my belly button!"),
            TONGUE to listOf("Bleh!", "Hee-hee, bleh!", "Here's a tongue for you!"),
            TWIRL to listOf("Wheee!", "Merry-go-round!", "One more spin!", "Here's my back!"),
            DIZZY to listOf("My head is spinning...", "Oof, everything is going round!", "Stop, I'm going to fall over!"),
            POKE_EYE to listOf("Oh, my eye!", "Ow, no poking my eyes!", "I blinked in surprise!"),
            POKE_MOUTH to listOf("Chomp! I'll bite you!", "Yum-yum, a tasty finger!", "Bleh, you caught me!"),
            POKE_NOSE to listOf("Beep! That's my nose.", "Achoo! That tickles.", "Beep-beep!"),
            POKE_BELLY to listOf("Hee-hee, that tickles!", "Ha-ha, my tummy!", "Tee-hee, not there!"),
            POKE_FEET to listOf("Oh, my little toes!", "Hop! I jumped!", "You're tickling my feet!"),
            POKE_HEAD to listOf("Knock! Hello to you too.", "Who's knocking on my head?", "Hey, I'm here!"),
            POKE_SIDE to listOf("Hey, don't push me!", "Whoa, I'll fall over!", "That pinches on the side!"),
            POKE_WARY to listOf("What now?", "Careful...", "Hmm."),
            MISS to listOf("What's over there?", "I'm here, next to me!", "Are you looking for me?"),
        )
    }
}

/**
 * Finger-driven turning, handed from the touch layer (UI thread) to the renderer (GL thread).
 * The renderer drains it once per frame with [take].
 */
class TwirlInput {
    data class Step(val dragDeg: Float, val flingDegPerS: Float?, val dragging: Boolean)

    private var drag = 0f
    private var fling: Float? = null
    private var dragging = false

    @Synchronized fun drag(deltaDeg: Float) {
        drag += deltaDeg
        dragging = true
    }

    @Synchronized fun release(degPerSecond: Float) {
        fling = degPerSecond.coerceIn(-MAX_SPIN, MAX_SPIN)
        dragging = false
    }

    @Synchronized fun take(): Step = Step(drag, fling, dragging).also { drag = 0f; fling = null }

    companion object {
        const val MAX_SPIN = 1_440f
    }
}
