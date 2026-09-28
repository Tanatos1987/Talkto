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
}
