package com.talkto.core.agent

import com.talkto.core.apps.AppActionResult
import com.talkto.core.apps.AppController
import com.talkto.core.apps.AppInfo
import com.talkto.core.apps.TerminateMethod
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.files.FileSystemManager
import com.talkto.core.files.PathGuard
import com.talkto.core.memory.InMemoryActionLogStore
import com.talkto.core.memory.MemoryRepository
import java.nio.file.Files

/** A dispatcher with inert collaborators, for tests that only need one to exist. */
object ToolDispatcherFixtures {
    fun dispatcher(): ToolDispatcher = ToolDispatcher(
        files = FileSystemManager(PathGuard(listOf(Files.createTempDirectory("talkto").toRealPath()))),
        apps = object : AppController {
            override suspend fun installedApps() = emptyList<AppInfo>()
            override suspend fun launch(query: String) = AppActionResult(query, query, false, "none")
            override suspend fun terminate(query: String, method: TerminateMethod) = AppActionResult(query, query, false, "none")
            override fun availableTerminateMethods() = emptySet<TerminateMethod>()
        },
        avatar = object : AvatarActions {
            val animations = mutableListOf<AnimationCommand>()
            override fun hasPendingPhoto() = false
            override suspend fun generateFromPendingPhoto(style: AvatarStyle, extraPrompt: String?) = error("unused")
            override suspend fun generateFromFile(path: String, style: AvatarStyle, extraPrompt: String?) = error("unused")
            override suspend fun animate(command: AnimationCommand) { animations += command }
        },
        memory = MemoryRepository(InMemoryActionLogStore()),
        gate = { false },
    )
}
