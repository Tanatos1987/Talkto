package com.talkto.app.avatar3d

import com.talkto.app.avatar3d.Painter.Companion.argb
import com.talkto.app.avatar3d.Painter.Companion.darken
import com.talkto.app.avatar3d.Painter.Companion.lighten
import com.talkto.app.avatar3d.Painter.Companion.luminance
import com.talkto.app.avatar3d.Painter.Companion.rgb
import com.talkto.app.avatar3d.Painter.Companion.v
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.look.Aura
import com.talkto.core.look.Brows
import com.talkto.core.look.CreatureLook
import com.talkto.core.look.Ears
import com.talkto.core.look.EyeStyle
import com.talkto.core.look.Hat
import com.talkto.core.look.HeadTop
import com.talkto.core.look.MouthStyle
import com.talkto.core.look.Nose
import com.talkto.core.look.Pattern
import com.talkto.core.look.Tail
import com.talkto.core.look.Wings
import com.talkto.core.pet.Knowledge
import com.talkto.core.pet.LifeStage
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin

/** Animation values of one frame, written by the renderer and read by the models. */
internal class Anim {
    var time = 0f
    var lookX = 0f
    var lookY = 0f
    var blink = 0f
    var mouthOpen = 0f
    var mouthWide = 0.5f
    var tongue = 0f
    var wince = false
    /** Body roll in degrees: the sprout sways against it. */
    var sway = 0f
    var waving = false
    /** 0..1: how much of the walk cycle shows; [walkPhase] in radians. */
    var walk = 0f
    var walkPhase = 0f
}

/** One eye on the face, in design space: centre and size factor. */
internal data class EyeSpot(val x: Float, val y: Float, val size: Float)

/** The face's design space: frames for features that keep their own shape, and the skin's depth. */
internal interface FaceSpace {
    /** A frame at design-space (x, y, z) on the head, undistorted by the head's stretch. */
    fun at(x: Float, y: Float, z: Float): FloatArray
    /** Depth of the skin at (x, y), without the belly. */
    fun surf(x: Float, y: Float): Float
}

/**
 * Draws the creature described by a [CreatureLook]: body shape and colours, belly and pattern, eyes (one to three,
 * five styles), brows, lashes, nose, mouth, cheeks, whiskers, ears, head top, tail, wings and feet.
 * Face features are placed on the body surface in design space and keep their own proportions on any body.
 */
internal class CreatureModel(private val p: Painter) {

    private val outfit = OutfitModel(p)

    /** Head frame and the scale that undoes its stretch for face features. */
    private var headM = Mat4.identity()
    private var kx = 1f
    private var ky = 1f
    private var kz = 1f
    private var plan = BodyPlan.of(CreatureLook())

    private val face = object : FaceSpace {
        override fun at(x: Float, y: Float, z: Float) = feature(x, y, z)
        override fun surf(x: Float, y: Float) = this@CreatureModel.surf(x, y)
    }

    fun draw(root: FloatArray, s: SceneState, a: Anim) {
        val look = s.look.clamped()
        plan = BodyPlan.of(look)
        val shine = 0.1f + look.glossy * 0.8f
        val rim = 0.35f + look.glossy * 0.35f
        val body = argb(look.bodyColor).let { if (s.sleeping) darken(it, 0.88f) else it }

        val lathe = plan.lathe
        if (lathe != null) {
            p.part(if (lathe === com.talkto.core.avatar3d.Lathe.PEAR) p.pear else p.bean, plan.latheFrame(root), body, shine = shine, rim = rim)
        }
        for (b in plan.parts) p.part(p.sphere, BodyPlan.raw(root, b), body, sc = v(b.rx, b.ry, b.rz), shine = shine, rim = rim)
        val mainM = BodyPlan.frame(root, plan.main)
        headM = BodyPlan.frame(root, plan.head)
        val hx = plan.head.rx / BodyPlan.REF.rx
        val hy = plan.head.ry / BodyPlan.REF.ry
        val hz = plan.head.rz / BodyPlan.REF.rz
        val k = cbrt(hx * hy * hz)
        kx = k / hx; ky = k / hy; kz = k / hz

        if (look.belly) p.part(p.sphere, mainM, argb(look.bellyColor), t = v(0f, -0.42f, 0.62f), sc = v(0.56f, 0.42f, 0.3f), shine = shine * 0.6f, rim = rim * 0.6f)
        drawPattern(root, mainM, look)
        drawLimbs(mainM, look, body, a)
        drawTail(mainM, look, body, a)
        drawWings(mainM, look, body, a, s.pose.expression)
        drawEars(look, body, a)
        val eyes = eyeSpots(look)
        drawFace(look, s, a, eyes, body)
        if (s.outfit.hat == Hat.NONE) drawTop(look, a, Knowledge.look(s.updates))
        outfit.draw(root, headM, plan, s.outfit, eyes, a, face)

        if (s.stage == LifeStage.EGG) {
            p.part(p.lowerHemisphere, mainM, rgb(0xFFF4DC), t = v(0f, -0.3f, 0f), sc = v(1.08f, 0.78f, 1.04f), shine = 0.5f, rim = 0.3f)
        }
    }

