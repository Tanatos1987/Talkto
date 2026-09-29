package com.talkto.app.avatar3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.talkto.app.avatar.AvatarPose
import com.talkto.app.avatar.Clothes
import com.talkto.app.avatar.Glasses
import com.talkto.app.avatar.Hat
import com.talkto.app.avatar.OutfitConfig
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.avatar3d.Ease
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.avatar3d.Mesh
import com.talkto.core.avatar3d.Primitives
import com.talkto.core.avatar3d.Spring
import com.talkto.core.pet.LifeStage
import com.talkto.core.touch.BodyBox
import com.talkto.core.touch.BodyLocator
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchEffect
import com.talkto.core.touch.TouchReaction
import com.talkto.core.touch.TwirlInput
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Everything the renderer needs from the UI thread, swapped atomically each recomposition. */
data class SceneState(
    val pose: AvatarPose = AvatarPose(),
    val outfit: OutfitConfig = OutfitConfig(),
    val stage: LifeStage = LifeStage.ADULT,
    val sleeping: Boolean = false,
    /** Clear colour; alpha 0 lets the mood background behind the view show through. */
    val background: Int = 0x00000000,
)

/**
 * The 3D ZnaiKo: a procedurally modelled creature (no asset files) rendered with OpenGL ES 2.0.
 *
 * Shading is a soft "toy" look: wrapped Lambert diffuse, Blinn-Phong highlight and a rim light, so the
 * vinyl-like body reads well on small screens. The face is built from primitives that animate directly:
 * eyelids blink by scaling the eyes, the mouth ellipsoid follows the lip-sync viseme, the tongue slides out,
 * and springs drive every physical reaction (slap recoil, hit squash, pat bounce, lean into a caress).
 *
 * A sideways drag turns the creature with the finger ([twirl]) and a flick keeps it spinning; when it slows down
 * it turns back to face the user. Every frame the body's screen position goes to [body], so a touch can tell the
 * belly from an eye.
 */
