package com.talkto.core.look

import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class BodyShape(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    ROUND("Топче", "Round", "⚪"),
    TALL("Яйчице", "Egg", "🥚"),
    CHUBBY("Пухкаво", "Chubby", "🍡"),
    PEAR("Крушка", "Pear", "🍐"),
    BEAN("Бобче", "Bean", "🫘"),
    ;

    override val shopId: String get() = "shape:$name"
}

@Serializable
enum class Pattern(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Гладко", "Plain", "➖"),
    SPOTS("Точки", "Spots", "🐞"),
    STRIPES("Райета", "Stripes", "🦓"),
    HEART("Сърце на коремчето", "Belly heart", "💚"),
    STARS("Звездички", "Stars", "✨", 50),
    ;

    override val shopId: String get() = "pattern:$name"
}

@Serializable
enum class EyeStyle(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    ROUND("Кръгли", "Round", "👀"),
    BIG("Големи", "Big", "🥺"),
    OVAL("Овални", "Oval", "🙂"),
    SLEEPY("Сънливи", "Sleepy", "😌"),
    SPARKLE("Блестящи", "Sparkly", "🤩"),
    ;

    override val shopId: String get() = "eyes:$name"
}

@Serializable
enum class Brows(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    AUTO("Само при емоция", "Only with feelings", "😐"),
    THIN("Тънки", "Thin", "🤨"),
    THICK("Рунтави", "Bushy", "😠"),
    ;

    override val shopId: String get() = "brows:$name"
}

@Serializable
enum class MouthStyle(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    SMILE("Усмивка", "Smile", "🙂"),
    CAT("Котешка", "Cat mouth", "😺"),
    GRIN("Широка", "Big grin", "😁"),
    FANG("Със зъбче", "Little fang", "😼"),
    TINY("Мъничка", "Tiny", "😶"),
    ;

    override val shopId: String get() = "mouth:$name"
}

@Serializable
enum class Nose(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без нос", "No nose", "➖"),
    DOT("Точица", "Dot", "•"),
    BUTTON("Копченце", "Button", "🔴"),
    SNOUT("Муцунка", "Snout", "🐽"),
    BEAK("Човка", "Beak", "🐤"),
    ;

    override val shopId: String get() = "nose:$name"
}

@Serializable
enum class Ears(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без уши", "No ears", "➖"),
    CAT("Котешки", "Cat", "🐱"),
    BUNNY("Заешки", "Bunny", "🐰"),
    BEAR("Мечешки", "Bear", "🐻"),
    MOUSE("Мишешки", "Mouse", "🐭"),
    ELF("Елфически", "Elf", "🧝"),
    DOG("Клепнали", "Floppy", "🐶"),
    ;

    override val shopId: String get() = "ears:$name"
}

@Serializable
enum class HeadTop(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    SPROUT("Листенце", "Sprout", "🌱"),
    NONE("Нищо", "Nothing", "➖"),
    ANTENNAE("Антенки", "Antennae", "📡"),
    HORNS("Рогца", "Little horns", "😈"),
    TUFT("Перчем", "Tuft", "💇"),
    UNICORN("Еднорог", "Unicorn horn", "🦄", 120),
    ;

    override val shopId: String get() = "top:$name"
}

@Serializable
enum class Tail(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без опашка", "No tail", "➖"),
    CAT("Котешка", "Cat", "🐈"),
    BUNNY("Пухче", "Pom-pom", "🐇"),
    DRAGON("Драконова", "Dragon", "🦎"),
    FOX("Лисича", "Fox", "🦊", 40),
    ;

    override val shopId: String get() = "tail:$name"
}

@Serializable
enum class Wings(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без криле", "No wings", "➖"),
    BUTTERFLY("Пеперудени", "Butterfly", "🦋", 80),
    BAT("Прилепови", "Bat", "🦇", 100),
    ANGEL("Ангелски", "Angel", "👼", 120),
    DRAGON("Драконови", "Dragon", "🐉", 150),
    ;

    override val shopId: String get() = "wings:$name"
}

