package com.krishna.miniremote.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** The gestures, each with a small looping demo. */
private enum class Demo { SWIPE, TAP, DOUBLE, HOLD, TWO_UP, TWO_SIDE, TWO_DOUBLE, RING }

private data class Move(val demo: Demo, val title: String, val text: String)

private val ONE = listOf(
    Move(Demo.SWIPE, "Move", "Swipe one finger. Hold at an edge to keep going."),
    Move(Demo.TAP, "Select", "Tap the middle of the dial, or anywhere on the trackpad."),
    Move(Demo.DOUBLE, "Mute", "Double-tap OK."),
    Move(Demo.HOLD, "Voice", "Hold OK, then speak to the TV."),
)
private val TWO = listOf(
    Move(Demo.TWO_UP, "Volume", "Slide two fingers up or down."),
    Move(Demo.TWO_SIDE, "Channel", "Slide two fingers left or right."),
    Move(Demo.TWO_DOUBLE, "Switch pad", "Double-tap with two fingers: dial ⇄ trackpad."),
)
private val RING = listOf(Move(Demo.RING, "Volume ring", "Black layout: turn a finger around the ring. Clockwise is louder."))

/** Gestures sheet: one finger, two fingers, and the ring. */
@androidx.compose.runtime.Composable
internal fun GesturesPage(accent: Color) {
    SectionTitle("One finger")
    Group { ONE.forEachIndexed { i, m -> GestureRow(m, accent, last = i == ONE.lastIndex) } }
    SectionTitle("Two fingers")
    Group { TWO.forEachIndexed { i, m -> GestureRow(m, accent, last = i == TWO.lastIndex) } }
    SectionTitle("Ring")
    Group { RING.forEach { GestureRow(it, accent, last = true) } }
    Hint("Gestures work the same on the dial and the trackpad.", top = 10)
}

