package com.talkto.app.apps

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.TimeUnit

/** Package names are passed to shells and hidden APIs, so they are validated strictly first. */
internal val PACKAGE_NAME = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

/**
 * Shizuku route: calls `IActivityManager.forceStopPackage` through Shizuku's binder wrapper, so the
 * call runs with the shell identity (uid 2000), which holds FORCE_STOP_PACKAGES. Same effect as
 * `adb shell am force-stop`, without root.
 */
object ShizukuBridge {
    const val REQUEST_CODE = 4217

    fun isInstalledAndRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = isInstalledAndRunning() &&
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    fun requestPermission() {
        // The binder can die between the ping and the request; never crash a settings click over it.
        if (isInstalledAndRunning() && !hasPermission()) runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name" }
        if (!hasPermission()) return@withContext false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("Landroid/app/IActivityManager")
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))
            val stub = Class.forName("android.app.IActivityManager\$Stub")
            val am = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val userId = Process.myUid() / PER_USER_RANGE
            am.javaClass.getMethod("forceStopPackage", String::class.java, Int::class.javaPrimitiveType)
                .invoke(am, packageName, userId)
            true
        }.getOrDefault(false)
    }

    private const val PER_USER_RANGE = 100_000
}

/** Root route: `su -c am force-stop <pkg>`, with a timeout so a hung su prompt never blocks the agent. */
object RootShell {
    @Volatile private var cachedAvailable: Boolean? = null

    suspend fun isAvailable(): Boolean = cachedAvailable ?: withContext(Dispatchers.IO) {
        val ok = run("id").let { it.first == 0 && it.second.contains("uid=0") }
        cachedAvailable = ok
        ok
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name" }
        run("am force-stop $packageName").first == 0
    }

    private fun run(command: String): Pair<Int, String> = runCatching {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = p.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly(); return@runCatching -1 to ""
        }
        p.exitValue() to p.inputStream.bufferedReader().readText()
    }.getOrDefault(-1 to "")
}
