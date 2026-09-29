package com.talkto.app.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.talkto.app.MainActivity
import com.talkto.app.R
import com.talkto.app.TalktoApp
import com.talkto.app.i18n.LanguageRepository
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that executes agent turns.
 *
 * Why a service: a turn can outlive the Activity. Closing an app through the Accessibility route
 * opens Recents (ZnaiKo leaves the foreground), and long searches or organise runs take seconds.
 * The service keeps the process at foreground priority until the last queued turn is done, then stops.
 */
class AgentService : LifecycleService() {

    private val running = AtomicInteger(0)
    private val container get() = (application as TalktoApp).container

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageRepository.localized(base, LanguageRepository.read(base)))
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Must be called promptly after startForegroundService, before any other work.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val text = intent?.takeIf { it.action == ACTION_SUBMIT }?.getStringExtra(EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            stopIfIdle(); return START_NOT_STICKY
        }
        running.incrementAndGet()
        lifecycleScope.launch(container.errors.coroutineHandler) {
            try {
                container.agentSession.run(text)
            } finally {
                if (running.decrementAndGet() == 0) stopIfIdle()
            }
        }
        // A turn interrupted by process death is not replayed: repeating a file operation blindly is worse than asking again.
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (running.get() == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.notif_agent_title))
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(openAppIntent(this))
        .build()

    companion object {
        const val ACTION_SUBMIT = "com.talkto.app.action.SUBMIT"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "talkto_agent"
        private const val NOTIFICATION_ID = 7001
        private const val CONFIRM_NOTIFICATION_ID = 7002

        fun submit(context: Context, text: String) {
            val intent = Intent(context, AgentService::class.java).setAction(ACTION_SUBMIT).putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, intent)
        }

        fun ensureChannel(base: Context) {
            val context = LanguageRepository.localized(base, LanguageRepository.read(base))
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_agent), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }

        /** Shown when a tool waits for a yes/no while ZnaiKo is not on screen. */
        fun notifyConfirmationPending(base: Context) {
            ensureChannel(base)
            val context = LanguageRepository.localized(base, LanguageRepository.read(base))
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notif_confirm_title))
                .setContentText(context.getString(R.string.notif_confirm_body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppIntent(context))
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(CONFIRM_NOTIFICATION_ID, n) }
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
