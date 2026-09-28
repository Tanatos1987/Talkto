package com.talkto.core.apps

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(val packageName: String, val label: String)

@Serializable
enum class TerminateMethod {
    /** Pick the strongest available: root -> Shizuku -> Accessibility swipe -> background kill. */
    AUTO,
    ACCESSIBILITY,
    SHIZUKU,
    ROOT,
    BACKGROUND_KILL,
}

@Serializable
data class AppActionResult(
    val packageName: String,
    val label: String,
    val success: Boolean,
    val method: String,
    val detail: String? = null,
)

/** Implemented on Android by `AndroidAppController`; faked in tests. */
interface AppController {
    suspend fun installedApps(): List<AppInfo>
    suspend fun launch(query: String): AppActionResult
    suspend fun terminate(query: String, method: TerminateMethod = TerminateMethod.AUTO): AppActionResult
    fun availableTerminateMethods(): Set<TerminateMethod>
}

/**
 * Fuzzy "Spotify" / "spotify" / "com.spotify.music" / "спотифай" matching.
 * Exact package > exact label > label prefix > label contains > transliterated label.
 */
object AppMatcher {
    fun bestMatch(query: String, apps: List<AppInfo>): AppInfo? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        apps.firstOrNull { it.packageName.lowercase() == q }?.let { return it }
        val scored = apps.mapNotNull { app ->
            val label = app.label.lowercase()
            val latin = transliterate(label)
            val qLatin = transliterate(q)
            val score = when {
                label == q -> 100
                latin == qLatin -> 95
                label.startsWith(q) -> 80
                latin.startsWith(qLatin) -> 75
                label.contains(q) -> 60
                latin.contains(qLatin) -> 55
                app.packageName.lowercase().contains(qLatin.replace(" ", "")) -> 40
                else -> 0
            }
            if (score > 0) app to score else null
        }
        return scored.maxWithOrNull(compareBy<Pair<AppInfo, Int>> { it.second }.thenBy { -it.first.label.length })?.first
    }

    private val table = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sht", 'ъ' to "a", 'ь' to "y", 'ю' to "yu", 'я' to "ya",
    )

    fun transliterate(s: String): String = buildString { s.forEach { c -> append(table[c] ?: c) } }
}
