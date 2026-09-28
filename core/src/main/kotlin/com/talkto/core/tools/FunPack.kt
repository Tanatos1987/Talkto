package com.talkto.core.tools

import kotlin.random.Random

/** Small offline games and pastimes. [random] is injectable so tests are deterministic. */
class FunPack(private val random: Random = Random.Default) {

    fun dice(count: Int = 1, sides: Int = 6): List<Int> =
        List(count.coerceIn(1, 10)) { random.nextInt(1, sides.coerceIn(2, 1000) + 1) }

    fun coin(): String = if (random.nextBoolean()) "Ези" else "Тура"

    fun number(from: Int, to: Int): Int = random.nextInt(minOf(from, to), maxOf(from, to) + 1)

    fun joke(): String = JOKES[random.nextInt(JOKES.size)]

    fun fact(): String = FACTS[random.nextInt(FACTS.size)]

    enum class Hand(val bg: String) { ROCK("камък"), SCISSORS("ножица"), PAPER("хартия") }

    /** Rock-paper-scissors against the pet. Returns the pet's hand and the verdict line. */
    fun rps(player: Hand): Pair<Hand, String> {
        val pet = Hand.entries[random.nextInt(3)]
        val verdict = when {
            pet == player -> "Равни сме!"
            (player == Hand.ROCK && pet == Hand.SCISSORS) ||
                (player == Hand.SCISSORS && pet == Hand.PAPER) ||
                (player == Hand.PAPER && pet == Hand.ROCK) -> "Ти печелиш!"
            else -> "Аз печеля!"
        }
        return pet to verdict
    }

    /** "Guess my number" 1..100. One game at a time; the caller keeps the instance. */
    class GuessGame(random: Random, val max: Int = 100) {
        val secret: Int = random.nextInt(1, max + 1)
        var attempts: Int = 0
            private set

        /** Returns the reply and whether the game is over. */
        fun guess(n: Int): Pair<String, Boolean> {
            attempts++
            return when {
                n == secret -> "Позна! Числото е $secret, отне ти $attempts опита." to true
                n < secret -> "Нагоре! По-голямо е от $n." to false
                else -> "Надолу! По-малко е от $n." to false
            }
        }
    }

    fun newGuessGame() = GuessGame(random)

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
