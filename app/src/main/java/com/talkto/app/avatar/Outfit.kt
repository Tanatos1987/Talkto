package com.talkto.app.avatar

import kotlinx.serialization.Serializable

@Serializable
enum class Hat { NONE, PARTY, BEANIE, CROWN, TOP_HAT, CAP }

@Serializable
enum class Glasses { NONE, ROUND, SUNGLASSES, HEART, MONOCLE }

@Serializable
enum class Clothes { NONE, SCARF, BOWTIE, HOODIE, TIE }

/** Stored locally as JSON in DataStore; ARGB colours as Long so the model has no Compose dependency. */
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
