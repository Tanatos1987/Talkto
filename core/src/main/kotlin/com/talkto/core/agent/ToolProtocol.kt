package com.talkto.core.agent

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool

/**
 * The five tools Talkto exposes to Claude. Schemas are strict (additionalProperties = false),
 * so the model's arguments are guaranteed to match; the dispatcher still validates semantics.
 */
object ToolProtocol {
    const val MANAGE_FILE = "manage_file"
    const val LAUNCH_APP = "launch_app"
    const val TERMINATE_APP = "terminate_app"
    const val GENERATE_AVATAR = "generate_avatar_from_image"
    const val ANIMATE_AVATAR = "animate_avatar"
    const val DEVICE = "device"
    const val NOTES = "notes"
    const val REMINDERS = "reminders"
    const val USER_PROFILE = "user_profile"
    const val CONVERSATION_HISTORY = "conversation_history"

    val all: List<Tool> by lazy {
        listOf(
            manageFile(), launchApp(), terminateApp(), generateAvatar(), animateAvatar(), device(), notes(), reminders(),
            userProfile(), conversationHistory(),
        )
    }

    private fun manageFile() = tool(
        name = MANAGE_FILE,
        description = """
            File operations on the phone's shared storage (the same folders the user sees in a file manager).
            Paths may be absolute (/storage/emulated/0/Download/a.pdf) or relative to the storage root (Download/a.pdf).
            Operations:
            - list: list one folder (path).
            - search: find files under path using name_pattern (glob, e.g. "*.pdf"), extensions, size and age filters.
            - copy / move: path -> destination. If destination is an existing folder the item keeps its name.
            - rename: path + new_name.
            - mkdir: create folder at path.
            - organize: sort the direct files of folder `path` into sub-folders by strategy. Always call with dry_run=true first
              and show the user the plan.
            - delete: ALWAYS two steps. Step 1: call with paths (and optional permanent) and no confirmation_token; you get a
              dry-run plan with a token. Tell the user exactly what will be removed. Step 2: only after the user agrees, call
              again with the same paths and confirmation_token. The app shows its own confirmation dialog as well.
              Deleted items go to the Talkto trash unless permanent=true.
            - empty_trash: permanently clear the Talkto trash.
            - storage_report: what takes space under path (per category, largest files, trash size).
            - find_duplicates: byte-identical files under path; in each group the first path is the oldest copy to keep.
              To remove the extras, run delete (two-step as always) with the other paths.
            - find_empty_dirs: empty folders under path.
            System folders and app-private data (Android/data, Android/obb) are protected and will be refused.
        """.trimIndent(),
        properties = mapOf(
            "operation" to enumProp(
                "Which operation to run.",
                "list", "search", "copy", "move", "rename", "mkdir", "organize", "delete", "empty_trash",
                "storage_report", "find_duplicates", "find_empty_dirs",
            ),
            "path" to strProp("Source file/folder, or the folder to list/search/organize."),
            "paths" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Targets for delete. Use instead of path for several items."),
            "destination" to strProp("Destination folder or full destination path for copy/move."),
            "new_name" to strProp("New file name for rename (no slashes)."),
            "name_pattern" to strProp("Glob for search, case-insensitive, e.g. \"IMG_2025*\" or \"*.pdf\"."),
            "extensions" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Extensions without dot for search, e.g. [\"jpg\",\"png\"]."),
            "min_size_bytes" to intProp("Search: minimum file size in bytes."),
            "max_size_bytes" to intProp("Search: maximum file size in bytes."),
            "modified_within_days" to intProp("Search: only files modified in the last N days."),
            "max_results" to intProp("Search: maximum results, default 100, at most 1000."),
            "strategy" to enumProp("Organize strategy.", "by_type", "by_month", "by_extension"),
            "dry_run" to boolProp("Report what would happen without changing anything."),
            "overwrite" to boolProp("copy/move: replace an existing destination. Default false."),
            "permanent" to boolProp("delete: skip the trash. Default false."),
            "confirmation_token" to strProp("delete step 2: token from the dry-run plan."),
        ),
        required = listOf("operation"),
    )

    private fun launchApp() = tool(
        name = LAUNCH_APP,
        description = "Open an installed app. `app` may be the visible name in any language (\"Spotify\", \"Камера\") or the package name. " +
            "Use list_only=true to get the installed app list when you are unsure what the user means.",
        properties = mapOf(
            "app" to strProp("App name or package name."),
            "list_only" to boolProp("Return installed launchable apps instead of launching."),
        ),
        required = listOf("app"),
    )

    private fun terminateApp() = tool(
        name = TERMINATE_APP,
        description = "Close a running app. method=auto picks the strongest available route: root, Shizuku (am force-stop), " +
            "the Accessibility service (swipes the app away in Recents), or a background-process kill. " +
            "Force-stopping loses unsaved work in that app; say so when the user might care.",
        properties = mapOf(
            "app" to strProp("App name or package name."),
            "method" to enumProp("How to close it.", "auto", "accessibility", "shizuku", "root", "background_kill"),
        ),
        required = listOf("app"),
    )

    private fun generateAvatar() = tool(
        name = GENERATE_AVATAR,
        description = "Turn a photo into the user's stylised Talkto avatar. source=picked_photo uses the photo the user just chose " +
            "in the app (ask them to tap the camera/gallery button first if none is pending); source=path uses an image file on storage.",
        properties = mapOf(
            "source" to enumProp("Where the photo comes from.", "picked_photo", "path"),
            "path" to strProp("Image path when source=path."),
            "style" to enumProp("Visual style.", "anime_2d", "cartoon_3d", "pixel_art", "chibi", "watercolor"),
            "extra_prompt" to strProp("Optional extra style words, e.g. \"wearing a red scarf, autumn colours\"."),
        ),
        required = listOf("source", "style"),
    )

