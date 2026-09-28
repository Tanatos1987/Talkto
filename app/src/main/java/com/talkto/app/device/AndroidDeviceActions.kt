package com.talkto.app.device

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.Settings
import com.talkto.core.device.BatteryInfo
import com.talkto.core.device.DeviceActions
import com.talkto.core.device.MemoryInfo
import com.talkto.core.device.SettingsPanel
import com.talkto.core.device.StorageInfo
import kotlin.math.roundToInt

/**
 * Phone controls that need no API key and no runtime permission.
 * Wi-Fi/Bluetooth cannot be toggled by apps since Android 10, so those open the system panel instead.
 */
class AndroidDeviceActions(private val context: Context) : DeviceActions {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val cameras = context.getSystemService(CameraManager::class.java)

    override fun battery(): BatteryInfo {
        val bm = context.getSystemService(BatteryManager::class.java)
        return BatteryInfo(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100), bm.isCharging)
    }

    override fun storage(): StorageInfo {
        val fs = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageInfo(totalBytes = fs.totalBytes, freeBytes = fs.availableBytes)
    }

    override fun memory(): MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return MemoryInfo(info.totalMem, info.availMem, info.lowMemory)
    }

    override fun setTorch(on: Boolean): Boolean = runCatching {
        val id = cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return false
        cameras.setTorchMode(id, on)
        true
    }.getOrDefault(false)

    override fun setVolume(percent: Int?, step: Int): Int {
        val stream = AudioManager.STREAM_MUSIC
        if (percent != null) {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, (max * percent.coerceIn(0, 100) / 100f).roundToInt(), AudioManager.FLAG_SHOW_UI)
        } else if (step != 0) {
            val direction = if (step > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(kotlin.math.abs(step)) { audio.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI) }
        }
        return currentPercent()
    }

    override fun mute(): Int {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        return 0
    }

    override fun openSettings(panel: SettingsPanel): Boolean {
        val intent = when (panel) {
            SettingsPanel.WIFI -> Intent(Settings.Panel.ACTION_WIFI)
            SettingsPanel.INTERNET -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            SettingsPanel.VOLUME -> Intent(Settings.Panel.ACTION_VOLUME)
            SettingsPanel.NFC -> Intent(Settings.Panel.ACTION_NFC)
            SettingsPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SettingsPanel.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SettingsPanel.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            SettingsPanel.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            SettingsPanel.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
            SettingsPanel.APP_INFO -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        return start(intent) || start(Intent(Settings.ACTION_SETTINGS))
    }

    override fun setTimer(seconds: Int, label: String?): Boolean = start(
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } },
    )

    override fun setAlarm(hour: Int, minute: Int, label: String?): Boolean = start(
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } },
    )

    private fun currentPercent(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    /** Talkto is on screen when the user asks, so starting an activity from the app context is allowed. */
    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
