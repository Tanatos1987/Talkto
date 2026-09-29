package com.talkto.core.error

import com.anthropic.core.JsonValue
import com.anthropic.core.http.Headers
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.UnauthorizedException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ErrorMapperTest {

    private fun body(message: String) = JsonValue.from(
        mapOf("type" to "error", "error" to mapOf("type" to "invalid_request_error", "message" to message)),
    )

    @Test fun `low credit balance is its own kind with the server's reason`() {
        val e = BadRequestException.builder().headers(Headers.builder().build())
            .body(body("Your credit balance is too low to access the Anthropic API.")).build()
        val err = ErrorMapper.map(e)
        assertThat(err.kind).isEqualTo(TalktoError.Kind.API_NO_CREDIT)
        assertThat(err.message).contains("credit balance is too low")
    }

    @Test fun `other bad requests keep the server message`() {
        val e = BadRequestException.builder().headers(Headers.builder().build()).body(body("messages.1: bad block")).build()
        val err = ErrorMapper.map(e)
        assertThat(err.kind).isEqualTo(TalktoError.Kind.API_REJECTED)
        assertThat(err.message).contains("messages.1: bad block")
    }

    @Test fun `a wrong key is not a missing key, and overload is worth a retry`() {
        val bad = UnauthorizedException.builder().headers(Headers.builder().build()).body(body("invalid x-api-key")).build()
        assertThat(ErrorMapper.map(bad).kind).isEqualTo(TalktoError.Kind.API_KEY_INVALID)
        val busy = InternalServerException.builder().statusCode(529).headers(Headers.builder().build()).body(body("Overloaded")).build()
        assertThat(ErrorMapper.map(busy).kind).isEqualTo(TalktoError.Kind.RATE_LIMITED)
    }
}
