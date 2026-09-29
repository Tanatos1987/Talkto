package com.talkto.core.pet

import com.talkto.core.i18n.Lang
import com.talkto.core.look.CreatureLook

/** Something ZnaiKo can eat. Healthy food keeps it fit and shiny; junk food is tasty, but it gets round and pale. */
enum class Food(val emoji: String, val bg: String, val en: String, val healthy: Boolean, val satiety: Float) {
    APPLE("🍎", "ябълка", "an apple", true, 22f),
    CARROT("🥕", "морков", "a carrot", true, 18f),
    BROCCOLI("🥦", "броколи", "broccoli", true, 20f),
    BANANA("🍌", "банан", "a banana", true, 24f),
    MILK("🥛", "мляко", "milk", true, 20f),
    FISH("🐟", "рибка", "fish", true, 30f),
    SALAD("🥗", "салата", "a salad", true, 26f),
    BURGER("🍔", "бургер", "a burger", false, 34f),
    FRIES("🍟", "картофки", "fries", false, 26f),
    PIZZA("🍕", "пица", "pizza", false, 32f),
    DONUT("🍩", "поничка", "a doughnut", false, 22f),
    LOLLIPOP("🍭", "близалка", "a lollipop", false, 10f),
    SODA("🥤", "газирано", "fizzy pop", false, 8f),
    CAKE("🍰", "торта", "cake", false, 24f),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    companion object {
        val HEALTHY = entries.filter { it.healthy }
        val JUNK = entries.filter { !it.healthy }
    }
}

/**
 * The body side of eating. [fat] 0..100 grows with junk food and melts with healthy food and play;
 * [vitality] 0..100 rises with healthy food and falls with junk. Both drift back slowly over the hours.
 */
object Nutrition {

    /** What one bite does to the needs and the body. */
    data class Bite(val satiety: Float, val happiness: Float, val energy: Float, val fat: Float, val vitality: Float)

    fun bite(food: Food, fat: Float): Bite = if (food.healthy) {
        Bite(food.satiety, 3f, 8f, -4f, 8f)
    } else {
        // Sweet and tasty now, a sugar dip after; the rounder ZnaiKo already is, the less it enjoys it.
        Bite(food.satiety, if (fat > 70f) 2f else 10f, -4f, 10f, -7f)
    }

    /** Playing burns a little. */
    const val PLAY_BURNS = 3f

    /** Hours pass: fat melts a little, vitality drifts towards the middle. */
    fun rest(fat: Float, vitality: Float, hours: Float): Pair<Float, Float> {
        val f = (fat - hours * 1.5f).coerceIn(0f, 100f)
        val v = vitality + (55f - vitality) * (1f - Math.pow(0.97, hours.toDouble()).toFloat())
        return f to v.coerceIn(0f, 100f)
    }

    /** How round ZnaiKo is drawn: 0 (fit) .. 1 (very round). A little fat does not show. */
    fun roundness(fat: Float): Float = ((fat - 15f) / 85f).coerceIn(0f, 1f)

    /**
     * The look after eating: a pale, greyish body when vitality is low, a brighter and glossier one when it is high,
     * and a wider, a little shorter body when round.
     */
    fun look(base: CreatureLook, fat: Float, vitality: Float): CreatureLook {
        val round = roundness(fat)
        val sick = ((40f - vitality) / 40f).coerceIn(0f, 1f)
        val fit = ((vitality - 70f) / 30f).coerceIn(0f, 1f)
        var body = mix(base.bodyColor, PALE, sick * 0.55f)
        body = mix(body, 0xFFFFFFFF, fit * 0.12f)
        return base.copy(
            width = base.width * (1f + round * 0.2f),
            height = base.height * (1f - round * 0.05f),
            bodyColor = body,
            bellyColor = mix(base.bellyColor, PALE, sick * 0.4f),
            glossy = (base.glossy + fit * 0.35f - sick * 0.25f).coerceIn(0f, 1f),
        )
    }

    /** What ZnaiKo says after eating [food]. */
    fun line(food: Food, fat: Float, vitality: Float, lang: Lang): String = when {
        food.healthy && vitality >= 80f -> lang.pick("Ммм, ${food.bg}! Чувствам се силен и лъскав! 💪✨", "Mmm, ${food.en}! I feel strong and shiny! 💪✨")
        food.healthy && fat > 40f -> lang.pick("${food.bg.replaceFirstChar { it.uppercase() }}! Така ще отслабна малко.", "${food.en.replaceFirstChar { it.uppercase() }}! That will slim me down a bit.")
        food.healthy -> lang.pick("Ням-ням, ${food.bg}! Полезно и вкусно!", "Yum, ${food.en}! Healthy and tasty!")
        fat > 70f -> lang.pick("Уф... коремчето ме боли. Може ли нещо полезно? 🤢", "Ugh... my tummy hurts. Can I have something healthy? 🤢")
        fat > 40f -> lang.pick("Вкусно е, но започвам да ставам кръгличък...", "It's tasty, but I'm getting a bit round...")
        else -> lang.pick("Уау, ${food.bg}! Вкусно! Но да не прекаляваме.", "Wow, ${food.en}! Tasty! But not too much.")
    }

    private const val PALE = 0xFFB9C4A8

    private fun mix(a: Long, b: Long, t: Float): Long {
        if (t <= 0f) return a
        fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toInt()
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}