@Serializable
enum class Aura(override val bg: String, override val en: String, override val emoji: String, override val price: Int = 0) : Cosmetic {
    NONE("Без магия", "No magic", "➖"),
    GLOW("Сияние", "Glow", "🌟", 150),
    SPARKLES("Искрици", "Sparkles", "💫", 200),
    RAINBOW("Дъга", "Rainbow", "🌈", 250),
    ;

    override val shopId: String get() = "aura:$name"
}

/**
 * Everything about how this ZnaiKo looks, down to the eyelashes. Colours are ARGB Longs.
 * Sizes are multipliers around 1.0; [eyeHeight] moves the eyes up or down a little.
 */
@Serializable
data class CreatureLook(
    val shape: BodyShape = BodyShape.ROUND,
    val width: Float = 1f,
    val height: Float = 1f,
    val bodyColor: Long = 0xFF7BD389,
    val belly: Boolean = true,
    val bellyColor: Long = 0xFFB8EBC0,
    val pattern: Pattern = Pattern.NONE,
    val patternColor: Long = 0xFF4FA85E,
    /** 0 = soft plush, 1 = shiny vinyl toy. */
    val glossy: Float = 0.35f,
    val eyes: EyeStyle = EyeStyle.ROUND,
    val eyeCount: Int = 2,
    val eyeSize: Float = 1f,
    val eyeSpacing: Float = 1f,
    val eyeHeight: Float = 0f,
    val irisColor: Long = 0xFF2D2A32,
    val lashes: Boolean = false,
    val brows: Brows = Brows.AUTO,
    val mouth: MouthStyle = MouthStyle.SMILE,
    /** Rosy cheeks all the time, not only when happy. */
    val blush: Boolean = false,
    val cheekColor: Long = 0xFFFF8FA3,
    val nose: Nose = Nose.NONE,
    val noseColor: Long = 0xFF2D2A32,
    val ears: Ears = Ears.NONE,
    val earInnerColor: Long = 0xFFFFB5C2,
    val top: HeadTop = HeadTop.SPROUT,
    val topColor: Long = 0xFF4FA85E,
    val tail: Tail = Tail.NONE,
    val wings: Wings = Wings.NONE,
    val wingColor: Long = 0xFFBDE0FE,
    val whiskers: Boolean = false,
    val armSize: Float = 1f,
    val feetSize: Float = 1f,
    /** Null: the body colour. */
    val feetColor: Long? = null,
    val aura: Aura = Aura.NONE,
) {
    /** The same look with every value inside its allowed range. */
    fun clamped(): CreatureLook = copy(
        width = width.coerceIn(0.8f, 1.25f),
        height = height.coerceIn(0.8f, 1.25f),
        glossy = glossy.coerceIn(0f, 1f),
        eyeCount = eyeCount.coerceIn(1, 3),
        eyeSize = eyeSize.coerceIn(0.7f, 1.5f),
        eyeSpacing = eyeSpacing.coerceIn(0.7f, 1.3f),
        eyeHeight = eyeHeight.coerceIn(-0.15f, 0.2f),
        armSize = armSize.coerceIn(0.6f, 1.5f),
        feetSize = feetSize.coerceIn(0.6f, 1.5f),
    )

    /** Shop items this look uses that must be owned. */
    fun paidParts(): List<Cosmetic> = listOf(shape, pattern, eyes, brows, mouth, nose, ears, top, tail, wings, aura).filter { it.price > 0 }
}

/** A ready-made character to start from; everything can be changed afterwards. */
data class Preset(val id: String, val bg: String, val en: String, val emoji: String, val look: CreatureLook) {
    fun label(lang: Lang) = lang.pick(bg, en)
}