class Creature3DRenderer(
    private val body: BodyLocator? = null,
    private val twirl: TwirlInput? = null,
) : GLSurfaceView.Renderer {

    @Volatile var scene = SceneState()

    private val reactions = ConcurrentLinkedQueue<Pair<TouchReaction, Touch>>()
    @Volatile private var lookTarget: Pair<Float, Float>? = null
    @Volatile private var lookUntilNs = 0L

    fun react(reaction: TouchReaction, touch: Touch) {
        reactions += reaction to touch
        lookAt(touch.nx, touch.ny)
    }

    /** Eyes follow the finger for a moment. nx, ny in 0..1 of the view. */
    fun lookAt(nx: Float, ny: Float) {
        lookTarget = nx to ny
        lookUntilNs = System.nanoTime() + 1_500_000_000L
    }

    // --------------------------------------------------------------------- GL state

    private class GpuMesh(val vbo: Int, val nbo: Int, val ibo: Int, val count: Int)

    private var program = 0
    private var aPos = 0
    private var aNor = 0
    private var uMvp = 0
    private var uModel = 0
    private var uNormal = 0
    private var uColor = 0
    private var uEye = 0
    private var uShine = 0
    private var uRim = 0
    private var uAlpha = 0

    private lateinit var sphere: GpuMesh
    private lateinit var hemisphere: GpuMesh
    private lateinit var lowerHemisphere: GpuMesh
    private lateinit var cylinder: GpuMesh
    private lateinit var tube: GpuMesh
    private lateinit var cone: GpuMesh
    private lateinit var ring: GpuMesh
    private lateinit var smile: GpuMesh

    private var viewProj = Mat4.identity()
    private val eye = floatArrayOf(0f, 0.25f, 5.4f)
    private var aspect = 1f
    private var cameraStage = -1f

    // --------------------------------------------------------------------- animation state

    private var lastNs = 0L
    private var time = 0f
    private val yaw = Spring(stiffness = 90f, damping = 9f)
    private val pitch = Spring(stiffness = 110f, damping = 11f)
    private val roll = Spring(stiffness = 90f, damping = 10f)
    private val squash = Spring(stiffness = 160f, damping = 10f)
    private val hop = Spring(stiffness = 140f, damping = 9f)
    private var lookX = 0f
    private var lookY = 0f
    private var blink = 0f
    private var nextBlinkAt = 2f
    private var blinkPhase = -1f
    private var lastGestureId = -1L
    private var gestureStart = -10f
    private var gesture = Gesture.NONE
    private var mouthOpen = 0f
    private var mouthWide = 0.5f
    private var tongue = 0f
    private var wander = 0f

    // Finger turning and touch effects.
    private var spin = 0f
    private var spinVel = 0f
    private var spinHeld = false
    private var winceUntil = -1f
    private var giggleUntil = -1f
    private var dizzyUntil = -1f
    private var lookHoldUntil = -1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = link(VERTEX, FRAGMENT)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNor = GLES20.glGetAttribLocation(program, "aNor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uNormal = GLES20.glGetUniformLocation(program, "uNormal")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uEye = GLES20.glGetUniformLocation(program, "uEye")
        uShine = GLES20.glGetUniformLocation(program, "uShine")
        uRim = GLES20.glGetUniformLocation(program, "uRim")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")

        sphere = upload(Primitives.sphere(28, 36))
        hemisphere = upload(Primitives.sphere(14, 36, 0.0, PI / 2))
        lowerHemisphere = upload(Primitives.sphere(14, 36, PI / 2, PI))
        cylinder = upload(Primitives.cylinder(36, capped = true))
        tube = upload(Primitives.cylinder(24, capped = false))
        cone = upload(Primitives.cone(36))
        ring = upload(Primitives.torus(0.14f, 40, 12))
        smile = upload(Primitives.torus(0.22f, 24, 10, sweep = PI, start = PI))

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // No face culling: open shapes (tubes, cones, arcs) are seen from both sides, and the scene is small.
        lastNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
        cameraStage = -1f
    }

    /**
     * Frames the creature by its size: a baby is seen from closer up, so it never looks lost on the stage,
     * yet an adult still reads as bigger. Hats stay in frame in portrait and landscape.
     */
    private fun updateCamera(stageScale: Float) {
        if (stageScale == cameraStage) return
        cameraStage = stageScale
        val fov = if (aspect < 1f) 28f / aspect.coerceAtLeast(0.6f) else 28f
        val distance = 4.2f + 1.4f * stageScale
        val centerY = -1f + stageScale * 1.15f
        eye[0] = 0f; eye[1] = centerY + 0.35f; eye[2] = distance
        val proj = Mat4.perspective(fov.coerceAtMost(60f), aspect, 0.5f, 20f)
        val view = Mat4.lookAt(eye[0], eye[1], eye[2], 0f, centerY, 0f)
        viewProj = Mat4.multiply(proj, view)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        time += dt
        val s = scene
        animate(s, dt, now)

        val bg = s.background
        GLES20.glClearColor(((bg shr 16) and 0xFF) / 255f, ((bg shr 8) and 0xFF) / 255f, (bg and 0xFF) / 255f, ((bg ushr 24) and 0xFF) / 255f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uEye, 1, eye, 0)

        drawShadow()
        drawCreature(s)
    }

    // --------------------------------------------------------------------- animation

    private fun animate(s: SceneState, dt: Float, now: Long) {
        while (true) {
            val (r, touch) = reactions.poll() ?: break
            yaw.kick(r.recoilYaw * 9f)
            squash.kick(r.squash * 14f)
            if (r.gesture == Gesture.BOUNCE) hop.kick(5f)
            when (r.effect) {
                TouchEffect.WINCE -> winceUntil = time + 0.9f
                TouchEffect.GIGGLE -> giggleUntil = time + 1.4f
                TouchEffect.HOP -> hop.kick(11f)
                // Pushed away from the finger: leans and tips back.
                TouchEffect.PUSH -> { roll.kick((touch.nx - 0.5f) * 400f); pitch.kick((touch.ny - 0.5f) * -250f) }
                TouchEffect.DIZZY -> dizzyUntil = time + 3f
                // Turns towards what the finger pointed at.
                TouchEffect.LOOK -> { lookHoldUntil = time + 2.5f; yaw.kick((touch.nx - 0.5f) * 500f) }
                // Leans into a caress, towards where the finger was.
                TouchEffect.LEAN -> roll.kick((touch.nx - 0.5f) * -300f)
                TouchEffect.NUZZLE -> { pitch.kick(-200f); squash.kick(2f) }
                TouchEffect.NONE -> Unit
            }
        }
        twirl?.take()?.let { step ->
            spin += step.dragDeg
            spinHeld = step.dragging
            if (step.dragging) spinVel = 0f
            step.flingDegPerS?.let { spinVel = it }
        }
        if (!spinHeld) {
            if (abs(spinVel) > 30f) {
                spin += spinVel * dt
                spinVel *= exp(-1.6f * dt)
            } else {
                // Slowed down: turn back to face the user the short way round.
                spinVel = 0f
                val home = (spin / 360f).roundToInt() * 360f
                spin = Ease.approach(spin, home, 3f, dt)
                if (abs(spin - home) < 0.3f) spin -= home
            }
        }
        if (s.pose.gestureId != lastGestureId) {
            lastGestureId = s.pose.gestureId
            gesture = s.pose.gesture
            gestureStart = time
        }
        val g = ((time - gestureStart) / 0.95f).coerceIn(0f, 1f)
        val wave = sin(g * PI.toFloat() * 4f) * (1f - g)
        yaw.target = when (gesture) {
            Gesture.SHAKE -> wave * 25f
            Gesture.SPIN -> if (g < 1f) Ease.smooth(g) * 360f else 0f
            else -> 0f
        }
        if (gesture == Gesture.SPIN && g >= 1f) {
            gesture = Gesture.NONE
            yaw.value = yaw.value % 360f
        }
        pitch.target = if (gesture == Gesture.NOD) wave * 18f else if (s.pose.expression == Expression.THINKING) -8f else 0f
        roll.target = if (gesture == Gesture.WAVE) wave * 10f else if (s.pose.expression == Expression.CONFUSED) 12f else 0f
        // Alive when idle: a slow sway and a look around, so it reads as a round 3D toy, not a flat sticker.
        if (!s.sleeping && (gesture == Gesture.NONE || g >= 1f)) {
            yaw.target += sin(time * 0.31f) * 16f + sin(time * 0.83f) * 5f
            roll.target += sin(time * 0.57f) * 3f
        }
        if (time < giggleUntil) {
            roll.target += sin(time * 26f) * 7f
            squash.target = (sin(time * 18f) * 0.08f).coerceAtLeast(0f)
        } else {
            squash.target = 0f
        }
        if (time < dizzyUntil) {
            val k = ((dizzyUntil - time) / 3f).coerceIn(0f, 1f)
            roll.target += sin(time * 5f) * 14f * k
            pitch.target += cos(time * 5f) * 8f * k
        }
        if (gesture == Gesture.BOUNCE && g < 0.05f) hop.kick(6f)
        yaw.step(dt); pitch.step(dt); roll.step(dt); squash.step(dt); hop.step(dt)

        // Eyes: follow the finger, otherwise wander a little.
        wander += dt
        val target = lookTarget?.takeIf { now < lookUntilNs || time < lookHoldUntil }
        var tx = target?.let { (it.first - 0.5f) * 2f } ?: (sin(wander * 0.7f) * 0.35f)
        var ty = target?.let { (0.5f - it.second) * 2f } ?: (sin(wander * 0.43f) * 0.2f)
        if (time < dizzyUntil) {
            // Eyes roll round and round.
            tx = cos(time * 9f); ty = sin(time * 9f)
        }
        lookX = Ease.approach(lookX, tx.coerceIn(-1f, 1f), 10f, dt)
        lookY = Ease.approach(lookY, ty.coerceIn(-1f, 1f), 10f, dt)

        // Blink: random 2..6 s, sometimes twice.
        if (blinkPhase < 0f && time >= nextBlinkAt) blinkPhase = 0f
        if (blinkPhase >= 0f) {
            blinkPhase += dt / 0.18f
            blink = if (blinkPhase < 0.5f) blinkPhase * 2f else (1f - (blinkPhase - 0.5f) * 2f)
            if (blinkPhase >= 1f) {
                blinkPhase = -1f; blink = 0f
                nextBlinkAt = time + if (Random.nextFloat() < 0.18f) 0.25f else Random.nextFloat() * 4f + 2f
            }
        }

        val speaking = s.pose.speaking
        mouthOpen = Ease.approach(mouthOpen, if (speaking) s.pose.viseme.openness else if (s.pose.expression == Expression.SURPRISED) 0.7f else 0f, 25f, dt)
        mouthWide = Ease.approach(mouthWide, if (speaking) s.pose.viseme.width * (1f - 0.4f * s.pose.viseme.round) else 0.5f, 25f, dt)
        tongue = Ease.approach(tongue, if (s.pose.expression == Expression.TONGUE) 1f else 0f, 9f, dt)
    }

    // --------------------------------------------------------------------- drawing

    private fun drawCreature(s: SceneState) {
        val stageScale = when (s.stage) {
            LifeStage.EGG -> 0.62f
            LifeStage.BABY -> 0.72f
            LifeStage.CHILD -> 0.84f
            LifeStage.TEEN -> 0.93f
            LifeStage.ADULT -> 1f
        }
        updateCamera(stageScale)
        val breath = sin(time * if (s.sleeping) 1.4f else 2.1f) * 0.018f
        val sq = squash.value.coerceIn(-0.4f, 0.6f)
        // Root: stands on the floor (y = -1), scales from the feet, turns and tilts as one body.
        var root = Mat4.translation(0f, -1f + hop.value.coerceAtLeast(0f) * 0.08f, 0f)
        root = Mat4.multiply(root, Mat4.scale(stageScale * (1f + sq * 0.35f), stageScale * (1f - sq * 0.5f + breath), stageScale * (1f + sq * 0.25f)))
        root = Mat4.multiply(root, Mat4.translation(0f, 1f, 0f))
        publishBody(root)
        root = Mat4.multiply(root, Mat4.rotationY(yaw.value + spin))
        root = Mat4.multiply(root, Mat4.rotationX(pitch.value))
        root = Mat4.multiply(root, Mat4.rotationZ(roll.value))

        val e = s.pose.expression
        val body = if (s.sleeping) rgb(0x6FB57D) else rgb(0x7BD389)
        val belly = rgb(0xB8EBC0)
        val ink = rgb(0x2D2A32)

        part(sphere, root, body, sc = floatArrayOf(1f, 0.93f, 0.9f), shine = 0.35f, rim = 0.6f)
        part(sphere, root, belly, t = floatArrayOf(0f, -0.42f, 0.62f), sc = floatArrayOf(0.56f, 0.42f, 0.3f), shine = 0.2f)
        // feet and arms
        part(sphere, root, body, t = floatArrayOf(-0.42f, -0.9f, 0.22f), sc = floatArrayOf(0.3f, 0.14f, 0.36f))
        part(sphere, root, body, t = floatArrayOf(0.42f, -0.9f, 0.22f), sc = floatArrayOf(0.3f, 0.14f, 0.36f))
        val wave = if (gesture == Gesture.WAVE) sin(time * 14f) * 25f + 110f else 15f
        part(sphere, Mat4.multiply(root, Mat4.multiply(Mat4.translation(0.88f, -0.15f, 0.05f), Mat4.rotationZ(wave))), body,
            t = floatArrayOf(0f, 0.12f, 0f), sc = floatArrayOf(0.15f, 0.3f, 0.15f))
        part(sphere, Mat4.multiply(root, Mat4.multiply(Mat4.translation(-0.88f, -0.15f, 0.05f), Mat4.rotationZ(-15f))), body,
            t = floatArrayOf(0f, 0.12f, 0f), sc = floatArrayOf(0.15f, 0.3f, 0.15f))

        drawFace(root, e, s, ink)
        drawOutfit(root, s.outfit, ink)

        if (s.stage == LifeStage.EGG) {
            part(lowerHemisphere, root, rgb(0xFFF4DC), t = floatArrayOf(0f, -0.3f, 0f), sc = floatArrayOf(1.08f, 0.78f, 1.04f), shine = 0.5f, rim = 0.3f)
        }
    }

    /** Projects the body centre and its radii to the view (0..1), for touch zones. Rotation does not move them. */
    private fun publishBody(base: FloatArray) {
        val locator = body ?: return
        val mvp = Mat4.multiply(viewProj, base)
        fun screen(x: Float, y: Float, z: Float): FloatArray {
            val c = Mat4.transform(mvp, x, y, z)
            val w = if (abs(c[3]) < 1e-5f) 1f else c[3]
            return floatArrayOf((c[0] / w + 1f) / 2f, (1f - c[1] / w) / 2f)
        }
        val c = screen(0f, 0f, 0f)
        val right = screen(1f, 0f, 0f)
        val top = screen(0f, 0.93f, 0f)
        locator.box = BodyBox(c[0], c[1], abs(right[0] - c[0]), abs(c[1] - top[1]))
    }

    private fun drawFace(root: FloatArray, e: Expression, s: SceneState, ink: FloatArray) {
        val white = rgb(0xFFFFFF)
        val eyeOpen = when {
            s.sleeping -> 0.08f
            e == Expression.SLEEPY -> 0.35f
            e == Expression.HAPPY || e == Expression.TONGUE -> 0.6f
            e == Expression.SURPRISED -> 1.2f
            else -> 1f
        } * (1f - blink * 0.92f) * (if (time < winceUntil) 0.06f else 1f)
        val pupilColor = if (e == Expression.LOVE) rgb(0xF15BB5) else ink
        val pupilSize = if (e == Expression.LOVE) 0.13f else if (e == Expression.SURPRISED) 0.08f else 0.1f
        for (side in listOf(-1f, 1f)) {
            val ex = 0.33f * side
            part(sphere, root, white, t = floatArrayOf(ex, 0.2f, 0.74f), sc = floatArrayOf(0.2f, 0.22f * eyeOpen.coerceAtLeast(0.05f), 0.12f), shine = 0.8f)
            if (eyeOpen > 0.2f) {
                val px = ex + lookX * 0.06f
                val py = 0.2f + lookY * 0.05f
                part(sphere, root, pupilColor, t = floatArrayOf(px, py, 0.85f), sc = floatArrayOf(pupilSize, pupilSize * eyeOpen.coerceAtMost(1f), 0.05f), shine = 1f)
                part(sphere, root, white, t = floatArrayOf(px + 0.035f, py + 0.04f, 0.9f), sc = floatArrayOf(0.028f, 0.028f, 0.01f), rim = 0f)
            }
            // Brows for strong emotions.
            val browTilt = when (e) {
                Expression.ANGRY -> -18f * side
                Expression.SAD -> 16f * side
                Expression.CONFUSED -> if (side > 0) -14f else 0f
                else -> null
            }
            if (browTilt != null) {
                val lift = if (e == Expression.CONFUSED && side > 0) 0.07f else 0f
                val m = Mat4.multiply(root, Mat4.multiply(Mat4.translation(ex, 0.46f + lift, 0.76f), Mat4.rotationZ(90f + browTilt)))
                part(tube, m, ink, sc = floatArrayOf(0.025f, 0.2f, 0.025f))
            }
            if (e == Expression.HAPPY || e == Expression.LOVE || e == Expression.TONGUE) {
                part(sphere, root, rgb(0xFF8FA3), t = floatArrayOf(0.55f * side, -0.06f, 0.72f), sc = floatArrayOf(0.13f, 0.07f, 0.05f), shine = 0f, rim = 0f)
            }
        }
        if (e == Expression.SAD) {
            val fall = (time * 0.6f) % 1f
            part(sphere, root, rgb(0x5BC0EB), t = floatArrayOf(-0.36f, 0.02f - fall * 0.35f, 0.88f), sc = floatArrayOf(0.035f, 0.05f, 0.035f), shine = 1f)
        }

        // Mouth: an open ellipsoid while talking/surprised/tongue, otherwise a smile or frown arc.
        val mouthY = -0.2f
        if (mouthOpen > 0.06f || tongue > 0.05f) {
            val open = maxOf(mouthOpen, tongue * 0.45f)
            part(sphere, root, rgb(0x4A1F2A), t = floatArrayOf(0f, mouthY, 0.86f), sc = floatArrayOf(0.1f + 0.14f * mouthWide, 0.03f + 0.16f * open, 0.05f), shine = 0.2f)
            if (tongue > 0.05f) {
                val wiggle = sin(time * 12f) * 8f * tongue
                val m = Mat4.multiply(root, Mat4.multiply(Mat4.translation(0f, mouthY - 0.06f * tongue, 0.88f + 0.06f * tongue), Mat4.rotationZ(wiggle)))
                part(sphere, m, rgb(0xFF6F91), t = floatArrayOf(0f, -0.1f * tongue, 0f), sc = floatArrayOf(0.085f, 0.13f * tongue + 0.01f, 0.05f), shine = 0.6f)
            }
        } else {
            val frown = e == Expression.SAD || e == Expression.ANGRY
            val width = when (e) { Expression.HAPPY, Expression.LOVE -> 0.17f; Expression.NEUTRAL, Expression.THINKING -> 0.1f; else -> 0.12f }
            var m = Mat4.multiply(root, Mat4.translation(0f, mouthY + if (frown) -0.05f else 0.02f, 0.86f))
            if (frown) m = Mat4.multiply(m, Mat4.rotationZ(180f))
            part(smile, m, ink, sc = floatArrayOf(width, width * 0.8f, 0.4f))
        }
    }

    private fun drawOutfit(root: FloatArray, o: OutfitConfig, ink: FloatArray) {
        val hat = argb(o.hatColor)
        val cloth = argb(o.clothesColor)
        val gold = rgb(0xFFC857)
        when (o.hat) {
            // No hat: a little sprout, ZnaiKo's trademark, sways as it moves.
            Hat.NONE -> {
                val sway = sin(time * 1.7f) * 8f + roll.value * 0.6f
                val stem = Mat4.multiply(root, Mat4.multiply(Mat4.translation(0f, 0.9f, 0f), Mat4.rotationZ(sway)))
                val leaf = rgb(0x4FA85E)
                part(tube, stem, leaf, t = floatArrayOf(0f, 0.12f, 0f), sc = floatArrayOf(0.035f, 0.26f, 0.035f), shine = 0.2f)
                for (side in listOf(-1f, 1f)) {
                    val m = Mat4.multiply(stem, Mat4.multiply(Mat4.translation(0.12f * side, 0.27f, 0f), Mat4.rotationZ(-50f * side)))
                    part(sphere, m, leaf, sc = floatArrayOf(0.16f, 0.07f, 0.1f), shine = 0.5f, rim = 0.5f)
                }
            }
            Hat.PARTY -> {
                val m = Mat4.multiply(root, Mat4.multiply(Mat4.translation(0.12f, 0.8f, 0f), Mat4.rotationZ(-12f)))
                part(cone, m, hat, sc = floatArrayOf(0.34f, 0.8f, 0.34f), shine = 0.4f)
                part(sphere, m, gold, t = floatArrayOf(0f, 0.82f, 0f), sc = floatArrayOf(0.09f, 0.09f, 0.09f))
            }
            Hat.BEANIE -> {
                // Sits above the eyes (their top edge is at y = 0.42).
                part(hemisphere, root, hat, t = floatArrayOf(0f, 0.47f, 0f), sc = floatArrayOf(0.86f, 0.6f, 0.84f), shine = 0.1f)
                part(tube, root, darken(hat), t = floatArrayOf(0f, 0.5f, 0f), sc = floatArrayOf(0.87f, 0.11f, 0.85f), shine = 0.1f)
                part(sphere, root, rgb(0xFFFFFF), t = floatArrayOf(0f, 1.1f, 0f), sc = floatArrayOf(0.12f, 0.12f, 0.12f), shine = 0f)
            }
            Hat.CROWN -> {
                part(tube, root, gold, t = floatArrayOf(0f, 0.98f, 0f), sc = floatArrayOf(0.42f, 0.26f, 0.42f), shine = 1f)
                for (k in 0 until 5) {
                    val a = 2 * PI * k / 5
                    part(sphere, root, hat, t = floatArrayOf((0.42 * kotlin.math.cos(a)).toFloat(), 1.13f, (0.42 * sin(a)).toFloat()),
                        sc = floatArrayOf(0.06f, 0.06f, 0.06f), shine = 1f)
                }
            }
            Hat.TOP_HAT -> {
                part(cylinder, root, ink, t = floatArrayOf(0f, 0.84f, 0f), sc = floatArrayOf(0.75f, 0.05f, 0.75f))
                part(cylinder, root, ink, t = floatArrayOf(0f, 1.15f, 0f), sc = floatArrayOf(0.44f, 0.62f, 0.44f))
                part(tube, root, hat, t = floatArrayOf(0f, 0.93f, 0f), sc = floatArrayOf(0.455f, 0.1f, 0.455f))
            }
            Hat.CAP -> {
                part(hemisphere, root, hat, t = floatArrayOf(0f, 0.5f, 0f), sc = floatArrayOf(0.84f, 0.5f, 0.82f), shine = 0.25f)
                // The brim sticks out forward above the eyes instead of covering them.
                part(sphere, root, darken(hat), t = floatArrayOf(0f, 0.55f, 0.72f), sc = floatArrayOf(0.46f, 0.035f, 0.36f))
            }
        }
        when (o.glasses) {
            Glasses.NONE -> Unit
            Glasses.ROUND, Glasses.HEART -> {
                val c = if (o.glasses == Glasses.HEART) rgb(0xF15BB5) else ink
                for (side in listOf(-1f, 1f)) part(ring, root, c, t = floatArrayOf(0.33f * side, 0.2f, 0.9f), sc = floatArrayOf(0.2f, 0.2f, 0.3f), shine = 1f)
                bridge(root, c)
            }
            Glasses.SUNGLASSES -> {
                for (side in listOf(-1f, 1f)) part(sphere, root, rgb(0x151515), t = floatArrayOf(0.33f * side, 0.2f, 0.88f), sc = floatArrayOf(0.23f, 0.16f, 0.06f), shine = 1.2f)
                bridge(root, rgb(0x151515))
            }
            Glasses.MONOCLE -> part(ring, root, gold, t = floatArrayOf(0.33f, 0.2f, 0.9f), sc = floatArrayOf(0.22f, 0.22f, 0.3f), shine = 1f)
        }
        when (o.clothes) {
            Clothes.NONE -> Unit
            Clothes.SCARF -> part(ring, Mat4.multiply(root, Mat4.multiply(Mat4.translation(0f, -0.5f, 0f), Mat4.rotationX(90f))), cloth,
                sc = floatArrayOf(0.9f, 0.98f, 1.4f), shine = 0.1f)
            Clothes.BOWTIE -> {
                for (side in listOf(-1f, 1f)) part(sphere, root, cloth, t = floatArrayOf(0.12f * side, -0.52f, 0.92f), sc = floatArrayOf(0.13f, 0.08f, 0.05f))
                part(sphere, root, darken(cloth), t = floatArrayOf(0f, -0.52f, 0.95f), sc = floatArrayOf(0.05f, 0.05f, 0.04f))
            }
            Clothes.TIE -> {
                part(sphere, root, darken(cloth), t = floatArrayOf(0f, -0.5f, 0.93f), sc = floatArrayOf(0.06f, 0.05f, 0.04f))
                part(sphere, root, cloth, t = floatArrayOf(0f, -0.72f, 0.86f), sc = floatArrayOf(0.07f, 0.2f, 0.04f))
            }
            Clothes.HOODIE -> part(lowerHemisphere, root, cloth, t = floatArrayOf(0f, -0.32f, 0f), sc = floatArrayOf(1.03f, 0.66f, 0.95f), shine = 0.05f, rim = 0.4f)
        }
    }

    private fun bridge(root: FloatArray, c: FloatArray) {
        val m = Mat4.multiply(root, Mat4.multiply(Mat4.translation(0f, 0.22f, 0.93f), Mat4.rotationZ(90f)))
        part(tube, m, c, sc = floatArrayOf(0.018f, 0.26f, 0.018f))
    }

    private fun drawShadow() {
        GLES20.glEnable(GLES20.GL_BLEND)
        // Separate alpha blend keeps the shadow visible when the view itself is transparent (TextureView).
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        val shrink = 1f - hop.value.coerceAtLeast(0f) * 0.05f
        part(sphere, Mat4.identity(), rgb(0x000000), t = floatArrayOf(0f, -1.02f, 0.1f), sc = floatArrayOf(0.95f * shrink, 0.015f, 0.62f * shrink), shine = 0f, rim = 0f, alpha = 0.16f)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** Draws [mesh] with model = parent · T(t) · S(sc). */
    private fun part(
        mesh: GpuMesh, parent: FloatArray, color: FloatArray,
        t: FloatArray = ZERO3, sc: FloatArray = ONE3, shine: Float = 0.5f, rim: Float = 0.35f, alpha: Float = 1f,
    ) {
        val model = Mat4.multiply(parent, Mat4.multiply(Mat4.translation(t[0], t[1], t[2]), Mat4.scale(sc[0], sc[1], sc[2])))
        val mvp = Mat4.multiply(viewProj, model)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniformMatrix3fv(uNormal, 1, false, Mat4.normalMatrix(model), 0)
        GLES20.glUniform3fv(uColor, 1, color, 0)
        GLES20.glUniform1f(uShine, shine)
        GLES20.glUniform1f(uRim, rim)
        GLES20.glUniform1f(uAlpha, alpha)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.nbo)
        GLES20.glEnableVertexAttribArray(aNor)
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.count, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    // --------------------------------------------------------------------- GL helpers

    private fun upload(m: Mesh): GpuMesh {
        val ids = IntArray(3)
        GLES20.glGenBuffers(3, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, m.positions.size * 4, floatBuffer(m.positions), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, m.normals.size * 4, floatBuffer(m.normals), GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ids[2])
        val ib = ByteBuffer.allocateDirect(m.indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(m.indices).also { it.position(0) }
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 2, ib, GLES20.GL_STATIC_DRAW)
        return GpuMesh(ids[0], ids[1], ids[2], m.indexCount)
    }

    private fun floatBuffer(a: FloatArray) =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).also { it.position(0) }

    private fun link(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Program link failed: ${GLES20.glGetProgramInfoLog(p)}" }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(s)}" }
        return s
    }

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
    private fun argb(c: Long) = rgb((c and 0xFFFFFF).toInt())
    private fun darken(c: FloatArray, f: Float = 0.72f) = floatArrayOf(c[0] * f, c[1] * f, c[2] * f)

    private companion object {
        val ZERO3 = floatArrayOf(0f, 0f, 0f)
        val ONE3 = floatArrayOf(1f, 1f, 1f)

        const val VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat3 uNormal;
            attribute vec3 aPos;
            attribute vec3 aNor;
            varying vec3 vN;
            varying vec3 vW;
            void main() {
                vW = (uModel * vec4(aPos, 1.0)).xyz;
                vN = normalize(uNormal * aNor);
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT = """
            precision mediump float;
            uniform vec3 uColor;
            uniform vec3 uEye;
            uniform float uShine;
            uniform float uRim;
            uniform float uAlpha;
            varying vec3 vN;
            varying vec3 vW;
            void main() {
                vec3 n = normalize(vN);
                vec3 v = normalize(uEye - vW);
                // Warm key light from the top left, cool fill from the right, sky/ground ambient.
                vec3 l = normalize(vec3(-0.45, 0.8, 0.6));
                vec3 l2 = normalize(vec3(0.75, 0.15, 0.5));
                vec3 h = normalize(l + v);
                float key = max(dot(n, l) * 0.6 + 0.4, 0.0);
                key *= key;
                float fill = max(dot(n, l2), 0.0) * 0.28;
                vec3 ambient = mix(vec3(0.20, 0.17, 0.23), vec3(0.40, 0.45, 0.56), n.y * 0.5 + 0.5);
                float nh = max(dot(n, h), 0.0);
                float spec = (pow(nh, 56.0) * 0.9 + pow(nh, 10.0) * 0.07) * uShine;
                float fres = pow(1.0 - max(dot(n, v), 0.0), 3.0) * uRim;
                // Contact darkening near the floor (y = -1) grounds the body.
                float ao = mix(0.7, 1.0, smoothstep(-1.15, -0.35, vW.y));
                vec3 c = uColor * (ambient + vec3(1.0, 0.96, 0.9) * key * 0.85 + vec3(0.8, 0.9, 1.0) * fill) * ao;
                c += vec3(spec) + vec3(0.85, 0.93, 1.0) * fres * 0.45;
                c = c / (1.0 + c * 0.15) * 1.1;
                gl_FragColor = vec4(c, uAlpha);
            }
        """
    }
}
