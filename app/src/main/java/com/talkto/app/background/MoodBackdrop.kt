package com.talkto.app.background

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.talkto.core.scene.MoodScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The world behind the pet, following its mood. With the user's own photos for a mood, those are shown
 * (slow Ken Burns pan, rotating every 40 s) with the mood's weather on top; otherwise an animated scene is drawn.
 * Scene changes crossfade over a second, so a slap turns the beach into a storm smoothly.
 */
@Composable
fun MoodBackdrop(scene: MoodScene, photos: List<String>, modifier: Modifier = Modifier) {
    Crossfade(targetState = scene to photos, animationSpec = tween(1_200), modifier = modifier, label = "scene") { (s, p) ->
        Box(Modifier.fillMaxSize()) {
            if (p.isNotEmpty()) PhotoLayer(p) else Canvas(Modifier.fillMaxSize()) { drawSky(s) }
            WeatherLayer(s, overPhoto = p.isNotEmpty())
        }
    }
}

// ------------------------------------------------------------------------ photos

@Composable
private fun PhotoLayer(paths: List<String>) {
    var index by remember(paths) { mutableIntStateOf(Random.nextInt(paths.size)) }
    LaunchedEffect(paths) {
        while (paths.size > 1) {
            delay(40_000)
            index = (index + 1) % paths.size
        }
    }
    val path = paths[index % paths.size]
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    }
    val loop = rememberInfiniteTransition(label = "kenburns")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "kb")
    image?.let {
        Image(
            it, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = 1.06f + 0.08f * t
                scaleX = s; scaleY = s
                translationX = (t - 0.5f) * size.width * 0.05f
            },
        )
    }
}

// ------------------------------------------------------------------------ skies

private fun DrawScope.drawSky(s: MoodScene) {
    val w = size.width
    val h = size.height
    when (s) {
        MoodScene.BEACH -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD3F7), Color(0xFFD9F4FF)), endY = h * 0.62f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A8), Color(0x00FFF2A8)), Offset(w * 0.8f, h * 0.18f), w * 0.3f), w * 0.3f, Offset(w * 0.8f, h * 0.18f))
            drawCircle(Color(0xFFFFE066), w * 0.09f, Offset(w * 0.8f, h * 0.18f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2FA4D7), Color(0xFF53C3E8)), startY = h * 0.58f, endY = h * 0.75f), Offset(0f, h * 0.58f), Size(w, h * 0.17f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF6DDA4), Color(0xFFEBC27A)), startY = h * 0.72f, endY = h), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
            // palm
            val trunk = Path().apply { moveTo(w * 0.1f, h); cubicTo(w * 0.12f, h * 0.8f, w * 0.16f, h * 0.62f, w * 0.2f, h * 0.5f) }
            drawPath(trunk, Color(0xFF9A6B3F), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.025f, cap = StrokeCap.Round))
            listOf(-40f, -10f, 20f, 60f, 110f).forEach { deg ->
                val r = Math.toRadians(deg.toDouble())
                val tip = Offset(w * 0.2f + cos(r).toFloat() * w * 0.16f, h * 0.5f + sin(r).toFloat() * w * 0.08f + w * 0.03f)
                val leaf = Path().apply {
                    moveTo(w * 0.2f, h * 0.5f)
                    cubicTo(w * 0.2f + (tip.x - w * 0.2f) * 0.5f, h * 0.5f - w * 0.06f, tip.x, tip.y - w * 0.02f, tip.x, tip.y)
                }
                drawPath(leaf, Color(0xFF3FA66A), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.03f, cap = StrokeCap.Round))
            }
        }
        MoodScene.MEADOW -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF9ED8FF), Color(0xFFE8F7FF)), endY = h * 0.7f))
            drawOval(Color(0xFF8BD17C), Offset(-w * 0.3f, h * 0.62f), Size(w * 1.2f, h * 0.6f))
            drawOval(Color(0xFF6CC06A), Offset(w * 0.2f, h * 0.7f), Size(w * 1.2f, h * 0.6f))
        }
        MoodScene.SUNSET -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFF7E9D), Color(0xFFFFB27A), Color(0xFFFFE0A3)), endY = h * 0.75f))
            drawCircle(Color(0xFFFFD27A), w * 0.18f, Offset(w * 0.5f, h * 0.72f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8E4B7A), Color(0xFF5B2F5E)), startY = h * 0.72f, endY = h), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
        }
        MoodScene.RAIN -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6D7B8A), Color(0xFFA7B2BC))))
            cloud(Offset(w * 0.25f, h * 0.15f), w * 0.2f, Color(0xFF55616E))
            cloud(Offset(w * 0.72f, h * 0.1f), w * 0.24f, Color(0xFF4B5763))
        }
        MoodScene.STORM -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2140), Color(0xFF4A3F63))))
            cloud(Offset(w * 0.3f, h * 0.12f), w * 0.26f, Color(0xFF1F1830))
            cloud(Offset(w * 0.75f, h * 0.16f), w * 0.22f, Color(0xFF241C38))
        }
        MoodScene.NIGHT -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0E1A3A), Color(0xFF263B6E))))
            drawCircle(Color(0xFFFFF6D6), w * 0.1f, Offset(w * 0.78f, h * 0.18f))
            drawCircle(Color(0xFF15254D), w * 0.085f, Offset(w * 0.82f, h * 0.16f)) // crescent
        }
        MoodScene.SPACE -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF05040F), Color(0xFF1B1036))))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB08CFF), Color(0xFF4B2E99)), Offset(w * 0.2f, h * 0.25f), w * 0.12f), w * 0.12f, Offset(w * 0.2f, h * 0.25f))
            drawOval(Color(0x88E0D4FF), Offset(w * 0.02f, h * 0.24f), Size(w * 0.36f, w * 0.05f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.01f))
        }
        MoodScene.FOG -> drawRect(Brush.verticalGradient(listOf(Color(0xFFBDB6CC), Color(0xFFE4E0EA))))
        MoodScene.FIREWORKS -> drawRect(Brush.verticalGradient(listOf(Color(0xFF120B2E), Color(0xFF2D1B5A))))
    }
}

