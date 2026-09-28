package com.talkto.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.talkto.core.memory.ActionLogStore
import com.talkto.core.memory.ActionRecord
import com.talkto.core.memory.ActionType

@Entity(
    tableName = "action_log",
    indices = [Index("timestamp_ms"), Index(value = ["type", "subject"])],
)
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val subject: String,
    val source: String?,
    val target: String?,
    @androidx.room.ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    val success: Boolean,
)

@Entity(tableName = "dismissed_habit")
data class DismissedHabitEntity(
    @PrimaryKey val key: String,
    @androidx.room.ColumnInfo(name = "dismissed_at_ms") val dismissedAtMs: Long,
)

@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(entity: ActionLogEntity): Long

    @Query("SELECT * FROM action_log WHERE timestamp_ms >= :fromMs ORDER BY timestamp_ms ASC")
    suspend fun since(fromMs: Long): List<ActionLogEntity>

    @Query("DELETE FROM action_log WHERE timestamp_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM action_log")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismiss(entity: DismissedHabitEntity)

    @Query("SELECT `key` FROM dismissed_habit")
    suspend fun dismissedKeys(): List<String>
}

@Database(entities = [ActionLogEntity::class, DismissedHabitEntity::class], version = 1, exportSchema = true)
abstract class TalktoDatabase : RoomDatabase() {
    abstract fun actionLog(): ActionLogDao

    companion object {
        fun create(context: Context): TalktoDatabase =
            Room.databaseBuilder(context.applicationContext, TalktoDatabase::class.java, "talkto.db")
                // Memory is a cache of habits; if a future schema change has no migration, relearning is acceptable.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

/** Bridges the Room DAO to the pure-Kotlin [ActionLogStore] used by `MemoryRepository`. */
class RoomActionLogStore(private val dao: ActionLogDao, private val clock: () -> Long = System::currentTimeMillis) : ActionLogStore {

    override suspend fun insert(record: ActionRecord): Long = dao.insert(
        ActionLogEntity(
            type = record.type.name,
            subject = record.subject.take(512),
            source = record.source?.take(1024),
            target = record.target?.take(1024),
            timestampMs = record.timestampMs,
            success = record.success,
        ),
    )

    override suspend fun since(fromMs: Long): List<ActionRecord> = dao.since(fromMs).mapNotNull { e ->
        val type = runCatching { ActionType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
        ActionRecord(e.id, type, e.subject, e.source, e.target, e.timestampMs, e.success)
    }

    override suspend fun deleteOlderThan(cutoffMs: Long): Int = dao.deleteOlderThan(cutoffMs)

    override suspend fun dismissedHabitKeys(): Set<String> = dao.dismissedKeys().toSet()

    override suspend fun dismissHabit(key: String) = dao.dismiss(DismissedHabitEntity(key, clock()))
}
