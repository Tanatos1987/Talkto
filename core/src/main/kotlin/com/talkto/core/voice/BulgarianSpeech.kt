package com.talkto.core.voice

/**
 * Makes Bulgarian text say itself right. Text-to-speech engines read "24 май" as "двадесет и четири май",
 * "1878 г." as "хиляда осемстотин седемдесет и осем г", "5 км" as "пет к м", and English words in a
 * Bulgarian sentence with a jump to another voice. This writes out what a person would say:
 * ordinals where Bulgarian needs them (dates, years, centuries, classes, places, "3-ти"), units with the right
 * number and gender ("един километър", "две минути", "два лева и петдесет стотинки"), common abbreviations, a few
 * Latin words in Cyrillic, and maths the way a teacher reads it ("7 − 3 = 4", "2x + 1", "3/4", "x²").
 * Plain numbers ("7 плюс 5") are left to the voice, which reads them well.
 */
object BulgarianSpeech {

    enum class Gender { M, F, N }

    /** A sentence with what phones get wrong: a date, a height in metres, a class, a sum. Settings plays it. */
    const val SAMPLE = "Здравей! Аз съм Знайко. Днес е 24 май, празникът на буквите. Връх Мусала е висок 2925 м, а в 3 клас знаем, че 7 × 8 = 56."

    fun normalize(text: String): String {
        var t = text
        t = joinDigitGroups(t)
        t = replaceWords(t, LATIN)
        t = abbreviations(t)
        t = capitals(t)
        t = negatives(t)
        // Clock times, money and units first: "1000 км" is a distance and "12.10 ч." a time, never a year or a date.
        t = times(t)
        t = money(t)
        t = units(t)
        t = dates(t)
        t = years(t)
        t = ranges(t)
        t = centuries(t)
        t = ordinalSuffixes(t)
        t = orderedNouns(t)
        t = kings(t)
        t = math(t)
        t = beforeFullStop(t)
        return t.replace(Regex(" {2,}"), " ").trim()
    }

    /**
     * "Отговорът е 9." -> "Отговорът е девет.": Bulgarian voices read a number right before a full stop as an ordinal
     * ("девети", as in "9. клас"), and every maths answer ends that way. Only a number inside a sentence (after a word
     * or a sign); a list number at the start of a line ("1. Купи мляко") stays. Counted the way children count: "едно",
     * "две".
     */
    private fun beforeFullStop(t: String): String =
        Regex("(?<=[\\p{L}=+−×÷)%]\\s{1,3})(\\d{1,9})\\.(?=\\s|$)").replace(t) { m ->
            cardinal(m.groupValues[1].toLong(), Gender.N) + "."
        }

    // ------------------------------------------------------------------ numbers in words

    private val UNITS_M = listOf("", "един", "два", "три", "четири", "пет", "шест", "седем", "осем", "девет")
    private val TEENS = listOf(
        "десет", "единадесет", "дванадесет", "тринадесет", "четиринадесет", "петнадесет", "шестнадесет", "седемнадесет", "осемнадесет", "деветнадесет",
    )
    private val TENS = listOf("", "", "двадесет", "тридесет", "четиридесет", "петдесет", "шестдесет", "седемдесет", "осемдесет", "деветдесет")
    private val HUNDREDS = listOf("", "сто", "двеста", "триста", "четиристотин", "петстотин", "шестстотин", "седемстотин", "осемстотин", "деветстотин")

    private fun unit(d: Int, g: Gender): String = when {
        d == 1 -> when (g) { Gender.M -> "един"; Gender.F -> "една"; Gender.N -> "едно" }
        d == 2 -> if (g == Gender.M) "два" else "две"
        else -> UNITS_M[d]
    }

    /** The words of 1..999, each part apart, so "и" can go before the last one. */
    private fun parts(n: Int, g: Gender): List<String> {
        val out = ArrayList<String>()
        val h = n / 100
        val r = n % 100
        if (h > 0) out += HUNDREDS[h]
        when {
            r in 10..19 -> out += TEENS[r - 10]
            r > 0 -> {
                if (r >= 20) out += TENS[r / 10]
                if (r % 10 > 0) out += unit(r % 10, g)
            }
        }
        return out
    }

    private fun joinParts(p: List<String>): String = when (p.size) {
        0 -> ""
        1 -> p[0]
        else -> p.dropLast(1).joinToString(" ") + " и " + p.last()
    }

