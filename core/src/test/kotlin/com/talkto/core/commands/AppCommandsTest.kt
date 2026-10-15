package com.talkto.core.commands

import com.google.common.truth.Truth.assertThat
import com.talkto.core.quiz.TriviaCategory
import org.junit.Test

class AppCommandsTest {

    @Test fun `house moves`() {
        listOf("Прибери се", "прибери се вкъщи", "хайде, прибирай се в къщичката", "влез в къщичката си", "иди си вкъщи", "Go home!", "go inside", "go into your house")
            .forEach { assertThat(AppCommands.parse(it.replace(",", ""))).isEqualTo(AppCommand.GoHome) }
        listOf("Излез навън", "излез от къщичката", "излизай", "покажи се", "come out", "Come outside please", "come out of your house")
            .forEach { assertThat(AppCommands.parse(it)).isEqualTo(AppCommand.ComeOut) }
        listOf("къщичката", "покажи ми къщичката си", "твоята къща", "show me your house").forEach { assertThat(AppCommands.parse(it)).isEqualTo(AppCommand.OpenHouse) }
    }

    @Test fun `places`() {
        listOf("магазин", "отвори магазина", "искам да пазарувам", "open the shop", "store").forEach { assertThat(AppCommands.parse(it)).isEqualTo(AppCommand.OpenShop) }
        listOf("промени си външния вид", "създай свой знайко", "customize you", "make my own znaiko").forEach { assertThat(AppCommands.parse(it)).isEqualTo(AppCommand.OpenCreator) }
        listOf("Запознай се с мен", "опознай ме", "get to know me").forEach { assertThat(AppCommands.parse(it)).isEqualTo(AppCommand.AboutMe) }
    }

    @Test fun `maths with and without a year`() {
        assertThat(AppCommands.parse("дай ми задачи по математика")).isEqualTo(AppCommand.Math(null))
        assertThat(AppCommands.parse("задачи за 3 клас")).isEqualTo(AppCommand.Math(3))
        assertThat(AppCommands.parse("искам задача за трети клас")).isEqualTo(AppCommand.Math(3))
        assertThat(AppCommands.parse("да смятаме")).isEqualTo(AppCommand.Math(null))
        assertThat(AppCommands.parse("give me maths for year 5")).isEqualTo(AppCommand.Math(5))
        assertThat(AppCommands.parse("алгебра")).isEqualTo(AppCommand.Math(null, algebra = true))
        assertThat(AppCommands.parse("дай ми уравнения за 7 клас")).isEqualTo(AppCommand.Math(7, algebra = true))
        assertThat(AppCommands.parse("let's do algebra")).isEqualTo(AppCommand.Math(null, algebra = true))
    }

    @Test fun `trivia with a subject`() {
        assertThat(AppCommands.parse("тривия")).isEqualTo(AppCommand.Trivia(null))
        assertThat(AppCommands.parse("задай ми въпроси от обща култура")).isEqualTo(AppCommand.Trivia(null))
        assertThat(AppCommands.parse("викторина за космоса")).isEqualTo(AppCommand.Trivia(TriviaCategory.SPACE))
        assertThat(AppCommands.parse("задай ми въпрос за животни")).isEqualTo(AppCommand.Trivia(TriviaCategory.ANIMALS))
        assertThat(AppCommands.parse("quiz about history")).isEqualTo(AppCommand.Trivia(TriviaCategory.HISTORY))
        assertThat(AppCommands.parse("trivia about Bulgaria")).isEqualTo(AppCommand.Trivia(TriviaCategory.BULGARIA))
    }

    @Test fun `ordinary talk is left alone`() {
        listOf(
            "какво е математика", "обичам математиката, защото учителката ми е много добра и винаги ни помага с домашното", "колко е 5 + 3",
            "къде е магазинът за хляб", "научи ме на английски", "да играем шах", "how are you", "my house is big", "излязох навън",
        ).forEach { assertThat(AppCommands.parse(it)).isNull() }
    }

    @Test fun `geometry tasks`() {
        assertThat(AppCommands.parse("Дай ми задачи по геометрия")).isEqualTo(AppCommand.Math(null, geometry = true))
        assertThat(AppCommands.parse("геометрия за 5 клас")).isEqualTo(AppCommand.Math(5, geometry = true))
        assertThat(AppCommands.parse("let's do geometry")).isEqualTo(AppCommand.Math(null, geometry = true))
    }

    @Test fun `arcade games`() {
        assertThat(AppCommands.parse("Хайде да играем тетрис")).isEqualTo(AppCommand.Tetris)
        assertThat(AppCommands.parse("пусни бонбонки")).isEqualTo(AppCommand.Sweets)
        assertThat(AppCommands.parse("let's play tetris")).isEqualTo(AppCommand.Tetris)
        assertThat(AppCommands.parse("обичам бонбони")).isNull()
    }
}
