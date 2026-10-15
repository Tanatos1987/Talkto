package com.talkto.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.talkto.core.touch.BodyLocator
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchClassifier
import com.talkto.core.touch.TouchKind
import com.talkto.core.touch.TouchSample

/**
 * Records each finger track on the pet (down -> moves -> up) and hands it to [TouchClassifier]:
 * slap, twirl, hit, pat, gentle touch or poke, plus the body part under the finger (from [body]).
 * [onDown] fires immediately so the eyes can follow the finger. A sideways drag turns the pet live through
 * [onTwirl] (degrees since the last event), and [onTwirlEnd] passes the release speed so it keeps spinning.
 */
fun Modifier.petTouches(
    body: BodyLocator,
    onDown: (nx: Float, ny: Float) -> Unit,
    onTouch: (Touch) -> Unit,
    onTwirl: (deltaDeg: Float) -> Unit = {},
    onTwirlEnd: (degPerSecond: Float) -> Unit = {},
): Modifier =
    pointerInput(body) {
        val classifier = TouchClassifier(size.width / density, size.height / density, body = { body.box })
        fun sample(c: PointerInputChange) = TouchSample(c.uptimeMillis, c.position.x / density, c.position.y / density, c.pressure)
        // One stage width of drag turns the pet all the way round.
        fun degrees(px: Float) = px / size.width.coerceAtLeast(1) * 360f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onDown(down.position.x / size.width, down.position.y / size.height)
            val track = arrayListOf(sample(down))
            var twirling = false
            var lastX = down.position.x
            var velocity = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                track += sample(change)
                val dxDp = (change.position.x - down.position.x) / density
                val dyDp = (change.position.y - down.position.y) / density
                if (!twirling && classifier.isTwirl(dxDp, dyDp)) twirling = true
                if (twirling && change.pressed) {
                    val step = degrees(change.position.x - lastX)
                    val dt = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1) / 1000f
                    velocity = velocity * 0.6f + (step / dt) * 0.4f
                    if (step != 0f) onTwirl(step)
                    change.consume()
                }
                lastX = change.position.x
                if (!change.pressed) break
            }
            val touch = classifier.classify(track)
            if (twirling) onTwirlEnd(if (touch?.kind == TouchKind.SLAP) 0f else velocity)
            touch?.let(onTouch)
        }
    }
