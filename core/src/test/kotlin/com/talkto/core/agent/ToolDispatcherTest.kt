package com.talkto.core.agent

import com.google.common.truth.Truth.assertThat
import com.talkto.core.apps.AppActionResult
import com.talkto.core.apps.AppController
import com.talkto.core.apps.AppInfo
import com.talkto.core.apps.AppMatcher
import com.talkto.core.apps.TerminateMethod
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.GeneratedAvatar
import com.talkto.core.error.TalktoError
import com.talkto.core.files.FileSystemManager
import com.talkto.core.files.PathGuard
import com.talkto.core.memory.ActionType
import com.talkto.core.memory.InMemoryActionLogStore
import com.talkto.core.memory.MemoryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ToolDispatcherTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var root: Path
    private val store = InMemoryActionLogStore()
    private val requests = mutableListOf<ConfirmationRequest>()
    private var approve = true
    private val errors = mutableListOf<TalktoError>()
    private val animations = mutableListOf<AnimationCommand>()
    private lateinit var dispatcher: ToolDispatcher

    private val apps = object : AppController {
        val installed = listOf(AppInfo("com.spotify.music", "Spotify"), AppInfo("com.android.camera", "Камера"))
        override suspend fun installedApps() = installed
        override suspend fun launch(query: String): AppActionResult {
            val app = AppMatcher.bestMatch(query, installed) ?: throw TalktoError.NotFound(query)
            return AppActionResult(app.packageName, app.label, true, "intent")
        }
        override suspend fun terminate(query: String, method: TerminateMethod) =
            throw TalktoError.CapabilityUnavailable("Accessibility service is off")
        override fun availableTerminateMethods() = setOf(TerminateMethod.BACKGROUND_KILL)
    }

    private val avatar = object : AvatarActions {
        override fun hasPendingPhoto() = false
        override suspend fun generateFromPendingPhoto(style: AvatarStyle, extraPrompt: String?) = error("unused")
        override suspend fun generateFromFile(path: String, style: AvatarStyle, extraPrompt: String?) =
            GeneratedAvatar("id1", style, "/x.png", 0)
        override suspend fun animate(command: AnimationCommand) { animations += command }
    }

    @Before fun setUp() {
        root = tmp.newFolder("sd").toPath().toRealPath()
        Files.createDirectories(root.resolve("Download"))
        Files.write(root.resolve("Download/old.zip"), ByteArray(4096))
        dispatcher = ToolDispatcher(
            files = FileSystemManager(PathGuard(listOf(root))),
            apps = apps,
            avatar = avatar,
            memory = MemoryRepository(store),
            gate = { requests += it; approve },
            onError = { errors += it },
        )
    }

    private fun args(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun `delete without token only returns a plan`() = runTest {
        val out = dispatcher.dispatch("manage_file", args("""{"operation":"delete","path":"Download/old.zip"}"""))
        assertThat(out.isError).isFalse()
        assertThat(out.content).contains("confirmation_token")
        assertThat(Files.exists(root.resolve("Download/old.zip"))).isTrue()
        assertThat(requests).isEmpty()
    }

    @Test fun `delete with token asks the human and respects a no`() = runTest {
        val plan = Json.parseToJsonElement(
            dispatcher.dispatch("manage_file", args("""{"operation":"delete","path":"Download/old.zip"}""")).content,
        ).jsonObject["plan"]!!.jsonObject
        val token = plan["token"]!!.jsonPrimitive.content

        approve = false
        val declined = dispatcher.dispatch("manage_file", args("""{"operation":"delete","path":"Download/old.zip","confirmation_token":"$token"}"""))
        assertThat(declined.isError).isTrue()
        assertThat(declined.content).contains("confirmation_required")
        assertThat(requests.single()).isInstanceOf(ConfirmationRequest.Delete::class.java)
        assertThat(Files.exists(root.resolve("Download/old.zip"))).isTrue()

        // The declined token is burnt; the model must start over.
        approve = true
        val reused = dispatcher.dispatch("manage_file", args("""{"operation":"delete","confirmation_token":"$token"}"""))
        assertThat(reused.isError).isTrue()
    }

    @Test fun `approved delete goes to trash and is remembered`() = runTest {
        val token = Json.parseToJsonElement(
            dispatcher.dispatch("manage_file", args("""{"operation":"delete","path":"Download/old.zip"}""")).content,
        ).jsonObject["plan"]!!.jsonObject["token"]!!.jsonPrimitive.content
        val out = dispatcher.dispatch("manage_file", args("""{"operation":"delete","confirmation_token":"$token"}"""))
        assertThat(out.isError).isFalse()
        assertThat(out.content).contains("\"movedToTrash\":true")
        assertThat(Files.exists(root.resolve("Download/old.zip"))).isFalse()
        assertThat(store.records.single().type).isEqualTo(ActionType.FILE_DELETE)
    }

    @Test fun `move is recorded for habit learning`() = runTest {
        Files.createDirectories(root.resolve("Documents"))
        val out = dispatcher.dispatch("manage_file", args("""{"operation":"move","path":"Download/old.zip","destination":"Documents"}"""))
        assertThat(out.isError).isFalse()
        val r = store.records.single()
        assertThat(r.type).isEqualTo(ActionType.FILE_MOVE)
        assertThat(r.source).isEqualTo(root.resolve("Download").toString())
        assertThat(r.target).isEqualTo(root.resolve("Documents").toString())
    }

    @Test fun `errors become friendly is_error results, never exceptions`() = runTest {
        val out = dispatcher.dispatch("manage_file", args("""{"operation":"list","path":"/system"}"""))
        assertThat(out.isError).isTrue()
        assertThat(out.content).contains("protected_path")
        assertThat(errors.single().kind).isEqualTo(TalktoError.Kind.PROTECTED_PATH)
    }

    @Test fun `unknown tool is an error result`() = runTest {
        assertThat(dispatcher.dispatch("rm_rf", args("{}")).isError).isTrue()
    }

    @Test fun `launch matches cyrillic labels`() = runTest {
        val out = dispatcher.dispatch("launch_app", args("""{"app":"камера"}"""))
        assertThat(out.content).contains("com.android.camera")
        assertThat(store.records.single().type).isEqualTo(ActionType.APP_LAUNCH)
    }

    @Test fun `terminate reports unavailable capability`() = runTest {
        val out = dispatcher.dispatch("terminate_app", args("""{"app":"Spotify","method":"accessibility"}"""))
        assertThat(out.isError).isTrue()
        assertThat(out.content).contains("capability_unavailable")
    }

    @Test fun `generate avatar from picked photo requires a photo`() = runTest {
        val out = dispatcher.dispatch("generate_avatar_from_image", args("""{"source":"picked_photo","style":"chibi"}"""))
        assertThat(out.isError).isTrue()
        assertThat(out.content).contains("photo")
    }

    @Test fun `animate clamps hold time`() = runTest {
        dispatcher.dispatch("animate_avatar", args("""{"expression":"happy","gesture":"wave","hold_ms":999999}"""))
        assertThat(animations.single().expression).isEqualTo(Expression.HAPPY)
        assertThat(animations.single().holdMs).isEqualTo(10_000)
    }

    @Test fun `tool schemas are strict objects`() {
        assertThat(ToolProtocol.all.map { it.name() }).containsExactly(
            "manage_file", "launch_app", "terminate_app", "generate_avatar_from_image", "animate_avatar",
            "device", "notes", "reminders",
        )
        ToolProtocol.all.forEach { t ->
            assertThat(t.strict().orElse(false)).isTrue()
            assertThat(t.inputSchema()._additionalProperties()["additionalProperties"].toString()).isEqualTo("false")
        }
    }
}
