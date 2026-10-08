package com.talkto.core.error

import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.core.jsonMapper
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import kotlin.coroutines.cancellation.CancellationException

/** Normalises any [Throwable] into a [TalktoError]. Cancellation is never swallowed. */
object ErrorMapper {

    fun map(t: Throwable): TalktoError {
        if (t is CancellationException) throw t
        return when (t) {
            is TalktoError -> t
            is AccessDeniedException -> TalktoError.PermissionDenied(t.file ?: "?", t)
            is SecurityException -> TalktoError.PermissionDenied(t.message ?: "?", t)
            is NoSuchFileException -> TalktoError.NotFound(t.file ?: "?")
            is FileNotFoundException -> TalktoError.NotFound(t.message ?: "?")
            is FileAlreadyExistsException -> TalktoError.AlreadyExists(t.file ?: "?")
            // Most specific Anthropic SDK exceptions first; the base class last.
            is UnauthorizedException -> TalktoError.ApiKeyInvalid("Claude", t)
            is RateLimitException -> TalktoError.RateLimited("Claude API rate limit", t)
            is AnthropicServiceException -> serviceError(t)
            is AnthropicIoException -> TalktoError.Network("Claude API unreachable", t)
            is UnknownHostException, is SocketTimeoutException -> TalktoError.Network(t.message ?: "network", t)
            is IOException -> if (t.message?.contains("ENOSPC") == true || t.message?.contains("No space") == true) {
                TalktoError.StorageFull(t.message ?: "disk full", t)
            } else {
                TalktoError.Unknown(t.message ?: t.javaClass.simpleName, t)
            }
            is IllegalArgumentException -> TalktoError.InvalidInput(t.message ?: "invalid input")
            else -> TalktoError.Unknown(t.message ?: t.javaClass.simpleName, t)
        }
    }
}

/**
 * Anthropic API failures, by what the user can do about them: a billing problem is not a bad request,
 * and an overloaded server is worth another try later.
 */
private fun serviceError(t: AnthropicServiceException): TalktoError {
    val detail = apiMessage(t)
    return when {
        BILLING.containsMatchIn(detail) -> TalktoError.ApiNoCredit("Claude API: $detail", t)
        t.statusCode() == 529 || t.statusCode() >= 500 -> TalktoError.RateLimited("Claude API overloaded (${t.statusCode()}): $detail", t)
        t is PermissionDeniedException -> TalktoError.ApiRejected("Claude API permission denied: $detail", t)
        t is NotFoundException -> TalktoError.ApiRejected("Claude API not found: $detail", t)
        t is BadRequestException -> TalktoError.ApiRejected("Claude API rejected the request: $detail", t)
        else -> TalktoError.ApiRejected("Claude API error ${t.statusCode()}: $detail", t)
    }
}

private val BILLING = Regex("credit balance|billing|purchase credits|spend limit|usage limit", RegexOption.IGNORE_CASE)
private val MESSAGE_FIELD = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

/** The server's own explanation ("Your credit balance is too low…"), not the whole JSON body. */
internal fun apiMessage(t: AnthropicServiceException): String {
    val raw = runCatching { jsonMapper().writeValueAsString(t.body()) }.getOrNull().orEmpty()
    val fromBody = MESSAGE_FIELD.find(raw)?.groupValues?.get(1)
    val fromText = t.message?.let { MESSAGE_FIELD.find(it)?.groupValues?.get(1) ?: it }
    return (fromBody ?: fromText ?: "status ${t.statusCode()}").replace("\\\"", "\"").take(300)
}

/** Runs [block] and converts any failure into a typed [Result] carrying a [TalktoError]. */
inline fun <T> talktoCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (t: Throwable) {
        Result.failure(ErrorMapper.map(t))
    }
