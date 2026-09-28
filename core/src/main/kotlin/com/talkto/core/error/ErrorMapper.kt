package com.talkto.core.error

import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
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
            is UnauthorizedException -> TalktoError.ApiKeyMissing("Claude")
            is PermissionDeniedException -> TalktoError.ApiRejected("Claude API: permission denied", t)
            is RateLimitException -> TalktoError.RateLimited("Claude API rate limit", t)
            is BadRequestException -> TalktoError.ApiRejected("Claude API rejected the request: ${t.message}", t)
            is AnthropicServiceException -> TalktoError.ApiRejected("Claude API error ${t.statusCode()}", t)
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

/** Runs [block] and converts any failure into a typed [Result] carrying a [TalktoError]. */
inline fun <T> talktoCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (t: Throwable) {
        Result.failure(ErrorMapper.map(t))
    }
