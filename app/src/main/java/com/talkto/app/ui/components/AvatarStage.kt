package com.talkto.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.talkto.app.avatar.AvatarPose
import com.talkto.app.avatar.AvatarVisual
import com.talkto.app.avatar.Clothes
import com.talkto.app.avatar.DEFAULT_CREATURE_ANCHORS
import com.talkto.app.avatar.Glasses
import com.talkto.app.avatar.Hat
import com.talkto.app.avatar.OutfitConfig
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.FaceAnchors
import com.talkto.core.avatar.Gesture
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live portrait. A single still image (the generated avatar, or the built-in creature) comes alive
 * through layered procedural animation:
 *  - breathing: slow vertical scale anchored at the bottom edge;
 *  - blinking: skin-coloured eyelids over the detected eye positions, random 2-6 s rhythm, sometimes double;
 *  - lip-sync: a mouth shape driven by the current viseme (openness / width / roundness);
 *  - gestures: nod, shake, wave, bounce, spin as short damped curves;
 *  - expressions: overlays (blush, tears, hearts, Zz, ?, …) plus eye and mouth changes;
 *  - outfit: hats, glasses and clothes drawn as vectors, anchored to the face box.
 */
@Composable
fun AvatarStage(
    visual: AvatarVisual,
    pose: AvatarPose,
    outfit: OutfitConfig,
    sleeping: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "Talkto",
) {
    val image: ImageBitmap? = remember(visual.bitmap) { visual.bitmap?.asImageBitmap() }
    val palette = remember(visual.bitmap, visual.anchors) { samplePalette(visual.bitmap, visual.anchors) }
    val measurer = rememberTextMeasurer()

    val loop = rememberInfiniteTransition(label = "idle")
    val breath by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(if (sleeping) 4200 else 3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")

    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sleeping) {
        if (sleeping) {
            blink = 1f; return@LaunchedEffect
        }
        blink = 0f
        while (true) {
            delay(Random.nextLong(2_000, 6_000))
            repeat(if (Random.nextFloat() < 0.18f) 2 else 1) {
                animate(0f, 1f, animationSpec = tween(70)) { v, _ -> blink = v }
                animate(1f, 0f, animationSpec = tween(110)) { v, _ -> blink = v }
                delay(90)
            }
        }
    }

    val gesture = remember { Animatable(0f) }
    LaunchedEffect(pose.gestureId) {
        gesture.snapTo(0f)
        if (pose.gesture != Gesture.NONE) gesture.animateTo(1f, tween(if (pose.gesture == Gesture.SPIN) 700 else 950))
    }

    val mouthOpen by animateFloatAsState(pose.viseme.openness, tween(55), label = "mouthOpen")
    val mouthWidth by animateFloatAsState(pose.viseme.width, tween(55), label = "mouthWidth")
    val mouthRound by animateFloatAsState(pose.viseme.round, tween(55), label = "mouthRound")

    Box(
        modifier
            .semantics { contentDescription = description }
            .graphicsLayer {
                val g = gesture.value
                val damped = sin(g * PI.toFloat() * 4f) * (1f - g)
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 1f + 0.010f * breath
                scaleY = 1f + 0.024f * breath
                translationY = -6f * breath
                when (pose.gesture) {
                    Gesture.NOD -> translationY += damped * 22f
                    Gesture.SHAKE -> translationX = damped * 26f
                    Gesture.WAVE -> rotationZ = damped * 9f
                    Gesture.BOUNCE -> translationY -= abs(sin(g * PI.toFloat() * 3f)) * (1f - g) * 70f
                    Gesture.SPIN -> rotationY = g * 360f
                    Gesture.NONE -> Unit
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } }) {
            val frame = fitFrame(size, visual.bitmap)
            val a = visual.anchors
            if (image != null) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
                    dstSize = IntSize(frame.width.toInt(), frame.height.toInt()),
                )
            } else {
                drawCreature(frame, pose.expression)
            }

            val lidClosure = when {
                sleeping -> 1f
                pose.expression == Expression.SLEEPY -> maxOf(0.55f, blink)
                pose.expression == Expression.HAPPY || pose.expression == Expression.LOVE -> maxOf(0.25f, blink)
                else -> blink
            }
            drawEyelids(frame, a, palette.skin, lidClosure)

            val showMouth = pose.speaking || pose.expression == Expression.SURPRISED || image == null
            if (showMouth) {
                val open = when {
                    pose.speaking -> mouthOpen
                    pose.expression == Expression.SURPRISED -> 0.7f
                    else -> 0f
                }
                val round = if (pose.expression == Expression.SURPRISED && !pose.speaking) 0.9f else mouthRound
                drawMouth(frame, a, palette, open, mouthWidth, round, pose.expression, coverOriginal = image != null)
            }

            drawExpression(frame, a, pose.expression, phase, measurer, sleeping)
            drawOutfit(frame, a, outfit)
        }
    }
}