    /** A whole number in words: 1878 -> "хиляда осемстотин седемдесет и осем", 21 (F) -> "двадесет и една". */
    fun cardinal(n: Long, g: Gender = Gender.M): String {
        if (n == 0L) return "нула"
        if (n < 0) return "минус " + cardinal(-n, g)
        if (n >= 1_000_000_000L) return n.toString()
        val millions = (n / 1_000_000).toInt()
        val thousands = ((n / 1000) % 1000).toInt()
        val rest = (n % 1000).toInt()
        val groups = ArrayList<String>()
        if (millions > 0) groups += if (millions == 1) "един милион" else joinParts(parts(millions, Gender.M)) + " милиона"
        if (thousands > 0) groups += if (thousands == 1) "хиляда" else joinParts(parts(thousands, Gender.F)) + " хиляди"
        val last = parts(rest, g)
        return when {
            last.isEmpty() -> groups.joinToString(" ")
            groups.isEmpty() -> joinParts(last)
            // "хиляда и пет", "хиляда и деветстотин", but "хиляда осемстотин седемдесет и осем".
            last.size == 1 -> groups.joinToString(" ") + " и " + last[0]
            else -> groups.joinToString(" ") + " " + joinParts(last)
        }
    }

    private val ORD_UNITS = listOf("", "първи", "втори", "трети", "четвърти", "пети", "шести", "седми", "осми", "девети")
    private val ORD_HUNDREDS = listOf("", "стотен", "двестотен", "тристотен", "четиристотен", "петстотен", "шестстотен", "седемстотен", "осемстотен", "деветстотен")
    private val THOUSAND_PREFIX = listOf("", "", "две", "три", "четири", "пет", "шест", "седем", "осем", "девет")

    /** The ordinal: only the last part changes, as in speech. 1878 (F) -> "хиляда осемстотин седемдесет и осма". */
    fun ordinal(n: Int, g: Gender = Gender.M): String {
        if (n <= 0 || n >= 1_000_000) return n.toString()
        val thousands = n / 1000
        val rest = n % 1000
        if (rest == 0) {
            val word = if (thousands == 1) "хиляден" else if (thousands < 10) THOUSAND_PREFIX[thousands] + "хиляден" else cardinal(thousands.toLong(), Gender.F) + " хиляден"
            return gender(word, g)
        }
        val p = parts(rest, Gender.M).toMutableList()
        p[p.lastIndex] = gender(ordinalWord(p.last()), g)
        val head = when {
            thousands == 0 -> ""
            thousands == 1 -> "хиляда"
            else -> joinParts(parts(thousands, Gender.F)) + " хиляди"
        }
        return when {
            head.isEmpty() -> joinParts(p)
            p.size == 1 -> "$head и ${p[0]}"
            else -> head + " " + joinParts(p)
        }
    }

    private fun ordinalWord(word: String): String {
        UNITS_M.indexOf(word).takeIf { it > 0 }?.let { return ORD_UNITS[it] }
        HUNDREDS.indexOf(word).takeIf { it > 0 }?.let { return ORD_HUNDREDS[it] }
        // Teens and tens: десет -> десети, двадесет -> двадесети.
        return word + "и"
    }

    private fun gender(masc: String, g: Gender): String = when {
        g == Gender.M -> masc
        masc.endsWith("ен") -> masc.dropLast(2) + if (g == Gender.F) "на" else "но"
        masc.endsWith("и") -> masc.dropLast(1) + if (g == Gender.F) "а" else "о"
        else -> masc
    }

    /** "трети" -> "трети", "стотен" -> "стотни": the plural, as in "две трети", "три стотни". */
    private fun ordinalPlural(n: Int): String {
        val masc = ordinal(n)
        return if (masc.endsWith("ен")) masc.dropLast(2) + "ни" else masc
    }

    /** "3-тия" -> "третия", "3-тата" -> "третата": the ordinal with the article its ending asks for. */
    private fun withArticle(n: Int, ending: String): String {
        val masc = ordinal(n)
        val stem = if (masc.endsWith("ен")) masc.dropLast(2) + "н" else masc.dropLast(1)
        return when (ending) {
            "и" -> masc
            "а" -> gender(masc, Gender.F)
            "о" -> gender(masc, Gender.N)
            "ия" -> stem + "ия"
            "ият" -> stem + "ият"
            "ата" -> gender(masc, Gender.F) + "та"
            "ото" -> gender(masc, Gender.N) + "то"
            else -> stem + "ите"
        }
    }

    // ------------------------------------------------------------------ rules

