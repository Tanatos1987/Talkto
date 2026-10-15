package com.talkto.core.quiz

import com.talkto.core.i18n.Lang
import java.util.Locale
import kotlin.random.Random

enum class TriviaCategory(val emoji: String, val bg: String, val en: String) {
    ANIMALS("🐾", "Животни", "Animals"),
    NATURE("🌿", "Природа", "Nature"),
    SPACE("🚀", "Космос", "Space"),
    SCIENCE("🔬", "Наука", "Science"),
    GEOGRAPHY("🌍", "География", "Geography"),
    BULGARIA("🇧🇬", "България", "Bulgaria"),
    HISTORY("🏛️", "История", "History"),
    SPORT("⚽", "Спорт", "Sport"),
    ART("🎨", "Изкуство", "Art"),
    EVERYDAY("🏠", "Всекидневие", "Everyday");

    fun label(lang: Lang) = lang.pick(bg, en)
}

/** A general-knowledge question in both languages. The first answer of each list is the right one. */
data class TriviaQuestion(
    val id: String,
    val category: TriviaCategory,
    private val bg: List<String>,
    private val en: List<String>,
    private val factBg: String,
    private val factEn: String,
) {
    fun question(lang: Lang) = if (lang == Lang.BG) bg[0] else en[0]
    /** The four answers, right one first. */
    fun answers(lang: Lang) = (if (lang == Lang.BG) bg else en).drop(1)
    fun fact(lang: Lang) = lang.pick(factBg, factEn)
}

/** A question as shown: the answers in a shuffled [order] (indexes into [TriviaQuestion.answers]). */
data class TriviaCard(val question: TriviaQuestion, val order: List<Int>) {
    fun options(lang: Lang): List<String> = question.answers(lang).let { a -> order.map { a[it] } }
    /** Index of the right option on screen. */
    val correct: Int get() = order.indexOf(0)

    /**
     * Which shown option a typed or spoken answer means, or null: "б", "B", "второто", "the second one",
     * a number for number answers ("осем" -> 8), or the words of an answer ("син кит" -> "Синият кит").
     */
    fun match(input: String, lang: Lang): Int? {
        val options = options(lang)
        val text = input.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s−-]"), " ").trim()
        if (text.isEmpty()) return null
        LETTERS.forEach { letters -> letters.indexOf(text).takeIf { it >= 0 }?.let { return it } }
        val ordinal = ORDINALS.indexOfFirst { words -> words.any { Regex("(?<![\\p{L}])$it").containsMatchIn(text) } }.takeIf { it >= 0 }
        val numeric = options.map { it.replace('−', '-').toIntOrNull() }
        if (numeric.all { it != null }) {
            // "4" means the answer 4 here, not the fourth button.
            if (ordinal != null) return ordinal
            val n = NumberWords.parse(text) ?: return null
            return numeric.indexOf(n).takeIf { it >= 0 }
        }
        bestByWords(text, options)?.let { return it }
        if (ordinal != null) return ordinal
        return POSITIONS.indexOf(text).takeIf { it >= 0 }
    }

    /** The one option whose words were said most completely, or null on no match or a tie. */
    private fun bestByWords(text: String, options: List<String>): Int? {
        val said = tokens(text)
        if (said.isEmpty()) return null
        val scores = options.map { option ->
            val want = tokens(option.lowercase(Locale.ROOT))
            if (want.isEmpty()) 0.0 else want.count { w -> said.any { s -> sameWord(s, w) } }.toDouble() / want.size
        }
        val best = scores.max()
        if (best <= 0.0 || scores.count { it == best } > 1) return null
        return scores.indexOf(best)
    }

    private companion object {
        val LETTERS = listOf(listOf("а", "б", "в", "г"), listOf("a", "b", "c", "d"))
        val POSITIONS = listOf("1", "2", "3", "4")
        val ORDINALS = listOf(
            listOf("първ", "first"), listOf("втор", "second"), listOf("трет", "third"), listOf("четвърт", "fourth", "last", "последн"),
        )
        val SKIP = setOf("the", "and", "от", "на", "за", "около", "about", "per", "в", "с", "и")

        fun tokens(s: String) = s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in SKIP && (it.length >= 3 || it.all(Char::isDigit)) }

        /** Same word give or take an ending: "синият" and "син", "слонът" and "слон". */
        fun sameWord(a: String, b: String): Boolean {
            if (a.all(Char::isDigit) || b.all(Char::isDigit)) return a == b
            val n = minOf(a.length, b.length, 5)
            return n >= 3 && a.take(n) == b.take(n)
        }
    }
}

/** Picks and shuffles trivia questions, avoiding the ones asked lately. */
class Trivia(private val random: Random = Random.Default) {

    fun card(q: TriviaQuestion) = TriviaCard(q, (0 until 4).shuffled(random))

    /** [size] questions from [category] (all when null), fresh ones before the [recent] ids. */
    fun round(size: Int = ROUND, category: TriviaCategory? = null, recent: Collection<String> = emptyList()): List<TriviaCard> {
        val pool = TriviaBank.of(category)
        val fresh = pool.filter { it.id !in recent }.shuffled(random)
        val old = pool.filter { it.id in recent }.shuffled(random)
        return (fresh + old).take(size).map(::card)
    }

    companion object { const val ROUND = 10 }
}

object TriviaBank {

    val ALL: List<TriviaQuestion> by lazy {
        val counters = mutableMapOf<TriviaCategory, Int>()
        RAW.map { (cat, bg, en, facts) ->
            val n = (counters[cat] ?: 0) + 1
            counters[cat] = n
            TriviaQuestion("${cat.name.lowercase(Locale.ROOT)}_$n", cat, bg.split('|'), en.split('|'), facts.first, facts.second)
        }
    }

