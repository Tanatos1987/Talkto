package com.talkto.app.avatar3d

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.TextureView

/**
 * A transparent OpenGL ES 2.0 view. Unlike GLSurfaceView (a separate window layer that punches a hole),
 * a TextureView is composited like any other view, so the mood background drawn by Compose shows through
 * wherever the creature is not. It drives a standard [GLSurfaceView.Renderer] on its own thread.
 */
class GLTextureView(context: Context, private val renderer: GLSurfaceView.Renderer) : TextureView(context), TextureView.SurfaceTextureListener {

    private var thread: RenderThread? = null
    @Volatile private var paused = false

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun onPause() {
        paused = true
        thread?.paused = true
    }

    fun onResume() {
        paused = false
        thread?.paused = false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        thread = RenderThread(surface, renderer, width, height).also {
            it.paused = paused
            it.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        thread?.finish()
        thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private class RenderThread(
        private val texture: SurfaceTexture,
        private val renderer: GLSurfaceView.Renderer,
        @Volatile private var width: Int,
        @Volatile private var height: Int,
    ) : Thread("TalktoGL") {

        @Volatile var paused = false
        @Volatile private var running = true
        @Volatile private var sizeChanged = true

        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun resize(w: Int, h: Int) {
            width = w; height = h; sizeChanged = true
        }

        fun finish() {
            running = false
            interrupt()
            runCatching { join(500) }
        }

        override fun run() {
            try {
                initEgl()
                renderer.onSurfaceCreated(null, null)
                while (running) {
                    if (paused) {
                        sleepQuietly(60); continue
                    }
                    val start = System.nanoTime()
                    if (sizeChanged) {
                        sizeChanged = false
                        renderer.onSurfaceChanged(null, width, height)
                    }
                    renderer.onDrawFrame(null)
                    if (!EGL14.eglSwapBuffers(display, surface)) {
                        Log.w(TAG, "eglSwapBuffers failed: 0x" + Integer.toHexString(EGL14.eglGetError()))
                        break
                    }
                    // ~60 fps without Choreographer: sleep the rest of the frame budget.
                    val spentMs = (System.nanoTime() - start) / 1_000_000
                    sleepQuietly((FRAME_MS - spentMs).coerceAtLeast(1))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "3D renderer stopped", t)
            } finally {
                releaseEgl()
            }
        }

        private fun initEgl() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No RGBA8888 + depth EGL config" }
            val config = configs[0]!!
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
            surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
        }

        private fun releaseEgl() {
            if (display == EGL14.EGL_NO_DISPLAY) return
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
        }

        private fun sleepQuietly(ms: Long) {
            try {
                sleep(ms)
            } catch (_: InterruptedException) {
                // finish() interrupts to stop promptly; the loop re-checks `running`.
            }
        }
    }

    private companion object {
        const val TAG = "Talkto3D"
        const val FRAME_MS = 16L
    }
}