    private const val NOT_LETTER_BEFORE = "(?<![\\p{L}\\d])"
    private const val NOT_LETTER_AFTER = "(?![\\p{L}\\d])"

    private val MONTHS = listOf("януари", "февруари", "март", "април", "май", "юни", "юли", "август", "септември", "октомври", "ноември", "декември")
    private val MONTH_WORDS = MONTHS.joinToString("|")

    /** "384 000" -> "384000", so it is read as one number. */
    private fun joinDigitGroups(t: String): String =
        Regex("(?<![\\d,.])\\d{1,3}(?: \\d{3})+(?![\\d,])").replace(t) { it.value.replace(" ", "") }

    private fun dates(t: String): String {
        var s = t
        // 15.03.2026, 3.05.1878, 15/3/1990
        s = Regex("(?<![\\d.,/])(\\d{1,2})([./])(\\d{1,2})\\2(\\d{4})(?:\\s*(?:г\\.|година))?(?![\\d])").replace(s) { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[3].toInt(); val y = m.groupValues[4].toInt()
            if (d in 1..31 && mo in 1..12) "${ordinal(d)} ${MONTHS[mo - 1]} ${ordinal(y, Gender.F)} година" else m.value
        }
        // "на 15.03", "на 5.3" at the end of a clause: a birthday, not a decimal.
        s = Regex("(?<![\\p{L}])([Нн]а)\\s+(\\d{1,2})\\.(\\d{1,2})(?=\\s*(?:[.!?;:)]|,\\s|$))").replace(s) { m ->
            val d = m.groupValues[2].toInt(); val mo = m.groupValues[3].toInt()
            if (d in 1..31 && mo in 1..12) "${m.groupValues[1]} ${ordinal(d)} ${MONTHS[mo - 1]}" else m.value
        }
        // 15.03, but never inside a sum ("12.05 + 3").
        s = Regex("(?<![\\d.,]|[+\\-−×÷=<>*/]\\s?)(\\d{2})\\.(\\d{2})(?![\\d.,])(?!\\s?[+\\-−×÷=<>*/])").replace(s) { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            if (d in 1..31 && mo in 1..12) "${ordinal(d)} ${MONTHS[mo - 1]}" else m.value
        }
        // 24 май, 3-ти март, 12 април 1961 (година)
        s = Regex("$NOT_LETTER_BEFORE(\\d{1,2})(?:-?(?:ви|ри|ти|ми))?\\s+($MONTH_WORDS)(?:\\s+(\\d{3,4})(?:\\s*(?:г\\.|година)(?![\\p{L}]))?)?$NOT_LETTER_AFTER", RegexOption.IGNORE_CASE).replace(s) { m ->
            val d = m.groupValues[1].toInt()
            if (d !in 1..31) return@replace m.value
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { " " + ordinal(it.toInt(), Gender.F) + " година" } ?: ""
            "${ordinal(d)} ${m.groupValues[2]}$year"
        }
        return s
    }

    private val YEAR_LEAD = "(?:през|в|във|от|до|след|преди|края\\s+на|началото\\s+на)"

