package com.talkto.app.avatar3d

import com.talkto.app.avatar3d.Painter.Companion.argb
import com.talkto.app.avatar3d.Painter.Companion.darken
import com.talkto.app.avatar3d.Painter.Companion.lighten
import com.talkto.app.avatar3d.Painter.Companion.rgb
import com.talkto.app.avatar3d.Painter.Companion.v
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.look.Clothes
import com.talkto.core.look.Glasses
import com.talkto.core.look.Hat
import com.talkto.core.look.OutfitConfig
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hats, glasses and clothes. Hats sit in the head's design space ([headM]); glasses follow the eyes wherever
 * the creator put them; clothes are fitted to the body by measuring it ([BodyPlan.cut], [BodyPlan.frontZ]).
 */
internal class OutfitModel(private val p: Painter) {

    private val ink = rgb(0x2D2A32)
    private val gold = rgb(0xFFC857)
    private val white = rgb(0xFFFFFF)

    fun draw(root: FloatArray, headM: FloatArray, plan: BodyPlan, o: OutfitConfig, eyes: List<EyeSpot>, a: Anim, face: FaceSpace) {
        drawClothes(root, plan, o, a)
        drawGlasses(headM, o, eyes, face)
        drawHat(headM, o, a)
    }

    // ------------------------------------------------------------------ hats

