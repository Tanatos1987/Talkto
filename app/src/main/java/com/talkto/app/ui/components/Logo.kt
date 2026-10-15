package com.talkto.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.talkto.app.i18n.tr
import com.talkto.app.ui.theme.Bubbles
import com.talkto.app.ui.theme.LogoColours
import kotlin.math.PI
import kotlin.math.sin

/**
 * The name in bubbly letters, each a different colour, bobbing one after another like a wave.
 * "Знайко" in Bulgarian, "ZnaiKo" in English.
 */
@Composable
fun ZnaiKoLogo(modifier: Modifier = Modifier, size: TextUnit = 28.sp) {
    val name = tr("Знайко", "ZnaiKo")
    val wave by rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "wave",
    )
    Row(modifier.clearAndSetSemantics { contentDescription = "ZnaiKo logo" }) {
        name.forEachIndexed { i, c ->
            val phase = (wave - i / name.length.toFloat()) * 2 * PI
            Text(
                c.toString(),
                style = TextStyle(
                    fontFamily = Bubbles,
                    fontSize = size,
                    color = LogoColours[i % LogoColours.size],
                    shadow = Shadow(Color(0x66000000), Offset(2f, 3f), 2f),
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = (sin(phase) * size.value * 0.12f).toFloat()
                    rotationZ = (sin(phase + 1.0) * 6).toFloat()
                },
            )
        }
    }
}
