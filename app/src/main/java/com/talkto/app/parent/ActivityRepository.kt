package com.talkto.app.parent

import com.talkto.app.data.prefs.PetStore
import com.talkto.core.parent.Activity
import com.talkto.core.parent.ActivityLog
import com.talkto.core.parent.DayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** The day-by-day activity log, persisted as one JSON blob next to the pet. */
class ActivityRepository(
    private val store: PetStore,
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _log = MutableStateFlow(ActivityLog())
    val log: StateFlow<ActivityLog> = _log.asStateFlow()

    /** Counting starts once the saved log is in, so an early count never overwrites it. */
    @Volatile private var loaded = false

    fun start() {
        scope.launch {
            runCatching { store.activity.first() }.getOrNull()?.let { _log.value = it }
            loaded = true
        }
    }

    fun today(): Long = LocalDate.now(zone()).toEpochDay()

    fun todayActivity(): DayActivity = _log.value.on(today())

    fun record(activity: Activity, times: Int = 1) {
        if (times > 0) save { it.record(today(), activity, times) }
    }

    /** Extra minutes a parent gave for today. */
    fun addBonus(minutes: Int) = save { it.addBonus(today(), minutes) }

    private fun save(f: (ActivityLog) -> ActivityLog) {
        if (!loaded) return
        _log.update(f)
        val snapshot = _log.value
        scope.launch { runCatching { store.saveActivity(snapshot) } }
    }
}