private fun DrawScope.cloud(c: Offset, r: Float, color: Color) {
    drawCircle(color, r * 0.55f, c)
    drawCircle(color, r * 0.42f, c + Offset(-r * 0.55f, r * 0.12f))
    drawCircle(color, r * 0.45f, c + Offset(r * 0.55f, r * 0.1f))
    drawOval(color, Offset(c.x - r * 0.95f, c.y), Size(r * 1.9f, r * 0.45f))
}

// ------------------------------------------------------------------------ weather

/** Animated layer: waves, drifting clouds, rain, lightning, stars, hearts, fog, fireworks. */
@Composable
private fun WeatherLayer(s: MoodScene, overPhoto: Boolean) {
    val loop = rememberInfiniteTransition(label = "weather")
    val t by loop.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "t")
    val seeds = remember { List(80) { i -> Random(i * 7919) }.map { r -> floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (overPhoto) {
            // Tint the user's photo towards the mood, so the pet stays readable on any picture.
            drawRect(TINT.getValue(s))
        }
        when (s) {
            MoodScene.BEACH -> {
                repeat(3) { k ->
                    val y = h * (0.6f + 0.04f * k)
                    val p = Path().apply {
                        moveTo(0f, y)
                        var x = 0f
                        while (x <= w) {
                            lineTo(x, y + sin(x / w * 12f + t * (1.2f + k * 0.3f)) * h * 0.006f); x += w / 40f
                        }
                    }
                    drawPath(p, Color.White.copy(alpha = 0.5f - k * 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(h * 0.004f))
                }
                seeds.take(3).forEachIndexed { i, sd ->
                    val x = ((sd[0] + t * 0.02f * (1 + i)) % 1.2f - 0.1f) * w
                    val y = h * (0.12f + sd[1] * 0.2f) + sin(t * 2f + i) * h * 0.01f
                    val wing = w * 0.025f
                    val flap = sin(t * 6f + i) * wing * 0.4f
                    drawLine(Color(0xFF3A4A5A), Offset(x - wing, y - flap), Offset(x, y), strokeWidth = w * 0.005f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF3A4A5A), Offset(x, y), Offset(x + wing, y - flap), strokeWidth = w * 0.005f, cap = StrokeCap.Round)
                }
            }
            MoodScene.MEADOW -> seeds.take(4).forEachIndexed { i, sd ->
                val x = ((sd[0] + t * 0.008f * (1 + sd[2])) % 1.3f - 0.15f) * w
                cloud(Offset(x, h * (0.1f + sd[1] * 0.25f)), w * (0.1f + sd[3] * 0.08f), Color.White.copy(alpha = 0.9f))
            }
            MoodScene.RAIN, MoodScene.STORM -> {
                val slant = if (s == MoodScene.STORM) 0.35f else 0.12f
                val len = h * if (s == MoodScene.STORM) 0.06f else 0.045f
                val color = Color(0xFFDDE8F2).copy(alpha = if (s == MoodScene.STORM) 0.55f else 0.45f)
                seeds.forEach { sd ->
                    val speed = 0.9f + sd[2] * 0.6f
                    val y = ((sd[1] + t * speed) % 1.1f) * h
                    val x = (sd[0] * 1.2f - 0.1f) * w + y * slant
                    drawLine(color, Offset(x, y), Offset(x - len * slant, y - len), strokeWidth = w * 0.004f, cap = StrokeCap.Round)
                }
                if (s == MoodScene.STORM) {
                    // A flash every ~7 s.
                    val phase = t % 7f
                    if (phase < 0.25f) {
                        drawRect(Color.White.copy(alpha = (0.25f - phase) * 2.4f))
                        val bolt = Path().apply {
                            moveTo(w * 0.62f, 0f); lineTo(w * 0.55f, h * 0.18f); lineTo(w * 0.61f, h * 0.2f); lineTo(w * 0.5f, h * 0.42f)
                        }
                        drawPath(bolt, Color(0xFFFFF7B0), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.012f))
                    }
                }
            }
            MoodScene.NIGHT, MoodScene.SPACE, MoodScene.FIREWORKS -> {
                seeds.forEach { sd ->
                    val twinkle = 0.4f + 0.6f * abs(sin(t * (0.8f + sd[2] * 2f) + sd[3] * 10f))
                    drawCircle(Color.White.copy(alpha = twinkle * 0.9f), w * (0.002f + sd[2] * 0.004f), Offset(sd[0] * w, sd[1] * h * if (s == MoodScene.NIGHT) 0.7f else 1f))
                }
                if (s == MoodScene.FIREWORKS) {
                    repeat(3) { k ->
                        val period = 3f + k
                        val p = (t % period) / period
                        val cx = w * (0.2f + 0.3f * k)
                        val cy = h * (0.2f + 0.1f * (k % 2))
                        val hue = listOf(Color(0xFFFFC857), Color(0xFFF15BB5), Color(0xFF7BD389))[k]
                        repeat(14) { j ->
                            val a = 2 * PI * j / 14
                            val r = w * 0.16f * p
                            drawCircle(hue.copy(alpha = (1f - p).coerceIn(0f, 1f)), w * 0.008f, Offset(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r + p * p * h * 0.05f))
                        }
                    }
                }
            }
            MoodScene.SUNSET -> seeds.take(10).forEach { sd ->
                val p = ((sd[1] + t * 0.05f * (0.6f + sd[2])) % 1f)
                val x = sd[0] * w + sin(t + sd[3] * 6f) * w * 0.03f
                val y = h * (1f - p)
                heart(Offset(x, y), w * (0.02f + sd[2] * 0.02f), Color(0xFFF15BB5).copy(alpha = 0.7f * (1f - p)))
            }
            MoodScene.FOG -> repeat(4) { k ->
                val x = (sin(t * 0.1f + k) * 0.2f) * w
                drawOval(Color.White.copy(alpha = 0.28f), Offset(x - w * 0.2f, h * (0.2f + 0.2f * k)), Size(w * 1.4f, h * 0.12f))
            }
        }
    }
}

private fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.35f)
        cubicTo(c.x - s * 1.1f, c.y - s * 0.3f, c.x - s * 0.45f, c.y - s * 1.0f, c.x, c.y - s * 0.4f)
        cubicTo(c.x + s * 0.45f, c.y - s * 1.0f, c.x + s * 1.1f, c.y - s * 0.3f, c.x, c.y + s * 0.35f)
        close()
    }
    drawPath(p, color)
}

private val TINT: Map<MoodScene, Color> = mapOf(
    MoodScene.BEACH to Color(0x14FFE066),
    MoodScene.MEADOW to Color(0x10FFFFFF),
    MoodScene.SUNSET to Color(0x30FF7E9D),
    MoodScene.RAIN to Color(0x405D6B7A),
    MoodScene.STORM to Color(0x662B2140),
    MoodScene.NIGHT to Color(0x800E1A3A),
    MoodScene.SPACE to Color(0x8805040F),
    MoodScene.FOG to Color(0x55E4E0EA),
    MoodScene.FIREWORKS to Color(0x77120B2E),
)