    private fun years(t: String): String {
        var s = t
        s = s.replace(Regex("пр\\.\\s?н\\.\\s?е\\.", RegexOption.IGNORE_CASE), "преди новата ера")
            .replace(Regex("(?<![\\p{L}.])н\\.\\s?е\\.", RegexOption.IGNORE_CASE), "от новата ера")
            .replace(Regex("пр\\.\\s?Хр\\."), "преди Христа")
            .replace(Regex("сл\\.\\s?Хр\\."), "след Христа")
        // "учебната 2025/2026 година": one school year.
        s = Regex("(?<![\\d.,/])(20\\d{2}|19\\d{2})\\s?/\\s?(20\\d{2}|19\\d{2})(?![\\d/])").replace(s) { m ->
            val from = m.groupValues[1].toInt(); val to = m.groupValues[2].toInt()
            if (to != from + 1) return@replace m.value
            val next = ordinal(to, Gender.F)
            // "две хиляди двадесет и пета - двадесет и шеста": the thousands are said once.
            val short = if (from / 100 == to / 100 && to % 100 != 0) ordinal(to % 100, Gender.F) else next
            val tail = if (Regex("^\\s*(?:година|г\\.)").containsMatchIn(s.substring(m.range.last + 1))) "" else " година"
            "${ordinal(from, Gender.F)} - $short$tail"
        }
        // "1941-1945 г.", "(1941–1945)": from one year to the other, when "година" or the end of the clause says so.
        s = Regex(
            "(?<![\\p{L}\\d.,])(?:(от|през|между|във|в)\\s+)?(1\\d{3}|20\\d{2})\\s?-\\s?(1\\d{3}|20\\d{2})" +
                "(?:\\s*(?:гг?\\.|години|година)(?![\\p{L}])|(?=\\s*(?:[.!?;:),]|$)))",
            RegexOption.IGNORE_CASE,
        ).replace(s) { m ->
            val from = m.groupValues[2].toInt(); val to = m.groupValues[3].toInt()
            if (to <= from) return@replace m.value
            val lead = m.groupValues[1]
            when (lead.lowercase()) {
                "" -> "от ${ordinal(from, Gender.F)} до ${ordinal(to, Gender.F)} година"
                "между" -> "$lead ${ordinal(from, Gender.F)} и ${ordinal(to, Gender.F)} година"
                else -> "$lead ${ordinal(from, Gender.F)} до ${ordinal(to, Gender.F)} година"
            }
        }
        // "1878 година", "79 година", "2026 годината", "1878 г." (four digits, or after през/в/от...)
        s = Regex("$NOT_LETTER_BEFORE(\\d{1,4})\\s*(година|годината|год\\.)$NOT_LETTER_AFTER").replace(s) { m ->
            val word = if (m.groupValues[2] == "годината") "годината" else "година"
            val n = m.groupValues[1].toInt()
            // "на 1 година" is an age; from 2 on, "година" in the singular can only be a year.
            if (n == 1 && word == "година") "една година" else ordinal(n, Gender.F) + " " + word
        }
        s = Regex("$NOT_LETTER_BEFORE(1\\d{3}|20\\d{2}|21\\d{2})\\s*г\\.").replace(s) { m -> ordinal(m.groupValues[1].toInt(), Gender.F) + " година" }
        s = Regex("(?<![\\p{L}])($YEAR_LEAD)\\s+(\\d{1,3})\\s*г\\.", RegexOption.IGNORE_CASE).replace(s) { m ->
            m.groupValues[1] + " " + ordinal(m.groupValues[2].toInt(), Gender.F) + " година"
        }
        // "роден е през 1990." without "година": at the end of a clause it can only be a year. After "до", "от", "в",
        // "след", "преди" it can also be counting ("брои до 1000", "кое число е след 1999?"), so there only years
        // that are not round hundreds and not in a question.
        s = Regex("(?<![\\p{L}])($YEAR_LEAD)\\s+(1\\d{3}|20\\d{2})(?=\\s*(?:([.!?;:)]|,\\s)|$))", RegexOption.IGNORE_CASE).replace(s) { m ->
            val lead = m.groupValues[1].lowercase()
            val n = m.groupValues[2].toInt()
            val sure = lead == "през" || lead.startsWith("края") || lead.startsWith("началото")
            if (!sure && (n % 100 == 0 || m.groupValues[3] == "?")) m.value
            else m.groupValues[1] + " " + ordinal(n, Gender.F) + " година"
        }
        return s
    }

    /** "5-6 години", "стр. 5-10." -> "5 до 6 години", "5 до 10.": a range, not a minus (a sum has "=" or "?" after it). */
    private fun ranges(t: String): String =
        Regex("(?<![\\d.,\\-])(\\d{1,4})\\s?-\\s?(\\d{1,4})(?![\\d\\-])(?=\\s+\\p{L}|\\s*[.!;:),]|$)").replace(t) { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (a < b) "$a до $b" else m.value
        }

    private fun roman(s: String): Int? {
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
        if (s.isEmpty() || s.any { it !in values }) return null
        var total = 0
        for (i in s.indices) {
            val v = values.getValue(s[i])
            val next = if (i + 1 < s.length) values.getValue(s[i + 1]) else 0
            total += if (v < next) -v else v
        }
        return total.takeIf { it in 1..40 }
    }

    private fun centuries(t: String): String =
        Regex("$NOT_LETTER_BEFORE([IVXLC]{1,6}|\\d{1,2})(-?(?:ви|ри|ти|ми))?\\s*(век|века|в\\.|хилядолетие|хилядолетия)(?![\\p{L}])").replace(t) { m ->
            val digits = m.groupValues[1].toIntOrNull()
            val n = digits ?: roman(m.groupValues[1]) ?: return@replace m.value
            val noun = when (m.groupValues[3]) { "в." -> "век"; else -> m.groupValues[3] }
            // "преди 2 века", "5 хилядолетия": that many, not the second one.
            if (digits != null && m.groupValues[2].isEmpty() && n >= 2 && (noun == "века" || noun == "хилядолетия")) {
                return@replace cardinal(n.toLong(), if (noun == "века") Gender.M else Gender.N) + " " + noun
            }
            val g = if (noun.startsWith("хилядолети")) Gender.N else Gender.M
            ordinal(n, g) + " " + noun
        }

