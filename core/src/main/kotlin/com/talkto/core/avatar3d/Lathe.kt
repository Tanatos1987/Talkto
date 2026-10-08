package com.talkto.core.avatar3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A smooth body of revolution for shapes that spheres cannot make without a crease: a pear, a bean.
 * At height y the cut is an ellipse of half-width [radius](y) and half-depth that times [depth], centred
 * [center](y) to the side. The same functions give the mesh and the exact surface, so faces and clothes sit on it.
 */
class Lathe(
    val top: Float,
    val bottom: Float,
    private val radius: (Float) -> Float,
    private val center: (Float) -> Float = { 0f },
    val depth: Float = 0.9f,
) {
    private val mid = (top + bottom) / 2f
    private val half = (top - bottom) / 2f

    /** Rings are closer together near the ends, where the outline turns fastest. */
    fun y(t: Float): Float = mid + half * cos(PI.toFloat() * t)

    /** Half-width, half-depth and centre of the cut at height [h], or null outside the body. */
    fun cut(h: Float): Triple<Float, Float, Float>? {
        if (h > top || h < bottom) return null
        val r = radius(h).coerceAtLeast(0f)
        return Triple(r, r * depth, center(h))
    }

    /** Depth of the front (+1) or back (-1) surface at (x, y), or null outside. */
    fun surface(x: Float, y: Float, side: Int): Float? {
        val (rx, rz, cx) = cut(y) ?: return null
        if (rx < 1e-4f) return null
        val q = 1f - ((x - cx) / rx).let { it * it }
        if (q <= 0f) return null
        return side * rz * sqrt(q)
    }

    fun mesh(stacks: Int = 44, slices: Int = 40): Mesh {
        val pos = ArrayList<Float>()
        val nor = ArrayList<Float>()
        val idx = ArrayList<Short>()
        fun point(t: Float, th: Float): FloatArray {
            val h = y(t.coerceIn(0f, 1f))
            val r = if (t <= 0f || t >= 1f) 0f else radius(h).coerceAtLeast(0f)
            return floatArrayOf(center(h) + r * cos(th), h, r * depth * sin(th))
        }
        for (i in 0..stacks) {
            val t = i.toFloat() / stacks
            for (j in 0..slices) {
                val th = 2f * PI.toFloat() * j / slices
                val p = point(t, th)
                pos += p[0]; pos += p[1]; pos += p[2]
                val n = when (i) {
                    0 -> floatArrayOf(0f, 1f, 0f)
                    stacks -> floatArrayOf(0f, -1f, 0f)
                    else -> {
                        val e = 1e-3f
                        val a = point(t + e, th)
                        val b = point(t - e, th)
                        val dt = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
                        val r = radius(y(t)).coerceAtLeast(1e-4f)
                        val dth = floatArrayOf(-r * sin(th), 0f, r * depth * cos(th))
                        // dθ × dt points outwards.
                        val nx = dth[1] * dt[2] - dth[2] * dt[1]
                        val ny = dth[2] * dt[0] - dth[0] * dt[2]
                        val nz = dth[0] * dt[1] - dth[1] * dt[0]
                        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-9f)
                        floatArrayOf(nx / l, ny / l, nz / l)
                    }
                }
                nor += n[0]; nor += n[1]; nor += n[2]
            }
        }
        for (i in 0 until stacks) for (j in 0 until slices) {
            val a = i * (slices + 1) + j
            val b = a + slices + 1
            idx += a.toShort(); idx += b.toShort(); idx += (a + 1).toShort()
            idx += (a + 1).toShort(); idx += b.toShort(); idx += (b + 1).toShort()
        }
        return Mesh(pos.toFloatArray(), nor.toFloatArray(), idx.toShortArray())
    }

    companion object {
        /** Half-width of an upright ellipse of centre [cy], half-height [ry], half-width [rx] at height [h]. */
        fun ellipse(h: Float, cy: Float, ry: Float, rx: Float): Float {
            val q = 1f - ((h - cy) / ry).let { it * it }
            return if (q <= 0f) 0f else rx * sqrt(q)
        }

        /** Smooth maximum: like max(a, b), with a rounded fillet [k] wide where they meet. */
        fun smoothMax(a: Float, b: Float, k: Float): Float {
            // Where only one part exists (the very top or bottom) it alone decides, so the tips stay pointed.
            if (a <= 0f) return b
            if (b <= 0f) return a
            val h = (0.5f + 0.5f * (a - b) / k).coerceIn(0f, 1f)
            return b + (a - b) * h + k * h * (1f - h)
        }

        /** A small round head on a big round bottom with a soft waist. Stands on y = -0.93 like every body. */
        val PEAR = Lathe(1.1f, -0.93f, radius = { h -> smoothMax(ellipse(h, 0.42f, 0.68f, 0.8f), ellipse(h, -0.27f, 0.66f, 1.06f), 0.22f) })

        /** A soft bean: two equal halves with a gentle waist; the top leans one way, the bottom the other. */
        val BEAN = Lathe(
            1.02f, -0.93f,
            radius = { h -> smoothMax(ellipse(h, 0.24f, 0.78f, 0.88f), ellipse(h, -0.25f, 0.68f, 0.9f), 0.25f) },
            center = { h -> -0.09f * (h - 0.045f) / 0.975f },
        )
    }
}