object Looks {
    val PRESETS: List<Preset> = listOf(
        Preset("znaiko", "Знайко", "ZnaiKo", "🌱", CreatureLook()),
        Preset(
            "kitty", "Котенце", "Kitty", "🐱",
            CreatureLook(
                bodyColor = 0xFFF4A261, bellyColor = 0xFFFFF1D6, pattern = Pattern.STRIPES, patternColor = 0xFFD9822B,
                ears = Ears.CAT, tail = Tail.CAT, nose = Nose.DOT, noseColor = 0xFFF15BB5, mouth = MouthStyle.CAT,
                whiskers = true, top = HeadTop.NONE, blush = true, irisColor = 0xFF2A9D8F, eyes = EyeStyle.OVAL,
            ),
        ),
        Preset(
            "bunny", "Зайче", "Bunny", "🐰",
            CreatureLook(
                bodyColor = 0xFFF7F4F2, bellyColor = 0xFFFFE3EC, ears = Ears.BUNNY, tail = Tail.BUNNY, nose = Nose.BUTTON,
                noseColor = 0xFFFF8FA3, mouth = MouthStyle.TINY, blush = true, top = HeadTop.NONE, eyes = EyeStyle.BIG,
                irisColor = 0xFF6D4C41, lashes = true, shape = BodyShape.TALL,
            ),
        ),
        Preset(
            "bear", "Мече", "Bear", "🐻",
            CreatureLook(
                bodyColor = 0xFF9C6B45, bellyColor = 0xFFE7C9A0, ears = Ears.BEAR, earInnerColor = 0xFFE7C9A0, nose = Nose.SNOUT,
                noseColor = 0xFF3E2723, shape = BodyShape.CHUBBY, top = HeadTop.NONE, glossy = 0.1f, width = 1.1f,
            ),
        ),
        Preset(
            "dragon", "Драконче", "Little dragon", "🐲",
            CreatureLook(
                bodyColor = 0xFF9B5DE5, bellyColor = 0xFFFFE066, top = HeadTop.HORNS, topColor = 0xFFFFC857, tail = Tail.DRAGON,
                mouth = MouthStyle.FANG, pattern = Pattern.SPOTS, patternColor = 0xFF7B3FC4, eyes = EyeStyle.SPARKLE,
                irisColor = 0xFFE4572E, shape = BodyShape.PEAR,
            ),
        ),
        Preset(
            "alien", "Извънземно", "Alien", "👽",
            CreatureLook(
                bodyColor = 0xFFA7F070, belly = false, eyeCount = 3, eyeSize = 0.85f, top = HeadTop.ANTENNAE, topColor = 0xFF5E60CE,
                shape = BodyShape.BEAN, mouth = MouthStyle.TINY, glossy = 0.8f, irisColor = 0xFF5E60CE,
            ),
        ),
        Preset(
            "penguin", "Пингвинче", "Penguin", "🐧",
            CreatureLook(
                bodyColor = 0xFF2D3142, bellyColor = 0xFFF8F9FA, nose = Nose.BEAK, noseColor = 0xFFFFA62B, feetColor = 0xFFFFA62B,
                shape = BodyShape.PEAR, armSize = 0.8f, top = HeadTop.NONE, blush = true, glossy = 0.6f,
            ),
        ),
        Preset(
            "frog", "Жабче", "Froggy", "🐸",
            CreatureLook(
                bodyColor = 0xFF52B788, bellyColor = 0xFFD8F3DC, eyes = EyeStyle.BIG, eyeHeight = 0.18f, eyeSpacing = 1.2f,
                mouth = MouthStyle.GRIN, pattern = Pattern.SPOTS, patternColor = 0xFF2D6A4F, top = HeadTop.NONE, width = 1.15f, height = 0.9f,
            ),
        ),
        Preset(
            "monster", "Чудовище", "Little monster", "👾",
            CreatureLook(
                bodyColor = 0xFF4CC9F0, bellyColor = 0xFFCAF0F8, eyeCount = 1, eyeSize = 1.4f, mouth = MouthStyle.FANG,
                top = HeadTop.HORNS, topColor = 0xFFFFFFFF, brows = Brows.THICK, pattern = Pattern.SPOTS, patternColor = 0xFF3A86FF,
                shape = BodyShape.CHUBBY, glossy = 0.15f,
            ),
        ),
        Preset(
            "fox", "Лисиче", "Fox cub", "🦊",
            CreatureLook(
                bodyColor = 0xFFE76F51, bellyColor = 0xFFFFF4E6, ears = Ears.CAT, earInnerColor = 0xFF2D2A32, tail = Tail.CAT,
                nose = Nose.DOT, mouth = MouthStyle.CAT, top = HeadTop.NONE, eyes = EyeStyle.OVAL, irisColor = 0xFF8D5524,
                shape = BodyShape.TALL, feetColor = 0xFF2D2A32,
            ),
        ),
    )

