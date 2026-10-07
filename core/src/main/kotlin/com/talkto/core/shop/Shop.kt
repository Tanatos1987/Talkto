package com.talkto.core.shop

import com.talkto.core.i18n.Lang
import com.talkto.core.look.Aura
import com.talkto.core.look.BodyShape
import com.talkto.core.look.Brows
import com.talkto.core.look.Clothes
import com.talkto.core.look.Cosmetic
import com.talkto.core.look.Ears
import com.talkto.core.look.EyeStyle
import com.talkto.core.look.Furniture
import com.talkto.core.look.Glasses
import com.talkto.core.look.Hat
import com.talkto.core.look.HeadTop
import com.talkto.core.look.HouseDecor
import com.talkto.core.look.MouthStyle
import com.talkto.core.look.Nose
import com.talkto.core.look.Pattern
import com.talkto.core.look.Tail
import com.talkto.core.look.Wings

/** Shop shelves. */
enum class ShopCategory(val bg: String, val en: String, val emoji: String) {
    HATS("Шапки", "Hats", "🎩"),
    GLASSES("Очила", "Glasses", "🕶️"),
    CLOTHES("Дрехи", "Clothes", "👕"),
    BODY("За тялото", "Body parts", "🦄"),
    MAGIC("Магия", "Magic", "✨"),
    HOUSE("Къщичка", "House", "🏡"),
    ROOM("Обзавеждане", "Furniture", "🛋️"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)
}

data class ShopItem(val category: ShopCategory, val thing: Cosmetic) {
    val id: String get() = thing.shopId
    val price: Int get() = thing.price
    fun label(lang: Lang) = thing.label(lang)
}

/** How ZnaiKo earns coins. Balanced so a new treat is a few days of play away, and the first one is right away. */
enum class CoinReason(val coins: Int) {
    DAILY_VISIT(20),
    GAME_PLAYED(5),
    GAME_WON(15),
    LESSON_ANSWER(2),
    LESSON_DONE(10),
    QUIZ_ANSWER(3),
    QUIZ_DONE(10),
    LEVEL_UP(25),
    UPDATE(30),
    TASK(2),
    BADGE(20),
    STORY(3),
    MISSIONS(30),
    /** A friend's chapter of "The stolen colours" won. */
    CHAPTER(25),
}

/** Everything for sale: the paid cosmetics of every kind, cheapest first on each shelf. */
object Shop {
    const val STARTING_COINS = 100

    val items: List<ShopItem> = buildList {
        fun add(category: ShopCategory, values: List<Cosmetic>) = values.filter { it.price > 0 }.sortedBy { it.price }.forEach { add(ShopItem(category, it)) }
        add(ShopCategory.HATS, Hat.entries)
        add(ShopCategory.GLASSES, Glasses.entries)
        add(ShopCategory.CLOTHES, Clothes.entries)
        add(
            ShopCategory.BODY,
            Wings.entries + HeadTop.entries + Tail.entries + Pattern.entries + Ears.entries + BodyShape.entries +
                EyeStyle.entries + MouthStyle.entries + Nose.entries + Brows.entries,
        )
        add(ShopCategory.MAGIC, Aura.entries)
        add(ShopCategory.HOUSE, HouseDecor.entries)
        add(ShopCategory.ROOM, Furniture.entries)
    }

    fun item(id: String): ShopItem? = items.firstOrNull { it.id == id }

    fun of(category: ShopCategory): List<ShopItem> = items.filter { it.category == category }

    /** Free things are always owned; paid ones once bought. */
    fun owns(owned: Set<String>, thing: Cosmetic): Boolean = thing.price == 0 || thing.shopId in owned
}

/** Coins and bought things. Immutable: every change returns a new wallet. */
data class Wallet(val coins: Int, val owned: Set<String>) {
    fun owns(thing: Cosmetic) = Shop.owns(owned, thing)

    fun canBuy(item: ShopItem) = item.id !in owned && coins >= item.price

    /** The wallet after buying [item], or null when it is already owned or too expensive. */
    fun buy(item: ShopItem): Wallet? = if (canBuy(item)) Wallet(coins - item.price, owned + item.id) else null

    fun earn(reason: CoinReason, times: Int = 1) = copy(coins = coins + reason.coins * times.coerceAtLeast(0))
}
