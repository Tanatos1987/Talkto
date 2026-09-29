package com.talkto.core.profile

import com.talkto.core.error.TalktoError
import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Something ZnaiKo learned about the user. [key] is stable ("name", "likes:кафе", "alias:кино"),
 * so learning the same thing twice updates instead of duplicating.
 */
@Serializable
data class Fact(val key: String, val value: String, val updatedAtMs: Long, val source: String = "chat")

interface ProfileStore {
    suspend fun upsert(fact: Fact)
    suspend fun all(): List<Fact>
    suspend fun delete(key: String): Boolean
}

/**
 * On-device memory about the user, learned from what they say (in both offline and Claude mode):
 * name, birthday, city, job, favourite things, likes and dislikes, free-form "remember that ..." facts,
 * and personal command shortcuts ("когато кажа кино, направи тихо").
 *
 * Nothing leaves the phone except, in Claude mode, as a short `<user_profile>` block in the system prompt.
 */
class ProfileRepository(private val store: ProfileStore, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun all(): List<Fact> = store.all().sortedBy { it.key }

    suspend fun get(key: String): String? = store.all().firstOrNull { it.key == key }?.value

    suspend fun remember(key: String, value: String, source: String = "chat"): Fact {
        val k = key.trim().lowercase(Locale.ROOT)
        val v = value.trim().trimEnd('.', '!')
        if (k.isEmpty() || v.isEmpty()) throw TalktoError.InvalidInput("Empty fact")
        if (v.length > 300) throw TalktoError.InvalidInput("Fact is too long")
        return Fact(k, v, clock(), source).also { store.upsert(it) }
    }

    /** Forgets by exact key, or every fact whose key or value contains [what]. Returns how many were removed. */
    suspend fun forget(what: String): Int {
        val w = what.trim().lowercase(Locale.ROOT)
        if (w.isEmpty()) return 0
        val matches = store.all().filter { it.key == w || w in it.key || w in it.value.lowercase(Locale.ROOT) }
        matches.forEach { store.delete(it.key) }
        return matches.size
    }

    suspend fun forgetAll(): Int = store.all().onEach { store.delete(it.key) }.size

    /** Learns from one user message. Returns what was newly learned or changed, for a friendly confirmation. */
    suspend fun learnFrom(text: String): List<Fact> {
        val learned = FactExtractor.extract(text)
        val known = store.all().associateBy { it.key }
        return learned.filter { (k, v) -> known[k]?.value != v }.map { (k, v) -> remember(k, v) }
    }

    /** Personal shortcut: if [text] is a known alias, the command it stands for. */
    suspend fun expandAlias(text: String): String? =
        get("alias:" + text.trim().trimEnd('.', '!', '?').lowercase(Locale.ROOT))

    /** Block for Claude's system prompt; empty when nothing is known (keeps the prompt identical for new users). */
    suspend fun promptBlock(): String {
        val facts = all().filterNot { it.key.startsWith("alias:") }
        if (facts.isEmpty()) return ""
        return buildString {
            appendLine("<user_profile>")
            appendLine("What the user told ZnaiKo about themselves (stored on the phone). Use it naturally; never recite it back unprompted.")
            facts.take(40).forEach { appendLine("- ${it.key}: ${it.value}") }
            append("</user_profile>")
        }
    }

    /** True when the stored birthday ("15.03", "15/3/1990", "15 март") is [day].[month]. */
    suspend fun isBirthday(day: Int, month: Int): Boolean {
        val b = get("birthday")?.lowercase(Locale.ROOT) ?: return false
        val m = Regex("^(\\d{1,2})[./ ]+(\\d{1,2}|\\p{L}+)").find(b) ?: return false
        val d = m.groupValues[1].toIntOrNull() ?: return false
        val mon = m.groupValues[2].toIntOrNull() ?: MONTHS.indexOfFirst { m.groupValues[2].startsWith(it) }.takeIf { it >= 0 }?.plus(1) ?: return false
        return d == day && mon == month
    }

    /** Human-readable summary for "какво знаеш за мен" / "what do you know about me". */
    suspend fun describe(lang: Lang = Lang.BG): String {
        val facts = all()
        if (facts.isEmpty()) {
            return lang.pick(
                "Още не знам нищо за теб. Разкажи ми: „казвам се …“, „обичам …“, „запомни, че …“.",
                "I don't know anything about you yet. Tell me: \"my name is …\", \"I like …\", \"remember that …\".",
            )
        }
        return facts.take(15).joinToString("\n") { "• " + label(it, lang) }
    }

    companion object {
        private val MONTHS = listOf("яну", "фев", "мар", "апр", "май", "юни", "юли", "авг", "сеп", "окт", "ное", "дек")

        fun label(f: Fact, lang: Lang = Lang.BG): String = if (lang == Lang.BG) {
            when {
                f.key == "name" -> "Казваш се ${f.value}"
                f.key == "birthday" -> "Рожденият ти ден е на ${f.value}"
                f.key == "city" -> "Живееш в ${f.value}"
                f.key == "job" -> "Работиш като ${f.value}"
                f.key == "age" -> "На ${f.value} години си"
                f.key == "friend" -> "Най-добрият ти приятел е ${f.value}"
                f.key == "pet" -> "Домашният ти любимец: ${f.value}"
                f.key == "grade" -> "Учиш в ${f.value} клас"
                f.key == "dream" -> "Като пораснеш, искаш да станеш ${f.value}"
                f.key.startsWith("favourite:") -> (Favourites.kind(f.key.removePrefix("favourite:"))?.bg ?: "Любим(а) ${f.key.removePrefix("favourite:")}") + ": ${f.value}"
                f.key.startsWith("likes:") -> "Обичаш ${f.value}"
                f.key.startsWith("dislikes:") -> "Не обичаш ${f.value}"
                f.key.startsWith("alias:") -> "Когато кажеш „${f.key.removePrefix("alias:")}“, правя „${f.value}“"
                f.key.startsWith("note:") -> f.value
                else -> "${f.key}: ${f.value}"
            }
        } else {
            when {
                f.key == "name" -> "Your name is ${f.value}"
                f.key == "birthday" -> "Your birthday is on ${f.value}"
                f.key == "city" -> "You live in ${f.value}"
                f.key == "job" -> "You work as ${f.value}"
                f.key == "age" -> "You are ${f.value} years old"
                f.key == "friend" -> "Your best friend is ${f.value}"
                f.key == "pet" -> "Your pet: ${f.value}"
                f.key == "grade" -> "You are in year ${f.value}"
                f.key == "dream" -> "When you grow up, you want to be ${f.value}"
                f.key.startsWith("favourite:") -> (Favourites.kind(f.key.removePrefix("favourite:"))?.en ?: "Favourite ${f.key.removePrefix("favourite:")}") + ": ${f.value}"
                f.key.startsWith("likes:") -> "You like ${f.value}"
                f.key.startsWith("dislikes:") -> "You don't like ${f.value}"
                f.key.startsWith("alias:") -> "When you say \"${f.key.removePrefix("alias:")}\", I do \"${f.value}\""
                f.key.startsWith("note:") -> f.value
                else -> "${f.key}: ${f.value}"
            }
        }
    }
}