    /** "3-ти" -> "трети", "2-ра" -> "втора", "1-во" -> "първо", "3-тия" -> "третия": the ending tells the form. */
    private fun ordinalSuffixes(t: String): String =
        Regex("$NOT_LETTER_BEFORE(\\d{1,4})\\s?-\\s?([врмт])(ият|ия|ата|ото|ите|и|а|о)(?![\\p{L}])").replace(t) { m ->
            val n = m.groupValues[1].toInt()
            // "2-ма", "3-ма души": двама, трима; "7-ма", "8-ма" are ordinals.
            if (m.groupValues[2] == "м" && m.groupValues[3] == "а" && n in 2..6) PEOPLE[n - 2]
            else withArticle(n, m.groupValues[3])
        }

    private val PEOPLE = listOf("двама", "трима", "четирима", "петима", "шестима")

    /** Nouns that take an ordinal before them: "3 клас" is "трети клас", "1 място" is "първо място". */
    private val ORDERED = mapOf(
        "клас" to Gender.M, "етаж" to Gender.M,
        "място" to Gender.N, "ниво" to Gender.N,
        "част" to Gender.F, "глава" to Gender.F, "серия" to Gender.F, "награда" to Gender.F,
    )

    private fun orderedNouns(t: String): String {
        val nouns = ORDERED.keys.joinToString("|")
        var s = Regex("$NOT_LETTER_BEFORE(\\d{1,2})\\.?\\s+($nouns)(?![\\p{L}])").replace(t) { m ->
            val noun = m.groupValues[2]
            val n = m.groupValues[1].toInt()
            // "1 глава лук", "1 част оцет": one of them.
            if (n == 1 && (noun == "глава" || noun == "част")) "една $noun" else ordinal(n, ORDERED.getValue(noun)) + " " + noun
        }
        // "V клас", "II място", "I световна война": schools and rankings write them in Roman numerals.
        s = Regex("$NOT_LETTER_BEFORE([IVX]{1,5})\\s+($nouns|световна)(?![\\p{L}])").replace(s) { m ->
            val n = romanStrict(m.groupValues[1]) ?: return@replace m.value
            val noun = m.groupValues[2]
            if (noun == "световна") (if (n == 1) "Първата" else if (n == 2) "Втората" else ordinal(n, Gender.F)) + " световна"
            else ordinal(n, ORDERED.getValue(noun)) + " " + noun
        }
        return s
    }

    /** Units after a number: the word, in the right number, and the number in words with the unit's gender. */
    private data class Unit(val one: String, val many: String, val g: Gender)

    private val UNIT_WORDS = linkedMapOf(
        "км²" to Unit("квадратен километър", "квадратни километра", Gender.M),
        "км2" to Unit("квадратен километър", "квадратни километра", Gender.M),
        "кв. км" to Unit("квадратен километър", "квадратни километра", Gender.M),
        "кв.км" to Unit("квадратен километър", "квадратни километра", Gender.M),
        "м²" to Unit("квадратен метър", "квадратни метра", Gender.M),
        "м2" to Unit("квадратен метър", "квадратни метра", Gender.M),
        "кв. м" to Unit("квадратен метър", "квадратни метра", Gender.M),
        "кв.м" to Unit("квадратен метър", "квадратни метра", Gender.M),
        "см²" to Unit("квадратен сантиметър", "квадратни сантиметра", Gender.M),
        "см2" to Unit("квадратен сантиметър", "квадратни сантиметра", Gender.M),
        "м³" to Unit("кубичен метър", "кубични метра", Gender.M),
        "м3" to Unit("кубичен метър", "кубични метра", Gender.M),
        "см³" to Unit("кубичен сантиметър", "кубични сантиметра", Gender.M),
        "см3" to Unit("кубичен сантиметър", "кубични сантиметра", Gender.M),
        "км" to Unit("километър", "километра", Gender.M),
        "кг" to Unit("килограм", "килограма", Gender.M),
        "см" to Unit("сантиметър", "сантиметра", Gender.M),
        "мм" to Unit("милиметър", "милиметра", Gender.M),
        "мл" to Unit("милилитър", "милилитра", Gender.M),
        "м" to Unit("метър", "метра", Gender.M),
        "л" to Unit("литър", "литра", Gender.M),
        "гр." to Unit("грам", "грама", Gender.M),
        "%" to Unit("процент", "процента", Gender.M),
        "°C" to Unit("градус по Целзий", "градуса по Целзий", Gender.M),
        "℃" to Unit("градус по Целзий", "градуса по Целзий", Gender.M),
        "°" to Unit("градус", "градуса", Gender.M),
        "лв." to Unit("лев", "лева", Gender.M),
        "лв" to Unit("лев", "лева", Gender.M),
        "ст." to Unit("стотинка", "стотинки", Gender.F),
        "€" to Unit("евро", "евро", Gender.N),
        "$" to Unit("долар", "долара", Gender.M),
        "ч." to Unit("час", "часа", Gender.M),
        "ч" to Unit("час", "часа", Gender.M),
        "мин." to Unit("минута", "минути", Gender.F),
        "мин" to Unit("минута", "минути", Gender.F),
        "сек." to Unit("секунда", "секунди", Gender.F),
        "сек" to Unit("секунда", "секунди", Gender.F),
        "хил." to Unit("хиляда", "хиляди", Gender.F),
        "млн." to Unit("милион", "милиона", Gender.M),
        "млрд." to Unit("милиард", "милиарда", Gender.M),
        "бр." to Unit("брой", "броя", Gender.M),
    )

