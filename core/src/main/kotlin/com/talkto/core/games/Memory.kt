package com.talkto.core.games

import kotlin.random.Random

/**
 * Memory: [pairs] pairs of picture cards face down. The user and ZnaiKo take turns flipping two cards;
 * a match stays open and earns another turn. ZnaiKo remembers the cards it has seen, but only as well as its
 * [skill] allows: a baby forgets most of them, a grown-up ZnaiKo forgets almost nothing.
 */
class Memory(
    val pairs: Int = 8,
    private val skill: Skill = Skill(5),
    private val random: Random = Random.Default,
) {
    /** Card faces; equal numbers make a pair. Shown by the UI as pictures. */
    val faces: List<Int> = (0 until pairs).flatMap { listOf(it, it) }.shuffled(random)
    val matchedBy = arrayOfNulls<Int>(faces.size) // 0 user, 1 ZnaiKo
    val open = mutableListOf<Int>()
    var userTurn = true
        private set
    var userPairs = 0
        private set
    var petPairs = 0
        private set
    private val seen = HashMap<Int, Int>() // card -> face, as ZnaiKo remembers it

    val over: Boolean get() = matchedBy.all { it != null }
    val outcome: GameOutcome?
        get() = if (!over) null else when {
            userPairs > petPairs -> GameOutcome.USER_WON
            petPairs > userPairs -> GameOutcome.PET_WON
            else -> GameOutcome.DRAW
        }

    fun isHidden(card: Int) = matchedBy[card] == null && card !in open

    /** Turns [card] over. After the second card call [resolve]. */
    fun flip(card: Int): Boolean {
        if (over || open.size >= 2 || !isHidden(card)) return false
        open += card
        if (random.nextFloat() < remember) seen[card] = faces[card]
        return true
    }

    /** Checks the two open cards: a pair stays, otherwise both turn back and the turn passes. Returns true on a match. */
    fun resolve(): Boolean {
        check(open.size == 2)
        val (a, b) = open
        open.clear()
        val match = faces[a] == faces[b]
        if (match) {
            val who = if (userTurn) 0 else 1
            matchedBy[a] = who; matchedBy[b] = who
            if (userTurn) userPairs++ else petPairs++
            seen.remove(a); seen.remove(b)
        } else {
            userTurn = !userTurn
        }
        return match
    }

    /** ZnaiKo's first card, then (after it is shown) [petSecond]. */
    fun petFirst(): Int {
        val known = knownPair()
        return known?.first ?: hidden().filter { it !in seen }.ifEmpty { hidden() }.random(random)
    }

    fun petSecond(): Int {
        val first = open.single()
        val face = faces[first]
        val partner = seen.entries.firstOrNull { it.key != first && it.value == face && isHidden(it.key) }?.key
        return partner ?: hidden().filter { it !in seen }.ifEmpty { hidden() }.random(random)
    }

    private fun knownPair(): Pair<Int, Int>? {
        val byFace = seen.entries.filter { isHidden(it.key) }.groupBy({ it.value }, { it.key })
        return byFace.values.firstOrNull { it.size >= 2 }?.let { it[0] to it[1] }
    }

    private fun hidden() = faces.indices.filter { isHidden(it) }

    /** How likely ZnaiKo is to remember a card it saw: 35% at skill 0, 95% at skill 10. */
    private val remember: Float get() = 0.35f + skill.value * 0.06f

    companion object {
        /** Pictures for the faces, in order. */
        val PICTURES = listOf("🍎", "🐸", "🚀", "🌻", "🐙", "🎈", "⭐", "🍩", "🦋", "🐢", "🎸", "🌈")
    }
}
