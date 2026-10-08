package com.talkto.core.pet

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.look.CreatureLook
import org.junit.Test

class NutritionTest {
    @Test fun `junk makes round and pale, healthy food slim and shiny`() {
        val base = CreatureLook()
        var fat = 0f; var vit = 55f
        repeat(8) { val b = Nutrition.bite(Food.BURGER, fat); fat = (fat + b.fat).coerceIn(0f, 100f); vit = (vit + b.vitality).coerceIn(0f, 100f) }
        val junky = Nutrition.look(base, fat, vit)
        assertThat(junky.width).isGreaterThan(base.width)
        assertThat(junky.bodyColor).isNotEqualTo(base.bodyColor)
        assertThat(junky.glossy).isLessThan(base.glossy)
        repeat(12) { val b = Nutrition.bite(Food.CARROT, fat); fat = (fat + b.fat).coerceIn(0f, 100f); vit = (vit + b.vitality).coerceIn(0f, 100f) }
        val fit = Nutrition.look(base, fat, vit)
        assertThat(fit.width).isLessThan(junky.width)
        assertThat(fit.glossy).isGreaterThan(base.glossy)
    }

    @Test fun `a little fat does not show and time melts it`() {
        assertThat(Nutrition.roundness(10f)).isEqualTo(0f)
        val (f, v) = Nutrition.rest(60f, 20f, 10f)
        assertThat(f).isLessThan(60f)
        assertThat(v).isGreaterThan(20f)
    }

    @Test fun `every food has words in both languages and the lines differ`() {
        Food.entries.forEach { assertThat(it.label(Lang.BG)).isNotEmpty(); assertThat(it.label(Lang.EN)).isNotEmpty() }
        assertThat(Food.HEALTHY).isNotEmpty(); assertThat(Food.JUNK).isNotEmpty()
        assertThat(Nutrition.line(Food.PIZZA, 80f, 30f, Lang.BG)).contains("боли")
        assertThat(Nutrition.line(Food.APPLE, 0f, 90f, Lang.BG)).contains("силен")
    }
}
