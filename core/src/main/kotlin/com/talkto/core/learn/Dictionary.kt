package com.talkto.core.learn

import com.talkto.core.i18n.Lang
import com.talkto.core.i18n.ScriptSegmenter
import java.util.Locale

/**
 * The offline dictionary: "как е куче на английски", "what is котка in English", "how do you say dog in Bulgarian",
 * "какво значи rainbow". Looks words up in [Vocabulary]; anything else is for Claude.
 */
object Dictionary {

    /** A request to translate [term]; [into] is null when the user did not say (then: the other language). */
    data class Query(val term: String, val into: Lang?)

    private const val LANGS = "английски|български|english|bulgarian"
    private val PATTERNS = listOf(
        Regex("^(?:как (?:е|се казва|ще кажа|да кажа)|какво е|как ще е) (.+?) на ($LANGS)$"),
        Regex("^(?:how do (?:you|i) say|what is|what's|how to say) (.+?) in ($LANGS)$"),
        Regex("^(.+?) на ($LANGS)$"),
        Regex("^(.+?) in ($LANGS)$"),
        Regex("^(?:какво (?:значи|означава)|what does) (.+?)(?: mean)?$"),
        Regex("^(?:преведи|превод на|translate) (.+?)(?: (?:на|in|into|to) ($LANGS))?$"),
    )

    fun parse(text: String): Query? {
        val t = text.trim().trimEnd('?', '.', '!').lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        for (p in PATTERNS) {
            val m = p.matchEntire(t) ?: continue
            val term = m.groupValues[1].trim().trim('"', '„', '“', '\'', '«', '»').trim()
            if (term.isEmpty() || term.split(' ').size > 4) return null
            val into = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("анг") || it == "english") Lang.EN else Lang.BG }
            return Query(term, into)
        }
        return null
    }

    /** Words whose Bulgarian or English form (or an accepted variant) is [term]; "кучето" finds "куче". */
    fun lookup(term: String): List<Word> {
        val lang = ScriptSegmenter.dominant(term)
        val q = AnswerMatcher.normalize(term, lang)
        if (q.isEmpty()) return emptyList()
        fun forms(w: Word) = (listOf(w.text(lang)) + w.alternatives(lang)).map { AnswerMatcher.normalize(it, lang) }
        val exact = Vocabulary.words.filter { w -> q in forms(w) }
        if (exact.isNotEmpty() || lang == Lang.EN) return exact
        // Bulgarian definite forms: кучето, котката, столът, стола.
        return Vocabulary.words.filter { w -> forms(w).any { f -> q.length > f.length && q.startsWith(f) && q.substring(f.length) in ARTICLES } }
    }

    /** The language to translate into: the one asked for, or the other one from the word's own script. */
    fun into(query: Query): Lang = query.into?.takeIf { it != ScriptSegmenter.dominant(query.term) } ?: ScriptSegmenter.dominant(query.term).other

    private val ARTICLES = setOf("ът", "а", "я", "ят", "та", "то", "те")
}

/** App-level requests about languages and lessons, understood in both modes. */
sealed interface LearnCommand {
    /** ZnaiKo switches its own language (speech and screens). */
    data class SwitchLanguage(val to: Lang) : LearnCommand
    /** Open the lessons, for learning [target] (null: the one chosen before). */
    data class OpenLessons(val target: Lang?) : LearnCommand
    /** Chat practice in [target] (needs Claude). */
    data class Practice(val target: Lang) : LearnCommand
    data object StopPractice : LearnCommand
    data object WordOfTheDay : LearnCommand
}

object LearnCommands {
    private val EN_WORDS = "английски|english"
    private val BG_WORDS = "български|bulgarian"
    private fun lang(word: String): Lang = if (Regex("^(?:$EN_WORDS)").containsMatchIn(word)) Lang.EN else Lang.BG

    private val SWITCH = Regex("^(?:говори|говори ми|говори с мен|мини|премини|превключи|смени езика)(?: на)? ($EN_WORDS|$BG_WORDS)(?: език)?$|^(?:speak|talk|switch to|change to|use) ($EN_WORDS|$BG_WORDS)(?: please)?$|^(?:speak|talk) (?:to me )?in ($EN_WORDS|$BG_WORDS)$")
    private val PRACTICE = Regex("^(?:да )?(?:упражняваме|поупражняваме|си говорим|поговорим|разговаряме)(?: на| по)? ($EN_WORDS|$BG_WORDS)$|^(?:упражнение|разговор) (?:на|по) ($EN_WORDS|$BG_WORDS)$|^(?:let'?s )?(?:practi[sc]e|chat in|have a chat in|talk in) ($EN_WORDS|$BG_WORDS)$")
    private val STOP = Regex("^(?:край на упражнението|спри упражнението|стига упражнения|спри разговора на (?:английски|български)|stop (?:the )?practi[sc]e|stop practi[sc]ing|end (?:the )?practi[sc]e)$")
    private val LESSONS = Regex("^(?:научи ме(?: на)?|искам да (?:уча|науча|се уча)|урок(?:и)?(?: по)?|да учим|учим|уча|teach me|i want to learn|let'?s learn|lessons?(?: in)?|learn)(?: ($EN_WORDS|$BG_WORDS))?(?: език| please)?$")
    private val WORD_OF_DAY = Regex("^(?:дума(?:та)? на деня|кажи ми дума(?:та)? на деня|word of the day|today'?s word)$")

    fun parse(text: String): LearnCommand? {
        val t = text.trim().trimEnd('?', '.', '!').lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        STOP.matchEntire(t)?.let { return LearnCommand.StopPractice }
        WORD_OF_DAY.matchEntire(t)?.let { return LearnCommand.WordOfTheDay }
        SWITCH.matchEntire(t)?.let { m -> return LearnCommand.SwitchLanguage(lang(m.groupValues.drop(1).first { it.isNotEmpty() })) }
        PRACTICE.matchEntire(t)?.let { m -> return LearnCommand.Practice(lang(m.groupValues.drop(1).first { it.isNotEmpty() })) }
        LESSONS.matchEntire(t)?.let { m -> return LearnCommand.OpenLessons(m.groupValues[1].takeIf { it.isNotEmpty() }?.let(::lang)) }
        return null
    }
}
