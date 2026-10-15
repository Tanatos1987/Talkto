package com.talkto.core.memory

import kotlinx.serialization.Serializable

enum class ActionType { FILE_MOVE, FILE_COPY, FILE_DELETE, FILE_ORGANIZE, FILE_SEARCH, APP_LAUNCH, APP_TERMINATE, AVATAR }

/** One thing the user asked ZnaiKo to do. Persisted by [ActionLogStore] (Room on Android). */
data class ActionRecord(
    val id: Long = 0,
    val type: ActionType,
    /** File name, package name, etc. */
    val subject: String,
    /** Source folder for file actions. */
    val source: String? = null,
    /** Destination folder for file actions. */
    val target: String? = null,
    val timestampMs: Long,
    val success: Boolean = true,
)

@Serializable
data class Habit(
    /** Stable identity; used to dismiss a suggestion forever. */
    val key: String,
    val description: String,
    val suggestion: String,
    val occurrences: Int,
    val lastSeenMs: Long,
    /** 0..1, recency-weighted. */
    val confidence: Double,
)

/** Storage abstraction so the learning logic stays testable without SQLite. */
interface ActionLogStore {
    suspend fun insert(record: ActionRecord): Long
    suspend fun since(fromMs: Long): List<ActionRecord>
    suspend fun deleteOlderThan(cutoffMs: Long): Int
    suspend fun dismissedHabitKeys(): Set<String>
    suspend fun dismissHabit(key: String)
}
