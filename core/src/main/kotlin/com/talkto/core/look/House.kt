package com.talkto.core.look

import kotlinx.serialization.Serializable

/** Things outside ZnaiKo's house. */
@Serializable
enum class HouseDecor(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    FLOWERS("Цветя до вратата", "Flowers by the door", "🌷", 30),
    SMOKE("Пушек от комина", "Chimney smoke", "💨", 40),
    LANTERN("Фенер", "Lantern", "🏮", 40),
    MAILBOX("Пощенска кутия", "Mailbox", "📫", 30),
    FENCE("Оградка", "Little fence", "🪵", 50),
    FLAG("Знаменце", "Flag", "🚩", 25),
    TREE("Дърво в двора", "Tree in the yard", "🌳", 60),
    ;

    override val shopId: String get() = "decor:$name"
}

/** Things inside the house; shown in the room. */
@Serializable
enum class Furniture(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    BED("Легло", "Bed", "🛏️"),
    LAMP("Лампа", "Lamp", "💡"),
    RUG("Килимче", "Rug", "🧶", 20),
    PLANT("Цвете в саксия", "Pot plant", "🪴", 25),
    PAINTING("Картина", "Painting", "🖼️", 30),
    TOYBOX("Сандък с играчки", "Toy box", "🧸", 30),
    BOOKSHELF("Библиотека", "Bookshelf", "📚", 40),
    TV("Телевизор", "TV", "📺", 60),
    AQUARIUM("Аквариум", "Aquarium", "🐠", 80),
    TELESCOPE("Телескоп", "Telescope", "🔭", 100),
    PIANO("Пиано", "Piano", "🎹", 120),
    ;

    override val shopId: String get() = "furniture:$name"
}

/** ZnaiKo's house: colours, what stands outside and what is inside (only owned things can be placed). */
@Serializable
data class HouseLook(
    val wallColor: Long = 0xFFFFF1D6,
    val roofColor: Long = 0xFFE4572E,
    val doorColor: Long = 0xFF8B5A2B,
    val wallpaper: Long = 0xFFFFE8D6,
    val decor: Set<HouseDecor> = emptySet(),
    val furniture: Set<Furniture> = setOf(Furniture.BED, Furniture.LAMP),
) {
    companion object {
        val WALLS = listOf(0xFFFFF1D6, 0xFFFFFFFF, 0xFFFFE066, 0xFFBDE0FE, 0xFFFFC8DD, 0xFFCDEAC0, 0xFFE0C3FC, 0xFFD4A373)
        val ROOFS = listOf(0xFFE4572E, 0xFF3F88C5, 0xFF2D6A4F, 0xFF9B5DE5, 0xFF6D4C41, 0xFF2D2A32, 0xFFF15BB5, 0xFFFFC857)
        val WALLPAPERS = listOf(0xFFFFE8D6, 0xFFE3F2FD, 0xFFFCE4EC, 0xFFE8F5E9, 0xFFFFF9C4, 0xFFEDE7F6, 0xFFFFFFFF, 0xFF263238)
    }
}
