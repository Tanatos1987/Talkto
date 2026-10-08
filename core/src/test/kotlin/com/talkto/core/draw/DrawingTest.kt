package com.talkto.core.draw

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import org.junit.Test
import kotlin.random.Random

class DrawingTest {

    private fun line(c: Crayon?, y: Float, width: Float = Brush.MEDIUM.width) = Stroke(c, width, listOf(Pt(0.05f, y), Pt(0.95f, y)))

    @Test fun `an empty page has no colour and an eraser takes colour away`() {
        assertThat(Drawing.coverage(emptyList())).isEqualTo(0f)
        val red = line(Crayon.RED, 0.5f)
        val drawn = Drawing.coverage(listOf(red))
        assertThat(drawn).isGreaterThan(0f)
        assertThat(Drawing.coverage(listOf(red, line(null, 0.5f, Brush.THICK.width)))).isLessThan(drawn)
    }

    @Test fun `thick strokes all over fill the page`() {
        val strokes = (0..12).map { line(Crayon.BLUE, it / 12f, Brush.THICK.width) }
        assertThat(Drawing.coverage(strokes)).isGreaterThan(0.45f)
    }

    @Test fun `colours come most used first and the eraser is not a colour`() {
        val strokes = listOf(
            line(Crayon.GREEN, 0.2f, Brush.THICK.width),
            line(Crayon.GREEN, 0.3f, Brush.THICK.width),
            line(Crayon.RED, 0.7f, Brush.THIN.width),
            line(null, 0.9f),
        )
        assertThat(Drawing.colours(strokes)).isEqualTo(listOf(Crayon.GREEN, Crayon.RED))
    }

    @Test fun `the title finds a word to learn`() {
        assertThat(Drawing.titleWord("Моето куче")?.en).isEqualTo("dog")
        assertThat(Drawing.titleWord("my cat")?.bg).isEqualTo("котка")
        assertThat(Drawing.titleWord("абракадабра")).isNull()
    }

    @Test fun `ZnaiKo talks about the title, the colours and a word in the language being learned`() {
        val strokes = listOf(line(Crayon.RED, 0.5f))
        val bg = Drawing.reaction(strokes, "Моето куче", null, Lang.BG, Lang.EN, Random(1))
        assertThat(bg).contains("Моето куче")
        assertThat(bg).contains("червено")
        assertThat(bg).contains("„dog“")
        val en = Drawing.reaction(strokes, "", Drawing.PROMPTS.first { it.en == "cat" }, Lang.EN, Lang.BG, Random(1))
        assertThat(en).contains("cat")
        assertThat(en).contains("котка")
        assertThat(Drawing.reaction(listOf(line(Crayon.GREEN, 0.5f)), "", null, Lang.BG, Lang.BG, Random(1))).contains("Само със зелено")
        assertThat(Drawing.withWord("червено")).isEqualTo("с червено")
        // Learning the screen language itself: no translation.
        assertThat(Drawing.reaction(strokes, "", null, Lang.BG, Lang.BG, Random(1))).doesNotContain("английски")
    }

    @Test fun `prompts are single words with a picture and change each time`() {
        assertThat(Drawing.PROMPTS).isNotEmpty()
        Drawing.PROMPTS.forEach { assertThat(it.bg).doesNotContain(" ") }
        val first = Drawing.prompt(Random(3))
        assertThat(Drawing.prompt(Random(3), not = first)).isNotEqualTo(first)
        assertThat(Drawing.promptLine(first, Lang.BG)).startsWith("Нарисувай ми")
    }
}
