package com.talkto.core.reminders

import com.talkto.core.error.TalktoError
import kotlinx.serialization.Serializable

@Serializable
data class Reminder(val id: Long = 0, val text: String, val atMs: Long, val done: Boolean = false)

interface ReminderStore {
    suspend fun insert(reminder: Reminder): Long
    suspend fun get(id: Long): Reminder?
    /** Not yet fired, soonest first. */
    suspend fun pending(): List<Reminder>
    suspend fun markDone(id: Long)
    suspend fun delete(id: Long): Boolean
}

/** Delivers a reminder at its time. AlarmManager on Android. */
interface ReminderScheduler {
    fun schedule(reminder: Reminder)
    fun cancel(id: Long)
}

class RemindersRepository(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun add(text: String, atMs: Long): Reminder {
        val clean = text.trim().ifEmpty { throw TalktoError.InvalidInput("Reminder text is empty") }.take(500)
        val now = clock()
        if (atMs <= now) throw TalktoError.InvalidInput("Reminder time is in the past")
        if (atMs - now > MAX_AHEAD_MS) throw TalktoError.InvalidInput("Reminders can be at most one year ahead")
        val r = Reminder(text = clean, atMs = atMs)
        val saved = r.copy(id = store.insert(r))
        scheduler.schedule(saved)
        return saved
    }

    suspend fun pending(): List<Reminder> = store.pending()

    suspend fun cancel(id: Long): Reminder {
        val r = store.get(id) ?: throw TalktoError.NotFound("reminder $id")
        scheduler.cancel(id)
        store.delete(id)
        return r
    }

    /** Cancels by the 1-based position in [pending]. */
    suspend fun cancelAt(position: Int): Reminder {
        val r = pending().getOrNull(position - 1) ?: throw TalktoError.NotFound("reminder #$position")
        return cancel(r.id)
    }

    /** Called when the alarm fires; returns the reminder to show, or null if it was cancelled meanwhile. */
    suspend fun fired(id: Long): Reminder? {
        val r = store.get(id)?.takeIf { !it.done } ?: return null
        store.markDone(id)
        return r
    }

    /** After a reboot or app update the OS forgets alarms: re-arm future ones, deliver missed ones now. */
    suspend fun rescheduleAll(): List<Reminder> {
        val now = clock()
        val missed = ArrayList<Reminder>()
        store.pending().forEach { r ->
            if (r.atMs > now) scheduler.schedule(r) else missed += r
        }
        return missed
    }

    companion object {
        const val MAX_AHEAD_MS = 366L * 86_400_000L
    }
}
