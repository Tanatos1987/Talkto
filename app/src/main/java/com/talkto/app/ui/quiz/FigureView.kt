package com.talkto.app.ui.quiz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkto.core.quiz.Figure
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val FILLS = listOf(Color(0xFF7BD389), Color(0xFFFFC857), Color(0xFF3F88C5), Color(0xFFF15BB5), Color(0xFF9B5DE5))
private val LINE = Color(0xFF2D2A32)
private val UNKNOWN = Color(0xFFE4572E)

/** The shape of a geometry task in bright colours, with its sides or angles written on it; the one to find in red. */
@Composable
fun FigureView(figure: Figure, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val fill = FILLS[figure.shape.ordinal % FILLS.size].copy(alpha = 0.55f)
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        fun label(text: String, at: Offset) {
            if (text.isEmpty()) return
            val style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black, color = if ("?" in text) UNKNOWN else LINE)
            val m = measurer.measure(text, style)
            drawText(m, topLeft = Offset(at.x - m.size.width / 2f, at.y - m.size.height / 2f))
        }
        val l = figure.labels
        val stroke = Stroke(width = 5f)
        val cx = size.width / 2; val cy = size.height / 2
        val u = min(size.width, size.height)
        when (figure.shape) {
            Figure.Shape.RECT, Figure.Shape.SQUARE -> {
                val w = if (figure.shape == Figure.Shape.SQUARE) u * 0.62f else u * 1.05f
                val h = u * 0.62f
                val tl = Offset(cx - w / 2, cy - h / 2)
                drawRect(fill, tl, Size(w, h)); drawRect(LINE, tl, Size(w, h), style = stroke)
                label(l.getOrElse(0) { "" }, Offset(cx, tl.y + h + 16.dp.toPx() * 0.9f))
                label(l.getOrElse(1) { l.getOrElse(0) { "" } }, Offset(tl.x + w + 22.dp.toPx(), cy))
            }
            Figure.Shape.TRIANGLE, Figure.Shape.ANGLES -> {
                val a = Offset(cx - u * 0.55f, cy + u * 0.34f); val b = Offset(cx + u * 0.55f, cy + u * 0.34f); val c = Offset(cx + u * 0.08f, cy - u * 0.38f)
                polygon(listOf(a, b, c), fill, stroke)
                if (figure.shape == Figure.Shape.ANGLES) {
                    label(l.getOrElse(0) { "" }, a + Offset(34f, -18f)); label(l.getOrElse(1) { "" }, b + Offset(-38f, -18f)); label(l.getOrElse(2) { "" }, c + Offset(0f, 30f))
                } else {
                    label(l.getOrElse(0) { "" }, Offset(cx, a.y + 22.dp.toPx() * 0.8f))
                    val second = l.getOrElse(1) { "" }
                    if (second.startsWith("h")) {
                        drawLine(LINE, c, Offset(c.x, a.y), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                        label(second, Offset(c.x + 44f, cy + 10f))
                    } else {
                        label(second, (b + c) / 2f + Offset(28f, -6f)); label(l.getOrElse(2) { "" }, (a + c) / 2f + Offset(-28f, -6f))
                    }
                }
            }
            Figure.Shape.RIGHT -> {
                val a = Offset(cx - u * 0.45f, cy + u * 0.36f); val b = Offset(cx + u * 0.55f, cy + u * 0.36f); val c = Offset(cx - u * 0.45f, cy - u * 0.40f)
                polygon(listOf(a, b, c), fill, stroke)
                drawRect(LINE, Offset(a.x, a.y - 18f), Size(18f, 18f), style = Stroke(3f))
                label(l.getOrElse(0) { "" }, Offset(a.x - 26f, (a.y + c.y) / 2))
                label(l.getOrElse(1) { "" }, Offset((a.x + b.x) / 2, a.y + 26f))
                label(l.getOrElse(2) { "" }, (b + c) / 2f + Offset(26f, -20f))
            }
            Figure.Shape.CIRCLE -> {
                val r = u * 0.42f
                drawCircle(fill, r, Offset(cx, cy)); drawCircle(LINE, r, Offset(cx, cy), style = stroke)
                drawLine(LINE, Offset(cx, cy), Offset(cx + r, cy), 4f); drawCircle(LINE, 6f, Offset(cx, cy))
                label(l.getOrElse(0) { "" }, Offset(cx + r / 2, cy - 18f))
            }
            Figure.Shape.CUBE, Figure.Shape.BOX -> {
                val w = if (figure.shape == Figure.Shape.CUBE) u * 0.5f else u * 0.8f
                val h = u * 0.5f; val d = u * 0.22f
                val f = Offset(cx - w / 2 - d / 2, cy - h / 2 + d / 2)
                val front = listOf(f, f + Offset(w, 0f), f + Offset(w, h), f + Offset(0f, h))
                val back = front.map { it + Offset(d, -d) }
                polygon(listOf(front[0], front[1], back[1], back[0]), fill.copy(alpha = 0.35f), stroke)
                polygon(listOf(front[1], back[1], back[2], front[2]), fill.copy(alpha = 0.8f), stroke)
                polygon(front, fill, stroke)
                label(l.getOrElse(0) { "" }, Offset(f.x + w / 2, f.y + h + 22f))
                if (figure.shape == Figure.Shape.BOX) {
                    label(l.getOrElse(1) { "" }, (front[1] + back[1]) / 2f + Offset(26f, 14f))
                    label(l.getOrElse(2) { "" }, Offset(back[2].x + 24f, (back[1].y + back[2].y) / 2))
                }
            }
            Figure.Shape.POLYGON -> {
                val n = figure.sides.coerceAtLeast(3); val r = u * 0.42f
                polygon((0 until n).map { i -> val t = -PI / 2 + 2 * PI * i / n; Offset(cx + r * cos(t).toFloat(), cy + r * sin(t).toFloat()) }, fill, stroke)
            }
            Figure.Shape.STRAIGHT -> {
                val o = Offset(cx, cy + u * 0.3f)
                drawLine(LINE, Offset(cx - u * 0.9f, o.y), Offset(cx + u * 0.9f, o.y), 5f)
                val deg = l.getOrElse(0) { "" }.filter(Char::isDigit).toIntOrNull() ?: 60
                val t = PI - deg * PI / 180
                drawLine(LINE, o, Offset(o.x + u * 0.7f * cos(t).toFloat(), o.y - u * 0.7f * sin(t).toFloat()), 5f)
                drawCircle(fill, 16f, o)
                label(l.getOrElse(0) { "" }, Offset(o.x - 70f, o.y - 26f)); label(l.getOrElse(1) { "" }, Offset(o.x + 70f, o.y - 26f))
            }
        }
    }
}

private fun DrawScope.polygon(points: List<Offset>, fill: Color, stroke: Stroke) {
    val p = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(p, fill); drawPath(p, LINE, style = stroke)
}
