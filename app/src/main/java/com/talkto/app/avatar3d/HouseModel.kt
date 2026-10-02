package com.talkto.app.avatar3d

import com.talkto.app.avatar3d.Painter.Companion.argb
import com.talkto.app.avatar3d.Painter.Companion.darken
import com.talkto.app.avatar3d.Painter.Companion.lighten
import com.talkto.app.avatar3d.Painter.Companion.rgb
import com.talkto.app.avatar3d.Painter.Companion.v
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.look.HouseDecor
import com.talkto.core.look.HouseLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ZnaiKo's house, to the left of where it usually stands: walls, a hip roof, a door on a hinge, two windows
 * that glow when someone is home, a chimney and whatever decor was bought for the yard.
 */
internal class HouseModel(private val p: Painter) {

    /** 0 closed .. 1 wide open. */
    var door = 0f
    /** 0..1: how warm the windows are lit. */
    var light = 0f

    fun draw(h: HouseLook, a: Anim, sleepingInside: Boolean) {
        val wall = argb(h.wallColor)
        val roof = argb(h.roofColor)
        val doorC = argb(h.doorColor)
        val base = Mat4.identity()
        fun at(x: Float, y: Float, z: Float) = Mat4.multiply(base, Mat4.translation(x, y, z))

        // Walls on a low stone plinth.
        p.part(p.box, base, darken(wall, 0.8f), t = v(X, -0.93f, Z), sc = v(W + 0.12f, 0.14f, D + 0.12f), shine = 0.1f)
        p.part(p.box, base, wall, t = v(X, -1f + H / 2f, Z), sc = v(W, H, D), shine = 0.15f, rim = 0.2f)
        // Corner posts.
        for (sx in intArrayOf(-1, 1)) p.part(p.box, base, darken(wall, 0.85f), t = v(X + sx * W / 2f, -1f + H / 2f, FRONT), sc = v(0.14f, H, 0.14f), shine = 0.1f)
        // Hip roof: a four-sided cone turned to the walls, with an overhang.
        val roofM = Mat4.multiply(at(X, -1f + H - 0.02f, Z), Mat4.multiply(Mat4.scale((W + 0.5f) / 1.414f, 1.35f, (D + 0.5f) / 1.414f), Mat4.rotationY(45f)))
        p.part(p.pyramid, roofM, roof, shine = 0.35f, rim = 0.3f)
        p.part(p.box, base, darken(roof, 0.75f), t = v(X, -1f + H, Z), sc = v(W + 0.46f, 0.08f, D + 0.46f), shine = 0.2f)
        // Chimney.
        p.part(p.box, base, darken(wall, 0.7f), t = v(X + 0.8f, -1f + H + 0.75f, Z - 0.35f), sc = v(0.38f, 0.95f, 0.38f), shine = 0.1f)
        p.part(p.box, base, darken(wall, 0.55f), t = v(X + 0.8f, -1f + H + 1.24f, Z - 0.35f), sc = v(0.46f, 0.08f, 0.46f), shine = 0.1f)

        // Doorway, dark inside, and the door on its hinge (left edge).
        p.part(p.box, base, rgb(0x2B1D14), t = v(X, -1f + DOOR_H / 2f, FRONT + 0.005f), sc = v(DOOR_W, DOOR_H, 0.02f), shine = 0f, rim = 0f)
        val hinge = Mat4.multiply(at(X - DOOR_W / 2f, -1f + DOOR_H / 2f, FRONT + 0.03f), Mat4.rotationY(-door * 100f))
        p.part(p.box, hinge, doorC, t = v(DOOR_W / 2f, 0f, 0f), sc = v(DOOR_W, DOOR_H, 0.06f), shine = 0.3f)
        p.part(p.box, hinge, darken(doorC, 0.75f), t = v(DOOR_W / 2f, 0.35f, 0.035f), sc = v(DOOR_W * 0.7f, 0.5f, 0.02f), shine = 0.2f)
        p.part(p.sphere, hinge, rgb(0xFFC857), t = v(DOOR_W * 0.85f, -0.05f, 0.06f), sc = v(0.06f, 0.06f, 0.06f), shine = 1.2f)
        // Door frame.
        p.part(p.box, base, darken(wall, 0.7f), t = v(X, -1f + DOOR_H + 0.05f, FRONT + 0.02f), sc = v(DOOR_W + 0.2f, 0.1f, 0.06f), shine = 0.2f)
        // Windows: lit from inside when ZnaiKo is home.
        val glass = mix(rgb(0x9ED8F5), rgb(0xFFD166), light)
        for (wx in floatArrayOf(X - 1.13f, X + 1.13f)) {
            p.part(p.box, base, darken(wall, 0.7f), t = v(wx, WIN_Y, FRONT + 0.01f), sc = v(0.66f, 0.66f, 0.05f), shine = 0.2f)
            p.part(p.box, base, glass, t = v(wx, WIN_Y, FRONT + 0.03f), sc = v(0.54f, 0.54f, 0.03f), shine = 1.2f, rim = 0.3f, glow = 0.15f + 0.75f * light)
            p.part(p.box, base, darken(wall, 0.6f), t = v(wx, WIN_Y, FRONT + 0.05f), sc = v(0.05f, 0.56f, 0.02f), shine = 0.2f)
            p.part(p.box, base, darken(wall, 0.6f), t = v(wx, WIN_Y, FRONT + 0.05f), sc = v(0.56f, 0.05f, 0.02f), shine = 0.2f)
            p.part(p.box, base, darken(wall, 0.75f), t = v(wx, WIN_Y - 0.36f, FRONT + 0.07f), sc = v(0.76f, 0.06f, 0.14f), shine = 0.2f)
        }
        // A doormat.
        p.part(p.box, base, rgb(0xB5838D), t = v(X, -0.99f, FRONT + 0.45f), sc = v(0.9f, 0.02f, 0.5f), shine = 0f)

        for (d in h.decor) drawDecor(d, a)
        if (sleepingInside) drawSnores(a)
    }

