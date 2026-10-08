package com.talkto.core.tools

import com.talkto.core.i18n.Lang
import kotlin.random.Random

/** Small offline games and pastimes, in ZnaiKo's current [lang]. [random] is injectable so tests are deterministic. */
class FunPack(private val random: Random = Random.Default, private val lang: () -> Lang = { Lang.BG }) {

    fun dice(count: Int = 1, sides: Int = 6): List<Int> =
        List(count.coerceIn(1, 10)) { random.nextInt(1, sides.coerceIn(2, 1000) + 1) }

    fun coin(): String = if (random.nextBoolean()) lang().pick("Ези", "Heads") else lang().pick("Тура", "Tails")

    fun number(from: Int, to: Int): Int = random.nextInt(minOf(from, to), maxOf(from, to) + 1)

    fun joke(): String = (if (lang() == Lang.BG) JOKES else JOKES_EN).let { it[random.nextInt(it.size)] }

    fun fact(): String = (if (lang() == Lang.BG) FACTS else FACTS_EN).let { it[random.nextInt(it.size)] }

    enum class Hand(val bg: String, val en: String) {
        ROCK("камък", "rock"), SCISSORS("ножица", "scissors"), PAPER("хартия", "paper");

        fun name(lang: Lang) = lang.pick(bg, en)
    }

    /** One round: the pet's hand, the verdict line and whether the user won. */
    data class RpsResult(val pet: Hand, val verdict: String, val userWon: Boolean)

    /** Rock-paper-scissors against the pet. */
    fun rps(player: Hand): RpsResult {
        val pet = Hand.entries[random.nextInt(3)]
        val l = lang()
        val userWon = (player == Hand.ROCK && pet == Hand.SCISSORS) ||
            (player == Hand.SCISSORS && pet == Hand.PAPER) ||
            (player == Hand.PAPER && pet == Hand.ROCK)
        val verdict = when {
            pet == player -> l.pick("Равни сме!", "It's a draw!")
            userWon -> l.pick("Ти печелиш!", "You win!")
            else -> l.pick("Аз печеля!", "I win!")
        }
        return RpsResult(pet, verdict, userWon)
    }

    /** "Guess my number" 1..100. One game at a time; the caller keeps the instance. */
    class GuessGame(random: Random, val max: Int = 100, private val lang: () -> Lang = { Lang.BG }) {
        val secret: Int = random.nextInt(1, max + 1)
        var attempts: Int = 0
            private set

        /** Returns the reply and whether the game is over. */
        fun guess(n: Int): Pair<String, Boolean> {
            attempts++
            val l = lang()
            return when {
                n == secret -> l.pick("Позна! Числото е $secret, отне ти $attempts опита.", "You got it! The number is $secret. It took you $attempts tries.") to true
                n < secret -> l.pick("Нагоре! По-голямо е от $n.", "Higher! It's bigger than $n.") to false
                else -> l.pick("Надолу! По-малко е от $n.", "Lower! It's smaller than $n.") to false
            }
        }
    }

    fun newGuessGame() = GuessGame(random, lang = lang)

    companion object {
        val JOKES = listOf(
            "Защо компютърът отиде на лекар? Хвана вирус, но се оказа, че просто има твърде много отворени табове.",
            "Батерията ми и аз имаме нещо общо: и двамата сме на 15%, когато ни трябваш най-много.",
            "Програмист влиза в бар. Поръчва 1 бира. Поръчва 0 бири. Поръчва -1 бири. Барът гръмва.",
            "Казах на телефона си, че имам нужда от почивка. Той ми показа 47 известия.",
            "Защо Wi-Fi-то е като котка? Идва, когато си поиска, и изчезва точно когато го викаш.",
            "Имам шега за UDP, но не съм сигурен дали ще я получиш.",
            "Моят любим спорт? Скачане между приложенията.",
            "Защо мишката не говори с клавиатурата? Защото тя винаги я натиска.",
            "Спасих един файл днес. Беше в кошчето, но с надежда.",
            "Облакът е просто чужд компютър, който се прави на небе.",
        )
        val JOKES_EN = listOf(
            "Why did the computer go to the doctor? It caught a virus, but it turned out it just had too many tabs open.",
            "My battery and I have something in common: we're both on 15% when you need us most.",
            "A robot walks into a bakery. It orders 1 cake. Then 0 cakes. Then minus 1 cakes. The bakery crashes.",
            "I told my phone I needed a break. It showed me 47 notifications.",
            "Why is Wi-Fi like a cat? It comes when it wants to and disappears exactly when you call it.",
            "I have a joke about UDP, but I'm not sure you'll get it.",
            "My favourite sport? Jumping between apps.",
            "Why doesn't the mouse talk to the keyboard? The keyboard keeps pushing its buttons.",
            "I rescued a file today. It was in the bin, but it still had hope.",
            "The cloud is just someone else's computer pretending to be the sky.",
        )
        val FACTS_EN = listOf(
            "The first camera phone went on sale in Japan in 2000, and its pictures had 0.11 megapixels.",
            "An octopus has three hearts and blue blood.",
            "Honey never goes off: honey more than 3,000 years old has been found in Egyptian tombs, still fit to eat.",
            "Lightning is about five times hotter than the surface of the Sun.",
            "The word robot comes from the Czech word robota, which means forced labour.",
            "Banana plants are giant herbs, and bananas are berries in the botanical sense.",
            "A blue whale's heart is as big as a small car.",
            "The first Tamagotchi came out in 1996, and by 1998 more than 40 million had been sold.",
            "A day on Venus is longer than a year on Venus.",
            "The Cyrillic alphabet was created at the Preslav Literary School at the end of the 9th century.",
        )
        val FACTS = listOf(
            "Първият камера-телефон се продава в Япония през 2000 г. и снима с 0,11 мегапиксела.",
            "Октоподът има три сърца и синя кръв.",
            "Медът не се разваля: в египетски гробници е намиран мед на над 3000 години, още годен за ядене.",
            "Мълнията е около пет пъти по-гореща от повърхността на Слънцето.",
            "Думата „робот“ идва от чешкото „robota“, тоест ангарийски труд.",
            "Банановите растения са треви, а бананите са горски плодове в ботаническия смисъл.",
            "Сърцето на синия кит е голямо колкото малка кола.",
            "Първото тамагочи се появява през 1996 г., а до 1998 г. са продадени над 40 милиона броя.",
            "Един ден на Венера е по-дълъг от една година на Венера.",
            "Кирилицата е създадена в Преславската книжовна школа в края на IX век.",
        )
    }
}
