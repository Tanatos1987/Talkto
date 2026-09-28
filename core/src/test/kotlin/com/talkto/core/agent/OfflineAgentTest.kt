package com.talkto.core.agent

import com.google.common.truth.Truth.assertThat
import com.talkto.core.apps.AppActionResult
import com.talkto.core.apps.AppController
import com.talkto.core.apps.AppInfo
import com.talkto.core.apps.AppMatcher
import com.talkto.core.apps.TerminateMethod
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.error.TalktoError
import com.talkto.core.files.FileSystemManager
import com.talkto.core.files.PathGuard
import com.talkto.core.memory.ActionRecord
import com.talkto.core.memory.ActionType
import com.talkto.core.memory.InMemoryActionLogStore
import com.talkto.core.memory.MemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset

class OfflineAgentTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var root: Path
    private val store = InMemoryActionLogStore()
    private val memory = MemoryRepository(store)
    private var approve = true
    private val confirmations = mutableListOf<ConfirmationRequest>()
    private val launched = mutableListOf<String>()
    private val petLog = mutableListOf<String>()
    private lateinit var agent: OfflineAgent

    private val apps = object : AppController {
        val installed = listOf(AppInfo("com.spotify.music", "Spotify"), AppInfo("com.android.camera2", "Камера"))
        override suspend fun installedApps() = installed
        override suspend fun launch(query: String): AppActionResult {
            val app = AppMatcher.bestMatch(query, installed) ?: throw TalktoError.NotFound("app '$query'")
            launched += app.packageName
            return AppActionResult(app.packageName, app.label, true, "launcher_intent")
        }
        override suspend fun terminate(query: String, method: TerminateMethod): AppActionResult {
            val app = AppMatcher.bestMatch(query, installed) ?: throw TalktoError.NotFound(query)
            return AppActionResult(app.packageName, app.label, true, "shizuku")
        }
        override fun availableTerminateMethods() = setOf(TerminateMethod.SHIZUKU)
    }

    private val avatar = object : AvatarActions {
        override fun hasPendingPhoto() = false
        override suspend fun generateFromPendingPhoto(style: AvatarStyle, extraPrompt: String?) = error("unused")
        override suspend fun generateFromFile(path: String, style: AvatarStyle, extraPrompt: String?) = error("unused")
        override suspend fun animate(command: AnimationCommand) = Unit
    }

    private val pet = object : PetActions {
        override fun feed() { petLog += "feed" }
        override fun play() { petLog += "play" }
        override fun sleep() { petLog += "sleep" }
        override fun wake() { petLog += "wake" }
        override fun status() = "Чувствам се чудесно."
    }

    @Before fun setUp() {
        root = tmp.newFolder("sd").toPath().toRealPath()
        listOf("Download", "Documents", "Pictures", "DCIM/Camera").forEach { Files.createDirectories(root.resolve(it)) }
        Files.write(root.resolve("Download/IMG_1.jpg"), ByteArray(10))
        Files.write(root.resolve("Download/IMG_2.png"), ByteArray(10))
        Files.write(root.resolve("Download/report.pdf"), ByteArray(10))
        val dispatcher = ToolDispatcher(
            files = FileSystemManager(PathGuard(listOf(root))),
            apps = apps, avatar = avatar, memory = memory,
            gate = { confirmations += it; approve },
        )
        agent = OfflineAgent(dispatcher, memory, pet, zone = { ZoneOffset.UTC })
    }

    private suspend fun say(text: String) = agent.send(text) {}

    @Test fun `opens apps by Bulgarian name`() = runTest {
        val r = say("Отвори камера")
        assertThat(r.text).isEqualTo("Отварям Камера.")
        assertThat(launched).containsExactly("com.android.camera2")
        assertThat(store.records.single().type).isEqualTo(ActionType.APP_LAUNCH)
    }

    @Test fun `closes apps`() = runTest {
        assertThat(say("затвори spotify").text).isEqualTo("Затворих Spotify.")
    }

    @Test fun `searches by kind in an aliased folder`() = runTest {
        val r = say("намери снимки в изтегляния")
        assertThat(r.text).startsWith("Намерих 2:")
        assertThat(r.text).contains("IMG_1.jpg")
        assertThat(r.text).doesNotContain("report.pdf")
    }

    @Test fun `searches by name`() = runTest {
        assertThat(say("търси report").text).contains("report.pdf")
    }

    @Test fun `moves a file and records it for habit learning`() = runTest {
        val r = say("премести Download/report.pdf в документи")
        assertThat(r.text).isEqualTo("Преместих „report.pdf“ в Documents.")
        assertThat(Files.exists(root.resolve("Documents/report.pdf"))).isTrue()
        assertThat(store.records.single().type).isEqualTo(ActionType.FILE_MOVE)
    }

    @Test fun `delete always goes through the confirmation dialog`() = runTest {
        approve = false
        assertThat(say("изтрий Download/report.pdf").text).isEqualTo("Добре, нищо не съм променял.")
        assertThat(confirmations.single()).isInstanceOf(ConfirmationRequest.Delete::class.java)
        assertThat(Files.exists(root.resolve("Download/report.pdf"))).isTrue()

        approve = true
        assertThat(say("изтрий Download/report.pdf").text).isEqualTo("Преместих 1 неща в кошчето на Talkto.")
        assertThat(Files.exists(root.resolve("Download/report.pdf"))).isFalse()
    }

    @Test fun `protected paths are refused with a friendly line`() = runTest {
        assertThat(say("покажи /system").text).isEqualTo("Това място е защитено и не пипам там.")
    }

    @Test fun `organize asks first and then sorts by type`() = runTest {
        val r = say("подреди изтегляния по тип")
        assertThat(r.text).isEqualTo("Подредих 3 файла в подпапки.")
        assertThat(confirmations.single()).isInstanceOf(ConfirmationRequest.Organize::class.java)
        assertThat(Files.exists(root.resolve("Download/Images/IMG_1.jpg"))).isTrue()
    }

    @Test fun `open folder lists instead of launching an app`() = runTest {
        val r = say("отвори папка изтегляния")
        assertThat(r.text).startsWith("3 неща")
        assertThat(launched).isEmpty()
    }

    @Test fun `pet commands drive the tamagotchi`() = runTest {
        say("нахрани"); say("играй"); say("лека нощ"); say("добро утро")
        assertThat(petLog).containsExactly("feed", "play", "sleep", "wake").inOrder()
    }

    @Test fun `habits are explained offline`() = runTest {
        repeat(3) { i ->
            memory.record(ActionRecord(type = ActionType.FILE_MOVE, subject = "a$i.pdf", source = "/sd/Download", target = "/sd/Documents", timestampMs = System.currentTimeMillis() - i * 3_600_000L))
        }
        val r = say("какво си научил")
        assertThat(r.text).contains("местиш файлове от Download в Documents")
    }

    @Test fun `free-form chat asks for an API key`() = runTest {
        val r = say("Разкажи ми виц за котки")
        assertThat(r.needsApiKey).isTrue()
        assertThat(agent.recognizes("Разкажи ми виц за котки")).isFalse()
        assertThat(agent.recognizes("отвори камера")).isTrue()
    }

    @Test fun `help lists commands`() = runTest {
        assertThat(say("помощ").text).contains("подреди <папка>")
    }
}
