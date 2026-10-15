package com.talkto.app.apps

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Closes apps the way a person does: open Recents, find the app's card, swipe it up.
 * This works without root on every launcher that exposes task cards to accessibility
 * (Pixel Launcher, One UI, MIUI, stock AOSP; tested labels are content descriptions or titles).
 * ZnaiKo does not read window content for any other purpose.
 */
class TalktoAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Returns true if a matching card was found and swiped away. Always returns to the home screen. */
    suspend fun swipeAwayFromRecents(label: String, packageName: String): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) return false
        delay(RECENTS_SETTLE_MS)
        try {
            repeat(MAX_PAGES) {
                val card = findCard(label, packageName)
                if (card != null) {
                    val r = Rect().also(card::getBoundsInScreen)
                    val swiped = swipe(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), 0f, 220)
                    delay(450)
                    return swiped
                }
                // Next page of cards: most launchers scroll horizontally, some vertically.
                val dm = resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()
                swipe(w * 0.2f, h * 0.5f, w * 0.85f, h * 0.5f, 250)
                delay(PAGE_SETTLE_MS)
            }
            return false
        } finally {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun findCard(label: String, packageName: String): AccessibilityNodeInfo? {
        val roots = windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow)
        for (root in roots) {
            val byText = root.findAccessibilityNodeInfosByText(label).orEmpty()
            val candidate = byText.firstOrNull { it.isVisibleToUser } ?: continue
            // Climb to the card container, so the swipe starts on the card and not on the tiny title.
            var node: AccessibilityNodeInfo? = candidate
            var best = candidate
            repeat(4) {
                node = node?.parent ?: return@repeat
                val r = Rect().also { node!!.getBoundsInScreen(it) }
                if (r.height() > resources.displayMetrics.heightPixels * 0.25f) {
                    best = node!!; return best
                }
            }
            return best
        }
        return null
    }

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }

    companion object {
        @Volatile var instance: TalktoAccessibilityService? = null
            private set

        private const val RECENTS_SETTLE_MS = 700L
        private const val PAGE_SETTLE_MS = 450L
        private const val MAX_PAGES = 6

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, TalktoAccessibilityService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
