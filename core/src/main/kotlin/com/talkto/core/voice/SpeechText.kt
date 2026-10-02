package com.talkto.core.voice

/**
 * Cleans what the speech recogniser heard before it reaches the assistant:
 * drops the pet's name used as a wake word ("Знайко, отвори камерата"), trailing punctuation
 * the recogniser adds, and duplicated spaces. The meaning is left untouched.
 */
object SpeechText {
    private val WAKE = Regex("^(?:хей |ей |hey |ok |окей )?(?:znaiko|znayko|знайко|знай ко|зная ко|знайку|talkto|talk to|токто|толкто|толк ту|токту)[,!.]?\\s*", RegexOption.IGNORE_CASE)

    fun clean(heard: String): String = heard.trim()
        .replace(WAKE, "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', '!')
        .replaceFirstChar { it.lowercaseChar() }

    /** Best of several hypotheses: the recogniser's first one, unless it is empty after cleaning. */
    fun pick(hypotheses: List<String>): String? = hypotheses.map(::clean).firstOrNull { it.isNotBlank() }
}
