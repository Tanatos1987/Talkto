package com.talkto.core.memory

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class InMemoryActionLogStore : ActionLogStore {
    val records = mutableListOf<ActionRecord>()
    private val dismissed = mutableSetOf<String>()
    override suspend fun insert(record: ActionRecord): Long {
        records += record.copy(id = records.size + 1L); return records.size.toLong()
    }
    override suspend fun since(fromMs: Long) = records.filter { it.timestampMs >= fromMs }
    override suspend fun deleteOlderThan(cutoffMs: Long): Int {
        val before = records.size; records.removeAll { it.timestampMs < cutoffMs }; return before - records.size
    }
    override suspend fun dismissedHabitKeys(): Set<String> = dismissed
    override suspend fun dismissHabit(key: String) { dismissed += key }
}

class MemoryRepositoryTest {

    private val zone = ZoneOffset.UTC
    // Sunday 2026-09-27 20:00 UTC
    private val now = LocalDateTime.of(2026, 9, 27, 20, 0).toInstant(zone).toEpochMilli()
    private val day = MemoryRepository.DAY_MS
    private val store = InMemoryActionLogStore()
    private val repo = MemoryRepository(store, clock = { now }, zone = zone)

    private suspend fun move(file: String, from: String, to: String, at: Long) =
        repo.record(ActionRecord(type = ActionType.FILE_MOVE, subject = file, source = from, target = to, timestampMs = at))

    private suspend fun launch(pkg: String, at: Long) =
        repo.record(ActionRecord(type = ActionType.APP_LAUNCH, subject = pkg, timestampMs = at))

    @Test fun `no history means an empty prompt`() = runTest {
        assertThat(repo.buildMemoryPrompt()).isEmpty()
    }

    @Test fun `repeated moves between the same folders become a habit`() = runTest {
        val wa = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"
        val fam = "/storage/emulated/0/Pictures/Family"
        // four Sundays in a row
        (0..3).forEach { w -> move("IMG-2026090${w}-WA0001.jpg", wa, "$fam/", now - w * 7 * day) }

        val habit = repo.detectHabits().single()
        assertThat(habit.key).isEqualTo("route:file_move:image:$wa->$fam")
        assertThat(habit.occurrences).isEqualTo(4)
        assertThat(habit.description).contains("usually on Sunday")
        assertThat(habit.suggestion).contains("every Sunday")
        assertThat(habit.confidence).isGreaterThan(0.6)

        val prompt = repo.buildMemoryPrompt()
        assertThat(prompt).startsWith("<learned_habits>")
        assertThat(prompt).contains(habit.key)
        assertThat(prompt).endsWith("</learned_habits>")
    }

    @Test fun `below threshold is not a habit`() = runTest {
        move("a.pdf", "/d/Download", "/d/Documents", now - day)
        move("b.pdf", "/d/Download", "/d/Documents", now - 2 * day)
        assertThat(repo.detectHabits()).isEmpty()
    }

    @Test fun `different file kinds are separate habits`() = runTest {
        repeat(3) { move("x$it.pdf", "/d/Download", "/d/Documents", now - it * day) }
        repeat(3) { move("x$it.jpg", "/d/Download", "/d/Documents", now - it * day) }
        assertThat(repo.detectHabits().map { it.key }).containsExactly(
            "route:file_move:document:/d/Download->/d/Documents",
            "route:file_move:image:/d/Download->/d/Documents",
        )
    }

    @Test fun `old evidence decays below the confidence bar`() = runTest {
        repeat(3) { move("x$it.pdf", "/d/Download", "/d/Documents", now - (40 + it) * day) }
        val decayed = MemoryRepository(store, clock = { now }, zone = zone)
        assertThat(decayed.detectHabits()).isEmpty()
        assertThat(decayed.score(store.records, now)).isLessThan(0.35)
    }

    @Test fun `failed actions do not count`() = runTest {
        repeat(4) {
            repo.record(ActionRecord(type = ActionType.FILE_MOVE, subject = "a.pdf", source = "/a", target = "/b", timestampMs = now - it * day, success = false))
        }
        assertThat(repo.detectHabits()).isEmpty()
    }

    @Test fun `dismissed habits are never suggested again`() = runTest {
        repeat(4) { move("x$it.pdf", "/d/Download", "/d/Documents", now - it * day) }
        val key = repo.detectHabits().single().key
        repo.dismiss(key)
        assertThat(repo.detectHabits()).isEmpty()
        assertThat(repo.buildMemoryPrompt()).isEmpty()
    }

    @Test fun `morning launches on weekdays are learned separately from weekends`() = runTest {
        // Mon-Thu of the week before `now`, 07:10 UTC
        (1..4).forEach { d ->
            val t = LocalDateTime.of(2026, 9, 20 + d, 7, 10).toInstant(zone).toEpochMilli()
            launch("com.spotify.music", t)
        }
        val keys = repo.detectHabits().map { it.key }
        assertThat(keys).contains("launch:com.spotify.music:7:wd")
    }

    @Test fun `app sequences are detected`() = runTest {
        repeat(3) { i ->
            val t = now - i * day
            launch("com.google.android.gm", t)
            launch("com.slack", t + 60_000)
        }
        assertThat(repo.detectHabits().map { it.key }).contains("sequence:com.google.android.gm->com.slack")
    }

    @Test fun `prune removes records past retention`() = runTest {
        move("a.pdf", "/a", "/b", now - 400 * day)
        move("b.pdf", "/a", "/b", now - day)
        assertThat(repo.prune()).isEqualTo(1)
        assertThat(store.records).hasSize(1)
    }

    @Test fun `prompt is capped`() = runTest {
        repeat(12) { folder -> repeat(3) { move("f$it.pdf", "/src$folder", "/dst$folder", now - it * day) } }
        val lines = repo.buildMemoryPrompt().lines().filter { it.startsWith("- [") }
        assertThat(lines).hasSize(8)
    }
}
