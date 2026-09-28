package com.talkto.app.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import java.nio.file.Path

/** All Files Access (MANAGE_EXTERNAL_STORAGE) helpers and the list of volumes Talkto may manage. */
object StorageAccess {

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** Opens the per-app "All files access" screen; falls back to the global list on OEMs that lack it. */
    fun openAllFilesAccess(context: Context) {
        val perApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(perApp)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun openAccessibilitySettings(context: Context) = context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** Primary shared storage first, then mounted SD cards / USB drives. */
    fun managedRoots(context: Context): List<Path> {
        val primary = Environment.getExternalStorageDirectory().toPath()
        val sm = context.getSystemService(StorageManager::class.java)
        val others = sm.storageVolumes
            .filter { !it.isPrimary && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { it.directory?.toPath() }
        return (listOf(primary) + others).distinct()
    }
}
