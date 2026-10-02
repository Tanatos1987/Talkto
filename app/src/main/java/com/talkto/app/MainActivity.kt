package com.talkto.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.talkto.app.i18n.LanguageRepository
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.TamagotchiScreen
import com.talkto.app.ui.theme.TalktoTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> vm.refreshPermissions() }

    /** Screens follow ZnaiKo's language, not only the phone's (values-en for English). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageRepository.localized(newBase, LanguageRepository.read(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TalktoTheme {
                // The Surface sets the content colour, so icons and labels are light on the dark shell (not black).
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    TamagotchiScreen(vm)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }
        // A new language: rebuild the screen with its resources. The ViewModel (and any game) survives.
        lifecycleScope.launch {
            (application as TalktoApp).container.language.lang.drop(1).collect { recreate() }
        }
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
