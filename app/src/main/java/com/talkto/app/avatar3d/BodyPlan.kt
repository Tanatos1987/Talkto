package com.talkto.app.avatar3d

import com.talkto.core.avatar3d.Lathe
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.look.BodyShape
import com.talkto.core.look.CreatureLook
import kotlin.math.abs
import kotlin.math.sqrt

/** One ellipsoid of the body in the creature's own space: the floor is at y = -1, the face looks towards +z. */
internal data class Blob(val x: Float, val y: Float, val z: Float, val rx: Float, val ry: Float, val rz: Float, val tilt: Float = 0f) {

    /** Half-width and half-depth of the horizontal cut at height [h], or null above or below the blob. */
    fun cut(h: Float): Pair<Float, Float>? {
        val q = 1f - ((h - y) / ry).let { it * it }
        if (q <= 0f) return null
        val k = sqrt(q)
        return rx * k to rz * k
    }

    /** Front (+1) or back (-1) surface depth at ([px], [py]), or null outside. */
    fun surface(px: Float, py: Float, side: Int): Float? {
        val q = 1f - ((px - x) / rx).let { it * it } - ((py - y) / ry).let { it * it }
        if (q <= 0f) return null
        return z + side * rz * sqrt(q)
    }
}

/**
 * The body of one [CreatureLook]: the ellipsoids it is drawn with ([parts]), or a smooth [lathe] for the pear and
 * the bean, plus the frames that carry the face ([head]) and the arms, legs, tail and wings ([main]).
 * Face and outfit coordinates were designed on the original round ZnaiKo ([REF]); [frame] maps that design space
 * onto any blob, so every shape wears the same hats and makes the same faces. Surfaces are measured on what is
 * actually drawn, so features sit on the skin whatever the shape.
 */
