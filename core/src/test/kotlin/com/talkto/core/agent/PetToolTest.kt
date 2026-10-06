package com.talkto.core.agent

import com.google.common.truth.Truth.assertThat
import com.talkto.core.apps.AppActionResult
import com.talkto.core.apps.AppController
import com.talkto.core.apps.AppInfo
import com.talkto.core.apps.TerminateMethod
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.commands.AppCommand
import com.talkto.core.files.FileSystemManager
import com.talkto.core.files.PathGuard
import com.talkto.core.games.GameKind
import com.talkto.core.memory.InMemoryActionLogStore
import com.talkto.core.memory.MemoryRepository
import com.talkto.core.pet.Food
import com.talkto.core.quiz.TriviaCategory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.nio.file.Files

class PetToolTest {

    private val done = mutableListOf<Any>()

    private val controls = object : PetControls {
        override fun describe() = "I'm fine."
        override fun feed(food: Food) { done += food }
        override fun play() { done += "play" }
        override fun sleep(asleep: Boolean) { done += if (asleep) "sleep" else "wake" }
        override fun app(command: AppCommand) { done += command }
        override fun game(kind: GameKind) { done += kind }
        override fun lessons() { done += "lessons" }
    }

    private val dispatcher = ToolDispatcher(
        files = FileSystemManager(PathGuard(listOf(Files.createTempDirectory("talkto").toRealPath()))),
        apps = object : AppController {
            override suspend fun installedApps() = emptyList<AppInfo>()
            override suspend fun launch(query: String) = AppActionResult(query, query, false, "none")
            override suspend fun terminate(query: String, method: TerminateMethod) = AppActionResult(query, query, false, "none")
            override fun availableTerminateMethods() = emptySet<TerminateMethod>()
        },
        avatar = object : AvatarActions {
            override fun hasPendingPhoto() = false
            override suspend fun generateFromPendingPhoto(style: AvatarStyle, extraPrompt: String?) = error("unused")
            override suspend fun generateFromFile(path: String, style: AvatarStyle, extraPrompt: String?) = error("unused")
            override suspend fun animate(command: AnimationCommand) {}
        },
        memory = MemoryRepository(InMemoryActionLogStore()),
        gate = { false },
        pet = controls,
    )

    private fun args(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun `feeds the named food, an apple by default`() = runTest {
        assertThat(dispatcher.dispatch("pet", args("""{"action":"feed","item":"broccoli"}""")).isError).isFalse()
        assertThat(dispatcher.dispatch("pet", args("""{"action":"feed","item":"пица"}""")).content).contains("\"healthy\":false")
        dispatcher.dispatch("pet", args("""{"action":"feed"}"""))
        assertThat(done).containsExactly(Food.BROCCOLI, Food.PIZZA, Food.APPLE).inOrder()
        assertThat(dispatcher.dispatch("pet", args("""{"action":"feed","item":"stones"}""")).isError).isTrue()
    }

    @Test fun `opens board games and the arcade`() = runTest {
        dispatcher.dispatch("pet", args("""{"action":"open_game","item":"chess"}"""))
        dispatcher.dispatch("pet", args("""{"action":"open_game","item":"tetris"}"""))
        dispatcher.dispatch("pet", args("""{"action":"open_game","item":"connect four"}"""))
        assertThat(done).containsExactly(GameKind.CHESS, AppCommand.Tetris, GameKind.CONNECT_FOUR).inOrder()
        assertThat(dispatcher.dispatch("pet", args("""{"action":"open_game","item":"poker"}""")).isError).isTrue()
    }

    @Test fun `starts maths and trivia`() = runTest {
        dispatcher.dispatch("pet", args("""{"action":"start_math","grade":3,"item":"geometry"}"""))
        dispatcher.dispatch("pet", args("""{"action":"start_trivia","item":"space"}"""))
        assertThat(done).containsExactly(AppCommand.Math(3, algebra = false, geometry = true), AppCommand.Trivia(TriviaCategory.SPACE)).inOrder()
        assertThat(dispatcher.dispatch("pet", args("""{"action":"start_math","grade":12}""")).isError).isTrue()
    }

    @Test fun `places, sleep and status`() = runTest {
        dispatcher.dispatch("pet", args("""{"action":"open_place","item":"shop"}"""))
        dispatcher.dispatch("pet", args("""{"action":"open_place","item":"lessons"}"""))
        dispatcher.dispatch("pet", args("""{"action":"sleep"}"""))
        assertThat(done).containsExactly(AppCommand.OpenShop, "lessons", "sleep").inOrder()
        assertThat(dispatcher.dispatch("pet", args("""{"action":"status"}""")).content).contains("I'm fine.")
    }

    @Test fun `without controls the tool says it is unavailable`() = runTest {
        val out = ToolDispatcherFixtures.dispatcher().dispatch("pet", args("""{"action":"play"}"""))
        assertThat(out.isError).isTrue()
        assertThat(out.content).contains("capability_unavailable")
    }
}