    fun preset(id: String): Preset? = PRESETS.firstOrNull { it.id == id }

    val PALETTE: List<Long> = listOf(
        0xFF7BD389, 0xFF52B788, 0xFFA7F070, 0xFF4CC9F0, 0xFF3F88C5, 0xFF5E60CE, 0xFF9B5DE5, 0xFFF15BB5,
        0xFFFF8FA3, 0xFFE4572E, 0xFFE76F51, 0xFFF4A261, 0xFFFFC857, 0xFFFFE066, 0xFF9C6B45, 0xFF6D4C41,
        0xFF2D3142, 0xFF2D2A32, 0xFFF7F4F2, 0xFFFFFFFF,
    )

    /** A surprise look from the free parts: one base colour and matching lighter and darker shades. */
    fun random(random: Random = Random.Default): CreatureLook {
        val base = PALETTE[random.nextInt(PALETTE.size)]
        fun <T : Cosmetic> free(values: Array<T>) = values.filter { it.price == 0 }.let { it[random.nextInt(it.size)] }
        return CreatureLook(
            shape = free(BodyShape.entries.toTypedArray()),
            width = 0.9f + random.nextFloat() * 0.25f,
            height = 0.9f + random.nextFloat() * 0.2f,
            bodyColor = base,
            belly = random.nextFloat() < 0.8f,
            bellyColor = mix(base, 0xFFFFFFFF, 0.6f),
            pattern = free(Pattern.entries.toTypedArray()),
            patternColor = mix(base, 0xFF000000, 0.3f),
            glossy = random.nextFloat() * 0.8f,
            eyes = free(EyeStyle.entries.toTypedArray()),
            eyeCount = if (random.nextFloat() < 0.12f) (if (random.nextBoolean()) 1 else 3) else 2,
            eyeSize = 0.85f + random.nextFloat() * 0.4f,
            eyeSpacing = 0.85f + random.nextFloat() * 0.3f,
            eyeHeight = -0.05f + random.nextFloat() * 0.15f,
            irisColor = listOf(0xFF2D2A32, 0xFF3F88C5, 0xFF2A9D8F, 0xFF8D5524, 0xFF5E60CE)[random.nextInt(5)],
            lashes = random.nextBoolean(),
            brows = free(Brows.entries.toTypedArray()),
            mouth = free(MouthStyle.entries.toTypedArray()),
            blush = random.nextBoolean(),
            nose = free(Nose.entries.toTypedArray()),
            ears = free(Ears.entries.toTypedArray()),
            top = free(HeadTop.entries.toTypedArray()),
            topColor = mix(base, 0xFF000000, 0.35f),
            tail = free(Tail.entries.toTypedArray()),
            whiskers = random.nextFloat() < 0.3f,
            armSize = 0.85f + random.nextFloat() * 0.3f,
            feetSize = 0.85f + random.nextFloat() * 0.3f,
        ).clamped()
    }

    /** Blends two ARGB colours; [t] = 0 gives [a], 1 gives [b]. */
    fun mix(a: Long, b: Long, t: Float): Long {
        fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()
        fun blend(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
    }
}
