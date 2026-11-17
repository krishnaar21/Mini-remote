package com.krishna.miniremote.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import com.krishna.miniremote.RemoteKey
import kotlin.math.abs
import kotlin.math.sign

/**
 * Two fingers on the dial or trackpad: slide up / down = volume, sideways = channel.
 * One step per [stepPx] of travel of the fingers' midpoint. One finger keeps navigating.
 */
internal class TwoFinger(private val stepPx: Float, private val emit: (RemoteKey) -> Unit) {
    var active = false
        private set
    private var acc = Offset.Zero
    private var last: Offset? = null
    private var count = 0

    /** Feeds one event. True when it belongs to a two-finger gesture (and was consumed). */
    fun feed(ev: PointerEvent): Boolean {
        val pressed = ev.changes.filter { it.pressed }
        if (!active && pressed.size < 2) return false
        active = true
        // A finger landing or lifting moves the midpoint: start measuring again from there.
        if (pressed.size != count) { count = pressed.size; last = null }
        if (pressed.isNotEmpty()) {
            val c = pressed.fold(Offset.Zero) { a, ch -> a + ch.position } / pressed.size.toFloat()
            last?.let { if (pressed.size >= 2) acc += c - it }
            last = c
            step()
        }
        ev.changes.forEach { it.consume() }
        return true
    }

    private fun step() {
        if (abs(acc.y) >= stepPx && abs(acc.y) >= abs(acc.x)) {
            emit(if (acc.y < 0) RemoteKey.VOL_UP else RemoteKey.VOL_DOWN)
            acc = Offset(acc.x * 0.5f, acc.y - sign(acc.y) * stepPx)
        } else if (abs(acc.x) >= stepPx * 1.6f) {
            // Channels need a longer slide, so a volume swipe doesn't change the channel by accident.
            emit(if (acc.x > 0) RemoteKey.CH_UP else RemoteKey.CH_DOWN)
            acc = Offset(acc.x - sign(acc.x) * stepPx * 1.6f, acc.y * 0.5f)
        }
    }
}

/** What a two-finger slide is doing, shown in place of the arrows: big arrow + "Volume +" etc. */
@androidx.compose.runtime.Composable
internal fun TwoFingerHint(key: RemoteKey?, accent: androidx.compose.ui.graphics.Color, icon: androidx.compose.ui.unit.Dp, text: androidx.compose.ui.unit.TextUnit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        if (key == null) {
            // Fingers down, no step yet: show both directions.
            androidx.compose.material3.Text("↕ Volume   ↔ Channel", color = T.Ink, fontSize = text)
        } else {
            val (ico, word) = when (key) {
                RemoteKey.VOL_UP -> Ico.UP to "Volume +"
                RemoteKey.VOL_DOWN -> Ico.DOWN to "Volume −"
                RemoteKey.CH_UP -> Ico.RIGHT to "Channel +"
                else -> Ico.LEFT to "Channel −"
            }
            LineIcon(ico, accent, icon, stroke = 2.4f)
            androidx.compose.material3.Text(word, color = T.Ink, fontSize = text,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
    }
}

/**
 * Waits for the finger to lift, consuming its events so nothing behind (like the page pager)
 * takes the gesture. True if it was a tap: it lifted without moving more than [slop].
 */
internal suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.tappedOnly(
    id: androidx.compose.ui.input.pointer.PointerId, slop: Float,
): Boolean {
    var start: Offset? = null
    var moved = false
    while (true) {
        val ev = awaitPointerEvent()
        val ch = ev.changes.firstOrNull { it.id == id } ?: return false
        if (start == null) start = ch.previousPosition
        if ((ch.position - start).getDistance() > slop) moved = true
        ev.changes.forEach { it.consume() }
        if (!ch.pressed) return !moved && ev.changes.count { it.pressed } == 0
    }
}
