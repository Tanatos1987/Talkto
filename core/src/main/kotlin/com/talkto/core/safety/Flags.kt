package com.talkto.core.safety

import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Why the child pressed 🚩 on something Claude said. [forClaude] is how Claude hears about it. */
enum class FlagReason(val emoji: String, val bg: String, val en: String, val forClaude: String) {
    SCARY("😨", "Страшно", "Scary", "it scared the child"),
    RUDE("😠", "Грубо", "Rude", "it felt rude or mean"),
    WRONG("🤔", "Не е вярно", "Not true", "it seemed wrong"),
    NOT_FOR_KIDS("🙈", "Не е за деца", "Not for kids", "it was not suitable for a child"),
    OTHER("💬", "Друго", "Something else", "something about it was not right");

    fun label(lang: Lang): String = emoji + " " + lang.pick(bg, en)
}

/**
 * One answer from Claude that the child flagged. [atMs] doubles as the id.
 * [question] is what the child said just before; it stays on the phone and goes out only in an e-mail a parent sends.
 */
@Serializable
data class FlaggedReply(
    val atMs: Long,
    val reason: FlagReason,
    val reply: String,
    val question: String = "",
    val model: String = "",
    val lang: String = "bg",
    /** The authors' report address took it. */
    val sent: Boolean = false,
    /** A parent put it in an e-mail to the authors. */
    val emailed: Boolean = false,
)

/** The last [KEEP] flags, oldest first. Immutable: every change returns a new log. */
@Serializable
data class FlagLog(val items: List<FlaggedReply> = emptyList()) {

    /** Adds [f]; flagging the same answer again replaces the earlier flag (a new reason, sent again). */
    fun add(f: FlaggedReply): FlagLog =
        FlagLog((items.filter { it.atMs != f.atMs && it.reply != f.reply } + f).sortedBy { it.atMs }.takeLast(KEEP))

    fun markSent(ids: Collection<Long>): FlagLog = FlagLog(items.map { if (it.atMs in ids) it.copy(sent = true) else it })

    fun markEmailed(ids: Collection<Long>): FlagLog = FlagLog(items.map { if (it.atMs in ids) it.copy(emailed = true) else it })

    fun remove(id: Long): FlagLog = FlagLog(items.filter { it.atMs != id })

    /** Not yet at the report address. */
    val unsent: List<FlaggedReply> get() = items.filter { !it.sent }

    /** Neither sent nor e-mailed: what a parent still has to pass on. */
    val waiting: List<FlaggedReply> get() = items.filter { !it.sent && !it.emailed }

    /** The newest flag, when it is younger than [windowMs]. */
    fun recent(nowMs: Long, windowMs: Long = RECENT_MS): FlaggedReply? =
        items.lastOrNull()?.takeIf { nowMs - it.atMs in 0..windowMs }

    companion object {
        const val KEEP = 50
        /** How long Claude is reminded of a flag. */
        const val RECENT_MS = 30 * 60_000L
    }
}

/** The texts made from flags: the report for the authors, the e-mail a parent sends, and the note for Claude. */
object FlagReport {
    private const val MAX_REPLY = 2_000
    private const val MAX_QUESTION = 500

    /**
     * What goes to the authors' report address without leaving the app: ZnaiKo's words and the reason.
     * Nothing the child typed or said, no name and no device id.
     */
    fun json(f: FlaggedReply, app: String, version: String): String = buildJsonObject {
        put("app", app)
        put("version", version)
        put("at", Instant.ofEpochMilli(f.atMs).toString())
        put("lang", f.lang)
        put("model", f.model)
        put("reason", f.reason.name.lowercase(Locale.ROOT))
        put("reply", clip(f.reply, MAX_REPLY))
    }.toString()

    fun subject(lang: Lang): String = "ZnaiKo " + lang.pick("сигнал за отговор", "flagged answer")

    /** An e-mail for the authors. A parent reads it before pressing send, so the child's question is in it too. */
    fun email(items: List<FlaggedReply>, version: String, lang: Lang, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT).withZone(zone)
        return buildString {
            items.forEachIndexed { i, f ->
                if (i > 0) appendLine().appendLine("---").appendLine()
                appendLine("${f.reason.label(lang)} · ${time.format(Instant.ofEpochMilli(f.atMs))}" + if (f.model.isNotBlank()) " · ${f.model}" else "")
                if (f.question.isNotBlank()) appendLine(lang.pick("Детето: ", "Child: ") + clip(f.question, MAX_QUESTION))
                appendLine("ZnaiKo: " + clip(f.reply, MAX_REPLY))
            }
            appendLine()
            append("ZnaiKo $version")
        }
    }

    /** For Claude's live context while a flag is fresh: what was flagged and why, so it is not repeated. */
    fun contextNote(f: FlaggedReply, nowMs: Long): String {
        val minutes = ((nowMs - f.atMs) / 60_000).coerceAtLeast(0)
        return """
            <flagged_answer>
            $minutes min ago the child pressed "report" on one of your answers, because ${f.reason.forClaude}. It began: "${clip(f.reply, 160)}"
            ZnaiKo has already said thank you and hidden it. Do not repeat that content or return to it unless the child asks. Keep the next answers especially gentle, simple and true.
            </flagged_answer>
        """.trimIndent()
    }

    internal fun clip(s: String, max: Int): String {
        val t = s.trim().replace(Regex("\\s+"), " ")
        return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
    }
}
