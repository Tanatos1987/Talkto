package com.talkto.core.device

import kotlinx.serialization.Serializable

@Serializable
data class BatteryInfo(val percent: Int, val charging: Boolean)

@Serializable
data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedPercent: Int get() = if (totalBytes <= 0) 0 else (100 - freeBytes * 100 / totalBytes).toInt()
}

@Serializable
data class MemoryInfo(val totalBytes: Long, val availableBytes: Long, val low: Boolean)

@Serializable
enum class SettingsPanel { WIFI, INTERNET, BLUETOOTH, VOLUME, DISPLAY, BATTERY, LOCATION, NFC, SOUND, APP_INFO }

/** Phone controls that need no API key and no special permission. Implemented by `AndroidDeviceActions`. */
interface DeviceActions {
    fun battery(): BatteryInfo
    fun storage(): StorageInfo
    fun memory(): MemoryInfo

    /** Returns false when the device has no flash. */
    fun setTorch(on: Boolean): Boolean

    /** Media volume. [percent] null = step up/down by [step]; returns the new level in percent. */
    fun setVolume(percent: Int? = null, step: Int = 0): Int
    fun mute(): Int

    fun openSettings(panel: SettingsPanel): Boolean

    /** Hands off to the system clock app. */
    fun setTimer(seconds: Int, label: String?): Boolean
    fun setAlarm(hour: Int, minute: Int, label: String?): Boolean
}
