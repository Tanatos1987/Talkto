package com.talkto.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchClassifier
import com.talkto.core.touch.TouchSample

/**
 * Records each finger track on the pet (down -> moves -> up) and hands it to [TouchClassifier]:
 * slap, hit, pat, gentle touch or poke. [onDown] fires immediately so the eyes can follow the finger.
 */
fun Modifier.petTouches(onDown: (nx: Float, ny: Float) -> Unit, onTouch: (Touch) -> Unit): Modifier =
    pointerInput(Unit) {
        val classifier = TouchClassifier(size.width / density, size.height / density)
        fun sample(c: PointerInputChange) = TouchSample(c.uptimeMillis, c.position.x / density, c.position.y / density, c.pressure)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onDown(down.position.x / size.width, down.position.y / size.height)
            val track = arrayListOf(sample(down))
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                track += sample(change)
                if (!change.pressed) break
            }
            classifier.classify(track)?.let(onTouch)
        }
    }
