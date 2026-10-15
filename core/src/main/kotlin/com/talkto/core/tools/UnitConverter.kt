package com.talkto.core.tools

import com.talkto.core.i18n.Lang
import java.util.Locale

/** "5 км в мили", "100 f to c", "2,5 кг в паунди", "1 gb в mb". */
object UnitConverter {

    private enum class Category { LENGTH, MASS, VOLUME, SPEED, DATA, TIME, TEMPERATURE }

    /** [factor] converts one unit into the category's base unit; temperature uses [toBase]/[fromBase]. */
    private data class Unit(
        val category: Category,
        val display: String,
        val factor: Double = 1.0,
        val toBase: ((Double) -> Double)? = null,
        val fromBase: ((Double) -> Double)? = null,
        val displayEn: String = display,
    )

    private val units: Map<String, Unit> = buildMap {
        fun add(u: Unit, vararg names: String) = names.forEach { put(it, u) }
        // length, base metre
        add(Unit(Category.LENGTH, "мм", 0.001, displayEn = "mm"), "mm", "мм", "милиметър", "милиметра", "милиметри")
        add(Unit(Category.LENGTH, "см", 0.01, displayEn = "cm"), "cm", "см", "сантиметър", "сантиметра", "сантиметри")
        add(Unit(Category.LENGTH, "м", 1.0, displayEn = "m"), "m", "м", "метър", "метра", "метри")
        add(Unit(Category.LENGTH, "км", 1000.0, displayEn = "km"), "km", "км", "километър", "километра", "километри")
        add(Unit(Category.LENGTH, "инча", 0.0254, displayEn = "inches"), "in", "inch", "inches", "инч", "инча", "инчове")
        add(Unit(Category.LENGTH, "фута", 0.3048, displayEn = "feet"), "ft", "foot", "feet", "фут", "фута", "футове")
        add(Unit(Category.LENGTH, "ярда", 0.9144, displayEn = "yards"), "yd", "yard", "yards", "ярд", "ярда", "ярдове")
        add(Unit(Category.LENGTH, "мили", 1609.344, displayEn = "miles"), "mi", "mile", "miles", "миля", "мили")
        // mass, base gram
        add(Unit(Category.MASS, "мг", 0.001, displayEn = "mg"), "mg", "мг", "милиграм", "милиграма")
        add(Unit(Category.MASS, "г", 1.0, displayEn = "g"), "g", "г", "гр", "грам", "грама")
        add(Unit(Category.MASS, "кг", 1000.0, displayEn = "kg"), "kg", "кг", "килограм", "килограма", "кила", "кило")
        add(Unit(Category.MASS, "т", 1_000_000.0, displayEn = "t"), "t", "тон", "тона", "тонове")
        add(Unit(Category.MASS, "унции", 28.349523125, displayEn = "ounces"), "oz", "ounce", "ounces", "унция", "унции")
        add(Unit(Category.MASS, "паунда", 453.59237, displayEn = "pounds"), "lb", "lbs", "pound", "pounds", "паунд", "паунда", "паунди")
        // volume, base litre
        add(Unit(Category.VOLUME, "мл", 0.001, displayEn = "ml"), "ml", "мл", "милилитър", "милилитра")
        add(Unit(Category.VOLUME, "л", 1.0, displayEn = "l"), "l", "л", "литър", "литра", "литри")
        add(Unit(Category.VOLUME, "галона", 3.785411784, displayEn = "gallons"), "gal", "gallon", "gallons", "галон", "галона")
        add(Unit(Category.VOLUME, "чаши", 0.2365882365, displayEn = "cups"), "cup", "cups", "чаша", "чаши")
        // speed, base m/s
        add(Unit(Category.SPEED, "м/с", 1.0, displayEn = "m/s"), "m/s", "м/с", "mps")
        add(Unit(Category.SPEED, "км/ч", 1 / 3.6, displayEn = "km/h"), "km/h", "kmh", "kph", "км/ч", "кмч")
        add(Unit(Category.SPEED, "мили/ч", 0.44704, displayEn = "mph"), "mph", "мили/ч")
        add(Unit(Category.SPEED, "възела", 0.514444, displayEn = "knots"), "kn", "knot", "knots", "възел", "възела")
        // data, base byte (binary multiples, as phones report storage)
        add(Unit(Category.DATA, "B", 1.0), "b", "byte", "bytes", "байт", "байта")
        add(Unit(Category.DATA, "KB", 1024.0), "kb", "кб", "килобайт", "килобайта")
        add(Unit(Category.DATA, "MB", 1024.0 * 1024), "mb", "мб", "мегабайт", "мегабайта")
        add(Unit(Category.DATA, "GB", 1024.0 * 1024 * 1024), "gb", "гб", "гигабайт", "гигабайта")
        add(Unit(Category.DATA, "TB", 1024.0 * 1024 * 1024 * 1024), "tb", "тб", "терабайт", "терабайта")
        // time, base second
        add(Unit(Category.TIME, "сек", 1.0, displayEn = "sec"), "s", "sec", "seconds", "сек", "секунда", "секунди")
        add(Unit(Category.TIME, "мин", 60.0, displayEn = "min"), "min", "minutes", "мин", "минута", "минути")
        add(Unit(Category.TIME, "ч", 3600.0, displayEn = "h"), "h", "hour", "hours", "ч", "час", "часа", "часове")
        add(Unit(Category.TIME, "дни", 86_400.0, displayEn = "days"), "day", "days", "ден", "дни", "дена")
        add(Unit(Category.TIME, "седмици", 604_800.0, displayEn = "weeks"), "week", "weeks", "седмица", "седмици")
        // temperature, base Celsius
        add(Unit(Category.TEMPERATURE, "°C", toBase = { it }, fromBase = { it }), "c", "°c", "с", "целзий", "целзия", "градуса")
        add(Unit(Category.TEMPERATURE, "°F", toBase = { (it - 32) * 5 / 9 }, fromBase = { it * 9 / 5 + 32 }), "f", "°f", "фаренхайт", "фаренхайта")
        add(Unit(Category.TEMPERATURE, "K", toBase = { it - 273.15 }, fromBase = { it + 273.15 }), "k", "келвин", "келвина")
    }

    private val PATTERN = Regex(
        "^(?:колко (?:са|е|прави) |convert |превърни |обърни )?(-?\\d+(?:[.,]\\d+)?)\\s*([\\p{L}°/]+)\\s+(?:в|във|to|in|into|на)\\s+([\\p{L}°/]+)$",
        RegexOption.IGNORE_CASE,
    )

    data class Conversion(val value: Double, val from: String, val result: Double, val to: String, val fromEn: String = from, val toEn: String = to) {
        fun describe(lang: Lang = Lang.BG) =
            "${Calculator.format(value, lang)} ${lang.pick(from, fromEn)} = ${Calculator.format(result, lang)} ${lang.pick(to, toEn)}"
    }

    /** Null when [text] is not a conversion request or the units are unknown/incompatible. */
    fun convert(text: String): Conversion? {
        val m = PATTERN.matchEntire(text.trim().trimEnd('?', '.', '!')) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDouble()
        val from = units[m.groupValues[2].lowercase(Locale.ROOT)] ?: return null
        val to = units[m.groupValues[3].lowercase(Locale.ROOT)] ?: return null
        if (from.category != to.category) return null
        val result = if (from.category == Category.TEMPERATURE) {
            to.fromBase!!(from.toBase!!(value))
        } else {
            value * from.factor / to.factor
        }
        return Conversion(value, from.display, result, to.display, from.displayEn, to.displayEn)
    }
}
