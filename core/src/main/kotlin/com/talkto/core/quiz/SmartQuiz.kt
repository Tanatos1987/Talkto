package com.talkto.core.quiz

import com.talkto.core.i18n.Lang

/**
 * Maths that follows the child: five right answers in a row move up a school year, two missed tasks in a row move
 * down one. The child can still pick any year by hand.
 */
object AdaptiveGrade {
    const val UP_AFTER = 5
    const val DOWN_AFTER = 2

    /** The new year, or null when it stays. */
    fun next(grade: Int, rightRun: Int, wrongRun: Int): Int? = when {
        rightRun >= UP_AFTER && grade < MathTasks.MAX_GRADE -> grade + 1
        wrongRun >= DOWN_AFTER && grade > 1 -> grade - 1
        else -> null
    }
}

/** One trivia question a day, the same for everyone that day. */
object DailyQuestion {
    fun of(day: Long): TriviaQuestion {
        val all = TriviaBank.ALL
        val i = ((day * 2_654_435_761L) % all.size + all.size) % all.size
        return all[i.toInt()]
    }
}

/**
 * Quiz questions Claude writes about the child's own interests. It is asked for a plain line format (no JSON, so a
 * small slip does not lose the whole round); [parse] keeps every complete question and drops the rest.
 */
object SmartQuiz {
    const val COUNT = 5

    fun system(lang: Lang, age: Int?): String {
        val who = if (age != null && age > 0) "a child aged $age" else "a child of primary-school age"
        val language = lang.nameIn(Lang.EN)
        return """
            You write fun multiple-choice quiz questions for $who, in $language. Everything must be true, kind and child-friendly.
            Write exactly $COUNT questions. For each question write exactly six lines, then one empty line:
            Q: the question
            R: the right answer
            W: a wrong answer
            W: another wrong answer
            W: a third wrong answer
            F: one short, surprising true fact about it
            Answers are short (one to four words) and the wrong ones are believable. No numbering, no markdown, nothing else.
        """.trimIndent()
    }

    fun prompt(lang: Lang, interests: List<String>): String {
        val topics = interests.filter { it.isNotBlank() }.take(6)
        return if (topics.isEmpty()) lang.pick("Въпроси за животни, космос и природа.", "Questions about animals, space and nature.")
        else lang.pick("Въпроси за: ", "Questions about: ") + topics.joinToString(", ")
    }

    fun parse(text: String, lang: Lang): List<TriviaQuestion> {
        val out = ArrayList<TriviaQuestion>()
        var q: String? = null
        var right: String? = null
        val wrong = ArrayList<String>()
        var fact = ""
        fun flush() {
            val question = q
            val r = right
            if (question != null && r != null && wrong.size >= 3) {
                val list = listOf(question, r) + wrong.take(3)
                out += TriviaQuestion(
                    id = "ai:" + (question.lowercase().hashCode() and 0x7fffffff),
                    category = TriviaCategory.EVERYDAY,
                    bg = list, en = list,
                    factBg = fact, factEn = fact,
                )
            }
            q = null; right = null; wrong.clear(); fact = ""
        }
        text.lines().map { it.trim() }.forEach { line ->
            val m = LINE.matchEntire(line) ?: return@forEach
            val value = m.groupValues[2].trim().trim('*', '"').trim()
            if (value.isEmpty()) return@forEach
            when (m.groupValues[1].uppercase()) {
                "Q" -> { flush(); q = value }
                "R" -> right = value
                "W" -> wrong += value
                "F" -> fact = value
            }
        }
        flush()
        // Four different answers, or the question is no good.
        return out.filter { t -> t.answers(lang).map { it.lowercase() }.toSet().size == 4 }.distinctBy { it.id }
    }

    private val LINE = Regex("^\\W*([QRWFqrwf])\\s*[:.)-]\\s*(.+)$")
}
