package com.talkto.core.look

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.Lang
import com.talkto.core.profile.AboutYou
import com.talkto.core.profile.Favourites
import com.talkto.core.profile.Fact
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.shop.CoinReason
import com.talkto.core.shop.Shop
import com.talkto.core.shop.ShopCategory
import com.talkto.core.shop.Wallet
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.random.Random

class LookTest {
    @Test fun `presets are free to use, distinct and survive JSON`() {
        assertThat(Looks.PRESETS.map { it.id }.toSet()).hasSize(Looks.PRESETS.size)
        assertThat(Looks.PRESETS.size).isAtLeast(10)
        Looks.PRESETS.forEach { p -> assertThat(p.look.paidParts()).isEmpty() }
        assertThat(Looks.PRESETS.map { it.look }.toSet()).hasSize(Looks.PRESETS.size)
        val json = Json { encodeDefaults = false }
        Looks.PRESETS.forEach { p ->
            val back = json.decodeFromString(CreatureLook.serializer(), json.encodeToString(CreatureLook.serializer(), p.look))
            assertThat(back).isEqualTo(p.look)
        }
        // An old save without any look fields gives the classic ZnaiKo.
        assertThat(Json.decodeFromString(CreatureLook.serializer(), "{}")).isEqualTo(CreatureLook())
    }

    @Test fun `random looks use only free parts and stay in range`() {
        repeat(200) { seed ->
            val l = Looks.random(Random(seed))
            assertThat(l.paidParts()).isEmpty()
            assertThat(l).isEqualTo(l.clamped())
            assertThat(l.eyeCount).isIn(1..3)
        }
        assertThat((0 until 20).map { Looks.random(Random(it)) }.toSet().size).isAtLeast(18)
    }

    @Test fun `clamping and colour mixing`() {
        val wild = CreatureLook(width = 3f, eyeCount = 7, eyeSize = 0.1f)
        assertThat(wild.clamped().width).isEqualTo(1.25f)
        assertThat(wild.clamped().eyeCount).isEqualTo(3)
        assertThat(wild.clamped().eyeSize).isEqualTo(0.7f)
        assertThat(Looks.mix(0xFF000000, 0xFFFFFFFF, 0.5f)).isEqualTo(0xFF7F7F7F)
        assertThat(Looks.mix(0xFF102030, 0xFF102030, 0.3f)).isEqualTo(0xFF102030)
    }

    @Test fun `every cosmetic has names in both languages and a unique shop id`() {
        val all = Hat.entries + Glasses.entries + Clothes.entries + BodyShape.entries + Pattern.entries + EyeStyle.entries +
            Brows.entries + MouthStyle.entries + Nose.entries + Ears.entries + HeadTop.entries + Tail.entries + Wings.entries +
            Aura.entries + HouseDecor.entries + Furniture.entries
        assertThat(all.map { it.shopId }.toSet()).hasSize(all.size)
        all.forEach { c ->
            assertThat(c.label(Lang.BG)).isNotEmpty()
            assertThat(c.label(Lang.EN)).isNotEmpty()
            assertThat(c.price).isAtLeast(0)
        }
    }
}

class ShopTest {
    @Test fun `the shop sells only paid things, every shelf has something`() {
        assertThat(Shop.items.none { it.price == 0 }).isTrue()
        ShopCategory.entries.forEach { c -> assertThat(Shop.of(c)).isNotEmpty() }
        assertThat(Shop.items.map { it.id }.toSet()).hasSize(Shop.items.size)
        assertThat(Shop.item("wings:BUTTERFLY")!!.price).isEqualTo(80)
        // A new player can buy something straight away.
        assertThat(Shop.items.minOf { it.price }).isAtMost(Shop.STARTING_COINS)
    }

    @Test fun `buying needs coins, happens once, and free things are always owned`() {
        val halo = Shop.item(Hat.HALO.shopId)!!
        var w = Wallet(100, emptySet())
        assertThat(w.owns(Hat.CROWN)).isTrue()
        assertThat(w.owns(Hat.HALO)).isFalse()
        w = w.buy(halo)!!
        assertThat(w.coins).isEqualTo(10)
        assertThat(w.owns(Hat.HALO)).isTrue()
        assertThat(w.buy(halo)).isNull()
        assertThat(w.buy(Shop.item(Wings.DRAGON.shopId)!!)).isNull()
        assertThat(w.earn(CoinReason.GAME_WON, 2).coins).isEqualTo(40)
    }
}

class HouseTest {
    @Test fun `a new house has a bed and a lamp and survives JSON`() {
        val h = HouseLook()
        assertThat(h.furniture).containsExactly(Furniture.BED, Furniture.LAMP)
        assertThat(Furniture.BED.price).isEqualTo(0)
        val json = Json.encodeToString(HouseLook.serializer(), h.copy(decor = setOf(HouseDecor.SMOKE)))
        assertThat(Json.decodeFromString(HouseLook.serializer(), json).decor).containsExactly(HouseDecor.SMOKE)
    }
}

class AboutYouTest {
    @Test fun `favourite kinds are the same key in both languages`() {
        assertThat(Favourites.canonical("Цвят")).isEqualTo("colour")
        assertThat(Favourites.canonical("color")).isEqualTo("colour")
        assertThat(Favourites.canonical("ястие")).isEqualTo("food")
        assertThat(Favourites.canonical("динозавър")).isEqualTo("динозавър")
        val f = Fact("favourite:colour", "синьо", 0)
        assertThat(ProfileRepository.label(f, Lang.BG)).isEqualTo("Любим цвят: синьо")
        assertThat(ProfileRepository.label(f, Lang.EN)).isEqualTo("Favourite colour: синьо")
    }

    @Test fun `questions and tidy answers`() {
        assertThat(AboutYou.remaining(setOf("name", "age")).first().key).isEqualTo("birthday")
        assertThat(AboutYou.QUESTIONS.map { it.key }.toSet()).hasSize(AboutYou.QUESTIONS.size)
        assertThat(AboutYou.normalize("name", "мария петрова")).isEqualTo("Мария Петрова")
        assertThat(AboutYou.normalize("age", "На 8 съм")).isEqualTo("8")
        assertThat(AboutYou.normalize("city", "  Варна. ")).isEqualTo("Варна")
        assertThat(AboutYou.normalize("dream", " ")).isNull()
    }
}
