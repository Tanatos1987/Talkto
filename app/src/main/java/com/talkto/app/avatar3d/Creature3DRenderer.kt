package com.talkto.app.avatar3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.talkto.app.avatar.AvatarPose
import com.talkto.app.avatar3d.Painter.Companion.rgb
import com.talkto.app.avatar3d.Painter.Companion.v
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.avatar3d.Ease
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.avatar3d.Spring
import com.talkto.core.look.CreatureLook
import com.talkto.core.look.HouseLook
import com.talkto.core.look.OutfitConfig
import com.talkto.core.pet.LifeStage
import com.talkto.core.touch.BodyBox
import com.talkto.core.touch.BodyLocator
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchEffect
import com.talkto.core.touch.TouchReaction
import com.talkto.core.touch.TwirlInput
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** Everything the renderer needs from the UI thread, swapped atomically each recomposition. */
data class SceneState(
    val pose: AvatarPose = AvatarPose(),
    val outfit: OutfitConfig = OutfitConfig(),
    val stage: LifeStage = LifeStage.ADULT,
    val sleeping: Boolean = false,
    /** Clear colour; alpha 0 lets the mood background behind the view show through. */
    val background: Int = 0x00000000,
    /** 0..1 through the life stage: the body grows smoothly towards the next stage's size. */
    val growth: Float = 0f,
    /** Installed knowledge updates: the sprout grows, flowers and gets a star. */
    val updates: Int = 0,
    /** How this ZnaiKo looks, from the creator. */
    val look: CreatureLook = CreatureLook(),
    val house: HouseLook = HouseLook(),
    /** ZnaiKo is (or is going) inside its house. */
    val atHome: Boolean = false,
    /** The creator's preview: just the creature, no house. */
    val preview: Boolean = false,
)

/**
 * The 3D ZnaiKo: a procedurally modelled creature (no asset files) rendered with OpenGL ES 2.0.
 *
 * Shading is a soft "toy" look: wrapped Lambert diffuse, Blinn-Phong highlight and a rim light, so the
 * vinyl-like body reads well on small screens. The face is built from primitives that animate directly:
 * eyelids blink by scaling the eyes, the mouth follows the lip-sync viseme, the tongue slides out,
 * and springs drive every physical reaction (slap recoil, hit squash, pat bounce, lean into a caress).
 * The body, face and clothes come from [CreatureModel] and [OutfitModel]; the house from [HouseModel].
 *
 * Going home is a little film: ZnaiKo turns, walks to its door while the camera follows, the door opens,
 * it goes in, the door closes and the windows light up. Coming out plays it backwards.
 *
 * A sideways drag turns the creature with the finger ([twirl]) and a flick keeps it spinning; when it slows down
 * it turns back to face the user. Every frame the body's screen position goes to [body], so a touch can tell the
 * belly from an eye.
 */
