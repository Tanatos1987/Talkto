package com.talkto.core.avatar

import com.google.common.truth.Truth.assertThat
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Paths

class AvatarGeneratorTest {

    @get:Rule val tmp = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(4_096) { 7 }

    private class FakeApi(var responses: MutableList<() -> ByteArray>) : ImageTransformApi {
        override val name = "fake"
        val requests = mutableListOf<TransformRequest>()
        override suspend fun transform(request: TransformRequest): ByteArray {
            requests += request
            return responses.removeAt(0).invoke()
        }
    }

    private fun generator(api: ImageTransformApi, sleeps: MutableList<Long> = mutableListOf(), dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        AvatarGenerator(
            api = api,
            cache = FileAvatarCache(tmp.root.toPath().resolve("avatars")),
            io = dispatcher,
            clock = { 42L },
            sleep = { sleeps += it },
        )

    @Test fun `generates, stores and returns a png`() = runTest {
        val api = FakeApi(mutableListOf({ png }))
        val avatar = generator(api, dispatcher = StandardTestDispatcher(testScheduler)).generate(jpeg, AvatarStyle.ANIME_2D)
        assertThat(avatar.fromCache).isFalse()
        assertThat(Files.readAllBytes(Paths.get(avatar.imagePath))).isEqualTo(png)
        assertThat(api.requests.single().mimeType).isEqualTo("image/jpeg")
        assertThat(api.requests.single().prompt).contains("anime-style")
        assertThat(api.requests.single().negativePrompt).isEqualTo(AvatarStyle.NEGATIVE_PROMPT)
    }

    @Test fun `same photo and style is served from cache`() = runTest {
        val api = FakeApi(mutableListOf({ png }))
        val gen = generator(api, dispatcher = StandardTestDispatcher(testScheduler))
        gen.generate(jpeg, AvatarStyle.CHIBI)
        val second = gen.generate(jpeg, AvatarStyle.CHIBI)
        assertThat(second.fromCache).isTrue()
        assertThat(api.requests).hasSize(1)
    }

    @Test fun `different style is a cache miss`() = runTest {
        val api = FakeApi(mutableListOf({ png }, { png }))
        val gen = generator(api, dispatcher = StandardTestDispatcher(testScheduler))
        gen.generate(jpeg, AvatarStyle.CHIBI)
        gen.generate(jpeg, AvatarStyle.PIXEL_ART)
        assertThat(api.requests).hasSize(2)
    }

    @Test fun `transient errors are retried with backoff`() = runTest {
        val api = FakeApi(mutableListOf(
            { throw TalktoError.Network("timeout") },
            { throw TalktoError.RateLimited("429") },
            { png },
        ))
        val sleeps = mutableListOf<Long>()
        val avatar = generator(api, sleeps, StandardTestDispatcher(testScheduler)).generate(jpeg, AvatarStyle.CARTOON_3D)
        assertThat(avatar.fromCache).isFalse()
        assertThat(sleeps).containsExactly(1_500L, 3_000L).inOrder()
    }

    @Test fun `permanent errors are not retried`() = runTest {
        val api = FakeApi(mutableListOf({ throw TalktoError.ApiRejected("nsfw") }, { png }))
        val e = runCatching { generator(api, dispatcher = StandardTestDispatcher(testScheduler)).generate(jpeg, AvatarStyle.CHIBI) }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.ApiRejected::class.java)
        assertThat(api.requests).hasSize(1)
    }

    @Test fun `gives up after max attempts`() = runTest {
        val api = FakeApi(MutableList(5) { { throw TalktoError.Network("down") } })
        val e = runCatching { generator(api, dispatcher = StandardTestDispatcher(testScheduler)).generate(jpeg, AvatarStyle.CHIBI) }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.Network::class.java)
        assertThat(api.requests).hasSize(3)
    }

    @Test fun `rejects non-image input without calling the api`() = runTest {
        val api = FakeApi(mutableListOf())
        val e = runCatching { generator(api, dispatcher = StandardTestDispatcher(testScheduler)).generate("hello".repeat(500).toByteArray(), AvatarStyle.CHIBI) }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.InvalidInput::class.java)
        assertThat(api.requests).isEmpty()
    }

    @Test fun `rejects a non-image response`() = runTest {
        val api = FakeApi(mutableListOf({ "<html>error</html>".toByteArray() }))
        val e = runCatching { generator(api, dispatcher = StandardTestDispatcher(testScheduler)).generate(jpeg, AvatarStyle.CHIBI) }.exceptionOrNull()
        assertThat(e).isInstanceOf(TalktoError.ApiRejected::class.java)
    }

    @Test fun `sniffMime recognises webp`() {
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
        assertThat(AvatarGenerator.sniffMime(webp)).isEqualTo("image/webp")
    }

    // ------------------------------------------------------- Stability client

    @Test fun `stability client sends multipart with auth and returns bytes`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
            server.start()
            val api = StabilityImageApi(apiKey = { "sk-test" }, baseUrl = server.url("/").toString())
            val out = api.transform(TransformRequest(jpeg, "image/jpeg", "prompt", "neg", 0.6f))
            assertThat(out).isEqualTo(png)
            val recorded = server.takeRequest()
            assertThat(recorded.path).isEqualTo("/v2beta/stable-image/control/structure")
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer sk-test")
            assertThat(recorded.getHeader("Accept")).isEqualTo("image/*")
            val body = recorded.body.readUtf8()
            assertThat(body).contains("name=\"control_strength\"")
            assertThat(body).contains("0.60")
        }
    }

    @Test fun `stability client maps http errors`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("{\"message\":\"slow down\"}"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            server.start()
            val api = StabilityImageApi(apiKey = { "k" }, baseUrl = server.url("/").toString())
            val req = TransformRequest(jpeg, "image/jpeg", "p", "n")
            assertThat(runCatching { api.transform(req) }.exceptionOrNull()).isInstanceOf(TalktoError.RateLimited::class.java)
            assertThat(runCatching { api.transform(req) }.exceptionOrNull()).isInstanceOf(TalktoError.ApiKeyMissing::class.java)
        }
    }

    @Test fun `stability client fails fast without key`() = runTest {
        val api = StabilityImageApi(apiKey = { null })
        assertThat(runCatching { api.transform(TransformRequest(jpeg, "image/jpeg", "p", "n")) }.exceptionOrNull())
            .isInstanceOf(TalktoError.ApiKeyMissing::class.java)
    }

    // --------------------------------------------------------------- lip-sync

    @Test fun `viseme planner merges repeated shapes and handles cyrillic`() {
        val frames = VisemePlanner().planWord("мама")
        assertThat(frames.map { it.viseme }).containsExactly(Viseme.MBP, Viseme.AI, Viseme.MBP, Viseme.AI).inOrder()
        val merged = VisemePlanner().planWord("ooo")
        assertThat(merged).hasSize(1)
        assertThat(merged.single().viseme).isEqualTo(Viseme.O)
    }

    @Test fun `utterance plan is monotonic and ends at rest`() {
        val frames = VisemePlanner().planUtterance("Здравей, Talkto!")
        assertThat(frames.last().viseme).isEqualTo(Viseme.REST)
        frames.zipWithNext().forEach { (a, b) -> assertThat(b.startMs).isAtLeast(a.startMs + a.durationMs) }
    }
}
