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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.talkto.core.memory.ActionLogStore
import com.talkto.core.memory.ActionRecord
import com.talkto.core.history.HistoryStore
import com.talkto.core.history.Speaker
import com.talkto.core.history.Utterance
import com.talkto.core.memory.ActionType
import com.talkto.core.notes.Note
import com.talkto.core.notes.NoteStore
import com.talkto.core.profile.Fact
import com.talkto.core.profile.ProfileStore
import com.talkto.core.reminders.Reminder
import com.talkto.core.reminders.ReminderStore

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

@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @androidx.room.ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

@Entity(tableName = "reminder", indices = [Index("at_ms")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @androidx.room.ColumnInfo(name = "at_ms") val atMs: Long,
    val done: Boolean,
)

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(e: NoteEntity): Long

    @Query("SELECT * FROM note ORDER BY created_at_ms DESC, id DESC")
    suspend fun all(): List<NoteEntity>

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(e: ReminderEntity): Long

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE done = 0 ORDER BY at_ms ASC")
    suspend fun pending(): List<ReminderEntity>

    @Query("UPDATE reminder SET done = 1 WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Entity(tableName = "user_fact")
data class UserFactEntity(
    @PrimaryKey val key: String,
    val value: String,
    @androidx.room.ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    val source: String,
)

@Entity(tableName = "utterance", indices = [Index("at_ms")])
data class UtteranceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speaker: String,
    val text: String,
    @androidx.room.ColumnInfo(name = "at_ms") val atMs: Long,
    val mode: String,
)

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: UserFactEntity)

    @Query("SELECT * FROM user_fact")
    suspend fun all(): List<UserFactEntity>

    @Query("DELETE FROM user_fact WHERE `key` = :key")
    suspend fun delete(key: String): Int
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(e: UtteranceEntity): Long

    /** Newest [limit] rows; the store reverses them to oldest-first. */
    @Query("SELECT * FROM utterance ORDER BY at_ms DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<UtteranceEntity>

    @Query("SELECT * FROM utterance WHERE text LIKE '%' || :query || '%' ORDER BY at_ms DESC, id DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<UtteranceEntity>

    @Query("SELECT COUNT(*) FROM utterance")
    suspend fun count(): Int

    @Query("DELETE FROM utterance")
    suspend fun clear()

    @Query("DELETE FROM utterance WHERE at_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}

@Database(
    entities = [
        ActionLogEntity::class, DismissedHabitEntity::class, NoteEntity::class, ReminderEntity::class,
        UserFactEntity::class, UtteranceEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TalktoDatabase : RoomDatabase() {
    abstract fun actionLog(): ActionLogDao
    abstract fun notes(): NoteDao
    abstract fun reminders(): ReminderDao
    abstract fun profile(): ProfileDao
    abstract fun history(): HistoryDao

    companion object {
        /** v2 adds notes and reminders. User data from here on, so upgrades migrate instead of wiping. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `note` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `created_at_ms` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reminder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `at_ms` INTEGER NOT NULL, `done` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_at_ms` ON `reminder` (`at_ms`)")
            }
        }

        /** v3 adds the user profile and the conversation log. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_fact` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updated_at_ms` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `utterance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `speaker` TEXT NOT NULL, `text` TEXT NOT NULL, `at_ms` INTEGER NOT NULL, `mode` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_utterance_at_ms` ON `utterance` (`at_ms`)")
            }
        }

        fun create(context: Context): TalktoDatabase =
            Room.databaseBuilder(context.applicationContext, TalktoDatabase::class.java, "talkto.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // Only a downgrade (sideloading an older APK) may wipe; upgrades always migrate.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}

class RoomNoteStore(private val dao: NoteDao) : NoteStore {
    override suspend fun insert(note: Note): Long = dao.insert(NoteEntity(text = note.text, createdAtMs = note.createdAtMs))
    override suspend fun all(): List<Note> = dao.all().map { Note(it.id, it.text, it.createdAtMs) }
    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0
}

class RoomReminderStore(private val dao: ReminderDao) : ReminderStore {
    override suspend fun insert(reminder: Reminder): Long =
        dao.insert(ReminderEntity(text = reminder.text, atMs = reminder.atMs, done = reminder.done))
    override suspend fun get(id: Long): Reminder? = dao.get(id)?.toModel()
    override suspend fun pending(): List<Reminder> = dao.pending().map { it.toModel() }
    override suspend fun markDone(id: Long) = dao.markDone(id)
    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0
    private fun ReminderEntity.toModel() = Reminder(id, text, atMs, done)
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

class RoomProfileStore(private val dao: ProfileDao) : ProfileStore {
    override suspend fun upsert(fact: Fact) = dao.upsert(UserFactEntity(fact.key, fact.value, fact.updatedAtMs, fact.source))
    override suspend fun all(): List<Fact> = dao.all().map { Fact(it.key, it.value, it.updatedAtMs, it.source) }
    override suspend fun delete(key: String): Boolean = dao.delete(key) > 0
}

class RoomHistoryStore(private val dao: HistoryDao) : HistoryStore {
    override suspend fun insert(u: Utterance): Long = dao.insert(UtteranceEntity(speaker = u.speaker.name, text = u.text, atMs = u.atMs, mode = u.mode))
    override suspend fun recent(limit: Int): List<Utterance> = dao.latest(limit).asReversed().mapNotNull { it.toModel() }
    // LIKE with user text: % and _ are wildcards in SQL, so escape-free matching is good enough for a search box.
    override suspend fun search(query: String, limit: Int): List<Utterance> = dao.search(query, limit).mapNotNull { it.toModel() }
    override suspend fun count(): Int = dao.count()
    override suspend fun clear() = dao.clear()
    override suspend fun deleteOlderThan(cutoffMs: Long): Int = dao.deleteOlderThan(cutoffMs)

    private fun UtteranceEntity.toModel(): Utterance? {
        val who = runCatching { Speaker.valueOf(speaker) }.getOrNull() ?: return null
        return Utterance(id, who, text, atMs, mode)
    }
}
