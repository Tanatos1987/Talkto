package com.talkto.app.avatar3d

import android.opengl.GLES20
import com.talkto.core.avatar3d.Lathe
import com.talkto.core.avatar3d.Mat4
import com.talkto.core.avatar3d.Mesh
import com.talkto.core.avatar3d.Primitives
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI

internal class GpuMesh(val vbo: Int, val nbo: Int, val ibo: Int, val count: Int)

/**
 * The shared drawing kit of the 3D scene: one shader, a handful of meshes and [part], which draws a mesh with
 * model = parent · T(t) · S(sc). The creature, its clothes and its house are all built from these.
 * Everything here runs on the GL thread.
 */
internal class Painter {

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
    private var uGlow = 0

    lateinit var sphere: GpuMesh
    lateinit var hemisphere: GpuMesh
    lateinit var lowerHemisphere: GpuMesh
    lateinit var cylinder: GpuMesh
    lateinit var tube: GpuMesh
    lateinit var cone: GpuMesh
    lateinit var ring: GpuMesh
    lateinit var smile: GpuMesh
    lateinit var arc: GpuMesh
    lateinit var band: GpuMesh
    lateinit var stripe: GpuMesh
    lateinit var box: GpuMesh
    lateinit var pyramid: GpuMesh
    lateinit var pear: GpuMesh
    lateinit var bean: GpuMesh

    var viewProj: FloatArray = Mat4.identity()
    val eye = floatArrayOf(0f, 0.25f, 5.4f)

    fun create() {
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
        uGlow = GLES20.glGetUniformLocation(program, "uGlow")

        sphere = upload(Primitives.sphere(28, 36))
        hemisphere = upload(Primitives.sphere(14, 36, 0.0, PI / 2))
        lowerHemisphere = upload(Primitives.sphere(14, 36, PI / 2, PI))
        cylinder = upload(Primitives.cylinder(36, capped = true))
        tube = upload(Primitives.cylinder(24, capped = false))
        cone = upload(Primitives.cone(36))
        ring = upload(Primitives.torus(0.14f, 40, 12))
        smile = upload(Primitives.torus(0.22f, 24, 10, sweep = PI, start = PI))
        arc = upload(Primitives.torus(0.05f, 40, 8, sweep = PI, start = 0.0))
        band = upload(Primitives.torus(0.035f, 48, 8))
        // The middle two thirds of the top half: a stripe that ends before the silhouette.
        stripe = upload(Primitives.torus(0.045f, 32, 8, sweep = PI * 2 / 3, start = PI / 6))
        box = upload(Primitives.box())
        pyramid = upload(Primitives.cone(4))
        pear = upload(Lathe.PEAR.mesh())
        bean = upload(Lathe.BEAN.mesh())
    }

    fun begin() {
        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uEye, 1, eye, 0)
    }

    /** Draws [mesh] with model = parent · T(t) · S(sc). [glow] 1 makes it shine by itself (lamps, windows, halos). */
    fun part(
        mesh: GpuMesh, parent: FloatArray, color: FloatArray,
        t: FloatArray = ZERO3, sc: FloatArray = ONE3, shine: Float = 0.5f, rim: Float = 0.35f, alpha: Float = 1f, glow: Float = 0f,
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
        GLES20.glUniform1f(uGlow, glow)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.nbo)
        GLES20.glEnableVertexAttribArray(aNor)
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.count, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    /** Transparent parts (shadows, glows, smoke): blended, and they do not hide what is drawn after them. */
    inline fun blended(block: () -> Unit) {
        GLES20.glEnable(GLES20.GL_BLEND)
        // Separate alpha blend keeps the result visible when the view itself is transparent (TextureView).
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        block()
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

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

    companion object {
        val ZERO3 = floatArrayOf(0f, 0f, 0f)
        val ONE3 = floatArrayOf(1f, 1f, 1f)

        fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
        fun argb(c: Long) = rgb((c and 0xFFFFFF).toInt())
        fun darken(c: FloatArray, f: Float = 0.72f) = floatArrayOf(c[0] * f, c[1] * f, c[2] * f)
        fun lighten(c: FloatArray, f: Float = 0.4f) = floatArrayOf(c[0] + (1 - c[0]) * f, c[1] + (1 - c[1]) * f, c[2] + (1 - c[2]) * f)
        fun luminance(c: FloatArray) = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]

        fun v(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        fun at(parent: FloatArray, x: Float, y: Float, z: Float) = Mat4.multiply(parent, Mat4.translation(x, y, z))

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
            uniform float uGlow;
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
                c = mix(c, uColor + vec3(fres * 0.3), uGlow);
                gl_FragColor = vec4(c, uAlpha);
            }
        """
    }
}
