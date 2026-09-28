package com.talkto.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.TamagotchiScreen
import com.talkto.app.ui.theme.TalktoTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> vm.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TalktoTheme {
                TamagotchiScreen(vm)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from system settings: All Files Access or the accessibility toggle may have changed.
        vm.onVisible(true)
    }

    override fun onPause() {
        vm.onVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        super.onDestroy()
    }
}