internal class BodyPlan(
    val parts: List<Blob>,
    val head: Blob,
    val main: Blob,
    private val belly: Boolean,
    val lathe: Lathe? = null,
    private val w: Float = 1f,
    private val h: Float = 1f,
    private val dz: Float = 1f,
) {
    private fun lx(x: Float) = x / w
    private fun ly(y: Float) = FLOOR + (y - FLOOR) / h

    val top: Float = lathe?.let { FLOOR + (it.top - FLOOR) * h } ?: parts.maxOf { it.y + it.ry }
    val bottom: Float = if (lathe != null) FLOOR else parts.minOf { it.y - it.ry }
    val halfWidth: Float = lathe?.let { l ->
        (0..40).maxOf { i -> l.cut(l.bottom + (l.top - l.bottom) * i / 40f)?.let { abs(it.third) + it.first } ?: 0f } * w
    } ?: parts.maxOf { abs(it.x) + it.rx }

    /** The drawn body's frame when it is a lathe: stretched like the blobs, feet on the floor. */
    fun latheFrame(root: FloatArray): FloatArray =
        Mat4.multiply(Mat4.multiply(Mat4.multiply(root, Mat4.translation(0f, FLOOR, 0f)), Mat4.scale(w, h, dz)), Mat4.translation(0f, -FLOOR, 0f))

    /** Where the head meets the body, as on the original round ZnaiKo (y = -0.5 there). */
    val neckY: Float = head.y - 0.54f * head.ry

    /** Half-width and half-depth of the whole body at height [h]. */
    fun cut(height: Float): Pair<Float, Float> {
        lathe?.let { l ->
            val c = l.cut(ly(height)) ?: return 0.05f to 0.05f
            return maxOf(0.05f, (abs(c.third) + c.first) * w) to maxOf(0.05f, c.second * dz)
        }
        var cw = 0.05f
        var cd = 0.05f
        for (b in parts) b.cut(height)?.let { (bw, bd) -> cw = maxOf(cw, abs(b.x) + bw); cd = maxOf(cd, bd) }
        return cw to cd
    }

    /** The front of the skin at ([x], [y]), without the belly; 0 outside the body. */
    fun bodyFront(x: Float, y: Float): Float {
        lathe?.let { l -> return (l.surface(lx(x), ly(y), 1) ?: 0f) * dz }
        var z = 0f
        for (b in parts) b.surface(x, y, 1)?.let { z = maxOf(z, it) }
        return z
    }

    /** The frontmost surface point at ([x], [y]), the belly included. */
    fun frontZ(x: Float, y: Float): Float {
        var z = bodyFront(x, y)
        if (belly) bellyBlob().surface(x, y, 1)?.let { z = maxOf(z, it) }
        return z
    }

    fun backZ(x: Float, y: Float): Float {
        lathe?.let { l -> return (l.surface(lx(x), ly(y), -1) ?: 0f) * dz }
        var z = 0f
        for (b in parts) b.surface(x, y, -1)?.let { z = minOf(z, it) }
        return z
    }

    /** The belly patch in the creature's space. */
    fun bellyBlob(): Blob {
        val sx = main.rx / REF.rx
        val sy = main.ry / REF.ry
        val sz = main.rz / REF.rz
        return Blob(main.x, main.y - 0.42f * sy, main.z + 0.62f * sz, 0.56f * sx, 0.42f * sy, 0.3f * sz)
    }

    companion object {
        /** The original round ZnaiKo: every face and outfit coordinate was designed on it. */
        val REF = Blob(0f, 0f, 0f, 1f, 0.93f, 0.9f)
        private const val FLOOR = -0.93f

        fun of(look: CreatureLook): BodyPlan {
            val l = look.clamped()
            val w = l.width
            val h = l.height
            // Stretching keeps the feet on the floor; depth follows the width a little.
            fun b(x: Float, y: Float, rx: Float, ry: Float, rz: Float, tilt: Float = 0f) =
                Blob(x * w, FLOOR + (y - FLOOR) * h, 0f, rx * w, ry * h, rz * (0.6f + 0.4f * w), tilt)
            return when (l.shape) {
                BodyShape.ROUND -> b(0f, 0f, 1f, 0.93f, 0.9f).let { BodyPlan(listOf(it), it, it, l.belly) }
                BodyShape.TALL -> b(0f, 0.15f, 0.86f, 1.08f, 0.84f).let { BodyPlan(listOf(it), it, it, l.belly) }
                BodyShape.CHUBBY -> b(0f, -0.09f, 1.14f, 0.84f, 1f).let { BodyPlan(listOf(it), it, it, l.belly) }
                // Two-part bodies are one smooth lathe; the blobs only carry the face and the limbs.
                BodyShape.PEAR -> BodyPlan(
                    emptyList(), b(0f, 0.42f, 0.8f, 0.68f, 0.72f), b(0f, -0.27f, 1.06f, 0.66f, 0.95f), l.belly,
                    Lathe.PEAR, w, h, 0.6f + 0.4f * w,
                )
                BodyShape.BEAN -> BodyPlan(
                    emptyList(), b(-0.02f, 0.24f, 0.88f, 0.78f, 0.79f), b(0.03f, -0.25f, 0.9f, 0.68f, 0.81f), l.belly,
                    Lathe.BEAN, w, h, 0.6f + 0.4f * w,
                )
            }
        }

        /** root · T(blob) · R(tilt): the blob's own frame, for drawing it with its radii as scale. */
        fun raw(root: FloatArray, b: Blob): FloatArray =
            Mat4.multiply(Mat4.multiply(root, Mat4.translation(b.x, b.y, b.z)), Mat4.rotationZ(b.tilt))

        /** Design space (the round ZnaiKo) mapped onto [b]. */
        fun frame(root: FloatArray, b: Blob): FloatArray =
            Mat4.multiply(raw(root, b), Mat4.scale(b.rx / REF.rx, b.ry / REF.ry, b.rz / REF.rz))

        /** Front depth of the round design body at (x, y): where face features sit. */
        fun refSurface(x: Float, y: Float): Float = REF.rz * sqrt(maxOf(0.02f, 1f - x * x - (y / REF.ry) * (y / REF.ry)))
    }
}