    private val UNIT_KEYS = UNIT_WORDS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    private fun units(t: String): String =
        // "1.500 км" is fifteen hundred, "1.5 км" and "1,5 км" one and a half.
        Regex("(?<![\\p{L}\\d.,])(\\d{1,3}(?:\\.\\d{3})+|\\d+(?:[.,]\\d+)?)\\s?($UNIT_KEYS)(?![\\p{L}\\d])").replace(t) { m ->
            val unit = UNIT_WORDS.getValue(m.groupValues[2])
            val raw = m.groupValues[1]
            val number = if (Regex("\\d{1,3}(?:\\.\\d{3})+").matches(raw)) raw.replace(".", "") else raw.replace('.', ',')
            val whole = number.toLongOrNull()
            when {
                whole == null -> "$number ${unit.many}" // 1,5 км: the voice reads the decimal
                else -> count(whole, unit)
            }
        }

    private fun count(n: Long, unit: Unit): String = if (n == 1L) "${cardinal(1, unit.g)} ${unit.one}" else "${cardinal(n, unit.g)} ${unit.many}"

    /** Money with its change: "2,50 лв." -> "два лева и петдесет стотинки", "€0.99" -> "деветдесет и девет цента". */
    private class Money(val main: Unit, val change: Unit)

    private val EURO = Money(Unit("евро", "евро", Gender.N), Unit("цент", "цента", Gender.M))
    private val LEV = Money(Unit("лев", "лева", Gender.M), Unit("стотинка", "стотинки", Gender.F))
    private val DOLLAR = Money(Unit("долар", "долара", Gender.M), Unit("цент", "цента", Gender.M))

    private fun money(t: String): String {
        // "€5", "$ 3.20": the sign goes after the number, where Bulgarian says it.
        var s = Regex("([€$])\\s?(\\d+(?:[.,]\\d+)?)").replace(t) { m -> m.groupValues[2] + " " + m.groupValues[1] }
        s = Regex("(?<![\\p{L}\\d.,])(\\d+)[.,](\\d{2})\\s?(€|\\$|лв\\.|лв|лева|евро|долара)(?![\\p{L}\\d])").replace(s) { m ->
            val money = when (m.groupValues[3]) { "€", "евро" -> EURO; "$", "долара" -> DOLLAR; else -> LEV }
            val whole = m.groupValues[1].toLong()
            val cents = m.groupValues[2].toLong()
            when {
                cents == 0L -> count(whole, money.main)
                whole == 0L -> count(cents, money.change)
                else -> count(whole, money.main) + " и " + count(cents, money.change)
            }
        }
        return s
    }

    /** "в 10.30 ч." -> "в десет и тридесет часа", "9:00 ч." -> "девет часа". "14:30" alone the voices read well. */
    private fun times(t: String): String =
        Regex("(?<![\\d.,:])([01]?\\d|2[0-3])[.:]([0-5]\\d)\\s?ч\\.?(?![\\p{L}])").replace(t) { m ->
            val h = cardinal(m.groupValues[1].toLong())
            val min = m.groupValues[2].toLong()
            if (min == 0L) "$h часа" else "$h и ${cardinal(min, Gender.F)} часа"
        }