// ------------------------------------------------------------------------ geometry

private data class Frame(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun x(nx: Float) = left + nx * width
    fun y(ny: Float) = top + ny * height
}

private fun fitFrame(canvas: Size, bitmap: Bitmap?): Frame {
    val aspect = bitmap?.let { it.width.toFloat() / it.height } ?: 1f
    val w: Float
    val h: Float
    if (canvas.width / canvas.height > aspect) {
        h = canvas.height; w = h * aspect
    } else {
        w = canvas.width; h = w / aspect
    }
    return Frame((canvas.width - w) / 2f, (canvas.height - h) / 2f, w, h)
}

private data class Palette(val skin: Color, val lip: Color)

private val CREATURE_BODY = Color(0xFF7BD389)
private val CREATURE_BELLY = Color(0xFFB8EBC0)
private val INK = Color(0xFF2D2A32)
private val MOUTH_INSIDE = Color(0xFF4A1F2A)

private fun samplePalette(bitmap: Bitmap?, a: FaceAnchors): Palette {
    if (bitmap == null) return Palette(CREATURE_BODY, Color(0xFF3E8E4E))
    fun at(nx: Float, ny: Float): Color {
        val x = (nx * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (ny * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        return Color(bitmap.getPixel(x, y))
    }
    val d = a.eyeDistance
    // Forehead just above each eye, averaged: robust against eyebrows and highlights.
    val s1 = at(a.leftEyeX, a.leftEyeY - d * 0.45f)
    val s2 = at(a.rightEyeX, a.rightEyeY - d * 0.45f)
    val s3 = at((a.leftEyeX + a.mouthX) / 2f, (a.leftEyeY + a.mouthY) / 2f)
    val skin = Color((s1.red + s2.red + s3.red) / 3f, (s1.green + s2.green + s3.green) / 3f, (s1.blue + s2.blue + s3.blue) / 3f)
    return Palette(skin, at(a.mouthX, a.mouthY))
}

// ------------------------------------------------------------------------ creature

private fun DrawScope.drawCreature(f: Frame, expression: Expression) {
    val body = Path().apply {
        moveTo(f.x(0.5f), f.y(0.2f))
        cubicTo(f.x(0.86f), f.y(0.2f), f.x(0.9f), f.y(0.6f), f.x(0.84f), f.y(0.8f))
        cubicTo(f.x(0.78f), f.y(0.92f), f.x(0.22f), f.y(0.92f), f.x(0.16f), f.y(0.8f))
        cubicTo(f.x(0.1f), f.y(0.6f), f.x(0.14f), f.y(0.2f), f.x(0.5f), f.y(0.2f))
        close()
    }
    drawOval(Color.Black.copy(alpha = 0.10f), Offset(f.x(0.22f), f.y(0.88f)), Size(f.width * 0.56f, f.height * 0.06f))
    drawPath(body, CREATURE_BODY)
    drawOval(CREATURE_BELLY, Offset(f.x(0.32f), f.y(0.62f)), Size(f.width * 0.36f, f.height * 0.24f))
    // Feet
    drawOval(CREATURE_BODY, Offset(f.x(0.28f), f.y(0.84f)), Size(f.width * 0.14f, f.height * 0.07f))
    drawOval(CREATURE_BODY, Offset(f.x(0.58f), f.y(0.84f)), Size(f.width * 0.14f, f.height * 0.07f))

    val a = DEFAULT_CREATURE_ANCHORS
    val eyeW = f.width * 0.075f
    val eyeH = f.height * 0.10f
    val lookUp = if (expression == Expression.THINKING) -f.height * 0.02f else 0f
    listOf(a.leftEyeX, a.rightEyeX).forEach { ex ->
        drawOval(INK, Offset(f.x(ex) - eyeW / 2, f.y(a.leftEyeY) - eyeH / 2 + lookUp), Size(eyeW, eyeH))
        drawCircle(Color.White, eyeW * 0.18f, Offset(f.x(ex) + eyeW * 0.15f, f.y(a.leftEyeY) - eyeH * 0.2f + lookUp))
    }
    if (expression == Expression.ANGRY || expression == Expression.CONFUSED) {
        val browY = f.y(a.leftEyeY) - eyeH * 0.9f
        drawLine(INK, Offset(f.x(a.leftEyeX) - eyeW, browY - (if (expression == Expression.ANGRY) eyeH * 0.2f else 0f)), Offset(f.x(a.leftEyeX) + eyeW, browY + (if (expression == Expression.ANGRY) eyeH * 0.2f else 0f)), strokeWidth = eyeW * 0.25f, cap = StrokeCap.Round)
        val tilt = if (expression == Expression.ANGRY) eyeH * 0.2f else -eyeH * 0.3f
        drawLine(INK, Offset(f.x(a.rightEyeX) - eyeW, browY + tilt), Offset(f.x(a.rightEyeX) + eyeW, browY - tilt), strokeWidth = eyeW * 0.25f, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------------ face layers

private fun DrawScope.drawEyelids(f: Frame, a: FaceAnchors, skin: Color, closure: Float) {
    if (closure <= 0.02f) return
    val eyeW = a.eyeDistance * f.width * 0.62f
    val eyeH = eyeW * 0.72f
    listOf(a.leftEyeX to a.leftEyeY, a.rightEyeX to a.rightEyeY).forEach { (ex, ey) ->
        val cx = f.x(ex)
        val cy = f.y(ey)
        val lidH = eyeH * closure
        drawOval(skin, Offset(cx - eyeW / 2, cy - eyeH / 2), Size(eyeW, lidH.coerceAtLeast(1f) * 1.08f))
        if (closure > 0.35f) {
            val lashY = cy - eyeH / 2 + lidH
            drawArc(
                INK.copy(alpha = 0.85f), 10f, 160f, false,
                Offset(cx - eyeW * 0.42f, lashY - eyeH * 0.18f), Size(eyeW * 0.84f, eyeH * 0.3f),
                style = Stroke(width = eyeW * 0.07f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawMouth(
    f: Frame, a: FaceAnchors, p: Palette, open: Float, width: Float, round: Float, expression: Expression, coverOriginal: Boolean,
) {
    val cx = f.x(a.mouthX)
    val cy = f.y(a.mouthY)
    val base = a.eyeDistance * f.width * 0.62f
    val w = base * (0.55f + 0.6f * width) * (1f - 0.45f * round)
    val h = (base * 0.62f * open).coerceAtLeast(base * 0.05f)

    if (coverOriginal) {
        // Soft skin patch hides the painted mouth, so the animated one does not double up.
        drawCircle(
            Brush.radialGradient(listOf(p.skin, p.skin, p.skin.copy(alpha = 0f)), center = Offset(cx, cy), radius = base * 0.62f),
            radius = base * 0.62f, center = Offset(cx, cy),
        )
    }

    if (open < 0.06f) {
        // Closed mouth: a line, curved by expression.
        val curve = when (expression) {
            Expression.HAPPY, Expression.LOVE -> base * 0.22f
            Expression.SAD -> -base * 0.18f
            Expression.ANGRY -> -base * 0.06f
            else -> base * 0.08f
        }
        val path = Path().apply {
            moveTo(cx - w / 2, cy)
            cubicTo(cx - w / 4, cy + curve, cx + w / 4, cy + curve, cx + w / 2, cy)
        }
        drawPath(path, INK, style = Stroke(width = base * 0.07f, cap = StrokeCap.Round))
        return
    }
    drawOval(p.lip.copy(alpha = 0.9f), Offset(cx - w / 2 - base * 0.04f, cy - h / 2 - base * 0.04f), Size(w + base * 0.08f, h + base * 0.08f))
    drawOval(MOUTH_INSIDE, Offset(cx - w / 2, cy - h / 2), Size(w, h))
    if (open > 0.45f) {
        drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(cx - w * 0.32f, cy - h / 2 + h * 0.04f), Size(w * 0.64f, h * 0.18f), CornerRadius(h * 0.08f))
    }
    if (open > 0.3f) {
        drawOval(Color(0xFFE06A7A), Offset(cx - w * 0.25f, cy + h * 0.08f), Size(w * 0.5f, h * 0.34f))
    }
}

private fun DrawScope.drawExpression(
    f: Frame, a: FaceAnchors, e: Expression, phase: Float, measurer: TextMeasurer, sleeping: Boolean,
) {
    val d = a.eyeDistance * f.width
    val cheekY = f.y((a.leftEyeY + a.mouthY) / 2f)
    when (e) {
        Expression.HAPPY, Expression.LOVE -> {
            val blush = Color(0xFFFF7A90).copy(alpha = 0.35f)
            drawOval(blush, Offset(f.x(a.leftEyeX) - d * 0.35f, cheekY - d * 0.1f), Size(d * 0.45f, d * 0.22f))
            drawOval(blush, Offset(f.x(a.rightEyeX) - d * 0.1f, cheekY - d * 0.1f), Size(d * 0.45f, d * 0.22f))
            if (e == Expression.LOVE) {
                repeat(3) { i ->
                    val t = (phase + i / 3f) % 1f
                    val x = f.x(a.faceRight) + d * 0.1f + sin((t + i) * 6f) * d * 0.12f
                    val y = f.y(a.faceTop) + (1f - t) * d * 1.2f
                    drawHeart(Offset(x, y), d * (0.18f + 0.05f * i), Color(0xFFF15BB5).copy(alpha = 1f - t))
                }
            }
        }
        Expression.SAD -> {
            val t = phase
            val x = f.x(a.leftEyeX) + d * 0.05f
            val y = f.y(a.leftEyeY) + d * 0.2f + t * d * 0.6f
            drawTear(Offset(x, y), d * 0.09f, Color(0xFF5BC0EB).copy(alpha = 1f - t * 0.7f))
        }
        Expression.SURPRISED -> {
            drawText(measurer, "!", Offset(f.x(a.faceRight), f.y(a.faceTop) - d * 0.2f), TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFE4572E)))
        }
        Expression.THINKING -> {
            repeat(3) { i ->
                val alpha = if ((phase * 3).toInt() >= i) 1f else 0.25f
                drawCircle(INK.copy(alpha = alpha), d * 0.06f, Offset(f.x(a.faceRight) + d * (0.05f + 0.18f * i), f.y(a.faceTop) + d * 0.05f - d * 0.08f * i))
            }
        }
        Expression.SLEEPY -> if (!sleeping) drawZz(f, a, d, phase, measurer)
        Expression.ANGRY -> {
            val c = Color(0xFFE4572E)
            val o = Offset(f.x(a.faceRight) - d * 0.2f, f.y(a.faceTop) + d * 0.15f)
            drawLine(c, o + Offset(-d * 0.1f, -d * 0.1f), o + Offset(d * 0.1f, d * 0.1f), strokeWidth = d * 0.05f, cap = StrokeCap.Round)
            drawLine(c, o + Offset(d * 0.1f, -d * 0.1f), o + Offset(-d * 0.1f, d * 0.1f), strokeWidth = d * 0.05f, cap = StrokeCap.Round)
        }
        Expression.CONFUSED -> {
            val bob = sin(phase * 2f * PI.toFloat()) * d * 0.05f
            drawText(measurer, "?", Offset(f.x(a.faceRight) - d * 0.05f, f.y(a.faceTop) - d * 0.35f + bob), TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFF9B5DE5)))
        }
        Expression.NEUTRAL -> Unit
    }
    if (sleeping) drawZz(f, a, d, phase, measurer)
}

private fun DrawScope.drawZz(f: Frame, a: FaceAnchors, d: Float, phase: Float, measurer: TextMeasurer) {
    listOf("z", "Z").forEachIndexed { i, s ->
        val t = (phase + i * 0.5f) % 1f
        drawText(
            measurer, s,
            Offset(f.x(a.faceRight) + d * 0.1f * i + t * d * 0.2f, f.y(a.faceTop) - t * d * 0.5f),
            TextStyle(fontSize = (18 + 8 * i).sp, fontWeight = FontWeight.Black, color = Color(0xFF3F88C5).copy(alpha = 1f - t)),
        )
    }
}

private fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.35f)
        cubicTo(c.x - s * 1.1f, c.y - s * 0.3f, c.x - s * 0.45f, c.y - s * 1.0f, c.x, c.y - s * 0.4f)
        cubicTo(c.x + s * 0.45f, c.y - s * 1.0f, c.x + s * 1.1f, c.y - s * 0.3f, c.x, c.y + s * 0.35f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawTear(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - s * 1.4f)
        cubicTo(c.x + s * 1.1f, c.y, c.x + s * 0.8f, c.y + s, c.x, c.y + s)
        cubicTo(c.x - s * 0.8f, c.y + s, c.x - s * 1.1f, c.y, c.x, c.y - s * 1.4f)
        close()
    }
    drawPath(p, color)
}

// ------------------------------------------------------------------------ outfit

private fun DrawScope.drawOutfit(f: Frame, a: FaceAnchors, o: OutfitConfig) {
    val faceW = a.faceWidth * f.width
    val cx = f.x((a.faceLeft + a.faceRight) / 2f)
    val top = f.y(a.faceTop)
    val bottom = f.y(a.faceBottom)
    val d = a.eyeDistance * f.width
    val hatColor = Color(o.hatColor)
    val clothColor = Color(o.clothesColor)

    when (o.clothes) {
        Clothes.NONE -> Unit
        Clothes.SCARF -> {
            drawRoundRect(clothColor, Offset(cx - faceW * 0.45f, bottom - d * 0.18f), Size(faceW * 0.9f, d * 0.34f), CornerRadius(d * 0.17f))
            drawRoundRect(clothColor, Offset(cx + faceW * 0.12f, bottom), Size(d * 0.28f, d * 0.7f), CornerRadius(d * 0.1f))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx + faceW * 0.12f, bottom + d * 0.5f), Offset(cx + faceW * 0.12f + d * 0.28f, bottom + d * 0.5f), strokeWidth = d * 0.05f)
        }
        Clothes.BOWTIE -> {
            val y = bottom + d * 0.02f
            val w = d * 0.42f
            val left = Path().apply { moveTo(cx, y); lineTo(cx - w, y - w * 0.5f); lineTo(cx - w, y + w * 0.5f); close() }
            val right = Path().apply { moveTo(cx, y); lineTo(cx + w, y - w * 0.5f); lineTo(cx + w, y + w * 0.5f); close() }
            drawPath(left, clothColor); drawPath(right, clothColor)
            drawCircle(clothColor.darken(), w * 0.2f, Offset(cx, y))
        }
        Clothes.HOODIE -> {
            drawRoundRect(clothColor, Offset(cx - faceW * 0.62f, bottom - d * 0.05f), Size(faceW * 1.24f, (size.height - bottom + d).coerceAtLeast(d)), CornerRadius(faceW * 0.3f))
            drawLine(Color.White, Offset(cx - d * 0.25f, bottom + d * 0.05f), Offset(cx - d * 0.3f, bottom + d * 0.7f), strokeWidth = d * 0.05f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(cx + d * 0.25f, bottom + d * 0.05f), Offset(cx + d * 0.3f, bottom + d * 0.7f), strokeWidth = d * 0.05f, cap = StrokeCap.Round)
        }
        Clothes.TIE -> {
            val y = bottom
            val w = d * 0.16f
            drawRoundRect(clothColor.darken(), Offset(cx - w, y - w * 0.4f), Size(w * 2, w * 1.3f), CornerRadius(w * 0.3f))
            val body = Path().apply {
                moveTo(cx - w * 0.8f, y + w * 0.9f); lineTo(cx + w * 0.8f, y + w * 0.9f)
                lineTo(cx + w * 1.3f, y + w * 5.5f); lineTo(cx, y + w * 6.6f); lineTo(cx - w * 1.3f, y + w * 5.5f); close()
            }
            drawPath(body, clothColor)
        }
    }

    val lens = d * 0.36f
    val ly = f.y((a.leftEyeY + a.rightEyeY) / 2f)
    val lx = f.x(a.leftEyeX)
    val rx = f.x(a.rightEyeX)
    when (o.glasses) {
        Glasses.NONE -> Unit
        Glasses.ROUND -> {
            drawCircle(INK, lens, Offset(lx, ly), style = Stroke(lens * 0.16f))
            drawCircle(INK, lens, Offset(rx, ly), style = Stroke(lens * 0.16f))
            drawLine(INK, Offset(lx + lens, ly), Offset(rx - lens, ly), strokeWidth = lens * 0.14f)
        }
        Glasses.SUNGLASSES -> {
            listOf(lx, rx).forEach { x ->
                drawRoundRect(Color(0xE6111111), Offset(x - lens * 1.1f, ly - lens * 0.7f), Size(lens * 2.2f, lens * 1.4f), CornerRadius(lens * 0.5f))
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x - lens * 0.7f, ly - lens * 0.3f), Offset(x - lens * 0.3f, ly - lens * 0.5f), strokeWidth = lens * 0.12f, cap = StrokeCap.Round)
            }
            drawLine(Color(0xFF111111), Offset(lx + lens * 1.1f, ly - lens * 0.3f), Offset(rx - lens * 1.1f, ly - lens * 0.3f), strokeWidth = lens * 0.18f)
        }
        Glasses.HEART -> {
            drawHeart(Offset(lx, ly + lens * 0.2f), lens * 1.1f, Color(0xCCF15BB5))
            drawHeart(Offset(rx, ly + lens * 0.2f), lens * 1.1f, Color(0xCCF15BB5))
            drawLine(Color(0xFFF15BB5), Offset(lx + lens * 0.9f, ly - lens * 0.2f), Offset(rx - lens * 0.9f, ly - lens * 0.2f), strokeWidth = lens * 0.14f)
        }
        Glasses.MONOCLE -> {
            drawCircle(Color(0xFFD4A017), lens * 1.05f, Offset(rx, ly), style = Stroke(lens * 0.14f))
            drawLine(Color(0xFFD4A017), Offset(rx, ly + lens * 1.05f), Offset(rx + lens * 0.4f, f.y(a.mouthY) + lens * 1.5f), strokeWidth = lens * 0.06f)
        }
    }

    val hatW = faceW * 1.02f
    when (o.hat) {
        Hat.NONE -> Unit
        Hat.PARTY -> {
            val cone = Path().apply { moveTo(cx - hatW * 0.28f, top + d * 0.1f); lineTo(cx, top - hatW * 0.62f); lineTo(cx + hatW * 0.28f, top + d * 0.1f); close() }
            drawPath(cone, hatColor)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx - hatW * 0.17f, top - hatW * 0.1f), Offset(cx + hatW * 0.12f, top - hatW * 0.2f), strokeWidth = d * 0.06f)
            drawCircle(Color(0xFFFFC857), d * 0.13f, Offset(cx, top - hatW * 0.62f))
        }
        Hat.BEANIE -> {
            drawArc(hatColor, 180f, 180f, true, Offset(cx - hatW / 2, top - hatW * 0.32f), Size(hatW, hatW * 0.7f))
            drawRoundRect(hatColor.darken(), Offset(cx - hatW * 0.52f, top - d * 0.02f), Size(hatW * 1.04f, d * 0.3f), CornerRadius(d * 0.12f))
            drawCircle(Color.White, d * 0.14f, Offset(cx, top - hatW * 0.33f))
        }
        Hat.CROWN -> {
            val gold = Color(0xFFFFC857)
            val w = hatW * 0.7f
            val base = top + d * 0.05f
            val crown = Path().apply {
                moveTo(cx - w / 2, base); lineTo(cx - w / 2, base - w * 0.35f); lineTo(cx - w / 4, base - w * 0.15f)
                lineTo(cx, base - w * 0.45f); lineTo(cx + w / 4, base - w * 0.15f); lineTo(cx + w / 2, base - w * 0.35f); lineTo(cx + w / 2, base); close()
            }
            drawPath(crown, gold)
            listOf(-0.25f, 0f, 0.25f).forEach { k -> drawCircle(hatColor, d * 0.06f, Offset(cx + w * k, base - w * 0.08f)) }
        }
        Hat.TOP_HAT -> {
            val w = hatW * 0.62f
            drawRoundRect(INK, Offset(cx - hatW * 0.46f, top - d * 0.05f), Size(hatW * 0.92f, d * 0.16f), CornerRadius(d * 0.08f))
            drawRect(INK, Offset(cx - w / 2, top - w * 0.9f), Size(w, w * 0.88f))
            drawRect(hatColor, Offset(cx - w / 2, top - w * 0.28f), Size(w, w * 0.16f))
        }
        Hat.CAP -> {
            drawArc(hatColor, 180f, 180f, true, Offset(cx - hatW * 0.46f, top - hatW * 0.26f), Size(hatW * 0.92f, hatW * 0.56f))
            drawOval(hatColor.darken(), Offset(cx - hatW * 0.05f, top - d * 0.02f), Size(hatW * 0.62f, d * 0.2f))
            drawCircle(hatColor.darken(), d * 0.05f, Offset(cx, top - hatW * 0.26f))
        }
    }
}

private fun Color.darken(f: Float = 0.75f) = Color(red * f, green * f, blue * f, alpha)
