package com.talkto.app.background

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.talkto.app.data.prefs.PetStore
import com.talkto.core.scene.MoodScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class BackgroundConfig(
    /** Follow the pet's mood with a matching background. */
    val enabled: Boolean = true,
    /** The user's own photos per mood; empty means the animated scene. */
    val photos: Map<MoodScene, List<String>> = emptyMap(),
)

/**
 * The user's photos as mood backgrounds. Picked or auto-selected pictures are copied (downscaled to
 * [MAX_EDGE] px, metadata dropped) into app storage, so they keep working without any gallery permission
 * and are gone when the app is uninstalled. Auto-selection labels recent gallery photos on-device with
 * ML Kit; no image leaves the phone.
 */
class BackgroundLibrary(private val context: Context, private val store: PetStore) {

    val config: Flow<BackgroundConfig> = store.background

    private val dir get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    suspend fun setEnabled(enabled: Boolean) = store.saveBackground(store.background.first().copy(enabled = enabled))

    /** Adds picked photos to a mood. Returns how many were imported. */
    suspend fun addPhotos(scene: MoodScene, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val imported = uris.take(MAX_PER_SCENE).mapNotNull { uri -> runCatching { import(ImageDecoder.createSource(context.contentResolver, uri)) }.getOrNull() }
        update { photos -> photos + (scene to ((photos[scene].orEmpty() + imported).takeLast(MAX_PER_SCENE))) }
        imported.size
    }

    suspend fun removePhoto(scene: MoodScene, path: String) = withContext(Dispatchers.IO) {
        File(path).delete()
        update { photos -> photos + (scene to photos[scene].orEmpty().filterNot { it == path }) }
    }

    suspend fun clear(scene: MoodScene) = withContext(Dispatchers.IO) {
        store.background.first().photos[scene].orEmpty().forEach { File(it).delete() }
        update { it - scene }
    }

    /**
     * Looks through the most recent gallery photos, labels each on-device and fills every mood that has no
     * photos yet with the best matches. Needs READ_MEDIA_IMAGES (or All Files Access). Returns photos added.
     */
    suspend fun autoFill(scanLimit: Int = 300, perScene: Int = 3, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int = withContext(Dispatchers.IO) {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build())
        try {
            val candidates = HashMap<MoodScene, MutableList<Pair<Uri, Float>>>()
            val uris = recentImages(scanLimit)
            uris.forEachIndexed { i, uri ->
                onProgress(i + 1, uris.size)
                val thumb = runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull() ?: return@forEachIndexed
                val labels = label(labeler, thumb)
                thumb.recycle()
                MoodScene.classify(labels)?.let { (scene, score) -> candidates.getOrPut(scene) { ArrayList() } += uri to score }
            }
            val existing = store.background.first().photos
            var added = 0
            val additions = HashMap<MoodScene, List<String>>()
            candidates.forEach { (scene, list) ->
                if (existing[scene].orEmpty().isNotEmpty()) return@forEach
                val files = list.sortedByDescending { it.second }.take(perScene)
                    .mapNotNull { (uri, _) -> runCatching { import(ImageDecoder.createSource(context.contentResolver, uri)) }.getOrNull() }
                if (files.isNotEmpty()) {
                    additions[scene] = files; added += files.size
                }
            }
            update { it + additions }
            added
        } finally {
            labeler.close()
        }
    }

    private fun recentImages(limit: Int): List<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<Uri>()
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Images.Media._ID), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && out.size < limit) out += ContentUris.withAppendedId(collection, c.getLong(id))
        }
        return out
    }

    private suspend fun label(labeler: com.google.mlkit.vision.label.ImageLabeler, bitmap: Bitmap): Map<String, Float> =
        suspendCancellableCoroutine { cont ->
            labeler.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { labels -> cont.resume(labels.associate { it.text to it.confidence }) }
                .addOnFailureListener { cont.resume(emptyMap()) }
        }

    private fun import(source: ImageDecoder.Source): String {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = MAX_EDGE.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val file = File(dir, "bg_${System.currentTimeMillis()}_${(Math.random() * 1e6).toInt()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private suspend fun update(f: (Map<MoodScene, List<String>>) -> Map<MoodScene, List<String>>) {
        val current = store.background.first()
        store.saveBackground(current.copy(photos = f(current.photos).filterValues { it.isNotEmpty() }))
    }

    companion object {
        const val MAX_EDGE = 1440
        const val MAX_PER_SCENE = 8
    }
}