    private fun animateAvatar() = tool(
        name = ANIMATE_AVATAR,
        description = "Make the avatar react: set a facial expression, play a gesture and optionally speak a short line with lip-sync. " +
            "Use it to show emotion that matches your reply; keep speech under 200 characters.",
        properties = mapOf(
            "expression" to enumProp("Facial expression. tongue = playfully sticks the tongue out.", "neutral", "happy", "sad", "surprised", "thinking", "sleepy", "angry", "love", "confused", "tongue"),
            "gesture" to enumProp("Body gesture.", "none", "nod", "shake", "wave", "bounce", "spin"),
            "speech" to strProp("Optional short line spoken aloud with lip-sync."),
            "hold_ms" to intProp("How long to hold the expression, 500-10000 ms. Default 2500."),
        ),
        required = listOf("expression"),
    )

    private fun device() = tool(
        name = DEVICE,
        description = "Phone status and quick controls: battery, free storage, RAM, flashlight, media volume, open a settings panel " +
            "(Wi-Fi, Bluetooth, display...), or hand a timer/alarm to the system clock app. Apps cannot toggle Wi-Fi or Bluetooth " +
            "directly on modern Android; open_settings shows the panel so the user can.",
        properties = mapOf(
            "action" to enumProp(
                "What to do.",
                "battery", "storage", "memory", "torch_on", "torch_off", "volume_up", "volume_down", "volume_mute", "volume_set",
                "open_settings", "set_timer", "set_alarm",
            ),
            "percent" to intProp("volume_set: 0-100."),
            "panel" to enumProp("open_settings: which panel.", "wifi", "internet", "bluetooth", "volume", "display", "battery", "location", "nfc", "sound", "app_info"),
            "seconds" to intProp("set_timer: duration in seconds."),
            "hour" to intProp("set_alarm: 0-23."),
            "minute" to intProp("set_alarm: 0-59."),
            "label" to strProp("Optional label for a timer or alarm."),
        ),
        required = listOf("action"),
    )

    private fun notes() = tool(
        name = NOTES,
        description = "The user's private notes, stored only on this phone. list returns newest first with ids.",
        properties = mapOf(
            "action" to enumProp("What to do.", "add", "list", "search", "delete"),
            "text" to strProp("add: the note text."),
            "query" to strProp("search: words to look for."),
            "id" to intProp("delete: note id from list/search."),
        ),
        required = listOf("action"),
    )

    private fun reminders() = tool(
        name = REMINDERS,
        description = "Reminders delivered as notifications at a set time, even if Talkto is closed. Give either in_minutes or at " +
            "(local date-time, ISO format like 2026-09-28T18:30, in the time zone from <live_context>).",
        properties = mapOf(
            "action" to enumProp("What to do.", "add", "list", "cancel"),
            "text" to strProp("add: what to remind about."),
            "in_minutes" to intProp("add: minutes from now."),
            "at" to strProp("add: local date-time, e.g. 2026-09-28T18:30."),
            "id" to intProp("cancel: reminder id from list."),
        ),
        required = listOf("action"),
    )

    private fun userProfile() = tool(
        name = USER_PROFILE,
        description = "Long-term memory about the user, kept on the phone and shown in <user_profile>. Use remember when the user " +
            "shares a stable fact or preference (name, birthday, city, job, likes/dislikes, how they want things done). " +
            "Keys: name, birthday, city, job, age, favourite:<thing>, likes:<thing>, dislikes:<thing>, note:<topic>, " +
            "alias:<phrase> (value = the command that phrase should run). Do not store secrets such as passwords or card numbers.",
        properties = mapOf(
            "action" to enumProp("What to do.", "list", "remember", "forget"),
            "key" to strProp("remember: fact key, e.g. likes:кафе."),
            "value" to strProp("remember: the fact."),
            "what" to strProp("forget: key or words to forget."),
        ),
        required = listOf("action"),
    )

    private fun conversationHistory() = tool(
        name = CONVERSATION_HISTORY,
        description = "The recorded log of past conversations with the user (both yours and offline mode). " +
            "search finds earlier messages by words; recent returns the latest lines.",
        properties = mapOf(
            "action" to enumProp("What to do.", "search", "recent"),
            "query" to strProp("search: words to look for."),
            "limit" to intProp("How many lines, default 20, at most 100."),
        ),
        required = listOf("action"),
    )

    // ----------------------------------------------------------------- builders

    private fun tool(name: String, description: String, properties: Map<String, Any>, required: List<String>): Tool {
        val props = Tool.InputSchema.Properties.builder().apply {
            properties.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
        }.build()
        return Tool.builder()
            .name(name)
            .description(description)
            .strict(true)
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(props)
                    .required(required)
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build(),
            )
            .build()
    }

    private fun strProp(d: String) = mapOf("type" to "string", "description" to d)
    private fun intProp(d: String) = mapOf("type" to "integer", "description" to d)
    private fun boolProp(d: String) = mapOf("type" to "boolean", "description" to d)
    private fun enumProp(d: String, vararg values: String) = mapOf("type" to "string", "enum" to values.toList(), "description" to d)
}
