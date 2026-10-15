package com.talkto.core.games

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.Topic
import com.talkto.core.learn.Vocabulary
import org.junit.Test
import kotlin.random.Random

class MiniGamesTest {

    @Test fun `catching healthy food scores and junk costs a heart`() {
        val game = CatchFood(Random(7))
        var guard = 0
        // Follow every falling item with the mouth until something is caught.
        while (game.lastCatch == null && guard++ < 10_000) {
            game.items.maxByOrNull { it.y }?.let { game.moveTo(it.x) }
            game.tick(16)
        }
        assertThat(game.lastCatch).isNotNull()
        if (game.lastCatch == true) assertThat(game.score).isAtLeast(10) else assertThat(game.hearts).isEqualTo(CatchFood.HEARTS - 1)
    }

    @Test fun `the round ends after a minute`() {
        val game = CatchFood(Random(1))
        game.moveTo(-5f) // far left, catches little
        repeat(4_000) { game.tick(16) }
        assertThat(game.over).isTrue()
        assertThat(game.items.all { it.y <= 1.05f }).isTrue()
    }

    @Test fun `letters must be tapped in order`() {
        val game = LetterRain(Lang.EN, Random(3), Vocabulary.of(Topic.ANIMALS))
        val word = game.text
        assertThat(word.length).isIn(3..7)
        var guard = 0
        while (game.wordsDone == 0 && guard++ < 20_000) {
            game.tick(16)
            // A wrong letter first, when one is falling.
            game.letters.firstOrNull { it.char != game.next }?.let { wrong ->
                val before = game.mistakes
                assertThat(game.tap(wrong.id)).isFalse()
                assertThat(game.mistakes).isEqualTo(before + 1)
            }
            game.letters.firstOrNull { it.char == game.next }?.let { assertThat(game.tap(it.id)).isTrue() }
        }
        assertThat(game.wordsDone).isEqualTo(1)
        assertThat(game.score).isEqualTo(10 * word.length)
    }

    @Test fun `bulgarian words use cyrillic letters only`() {
        val game = LetterRain(Lang.BG, Random(5))
        assertThat(game.text.all { it in 'А'..'Я' || it == 'Ъ' || it == 'Ь' || it == 'Ю' || it == 'Я' || it == 'Й' }).isTrue()
    }
}
