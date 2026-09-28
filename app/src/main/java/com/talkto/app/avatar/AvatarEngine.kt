package com.talkto.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import androidx.core.content.FileProvider
import com.talkto.app.data.prefs.CurrentAvatar
import com.talkto.app.data.prefs.PetStore
import com.talkto.core.agent.AvatarActions
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarGenerator
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.FaceAnchors
import com.talkto.core.avatar.GeneratedAvatar
import com.talkto.core.avatar.Gesture
import com.talkto.core.avatar.Viseme
import com.talkto.core.error.TalktoError
import com.talkto.core.files.PathGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything the stage needs to draw one frame of the live portrait. */
data class AvatarPose(
    val expression: Expression = Expression.NEUTRAL,
    val gesture: Gesture = Gesture.NONE,
    /** Increments on every gesture so the same gesture twice still restarts the animation. */
    val gestureId: Long = 0,
    val viseme: Viseme = Viseme.REST,
    val speaking: Boolean = false,
)

data class AvatarVisual(
    /** Null = draw the built-in Talkto creature. */
    val bitmap: Bitmap? = null,
    val anchors: FaceAnchors = DEFAULT_CREATURE_ANCHORS,
    val style: AvatarStyle? = null,
    val generating: Boolean = false,
)

val DEFAULT_CREATURE_ANCHORS = FaceAnchors(
    leftEyeX = 0.39f, leftEyeY = 0.47f, rightEyeX = 0.61f, rightEyeY = 0.47f,
    mouthX = 0.5f, mouthY = 0.62f,
    faceLeft = 0.2f, faceTop = 0.22f, faceRight = 0.8f, faceBottom = 0.86f, detected = true,
)

/**
 * Avatar Engine: upload -> generate -> animate.
 *
 * 1. Upload: [setPendingPhoto] receives a Photo Picker / camera URI. The photo is decoded with
 *    ImageDecoder (EXIF rotation applied), downscaled to [MAX_EDGE] px and re-encoded as JPEG,
 *    which also strips location metadata before anything leaves the device.
 * 2. Generate: [AvatarGenerator] (core) restyles it through the configured image API, with caching and retries.
 *    ML Kit then locates eyes and mouth on the result.
 * 3. Animate: [pose] merges expression/gesture commands with live visemes from [SpeechEngine]. The
 *    Compose stage turns pose + anchors into breathing, blinking, lip-sync and gestures.
 *    For rigged models, [Live2DParameters.from] maps the same pose to standard Cubism parameters.
 */
