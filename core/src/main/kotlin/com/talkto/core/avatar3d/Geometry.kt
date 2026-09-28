package com.talkto.core.avatar3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Interleaved-free mesh: xyz positions, xyz normals, 16-bit triangle indices. Ready for GLES 2 VBOs. */
class Mesh(val positions: FloatArray, val normals: FloatArray, val indices: ShortArray) {
    val vertexCount: Int get() = positions.size / 3
    val indexCount: Int get() = indices.size

    init {
        require(positions.size == normals.size) { "positions/normals size mismatch" }
        require(vertexCount <= 65_535) { "Too many vertices for 16-bit indices" }
    }
}

/** Procedural primitives. Everything the creature, its face and its outfit are made of. */
object Primitives {

    /**
     * UV sphere of radius 1. [latFrom]..[latTo] in radians from the north pole (0..PI) allows caps,
     * e.g. a lower hemisphere for the egg shell.
     */
    fun sphere(stacks: Int = 24, slices: Int = 32, latFrom: Double = 0.0, latTo: Double = PI): Mesh {
        val pos = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (i in 0..stacks) {
            val phi = latFrom + (latTo - latFrom) * i / stacks
            for (j in 0..slices) {
                val theta = 2 * PI * j / slices
                val x = (sin(phi) * cos(theta)).toFloat()
                val y = cos(phi).toFloat()
                val z = (sin(phi) * sin(theta)).toFloat()
                pos += x; pos += y; pos += z
            }
        }
        for (i in 0 until stacks) for (j in 0 until slices) {
            val a = i * (slices + 1) + j
            val b = a + slices + 1
            idx += a.toShort(); idx += b.toShort(); idx += (a + 1).toShort()
            idx += (a + 1).toShort(); idx += b.toShort(); idx += (b + 1).toShort()
        }
        val p = pos.toFloatArray()
        return Mesh(p, p.copyOf(), idx.toShortArray()) // unit sphere: normal == position
    }

    /** Open cylinder (no caps) of radius 1, from y = -0.5 to y = 0.5, plus a top cap when [capped]. */
    fun cylinder(slices: Int = 32, capped: Boolean = true): Mesh {
        val pos = ArrayList<Float>()
        val nor = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (j in 0..slices) {
            val t = 2 * PI * j / slices
            val x = cos(t).toFloat()
            val z = sin(t).toFloat()
            pos += listOf(x, -0.5f, z, x, 0.5f, z)
            nor += listOf(x, 0f, z, x, 0f, z)
        }
        for (j in 0 until slices) {
            val a = (j * 2).toShort()
            val b = (j * 2 + 1).toShort()
            val c = (j * 2 + 2).toShort()
            val d = (j * 2 + 3).toShort()
            idx += listOf(a, b, c, c, b, d)
        }
        if (capped) {
            val center = (pos.size / 3).toShort()
            pos += listOf(0f, 0.5f, 0f); nor += listOf(0f, 1f, 0f)
            val ring = pos.size / 3
            for (j in 0..slices) {
                val t = 2 * PI * j / slices
                pos += listOf(cos(t).toFloat(), 0.5f, sin(t).toFloat()); nor += listOf(0f, 1f, 0f)
            }
            for (j in 0 until slices) idx += listOf(center, (ring + j + 1).toShort(), (ring + j).toShort())
        }
        return Mesh(pos.toFloatArray(), nor.toFloatArray(), idx.toShortArray())
    }

    /** Cone with base radius 1 at y = 0 and apex at y = 1. */
    fun cone(slices: Int = 32): Mesh {
        val pos = ArrayList<Float>()
        val nor = ArrayList<Float>()
        val idx = ArrayList<Short>()
        val ny = (1 / sqrt(2.0)).toFloat()
        for (j in 0..slices) {
            val t = 2 * PI * j / slices
            val x = cos(t).toFloat()
            val z = sin(t).toFloat()
            pos += listOf(x, 0f, z, 0f, 1f, 0f)
            nor += listOf(x * ny, ny, z * ny, x * ny, ny, z * ny)
        }
        for (j in 0 until slices) {
            val base = (j * 2).toShort()
            val tip = (j * 2 + 1).toShort()
            val next = (j * 2 + 2).toShort()
            idx += listOf(base, tip, next)
        }
        return Mesh(pos.toFloatArray(), nor.toFloatArray(), idx.toShortArray())
    }

