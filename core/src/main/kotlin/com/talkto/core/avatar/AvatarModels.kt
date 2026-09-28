package com.talkto.core.avatar

import kotlinx.serialization.Serializable

@Serializable
enum class AvatarStyle(val prompt: String) {
    ANIME_2D(
        "anime-style 2D character portrait of the same person, clean cel shading, soft pastel background, " +
            "large expressive eyes, front-facing, head and shoulders, centered, mouth closed, eyes open",
    ),
    CARTOON_3D(
        "stylized 3D animated-film character portrait of the same person, soft global illumination, subsurface skin, " +
            "friendly proportions, front-facing, head and shoulders, centered, neutral expression, plain background",
    ),
    PIXEL_ART(
        "64x64 pixel-art tamagotchi style portrait of the same person scaled up crisply, limited palette, " +
            "front-facing, centered face, plain background",
    ),
    CHIBI(
        "cute chibi character of the same person, big head small body, bold outlines, front-facing, centered, " +
            "neutral expression, plain pastel background",
    ),
    WATERCOLOR(
        "watercolor storybook illustration portrait of the same person, gentle textures, front-facing, centered, " +
            "neutral expression, light paper background",
    ),
    ;

    companion object {
        const val NEGATIVE_PROMPT =
            "text, watermark, logo, extra faces, extra limbs, cropped head, side profile, closed eyes, open mouth, blurry, deformed"
    }
}

@Serializable
data class GeneratedAvatar(
    val id: String,
    val style: AvatarStyle,
    /** Absolute path of the PNG on internal storage. */
    val imagePath: String,
    val createdAtEpochMs: Long,
    val fromCache: Boolean = false,
)

/** Face anchors in normalised (0..1) image coordinates, used for blinking, lip-sync and outfit placement. */
@Serializable
data class FaceAnchors(
    val leftEyeX: Float = 0.38f,
    val leftEyeY: Float = 0.42f,
    val rightEyeX: Float = 0.62f,
    val rightEyeY: Float = 0.42f,
    val mouthX: Float = 0.5f,
    val mouthY: Float = 0.66f,
    val faceLeft: Float = 0.22f,
    val faceTop: Float = 0.18f,
    val faceRight: Float = 0.78f,
    val faceBottom: Float = 0.86f,
    val detected: Boolean = false,
) {
    val eyeDistance: Float get() = kotlin.math.hypot(rightEyeX - leftEyeX, rightEyeY - leftEyeY)
    val faceWidth: Float get() = faceRight - faceLeft
}

@Serializable
enum class Expression { NEUTRAL, HAPPY, SAD, SURPRISED, THINKING, SLEEPY, ANGRY, LOVE, CONFUSED, TONGUE }

@Serializable
enum class Gesture { NONE, NOD, SHAKE, WAVE, BOUNCE, SPIN }

/** What `animate_avatar` asks for. */
@Serializable
data class AnimationCommand(
    val expression: Expression = Expression.NEUTRAL,
    val gesture: Gesture = Gesture.NONE,
    /** Optional line to speak with lip-sync. */
    val speech: String? = null,
    /** How long the expression is held before returning to the mood-driven idle state. */
    val holdMs: Long = 2_500,
)