    /** "-5 °C" -> "минус 5 °C": a minus before a number, not a dash between words. */
    private fun negatives(t: String): String = Regex("(?<=^|[\\s(=])[-−](?=\\d)").replace(t, "минус ")

    /** "БРАВО" -> "браво": voices spell words in capitals letter by letter. Short ones (САЩ, НАТО, БАН) are spelled on purpose. */
    private fun capitals(t: String): String =
        Regex("(?<![\\p{L}])[А-Я]{5,}(?![\\p{L}])").replace(t) { it.value.lowercase() }

    /** Roman numerals the way they are written (III, not IIII), 1 to 20. */
    private fun romanStrict(s: String): Int? {
        val n = roman(s) ?: return null
        return n.takeIf { it <= 20 && toRoman(it) == s }
    }

    private fun toRoman(n: Int): String {
        val tens = listOf("", "X", "XX")
        val ones = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")
        return tens[n / 10] + ones[n % 10]
    }

    /** "Цар Борис III" -> "Цар Борис трети", "Екатерина II" -> "Екатерина втора", "Глава IV" -> "Глава четвърта". */
    private fun kings(t: String): String =
        Regex("(?<![\\p{L}])([А-Я][а-я]{1,20}|${BOOK_PARTS.keys.joinToString("|")}) ([IVX]{1,5})(?![\\p{L}\\d])(?!\\s+[A-Za-z])").replace(t) { m ->
            val word = m.groupValues[1]
            val n = romanStrict(m.groupValues[2]) ?: return@replace m.value
            val g = BOOK_PARTS[word.lowercase()] ?: if (word.endsWith("а") || word.endsWith("я") || word in WOMEN) Gender.F else Gender.M
            "$word ${ordinal(n, g)}"
        }

    private val WOMEN = setOf("Елизабет", "Маргрете", "Кристин")

    /** "том II", "глава IV": parts of a book, numbered after the word. */
    private val BOOK_PARTS = mapOf(
        "том" to Gender.M, "раздел" to Gender.M, "клас" to Gender.M, "глава" to Gender.F, "част" to Gender.F, "книга" to Gender.F,
        "серия" to Gender.F, "точка" to Gender.F,
    )

    private val ABBREVIATIONS = listOf(
        "т\\.\\s?е\\." to "тоест", "и т\\.\\s?н\\." to "и така нататък", "и др\\." to "и други", "т\\.\\s?нар\\." to "така наречения",
        "напр\\." to "например", "вкл\\." to "включително", "прибл\\." to "приблизително",
        "д-р" to "доктор", "г-н" to "господин", "г-жа" to "госпожа", "г-ца" to "госпожица",
        "проф\\." to "професор", "доц\\." to "доцент", "акад\\." to "академик", "инж\\." to "инженер",
        "ул\\." to "улица", "бул\\." to "булевард", "пл\\." to "площад", "стр\\." to "страница", "тел\\." to "телефон",
        "№" to "номер", "&" to "и",
    )

    private fun abbreviations(t: String): String {
        var s = t
        for ((pattern, word) in ABBREVIATIONS) s = s.replace(Regex("(?<![\\p{L}])$pattern(?![\\p{L}])", RegexOption.IGNORE_CASE), word)
        // Before a name only: "гр. Пловдив", "с. Арбанаси", "св. Иван".
        s = s.replace(Regex("(?<![\\p{L}])гр\\.\\s?(?=[А-Я])"), "град ")
            .replace(Regex("(?<![\\p{L}])с\\.\\s?(?=[А-Я])"), "село ")
            .replace(Regex("(?<![\\p{L}])[Сс]в\\.\\s?(?=[А-Я])"), "Свети ")
        return s
    }

    /** Latin words that come up in Bulgarian replies, spelled the way they sound, so the voice does not switch. */
    private val LATIN = mapOf(
        "ok" to "окей", "okay" to "окей", "sms" to "есемес", "wi-fi" to "уайфай", "wifi" to "уайфай",
        "youtube" to "Ютюб", "google" to "Гугъл", "facebook" to "Фейсбук", "viber" to "Вайбър", "whatsapp" to "Уотсап",
        "instagram" to "Инстаграм", "tiktok" to "ТикТок", "android" to "Андроид", "iphone" to "айфон", "email" to "имейл",
        "e-mail" to "имейл", "usb" to "ю ес би", "gps" to "джи пи ес", "tv" to "ти ви", "pc" to "пи си", "dj" to "диджей",
        "pin" to "пин", "claude" to "Клод", "bluetooth" to "блутут", "h2o" to "аш две о", "co2" to "це о две",
        "o2" to "о две", "nacl" to "натриев хлорид", "dna" to "де ен ка", "eur" to "€", "usd" to "$", "bgn" to "лв.",
    )