@Composable
private fun GestureRow(m: Move, accent: Color, last: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp).semantics(mergeDescendants = true) { contentDescription = "${m.title}. ${m.text}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GestureDemo(m.demo, accent, Modifier.size(84.dp).clearAndSetSemantics {})
        Column(Modifier.weight(1f)) {
            Text(m.title, color = T.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(m.text, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
    if (!last) Divider()
}

@Composable
private fun GestureDemo(d: Demo, accent: Color, modifier: Modifier) {
    val loop = rememberInfiniteTransition(label = "demo")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(if (d == Demo.HOLD || d == Demo.RING) 2200 else 1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier) {
        val w = size.width
        val c = center
        // The pad: a round dial for the ring, a soft square otherwise.
        if (d == Demo.RING) {
            drawCircle(M.High, w * 0.40f, c)
            drawCircle(M.Highest, w * 0.47f, c, style = Stroke(2.dp.toPx()))
        } else drawRoundRect(M.High, size = size, cornerRadius = CornerRadius(w * 0.22f))
        fun span(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
        fun ease(x: Float) = x * x * (3 - 2 * x)
        val r = w * 0.085f
        when (d) {
            Demo.SWIPE -> {
                val p = ease(span(0.15f, 0.65f))
                val x = w * (0.28f + 0.44f * p)
                val a = fadeInOut(t, 0.08f, 0.8f)
                if (p > 0f) drawLine(accent.copy(alpha = 0.45f * a), Offset(w * 0.28f, c.y), Offset(x, c.y), r * 1.2f, StrokeCap.Round)
                chevronRight(Offset(w * 0.86f, c.y), w * 0.07f, accent.copy(alpha = if (p > 0.9f) 1f else 0.35f))
                finger(Offset(x, c.y), r, a)
            }
            Demo.TAP -> { finger(c, r, fadeInOut(t, 0.1f, 0.55f)); ripple(c, w, span(0.2f, 0.6f), accent) }
            Demo.DOUBLE -> {
                val on = t in 0.1f..0.25f || t in 0.33f..0.48f
                finger(c, r, if (on) 1f else 0.25f)
                ripple(c, w, span(0.12f, 0.4f), accent); ripple(c, w, span(0.35f, 0.65f), accent)
            }
            Demo.HOLD -> {
                val a = fadeInOut(t, 0.06f, 0.85f)
                finger(c, r, a)
                val p = span(0.12f, 0.7f)
                if (p > 0f) drawArc(accent.copy(alpha = a), -90f, 360f * p, false, Offset(c.x - w * 0.24f, c.y - w * 0.24f), Size(w * 0.48f, w * 0.48f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                if (p >= 1f) drawCircle(accent.copy(alpha = 0.25f * a), w * 0.24f, c)
            }
            Demo.TWO_UP -> {
                val p = ease(span(0.15f, 0.65f)); val a = fadeInOut(t, 0.08f, 0.8f)
                val y = w * (0.70f - 0.40f * p)
                listOf(0.38f, 0.62f).forEach { fx ->
                    if (p > 0f) drawLine(accent.copy(alpha = 0.4f * a), Offset(w * fx, w * 0.70f), Offset(w * fx, y), r * 1.2f, StrokeCap.Round)
                    finger(Offset(w * fx, y), r, a)
                }
                plusMinus(w, accent, vertical = true)
            }
            Demo.TWO_SIDE -> {
                val p = ease(span(0.15f, 0.65f)); val a = fadeInOut(t, 0.08f, 0.8f)
                val x = w * (0.30f + 0.40f * p)
                listOf(0.38f, 0.62f).forEach { fy ->
                    if (p > 0f) drawLine(accent.copy(alpha = 0.4f * a), Offset(w * 0.30f, w * fy), Offset(x, w * fy), r * 1.2f, StrokeCap.Round)
                    finger(Offset(x, w * fy), r, a)
                }
                plusMinus(w, accent, vertical = false)
            }
            Demo.TWO_DOUBLE -> {
                val on = t in 0.1f..0.25f || t in 0.33f..0.48f
                listOf(0.38f, 0.62f).forEach { fx -> finger(Offset(w * fx, c.y), r, if (on) 1f else 0.25f) }
                ripple(c, w * 1.2f, span(0.35f, 0.7f), accent)
            }
            Demo.RING -> {
                val p = ease(span(0.1f, 0.75f)); val a = fadeInOut(t, 0.05f, 0.85f)
                val rr = w * 0.47f
                val ang = -90f + 140f * p
                if (p > 0f) drawArc(accent.copy(alpha = a), -90f, 140f * p, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                val rad = Math.toRadians(ang.toDouble())
                finger(Offset(c.x + rr * cos(rad).toFloat(), c.y + rr * sin(rad).toFloat()), r, a)
            }
        }
    }
}

private fun fadeInOut(t: Float, inEnd: Float, outStart: Float) = when {
    t < inEnd -> t / inEnd
    t > outStart -> ((1f - t) / (1f - outStart)).coerceIn(0f, 1f)
    else -> 1f
}

private fun DrawScope.finger(p: Offset, r: Float, a: Float) {
    if (a <= 0f) return
    drawCircle(T.Ink.copy(alpha = 0.18f * a), r * 1.9f, p)
    drawCircle(T.Ink.copy(alpha = 0.9f * a), r, p)
}

private fun DrawScope.ripple(c: Offset, w: Float, p: Float, accent: Color) {
    if (p <= 0f || p >= 1f) return
    drawCircle(accent.copy(alpha = 0.6f * (1f - p)), w * (0.1f + 0.3f * p), c, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.chevronRight(at: Offset, s: Float, col: Color) {
    drawLine(col, Offset(at.x - s / 2, at.y - s), Offset(at.x + s / 2, at.y), 2.dp.toPx(), StrokeCap.Round)
    drawLine(col, Offset(at.x + s / 2, at.y), Offset(at.x - s / 2, at.y + s), 2.dp.toPx(), StrokeCap.Round)
}

/** Small + and − marks at the ends of a two-finger slide. */
private fun DrawScope.plusMinus(w: Float, accent: Color, vertical: Boolean) {
    val s = w * 0.05f
    val sw = 2.dp.toPx()
    val plus = if (vertical) Offset(w * 0.5f, w * 0.13f) else Offset(w * 0.87f, w * 0.5f)
    val minus = if (vertical) Offset(w * 0.5f, w * 0.87f) else Offset(w * 0.13f, w * 0.5f)
    drawLine(accent, Offset(plus.x - s, plus.y), Offset(plus.x + s, plus.y), sw, StrokeCap.Round)
    drawLine(accent, Offset(plus.x, plus.y - s), Offset(plus.x, plus.y + s), sw, StrokeCap.Round)
    drawLine(T.Dim, Offset(minus.x - s, minus.y), Offset(minus.x + s, minus.y), sw, StrokeCap.Round)
}
