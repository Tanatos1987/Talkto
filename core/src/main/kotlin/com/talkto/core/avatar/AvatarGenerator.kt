package com.talkto.core.avatar

import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Content-addressed store for generated avatars. */
interface AvatarCache {
    fun get(key: String): Path?
    fun put(key: String, png: ByteArray): Path
}

class FileAvatarCache(private val dir: Path) : AvatarCache {
    override fun get(key: String): Path? = dir.resolve("$key.png").takeIf { Files.isRegularFile(it) && Files.size(it) > 0 }

    override fun put(key: String, png: ByteArray): Path {
        Files.createDirectories(dir)
        val target = dir.resolve("$key.png")
        // Write-then-rename, so a crash never leaves a half-written avatar that later loads as corrupt.
        val tmp = Files.createTempFile(dir, key, ".tmp")
        Files.write(tmp, png)
        return Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

/**
 * Photo-to-avatar pipeline:
 * validate input -> cache lookup (same photo + same style is free) -> remote transform with
 * bounded exponential backoff on transient errors -> validate output -> atomic write.
 *
 * Image decoding/downscaling happens on Android before bytes reach this class, so it stays pure JVM.
 */
class AvatarGenerator(
    private val api: ImageTransformApi,
    private val cache: AvatarCache,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retry: RetryPolicy = RetryPolicy(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    data class RetryPolicy(val maxAttempts: Int = 3, val initialDelayMs: Long = 1_500, val factor: Double = 2.0)

    suspend fun generate(
        image: ByteArray,
        style: AvatarStyle,
        extraPrompt: String? = null,
        forceRegenerate: Boolean = false,
    ): GeneratedAvatar = withContext(io) {
        val mime = sniffMime(image) ?: throw TalktoError.InvalidInput("Unsupported image. Use JPEG, PNG or WEBP.")
        if (image.size > MAX_INPUT_BYTES) throw TalktoError.InvalidInput("Photo is larger than ${MAX_INPUT_BYTES / 1_048_576} MB")
        if (image.size < MIN_INPUT_BYTES) throw TalktoError.InvalidInput("Photo is too small to recognise a face")

        val prompt = buildString {
            append(style.prompt)
            extraPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let { append(", ").append(it.take(300)) }
        }
        val key = cacheKey(image, api.name, prompt)
        if (!forceRegenerate) {
            cache.get(key)?.let { return@withContext GeneratedAvatar(key, style, it.toString(), clock(), fromCache = true) }
        }

        val request = TransformRequest(image, mime, prompt, AvatarStyle.NEGATIVE_PROMPT)
        val png = withRetry { api.transform(request) }
        if (sniffMime(png) == null) throw TalktoError.ApiRejected("Avatar service returned something that is not an image")
        val path = cache.put(key, png)
        GeneratedAvatar(key, style, path.toString(), clock(), fromCache = false)
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var wait = retry.initialDelayMs
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (t: Throwable) {
                val err = ErrorMapper.map(t)
                val transient = err.kind == TalktoError.Kind.NETWORK || err.kind == TalktoError.Kind.RATE_LIMITED
                if (!transient || attempt >= retry.maxAttempts) throw err
                sleep(wait)
                wait = (wait * retry.factor).toLong()
                attempt++
            }
        }
    }

    companion object {
        const val MAX_INPUT_BYTES = 10 * 1_048_576
        const val MIN_INPUT_BYTES = 1_024

        fun cacheKey(image: ByteArray, backend: String, prompt: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(image)
            md.update(0)
            md.update(backend.toByteArray())
            md.update(0)
            md.update(prompt.toByteArray())
            return md.digest().joinToString("") { "%02x".format(it) }.take(32)
        }

        /** Magic-byte detection; never trust the file extension or the picker's MIME type. */
        fun sniffMime(b: ByteArray): String? = when {
            b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "image/jpeg"
            b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> "image/png"
            b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }
    }
}
