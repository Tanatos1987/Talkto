package com.talkto.core.story

/** "Разкажи ми приказка", "басня", "гатанка", "a bedtime story": which kind of tale, or a riddle. */
data class StoryRequest(val kind: TaleKind? = null, val riddle: Boolean = false, val bedtime: Boolean = false)

/** Recognises requests for a tale or a riddle in Bulgarian and English (used offline; online Claude tells them). */
object StoryCommands {
    private val ASK = Regex(
        "^(?:хайде |моля те |моля |може ли |искаш ли |ще )?(?:да )?(?:ми )?" +
            "(?:разкажи|разкажеш|разказваш|прочети|прочетеш|кажи|кажеш|задай|зададеш|искам|чуя|tell|read|say|give|i want|can you tell|can you read)" +
            "|^(?:a |an |one more |another |още една |една )?(?:story|fable|riddle|fairy ?tale|bedtime story|приказка|басня|гатанка|приказчица)",
    )
    private val RIDDLE = Regex("гатанк|riddle")
    private val FABLE = Regex("басн|fable")
    private val FAIRY = Regex("приказ|fairy")
    private val STORY = Regex("истори|story|tale")
    private val BEDTIME = Regex("лека нощ|за сън|преди сън|приспив|bedtime|good ?night|sleep")

    fun parse(text: String): StoryRequest? {
        val t = text.trim().trimEnd('?', '.', '!').lowercase().replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.split(' ').size > 10 || !ASK.containsMatchIn(t)) return null
        val bedtime = BEDTIME.containsMatchIn(t)
        return when {
            RIDDLE.containsMatchIn(t) -> StoryRequest(riddle = true)
            FABLE.containsMatchIn(t) -> StoryRequest(TaleKind.FABLE, bedtime = bedtime)
            FAIRY.containsMatchIn(t) -> StoryRequest(TaleKind.FAIRY_TALE, bedtime = bedtime)
            STORY.containsMatchIn(t) -> StoryRequest(null, bedtime = bedtime)
            else -> null
        }
    }
}
