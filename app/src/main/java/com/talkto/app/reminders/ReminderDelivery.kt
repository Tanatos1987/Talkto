package com.talkto.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.talkto.app.MainActivity
import com.talkto.app.R
import com.talkto.app.TalktoApp
import com.talkto.core.reminders.Reminder
import com.talkto.core.reminders.ReminderScheduler
import kotlinx.coroutines.launch

/**
 * AlarmManager-backed delivery. Exact alarms when the user allowed them (Android 12+ asks for
 * SCHEDULE_EXACT_ALARM), otherwise an inexact alarm that Doze may delay by a few minutes.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: Reminder) {
        val pi = pendingIntent(context, reminder.id, reminder.text)
        // minSdk is 30; the exact-alarm permission check exists from Android 12 (API 31).
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMs, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMs, pi)
        }
    }

    override fun cancel(id: Long) {
        alarms.cancel(pendingIntent(context, id, ""))
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
        private const val CHANNEL_ID = "talkto_reminders"

        // Request code = reminder id, so each reminder has its own PendingIntent and cancel() finds it.
        fun pendingIntent(context: Context, id: Long, text: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun notify(context: Context, reminder: Reminder) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                )
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.notif_reminder_title))
                .setContentText(reminder.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            // POST_NOTIFICATIONS may be denied; the reminder is still marked done so it does not fire again.
            runCatching { nm.notify(NOTIFICATION_BASE + reminder.id.toInt(), n) }
        }

        private const val NOTIFICATION_BASE = 10_000
    }
}

/** Fires at the reminder time. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AndroidReminderScheduler.EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        val container = (context.applicationContext as TalktoApp).container
        container.appScope.launch {
            try {
                container.reminders.fired(id)?.let { AndroidReminderScheduler.notify(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** The OS forgets alarms on reboot and on app update; put them back and deliver the ones missed while off. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as TalktoApp).container
        container.appScope.launch {
            try {
                container.reminders.rescheduleAll().forEach { missed ->
                    container.reminders.fired(missed.id)?.let { AndroidReminderScheduler.notify(context, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