    /** Transparent magic around the body; drawn last, blended. */
    fun drawAura(root: FloatArray, look: CreatureLook, a: Anim) {
        if (look.aura == Aura.NONE) return
        val plan = BodyPlan.of(look)
        val cy = (plan.top + plan.bottom) / 2f
        val hy = (plan.top - plan.bottom) / 2f
        when (look.aura) {
            Aura.NONE -> Unit
            Aura.GLOW -> {
                // A glowing disc behind the body, so the body itself keeps its colours.
                val pulse = 1f + sin(a.time * 2.2f) * 0.05f
                for ((k, alpha) in listOf(1.45f to 0.16f, 1.25f to 0.22f, 1.08f to 0.3f)) {
                    p.part(p.sphere, root, rgb(0xFFE066), t = v(0f, cy, -0.95f), sc = v(plan.halfWidth * k * pulse, hy * k * pulse, 0.05f), shine = 0f, rim = 0f, alpha = alpha, glow = 1f)
                }
            }
            Aura.SPARKLES -> {
                val colours = intArrayOf(0xFFE066, 0xFFFFFF, 0xFF8FA3, 0xBDE0FE)
                for (i in 0 until 12) {
                    val ang = a.time * 0.7f + i * (2f * PI.toFloat() / 12f)
                    val twinkle = (sin(a.time * 5f + i * 1.7f) * 0.5f + 0.5f)
                    val r = plan.halfWidth + 0.35f
                    val size = 0.05f * (0.6f + twinkle)
                    p.part(
                        p.sphere, root, rgb(colours[i % colours.size]),
                        t = v(cos(ang) * r, cy + sin(a.time * 1.3f + i) * hy * 0.8f, sin(ang) * r * 0.8f),
                        sc = v(size, size, size), shine = 1f, rim = 0f, alpha = 0.35f + 0.6f * twinkle, glow = 1f,
                    )
                }
            }
            Aura.RAINBOW -> {
                val bands = intArrayOf(0xE63946, 0xF4A261, 0xFFE066, 0x52B788, 0x4CC9F0, 0x9B5DE5)
                bands.forEachIndexed { i, c ->
                    val r = plan.halfWidth + 0.25f + (bands.size - 1 - i) * 0.09f
                    p.part(p.arc, root, rgb(c), t = v(0f, plan.top - 0.35f, -0.55f), sc = v(r, r, 1f), shine = 0f, rim = 0f, alpha = 0.8f, glow = 0.8f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A face feature at design-space ([x], [y], [z]) on the head, keeping its own shape whatever the head's. */
    private fun feature(x: Float, y: Float, z: Float): FloatArray =
        Mat4.multiply(Mat4.multiply(headM, Mat4.translation(x, y, z)), Mat4.scale(kx, ky, kz))

    private fun feat(mesh: GpuMesh, color: FloatArray, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, shine: Float = 0.5f, rim: Float = 0.35f, glow: Float = 0f) =
        p.part(mesh, feature(x, y, z), color, sc = v(sx, sy, sz), shine = shine, rim = rim, glow = glow)

    /** Head design space to the creature's space and back, to measure the skin that is really drawn. */
    private fun headX(x: Float) = plan.head.x + x * plan.head.rx / BodyPlan.REF.rx
    private fun headY(y: Float) = plan.head.y + y * plan.head.ry / BodyPlan.REF.ry
    private fun headZ(z: Float) = (z - plan.head.z) / (plan.head.rz / BodyPlan.REF.rz)

    /** Depth of the skin at design-space (x, y) on the head. */
    private fun surf(x: Float, y: Float): Float {
        val z = plan.bodyFront(headX(x), headY(y))
        return if (z <= 0f) BodyPlan.refSurface(x, y) else headZ(z)
    }

    /** Front of the face at (x, y): the skin, or the belly where it bulges out further. */
    private fun faceZ(x: Float, y: Float): Float {
        val z = plan.frontZ(headX(x), headY(y))
        return if (z <= 0f) surf(x, y) else maxOf(surf(x, y), headZ(z))
    }

    /** A point of the main blob's design space in the creature's space, with the skin depth there (front or back). */
    private fun onMain(x: Float, y: Float, front: Boolean): FloatArray {
        val m = plan.main
        val wx = m.x + x * m.rx / BodyPlan.REF.rx
        val wy = m.y + y * m.ry / BodyPlan.REF.ry
        val z = if (front) plan.bodyFront(wx, wy) else plan.backZ(wx, wy)
        return v(wx, wy, z)
    }

    private fun eyeSpots(look: CreatureLook): List<EyeSpot> {
        val y = 0.2f + look.eyeHeight
        val sp = look.eyeSpacing
        val s = look.eyeSize
        return when (look.eyeCount) {
            1 -> listOf(EyeSpot(0f, y + 0.02f, s * 1.35f))
            3 -> listOf(EyeSpot(-0.4f * sp, y - 0.03f, s * 0.8f), EyeSpot(0f, y + 0.12f, s * 0.8f), EyeSpot(0.4f * sp, y - 0.03f, s * 0.8f))
            else -> listOf(EyeSpot(-0.33f * sp, y, s), EyeSpot(0.33f * sp, y, s))
        }
    }

    // ------------------------------------------------------------------ body

    private fun drawPattern(root: FloatArray, mainM: FloatArray, look: CreatureLook) {
        val c = argb(look.patternColor)
        when (look.pattern) {
            Pattern.NONE -> Unit
            Pattern.SPOTS -> {
                // Front spots stay off the face and the belly; back spots show when ZnaiKo turns.
                val front = listOf(-0.72f to 0.3f, 0.74f to 0.05f, -0.62f to -0.5f, 0.6f to -0.6f, 0.45f to 0.66f)
                val back = listOf(0.3f to 0.5f, -0.45f to 0.1f, 0.5f to -0.35f, -0.2f to -0.55f, 0f to 0.75f)
                for ((x, y) in front) onMain(x, y, true).let { q -> if (q[2] > 0f) p.part(p.sphere, root, c, t = v(q[0], q[1], q[2] - 0.07f), sc = v(0.13f, 0.13f, 0.13f), shine = 0.3f) }
                for ((x, y) in back) onMain(x, y, false).let { q -> if (q[2] < 0f) p.part(p.sphere, root, c, t = v(q[0], q[1], q[2] + 0.07f), sc = v(0.14f, 0.14f, 0.14f), shine = 0.3f) }
            }
            Pattern.STRIPES -> {
                // Tabby arcs over the back, measured on the real body at each height.
                for (f in floatArrayOf(0.66f, 0.38f, 0.1f, -0.18f, -0.46f)) {
                    val y = plan.main.y + f * plan.main.ry
                    val (rw, rd) = plan.cut(y)
                    val cx = plan.lathe?.cut(y)?.third ?: plan.main.x
                    val m = Mat4.multiply(Mat4.multiply(root, Mat4.translation(cx, y, 0f)), Mat4.rotationX(-90f))
                    p.part(p.stripe, m, c, sc = v((rw - kotlin.math.abs(cx)) * 0.99f, rd * 0.99f, 1.6f), shine = 0.3f, rim = 0.2f)
                }
            }
            Pattern.HEART -> {
                val z = if (look.belly) 0.9f else surf(0f, -0.4f) + 0.01f
                p.part(p.sphere, mainM, c, t = v(-0.075f, -0.34f, z), sc = v(0.09f, 0.085f, 0.04f), shine = 0.6f)
                p.part(p.sphere, mainM, c, t = v(0.075f, -0.34f, z), sc = v(0.09f, 0.085f, 0.04f), shine = 0.6f)
                val m = Mat4.multiply(Mat4.multiply(mainM, Mat4.translation(0f, -0.42f, z)), Mat4.rotationZ(45f))
                p.part(p.box, m, c, sc = v(0.13f, 0.13f, 0.06f), shine = 0.6f)
            }
            Pattern.STARS -> {
                val spots = listOf(-0.7f to 0.35f, 0.72f to 0.2f, -0.6f to -0.45f, 0.62f to -0.5f, 0.35f to 0.7f, -0.3f to 0.72f, 0.8f to -0.15f, -0.82f to -0.05f)
                spots.forEachIndexed { i, (x, y) ->
                    val q = onMain(x, y, true)
                    if (q[2] <= 0f) return@forEachIndexed
                    val m = Mat4.multiply(Mat4.multiply(root, Mat4.translation(q[0], q[1], q[2] - 0.01f)), Mat4.rotationZ(i * 23f))
                    val star = lighten(c, 0.35f)
                    p.part(p.box, m, star, sc = v(0.09f, 0.09f, 0.05f), shine = 1.2f, glow = 0.7f)
                    p.part(p.box, Mat4.multiply(m, Mat4.rotationZ(45f)), star, sc = v(0.09f, 0.09f, 0.05f), shine = 1.2f, glow = 0.7f)
                }
            }
        }
    }

    private fun drawLimbs(mainM: FloatArray, look: CreatureLook, body: FloatArray, a: Anim) {
        val feet = look.feetColor?.let(::argb) ?: body
        val fs = look.feetSize
        for (side in intArrayOf(-1, 1)) {
            val stride = sin(a.walkPhase + if (side < 0) 0f else PI.toFloat()) * a.walk
            p.part(p.sphere, mainM, feet, t = v(0.42f * side, -0.9f + maxOf(0f, stride) * 0.08f, 0.22f + stride * 0.2f), sc = v(0.3f * fs, 0.14f * fs, 0.36f * fs))
        }
        val armSwing = sin(a.walkPhase) * 28f * a.walk
        val wave = if (a.waving) sin(a.time * 14f) * 25f + 110f else 15f
        val asz = look.armSize
        for (side in intArrayOf(-1, 1)) {
            val lift = if (side > 0) wave else -15f
            val m = Mat4.multiply(
                Mat4.multiply(Mat4.multiply(mainM, Mat4.translation(0.88f * side, -0.15f, 0.05f)), Mat4.rotationZ(lift)),
                Mat4.rotationX(armSwing * side),
            )
            p.part(p.sphere, m, body, t = v(0f, 0.12f * asz, 0f), sc = v(0.15f * asz, 0.3f * asz, 0.15f * asz))
        }
    }

    private fun drawTail(mainM: FloatArray, look: CreatureLook, body: FloatArray, a: Anim) {
        val wag = sin(a.time * 3.1f)
        when (look.tail) {
            Tail.NONE -> Unit
            Tail.CAT -> {
                val tip = if (look.pattern != Pattern.NONE) argb(look.patternColor) else darken(body, 0.85f)
                for (i in 0..8) {
                    val t = i / 8f
                    val x = wag * 0.28f * t * t
                    val y = -0.62f + 0.95f * t
                    val z = -0.84f - 0.32f * sin(t * PI.toFloat() * 0.5f) + 0.14f * t * t
                    val r = 0.085f * (1f - 0.25f * t)
                    p.part(p.sphere, mainM, if (i >= 7) tip else body, t = v(x, y, z), sc = v(r, r, r))
                }
            }
            Tail.BUNNY -> p.part(p.sphere, mainM, if (look.belly) argb(look.bellyColor) else lighten(body, 0.5f), t = v(0f, -0.55f, -0.86f), sc = v(0.2f, 0.2f, 0.2f), shine = 0.05f, rim = 0.8f)
            Tail.DRAGON -> {
                val spike = argb(look.topColor)
                for (i in 0..6) {
                    val t = i / 6f
                    val x = sin(a.time * 1.5f + t * 3f) * 0.16f * t
                    val y = -0.68f - 0.24f * t
                    val z = -0.8f - 0.85f * t
                    val r = 0.16f * (1f - 0.7f * t) + 0.03f
                    p.part(p.sphere, mainM, body, t = v(x, y, z), sc = v(r, r, r * 1.2f))
                    val sm = Mat4.multiply(Mat4.multiply(mainM, Mat4.translation(x, y + r * 0.8f, z)), Mat4.rotationX(-25f))
                    p.part(p.cone, sm, spike, sc = v(r * 0.45f, r * 0.9f, r * 0.45f), shine = 0.7f)
                }
            }
            Tail.FOX -> {
                val m = Mat4.multiply(Mat4.multiply(Mat4.multiply(mainM, Mat4.translation(0f, -0.45f, -0.88f)), Mat4.rotationZ(wag * 10f)), Mat4.rotationX(-38f))
                p.part(p.sphere, m, body, t = v(0f, 0.38f, 0f), sc = v(0.27f, 0.5f, 0.25f), shine = 0.1f, rim = 0.6f)
                p.part(p.sphere, m, if (look.belly) argb(look.bellyColor) else rgb(0xFFFFFF), t = v(0f, 0.82f, 0f), sc = v(0.18f, 0.2f, 0.17f), shine = 0.1f, rim = 0.6f)
            }
        }
    }

    private fun drawWings(mainM: FloatArray, look: CreatureLook, body: FloatArray, a: Anim, e: Expression) {
        if (look.wings == Wings.NONE) return
        val excited = e == Expression.HAPPY || e == Expression.LOVE || e == Expression.SURPRISED
        val flap = sin(a.time * if (excited) 9f else 2.6f) * if (excited) 24f else 9f
        val wing = argb(look.wingColor)
        for (side in intArrayOf(-1, 1)) {
            val frame = Mat4.multiply(Mat4.multiply(mainM, Mat4.translation(0.32f * side, 0.18f, -0.7f)), Mat4.rotationY(side * (38f + flap)))
            when (look.wings) {
                Wings.NONE -> Unit
                Wings.BUTTERFLY -> {
                    p.part(p.sphere, frame, wing, t = v(0.5f * side, 0.3f, 0f), sc = v(0.52f, 0.42f, 0.03f), shine = 0.9f, rim = 0.7f)
                    p.part(p.sphere, frame, lighten(wing, 0.25f), t = v(0.38f * side, -0.24f, 0.005f), sc = v(0.36f, 0.3f, 0.03f), shine = 0.9f, rim = 0.7f)
                    p.part(p.sphere, frame, darken(wing, 0.6f), t = v(0.62f * side, 0.38f, 0.03f), sc = v(0.12f, 0.1f, 0.02f), shine = 0.5f)
                    p.part(p.sphere, frame, rgb(0xFFFFFF), t = v(0.42f * side, -0.26f, 0.03f), sc = v(0.08f, 0.07f, 0.02f), shine = 0.5f)
                }
                Wings.BAT, Wings.DRAGON -> {
                    val membrane = if (look.wings == Wings.BAT) darken(wing, 0.55f) else wing
                    for (k in 0..2) {
                        val fan = Mat4.multiply(frame, Mat4.rotationZ(side * (32f - 26f * k)))
                        p.part(p.sphere, fan, membrane, t = v(0.46f * side, 0f, 0f), sc = v(0.5f, 0.13f, 0.02f), shine = 0.4f, rim = 0.5f)
                        if (look.wings == Wings.DRAGON) {
                            val spar = Mat4.multiply(fan, Mat4.rotationZ(90f))
                            p.part(p.tube, spar, body, t = v(0f, -0.46f * side, 0.02f), sc = v(0.025f, 0.95f, 0.025f))
                        }
                    }
                    p.part(p.cone, Mat4.multiply(Mat4.multiply(frame, Mat4.translation(0.1f * side, 0.12f, 0f)), Mat4.rotationZ(-30f * side)), darken(membrane, 0.7f), sc = v(0.04f, 0.14f, 0.04f))
                }
                Wings.ANGEL -> {
                    val feather = lighten(wing, 0.7f)
                    for (k in 0..3) {
                        p.part(
                            p.sphere, frame, if (k % 2 == 0) feather else lighten(wing, 0.5f),
                            t = v((0.32f + 0.1f * k) * side, 0.3f - 0.15f * k, 0.01f * k), sc = v(0.42f - 0.06f * k, 0.13f, 0.05f), shine = 0.2f, rim = 0.9f,
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ head

    private fun drawEars(look: CreatureLook, body: FloatArray, a: Anim) {
        val inner = argb(look.earInnerColor)
        for (side in intArrayOf(-1, 1)) {
            when (look.ears) {
                Ears.NONE -> Unit
                Ears.CAT -> {
                    val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.5f * side, 0.66f, 0.05f)), Mat4.rotationZ(-22f * side))
                    p.part(p.cone, m, body, sc = v(0.24f, 0.44f, 0.13f))
                    p.part(p.cone, m, inner, t = v(0f, 0.03f, 0.07f), sc = v(0.14f, 0.3f, 0.06f), shine = 0.1f)
                }
                Ears.BUNNY -> {
                    val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.3f * side, 0.8f, -0.02f)), Mat4.rotationZ(-10f * side + sin(a.time * 2f + side) * 4f))
                    p.part(p.sphere, m, body, t = v(0f, 0.42f, 0f), sc = v(0.15f, 0.48f, 0.1f))
                    p.part(p.sphere, m, inner, t = v(0f, 0.42f, 0.06f), sc = v(0.08f, 0.36f, 0.05f), shine = 0.1f)
                }
                Ears.BEAR -> {
                    p.part(p.sphere, headM, body, t = v(0.62f * side, 0.72f, 0f), sc = v(0.24f, 0.24f, 0.12f))
                    p.part(p.sphere, headM, inner, t = v(0.62f * side, 0.72f, 0.08f), sc = v(0.13f, 0.13f, 0.05f), shine = 0.1f)
                }
                Ears.MOUSE -> {
                    p.part(p.sphere, headM, body, t = v(0.72f * side, 0.76f, -0.05f), sc = v(0.36f, 0.36f, 0.07f))
                    p.part(p.sphere, headM, inner, t = v(0.72f * side, 0.76f, 0.0f), sc = v(0.24f, 0.24f, 0.04f), shine = 0.1f)
                }
                Ears.ELF -> {
                    val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.9f * side, 0.3f, 0f)), Mat4.rotationZ(-72f * side))
                    p.part(p.cone, m, body, sc = v(0.12f, 0.5f, 0.08f))
                    p.part(p.cone, m, inner, t = v(0f, 0.04f, 0.04f), sc = v(0.06f, 0.36f, 0.04f), shine = 0.1f)
                }
                Ears.DOG -> {
                    val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.86f * side, 0.62f, 0.12f)), Mat4.rotationZ((16f + sin(a.time * 2.5f) * 5f) * side))
                    p.part(p.sphere, m, darken(body, 0.8f), t = v(0.1f * side, -0.3f, 0f), sc = v(0.2f, 0.42f, 0.13f), shine = 0.2f)
                }
            }
        }
    }

    /** What grows on the head when no hat covers it. The sprout grows with every update. */
    private fun drawTop(look: CreatureLook, a: Anim, k: Knowledge.Look) {
        val top = argb(look.topColor)
        when (look.top) {
            HeadTop.NONE -> Unit
            HeadTop.SPROUT -> {
                val sway = sin(a.time * 1.7f) * 8f + a.sway * 0.6f
                val stem = Mat4.multiply(
                    headM,
                    Mat4.multiply(Mat4.translation(0f, 0.9f, 0f), Mat4.multiply(Mat4.rotationZ(sway), Mat4.scale(k.sproutScale, k.sproutScale, k.sproutScale))),
                )
                p.part(p.tube, stem, top, t = v(0f, 0.12f, 0f), sc = v(0.035f, 0.26f, 0.035f), shine = 0.2f)
                for (i in 0 until k.leaves) {
                    val side = if (i % 2 == 0) -1f else 1f
                    val y = 0.27f - (i / 2) * 0.1f
                    val m = Mat4.multiply(stem, Mat4.multiply(Mat4.translation(0.12f * side, y, (i / 2) * 0.05f), Mat4.rotationZ(-50f * side)))
                    p.part(p.sphere, m, top, sc = v(0.16f, 0.07f, 0.1f), shine = 0.5f, rim = 0.5f)
                }
                if (k.flower) {
                    for (i in 0 until 5) {
                        val ang = 2 * PI * i / 5 + a.time * 0.3
                        p.part(p.sphere, stem, rgb(0xFF8FA3), t = v((0.07 * cos(ang)).toFloat(), 0.3f, (0.07 * sin(ang)).toFloat()), sc = v(0.065f, 0.035f, 0.065f), shine = 0.4f, rim = 0.4f)
                    }
                    p.part(p.sphere, stem, rgb(0xFFC857), t = v(0f, 0.31f, 0f), sc = v(0.04f, 0.035f, 0.04f), shine = 0.8f)
                }
                if (k.star) {
                    val bob = sin(a.time * 2.2f) * 0.03f
                    val m = Mat4.multiply(stem, Mat4.multiply(Mat4.translation(0f, 0.47f + bob, 0f), Mat4.rotationY(a.time * 90f)))
                    p.part(p.cone, m, rgb(0xFFE066), sc = v(0.06f, 0.07f, 0.06f), shine = 1.2f, rim = 0.8f, glow = 0.3f)
                    p.part(p.cone, Mat4.multiply(m, Mat4.rotationX(180f)), rgb(0xFFE066), sc = v(0.06f, 0.07f, 0.06f), shine = 1.2f, rim = 0.8f, glow = 0.3f)
                }
            }
            HeadTop.ANTENNAE -> for (side in intArrayOf(-1, 1)) {
                val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.25f * side, 0.85f, 0f)), Mat4.rotationZ(-18f * side + sin(a.time * 3f + side) * 6f))
                p.part(p.tube, m, darken(top, 0.8f), t = v(0f, 0.25f, 0f), sc = v(0.025f, 0.5f, 0.025f))
                p.part(p.sphere, m, top, t = v(0f, 0.53f, 0f), sc = v(0.09f, 0.09f, 0.09f), shine = 1f, glow = 0.35f)
            }
            HeadTop.HORNS -> for (side in intArrayOf(-1, 1)) {
                val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.36f * side, 0.8f, 0.05f)), Mat4.rotationZ(-20f * side))
                p.part(p.cone, m, top, sc = v(0.1f, 0.32f, 0.1f), shine = 0.8f)
            }
            HeadTop.TUFT -> for (i in -1..1) {
                val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0.1f * i, 0.9f - 0.02f * i * i, 0.02f)), Mat4.rotationZ(-24f * i + sin(a.time * 2f) * 4f))
                p.part(p.cone, m, top, sc = v(0.1f, 0.36f, 0.1f), shine = 0.3f)
            }
            HeadTop.UNICORN -> {
                val m = Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0f, 0.8f, 0.3f)), Mat4.rotationX(22f))
                p.part(p.cone, m, top, sc = v(0.11f, 0.72f, 0.11f), shine = 1.3f, rim = 0.8f, glow = 0.15f)
                for (i in 0 until 4) {
                    val h = 0.1f + i * 0.13f
                    val r = 0.11f * (1f - h / 0.72f) + 0.004f
                    val ring = Mat4.multiply(Mat4.multiply(Mat4.multiply(m, Mat4.translation(0f, h, 0f)), Mat4.rotationZ(14f)), Mat4.rotationX(90f))
                    p.part(p.band, ring, lighten(top, 0.55f), sc = v(r, r, 0.6f), shine = 1f, glow = 0.2f)
                }
            }
        }
    }

    private fun drawFace(look: CreatureLook, s: SceneState, a: Anim, eyes: List<EyeSpot>, body: FloatArray) {
        val e = s.pose.expression
        val ink = rgb(0x2D2A32)
        val white = rgb(0xFFFFFF)
        val styleOpen = if (look.eyes == EyeStyle.SLEEPY) 0.55f else 1f
        val eyeOpen = when {
            s.sleeping -> 0.08f
            e == Expression.SLEEPY -> 0.35f
            e == Expression.HAPPY || e == Expression.TONGUE -> 0.6f
            e == Expression.SURPRISED -> 1.2f
            else -> 1f
        } * styleOpen * (1f - a.blink * 0.92f) * (if (a.wince) 0.06f else 1f)
        val (ew, eh) = when (look.eyes) {
            EyeStyle.BIG -> 0.25f to 0.27f
            EyeStyle.OVAL -> 0.17f to 0.26f
            else -> 0.2f to 0.22f
        }
        val iris = if (e == Expression.LOVE) rgb(0xF15BB5) else argb(look.irisColor)
        val colouredIris = luminance(iris) > 0.22f

        for (eye in eyes) {
            val s1 = eye.size
            val z = surf(eye.x, eye.y)
            val side = if (eye.x > 0.01f) 1f else if (eye.x < -0.01f) -1f else 0f
            feat(p.sphere, white, eye.x, eye.y, z - 0.09f * s1, ew * s1, eh * s1 * eyeOpen.coerceAtLeast(0.05f), 0.12f * s1, shine = 0.8f)
            if (eyeOpen > 0.2f) {
                val px = eye.x + a.lookX * 0.06f * s1
                val py = eye.y + a.lookY * 0.05f * s1
                val pz = z + 0.02f * s1
                val pupil = (if (e == Expression.LOVE) 0.13f else if (e == Expression.SURPRISED) 0.08f else 0.1f) * s1 * (if (look.eyes == EyeStyle.BIG) 1.25f else 1f)
                val open = eyeOpen.coerceAtMost(1f)
                if (colouredIris) {
                    feat(p.sphere, iris, px, py, pz, pupil * 1.25f, pupil * 1.25f * open, 0.05f * s1, shine = 1f)
                    feat(p.sphere, ink, px, py, pz + 0.012f * s1, pupil * 0.6f, pupil * 0.6f * open, 0.05f * s1, shine = 1f)
                } else {
                    feat(p.sphere, iris, px, py, pz, pupil, pupil * open, 0.05f * s1, shine = 1f)
                }
                feat(p.sphere, white, px + 0.035f * s1, py + 0.04f * s1, z + 0.075f * s1, 0.028f * s1, 0.028f * s1, 0.01f, rim = 0f)
                if (look.eyes == EyeStyle.SPARKLE || look.eyes == EyeStyle.BIG) {
                    feat(p.sphere, white, px - 0.035f * s1, py - 0.035f * s1, z + 0.075f * s1, 0.016f * s1, 0.016f * s1, 0.01f, rim = 0f)
                }
                if (look.eyes == EyeStyle.SPARKLE) {
                    val tw = 0.7f + 0.3f * sin(a.time * 6f + eye.x * 5f)
                    val m = Mat4.multiply(feature(px + 0.01f * s1, py + 0.065f * s1, z + 0.078f * s1), Mat4.rotationZ(45f))
                    p.part(p.box, m, white, sc = v(0.03f * s1 * tw, 0.03f * s1 * tw, 0.005f), rim = 0f, glow = 0.8f)
                }
                if (look.lashes) {
                    for (k in -1..1) {
                        val lx = eye.x + k * 0.07f * s1 + side * 0.02f * s1
                        val ly = eye.y + eh * s1 * open * 0.95f
                        val m = Mat4.multiply(feature(lx, ly, surf(lx, ly) - 0.01f), Mat4.rotationZ(-k * 28f - side * 12f))
                        p.part(p.tube, m, ink, t = v(0f, 0.035f * s1, 0f), sc = v(0.012f, 0.07f * s1, 0.012f))
                    }
                }
            }
            // Brows: always there when chosen, otherwise only for strong feelings.
            val tilt: Float? = when (e) {
                Expression.ANGRY -> -18f * side
                Expression.SAD -> 16f * side
                Expression.CONFUSED -> if (side > 0) -14f else 0f
                else -> if (look.brows == Brows.AUTO) null else 0f
            }
            if (tilt != null) {
                val lift = if (e == Expression.CONFUSED && side > 0) 0.07f else 0f
                val by = eye.y + 0.26f * s1 + lift + (eyeOpen - 1f).coerceAtLeast(0f) * 0.05f
                val thick = when (look.brows) { Brows.THIN -> 0.018f; Brows.THICK -> 0.045f; Brows.AUTO -> 0.025f }
                val m = Mat4.multiply(feature(eye.x, by, surf(eye.x, by) + 0.03f), Mat4.rotationZ(90f + tilt))
                p.part(p.tube, m, ink, sc = v(thick, 0.2f * s1 * (if (look.brows == Brows.THICK) 1.1f else 1f), thick))
            }
        }

        val cheeks = look.blush || e == Expression.HAPPY || e == Expression.LOVE || e == Expression.TONGUE
        if (cheeks) for (side in intArrayOf(-1, 1)) {
            val cx = 0.55f * side
            feat(p.sphere, argb(look.cheekColor), cx, -0.06f, surf(cx, -0.06f) - 0.03f, 0.13f, 0.07f, 0.05f, shine = 0f, rim = 0f)
        }
        if (e == Expression.SAD) {
            val fall = (a.time * 0.6f) % 1f
            val first = eyes.first()
            val tx = first.x - 0.03f
            val ty = first.y - 0.18f - fall * 0.35f
            feat(p.sphere, rgb(0x5BC0EB), tx, ty, surf(tx, ty) + 0.03f, 0.035f, 0.05f, 0.035f, shine = 1f)
        }

        val noseY = (eyes.map { it.y }.average().toFloat() + -0.2f) / 2f - 0.02f
        drawNose(look, noseY, body, a)
        if (look.whiskers) for (side in intArrayOf(-1, 1)) for (k in -1..1) {
            val wx = 0.36f * side
            val wy = noseY - 0.06f + k * 0.045f
            val m = Mat4.multiply(feature(wx, wy, surf(wx, wy) + 0.015f), Mat4.rotationZ(90f + k * 12f * side))
            p.part(p.tube, m, ink, sc = v(0.008f, 0.32f, 0.008f), rim = 0f)
        }
        if (look.nose != Nose.BEAK) drawMouth(look, e, a, if (look.nose == Nose.SNOUT) -0.27f else -0.2f)
    }

    private fun drawNose(look: CreatureLook, y: Float, body: FloatArray, a: Anim) {
        val c = argb(look.noseColor)
        val z = surf(0f, y)
        when (look.nose) {
            Nose.NONE -> Unit
            Nose.DOT -> feat(p.sphere, c, 0f, y, z - 0.01f, 0.045f, 0.035f, 0.04f, shine = 1f)
            Nose.BUTTON -> feat(p.sphere, c, 0f, y, z - 0.02f, 0.075f, 0.06f, 0.06f, shine = 1.2f)
            Nose.SNOUT -> {
                val snout = if (look.belly) argb(look.bellyColor) else lighten(body, 0.3f)
                val front = surf(0f, y - 0.08f)
                feat(p.sphere, snout, 0f, y - 0.08f, front - 0.04f, 0.24f, 0.15f, 0.13f, shine = 0.3f)
                feat(p.sphere, c, 0f, y - 0.02f, front + 0.07f, 0.075f, 0.052f, 0.05f, shine = 1.2f)
                feat(p.sphere, rgb(0xFFFFFF), 0.025f, y, front + 0.11f, 0.018f, 0.012f, 0.008f, rim = 0f)
            }
            Nose.BEAK -> {
                val up = Mat4.multiply(feature(0f, y - 0.03f, z - 0.04f), Mat4.rotationX(90f - 6f))
                p.part(p.cone, up, c, sc = v(0.1f, 0.2f, 0.06f), shine = 0.7f)
                val open = maxOf(a.mouthOpen, a.tongue * 0.5f)
                val low = Mat4.multiply(feature(0f, y - 0.07f, z - 0.05f), Mat4.rotationX(90f + 8f + open * 28f))
                p.part(p.cone, low, darken(c, 0.85f), sc = v(0.08f, 0.15f, 0.045f), shine = 0.6f)
            }
        }
    }

    private fun drawMouth(look: CreatureLook, e: Expression, a: Anim, y: Float) {
        val ink = rgb(0x2D2A32)
        val z = faceZ(0f, y) - 0.02f
        if (a.mouthOpen > 0.06f || a.tongue > 0.05f) {
            val open = maxOf(a.mouthOpen, a.tongue * 0.45f)
            feat(p.sphere, rgb(0x4A1F2A), 0f, y, z, 0.1f + 0.14f * a.mouthWide, 0.03f + 0.16f * open, 0.05f, shine = 0.2f)
            if (a.tongue > 0.05f) {
                val wiggle = sin(a.time * 12f) * 8f * a.tongue
                val m = Mat4.multiply(feature(0f, y - 0.06f * a.tongue, z + 0.02f + 0.06f * a.tongue), Mat4.rotationZ(wiggle))
                p.part(p.sphere, m, rgb(0xFF6F91), t = v(0f, -0.1f * a.tongue, 0f), sc = v(0.085f, 0.13f * a.tongue + 0.01f, 0.05f), shine = 0.6f)
            }
            return
        }
        val frown = e == Expression.SAD || e == Expression.ANGRY
        if (frown) {
            val m = Mat4.multiply(feature(0f, y - 0.05f, z), Mat4.rotationZ(180f))
            p.part(p.smile, m, ink, sc = v(0.12f, 0.1f, 0.4f))
            return
        }
        val width = when (e) { Expression.HAPPY, Expression.LOVE -> 0.17f; Expression.NEUTRAL, Expression.THINKING -> 0.1f; else -> 0.12f }
        when (look.mouth) {
            MouthStyle.SMILE -> p.part(p.smile, feature(0f, y + 0.02f, z), ink, sc = v(width, width * 0.8f, 0.4f))
            MouthStyle.TINY -> p.part(p.smile, feature(0f, y + 0.02f, z), ink, sc = v(0.06f, 0.05f, 0.4f))
            MouthStyle.CAT -> for (side in intArrayOf(-1, 1)) p.part(p.smile, feature(0.055f * side, y + 0.02f, z), ink, sc = v(0.06f, 0.05f, 0.4f))
            MouthStyle.GRIN -> {
                feat(p.lowerHemisphere, rgb(0x4A1F2A), 0f, y + 0.04f, z, 0.19f, 0.13f, 0.05f, shine = 0.2f)
                feat(p.box, rgb(0xFFFFFF), 0f, y + 0.02f, z + 0.03f, 0.3f, 0.04f, 0.02f, shine = 0.6f)
                feat(p.sphere, rgb(0xFF6F91), 0f, y - 0.06f, z + 0.02f, 0.08f, 0.03f, 0.02f, shine = 0.4f)
            }
            MouthStyle.FANG -> {
                p.part(p.smile, feature(0f, y + 0.02f, z), ink, sc = v(width, width * 0.8f, 0.4f))
                val m = Mat4.multiply(feature(0.07f, y - 0.005f, z + 0.03f), Mat4.rotationZ(180f))
                p.part(p.cone, m, rgb(0xFFFFFF), sc = v(0.034f, 0.085f, 0.025f), shine = 0.8f)
            }
        }
    }
}