    // ------------------------------------------------------------------ maths

    private val VARIABLES = mapOf("x" to "хикс", "y" to "игрек", "z" to "зет", "a" to "а", "b" to "бе", "c" to "це", "n" to "ен")

    /** Maths read the way a teacher says it: "7 − 3 = 4" -> "7 минус 3 е равно на 4", "2x²" -> "2 хикс на квадрат". */
    private fun math(t: String): String {
        var s = t
        // "3 x 4", "3х4": times between two numbers.
        s = Regex("(?<=\\d)\\s*[xх]\\s*(?=\\d)").replace(s, " × ")
        // Letters for numbers: after a coefficient ("2x"), or next to a sign ("x = 5", "(a + b)").
        val v = VARIABLES.keys.joinToString("")
        s = Regex("(?<=\\d)([$v])(?![\\p{L}\\d])").replace(s) { " " + VARIABLES.getValue(it.value) }
        s = Regex("(?<![\\p{L}\\d])([$v])(?![\\p{L}\\d])(?=\\s*[=+−×÷<>≤≥≠≈²³/)]|\\s+-\\s)").replace(s) { VARIABLES.getValue(it.value) }
        s = Regex("(?<=[=+−×÷<>≤≥≠≈(/]\\s{0,2})([$v])(?![\\p{L}\\d])").replace(s) { VARIABLES.getValue(it.value) }
        // Fractions: "3/4" -> "три четвърти", "1/2" -> "една втора"; "10/2" or "10 / 2 =" is a division.
        s = Regex("(?<![\\d/.,:])(\\d{1,3})(\\s?)/(\\s?)(\\d{1,3})(?![\\d/.,])(\\s*=)?").replace(s) { m ->
            val n = m.groupValues[1].toInt(); val d = m.groupValues[4].toInt()
            val spaced = m.groupValues[2].isNotEmpty() || m.groupValues[3].isNotEmpty()
            when {
                n == 24 && d == 7 -> "двадесет и четири на седем"
                d < 2 -> m.value
                spaced || n >= d || m.groupValues[5].isNotEmpty() || d > 100 -> "$n делено на $d" + m.groupValues[5]
                n == 1 -> "една " + ordinal(d, Gender.F)
                else -> cardinal(n.toLong(), Gender.F) + " " + ordinalPlural(d)
            }
        }
        // "x/2", "(a + b) / 2", but not "и/или".
        s = s.replace(Regex("(?<=[\\d)²³]|хикс|игрек|зет)\\s*/\\s*(?=[\\d(]|хикс|игрек|зет)"), " делено на ")
        // "7-3=4", "Колко е 7-3?": a minus when a sum follows.
        s = Regex("(?<=\\d)-(?=\\d+\\s*[=?])").replace(s, " минус ")
        s = s.replace(Regex("\\s*=\\s*\\?"), "?")
        for ((sign, words) in SIGN_WORDS) s = s.replace(Regex("\\s*${Regex.escape(sign)}\\s*"), " $words ")
        // "+" between terms, not in "C++".
        s = s.replace(Regex("(?<=[^\\s+])\\s*\\+(?!\\+)\\s*(?=[\\d(\\p{L}√π−])"), " плюс ")
        s = s.replace("²", " на квадрат").replace("³", " на куб")
        s = s.replace(Regex("√\\s*"), "корен квадратен от ").replace("π", "пи")
        return s
    }

    private val SIGN_WORDS = listOf(
        "≤" to "е по-малко или равно на", "≥" to "е по-голямо или равно на", "≠" to "не е равно на", "≈" to "е приблизително",
        "=" to "е равно на", "<" to "е по-малко от", ">" to "е по-голямо от", "−" to "минус", "×" to "по", "·" to "по", "÷" to "делено на",
    )

    private fun replaceWords(t: String, words: Map<String, String>): String =
        Regex("(?<![\\p{L}\\d-])([A-Za-z][A-Za-z0-9-]*)(?![\\p{L}\\d])").replace(t) { m -> words[m.value.lowercase()] ?: m.value }
}