    /** Blended extras: smoke from the chimney. */
    fun drawBlended(h: HouseLook, a: Anim) {
        if (HouseDecor.SMOKE !in h.decor) return
        for (i in 0 until 5) {
            val t = ((a.time * 0.25f + i / 5f) % 1f)
            val r = 0.14f + t * 0.3f
            p.part(
                p.sphere, Mat4.identity(), rgb(0xE9ECEF),
                t = v(X + 0.8f + sin(a.time + i) * 0.12f + t * 0.4f, -1f + H + 1.35f + t * 1.6f, Z - 0.35f),
                sc = v(r, r, r), shine = 0f, rim = 0.3f, alpha = 0.55f * (1f - t), glow = 0.3f,
            )
        }
    }

    private fun drawDecor(d: HouseDecor, a: Anim) {
        val base = Mat4.identity()
        fun at(x: Float, y: Float, z: Float) = Mat4.multiply(base, Mat4.translation(x, y, z))
        when (d) {
            HouseDecor.FLOWERS -> {
                val colours = intArrayOf(0xFF8FA3, 0xFFE066, 0xF15BB5, 0xFFFFFF, 0x9B5DE5)
                var k = 0
                for (fx in floatArrayOf(X - 1.45f, X - 1.1f, X - 0.8f, X + 0.8f, X + 1.1f, X + 1.45f)) {
                    val sway = sin(a.time * 1.5f + fx) * 5f
                    val stem = Mat4.multiply(at(fx, -1f, FRONT + 0.22f), Mat4.rotationZ(sway))
                    p.part(p.tube, stem, rgb(0x52B788), t = v(0f, 0.18f, 0f), sc = v(0.02f, 0.36f, 0.02f), shine = 0.2f)
                    val c = rgb(colours[k++ % colours.size])
                    for (j in 0 until 5) {
                        val b = 2 * PI * j / 5
                        p.part(p.sphere, stem, c, t = v((0.06 * cos(b)).toFloat(), 0.38f, (0.06 * sin(b)).toFloat()), sc = v(0.055f, 0.04f, 0.055f), shine = 0.3f, rim = 0.4f)
                    }
                    p.part(p.sphere, stem, rgb(0xFFC857), t = v(0f, 0.4f, 0f), sc = v(0.035f, 0.03f, 0.035f), shine = 0.8f)
                }
            }
            HouseDecor.SMOKE -> Unit // blended pass
            HouseDecor.LANTERN -> {
                val lx = X + W / 2f + 0.45f
                p.part(p.tube, base, rgb(0x2D2A32), t = v(lx, -0.35f, FRONT + 0.4f), sc = v(0.04f, 1.3f, 0.04f), shine = 0.6f)
                p.part(p.box, base, rgb(0x2D2A32), t = v(lx, 0.36f, FRONT + 0.4f), sc = v(0.24f, 0.3f, 0.24f), shine = 0.6f)
                p.part(p.box, base, rgb(0xFFD166), t = v(lx, 0.36f, FRONT + 0.4f), sc = v(0.19f, 0.25f, 0.25f), shine = 0.5f, glow = 0.55f + 0.45f * light)
                p.part(p.pyramid, Mat4.multiply(at(lx, 0.51f, FRONT + 0.4f), Mat4.multiply(Mat4.scale(0.2f, 0.14f, 0.2f), Mat4.rotationY(45f))), rgb(0x2D2A32), shine = 0.6f)
            }
            HouseDecor.MAILBOX -> {
                val mx = X - W / 2f - 0.55f
                p.part(p.box, base, rgb(0x8B5A2B), t = v(mx, -0.55f, FRONT + 0.7f), sc = v(0.08f, 0.9f, 0.08f), shine = 0.1f)
                p.part(p.box, base, rgb(0x3F88C5), t = v(mx, -0.02f, FRONT + 0.7f), sc = v(0.3f, 0.26f, 0.5f), shine = 0.6f)
                p.part(p.box, base, rgb(0xE4572E), t = v(mx + 0.17f, 0.12f, FRONT + 0.6f), sc = v(0.03f, 0.3f, 0.04f), shine = 0.5f)
                p.part(p.box, base, rgb(0xE4572E), t = v(mx + 0.17f, 0.24f, FRONT + 0.67f), sc = v(0.03f, 0.1f, 0.14f), shine = 0.5f)
            }
            HouseDecor.FENCE -> {
                val wood = rgb(0xE9D8A6)
                var z = FRONT + 1.6f
                while (z > Z - D / 2f) {
                    p.part(p.box, base, wood, t = v(X - W / 2f - 0.9f, -0.72f, z), sc = v(0.1f, 0.56f, 0.12f), shine = 0.1f)
                    p.part(p.pyramid, Mat4.multiply(at(X - W / 2f - 0.9f, -0.44f, z), Mat4.multiply(Mat4.scale(0.07f, 0.1f, 0.08f), Mat4.rotationY(45f))), wood, shine = 0.1f)
                    z -= 0.3f
                }
                p.part(p.box, base, darken(wood, 0.9f), t = v(X - W / 2f - 0.9f, -0.6f, (FRONT + 1.6f + Z - D / 2f) / 2f), sc = v(0.05f, 0.07f, FRONT + 1.6f - (Z - D / 2f)), shine = 0.1f)
            }
            HouseDecor.FLAG -> {
                val top = -1f + H + 1.35f
                p.part(p.tube, base, rgb(0xDDDDDD), t = v(X, top + 0.35f, Z), sc = v(0.025f, 0.7f, 0.025f), shine = 1f)
                val wave = sin(a.time * 4f) * 12f
                val m = Mat4.multiply(at(X, top + 0.58f, Z), Mat4.rotationY(wave))
                p.part(p.box, m, rgb(0xE4572E), t = v(0.2f, 0f, 0f), sc = v(0.4f, 0.24f, 0.02f), shine = 0.3f)
                p.part(p.sphere, base, rgb(0xFFC857), t = v(X, top + 0.72f, Z), sc = v(0.045f, 0.045f, 0.045f), shine = 1f)
            }
            HouseDecor.TREE -> {
                val tx = X - W / 2f - 1.1f
                val tz = Z - D / 2f + 0.2f
                p.part(p.cylinder, base, rgb(0x8B5A2B), t = v(tx, -0.2f, tz), sc = v(0.16f, 1.6f, 0.16f), shine = 0.1f)
                val leaves = rgb(0x52B788)
                for ((dx, dy, r) in listOf(Triple(0f, 1.05f, 0.7f), Triple(-0.4f, 0.75f, 0.5f), Triple(0.42f, 0.8f, 0.5f), Triple(0.05f, 1.55f, 0.45f))) {
                    p.part(p.sphere, base, if (dy > 1.2f) lighten(leaves, 0.15f) else leaves, t = v(tx + dx, dy, tz), sc = v(r, r * 0.9f, r), shine = 0.15f, rim = 0.5f)
                }
                p.part(p.sphere, base, rgb(0xE63946), t = v(tx + 0.3f, 1.0f, tz + 0.6f), sc = v(0.08f, 0.08f, 0.08f), shine = 1f)
                p.part(p.sphere, base, rgb(0xE63946), t = v(tx - 0.35f, 0.7f, tz + 0.45f), sc = v(0.08f, 0.08f, 0.08f), shine = 1f)
            }
        }
    }

    /** Little bubbles rising from the window while ZnaiKo sleeps inside. */
    private fun drawSnores(a: Anim) {
        for (i in 0 until 3) {
            val t = ((a.time * 0.35f + i / 3f) % 1f)
            val r = 0.05f + 0.07f * t
            p.part(p.sphere, Mat4.identity(), rgb(0x9ED8F5), t = v(X + 1.2f + t * 0.5f + sin(a.time * 2f + i) * 0.08f, WIN_Y + 0.4f + t * 1.1f, FRONT + 0.2f), sc = v(r, r, r), shine = 1f, rim = 0.8f, glow = 0.3f)
        }
    }

    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + (b[it] - a[it]) * t }

    companion object {
        /** House centre and size in world units; the creature is about 2 wide. */
        const val X = -4.3f
        const val Z = -1.1f
        const val W = 3.3f
        const val D = 2.8f
        const val H = 2.4f
        const val FRONT = Z + D / 2f
        const val DOOR_W = 1.3f
        const val DOOR_H = 1.75f
        const val WIN_Y = 0.35f

        /** Where ZnaiKo stands to open the door, and where it goes when inside. */
        const val PORCH_Z = FRONT + 1.2f
        const val INSIDE_Z = FRONT - 1.0f
    }
}
