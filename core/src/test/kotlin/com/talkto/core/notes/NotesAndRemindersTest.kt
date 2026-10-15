package com.talkto.core.notes

import com.google.common.truth.Truth.assertThat
import com.talkto.core.error.TalktoError
import com.talkto.core.pet.LifeStage
import com.talkto.core.pet.Progression
import com.talkto.core.reminders.Reminder
import com.talkto.core.reminders.ReminderScheduler
import com.talkto.core.reminders.ReminderStore
import com.talkto.core.reminders.RemindersRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class InMemoryNoteStore : NoteStore {
    private val notes = mutableListOf<Note>()
    override suspend fun insert(note: Note): Long {
        val id = (notes.maxOfOrNull { it.id } ?: 0) + 1
        notes += note.copy(id = id); return id
    }
    override suspend fun all() = notes.sortedByDescending { it.createdAtMs }
    override suspend fun delete(id: Long) = notes.removeIf { it.id == id }
}

class InMemoryReminderStore : ReminderStore {
    val items = mutableListOf<Reminder>()
    override suspend fun insert(reminder: Reminder): Long {
        val id = (items.maxOfOrNull { it.id } ?: 0) + 1
        items += reminder.copy(id = id); return id
    }
    override suspend fun get(id: Long) = items.firstOrNull { it.id == id }
    override suspend fun pending() = items.filter { !it.done }.sortedBy { it.atMs }
    override suspend fun markDone(id: Long) {
        items.replaceAll { if (it.id == id) it.copy(done = true) else it }
    }
    override suspend fun delete(id: Long) = items.removeIf { it.id == id }
}

class RecordingScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(reminder: Reminder) { scheduled += reminder.id }
    override fun cancel(id: Long) { cancelled += id }
}

class NotesRepositoryTest {
    private var now = 1_000L
    private val repo = NotesRepository(InMemoryNoteStore(), clock = { now++ })

    @Test fun `add, list newest first, search and delete by position`() = runTest {
        repo.add("купи мляко")
        repo.add("  парола за wifi: на кутията  ")
        assertThat(repo.list().map { it.text }).containsExactly("парола за wifi: на кутията", "купи мляко").inOrder()
        assertThat(repo.search("МЛЯКО").single().text).isEqualTo("купи мляко")
        assertThat(repo.deleteAt(2).text).isEqualTo("купи мляко")
        assertThat(repo.list()).hasSize(1)
    }

    @Test fun `empty and oversized notes are rejected`() = runTest {
        assertThat(runCatching { repo.add("   ") }.exceptionOrNull()).isInstanceOf(TalktoError.InvalidInput::class.java)
        assertThat(runCatching { repo.add("x".repeat(3000)) }.exceptionOrNull()).isInstanceOf(TalktoError.InvalidInput::class.java)
        assertThat(runCatching { repo.deleteAt(5) }.exceptionOrNull()).isInstanceOf(TalktoError.NotFound::class.java)
    }
}

class RemindersRepositoryTest {
    private var now = 10_000_000L
    private val store = InMemoryReminderStore()
    private val scheduler = RecordingScheduler()
    private val repo = RemindersRepository(store, scheduler, clock = { now })

    @Test fun `adding schedules, cancelling unschedules`() = runTest {
        val r = repo.add("звънни на мама", now + 60_000)
        assertThat(scheduler.scheduled).containsExactly(r.id)
        repo.cancelAt(1)
        assertThat(scheduler.cancelled).containsExactly(r.id)
        assertThat(repo.pending()).isEmpty()
    }

    @Test fun `past and far-future times are refused`() = runTest {
        assertThat(runCatching { repo.add("x", now - 1) }.exceptionOrNull()).isInstanceOf(TalktoError.InvalidInput::class.java)
        assertThat(runCatching { repo.add("x", now + 400L * 86_400_000) }.exceptionOrNull()).isInstanceOf(TalktoError.InvalidInput::class.java)
    }

    @Test fun `fired marks done once`() = runTest {
        val r = repo.add("лекарство", now + 1_000)
        assertThat(repo.fired(r.id)?.text).isEqualTo("лекарство")
        assertThat(repo.fired(r.id)).isNull()
        assertThat(repo.pending()).isEmpty()
    }

    @Test fun `reboot re-arms future reminders and reports missed ones`() = runTest {
        val future = repo.add("future", now + 3_600_000)
        val soon = repo.add("soon", now + 1_000)
        scheduler.scheduled.clear()
        now += 10_000 // phone was off; "soon" passed
        val missed = repo.rescheduleAll()
        assertThat(missed.map { it.id }).containsExactly(soon.id)
        assertThat(scheduler.scheduled).containsExactly(future.id)
    }
}

class ProgressionTest {
    @Test fun `levels are quadratic and consistent`() {
        assertThat(Progression.levelFor(0)).isEqualTo(1)
        assertThat(Progression.levelFor(19)).isEqualTo(1)
        assertThat(Progression.levelFor(20)).isEqualTo(2)
        assertThat(Progression.levelFor(199)).isEqualTo(4)
        assertThat(Progression.levelFor(200)).isEqualTo(5)
        assertThat(Progression.levelFor(3800)).isEqualTo(20)
        (1..40).forEach { l -> assertThat(Progression.levelFor(Progression.xpForLevel(l))).isEqualTo(l) }
        assertThat(Progression.progress(30)).isWithin(0.001f).of(0.25f)
    }

    @Test fun `stages follow levels`() {
        assertThat(Progression.stageFor(1)).isEqualTo(LifeStage.EGG)
        assertThat(Progression.stageFor(4)).isEqualTo(LifeStage.BABY)
        assertThat(Progression.stageFor(9)).isEqualTo(LifeStage.CHILD)
        assertThat(Progression.stageFor(10)).isEqualTo(LifeStage.TEEN)
        assertThat(Progression.stageFor(50)).isEqualTo(LifeStage.ADULT)
    }

    @Test fun `daily streak`() {
        val d = LocalDate.of(2026, 9, 28)
        assertThat(Progression.visit(null, 0, d)).isEqualTo(1 to true)
        assertThat(Progression.visit(d, 3, d)).isEqualTo(3 to false)
        assertThat(Progression.visit(d.minusDays(1), 3, d)).isEqualTo(4 to true)
        assertThat(Progression.visit(d.minusDays(3), 3, d)).isEqualTo(1 to true)
        assertThat(Progression.dailyBonus(30)).isEqualTo(70)
    }
}
