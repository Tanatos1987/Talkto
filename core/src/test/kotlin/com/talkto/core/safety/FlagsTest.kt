package com.talkto.core.safety

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.time.ZoneOffset

class FlagsTest {

    private fun flag(at: Long, reply: String = "reply $at", reason: FlagReason = FlagReason.SCARY) =
        FlaggedReply(atMs = at, reason = reason, reply = reply, question = "question $at", model = "claude-sonnet-5-5")

    @Test fun `the log keeps the newest flags, oldest first`() {
        var log = FlagLog()
        (1L..(FlagLog.KEEP + 5L)).forEach { log = log.add(flag(it)) }
        assertThat(log.items).hasSize(FlagLog.KEEP)
        assertThat(log.items.first().atMs).isEqualTo(6L)
        assertThat(log.items.last().atMs).isEqualTo(FlagLog.KEEP + 5L)
    }

    @Test fun `flagging the same answer again replaces the earlier flag`() {
        val log = FlagLog().add(flag(1, "The wolf ate everyone.")).markSent(listOf(1L))
            .add(flag(2, "The wolf ate everyone.", FlagReason.NOT_FOR_KIDS))
        assertThat(log.items).hasSize(1)
        assertThat(log.items.single().reason).isEqualTo(FlagReason.NOT_FOR_KIDS)
        assertThat(log.unsent.map { it.atMs }).containsExactly(2L)
    }

    @Test fun `sent, e-mailed and removed`() {
        val log = FlagLog().add(flag(1)).add(flag(2)).add(flag(3)).markSent(listOf(1L)).markEmailed(listOf(2L)).remove(3)
        assertThat(log.unsent.map { it.atMs }).containsExactly(2L)
        assertThat(log.waiting).isEmpty()
        assertThat(log.items.map { it.atMs }).containsExactly(1L, 2L).inOrder()
    }

    @Test fun `only a fresh flag is recent`() {
        val log = FlagLog().add(flag(1_000))
        assertThat(log.recent(1_000 + 60_000)).isNotNull()
        assertThat(log.recent(1_000 + FlagLog.RECENT_MS + 1)).isNull()
        assertThat(FlagLog().recent(5)).isNull()
    }

    @Test fun `the automatic report carries ZnaiKo's words, not the child's`() {
        val f = flag(1_700_000_000_000).copy(question = "My name is Ana and I live on Vitosha 5", reply = "  A   scary\nstory ")
        val obj = Json.parseToJsonElement(FlagReport.json(f, "znaiKo.app", "1.1.80")).jsonObject
        assertThat(obj["reply"]!!.jsonPrimitive.content).isEqualTo("A scary story")
        assertThat(obj["reason"]!!.jsonPrimitive.content).isEqualTo("scary")
        assertThat(obj["version"]!!.jsonPrimitive.content).isEqualTo("1.1.80")
        assertThat(obj["at"]!!.jsonPrimitive.content).isEqualTo("2023-11-14T22:13:20Z")
        assertThat(obj.toString()).doesNotContain("Ana")
        assertThat(obj.keys).doesNotContain("question")
    }

    @Test fun `the parent's e-mail lists every flag with the question`() {
        val text = FlagReport.email(listOf(flag(0, "Boo!"), flag(60_000, "2+2=5", FlagReason.WRONG)), "1.1.80", Lang.BG, ZoneOffset.UTC)
        assertThat(text).contains("😨 Страшно · 01.01.1970 00:00 · claude-sonnet-5-5")
        assertThat(text).contains("Детето: question 0")
        assertThat(text).contains("ZnaiKo: 2+2=5")
        assertThat(text).contains("🤔 Не е вярно")
        assertThat(text).endsWith("ZnaiKo 1.1.80")
        assertThat(FlagReport.email(listOf(flag(0)), "1", Lang.EN, ZoneOffset.UTC)).contains("Child: question 0")
    }

    @Test fun `Claude hears why and how long ago`() {
        val note = FlagReport.contextNote(flag(0, "x".repeat(400), FlagReason.RUDE), 5 * 60_000)
        assertThat(note).contains("5 min ago")
        assertThat(note).contains("it felt rude or mean")
        assertThat(note).contains("x".repeat(159) + "…")
        assertThat(note).doesNotContain("x".repeat(161))
    }

    @Test fun `only https report addresses are used`() {
        assertThat(FlagSender.accepts("https://script.google.com/macros/s/abc/exec")).isTrue()
        assertThat(FlagSender.accepts("http://example.com")).isFalse()
        assertThat(FlagSender.accepts("https://")).isFalse()
        assertThat(FlagSender.accepts("")).isFalse()
        assertThat(FlagSender.accepts(null)).isFalse()
    }

    @Test fun `the sender posts JSON and follows the redirect`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/done").toString()))
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val sent = FlagSender(server.url("/exec").toString()).send("""{"reason":"scary"}""")
            assertThat(sent).isTrue()
            val post = server.takeRequest()
            assertThat(post.method).isEqualTo("POST")
            assertThat(post.getHeader("Content-Type")).startsWith("application/json")
            assertThat(post.body.readUtf8()).isEqualTo("""{"reason":"scary"}""")
            assertThat(server.takeRequest().path).isEqualTo("/done")
        }
    }

    @Test fun `a failed report is not counted as sent`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()
            assertThat(FlagSender(server.url("/exec").toString()).send("{}")).isFalse()
        }
        assertThat(FlagSender("https://127.0.0.1:1/nothing-here").send("{}")).isFalse()
    }
}
