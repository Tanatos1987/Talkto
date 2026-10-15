package com.talkto.core.i18n

/**
 * The two languages ZnaiKo speaks. [tag] is the BCP 47 tag for text-to-speech and speech recognition,
 * [native] the language's own name, used in the language picker.
 */
enum class Lang(val code: String, val tag: String, val native: String) {
    BG("bg", "bg-BG", "Български"),
    EN("en", "en-US", "English");

    /** Picks the text for this language. */
    fun pick(bg: String, en: String): String = if (this == BG) bg else en

    /** The other language: what a Bulgarian speaker learns, and the other way round. */
    val other: Lang get() = if (this == BG) EN else BG

    /** This language's name, written in [inLang] ("английски" / "English"). */
    fun nameIn(inLang: Lang): String = when (this) {
        BG -> inLang.pick("български", "Bulgarian")
        EN -> inLang.pick("английски", "English")
    }

    companion object {
        fun of(code: String?): Lang = entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: BG
    }
}
