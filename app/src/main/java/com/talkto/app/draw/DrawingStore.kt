package com.talkto.app.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.talkto.core.draw.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A drawing in the gallery: the PNG [file] in the app's own storage, its [title] and when it was made. */
@Serializable
data class SavedDrawing(val file: String, val title: String = "", val atMs: Long = 0)

/**
 * The child's drawings, kept only in the app's private storage (no permission, not in the cloud backup, never sent
 * anywhere). The newest [MAX] are kept.
 */
class DrawingStore(context: Context) {

    private val dir = File(context.filesDir, "drawings")
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _drawings = MutableStateFlow<List<SavedDrawing>>(emptyList())
    /** Newest first. */
    val drawings: StateFlow<List<SavedDrawing>> = _drawings.asStateFlow()

    suspend fun load() = lock.withLock {
        _drawings.value = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(LIST, index.readText()) }.getOrDefault(emptyList())
                .filter { File(dir, it.file).exists() }
        }
    }

    fun file(d: SavedDrawing): File = File(dir, d.file)

    /** Draws [strokes] on a white page of [width] x [height] pixels and keeps it. */
    suspend fun save(strokes: List<Stroke>, width: Int, height: Int, title: String): SavedDrawing = lock.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val now = System.currentTimeMillis()
            val d = SavedDrawing("drawing_$now.png", title.trim().take(TITLE_MAX), now)
            val bitmap = render(strokes, width, height)
            File(dir, d.file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val all = listOf(d) + _drawings.value
            all.drop(MAX).forEach { File(dir, it.file).delete() }
            write(all.take(MAX))
            d
        }
    }

    suspend fun delete(d: SavedDrawing) = lock.withLock {
        withContext(Dispatchers.IO) {
            File(dir, d.file).delete()
            write(_drawings.value.filter { it.file != d.file })
        }
    }

    /** A small copy for the gallery grid. */
    suspend fun thumbnail(d: SavedDrawing, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(d)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun write(list: List<SavedDrawing>) {
        index.writeText(json.encodeToString(LIST, list))
        _drawings.value = list
    }

    companion object {
        const val MAX = 100
        const val TITLE_MAX = 40
        private val LIST = kotlinx.serialization.builtins.ListSerializer(SavedDrawing.serializer())

        /** The strokes on a white page; the eraser paints white. */
        fun render(strokes: List<Stroke>, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            strokes.forEach { s ->
                if (s.points.isEmpty()) return@forEach
                paint.color = s.crayon?.argb?.toInt() ?: android.graphics.Color.WHITE
                paint.strokeWidth = s.width * width
                val path = Path()
                path.moveTo(s.points[0].x * width, s.points[0].y * height)
                if (s.points.size == 1) path.lineTo(s.points[0].x * width + 0.1f, s.points[0].y * height)
                s.points.drop(1).forEach { p -> path.lineTo(p.x * width, p.y * height) }
                canvas.drawPath(path, paint)
            }
            return bitmap
        }
    }
}