class Creature3DRenderer(
    private val body: BodyLocator? = null,
    private val twirl: TwirlInput? = null,
    private val nanoTime: () -> Long = System::nanoTime,
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
        lookUntilNs = nanoTime() + 1_500_000_000L
    }

    private val p = Painter()
    private val creature = CreatureModel(p)
    private val house = HouseModel(p)
    private val anim = Anim()
    private var aspect = 1f

    // --------------------------------------------------------------------- animation state

    private var lastNs = 0L
    private val yaw = Spring(stiffness = 90f, damping = 9f)
    private val pitch = Spring(stiffness = 110f, damping = 11f)
    private val roll = Spring(stiffness = 90f, damping = 10f)
    private val squash = Spring(stiffness = 160f, damping = 10f)
    private val hop = Spring(stiffness = 140f, damping = 9f)
    private var nextBlinkAt = 2f
    private var blinkPhase = -1f
    private var lastGestureId = -1L
    private var gestureStart = -10f
    private var gesture = Gesture.NONE
    private var wander = 0f

    // Finger turning and touch effects.
    private var spin = 0f
    private var spinVel = 0f
    private var spinHeld = false
    private var winceUntil = -1f
    private var giggleUntil = -1f
    private var dizzyUntil = -1f
    private var lookHoldUntil = -1f

    // Going home.
    private enum class Home { OUT, GOING, IN, LEAVING }
    private var home = Home.OUT
    private var homeT = 0f
    private var started = false
    private var posX = 0f
    private var posZ = 0f
    private var walkYaw = 0f
    private var doorScale = 1f
    /** 0: the camera frames ZnaiKo; 1: it frames the house. */
    private var camHome = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        p.create()
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // No face culling: open shapes (tubes, cones, arcs) are seen from both sides, and the scene is small.
        lastNs = nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
    }

    /**
     * Frames the creature by its size: a baby is seen from closer up, so it never looks lost on the stage, yet an
     * adult still reads as bigger. Towards the house the camera pulls back until the whole yard fits.
     */
    private fun updateCamera(stageScale: Float) {
        val fov = (if (aspect < 1f) 28f / aspect.coerceAtLeast(0.6f) else 28f).coerceAtMost(60f)
        val tanHalf = tan(fov / 2f * PI.toFloat() / 180f)
        val normalDist = 4.2f + 1.4f * stageScale
        val normalY = -1f + stageScale * 1.15f
        // The yard: 3.1 either side of its centre, 2.4 above and below.
        val homeDist = maxOf(3.1f / (tanHalf * aspect), 2.4f / tanHalf) + 1.2f
        val k = Ease.smooth(camHome)
        val cx = HOME_CX * k
        val cy = normalY + (HOME_CY - normalY) * k
        val dist = normalDist + (homeDist - normalDist) * k
        p.eye[0] = cx; p.eye[1] = cy + 0.35f + 0.6f * k; p.eye[2] = dist
        val proj = Mat4.perspective(fov, aspect, 0.5f, 40f)
        val view = Mat4.lookAt(p.eye[0], p.eye[1], p.eye[2], cx, cy, 0f)
        p.viewProj = Mat4.multiply(proj, view)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        anim.time += dt
        val s = scene
        updateHome(s, dt)
        animate(s, dt, now)

        val bg = s.background
        GLES20.glClearColor(((bg shr 16) and 0xFF) / 255f, ((bg shr 8) and 0xFF) / 255f, (bg and 0xFF) / 255f, ((bg ushr 24) and 0xFF) / 255f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val stageScale = stageScale(s)
        updateCamera(stageScale)
        p.begin()
        val showHouse = !s.preview && (camHome > 0.001f || aspect > 1.3f)
        val inside = home == Home.IN

        p.blended {
            if (!inside) {
                val shrink = (1f - hop.value.coerceAtLeast(0f) * 0.05f) * stageScale * doorScale
                val w = s.look.clamped().width
                p.part(p.sphere, Mat4.identity(), rgb(0x000000), t = v(posX, -1.02f, posZ + 0.1f), sc = v(0.95f * shrink * w, 0.015f, 0.62f * shrink), shine = 0f, rim = 0f, alpha = 0.16f)
            }
            if (showHouse) {
                p.part(p.sphere, Mat4.identity(), rgb(0x000000), t = v(HouseModel.X, -1.02f, HouseModel.Z), sc = v(2.3f, 0.015f, 1.9f), shine = 0f, rim = 0f, alpha = 0.14f)
            }
        }
        if (showHouse) house.draw(s.house, anim, sleepingInside = inside && s.sleeping)

        val root = if (inside) null else creatureRoot(s, stageScale)
        if (root != null) creature.draw(root, s, anim) else hideBody()

        p.blended {
            if (root != null) creature.drawAura(root, s.look.clamped(), anim)
            if (showHouse) house.drawBlended(s.house, anim)
        }
    }

    private fun stageScale(s: SceneState): Float {
        val next = LifeStage.entries.getOrNull(s.stage.ordinal + 1)
        val base = scaleOf(s.stage)
        // Grows most of the way within a stage; the rest comes as a visible jump when the stage changes.
        return if (next == null) base else base + (scaleOf(next) - base) * s.growth.coerceIn(0f, 1f) * 0.7f
    }

    /** Root: stands on the floor (y = -1), scales from the feet, walks, turns and tilts as one body. */
    private fun creatureRoot(s: SceneState, stageScale: Float): FloatArray {
        val breath = sin(anim.time * if (s.sleeping) 1.4f else 2.1f) * 0.018f
        val sq = squash.value.coerceIn(-0.4f, 0.6f)
        val scale = stageScale * doorScale
        val bob = abs(sin(anim.walkPhase)) * 0.07f * anim.walk
        var root = Mat4.translation(posX, -1f + hop.value.coerceAtLeast(0f) * 0.08f + bob, posZ)
        root = Mat4.multiply(root, Mat4.scale(scale * (1f + sq * 0.35f), scale * (1f - sq * 0.5f + breath), scale * (1f + sq * 0.25f)))
        root = Mat4.multiply(root, Mat4.translation(0f, 1f, 0f))
        publishBody(root, BodyPlan.of(s.look))
        root = Mat4.multiply(root, Mat4.rotationY(yaw.value + spin + walkYaw))
        root = Mat4.multiply(root, Mat4.rotationX(pitch.value))
        root = Mat4.multiply(root, Mat4.rotationZ(roll.value + sin(anim.walkPhase) * 4f * anim.walk))
        return root
    }

    private fun scaleOf(stage: LifeStage) = when (stage) {
        LifeStage.EGG -> 0.62f
        LifeStage.BABY -> 0.72f
        LifeStage.CHILD -> 0.84f
        LifeStage.TEEN -> 0.93f
        LifeStage.ADULT -> 1f
    }

    /** Projects the body's box to the view (0..1), for touch zones. Rotation does not move it. */
    private fun publishBody(base: FloatArray, plan: BodyPlan) {
        val locator = body ?: return
        val mvp = Mat4.multiply(p.viewProj, base)
        fun screen(x: Float, y: Float, z: Float): FloatArray {
            val c = Mat4.transform(mvp, x, y, z)
            val w = if (abs(c[3]) < 1e-5f) 1f else c[3]
            return floatArrayOf((c[0] / w + 1f) / 2f, (1f - c[1] / w) / 2f)
        }
        val midY = (plan.top + plan.bottom) / 2f
        val c = screen(0f, midY, 0f)
        val right = screen(plan.halfWidth, midY, 0f)
        val top = screen(0f, plan.top, 0f)
        locator.box = BodyBox(c[0], c[1], abs(right[0] - c[0]), abs(c[1] - top[1]))
    }

    /** Inside the house nothing on the stage is ZnaiKo's body. */
    private fun hideBody() {
        body?.box = BodyBox(-10f, -10f, 0.01f, 0.01f)
    }

    // --------------------------------------------------------------------- going home

    private fun updateHome(s: SceneState, dt: Float) {
        if (!started) {
            started = true
            // Opened while ZnaiKo is at home: it is simply inside, no film.
            if (s.atHome && !s.preview) {
                home = Home.IN; posX = HouseModel.X; posZ = HouseModel.INSIDE_Z; camHome = 1f; house.light = 1f; doorScale = DOOR_SCALE
            }
        }
        val wantsHome = s.atHome && !s.preview
        when (home) {
            Home.OUT -> if (wantsHome) { home = Home.GOING; homeT = 0f }
            Home.IN -> if (!wantsHome) { home = Home.LEAVING; homeT = 0f }
            else -> Unit
        }
        homeT += dt
        val porchX = HouseModel.X
        val porchZ = HouseModel.PORCH_Z
        val leg = hypot(porchX, porchZ)
        val legT = leg / WALK_SPEED
        val toHouse = deg(atan2(porchX, porchZ))
        val fromHouse = deg(atan2(-porchX, -porchZ))
        var walking = false
        when (home) {
            Home.OUT -> {
                posX = 0f; posZ = 0f; walkYaw = 0f; doorScale = 1f
                camHome = Ease.approach(camHome, 0f, 3f, dt)
                house.door = Ease.approach(house.door, 0f, 4f, dt)
                house.light = Ease.approach(house.light, 0f, 2f, dt)
            }
            Home.IN -> {
                camHome = Ease.approach(camHome, 1f, 3f, dt)
                house.door = Ease.approach(house.door, 0f, 5f, dt)
                house.light = Ease.approach(house.light, 1f, 3f, dt)
            }
            Home.GOING -> {
                var t = homeT
                when {
                    t < TURN -> walkYaw = toHouse * Ease.smooth(t / TURN)
                    (t - TURN).also { t = it } < legT -> {
                        val u = t / legT
                        posX = porchX * u; posZ = porchZ * u; walkYaw = toHouse; walking = true
                        camHome = maxOf(camHome, Ease.smooth(u))
                    }
                    (t - legT).also { t = it } < DOOR_TURN -> {
                        walkYaw = toHouse + (-180f - toHouse) * Ease.smooth(t / DOOR_TURN)
                        house.door = Ease.smooth(t / DOOR_TURN)
                    }
                    (t - DOOR_TURN).also { t = it } < ENTER -> {
                        val u = t / ENTER
                        posX = porchX; posZ = porchZ + (HouseModel.INSIDE_Z - porchZ) * u; walkYaw = -180f; walking = true
                        doorScale = 1f + (DOOR_SCALE - 1f) * Ease.smooth((u / 0.6f).coerceAtMost(1f))
                        house.door = 1f
                        house.light = Ease.smooth(u)
                    }
                    else -> { home = Home.IN; camHome = 1f; walkYaw = 0f }
                }
            }
            Home.LEAVING -> {
                var t = homeT
                when {
                    t < DOOR_TURN -> { house.door = Ease.smooth(t / DOOR_TURN); walkYaw = 0f; doorScale = DOOR_SCALE; posX = porchX; posZ = HouseModel.INSIDE_Z }
                    (t - DOOR_TURN).also { t = it } < ENTER -> {
                        val u = t / ENTER
                        posX = porchX; posZ = HouseModel.INSIDE_Z + (porchZ - HouseModel.INSIDE_Z) * u; walking = true
                        doorScale = DOOR_SCALE + (1f - DOOR_SCALE) * Ease.smooth(((u - 0.55f) / 0.45f).coerceIn(0f, 1f))
                        house.light = 1f - Ease.smooth(u)
                    }
                    (t - ENTER).also { t = it } < TURN -> {
                        walkYaw = fromHouse * Ease.smooth(t / TURN); doorScale = 1f
                        house.door = 1f - Ease.smooth(t / TURN)
                    }
                    (t - TURN).also { t = it } < legT -> {
                        val u = t / legT
                        posX = porchX * (1f - u); posZ = porchZ * (1f - u); walkYaw = fromHouse; walking = true
                        camHome = minOf(camHome, 1f - Ease.smooth(u))
                    }
                    (t - legT).also { t = it } < TURN -> walkYaw = fromHouse * (1f - Ease.smooth(t / TURN))
                    else -> { home = Home.OUT; walkYaw = 0f; posX = 0f; posZ = 0f }
                }
            }
        }
        anim.walk = Ease.approach(anim.walk, if (walking) 1f else 0f, 10f, dt)
        if (anim.walk > 0.01f) anim.walkPhase += dt * 11f
    }

    private fun deg(rad: Float) = rad * 180f / PI.toFloat()

    // --------------------------------------------------------------------- animation

    private fun animate(s: SceneState, dt: Float, now: Long) {
        val time = anim.time
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
        val out = home == Home.OUT
        twirl?.take()?.let { step ->
            if (out) {
                spin += step.dragDeg
                spinHeld = step.dragging
                if (step.dragging) spinVel = 0f
                step.flingDegPerS?.let { spinVel = it }
            }
        }
        if (!spinHeld || !out) {
            if (abs(spinVel) > 30f && out) {
                spin += spinVel * dt
                spinVel *= exp(-1.6f * dt)
            } else {
                // Slowed down: turn back to face the user the short way round.
                spinVel = 0f
                val homeAngle = (spin / 360f).roundToInt() * 360f
                spin = Ease.approach(spin, homeAngle, 3f, dt)
                if (abs(spin - homeAngle) < 0.3f) spin -= homeAngle
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
            Gesture.SPIN -> if (g < 1f && out) Ease.smooth(g) * 360f else 0f
            else -> 0f
        }
        if (gesture == Gesture.SPIN && g >= 1f) {
            gesture = Gesture.NONE
            yaw.value = yaw.value % 360f
        }
        pitch.target = if (gesture == Gesture.NOD) wave * 18f else if (s.pose.expression == Expression.THINKING) -8f else 0f
        roll.target = if (gesture == Gesture.WAVE) wave * 10f else if (s.pose.expression == Expression.CONFUSED) 12f else 0f
        // Alive when idle: a slow sway and a look around, so it reads as a round 3D toy, not a flat sticker.
        if (!s.sleeping && out && (gesture == Gesture.NONE || g >= 1f)) {
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
        anim.sway = roll.value
        anim.waving = gesture == Gesture.WAVE
        anim.wince = time < winceUntil

        // Eyes: follow the finger, otherwise wander a little.
        wander += dt
        val target = lookTarget?.takeIf { now < lookUntilNs || time < lookHoldUntil }
        var tx = target?.let { (it.first - 0.5f) * 2f } ?: (sin(wander * 0.7f) * 0.35f)
        var ty = target?.let { (0.5f - it.second) * 2f } ?: (sin(wander * 0.43f) * 0.2f)
        if (time < dizzyUntil) {
            // Eyes roll round and round.
            tx = cos(time * 9f); ty = sin(time * 9f)
        }
        anim.lookX = Ease.approach(anim.lookX, tx.coerceIn(-1f, 1f), 10f, dt)
        anim.lookY = Ease.approach(anim.lookY, ty.coerceIn(-1f, 1f), 10f, dt)

        // Blink: random 2..6 s, sometimes twice.
        if (blinkPhase < 0f && time >= nextBlinkAt) blinkPhase = 0f
        if (blinkPhase >= 0f) {
            blinkPhase += dt / 0.18f
            anim.blink = if (blinkPhase < 0.5f) blinkPhase * 2f else (1f - (blinkPhase - 0.5f) * 2f)
            if (blinkPhase >= 1f) {
                blinkPhase = -1f; anim.blink = 0f
                nextBlinkAt = time + if (Random.nextFloat() < 0.18f) 0.25f else Random.nextFloat() * 4f + 2f
            }
        }

        val speaking = s.pose.speaking
        anim.mouthOpen = Ease.approach(anim.mouthOpen, if (speaking) s.pose.viseme.openness else if (s.pose.expression == Expression.SURPRISED) 0.7f else 0f, 25f, dt)
        anim.mouthWide = Ease.approach(anim.mouthWide, if (speaking) s.pose.viseme.width * (1f - 0.4f * s.pose.viseme.round) else 0.5f, 25f, dt)
        anim.tongue = Ease.approach(anim.tongue, if (s.pose.expression == Expression.TONGUE) 1f else 0f, 9f, dt)
    }

    private companion object {
        const val WALK_SPEED = 2.3f
        const val TURN = 0.35f
        const val DOOR_TURN = 0.45f
        const val ENTER = 1.0f
        /** Small enough to fit through the door. */
        const val DOOR_SCALE = 0.6f
        const val HOME_CX = HouseModel.X - 0.2f
        const val HOME_CY = 1.0f
    }
}
