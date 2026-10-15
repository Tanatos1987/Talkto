package com.talkto.app.ui.look

import com.talkto.core.look.Aura
import com.talkto.core.look.BodyShape
import com.talkto.core.look.Brows
import com.talkto.core.look.Clothes
import com.talkto.core.look.Cosmetic
import com.talkto.core.look.CreatureLook
import com.talkto.core.look.Ears
import com.talkto.core.look.EyeStyle
import com.talkto.core.look.Furniture
import com.talkto.core.look.Glasses
import com.talkto.core.look.Hat
import com.talkto.core.look.HeadTop
import com.talkto.core.look.HouseDecor
import com.talkto.core.look.HouseLook
import com.talkto.core.look.MouthStyle
import com.talkto.core.look.Nose
import com.talkto.core.look.OutfitConfig
import com.talkto.core.look.Pattern
import com.talkto.core.look.Tail
import com.talkto.core.look.Wings

/** Everything ZnaiKo wears and has, changed together. */
data class Wearing(val look: CreatureLook, val outfit: OutfitConfig, val house: HouseLook) {

    /** Puts [thing] on (or into the house). Decor and furniture toggle. Some parts bring a colour that suits them. */
    fun with(thing: Cosmetic): Wearing = when (thing) {
        is Hat -> copy(outfit = outfit.copy(hat = thing))
        is Glasses -> copy(outfit = outfit.copy(glasses = thing))
        is Clothes -> copy(outfit = outfit.copy(clothes = thing))
        is BodyShape -> copy(look = look.copy(shape = thing))
        is Pattern -> copy(look = look.copy(pattern = thing, patternColor = if (thing == Pattern.STARS && look.patternColor == DEFAULT.patternColor) GOLD else look.patternColor))
        is EyeStyle -> copy(look = look.copy(eyes = thing))
        is Brows -> copy(look = look.copy(brows = thing))
        is MouthStyle -> copy(look = look.copy(mouth = thing))
        is Nose -> copy(look = look.copy(nose = thing, noseColor = if (thing == Nose.BEAK && look.noseColor == DEFAULT.noseColor) ORANGE else look.noseColor))
        is Ears -> copy(look = look.copy(ears = thing))
        is HeadTop -> copy(look = look.copy(top = thing, topColor = if (thing == HeadTop.UNICORN && look.topColor == DEFAULT.topColor) GOLD else look.topColor))
        is Tail -> copy(look = look.copy(tail = thing))
        is Wings -> copy(look = look.copy(wings = thing))
        is Aura -> copy(look = look.copy(aura = thing))
        is HouseDecor -> copy(house = house.copy(decor = if (thing in house.decor) house.decor - thing else house.decor + thing))
        is Furniture -> copy(house = house.copy(furniture = if (thing in house.furniture) house.furniture - thing else house.furniture + thing))
        else -> this
    }

    /** Is [thing] on right now? */
    fun wears(thing: Cosmetic): Boolean = when (thing) {
        is Hat -> outfit.hat == thing
        is Glasses -> outfit.glasses == thing
        is Clothes -> outfit.clothes == thing
        is BodyShape -> look.shape == thing
        is Pattern -> look.pattern == thing
        is EyeStyle -> look.eyes == thing
        is Brows -> look.brows == thing
        is MouthStyle -> look.mouth == thing
        is Nose -> look.nose == thing
        is Ears -> look.ears == thing
        is HeadTop -> look.top == thing
        is Tail -> look.tail == thing
        is Wings -> look.wings == thing
        is Aura -> look.aura == thing
        is HouseDecor -> thing in house.decor
        is Furniture -> thing in house.furniture
        else -> false
    }

    private companion object {
        val DEFAULT = CreatureLook()
        const val GOLD = 0xFFFFC857
        const val ORANGE = 0xFFFFA62B
    }
}
