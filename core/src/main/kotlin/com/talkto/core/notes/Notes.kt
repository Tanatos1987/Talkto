package com.talkto.core.notes

import com.talkto.core.error.TalktoError
import kotlinx.serialization.Serializable

@Serializable
data class Note(val id: Long = 0, val text: String, val createdAtMs: Long)

/** Room on Android, in-memory in tests. */
interface NoteStore {
    suspend fun insert(note: Note): Long
    /** Newest first. */
    suspend fun all(): List<Note>
    suspend fun delete(id: Long): Boolean
}

class NotesRepository(private val store: NoteStore, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun add(text: String): Note {
        val clean = text.trim()
        if (clean.isEmpty()) throw TalktoError.InvalidInput("Empty note")
        if (clean.length > MAX_LENGTH) throw TalktoError.InvalidInput("Note is longer than $MAX_LENGTH characters")
        val note = Note(text = clean, createdAtMs = clock())
        return note.copy(id = store.insert(note))
    }

    suspend fun list(): List<Note> = store.all()

    suspend fun search(query: String): List<Note> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) list() else list().filter { q in it.text.lowercase() }
    }

    suspend fun delete(id: Long) {
        if (!store.delete(id)) throw TalktoError.NotFound("note $id")
    }

    /** Deletes by the 1-based position shown to the user (newest first). */
    suspend fun deleteAt(position: Int): Note {
        val note = list().getOrNull(position - 1) ?: throw TalktoError.NotFound("note #$position")
        delete(note.id)
        return note
    }

    companion object {
        const val MAX_LENGTH = 2_000
    }
}
