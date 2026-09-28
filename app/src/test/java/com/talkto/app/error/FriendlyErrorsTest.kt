package com.talkto.app.error

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.talkto.app.R
import com.talkto.core.avatar.Expression
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.AccessDeniedException

@RunWith(RobolectricTestRunner::class)
class FriendlyErrorsTest {

    @Test fun `every error kind has a friendly line`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        TalktoError.Kind.entries.forEach { kind ->
            assertThat(ctx.getString(FriendlyErrors.messageFor(kind))).isNotEmpty()
        }
    }

    @Test fun `permission error becomes the folder message, not a crash`() = runTest(UnconfinedTestDispatcher()) {
        val handler = GlobalErrorHandler(ApplicationProvider.getApplicationContext())
        val notice = async { handler.notices.first() }
        handler.report(AccessDeniedException("/storage/emulated/0/Android/data"))
        val n = notice.await()
        assertThat(n.kind).isEqualTo(TalktoError.Kind.PERMISSION_DENIED)
        assertThat(n.message).isEqualTo(R.string.err_permission)
        assertThat(n.expression).isEqualTo(Expression.SURPRISED)
    }

    @Test fun `cancellation is not reported`() = runTest(UnconfinedTestDispatcher()) {
        val handler = GlobalErrorHandler(ApplicationProvider.getApplicationContext())
        val seen = mutableListOf<UiNotice>()
        val job = launch { handler.notices.collect { seen += it } }
        handler.report(kotlinx.coroutines.CancellationException("bye"))
        job.cancel()
        assertThat(seen).isEmpty()
    }

    @Test fun `crash marker is consumed once`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(ctx.filesDir, "crash_marker").writeText("x")
        val handler = GlobalErrorHandler(ctx)
        assertThat(handler.consumeCrashMarker()).isTrue()
        assertThat(handler.consumeCrashMarker()).isFalse()
    }
}
