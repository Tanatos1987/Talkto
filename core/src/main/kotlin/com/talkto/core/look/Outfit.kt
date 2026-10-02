package com.talkto.core.look

import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable

/** Something ZnaiKo can wear or have. [price] 0 is free; anything else is bought in the shop with coins. */
interface Cosmetic {
    val bg: String
    val en: String
    val emoji: String
    val price: Int
    /** Stable id in the shop and in the owned set, e.g. "hat:WIZARD". */
    val shopId: String

    fun label(lang: Lang) = lang.pick(bg, en)
}

@Serializable
enum class Hat(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без шапка", "No hat", "➖"),
    PARTY("Парти", "Party", "🥳"),
    BEANIE("Зимна", "Beanie", "🧶"),
    CROWN("Корона", "Crown", "👑"),
    TOP_HAT("Цилиндър", "Top hat", "🎩"),
    CAP("С козирка", "Cap", "🧢"),
    WIZARD("Магьосник", "Wizard", "🧙", 60),
    FLOWER_CROWN("Венец от цветя", "Flower crown", "🌼", 50),
    COWBOY("Каубой", "Cowboy", "🤠", 60),
    HALO("Ореол", "Halo", "😇", 90),
    CHEF("Готвач", "Chef", "👨‍🍳", 40),
    VIKING("Викинг", "Viking", "🪖", 80),
    ;

    override val shopId: String get() = "hat:$name"
}

@Serializable
enum class Glasses(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без очила", "No glasses", "➖"),
    ROUND("Кръгли", "Round", "👓"),
    SUNGLASSES("Слънчеви", "Sunglasses", "🕶️"),
    HEART("Сърца", "Hearts", "😍"),
    MONOCLE("Монокъл", "Monocle", "🧐"),
    STAR("Звезди", "Stars", "🤩", 40),
    SKI("Ски очила", "Ski goggles", "🥽", 50),
    THREE_D("3D очила", "3D glasses", "🎞️", 40),
    ;

    override val shopId: String get() = "glasses:$name"
}

@Serializable
enum class Clothes(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без дрехи", "No clothes", "➖"),
    SCARF("Шал", "Scarf", "🧣"),
    BOWTIE("Папийонка", "Bow tie", "🎀"),
    HOODIE("Суитшърт", "Hoodie", "👕"),
    TIE("Вратовръзка", "Tie", "👔"),
    CAPE("Наметало на герой", "Hero cape", "🦸", 70),
    TUTU("Балетна поличка", "Tutu", "🩰", 50),
    BACKPACK("Раничка", "Backpack", "🎒", 60),
    NECKLACE("Огърлица", "Necklace", "📿", 40),
    MEDAL("Златен медал", "Gold medal", "🥇", 100),
    ;

    override val shopId: String get() = "clothes:$name"
}

/** What ZnaiKo wears. Stored locally as JSON; ARGB colours as Long so the model has no Compose dependency. */
@Serializable
data class OutfitConfig(
    val hat: Hat = Hat.NONE,
    val hatColor: Long = 0xFFE4572E,
    val glasses: Glasses = Glasses.NONE,
    val clothes: Clothes = Clothes.NONE,
    val clothesColor: Long = 0xFF3F88C5,
)

val OUTFIT_PALETTE: List<Long> = listOf(
    0xFFE4572E, // tomato
    0xFFFFC857, // sunflower
    0xFF7BD389, // mint
    0xFF3F88C5, // denim
    0xFF9B5DE5, // violet
    0xFFF15BB5, // bubblegum
    0xFF2D2A32, // ink
    0xFFF7F1E8, // cream
)
