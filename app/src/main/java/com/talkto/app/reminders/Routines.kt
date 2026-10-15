package com.talkto.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.talkto.app.MainActivity
import com.talkto.app.R
import com.talkto.app.TalktoApp
import com.talkto.app.i18n.LanguageRepository
import com.talkto.core.routine.RoutineKind
import com.talkto.core.routine.Routines
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The morning and evening notes from ZnaiKo, once a day at the times a parent chose. Inexact alarms (a few minutes
 * either way are fine for "time to brush your teeth"), so no exact-alarm permission is needed.
 */
class RoutineScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Arms (or disarms, for [Routines.OFF]) both routines. */
    fun apply(morningMinute: Int, eveningMinute: Int) {
        set(RoutineKind.MORNING, morningMinute)
        set(RoutineKind.EVENING, eveningMinute)
    }

    fun set(kind: RoutineKind, minuteOfDay: Int) {
        val pi = pendingIntent(context, kind)
        alarms.cancel(pi)
        if (minuteOfDay < 0) return
        val at = Routines.nextAt(ZonedDateTime.now(), minuteOfDay).toInstant().toEpochMilli()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
    }

    companion object {
        const val EXTRA_KIND = "routine_kind"
        private const val CHANNEL_ID = "talkto_routines"
        private const val WINDOW_MS = 10 * 60_000L

        fun pendingIntent(context: Context, kind: RoutineKind): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + kind.ordinal,
            Intent(context, RoutineReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun notify(base: Context, kind: RoutineKind, text: String) {
            val context = LanguageRepository.localized(base, LanguageRepository.read(base))
            val nm = context.getSystemService(NotificationManager::class.java)
            val lang = LanguageRepository.read(base)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, lang.pick("Сутрин и вечер със Знайко", "Mornings and evenings with ZnaiKo"), NotificationManager.IMPORTANCE_DEFAULT),
            )
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = if (kind == RoutineKind.MORNING) lang.pick("🌅 Добро утро!", "🌅 Good morning!") else lang.pick("🌙 Лека нощ!", "🌙 Good night!")
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_znaiko)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { nm.notify(NOTIFICATION_BASE + kind.ordinal, n) }
        }

        private const val REQUEST_BASE = 900_000
        private const val NOTIFICATION_BASE = 9_000
    }
}

/** A routine is due: ZnaiKo's note, then the same time tomorrow. */
class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { RoutineKind.valueOf(intent.getStringExtra(RoutineScheduler.EXTRA_KIND).orEmpty()) }.getOrNull() ?: return
        val pending = goAsync()
        val container = (context.applicationContext as TalktoApp).container
        container.appScope.launch {
            try {
                val s = container.settings.awaitLoaded()
                val minute = if (kind == RoutineKind.MORNING) s.morningMinute else s.eveningMinute
                if (minute >= 0) {
                    val name = runCatching { container.profile.get("name") }.getOrNull()
                    RoutineScheduler.notify(context, kind, Routines.line(kind, LocalDate.now(), container.language.current, name))
                    container.routines.set(kind, minute)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
