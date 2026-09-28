package com.talkto.core.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

class CalculatorTest {

    private fun calc(text: String): String? = Calculator.extract(text)?.let { Calculator.format(Calculator.evaluate(it)) }

    @Test fun `precedence, parentheses and power`() {
        assertThat(calc("2+3*4")).isEqualTo("14")
        assertThat(calc("(2+3)*4")).isEqualTo("20")
        assertThat(calc("2^3^2")).isEqualTo("512")
        assertThat(calc("-3+5")).isEqualTo("2")
    }

    @Test fun `bulgarian words and decimal comma`() {
        assertThat(calc("колко е 7 по 6")).isEqualTo("42")
        assertThat(calc("сметни 10 делено на 4")).isEqualTo("2,5")
        assertThat(calc("3,5 плюс 1,25")).isEqualTo("4,75")
        assertThat(calc("колко е 15% от 240")).isEqualTo("36")
        assertThat(calc("корен от 81")).isEqualTo("9")
        assertThat(calc("5 на квадрат")).isEqualTo("25")
        assertThat(calc("2 на степен 10")).isEqualTo("1024")
    }

    @Test fun `non calculations are ignored`() {
        assertThat(Calculator.extract("42")).isNull()
        assertThat(Calculator.extract("отвори камера")).isNull()
        assertThat(Calculator.extract("6:45")).isNull()
        assertThat(Calculator.extract("колко е часът")).isNull()
    }

    @Test fun `division by zero is an error`() {
        assertThrows(IllegalArgumentException::class.java) { Calculator.evaluate("1/0") }
    }

    @Test fun `formatting trims noise`() {
        assertThat(Calculator.format(1.0 / 3)).isEqualTo("0,3333333333")
        assertThat(Calculator.format(1e12)).isEqualTo("1000000000000")
    }
}

class UnitConverterTest {
    @Test fun `length, mass, temperature, data`() {
        assertThat(UnitConverter.convert("5 км в мили")!!.describe()).isEqualTo("5 км = 3,106855961 мили")
        assertThat(UnitConverter.convert("2,5 кг в паунди")!!.describe()).isEqualTo("2,5 кг = 5,511556555 паунда")
        assertThat(UnitConverter.convert("100 f в c")!!.describe()).isEqualTo("100 °F = 37,77777778 °C")
        assertThat(UnitConverter.convert("1 gb в mb")!!.describe()).isEqualTo("1 GB = 1024 MB")
        assertThat(UnitConverter.convert("90 минути в часа")!!.describe()).isEqualTo("90 мин = 1,5 ч")
    }

    @Test fun `incompatible or unknown units are rejected`() {
        assertThat(UnitConverter.convert("5 км в кг")).isNull()
        assertThat(UnitConverter.convert("5 котки в кучета")).isNull()
        assertThat(UnitConverter.convert("премести a.pdf в документи")).isNull()
    }
}

class TimeParserTest {
    // Monday 2026-09-28 14:10 UTC
    private val now = ZonedDateTime.of(2026, 9, 28, 14, 10, 0, 0, ZoneOffset.UTC)

    @Test fun `relative times`() {
        assertThat(TimeParser.find("след 10 минути да звънна", now)!!.at).isEqualTo(now.plusMinutes(10))
        assertThat(TimeParser.find("след половин час", now)!!.at).isEqualTo(now.plusMinutes(30))
        assertThat(TimeParser.find("in 2 hours", now)!!.at).isEqualTo(now.plusHours(2))
        assertThat(TimeParser.find("след 1 час и 15 минути", now)!!.at).isEqualTo(now.plusMinutes(75))
    }

    @Test fun `absolute times roll forward`() {
        assertThat(TimeParser.find("в 18:30", now)!!.at).isEqualTo(now.withHour(18).withMinute(30))
        assertThat(TimeParser.find("утре в 9", now)!!.at).isEqualTo(now.plusDays(1).withHour(9).withMinute(0))
        // 7 o'clock already passed this morning, the next 7 is this evening.
        assertThat(TimeParser.find("в 7", now)!!.at).isEqualTo(now.withHour(19).withMinute(0))
        assertThat(TimeParser.find("в 8 сутринта", now)!!.at).isEqualTo(now.plusDays(1).withHour(8).withMinute(0))
        assertThat(TimeParser.find("at 7 pm", now)!!.at).isEqualTo(now.withHour(19).withMinute(0))
        assertThat(TimeParser.find("утре", now)!!.at).isEqualTo(now.plusDays(1).withHour(9).withMinute(0))
    }

    @Test fun `match span lets the caller cut the time out of the text`() {
        val text = "да купя хляб в 18:30"
        val f = TimeParser.find(text, now)!!
        assertThat(text.removeRange(f.start, f.end).trim()).isEqualTo("да купя хляб")
    }

    @Test fun `durations`() {
        assertThat(TimeParser.duration("5 минути")).isEqualTo(Duration.ofMinutes(5))
        assertThat(TimeParser.duration("30 сек")).isEqualTo(Duration.ofSeconds(30))
        assertThat(TimeParser.duration("един час и 20 минути")).isEqualTo(Duration.ofMinutes(80))
        assertThat(TimeParser.duration("нищо")).isNull()
    }

    @Test fun `no time is null`() {
        assertThat(TimeParser.find("купи хляб", now)).isNull()
    }
}

class FunPackTest {
    @Test fun `deterministic with a seeded random`() {
        val a = FunPack(Random(7))
        val b = FunPack(Random(7))
        assertThat(a.dice(3)).isEqualTo(b.dice(3))
        assertThat(a.dice(5).all { it in 1..6 }).isTrue()
        assertThat(FunPack(Random(1)).number(10, 1)).isIn(1..10)
    }

    @Test fun `guess game converges`() {
        val game = FunPack(Random(3)).newGuessGame()
        var lo = 1
        var hi = 100
        var over = false
        while (!over) {
            val mid = (lo + hi) / 2
            val (reply, done) = game.guess(mid)
            over = done
            if (reply.startsWith("Нагоре")) lo = mid + 1 else if (reply.startsWith("Надолу")) hi = mid - 1
        }
        assertThat(game.attempts).isAtMost(7)
    }

    @Test fun `rock paper scissors verdicts are consistent`() {
        repeat(30) { seed ->
            val (pet, verdict) = FunPack(Random(seed)).rps(FunPack.Hand.ROCK)
            val expected = when (pet) {
                FunPack.Hand.ROCK -> "Равни сме!"
                FunPack.Hand.SCISSORS -> "Ти печелиш!"
                FunPack.Hand.PAPER -> "Аз печеля!"
            }
            assertThat(verdict).isEqualTo(expected)
        }
    }
}
