package com.talkto.app.apps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.talkto.core.apps.AppActionResult
import com.talkto.core.apps.AppController
import com.talkto.core.apps.AppInfo
import com.talkto.core.apps.AppMatcher
import com.talkto.core.apps.TerminateMethod
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App Management Engine.
 *
 * Launch: explicit launcher intent from PackageManager.
 * Terminate (hybrid, strongest first when method = AUTO):
 *   ROOT          `am force-stop` via su                       - true force stop
 *   SHIZUKU       IActivityManager.forceStopPackage as shell   - true force stop, no root
 *   ACCESSIBILITY swipe the card away in Recents                - what a user would do; process may linger cached
 *   BACKGROUND_KILL ActivityManager.killBackgroundProcesses     - only if the app is already in background
 */
class AndroidAppController(private val context: Context) : AppController {

    private val pm: PackageManager = context.packageManager

    override suspend fun installedApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        resolved
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    override suspend fun launch(query: String): AppActionResult {
        val app = resolve(query)
        val intent = pm.getLaunchIntentForPackage(app.packageName)
            ?: throw TalktoError.CapabilityUnavailable("${app.label} has no launchable screen")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        withContext(Dispatchers.Main) { context.startActivity(intent) }
        return AppActionResult(app.packageName, app.label, success = true, method = "launcher_intent")
    }

    override suspend fun terminate(query: String, method: TerminateMethod): AppActionResult {
        val app = resolve(query)
        if (app.packageName == context.packageName) throw TalktoError.InvalidInput("I can't close myself")
        val order = when (method) {
            TerminateMethod.AUTO -> listOf(TerminateMethod.ROOT, TerminateMethod.SHIZUKU, TerminateMethod.ACCESSIBILITY, TerminateMethod.BACKGROUND_KILL)
            else -> listOf(method)
        }
        val tried = mutableListOf<String>()
        for (m in order) {
            if (!isAvailable(m)) {
                tried += "${m.name.lowercase()}: unavailable"; continue
            }
            val ok = when (m) {
                TerminateMethod.ROOT -> RootShell.forceStop(app.packageName)
                TerminateMethod.SHIZUKU -> ShizukuBridge.forceStop(app.packageName)
                TerminateMethod.ACCESSIBILITY ->
                    TalktoAccessibilityService.instance?.swipeAwayFromRecents(app.label, app.packageName) == true
                TerminateMethod.BACKGROUND_KILL -> killBackground(app.packageName)
                TerminateMethod.AUTO -> false
            }
            if (ok) {
                return AppActionResult(
                    app.packageName, app.label, success = true, method = m.name.lowercase(),
                    detail = if (m == TerminateMethod.BACKGROUND_KILL) "Background processes killed; a foreground app keeps running." else null,
                )
            }
            tried += "${m.name.lowercase()}: failed"
        }
        if (order.size == 1 && !isAvailable(order.first())) {
            throw TalktoError.CapabilityUnavailable(
                when (order.first()) {
                    TerminateMethod.ACCESSIBILITY -> "ZnaiKo accessibility service is not enabled"
                    TerminateMethod.SHIZUKU -> "Shizuku is not running or permission not granted"
                    TerminateMethod.ROOT -> "Device is not rooted or su was denied"
                    else -> "Method not available"
                },
            )
        }
        return AppActionResult(app.packageName, app.label, success = false, method = "none", detail = tried.joinToString("; "))
    }

    override fun availableTerminateMethods(): Set<TerminateMethod> = buildSet {
        if (ShizukuBridge.hasPermission()) add(TerminateMethod.SHIZUKU)
        if (TalktoAccessibilityService.instance != null) add(TerminateMethod.ACCESSIBILITY)
        add(TerminateMethod.BACKGROUND_KILL)
        // Root is probed lazily (it may show a su prompt), so it is reported only once confirmed.
    }

    private suspend fun isAvailable(m: TerminateMethod): Boolean = when (m) {
        TerminateMethod.ROOT -> RootShell.isAvailable()
        TerminateMethod.SHIZUKU -> ShizukuBridge.hasPermission()
        TerminateMethod.ACCESSIBILITY -> TalktoAccessibilityService.instance != null
        TerminateMethod.BACKGROUND_KILL -> true
        TerminateMethod.AUTO -> true
    }

    private fun killBackground(pkg: String): Boolean = runCatching {
        context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
        true
    }.getOrDefault(false)

    private suspend fun resolve(query: String): AppInfo {
        val apps = installedApps()
        return AppMatcher.bestMatch(query, apps) ?: throw TalktoError.NotFound("app '$query'")
    }
}
