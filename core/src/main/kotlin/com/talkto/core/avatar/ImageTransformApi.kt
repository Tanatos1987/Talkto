package com.talkto.core.avatar

import com.talkto.core.error.TalktoError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Image-to-image backend. Swap implementations (Stability, a self-hosted ComfyUI endpoint,
 * a specialised avatar vendor) without touching [AvatarGenerator].
 */
interface ImageTransformApi {
    val name: String

    suspend fun transform(request: TransformRequest): ByteArray
}

data class TransformRequest(
    val image: ByteArray,
    val mimeType: String,
    val prompt: String,
    val negativePrompt: String,
    /** How strictly the output follows the input's structure (face shape, pose). 0..1. */
    val structureStrength: Float = 0.7f,
    val seed: Long? = null,
) {
    override fun equals(other: Any?) = other is TransformRequest && other.prompt == prompt && other.image.contentEquals(image)
    override fun hashCode() = 31 * prompt.hashCode() + image.contentHashCode()
}

/**
 * Stability AI "Stable Image - Control / Structure" endpoint: keeps the face geometry of the photo
 * and restyles it according to the prompt, which is what an avatar needs.
 * Docs: https://platform.stability.ai/docs/api-reference
 */
class StabilityImageApi(
    private val apiKey: () -> String?,
    private val http: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.stability.ai",
    private val endpoint: String = "/v2beta/stable-image/control/structure",
) : ImageTransformApi {

    override val name = "stability"

    override suspend fun transform(request: TransformRequest): ByteArray {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: throw TalktoError.ApiKeyMissing("Stability AI")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", "input", request.image.toRequestBody(request.mimeType.toMediaType()))
            .addFormDataPart("prompt", request.prompt)
            .addFormDataPart("negative_prompt", request.negativePrompt)
            .addFormDataPart("control_strength", String.format(Locale.ROOT, "%.2f", request.structureStrength.coerceIn(0f, 1f)))
            .addFormDataPart("output_format", "png")
            .apply { request.seed?.let { addFormDataPart("seed", it.toString()) } }
            .build()
        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + endpoint)
            .header("Authorization", "Bearer $key")
            .header("Accept", "image/*")
            .post(body)
            .build()

        return http.newCall(httpRequest).await().use { response ->
            val bytes = response.body.bytes()
            when {
                response.isSuccessful -> bytes
                else -> throw mapHttpError(response.code, bytes.decodeToString())
            }
        }
    }

    private fun mapHttpError(code: Int, body: String): TalktoError {
        val detail = runCatching {
            Json.parseToJsonElement(body).jsonObject["errors"]?.toString()
                ?: Json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull() ?: body.take(300)
        return when (code) {
            401, 403 -> TalktoError.ApiKeyMissing("Stability AI")
            413 -> TalktoError.InvalidInput("Photo is too large for the avatar service")
            400, 422 -> TalktoError.ApiRejected("Avatar service rejected the photo: $detail")
            429 -> TalktoError.RateLimited("Avatar service rate limit")
            in 500..599 -> TalktoError.Network("Avatar service unavailable ($code)")
            else -> TalktoError.ApiRejected("Avatar service error $code: $detail")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** Cancellation-aware bridge from OkHttp's callback API to coroutines. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, r, _ -> r.close() }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(TalktoError.Network(e.message ?: "network error", e))
        }
    })
}