    /**
     * Torus in the XY plane (facing +Z), ring radius 1, tube radius [tube]. Used for glasses and scarves.
     * [sweep] < 2π gives an arc starting at [start]: the bottom half (start = π) is a smile, the top half a frown.
     */
    fun torus(tube: Float = 0.12f, rings: Int = 32, sides: Int = 12, sweep: Double = 2 * PI, start: Double = 0.0): Mesh {
        val pos = ArrayList<Float>()
        val nor = ArrayList<Float>()
        val idx = ArrayList<Short>()
        for (i in 0..rings) {
            val u = start + sweep * i / rings
            val cu = cos(u).toFloat()
            val su = sin(u).toFloat()
            for (j in 0..sides) {
                val v = 2 * PI * j / sides
                val cv = cos(v).toFloat()
                val sv = sin(v).toFloat()
                pos += listOf((1 + tube * cv) * cu, (1 + tube * cv) * su, tube * sv)
                nor += listOf(cv * cu, cv * su, sv)
            }
        }
        for (i in 0 until rings) for (j in 0 until sides) {
            val a = i * (sides + 1) + j
            val b = a + sides + 1
            idx += listOf(a, b, a + 1, a + 1, b, b + 1).map { it.toShort() }
        }
        return Mesh(pos.toFloatArray(), nor.toFloatArray(), idx.toShortArray())
    }
}

/** Column-major 4x4 matrices, the layout OpenGL expects. Small, allocation-light, testable on the JVM. */
object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[c * 4 + k]
            r[c * 4 + row] = s
        }
        return r
    }

    fun translation(x: Float, y: Float, z: Float) = identity().also { it[12] = x; it[13] = y; it[14] = z }

    fun scale(x: Float, y: Float, z: Float) = identity().also { it[0] = x; it[5] = y; it[10] = z }

    fun rotationX(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return identity().also { it[5] = c; it[6] = s; it[9] = -s; it[10] = c }
    }

    fun rotationY(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return identity().also { it[0] = c; it[2] = -s; it[8] = s; it[10] = c }
    }

    fun rotationZ(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return identity().also { it[0] = c; it[1] = s; it[4] = -s; it[5] = c }
    }

    fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = (1.0 / tan(Math.toRadians(fovYDeg / 2.0))).toFloat()
        return FloatArray(16).also {
            it[0] = f / aspect; it[5] = f
            it[10] = (far + near) / (near - far); it[11] = -1f
            it[14] = 2 * far * near / (near - far)
        }
    }

    fun lookAt(ex: Float, ey: Float, ez: Float, cx: Float, cy: Float, cz: Float): FloatArray {
        var fx = cx - ex; var fy = cy - ey; var fz = cz - ez
        val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
        // side = f x up(0,1,0)
        var sx = -fz; var sz = fx; val sy = 0f
        val sl = sqrt(sx * sx + sz * sz); sx /= sl; sz /= sl
        val ux = sy * fz - sz * fy; val uy = sz * fx - sx * fz; val uz = sx * fy - sy * fx
        return floatArrayOf(
            sx, ux, -fx, 0f,
            sy, uy, -fy, 0f,
            sz, uz, -fz, 0f,
            -(sx * ex + sy * ey + sz * ez), -(ux * ex + uy * ey + uz * ez), fx * ex + fy * ey + fz * ez, 1f,
        )
    }

    /** Applies [m] to point (x, y, z, 1). */
    fun transform(m: FloatArray, x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        m[0] * x + m[4] * y + m[8] * z + m[12],
        m[1] * x + m[5] * y + m[9] * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14],
        m[3] * x + m[7] * y + m[11] * z + m[15],
    )

    /** Upper-left 3x3 inverse-transpose for normals, returned as a 9-float column-major matrix. */
    fun normalMatrix(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[4]; val c = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val h = m[6]; val i = m[10]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val inv = if (det == 0f) 0f else 1f / det
        // inverse-transpose == cofactor matrix / det; listed column by column
        return floatArrayOf(
            (e * i - f * h) * inv, -(b * i - c * h) * inv, (b * f - c * e) * inv,
            -(d * i - f * g) * inv, (a * i - c * g) * inv, -(a * f - c * d) * inv,
            (d * h - e * g) * inv, -(a * h - b * g) * inv, (a * e - b * d) * inv,
        )
    }
}

/**
 * Critically-damped-ish spring used for every "physical" reaction: the head recoiling from a slap,
 * squash after a hit, wobble after a pat. Frame-rate independent (semi-implicit Euler, sub-stepped).
 */
class Spring(var value: Float = 0f, private val stiffness: Float = 120f, private val damping: Float = 12f) {
    var velocity = 0f
        private set
    var target = 0f

    fun kick(impulse: Float) {
        velocity += impulse
    }

    fun step(dtSeconds: Float): Float {
        var left = dtSeconds.coerceIn(0f, 0.1f)
        while (left > 0f) {
            val h = minOf(left, 1f / 240f)
            val accel = -stiffness * (value - target) - damping * velocity
            velocity += accel * h
            value += velocity * h
            left -= h
        }
        return value
    }

    val settled: Boolean get() = kotlin.math.abs(value - target) < 1e-3f && kotlin.math.abs(velocity) < 1e-3f
}

/** Smooth 0..1 easing, and exponential approach for following a target without springs. */
object Ease {
    fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    fun approach(current: Float, target: Float, rate: Float, dt: Float): Float = target + (current - target) * exp(-rate * dt)
}