    fun of(category: TriviaCategory?): List<TriviaQuestion> = if (category == null) ALL else ALL.filter { it.category == category }

    private data class Raw(val cat: TriviaCategory, val bg: String, val en: String, val facts: Pair<String, String>)

    private fun q(cat: TriviaCategory, bg: String, en: String, factBg: String = "", factEn: String = "") = Raw(cat, bg, en, factBg to factEn)

    private val A = TriviaCategory.ANIMALS
    private val N = TriviaCategory.NATURE
    private val S = TriviaCategory.SPACE
    private val C = TriviaCategory.SCIENCE
    private val G = TriviaCategory.GEOGRAPHY
    private val B = TriviaCategory.BULGARIA
    private val H = TriviaCategory.HISTORY
    private val P = TriviaCategory.SPORT
    private val R = TriviaCategory.ART
    private val E = TriviaCategory.EVERYDAY

    private val RAW = listOf(
        // ---------------------------------------------------------------- animals
        q(A, "Кое е най-голямото животно на Земята?|Синият кит|Африканският слон|Китовата акула|Жирафът",
            "What is the largest animal on Earth?|The blue whale|The African elephant|The whale shark|The giraffe",
            "Синият кит е дълъг почти 30 метра, почти колкото три автобуса.", "A blue whale can be almost 30 metres long, nearly as long as three buses."),
        q(A, "Кое е най-бързото сухоземно животно?|Гепардът|Лъвът|Конят|Зебрата",
            "Which is the fastest land animal?|The cheetah|The lion|The horse|The zebra",
            "Гепардът тича с близо 100 километра в час, но само за кратко.", "A cheetah can run at almost 100 kilometres an hour, but only for a short sprint."),
        q(A, "Колко крака има паякът?|8|6|10|4", "How many legs does a spider have?|8|6|10|4",
            "Паякът не е насекомо. Насекомите имат 6 крака.", "A spider is not an insect. Insects have 6 legs."),
        q(A, "Кое от тези животни е бозайник?|Делфинът|Акулата|Пъстървата|Октоподът",
            "Which of these animals is a mammal?|The dolphin|The shark|The trout|The octopus",
            "Делфините дишат въздух и хранят малките си с мляко.", "Dolphins breathe air and feed their babies milk."),
        q(A, "Кое е най-високото животно?|Жирафът|Слонът|Камилата|Щраусът",
            "Which is the tallest animal?|The giraffe|The elephant|The camel|The ostrich",
            "Жирафът може да е висок над 5 метра.", "A giraffe can be more than 5 metres tall."),
        q(A, "Как се казва малкото на кравата?|Теле|Агне|Яре|Жребче", "What is a baby cow called?|A calf|A lamb|A kid|A foal"),
        q(A, "Кое от тези животни спи зимен сън?|Мечката|Вълкът|Лисицата|Заекът",
            "Which of these animals sleeps through the winter?|The bear|The wolf|The fox|The hare",
            "Преди зимата мечката яде много, за да натрупа мазнини.", "Before winter a bear eats a lot to build up fat."),
        q(A, "Какво яде най-вече гигантската панда?|Бамбук|Риба|Мед|Банани",
            "What does a giant panda eat most of all?|Bamboo|Fish|Honey|Bananas",
            "Пандата яде бамбук по много часове всеки ден.", "A panda eats bamboo for many hours every day."),
        q(A, "Колко сърца има октоподът?|3|1|2|8", "How many hearts does an octopus have?|3|1|2|8",
            "Кръвта на октопода е синя.", "An octopus has blue blood."),
        q(A, "Коя от тези птици не може да лети?|Пингвинът|Орелът|Лястовицата|Щъркелът",
            "Which of these birds cannot fly?|The penguin|The eagle|The swallow|The stork",
            "Затова пък пингвините плуват отлично.", "Penguins are excellent swimmers instead."),
        q(A, "Кое животно може да сменя цвета си?|Хамелеонът|Таралежът|Костенурката|Змията",
            "Which animal can change its colour?|The chameleon|The hedgehog|The tortoise|The snake"),
        q(A, "Кое е най-голямото сухоземно животно?|Африканският слон|Носорогът|Хипопотамът|Жирафът",
            "Which is the largest land animal?|The African elephant|The rhinoceros|The hippopotamus|The giraffe",
            "Африканският слон може да тежи 6 тона.", "An African elephant can weigh 6 tonnes."),
        q(A, "С какво дишат рибите?|С хриле|С бели дробове|С кожата си|С перките си",
            "What do fish breathe with?|Gills|Lungs|Their skin|Their fins",
            "Хрилете взимат кислорода от водата.", "Gills take oxygen out of the water."),
        q(A, "Кой бозайник наистина може да лети?|Прилепът|Летящата катерица|Колибрито|Бръмбарът",
            "Which mammal can really fly?|The bat|The flying squirrel|The hummingbird|The beetle",
            "Летящата катерица само се плъзга във въздуха.", "A flying squirrel only glides."),
        q(A, "Колко крака има насекомото?|6|8|4|10", "How many legs does an insect have?|6|8|4|10"),

        // ---------------------------------------------------------------- nature
        q(N, "Кое е най-високото дърво в света?|Секвоята|Дъбът|Борът|Палмата",
            "Which is the tallest kind of tree in the world?|The redwood|The oak|The pine|The palm",
            "Най-високата секвоя е висока около 116 метра.", "The tallest redwood is about 116 metres tall."),
        q(N, "Как растенията правят храна от слънчевата светлина?|Чрез фотосинтеза|Чрез изпарение|Чрез опрашване|Чрез покълване",
            "How do plants make food from sunlight?|By photosynthesis|By evaporation|By pollination|By germination"),
        q(N, "Какъв газ отделят растенията през деня?|Кислород|Въглероден диоксид|Азот|Водород",
            "Which gas do plants give off during the day?|Oxygen|Carbon dioxide|Nitrogen|Hydrogen",
            "Растенията поемат въглероден диоксид и отделят кислород.", "Plants take in carbon dioxide and give off oxygen."),
        q(N, "Колко сезона има годината?|4|2|3|6", "How many seasons are there in a year?|4|2|3|6"),
        q(N, "При колко градуса по Целзий замръзва водата?|0|10|−10|100", "At how many degrees Celsius does water freeze?|0|10|−10|100"),
        q(N, "При колко градуса по Целзий кипи водата на морското равнище?|100|50|80|200",
            "At how many degrees Celsius does water boil at sea level?|100|50|80|200",
            "Високо в планината водата кипи при по-ниска температура.", "High up in the mountains water boils at a lower temperature."),
        q(N, "Кой е най-големият океан?|Тихият|Атлантическият|Индийският|Северният ледовит",
            "Which is the largest ocean?|The Pacific|The Atlantic|The Indian|The Arctic",
            "Тихият океан е по-голям от цялата суша на Земята, взета заедно.", "The Pacific is bigger than all the land on Earth put together."),
        q(N, "Кой е най-високият връх в света?|Еверест|К2|Монблан|Мусала",
            "Which is the highest mountain in the world?|Everest|K2|Mont Blanc|Musala",
            "Еверест е висок 8849 метра.", "Everest is 8,849 metres high."),
        q(N, "Коя е най-голямата гореща пустиня?|Сахара|Гоби|Калахари|Атакама",
            "Which is the largest hot desert?|The Sahara|The Gobi|The Kalahari|The Atacama",
            "Най-голямата пустиня изобщо е студената Антарктида.", "The largest desert of all is cold Antarctica."),
        q(N, "Колко цвята има дъгата?|7|5|6|9", "How many colours does a rainbow have?|7|5|6|9",
            "Червено, оранжево, жълто, зелено, синьо, тъмносиньо и виолетово.", "Red, orange, yellow, green, blue, indigo and violet."),
        q(N, "Какво събират пчелите от цветята?|Нектар|Вода|Листа|Семена", "What do bees collect from flowers?|Nectar|Water|Leaves|Seeds",
            "От нектара пчелите правят мед.", "Bees turn nectar into honey."),
        q(N, "Коя е най-голямата тропическа гора?|Амазонската|Конгоанската|Шварцвалд|Беловежката пуща",
            "Which is the largest tropical rainforest?|The Amazon|The Congo rainforest|The Black Forest|The Białowieża Forest"),
        q(N, "Какво показват кръговете в пъна на отрязано дърво?|Възрастта му|Височината му|Колко вода е пило|Колко листа е имало",
            "What do the rings in a tree stump show?|The tree's age|Its height|How much water it drank|How many leaves it had",
            "Всяка година дървото добавя по един пръстен.", "A tree adds one ring every year."),
        q(N, "Какво е вулканът?|Планина, от която може да изригне лава|Много дълбоко езеро|Голяма пещера|Ледена планина",
            "What is a volcano?|A mountain that can erupt lava|A very deep lake|A big cave|A mountain of ice"),

        // ---------------------------------------------------------------- space
        q(S, "Коя е най-голямата планета в Слънчевата система?|Юпитер|Сатурн|Нептун|Земята",
            "Which is the largest planet in the Solar System?|Jupiter|Saturn|Neptune|Earth",
            "В Юпитер биха се побрали над 1000 планети като Земята.", "More than 1,000 Earths could fit inside Jupiter."),
        q(S, "Коя планета е най-близо до Слънцето?|Меркурий|Венера|Марс|Земята", "Which planet is closest to the Sun?|Mercury|Venus|Mars|Earth"),
        q(S, "Коя планета наричат Червената планета?|Марс|Венера|Юпитер|Меркурий", "Which planet is called the Red Planet?|Mars|Venus|Jupiter|Mercury",
            "Марс е червен заради ръждата в прахта му.", "Mars is red because of rust in its dust."),
        q(S, "Колко планети има в Слънчевата система?|8|9|7|10", "How many planets are there in the Solar System?|8|9|7|10",
            "От 2006 година Плутон е джудже-планета.", "Since 2006 Pluto has been called a dwarf planet."),
        q(S, "Кой е първият човек в космоса?|Юрий Гагарин|Нийл Армстронг|Георги Иванов|Бъз Олдрин",
            "Who was the first person in space?|Yuri Gagarin|Neil Armstrong|Georgi Ivanov|Buzz Aldrin",
            "Гагарин полетя на 12 април 1961 година.", "Gagarin flew on 12 April 1961."),
        q(S, "Кой пръв стъпва на Луната?|Нийл Армстронг|Юрий Гагарин|Бъз Олдрин|Майкъл Колинс",
            "Who was the first person to walk on the Moon?|Neil Armstrong|Yuri Gagarin|Buzz Aldrin|Michael Collins",
            "Това става през 1969 година с мисията Аполо 11.", "It happened in 1969 on the Apollo 11 mission."),
        q(S, "Какво е Слънцето?|Звезда|Планета|Комета|Луна", "What is the Sun?|A star|A planet|A comet|A moon",
            "В Слънцето биха се побрали над милион Земи.", "More than a million Earths could fit inside the Sun."),
        q(S, "Коя планета е известна с ярките си пръстени?|Сатурн|Марс|Венера|Меркурий",
            "Which planet is famous for its bright rings?|Saturn|Mars|Venus|Mercury",
            "Пръстените на Сатурн са от лед и камъни.", "Saturn's rings are made of ice and rock."),
        q(S, "За колко време Земята обикаля около Слънцето?|За около 365 дни|За 30 дни|За 24 часа|За 7 дни",
            "How long does the Earth take to go around the Sun?|About 365 days|30 days|24 hours|7 days"),
        q(S, "За колко време Земята се завърта около оста си?|За около 24 часа|За 12 часа|За 7 дни|За 365 дни",
            "How long does the Earth take to spin once on its axis?|About 24 hours|12 hours|7 days|365 days",
            "Затова имаме ден и нощ.", "That is why we have day and night."),
        q(S, "Как се казва нашата галактика?|Млечния път|Андромеда|Голямото Магеланово облако|Триъгълник",
            "What is our galaxy called?|The Milky Way|Andromeda|The Large Magellanic Cloud|Triangulum"),
        q(S, "Коя е най-горещата планета?|Венера|Меркурий|Марс|Юпитер", "Which is the hottest planet?|Venus|Mercury|Mars|Jupiter",
            "Венера е по-гореща от Меркурий заради гъстата си атмосфера.", "Venus is hotter than Mercury because of its thick atmosphere."),
        q(S, "Коя е най-близката звезда до Земята?|Слънцето|Сириус|Полярната звезда|Проксима Кентавър",
            "Which star is closest to the Earth?|The Sun|Sirius|The North Star|Proxima Centauri",
            "Следващата най-близка звезда е Проксима Кентавър.", "The next closest star is Proxima Centauri."),
        q(S, "Колко е далеч Луната от Земята?|Около 384 000 км|Около 1000 км|Около 38 000 км|Около 150 милиона км",
            "How far is the Moon from the Earth?|About 384,000 km|About 1,000 km|About 38,000 km|About 150 million km",
            "150 милиона километра е разстоянието до Слънцето.", "150 million km is the distance to the Sun."),
        q(S, "Какво е Луната за Земята?|Естествен спътник|Звезда|Планета|Комета", "What is the Moon to the Earth?|Its natural satellite|A star|A planet|A comet"),

        // ---------------------------------------------------------------- science
        q(C, "Каква е химичната формула на водата?|H2O|CO2|O2|NaCl", "What is the chemical formula of water?|H2O|CO2|O2|NaCl",
            "Два атома водород и един атом кислород.", "Two hydrogen atoms and one oxygen atom."),
        q(C, "Кое е най-твърдото естествено вещество?|Диамантът|Желязото|Гранитът|Златото",
            "What is the hardest natural material?|Diamond|Iron|Granite|Gold",
            "Диамантът е от въглерод, като графита в моливите.", "A diamond is made of carbon, just like the graphite in pencils."),
        q(C, "Колко кости има тялото на възрастен човек?|206|106|306|186", "How many bones are there in an adult human body?|206|106|306|186",
            "Бебетата имат около 300 кости и някои от тях после се сливат.", "Babies have about 300 bones, and some of them join together later."),
        q(C, "Кой орган изпомпва кръвта в тялото?|Сърцето|Белите дробове|Черният дроб|Стомахът",
            "Which organ pumps blood around the body?|The heart|The lungs|The liver|The stomach"),
        q(C, "Коя е най-дългата кост в тялото?|Бедрената кост|Раменната кост|Пищялът|Лъчевата кост",
            "Which is the longest bone in the body?|The thigh bone|The upper arm bone|The shin bone|The forearm bone"),
        q(C, "Кой е най-големият орган на човешкото тяло?|Кожата|Черният дроб|Мозъкът|Сърцето",
            "What is the largest organ of the human body?|The skin|The liver|The brain|The heart"),
        q(C, "Какво привлича магнитът?|Желязо|Дърво|Пластмаса|Стъкло", "What does a magnet attract?|Iron|Wood|Plastic|Glass"),
        q(C, "Какво измерва термометърът?|Температурата|Теглото|Скоростта|Налягането", "What does a thermometer measure?|Temperature|Weight|Speed|Pressure"),
        q(C, "Кой учен описва закона за всемирното привличане?|Исак Нютон|Алберт Айнщайн|Галилео Галилей|Никола Тесла",
            "Which scientist described the law of universal gravitation?|Isaac Newton|Albert Einstein|Galileo Galilei|Nikola Tesla",
            "Според легендата идеята му дава падаща ябълка.", "Legend says a falling apple gave him the idea."),
        q(C, "Колко бързо се движи светлината?|Около 300 000 км в секунда|Около 300 км в секунда|Около 3000 км в секунда|Около 30 км в секунда",
            "How fast does light travel?|About 300,000 km per second|About 300 km per second|About 3,000 km per second|About 30 km per second",
            "Светлината от Слънцето стига до нас за около 8 минути.", "Sunlight takes about 8 minutes to reach us."),
        q(C, "Кой газ от въздуха е нужен на тялото ни, за да живее?|Кислородът|Хелият|Въглеродният диоксид|Азотът",
            "Which gas in the air does our body need to live?|Oxygen|Helium|Carbon dioxide|Nitrogen"),
        q(C, "Кой газ е най-много във въздуха?|Азотът|Кислородът|Въглеродният диоксид|Водородът",
            "Which gas makes up most of the air?|Nitrogen|Oxygen|Carbon dioxide|Hydrogen",
            "Азотът е около 78 процента от въздуха.", "Nitrogen makes up about 78 per cent of the air."),
        q(C, "Колко зъба има възрастен човек заедно с мъдреците?|32|20|24|40", "How many teeth does an adult have, wisdom teeth included?|32|20|24|40",
            "Децата имат 20 млечни зъба.", "Children have 20 baby teeth."),
        q(C, "Кой създава теорията на относителността?|Алберт Айнщайн|Исак Нютон|Мария Кюри|Чарлз Дарвин",
            "Who came up with the theory of relativity?|Albert Einstein|Isaac Newton|Marie Curie|Charles Darwin"),
        q(C, "Коя учена печели Нобелова награда в две различни науки?|Мария Кюри|Ада Лъвлейс|Розалинд Франклин|Джейн Гудол",
            "Which scientist won Nobel Prizes in two different sciences?|Marie Curie|Ada Lovelace|Rosalind Franklin|Jane Goodall",
            "Тя ги печели по физика и по химия.", "She won them in physics and in chemistry."),
        q(C, "Как се казва най-малката частица на химичния елемент?|Атом|Клетка|Песъчинка|Капка",
            "What is the smallest particle of a chemical element called?|An atom|A cell|A grain of sand|A drop"),

        // ---------------------------------------------------------------- geography
        q(G, "Коя е столицата на Франция?|Париж|Лион|Марсилия|Ница", "What is the capital of France?|Paris|Lyon|Marseille|Nice"),
        q(G, "Коя е най-голямата страна в света?|Русия|Канада|Китай|САЩ", "Which is the largest country in the world?|Russia|Canada|China|The USA"),
        q(G, "Коя е столицата на Италия?|Рим|Милано|Венеция|Неапол", "What is the capital of Italy?|Rome|Milan|Venice|Naples"),
        q(G, "На кой континент е Египет?|Африка|Азия|Европа|Южна Америка", "On which continent is Egypt?|Africa|Asia|Europe|South America"),
        q(G, "Коя е столицата на Япония?|Токио|Осака|Киото|Пекин", "What is the capital of Japan?|Tokyo|Osaka|Kyoto|Beijing"),
        q(G, "Кой е най-големият остров в света?|Гренландия|Мадагаскар|Исландия|Великобритания",
            "Which is the largest island in the world?|Greenland|Madagascar|Iceland|Great Britain"),
        q(G, "В коя страна са пирамидите в Гиза?|Египет|Мексико|Гърция|Индия", "In which country are the pyramids of Giza?|Egypt|Mexico|Greece|India"),
        q(G, "Коя е най-дългата река в Европа?|Волга|Дунав|Рейн|Днепър", "Which is the longest river in Europe?|The Volga|The Danube|The Rhine|The Dnieper",
            "Дунав е втори и тече по границата на България.", "The Danube is second, and it flows along Bulgaria's border."),
        q(G, "Коя е столицата на Германия?|Берлин|Мюнхен|Хамбург|Франкфурт", "What is the capital of Germany?|Berlin|Munich|Hamburg|Frankfurt"),
        q(G, "Кой океан е между Европа и Америка?|Атлантическият|Тихият|Индийският|Южният",
            "Which ocean lies between Europe and America?|The Atlantic|The Pacific|The Indian|The Southern"),
        q(G, "Коя страна има най-много жители?|Индия|Китай|САЩ|Индонезия", "Which country has the most people?|India|China|The USA|Indonesia",
            "През 2023 година Индия изпревари Китай.", "India overtook China in 2023."),
        q(G, "Коя е столицата на Великобритания?|Лондон|Манчестър|Ливърпул|Бирмингам",
            "What is the capital of the United Kingdom?|London|Manchester|Liverpool|Birmingham"),
        q(G, "В коя страна е Барселона?|Испания|Италия|Португалия|Франция", "In which country is Barcelona?|Spain|Italy|Portugal|France"),
        q(G, "Коя страна прилича на ботуш на картата?|Италия|Гърция|Испания|Норвегия", "Which country looks like a boot on the map?|Italy|Greece|Spain|Norway"),
        q(G, "Кое е най-дълбокото езеро в света?|Байкал|Виктория|Мичиган|Охрид",
            "Which is the deepest lake in the world?|Lake Baikal|Lake Victoria|Lake Michigan|Lake Ohrid",
            "Байкал е дълбок над 1600 метра.", "Lake Baikal is more than 1,600 metres deep."),
        q(G, "Коя е столицата на Гърция?|Атина|Солун|Патра|Лариса", "What is the capital of Greece?|Athens|Thessaloniki|Patras|Larissa"),

        // ---------------------------------------------------------------- Bulgaria
        q(B, "Коя е столицата на България?|София|Пловдив|Варна|Велико Търново", "What is the capital of Bulgaria?|Sofia|Plovdiv|Varna|Veliko Tarnovo"),
        q(B, "Кой е най-високият връх в България?|Мусала|Вихрен|Ботев|Черни връх", "Which is the highest peak in Bulgaria?|Musala|Vihren|Botev|Cherni Vrah",
            "Мусала е висок 2925 метра и е най-високият връх на Балканите.", "Musala is 2,925 metres high, the highest peak in the Balkans."),
        q(B, "Кой е първият българин в космоса?|Георги Иванов|Александър Александров|Юрий Гагарин|Владимир Ремек",
            "Who was the first Bulgarian in space?|Georgi Ivanov|Aleksandar Aleksandrov|Yuri Gagarin|Vladimír Remek",
            "Той полетя през 1979 година.", "He flew in 1979."),
        q(B, "Кой е написал романа „Под игото“?|Иван Вазов|Христо Ботев|Алеко Константинов|Елин Пелин",
            "Who wrote the novel “Under the Yoke”?|Ivan Vazov|Hristo Botev|Aleko Konstantinov|Elin Pelin"),
        q(B, "Кои братя създават първата славянска азбука?|Кирил и Методий|Климент и Наум|Борис и Симеон|Асен и Петър",
            "Which brothers created the first Slavic alphabet?|Cyril and Methodius|Clement and Naum|Boris and Simeon|Asen and Peter",
            "Тази първа азбука е глаголицата.", "That first alphabet is called Glagolitic."),
        q(B, "Кога е празникът на славянската писменост и култура?|24 май|3 март|6 септември|1 ноември",
            "When is Bulgaria's Day of Slavic Writing and Culture?|24 May|3 March|6 September|1 November"),
        q(B, "Кой е националният празник на България?|3 март|24 май|6 септември|22 септември",
            "What is Bulgaria's national holiday?|3 March|24 May|6 September|22 September",
            "На 3 март 1878 година е подписан Санстефанският мирен договор.", "The Treaty of San Stefano was signed on 3 March 1878."),
        q(B, "Коя е най-дългата река, която тече само в България?|Искър|Марица|Струма|Янтра",
            "Which is the longest river that flows only through Bulgaria?|The Iskar|The Maritsa|The Struma|The Yantra",
            "Искър е дълъг 368 километра и минава през Стара планина.", "The Iskar is 368 km long and cuts through the Balkan Mountains."),
        q(B, "Кое море е на българския бряг?|Черно море|Средиземно море|Егейско море|Адриатическо море",
            "Which sea is on Bulgaria's coast?|The Black Sea|The Mediterranean|The Aegean|The Adriatic"),
        q(B, "През коя година е основана българската държава?|681|1878|865|1396", "In which year was the Bulgarian state founded?|681|1878|865|1396",
            "През 1878 година България е освободена.", "Bulgaria was liberated in 1878."),
        q(B, "Кой хан основава българската държава на Дунав?|Аспарух|Крум|Тервел|Кубрат",
            "Which khan founded the Bulgarian state on the Danube?|Asparuh|Krum|Tervel|Kubrat"),
        q(B, "Кого наричат Апостола на свободата?|Васил Левски|Христо Ботев|Георги Раковски|Любен Каравелов",
            "Who is called the Apostle of Freedom?|Vasil Levski|Hristo Botev|Georgi Rakovski|Lyuben Karavelov"),
        q(B, "С каква валута се плаща в България от 2026 година?|С евро|С лев|С долар|С франк",
            "What currency has Bulgaria used since 2026?|The euro|The lev|The dollar|The franc",
            "До края на 2025 година се плащаше с левове.", "Until the end of 2025 people paid in leva."),
        q(B, "В кой град е крепостта Царевец?|Велико Търново|Пловдив|Шумен|Видин", "In which town is the Tsarevets fortress?|Veliko Tarnovo|Plovdiv|Shumen|Vidin"),
        q(B, "От кое цвете в България се прави прочутото масло?|Розата|Лалето|Кокичето|Маргаритката",
            "Which flower is Bulgaria famous for making oil from?|The rose|The tulip|The snowdrop|The daisy",
            "Долината на розите е около Казанлък.", "The Valley of Roses is around Kazanlak."),
        q(B, "Какви са цветовете на българското знаме отгоре надолу?|Бяло, зелено, червено|Червено, бяло, зелено|Зелено, бяло, червено|Бяло, червено, зелено",
            "What are the colours of the Bulgarian flag from top to bottom?|White, green, red|Red, white, green|Green, white, red|White, red, green"),
        q(B, "В кой град е намерено най-старото обработено злато в света?|Варна|Пловдив|Бургас|Русе",
            "In which city was the world's oldest worked gold found?|Varna|Plovdiv|Burgas|Ruse",
            "Това злато е на повече от 6000 години.", "That gold is more than 6,000 years old."),
        q(B, "Кой поет пише стихотворението „Хаджи Димитър“?|Христо Ботев|Иван Вазов|Пейо Яворов|Христо Смирненски",
            "Which poet wrote the poem “Hadzhi Dimitar”?|Hristo Botev|Ivan Vazov|Peyo Yavorov|Hristo Smirnenski"),

        // ---------------------------------------------------------------- history
        q(H, "Кой е построил пирамидите в Гиза?|Древните египтяни|Римляните|Древните гърци|Викингите",
            "Who built the pyramids of Giza?|The ancient Egyptians|The Romans|The ancient Greeks|The Vikings"),
        q(H, "През коя година Колумб достига Америка?|1492|1392|1592|1789", "In which year did Columbus reach America?|1492|1392|1592|1789"),
        q(H, "Кой е първият президент на САЩ?|Джордж Вашингтон|Ейбрахам Линкълн|Томас Джеферсън|Джон Адамс",
            "Who was the first president of the USA?|George Washington|Abraham Lincoln|Thomas Jefferson|John Adams"),
        q(H, "Как се казва прочутата дълга стена в Китай?|Великата китайска стена|Берлинската стена|Адриановият вал|Стената на плача",
            "What is the famous long wall in China called?|The Great Wall|The Berlin Wall|Hadrian's Wall|The Western Wall"),
        q(H, "През коя година пада Берлинската стена?|1989|1961|1945|2001", "In which year did the Berlin Wall fall?|1989|1961|1945|2001"),
        q(H, "Кой въвежда в Европа печатането с подвижни букви?|Йоханес Гутенберг|Леонардо да Винчи|Галилео Галилей|Исак Нютон",
            "Who brought printing with movable type to Europe?|Johannes Gutenberg|Leonardo da Vinci|Galileo Galilei|Isaac Newton"),
        q(H, "През коя година завършва Втората световна война?|1945|1918|1939|1950", "In which year did the Second World War end?|1945|1918|1939|1950"),
        q(H, "Кой римски владетел е убит през 44 година преди новата ера?|Юлий Цезар|Нерон|Александър Македонски|Спартак",
            "Which Roman leader was killed in 44 BC?|Julius Caesar|Nero|Alexander the Great|Spartacus"),
        q(H, "Откъде идват викингите?|От Скандинавия|От Италия|От Египет|От Испания", "Where did the Vikings come from?|Scandinavia|Italy|Egypt|Spain"),
        q(H, "Кой древен град е затрупан от вулкана Везувий?|Помпей|Атина|Троя|Картаген",
            "Which ancient city was buried by the volcano Vesuvius?|Pompeii|Athens|Troy|Carthage",
            "Това става през 79 година.", "It happened in the year 79."),
        q(H, "Кой е учителят на Александър Македонски?|Аристотел|Платон|Сократ|Питагор", "Who was Alexander the Great's teacher?|Aristotle|Plato|Socrates|Pythagoras"),
        q(H, "Коя страна изстрелва първия изкуствен спътник през 1957 година?|СССР|САЩ|Китай|Франция",
            "Which country launched the first artificial satellite in 1957?|The USSR|The USA|China|France",
            "Спътникът се казва Спутник 1.", "It was called Sputnik 1."),
        q(H, "Кой кораб потъва през 1912 година след удар в айсберг?|Титаник|Британик|Лузитания|Санта Мария",
            "Which ship sank in 1912 after hitting an iceberg?|The Titanic|The Britannic|The Lusitania|The Santa Maria"),
        q(H, "Кой народ построява Мачу Пикчу?|Инките|Маите|Ацтеките|Египтяните", "Which people built Machu Picchu?|The Incas|The Maya|The Aztecs|The Egyptians"),
        q(H, "Кой град става столица на Първата българска държава след Плиска?|Преслав|Търново|София|Пловдив",
            "Which city became the capital of the First Bulgarian Empire after Pliska?|Preslav|Tarnovo|Sofia|Plovdiv"),

        // ---------------------------------------------------------------- sport
        q(P, "Колко играчи има един футболен отбор на терена?|11|10|9|12", "How many players does a football team have on the pitch?|11|10|9|12"),
        q(P, "През колко години се провеждат летните олимпийски игри?|4|2|3|5",
            "How often are the Summer Olympic Games held?|Every 4 years|Every 2 years|Every 3 years|Every 5 years"),
        q(P, "В кой град са първите модерни олимпийски игри през 1896 година?|Атина|Париж|Лондон|Рим",
            "Where were the first modern Olympic Games held in 1896?|Athens|Paris|London|Rome"),
        q(P, "Колко кръга има на олимпийското знаме?|5|4|6|7", "How many rings are on the Olympic flag?|5|4|6|7"),
        q(P, "В кой спорт се играе с ракета и перце?|Бадминтон|Тенис|Скуош|Волейбол", "Which sport is played with a racket and a shuttlecock?|Badminton|Tennis|Squash|Volleyball"),
        q(P, "Колко точки дава кош отдалеч, зад линията, в баскетбола?|3|2|1|4", "In basketball, how many points is a shot from behind the long line worth?|3|2|1|4"),
        q(P, "Кой български футболист печели Златната топка през 1994 година?|Христо Стоичков|Димитър Бербатов|Красимир Балъков|Йордан Лечков",
            "Which Bulgarian footballer won the Ballon d'Or in 1994?|Hristo Stoichkov|Dimitar Berbatov|Krasimir Balakov|Yordan Letchkov"),
        q(P, "В коя игра обявяваш мат?|Шах|Бокс|Джудо|Фехтовка", "In which game do you announce checkmate?|Chess|Boxing|Judo|Fencing"),
        q(P, "Колко минути трае футболен мач без продълженията?|90|60|80|100", "How many minutes does a football match last without extra time?|90|60|80|100"),
        q(P, "Коя страна печели златото при ансамблите по художествена гимнастика в Токио?|България|Русия|Италия|Израел",
            "Which country won gold in the rhythmic gymnastics group event in Tokyo?|Bulgaria|Russia|Italy|Israel",
            "Нашите гимнастички ги наричат Златните момичета.", "The Bulgarian gymnasts are nicknamed the Golden Girls."),
        q(P, "Колко играчи има един волейболен отбор на игрището?|6|5|7|11", "How many players does a volleyball team have on the court?|6|5|7|11"),
        q(P, "Кой спорт играе Григор Димитров?|Тенис|Футбол|Баскетбол|Плуване", "Which sport does Grigor Dimitrov play?|Tennis|Football|Basketball|Swimming"),
        q(P, "Каква е формата на топката за ръгби?|Овална|Кръгла|Квадратна|Плоска", "What shape is a rugby ball?|Oval|Round|Square|Flat"),
        q(P, "Колко дупки има стандартното игрище за голф?|18|9|12|20", "How many holes does a standard golf course have?|18|9|12|20"),
        q(P, "Кой държи световния рекорд на 100 метра?|Юсейн Болт|Карл Луис|Майкъл Фелпс|Майкъл Джонсън",
            "Who holds the 100 metres world record?|Usain Bolt|Carl Lewis|Michael Phelps|Michael Johnson",
            "Болт пробяга 100 метра за 9,58 секунди през 2009 година.", "Bolt ran 100 metres in 9.58 seconds in 2009."),

        // ---------------------------------------------------------------- art
        q(R, "Кой е нарисувал Мона Лиза?|Леонардо да Винчи|Микеланджело|Пабло Пикасо|Винсент ван Гог",
            "Who painted the Mona Lisa?|Leonardo da Vinci|Michelangelo|Pablo Picasso|Vincent van Gogh",
            "Картината е в музея Лувър в Париж.", "The painting hangs in the Louvre in Paris."),
        q(R, "Кой композитор продължава да пише музика, след като оглушава?|Лудвиг ван Бетовен|Волфганг Амадеус Моцарт|Йохан Себастиан Бах|Антонио Вивалди",
            "Which composer kept writing music after he went deaf?|Ludwig van Beethoven|Wolfgang Amadeus Mozart|Johann Sebastian Bach|Antonio Vivaldi"),
        q(R, "Кой е написал „Ромео и Жулиета“?|Уилям Шекспир|Чарлз Дикенс|Виктор Юго|Марк Твен",
            "Who wrote “Romeo and Juliet”?|William Shakespeare|Charles Dickens|Victor Hugo|Mark Twain"),
        q(R, "Колко клавиша има стандартното пиано?|88|66|100|52", "How many keys does a standard piano have?|88|66|100|52",
            "52 от тях са бели, а 36 са черни.", "52 of them are white and 36 are black."),
        q(R, "Кой е написал „Малкият принц“?|Антоан дьо Сент-Екзюпери|Жул Верн|Ханс Кристиан Андерсен|Шарл Перо",
            "Who wrote “The Little Prince”?|Antoine de Saint-Exupéry|Jules Verne|Hans Christian Andersen|Charles Perrault"),
        q(R, "Кой е написал „Грозното патенце“?|Ханс Кристиан Андерсен|Братя Грим|Шарл Перо|Езоп",
            "Who wrote “The Ugly Duckling”?|Hans Christian Andersen|The Brothers Grimm|Charles Perrault|Aesop"),
        q(R, "Кой е нарисувал „Звездна нощ“?|Винсент ван Гог|Клод Моне|Салвадор Дали|Рембранд",
            "Who painted “The Starry Night”?|Vincent van Gogh|Claude Monet|Salvador Dalí|Rembrandt"),
        q(R, "Колко струни има обикновената китара?|6|4|5|12", "How many strings does an ordinary guitar have?|6|4|5|12"),
        q(R, "Кой е композирал „Четирите сезона“?|Антонио Вивалди|Волфганг Амадеус Моцарт|Лудвиг ван Бетовен|Йохан Щраус",
            "Who composed “The Four Seasons”?|Antonio Vivaldi|Wolfgang Amadeus Mozart|Ludwig van Beethoven|Johann Strauss"),
        q(R, "Кой е създал Мики Маус?|Уолт Дисни|Чарлз Шулц|Стан Лий|Джим Хенсън", "Who created Mickey Mouse?|Walt Disney|Charles Schulz|Stan Lee|Jim Henson"),
        q(R, "Коя писателка е създала Хари Потър?|Джоан Роулинг|Астрид Линдгрен|Туве Янсон|Енид Блайтън",
            "Which author created Harry Potter?|J. K. Rowling|Astrid Lindgren|Tove Jansson|Enid Blyton"),
        q(R, "Коя писателка е създала Пипи Дългото чорапче?|Астрид Линдгрен|Селма Лагерльоф|Туве Янсон|Джоан Роулинг",
            "Which author created Pippi Longstocking?|Astrid Lindgren|Selma Lagerlöf|Tove Jansson|J. K. Rowling"),
        q(R, "Кой е написал „Бай Ганьо“?|Алеко Константинов|Иван Вазов|Елин Пелин|Йордан Йовков",
            "Who wrote “Bay Ganyo”?|Aleko Konstantinov|Ivan Vazov|Elin Pelin|Yordan Yovkov"),
        q(R, "Кой е написал „Ян Бибиян“?|Елин Пелин|Иван Вазов|Ран Босилек|Ангел Каралийчев",
            "Who wrote “Yan Bibiyan”?|Elin Pelin|Ivan Vazov|Ran Bosilek|Angel Karaliychev"),
        q(R, "Колко линии има нотоносецът?|5|4|6|7", "How many lines are there in a musical staff?|5|4|6|7"),
        q(R, "Кой е изрисувал тавана на Сикстинската капела?|Микеланджело|Рафаело|Леонардо да Винчи|Донатело",
            "Who painted the ceiling of the Sistine Chapel?|Michelangelo|Raphael|Leonardo da Vinci|Donatello"),

        // ---------------------------------------------------------------- everyday
        q(E, "Колко дни има седмицата?|7|5|6|10", "How many days are there in a week?|7|5|6|10"),
        q(E, "Колко месеца има годината?|12|10|11|13", "How many months are there in a year?|12|10|11|13"),
        q(E, "Колко минути има един час?|60|100|30|90", "How many minutes are there in an hour?|60|100|30|90"),
        q(E, "Кой месец има най-малко дни?|Февруари|Април|Юни|Ноември", "Which month has the fewest days?|February|April|June|November"),
        q(E, "Колко дни има високосната година?|366|365|364|360", "How many days are there in a leap year?|366|365|364|360"),
        q(E, "Кои две бои смесваме, за да получим зелено?|Синя и жълта|Червена и синя|Червена и жълта|Бяла и черна",
            "Which two paints make green when mixed?|Blue and yellow|Red and blue|Red and yellow|White and black"),
        q(E, "На какъв светофар пресичаме улицата?|На зелен|На червен|На жълт|На син", "At which traffic light colour do we cross the street?|Green|Red|Yellow|Blue"),
        q(E, "Кой е единният европейски номер за спешни случаи?|112|911|999|555", "What is the single European emergency number?|112|911|999|555",
            "112 работи във всички държави от Европейския съюз.", "112 works in every country of the European Union."),
        q(E, "Колко е една дузина?|12|10|6|20", "How many is a dozen?|12|10|6|20"),
        q(E, "От какво най-често се прави хартията?|От дърво|От пластмаса|От камък|От стъкло", "What is paper usually made from?|Wood|Plastic|Stone|Glass"),
        q(E, "Колко сантиметра има един метър?|100|10|1000|60", "How many centimetres are there in a metre?|100|10|1000|60"),
        q(E, "Колко грама има един килограм?|1000|100|10|500", "How many grams are there in a kilogram?|1000|100|10|500"),
        q(E, "Колко часа има денонощието?|24|12|20|48", "How many hours are there in a day and a night?|24|12|20|48"),
        q(E, "От кое животно се получава най-много вълна?|От овцата|От кравата|От кокошката|От прасето",
            "Which animal gives us most of our wool?|The sheep|The cow|The hen|The pig"),
        q(E, "Колко страни има шестоъгълникът?|6|5|8|7", "How many sides does a hexagon have?|6|5|8|7",
            "Пчелите правят килийките на питата шестоъгълни.", "Bees build the cells of their honeycomb as hexagons."),
        q(E, "Колко светлини има светофарът за колите?|3|2|4|5", "How many lights does a traffic light for cars have?|3|2|4|5"),
    )
}
