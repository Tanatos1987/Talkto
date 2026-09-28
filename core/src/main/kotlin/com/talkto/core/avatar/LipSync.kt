package com.talkto.core.avatar

/**
 * Mouth shapes. A reduced Preston-Blair set: enough for a convincing 2D mouth without
 * shipping per-phoneme artwork.
 */
enum class Viseme(val openness: Float, val width: Float, val round: Float) {
    REST(0.00f, 0.50f, 0.0f),
    MBP(0.00f, 0.45f, 0.0f), // lips pressed: m, b, p
    FV(0.12f, 0.55f, 0.0f), // lower lip under teeth: f, v
    E(0.35f, 0.80f, 0.0f), // wide: e, i
    AI(0.85f, 0.65f, 0.0f), // open: a
    O(0.65f, 0.40f, 0.8f), // round: o
    U(0.35f, 0.30f, 1.0f), // pursed: u, w
    L(0.45f, 0.55f, 0.0f), // tongue: l, t, d, n
    CONS(0.25f, 0.55f, 0.0f), // other consonants
}

data class VisemeFrame(val viseme: Viseme, val startMs: Long, val durationMs: Long)

/**
 * Text -> viseme timeline. Handles Latin and Bulgarian Cyrillic.
 *
 * Android TTS reports word boundaries (`UtteranceProgressListener.onRangeStart`), not phonemes,
 * so the engine calls [planWord] every time a word starts and spreads its letters across the
 * expected word duration. When the engine gives no boundaries, [planUtterance] estimates the whole line.
 */
class VisemePlanner(
    /** Average speaking rate in characters per second at speech-rate 1.0. */
    private val charsPerSecond: Float = 14f,
) {
    fun planWord(word: String, speechRate: Float = 1f): List<VisemeFrame> {
        val letters = word.lowercase().filter { it.isLetter() }
        if (letters.isEmpty()) return emptyList()
        val perChar = (1000f / (charsPerSecond * speechRate.coerceIn(0.25f, 4f))).toLong().coerceAtLeast(25)
        val frames = ArrayList<VisemeFrame>()
        var t = 0L
        letters.forEach { c ->
            val v = visemeFor(c)
            val last = frames.lastOrNull()
            // Merge repeated shapes, so "ooo" or "мм" is one sustained pose instead of a jitter.
            if (last != null && last.viseme == v) {
                frames[frames.lastIndex] = last.copy(durationMs = last.durationMs + perChar)
            } else {
                frames += VisemeFrame(v, t, perChar)
            }
            t += perChar
        }
        return frames
    }

    fun planUtterance(text: String, speechRate: Float = 1f): List<VisemeFrame> {
        val out = ArrayList<VisemeFrame>()
        var offset = 0L
        val pauseMs = (140 / speechRate.coerceIn(0.25f, 4f)).toLong()
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
            val frames = planWord(token, speechRate)
            frames.forEach { out += it.copy(startMs = it.startMs + offset) }
            offset += frames.sumOf { it.durationMs }
            val punctuationPause = if (token.last() in ".,!?;:") pauseMs * 2 else pauseMs
            out += VisemeFrame(Viseme.REST, offset, punctuationPause)
            offset += punctuationPause
        }
        return out
    }

    companion object {
        fun visemeFor(c: Char): Viseme = when (c) {
            'a', 'а', 'я' -> Viseme.AI
            'e', 'i', 'y', 'е', 'и', 'й', 'ь', 'ъ' -> Viseme.E
            'o', 'о', 'ю' -> Viseme.O
            'u', 'w', 'q', 'у', 'щ' -> Viseme.U
            'm', 'b', 'p', 'м', 'б', 'п' -> Viseme.MBP
            'f', 'v', 'ф', 'в' -> Viseme.FV
            'l', 't', 'd', 'n', 'л', 'т', 'д', 'н' -> Viseme.L
            else -> Viseme.CONS
        }
    }
}
