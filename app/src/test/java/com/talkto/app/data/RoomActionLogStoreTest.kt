package com.talkto.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.talkto.app.data.db.RoomActionLogStore
import com.talkto.app.data.db.TalktoDatabase
import com.talkto.core.memory.ActionRecord
import com.talkto.core.memory.ActionType
import com.talkto.core.memory.MemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** MemoryRepository on top of the real Room schema (in-memory SQLite under Robolectric). */
@RunWith(RobolectricTestRunner::class)
class RoomActionLogStoreTest {

    private lateinit var db: TalktoDatabase
    private lateinit var store: RoomActionLogStore
    private val now = 1_790_000_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TalktoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomActionLogStore(db.actionLog(), clock = { now })
    }

    @After fun tearDown() = db.close()

    @Test fun `records round-trip through Room`() = runTest {
        store.insert(ActionRecord(type = ActionType.FILE_MOVE, subject = "a.jpg", source = "/s", target = "/t", timestampMs = now))
        val back = store.since(0).single()
        assertThat(back.type).isEqualTo(ActionType.FILE_MOVE)
        assertThat(back.source).isEqualTo("/s")
        assertThat(back.id).isGreaterThan(0)
    }

    @Test fun `habit is learned from persisted history and can be dismissed`() = runTest {
        val repo = MemoryRepository(store, clock = { now })
        repeat(4) { i ->
            repo.record(ActionRecord(type = ActionType.FILE_MOVE, subject = "scan$i.pdf", source = "/sd/Download", target = "/sd/Documents/Scans", timestampMs = now - i * MemoryRepository.DAY_MS))
        }
        val habit = repo.detectHabits().single()
        assertThat(repo.buildMemoryPrompt()).contains(habit.key)

        repo.dismiss(habit.key)
        assertThat(store.dismissedHabitKeys()).containsExactly(habit.key)
        assertThat(repo.detectHabits()).isEmpty()
    }

    @Test fun `retention prune deletes old rows`() = runTest {
        store.insert(ActionRecord(type = ActionType.APP_LAUNCH, subject = "x", timestampMs = now - 400 * MemoryRepository.DAY_MS))
        store.insert(ActionRecord(type = ActionType.APP_LAUNCH, subject = "y", timestampMs = now))
        assertThat(MemoryRepository(store, clock = { now }).prune()).isEqualTo(1)
        assertThat(store.since(0).map { it.subject }).containsExactly("y")
    }

    @Test fun `unknown action types from a newer version are skipped, not crashed on`() = runTest {
        db.actionLog().insert(com.talkto.app.data.db.ActionLogEntity(type = "TELEPORT", subject = "?", source = null, target = null, timestampMs = now, success = true))
        assertThat(store.since(0)).isEmpty()
    }

    @Test fun `notes are stored newest first and deleted by id`() = runTest {
        var t = now
        val notes = com.talkto.core.notes.NotesRepository(com.talkto.app.data.db.RoomNoteStore(db.notes()), clock = { t++ })
        notes.add("първа")
        notes.add("втора")
        assertThat(notes.list().map { it.text }).containsExactly("втора", "първа").inOrder()
        notes.deleteAt(1)
        assertThat(notes.list().map { it.text }).containsExactly("първа")
    }

    @Test fun `reminders persist pending state`() = runTest {
        val store = com.talkto.app.data.db.RoomReminderStore(db.reminders())
        val a = store.insert(com.talkto.core.reminders.Reminder(text = "a", atMs = now + 2_000))
        val b = store.insert(com.talkto.core.reminders.Reminder(text = "b", atMs = now + 1_000))
        assertThat(store.pending().map { it.id }).containsExactly(b, a).inOrder()
        store.markDone(b)
        assertThat(store.pending().map { it.id }).containsExactly(a)
        assertThat(store.get(b)?.done).isTrue()
        assertThat(store.delete(a)).isTrue()
        assertThat(store.pending()).isEmpty()
    }

    @Test fun `profile facts upsert by key`() = runTest {
        val repo = com.talkto.core.profile.ProfileRepository(com.talkto.app.data.db.RoomProfileStore(db.profile()), clock = { now })
        repo.learnFrom("Казвам се Мария")
        repo.learnFrom("Казвам се Мими")
        assertThat(repo.all().single().value).isEqualTo("Мими")
        assertThat(repo.forget("name")).isEqualTo(1)
    }

    @Test fun `history keeps order, searches and clears`() = runTest {
        var t = now
        val repo = com.talkto.core.history.HistoryRepository(com.talkto.app.data.db.RoomHistoryStore(db.history()), clock = { t++ })
        repo.record(com.talkto.core.history.Speaker.USER, "първо", "offline")
        repo.record(com.talkto.core.history.Speaker.TALKTO, "второ", "offline")
        assertThat(repo.recent(10).map { it.text }).containsExactly("първо", "второ").inOrder()
        assertThat(repo.search("втор").single().speaker).isEqualTo(com.talkto.core.history.Speaker.TALKTO)
        repo.clear()
        assertThat(repo.count()).isEqualTo(0)
    }
}