class AvatarEngine(
    private val context: Context,
    private val generator: AvatarGenerator,
    private val detector: FaceAnchorDetector,
    private val speech: SpeechEngine,
    private val store: PetStore,
    private val guard: PathGuard,
    private val scope: CoroutineScope,
) : AvatarActions {

    private val _visual = MutableStateFlow(AvatarVisual())
    val visual: StateFlow<AvatarVisual> = _visual.asStateFlow()

    private val _pendingPhoto = MutableStateFlow<Uri?>(null)
    val pendingPhoto: StateFlow<Uri?> = _pendingPhoto.asStateFlow()

    private val command = MutableStateFlow(AvatarPose())
    private var moodExpression = Expression.NEUTRAL
    private var holdJob: Job? = null

    val pose: StateFlow<AvatarPose> = combine(command, speech.viseme, speech.speaking) { c, v, s ->
        c.copy(viseme = v, speaking = s)
    }.stateIn(scope, SharingStarted.Eagerly, AvatarPose())

    fun restore() {
        scope.launch {
            val saved = store.avatar.first() ?: return@launch
            val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(saved.imagePath) }
            if (bmp == null) {
                store.saveAvatar(null); return@launch
            }
            _visual.value = AvatarVisual(bmp, saved.anchors, saved.style)
        }
    }

    // ------------------------------------------------------------------ upload

    @Volatile private var pendingJpeg: ByteArray? = null

    /**
     * Stores the picked photo. It is decoded right away: Photo Picker grants are tied to the
     * process, and the agent may use the photo later from the foreground service.
     */
    fun setPendingPhoto(uri: Uri?) {
        _pendingPhoto.value = uri
        pendingJpeg = null
        if (uri == null) return
        scope.launch(Dispatchers.IO) {
            pendingJpeg = runCatching { normalise(ImageDecoder.createSource(context.contentResolver, uri)) }.getOrNull()
        }
    }

    /** A content:// URI the camera app can write the new photo into. */
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    override fun hasPendingPhoto(): Boolean = _pendingPhoto.value != null

    // ---------------------------------------------------------------- generate

    override suspend fun generateFromPendingPhoto(style: AvatarStyle, extraPrompt: String?): GeneratedAvatar {
        val uri = _pendingPhoto.value ?: throw TalktoError.InvalidInput("No photo selected")
        val bytes = pendingJpeg ?: withContext(Dispatchers.IO) { normalise(ImageDecoder.createSource(context.contentResolver, uri)) }
        return generate(bytes, style, extraPrompt).also { setPendingPhoto(null) }
    }

    override suspend fun generateFromFile(path: String, style: AvatarStyle, extraPrompt: String?): GeneratedAvatar {
        val file = guard.resolve(path)
        if (!Files.isRegularFile(file)) throw TalktoError.NotFound(file.toString())
        val bytes = withContext(Dispatchers.IO) { normalise(ImageDecoder.createSource(file.toFile())) }
        return generate(bytes, style, extraPrompt)
    }

    fun resetToCreature() {
        scope.launch {
            store.saveAvatar(null)
            _visual.value = AvatarVisual()
        }
    }

    private suspend fun generate(jpeg: ByteArray, style: AvatarStyle, extraPrompt: String?): GeneratedAvatar {
        _visual.update { it.copy(generating = true) }
        play(AnimationCommand(Expression.THINKING, Gesture.NONE, holdMs = 60_000))
        try {
            val result = generator.generate(jpeg, style, extraPrompt)
            val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(result.imagePath) }
                ?: throw TalktoError.ApiRejected("Generated avatar could not be decoded")
            val anchors = detector.detect(bmp)
            store.saveAvatar(CurrentAvatar(result.imagePath, style, anchors))
            _visual.value = AvatarVisual(bmp, anchors, style, generating = false)
            play(AnimationCommand(Expression.HAPPY, Gesture.SPIN, holdMs = 2_000))
            return result
        } catch (t: Throwable) {
            _visual.update { it.copy(generating = false) }
            play(AnimationCommand(Expression.SAD, Gesture.SHAKE))
            throw t
        }
    }

    /** Decode with EXIF rotation, cap the long edge, re-encode as JPEG (drops all metadata). */
    private fun normalise(source: ImageDecoder.Source): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val s: Size = info.size
            val scale = MAX_EDGE.toFloat() / max(s.width, s.height)
            if (scale < 1f) decoder.setTargetSize((s.width * scale).roundToInt(), (s.height * scale).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    // ----------------------------------------------------------------- animate

    override suspend fun animate(command: AnimationCommand) {
        play(command)
        command.speech?.takeIf { it.isNotBlank() }?.let { speak(it, voice = true) }
    }

    /** Idle expression follows the pet's mood whenever no explicit command is active. */
    fun setMood(expression: Expression) {
        moodExpression = expression
        if (holdJob?.isActive != true) command.update { it.copy(expression = expression) }
    }

    fun play(cmd: AnimationCommand) {
        holdJob?.cancel()
        command.update { it.copy(expression = cmd.expression, gesture = cmd.gesture, gestureId = it.gestureId + 1) }
        holdJob = scope.launch {
            delay(cmd.holdMs)
            command.update { it.copy(expression = moodExpression, gesture = Gesture.NONE) }
        }
    }

    suspend fun speak(text: String, voice: Boolean) {
        if (voice) speech.speak(text) else speech.mimeSilently(text)
    }

    fun stopSpeaking() = speech.stop()

    companion object {
        const val MAX_EDGE = 1024
    }
}

/**
 * Pose -> Live2D Cubism standard parameter IDs. Feed these to `CubismModel.setParameterValue`
 * each frame when a rigged `.moc3` model replaces the procedural portrait.
 */
object Live2DParameters {
    fun from(pose: AvatarPose, blink: Float, breath: Float, gestureProgress: Float): Map<String, Float> {
        val eyeOpen = when (pose.expression) {
            Expression.SLEEPY -> 0.35f
            Expression.SURPRISED -> 1.2f
            Expression.HAPPY, Expression.LOVE -> 0.8f
            else -> 1f
        } * (1f - blink)
        val mouthForm = when (pose.expression) {
            Expression.HAPPY, Expression.LOVE -> 1f
            Expression.SAD, Expression.ANGRY -> -0.8f
            else -> 0f
        }
        val wave = kotlin.math.sin(gestureProgress * Math.PI * 4).toFloat()
        return mapOf(
            "ParamEyeLOpen" to eyeOpen,
            "ParamEyeROpen" to eyeOpen,
            "ParamMouthOpenY" to pose.viseme.openness,
            "ParamMouthForm" to if (pose.speaking) (pose.viseme.width - 0.5f) * 2f else mouthForm,
            "ParamBreath" to breath,
            "ParamAngleX" to if (pose.gesture == Gesture.SHAKE) wave * 25f else 0f,
            "ParamAngleY" to if (pose.gesture == Gesture.NOD) wave * 20f else 0f,
            "ParamBodyAngleZ" to if (pose.gesture == Gesture.WAVE) wave * 8f else 0f,
            "ParamCheek" to if (pose.expression == Expression.HAPPY || pose.expression == Expression.LOVE) 1f else 0f,
        )
    }
}