/** Pattern-based extraction. Deliberately conservative: a wrong "fact" is worse than a missed one. */
object FactExtractor {

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private const val END = "(?:[.,!]|$| и | но )"

    // No IGNORE_CASE here: the name itself must start with a capital letter, so "аз съм гладен" is not a name
    // while "аз съм Мария" is. Trigger words spell out both cases, because inline (?i) is ASCII-only on the JVM
    // and (?u) does not exist in Android's ICU regex engine.
    private val NAME = Regex("(?:^|(?<!\\p{L}))(?:[Кк]азвам се|[Ии]мето ми е|[Аа]з съм|[Mm]y name is|I am|I'm|i'm|[Cc]all me|[Вв]икай ми|[Нн]аричай ме)\\s+(\\p{Lu}[\\p{L}-]{1,30})(?=$END)")
    private val BIRTHDAY = r("(?:рожденият ми ден е|роден(?:а)? съм|my birthday is|i was born)(?: на| on)?\\s+(\\d{1,2}[./ ](?:\\d{1,2}|[\\p{L}]+)(?:[./ ]\\d{2,4})?)")
    private val CITY = r("(?:живея в|живея във|i live in)\\s+([\\p{L}][\\p{L} -]{1,40}?)(?=$END)")
    private val JOB = r("(?:работя като|по професия съм|i work as an?|i work as)\\s+([\\p{L}][\\p{L} -]{1,40}?)(?=$END)")
    private val AGE = r("(?:на (\\d{1,3}) години съм|i am (\\d{1,3}) years old|i'm (\\d{1,3}))")
    private val FAVOURITE = r("(?:любимият ми|любимата ми|любимото ми|любимите ми|my favou?rite)\\s+([\\p{L}]{2,20})\\s+(?:е|са|is|are)\\s+(.{1,40}?)(?=$END)")
    private val LIKES = r("(?:^|(?<!\\p{L}))(?:много )?(?:обичам|харесвам|i love|i like)\\s+(?!да |to |те(?!\\p{L}))(.{2,40}?)(?=$END)")
    private val DISLIKES = r("(?:не обичам|не харесвам|мразя|i hate|i don't like|i dislike)\\s+(?!да |to )(.{2,40}?)(?=$END)")
    private val REMEMBER = r("^(?:запомни|remember)(?:,)?\\s+(?:че|that)\\s+(.{3,200})$")
    private val ALIAS = r("^(?:когато кажа|ако кажа|when i say)\\s+[\"„“']?(.{1,30}?)[\"„“']?\\s*(?:,\\s*)?(?:направи|изпълни|значи|означава|do|means)\\s+[\"„“']?(.{2,80}?)[\"„“']?$")

    fun extract(text: String): List<Pair<String, String>> {
        val t = text.trim()
        val out = ArrayList<Pair<String, String>>()
        ALIAS.find(t)?.let { m -> return listOf("alias:" + m.groupValues[1].lowercase(Locale.ROOT).trim() to m.groupValues[2].trim()) }
        REMEMBER.find(t)?.let { m ->
            val v = m.groupValues[1].trim().trimEnd('.')
            return listOf("note:" + v.lowercase(Locale.ROOT).take(40) to v)
        }
        NAME.find(t)?.let { out += "name" to it.groupValues[1] }
        BIRTHDAY.find(t)?.let { out += "birthday" to it.groupValues[1].trim() }
        CITY.find(t)?.let { out += "city" to it.groupValues[1].trim() }
        JOB.find(t)?.let { out += "job" to it.groupValues[1].trim() }
        AGE.find(t)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let { out += "age" to it } }
        FAVOURITE.find(t)?.let { out += "favourite:" + Favourites.canonical(it.groupValues[1]) to it.groupValues[2].trim() }
        val dislike = DISLIKES.find(t)
        dislike?.let { out += "dislikes:" + it.groupValues[1].lowercase(Locale.ROOT).trim() to it.groupValues[1].trim() }
        if (dislike == null) {
            LIKES.find(t)?.let { out += "likes:" + it.groupValues[1].lowercase(Locale.ROOT).trim() to it.groupValues[1].trim() }
        }
        return out
    }
}