    private fun drawHat(headM: FloatArray, o: OutfitConfig, a: Anim) {
        val hat = argb(o.hatColor)
        fun at(x: Float, y: Float, z: Float) = Mat4.multiply(headM, Mat4.translation(x, y, z))
        when (o.hat) {
            Hat.NONE -> Unit
            Hat.PARTY -> {
                val m = Mat4.multiply(at(0.12f, 0.8f, 0f), Mat4.rotationZ(-12f))
                p.part(p.cone, m, hat, sc = v(0.34f, 0.8f, 0.34f), shine = 0.4f)
                p.part(p.sphere, m, gold, t = v(0f, 0.82f, 0f), sc = v(0.09f, 0.09f, 0.09f))
            }
            Hat.BEANIE -> {
                // Sits above the eyes (their top edge is at y = 0.42).
                p.part(p.hemisphere, headM, hat, t = v(0f, 0.47f, 0f), sc = v(0.86f, 0.6f, 0.84f), shine = 0.1f)
                p.part(p.tube, headM, darken(hat), t = v(0f, 0.5f, 0f), sc = v(0.87f, 0.11f, 0.85f), shine = 0.1f)
                p.part(p.sphere, headM, white, t = v(0f, 1.1f, 0f), sc = v(0.12f, 0.12f, 0.12f), shine = 0f)
            }
            Hat.CROWN -> {
                p.part(p.tube, headM, gold, t = v(0f, 0.98f, 0f), sc = v(0.42f, 0.26f, 0.42f), shine = 1f)
                for (k in 0 until 5) {
                    val ang = 2 * PI * k / 5
                    p.part(p.sphere, headM, hat, t = v((0.42 * cos(ang)).toFloat(), 1.13f, (0.42 * sin(ang)).toFloat()), sc = v(0.06f, 0.06f, 0.06f), shine = 1f)
                }
            }
            Hat.TOP_HAT -> {
                p.part(p.cylinder, headM, ink, t = v(0f, 0.84f, 0f), sc = v(0.75f, 0.05f, 0.75f))
                p.part(p.cylinder, headM, ink, t = v(0f, 1.15f, 0f), sc = v(0.44f, 0.62f, 0.44f))
                p.part(p.tube, headM, hat, t = v(0f, 0.93f, 0f), sc = v(0.455f, 0.1f, 0.455f))
            }
            Hat.CAP -> {
                p.part(p.hemisphere, headM, hat, t = v(0f, 0.5f, 0f), sc = v(0.84f, 0.5f, 0.82f), shine = 0.25f)
                // The brim sticks out forward above the eyes instead of covering them.
                val brim = Mat4.multiply(at(0f, 0.54f, 0.7f), Mat4.rotationX(14f))
                p.part(p.sphere, brim, darken(hat), sc = v(0.48f, 0.05f, 0.38f), shine = 0.3f)
            }
            Hat.WIZARD -> {
                p.part(p.cylinder, headM, darken(hat, 0.85f), t = v(0f, 0.8f, 0f), sc = v(0.82f, 0.035f, 0.82f), shine = 0.3f)
                val m = Mat4.multiply(at(0.04f, 0.82f, 0f), Mat4.rotationZ(-8f + sin(a.time * 1.3f) * 3f))
                p.part(p.cone, m, hat, sc = v(0.46f, 1.08f, 0.46f), shine = 0.3f)
                for ((x, y, z) in listOf(Triple(0.24f, 0.25f, 0.3f), Triple(-0.2f, 0.5f, 0.2f), Triple(0.08f, 0.78f, 0.1f))) {
                    val star = Mat4.multiply(Mat4.multiply(m, Mat4.translation(x, y, z)), Mat4.rotationZ(a.time * 40f))
                    p.part(p.box, star, gold, sc = v(0.06f, 0.06f, 0.02f), shine = 1f, glow = 0.5f)
                    p.part(p.box, Mat4.multiply(star, Mat4.rotationZ(45f)), gold, sc = v(0.06f, 0.06f, 0.02f), shine = 1f, glow = 0.5f)
                }
                p.part(p.sphere, m, gold, t = v(0f, 1.06f, 0f), sc = v(0.06f, 0.06f, 0.06f), shine = 1f, glow = 0.4f)
            }
            Hat.FLOWER_CROWN -> {
                val petals = intArrayOf(0xFF8FA3, 0xFFFFFF, 0xFFE066, 0xC8B6FF)
                for (k in 0 until 8) {
                    val ang = 2 * PI * k / 8 + PI / 8
                    val x = (0.47 * cos(ang)).toFloat()
                    val z = (0.43 * sin(ang)).toFloat()
                    // Each flower faces outwards and a little up.
                    val flower = Mat4.multiply(Mat4.multiply(at(x, 0.84f, z), Mat4.rotationY(90f - Math.toDegrees(ang).toFloat())), Mat4.rotationX(-30f))
                    val c = rgb(petals[k % petals.size])
                    for (j in 0 until 5) {
                        val b = 2 * PI * j / 5
                        p.part(p.sphere, flower, c, t = v((0.075 * cos(b)).toFloat(), (0.075 * sin(b)).toFloat(), 0.03f), sc = v(0.065f, 0.065f, 0.03f), shine = 0.3f, rim = 0.4f)
                    }
                    p.part(p.sphere, flower, gold, t = v(0f, 0f, 0.05f), sc = v(0.04f, 0.04f, 0.03f), shine = 0.8f)
                    val leafAng = ang + PI / 8
                    p.part(p.sphere, headM, rgb(0x52B788), t = v((0.48 * cos(leafAng)).toFloat(), 0.82f, (0.44 * sin(leafAng)).toFloat()), sc = v(0.08f, 0.03f, 0.045f), shine = 0.4f)
                }
            }
            Hat.COWBOY -> {
                p.part(p.cylinder, headM, hat, t = v(0f, 0.78f, 0f), sc = v(1.02f, 0.035f, 0.92f), shine = 0.2f)
                for (side in intArrayOf(-1, 1)) {
                    val curl = Mat4.multiply(at(0.92f * side, 0.86f, 0f), Mat4.rotationZ(-35f * side))
                    p.part(p.sphere, curl, hat, sc = v(0.18f, 0.05f, 0.5f), shine = 0.2f)
                }
                p.part(p.cylinder, headM, hat, t = v(0f, 1.0f, 0f), sc = v(0.5f, 0.42f, 0.44f), shine = 0.2f)
                p.part(p.sphere, headM, darken(hat, 0.9f), t = v(0f, 1.2f, 0f), sc = v(0.5f, 0.1f, 0.44f), shine = 0.2f)
                p.part(p.tube, headM, darken(hat, 0.55f), t = v(0f, 0.87f, 0f), sc = v(0.505f, 0.08f, 0.445f), shine = 0.3f)
            }
            Hat.HALO -> {
                val m = Mat4.multiply(at(0f, 1.13f + sin(a.time * 2f) * 0.035f, 0f), Mat4.rotationX(90f))
                p.part(p.ring, m, rgb(0xFFE066), sc = v(0.42f, 0.42f, 0.6f), shine = 1.2f, rim = 0.8f, glow = 0.75f)
            }
            Hat.CHEF -> {
                p.part(p.cylinder, headM, white, t = v(0f, 0.9f, 0f), sc = v(0.52f, 0.24f, 0.5f), shine = 0.15f)
                for (k in 0 until 5) {
                    val ang = 2 * PI * k / 5
                    p.part(p.sphere, headM, white, t = v((0.3 * cos(ang)).toFloat(), 1.12f, (0.28 * sin(ang)).toFloat()), sc = v(0.27f, 0.24f, 0.27f), shine = 0.15f, rim = 0.5f)
                }
                p.part(p.sphere, headM, white, t = v(0f, 1.24f, 0f), sc = v(0.3f, 0.26f, 0.3f), shine = 0.15f, rim = 0.5f)
            }
            Hat.VIKING -> {
                val metal = rgb(0x9AA5B1)
                p.part(p.hemisphere, headM, metal, t = v(0f, 0.45f, 0f), sc = v(0.9f, 0.62f, 0.88f), shine = 1f, rim = 0.5f)
                p.part(p.tube, headM, rgb(0xB08968), t = v(0f, 0.5f, 0f), sc = v(0.91f, 0.1f, 0.89f), shine = 0.5f)
                for (side in intArrayOf(-1, 1)) {
                    val horn = Mat4.multiply(at(0.7f * side, 0.8f, 0f), Mat4.rotationZ(-55f * side))
                    p.part(p.cone, horn, rgb(0xFFF1D6), sc = v(0.11f, 0.42f, 0.11f), shine = 0.6f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ glasses

    private fun drawGlasses(headM: FloatArray, o: OutfitConfig, eyes: List<EyeSpot>, face: FaceSpace) {
        if (o.glasses == Glasses.NONE) return
        val feature = face::at
        fun z(e: EyeSpot) = face.surf(e.x, e.y) + 0.073f * e.size
        fun bridge(c: FloatArray) {
            for (i in 0 until eyes.size - 1) {
                val l = eyes[i]
                val r = eyes[i + 1]
                val mx = (l.x + r.x) / 2f
                val my = (l.y + r.y) / 2f + 0.02f
                val len = sqrt((r.x - l.x) * (r.x - l.x) + (r.y - l.y) * (r.y - l.y)) - 0.36f * (l.size + r.size) / 2f
                if (len <= 0.01f) continue
                val ang = Math.toDegrees(atan2((r.y - l.y).toDouble(), (r.x - l.x).toDouble())).toFloat()
                val m = Mat4.multiply(feature(mx, my, face.surf(mx, my) + 0.05f), Mat4.rotationZ(90f + ang))
                p.part(p.tube, m, c, sc = v(0.018f, len, 0.018f))
            }
        }
        when (o.glasses) {
            Glasses.NONE -> Unit
            Glasses.ROUND, Glasses.HEART -> {
                val c = if (o.glasses == Glasses.HEART) rgb(0xF15BB5) else ink
                for (e in eyes) p.part(p.ring, feature(e.x, e.y, z(e)), c, sc = v(0.2f * e.size, 0.2f * e.size, 0.3f), shine = 1f)
                bridge(c)
            }
            Glasses.SUNGLASSES -> {
                val c = rgb(0x151515)
                for (e in eyes) p.part(p.sphere, feature(e.x, e.y, z(e) - 0.02f), c, sc = v(0.23f * e.size, 0.16f * e.size, 0.06f), shine = 1.2f)
                bridge(c)
            }
            Glasses.MONOCLE -> {
                val e = eyes.last()
                p.part(p.ring, feature(e.x, e.y, z(e)), gold, sc = v(0.22f * e.size, 0.22f * e.size, 0.3f), shine = 1f)
            }
            Glasses.STAR -> {
                val c = rgb(0xFFE066)
                for (e in eyes) {
                    val center = feature(e.x, e.y, z(e) - 0.03f)
                    for (k in 0 until 5) p.part(p.cone, Mat4.multiply(center, Mat4.rotationZ(72f * k)), c, sc = v(0.08f * e.size, 0.25f * e.size, 0.02f), shine = 1f, glow = 0.2f)
                    p.part(p.ring, center, rgb(0xF4A261), sc = v(0.13f * e.size, 0.13f * e.size, 0.25f), shine = 1f)
                }
                bridge(c)
            }
            Glasses.SKI -> {
                val minX = eyes.minOf { it.x - 0.24f * it.size }
                val maxX = eyes.maxOf { it.x + 0.24f * it.size }
                val cy = eyes.map { it.y }.average().toFloat()
                val size = eyes.maxOf { it.size }
                val cx = (minX + maxX) / 2f
                p.part(p.sphere, feature(cx, cy, face.surf(cx, cy) + 0.02f), rgb(0xFF9F1C), sc = v((maxX - minX) / 2f + 0.04f, 0.17f * size, 0.09f), shine = 1.4f, rim = 0.9f)
                p.part(p.sphere, feature(cx + 0.1f, cy + 0.05f, face.surf(cx, cy) + 0.1f), rgb(0xFFFFFF), sc = v(0.12f, 0.025f, 0.01f), shine = 1f, rim = 0f)
                val r = sqrt(maxOf(0.1f, 1f - (cy / 0.93f) * (cy / 0.93f)))
                p.part(p.band, Mat4.multiply(Mat4.multiply(headM, Mat4.translation(0f, cy, 0f)), Mat4.rotationX(90f)), rgb(0x3F88C5), sc = v(r, r * 0.9f, 1.4f), shine = 0.3f)
            }
            Glasses.THREE_D -> {
                val lens = listOf(rgb(0xE63946), rgb(0x4CC9F0))
                eyes.forEachIndexed { i, e ->
                    val m = feature(e.x, e.y, z(e) - 0.01f)
                    p.part(p.box, m, white, sc = v(0.46f * e.size, 0.3f * e.size, 0.03f), shine = 0.4f)
                    p.part(p.box, m, lens[i % 2], t = v(0f, 0f, 0.02f), sc = v(0.36f * e.size, 0.22f * e.size, 0.02f), shine = 1.2f, glow = 0.25f)
                }
                bridge(white)
            }
        }
    }

    // ------------------------------------------------------------------ clothes

    private fun drawClothes(root: FloatArray, plan: BodyPlan, o: OutfitConfig, a: Anim) {
        val cloth = argb(o.clothesColor)
        val neck = plan.neckY
        val (nw, nd) = plan.cut(neck)
        // Ties, bows and medals hang just under the mouth; the scarf goes round the neck line.
        val chest = neck + 0.14f * plan.head.ry / 0.93f
        val chestZ = plan.frontZ(0f, chest - 0.02f)
        val neckZ = plan.frontZ(0f, neck)
        fun at(x: Float, y: Float, z: Float) = Mat4.multiply(root, Mat4.translation(x, y, z))
        /** A ring lying flat around the body at height [y]. */
        fun band(y: Float, w: Float, d: Float, thickness: Float, c: FloatArray, shine: Float = 0.1f) =
            p.part(p.ring, Mat4.multiply(at(0f, y, 0f), Mat4.rotationX(90f)), c, sc = v(w, d, thickness), shine = shine)

        when (o.clothes) {
            Clothes.NONE -> Unit
            Clothes.SCARF -> {
                band(neck, nw * 1.07f, maxOf(nd * 1.07f, neckZ + 0.04f), 1.4f, cloth)
                val end = Mat4.multiply(at(nw * 0.35f, neck - 0.28f, plan.frontZ(nw * 0.35f, neck - 0.28f) + 0.02f), Mat4.rotationZ(8f))
                p.part(p.box, end, cloth, sc = v(0.2f, 0.42f, 0.06f), shine = 0.1f)
                p.part(p.box, end, lighten(cloth, 0.5f), t = v(0f, -0.12f, 0.035f), sc = v(0.2f, 0.04f, 0.01f), shine = 0.1f)
            }
            Clothes.BOWTIE -> {
                for (side in intArrayOf(-1, 1)) {
                    val wing = Mat4.multiply(at(0.12f * side, chest - 0.02f, plan.frontZ(0.12f * side, chest - 0.02f) + 0.02f), Mat4.rotationZ(-12f * side))
                    p.part(p.sphere, wing, cloth, sc = v(0.14f, 0.085f, 0.05f), shine = 0.4f)
                }
                p.part(p.sphere, at(0f, chest - 0.02f, chestZ + 0.05f), darken(cloth), sc = v(0.05f, 0.05f, 0.04f), shine = 0.4f)
            }
            Clothes.TIE -> {
                p.part(p.sphere, at(0f, chest, chestZ + 0.02f), darken(cloth), sc = v(0.065f, 0.055f, 0.04f), shine = 0.4f)
                val y = chest - 0.24f
                p.part(p.sphere, at(0f, y, plan.frontZ(0f, y) + 0.015f), cloth, sc = v(0.08f, 0.22f, 0.04f), shine = 0.4f)
            }
            Clothes.HOODIE -> {
                val mainM = BodyPlan.frame(root, plan.main)
                p.part(p.lowerHemisphere, mainM, cloth, t = v(0f, -0.32f, 0f), sc = v(1.03f, 0.66f, 0.95f), shine = 0.05f, rim = 0.4f)
                for (side in intArrayOf(-1, 1)) {
                    val x = 0.22f * side
                    val y = -0.5f
                    val q = 1f - (x / 1.03f) * (x / 1.03f) - ((y + 0.32f) / 0.66f) * ((y + 0.32f) / 0.66f)
                    p.part(p.tube, mainM, white, t = v(x, y, 0.95f * sqrt(maxOf(0f, q)) + 0.02f), sc = v(0.022f, 0.3f, 0.022f), shine = 0.2f)
                    p.part(p.sphere, mainM, white, t = v(x, y - 0.16f, 0.95f * sqrt(maxOf(0f, q)) + 0.02f), sc = v(0.035f, 0.035f, 0.035f), shine = 0.2f)
                }
            }
            Clothes.CAPE -> {
                val bottom = plan.bottom + 0.08f
                val mid = (neck + bottom) / 2f
                val backZ = plan.backZ(0f, mid) - 0.06f
                val flutter = sin(a.time * 2.4f) * 4f + a.walk * 14f
                val m = Mat4.multiply(at(0f, neck, backZ + 0.1f), Mat4.rotationX(-8f - flutter))
                p.part(p.sphere, m, cloth, t = v(0f, (bottom - neck) / 2f, -0.08f), sc = v(nw * 1.05f, (neck - bottom) / 2f + 0.05f, 0.07f), shine = 0.35f, rim = 0.5f)
                band(neck, nw * 1.04f, maxOf(nd * 1.04f, neckZ + 0.02f), 0.7f, darken(cloth, 0.8f), 0.4f)
                p.part(p.sphere, at(0f, neck, maxOf(nd * 1.04f, neckZ + 0.02f) + 0.04f), gold, sc = v(0.07f, 0.07f, 0.04f), shine = 1f)
            }
            Clothes.TUTU -> {
                val y = plan.main.y - 0.55f * plan.main.ry
                val (w, d) = plan.cut(y)
                val frill = lighten(cloth, 0.35f)
                for (layer in 0..1) for (k in 0 until 22) {
                    val ang = 2 * PI * (k + layer * 0.5) / 22
                    val out = 0.08f + layer * 0.07f
                    val x = (cos(ang) * (w + out)).toFloat()
                    val z = (sin(ang) * (d + out)).toFloat()
                    p.part(p.sphere, at(x, y - layer * 0.05f, z), if ((k + layer) % 2 == 0) cloth else frill, sc = v(0.17f, 0.07f, 0.17f), shine = 0.2f, rim = 0.6f)
                }
                p.part(p.band, Mat4.multiply(at(0f, y + 0.07f, 0f), Mat4.rotationX(90f)), darken(cloth, 0.85f), sc = v(w * 1.02f, d * 1.02f + 0.02f, 1.5f), shine = 0.3f)
            }
            Clothes.BACKPACK -> {
                val y = plan.main.y + 0.05f * plan.main.ry
                val backZ = plan.backZ(0f, y)
                p.part(p.sphere, at(0f, y, backZ - 0.2f), cloth, sc = v(0.64f, 0.62f, 0.34f), shine = 0.3f)
                p.part(p.sphere, at(0f, y - 0.2f, backZ - 0.5f), darken(cloth, 0.85f), sc = v(0.36f, 0.24f, 0.1f), shine = 0.3f)
                p.part(p.box, at(0f, y + 0.46f, backZ - 0.14f), darken(cloth, 0.6f), sc = v(0.22f, 0.06f, 0.06f))
                for (side in intArrayOf(-1, 1)) {
                    val x = 0.62f * side * plan.main.rx
                    val sy = plan.main.y - 0.36f * plan.main.ry / 0.93f
                    val m = Mat4.multiply(at(x, sy, plan.frontZ(x, sy) + 0.02f), Mat4.rotationZ(-8f * side))
                    p.part(p.box, m, darken(cloth, 0.7f), sc = v(0.08f, 0.5f, 0.03f), shine = 0.2f)
                }
            }
            Clothes.NECKLACE -> {
                val y0 = chest + 0.02f
                for (k in 0..12) {
                    val ang = (-75.0 + k * 12.5) * PI / 180.0
                    val x = (sin(ang) * nw * 0.98).toFloat()
                    val y = y0 - (cos(ang) * 0.12).toFloat()
                    val z = plan.frontZ(x, y) + 0.015f
                    p.part(p.sphere, at(x, y, z), if (k == 6) gold else lighten(cloth, 0.2f), sc = if (k == 6) v(0.07f, 0.08f, 0.05f) else v(0.035f, 0.035f, 0.035f), shine = 1.1f)
                }
            }
            Clothes.MEDAL -> {
                val top = chest + 0.04f
                val low = chest - 0.22f
                for (side in intArrayOf(-1, 1)) {
                    val topX = 0.2f * side
                    val mx = topX / 2f
                    val my = (top + low) / 2f
                    val ang = -Math.toDegrees(atan2(topX.toDouble(), (top - low).toDouble())).toFloat()
                    val m = Mat4.multiply(at(mx, my, plan.frontZ(mx, my) + 0.015f), Mat4.rotationZ(ang))
                    p.part(p.box, m, cloth, sc = v(0.08f, sqrt(topX * topX + (top - low) * (top - low)), 0.02f), shine = 0.3f)
                }
                val disc = Mat4.multiply(at(0f, low - 0.1f, plan.frontZ(0f, low - 0.1f) + 0.04f), Mat4.rotationX(90f))
                p.part(p.cylinder, disc, gold, sc = v(0.13f, 0.03f, 0.13f), shine = 1.3f, rim = 0.7f, glow = 0.15f)
                p.part(p.sphere, at(0f, low - 0.1f, plan.frontZ(0f, low - 0.1f) + 0.07f), rgb(0xFFE9A0), sc = v(0.06f, 0.06f, 0.02f), shine = 1.2f)
            }
        }
    }
}
