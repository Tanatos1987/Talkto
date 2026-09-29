package com.talkto.app.error

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import com.talkto.app.R
import com.talkto.core.avatar.Expression
import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/** A failure, translated into something the avatar can say and show. */
data class UiNotice(
    val kind: TalktoError.Kind,
    @StringRes val message: Int,
    val expression: Expression,
    val technical: String,
)

object FriendlyErrors {
    @StringRes
    fun messageFor(kind: TalktoError.Kind): Int = when (kind) {
        TalktoError.Kind.PERMISSION_DENIED -> R.string.err_permission
        TalktoError.Kind.PROTECTED_PATH -> R.string.err_protected
        TalktoError.Kind.NOT_FOUND -> R.string.err_not_found
        TalktoError.Kind.ALREADY_EXISTS -> R.string.err_exists
        TalktoError.Kind.INVALID_INPUT -> R.string.err_input
        TalktoError.Kind.CONFIRMATION_REQUIRED -> R.string.err_confirmation
        TalktoError.Kind.NETWORK -> R.string.err_network
        TalktoError.Kind.RATE_LIMITED -> R.string.err_rate_limited
        TalktoError.Kind.API_KEY_MISSING -> R.string.err_api_key
        TalktoError.Kind.API_REJECTED -> R.string.err_api_rejected
        TalktoError.Kind.CAPABILITY_UNAVAILABLE -> R.string.err_capability
        TalktoError.Kind.STORAGE_FULL -> R.string.err_storage_full
        TalktoError.Kind.UNKNOWN -> R.string.err_unknown
    }

    fun expressionFor(kind: TalktoError.Kind): Expression = when (kind) {
        TalktoError.Kind.PROTECTED_PATH, TalktoError.Kind.PERMISSION_DENIED -> Expression.SURPRISED
        TalktoError.Kind.NETWORK, TalktoError.Kind.RATE_LIMITED, TalktoError.Kind.STORAGE_FULL -> Expression.SAD
        TalktoError.Kind.CONFIRMATION_REQUIRED -> Expression.NEUTRAL
        else -> Expression.CONFUSED
    }
}

/**
 * One place where every error ends up:
 * - [coroutineHandler] for all app-scope coroutines;
 * - [report] for caught errors (agent turns, avatar generation, UI actions);
 * - an uncaught-exception hook that keeps worker-thread failures from killing the process and leaves a
 *   marker for main-thread crashes, so the pet apologises on the next start.
 * The UI collects [notices] and lets the avatar deliver a friendly line instead of a crash dialog.
 */
class GlobalErrorHandler(private val context: Context) {

    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<UiNotice> = _notices.asSharedFlow()

    val coroutineHandler = CoroutineExceptionHandler { _, t -> report(t, "coroutine") }

    private val crashMarker get() = File(context.filesDir, "crash_marker")

    fun report(t: Throwable, where: String = "app") {
        if (t is CancellationException) return
        val err = runCatching { ErrorMapper.map(t) }.getOrElse { TalktoError.Unknown(t.message ?: "error", t) }
        Log.w(TAG, "[$where] ${err.kind}: ${err.message}", t)
        _notices.tryEmit(
            UiNotice(err.kind, FriendlyErrors.messageFor(err.kind), FriendlyErrors.expressionFor(err.kind), err.message ?: ""),
        )
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            if (thread === Looper.getMainLooper().thread) {
                // The main looper cannot be resumed after an uncaught exception; record and let the system restart us.
                runCatching { crashMarker.writeText("${System.currentTimeMillis()} ${e.javaClass.name}: ${e.message}") }
                previous?.uncaughtException(thread, e)
            } else {
                // A background worker died. Its thread is gone, the app is not.
                report(e, "thread:${thread.name}")
            }
        }
    }

    /** True once after a crash; clears the marker. */
    fun consumeCrashMarker(): Boolean = crashMarker.exists().also { if (it) crashMarker.delete() }

    private companion object {
        const val TAG = "ZnaiKo"
    }
}
