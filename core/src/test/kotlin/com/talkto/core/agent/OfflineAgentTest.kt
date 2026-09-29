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
import com.talkto.core.device.BatteryInfo
import com.talkto.core.device.DeviceActions
import com.talkto.core.device.MemoryInfo
import com.talkto.core.device.SettingsPanel
import com.talkto.core.device.StorageInfo
import com.talkto.core.notes.InMemoryNoteStore
import com.talkto.core.notes.InMemoryReminderStore
import com.talkto.core.notes.NotesRepository
import com.talkto.core.notes.RecordingScheduler
import com.talkto.core.reminders.RemindersRepository
import kotlin.random.Random
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
        override fun progress() = "Ниво 3."
        override fun gameWon() { petLog += "won" }
    }

    private val deviceLog = mutableListOf<String>()
    private val device = object : DeviceActions {
        override fun battery() = BatteryInfo(15, charging = false)
        override fun storage() = StorageInfo(totalBytes = 64L shl 30, freeBytes = 10L shl 30)
        override fun memory() = MemoryInfo(8L shl 30, 3L shl 30, low = false)
        override fun setTorch(on: Boolean) = true.also { deviceLog += "torch:$on" }
        override fun setVolume(percent: Int?, step: Int) = (percent ?: (50 + step * 10)).also { deviceLog += "volume:$it" }
        override fun mute() = 0.also { deviceLog += "mute" }
        override fun openSettings(panel: SettingsPanel) = true.also { deviceLog += "panel:$panel" }
        override fun setTimer(seconds: Int, label: String?) = true.also { deviceLog += "timer:$seconds" }
        override fun setAlarm(hour: Int, minute: Int, label: String?) = true.also { deviceLog += "alarm:$hour:$minute" }
    }
    private val reminderStore = InMemoryReminderStore()
    private val profile = com.talkto.core.profile.ProfileRepository(com.talkto.core.profile.InMemoryProfileStore())
    private val history = com.talkto.core.history.HistoryRepository(com.talkto.core.profile.InMemoryHistoryStore())
    // Monday 2026-09-28 14:10 UTC
    private val nowMs = 1_790_604_600_000L

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
            clock = { nowMs },
            device = device,
            notes = NotesRepository(InMemoryNoteStore(), clock = { System.nanoTime() }),
            reminders = RemindersRepository(reminderStore, RecordingScheduler(), clock = { nowMs }),
            zone = { ZoneOffset.UTC },
        )
        agent = OfflineAgent(
            dispatcher, memory, pet, zone = { ZoneOffset.UTC }, clock = { nowMs }, random = Random(42),
            profile = profile, history = history,
        )
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
        assertThat(say("изтрий Download/report.pdf").text).isEqualTo("Преместих 1 неща в кошчето на ZnaiKo.")
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

    // ------------------------------------------------------------ new offline skills

    @Test fun `calculator and conversions`() = runTest {
        assertThat(say("колко е 15% от 240").text).isEqualTo("36.")
        assertThat(say("12*(3+4)").text).isEqualTo("84.")
        assertThat(say("5 км в мили").text).isEqualTo("5 км = 3,106855961 мили.")
    }

    @Test fun `notes round trip`() = runTest {
        assertThat(say("запиши купи мляко").text).isEqualTo("Записах: „купи мляко“.")
        say("запиши паролата е на рутера")
        assertThat(say("бележки").text).isEqualTo("1. паролата е на рутера\n2. купи мляко")
        assertThat(say("намери в бележките мляко").text).contains("купи мляко")
        assertThat(say("изтрий бележка 1").text).isEqualTo("Изтрих бележка 1.")
        assertThat(say("бележки").text).isEqualTo("1. купи мляко")
    }

    @Test fun `reminders are parsed and scheduled`() = runTest {
        assertThat(say("напомни ми в 18:30 да купя хляб").text).isEqualTo("Добре, ще ти напомня днес в 18:30: „купя хляб“.")
        assertThat(say("напомни ми да звънна на мама след 20 минути").text).isEqualTo("Добре, ще ти напомня днес в 14:30: „звънна на мама“.")
        assertThat(say("напомняния").text).isEqualTo("1. днес в 14:30: звънна на мама\n2. днес в 18:30: купя хляб")
        assertThat(say("отмени напомняне 1").text).isEqualTo("Отмених напомняне 1.")
        assertThat(reminderStore.pending().single().text).isEqualTo("купя хляб")
        assertThat(say("напомни ми нещо").text).startsWith("Кога да ти напомня?")
    }

    @Test fun `timer and alarm go to the clock app`() = runTest {
        assertThat(say("таймер 1 час и 5 минути").text).isEqualTo("Пуснах таймер за 1 ч 5 мин.")
        assertThat(say("аларма 6:45").text).isEqualTo("Будилникът е за 06:45.")
        assertThat(deviceLog).containsExactly("timer:3900", "alarm:6:45").inOrder()
    }

    @Test fun `phone controls`() = runTest {
        assertThat(say("батерия").text).isEqualTo("Батерията е на 15%. Време е за зарядно!")
        assertThat(say("колко място имам").text).startsWith("Свободни са 10,0 GB от 64,0 GB.")
        say("фенерче"); say("угаси фенерчето"); say("по-силно"); say("звук 30%"); say("тихо")
        assertThat(say("отвори wifi").text).isEqualTo("Отварям настройките.")
        assertThat(say("настройки за bluetooth").text).isEqualTo("Отварям настройките.")
        assertThat(deviceLog).containsExactly(
            "torch:true", "torch:false", "volume:60", "volume:30", "mute", "panel:WIFI", "panel:BLUETOOTH",
        ).inOrder()
        assertThat(launched).isEmpty() // "отвори wifi" is a settings panel, not an app
    }

    @Test fun `duplicates are found and extras deleted after confirmation`() = runTest {
        val bytes = ByteArray(20_000) { (it % 7).toByte() }
        Files.write(root.resolve("Download/a.bin"), bytes)
        Files.write(root.resolve("Documents/a copy.bin"), bytes)
        assertThat(say("дубликати").text).startsWith("Намерих 1 групи еднакви файлове")
        assertThat(say("изтрий дубликатите").text).startsWith("Преместих 1 дубликата в кошчето")
        assertThat(confirmations.last()).isInstanceOf(ConfirmationRequest.Delete::class.java)
        val remaining = listOf("Download/a.bin", "Documents/a copy.bin").count { Files.exists(root.resolve(it)) }
        assertThat(remaining).isEqualTo(1)
    }

    @Test fun `cleanup summarises what can be freed`() = runTest {
        Files.write(root.resolve("Download/old.apk"), ByteArray(10))
        Files.createDirectories(root.resolve("Download/empty"))
        val r = say("почисти").text
        assertThat(r).contains("APK")
        assertThat(r).contains("празни папки")
    }

    @Test fun `guess the number game`() = runTest {
        assertThat(say("познай числото").text).startsWith("Намислих си число")
        var lo = 1
        var hi = 100
        var reply = ""
        repeat(10) {
            if (reply.startsWith("Позна")) return@repeat
            val mid = (lo + hi) / 2
            reply = say(mid.toString()).text
            if (reply.startsWith("Нагоре")) lo = mid + 1 else if (reply.startsWith("Надолу")) hi = mid - 1
        }
        assertThat(reply).startsWith("Позна!")
        assertThat(petLog).contains("won")
        assertThat(agent.recognizes("50")).isFalse() // game over, bare numbers mean nothing again
    }

    @Test fun `fun commands`() = runTest {
        assertThat(say("хвърли зар").text).matches("Падна се [1-6]\\.")
        assertThat(say("ези или тура").text).isAnyOf("Ези!", "Тура!")
        assertThat(say("случайно число от 1 до 3").text).isAnyOf("Избрах 1.", "Избрах 2.", "Избрах 3.")
        assertThat(say("виц").text).isNotEmpty()
        assertThat(say("камък").text).startsWith("Аз избрах")
        assertThat(say("ниво").text).isEqualTo("Ниво 3.")
    }

    @Test fun `time and greeting use the injected clock`() = runTest {
        assertThat(say("колко е часът").text).isEqualTo("Часът е 14:10, понеделник, 28 септември.")
        assertThat(say("здравей").text).startsWith("Добър ден!")
    }

    // --------------------------------------------------------------- learning

    @Test fun `learns and recalls the user`() = runTest {
        // The session stores facts before the agent answers; do the same here.
        profile.learnFrom("Казвам се Мария")
        assertThat(say("Казвам се Мария").text).isEqualTo("Приятно ми е, Мария! Ще го запомня.")
        profile.learnFrom("обичам джаз")
        assertThat(say("как се казвам").text).isEqualTo("Казваш се Мария.")
        assertThat(say("какво знаеш за мен").text).contains("Обичаш джаз")
        assertThat(say("здравей").text).startsWith("Добър ден, Мария!")
        assertThat(say("забрави джаз").text).isEqualTo("Забравих го.")
        assertThat(say("какво знаеш за мен").text).doesNotContain("джаз")
    }

    @Test fun `conversation history is searchable`() = runTest {
        history.record(com.talkto.core.history.Speaker.USER, "рецепта за баница", "offline")
        history.record(com.talkto.core.history.Speaker.TALKTO, "С кори и сирене", "offline")
        assertThat(say("история").text).isEqualTo("Ти: рецепта за баница\nАз: С кори и сирене")
        assertThat(say("какво говорихме за баница").text).contains("ти: рецепта за баница")
        assertThat(say("търси в разговорите пица").text).startsWith("Не помня")
    }

    @Test fun `tongue`() = runTest {
        assertThat(say("плезни се").text).isEqualTo("Бе-е-е!")
    }
}
