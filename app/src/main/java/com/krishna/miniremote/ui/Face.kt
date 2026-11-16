package com.krishna.miniremote.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The design draws the remote on a 920 x 2000 "board" and scales the whole board to the phone.
 * LocalK is that scale in dp per board pixel, so every size below is the design's own number.
 */
val LocalK = compositionLocalOf { 0.413f }

@Composable fun bdp(x: Float): Dp = (x * LocalK.current).dp
@Composable fun bsp(x: Float): TextUnit = with(LocalDensity.current) { (x * LocalK.current).dp.toSp() }

/** One CSS box-shadow. Lengths are board pixels. */
data class Sh(val dy: Float, val blur: Float, val color: Color, val spread: Float = 0f, val inset: Boolean = false)

/** A CSS background gradient plus its box-shadow list (first listed is painted on top, like CSS). */
data class Face(val colors: List<Color>, val shadows: List<Sh>, val angle: Float = 180f, val stops: List<Float>? = null)

private val B = Color.Black
private val W = Color.White

/** .ib2 / .ipill / .devpill / .ivol at rest (v16 "charcoal soft-touch"). */
val KEY_UP = Face(listOf(T.FaceTop, T.FaceBot), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1.5f), Sh(10f, 20f, B.copy(.5f)),
    Sh(1.5f, 0f, W.copy(.13f), inset = true), Sh(-3f, 4f, B.copy(.35f), inset = true)))

/** The same keys while pressed: gradient flips and the shadow sinks inside. */
val KEY_DOWN = Face(listOf(T.FaceBot, T.FaceTop), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1.5f), Sh(2f, 4f, B.copy(.5f)), Sh(3f, 8f, B.copy(.55f), inset = true)))

/** The volume rocker shell: same face, its segments draw their own pressed state. */
val ROCKER = KEY_UP

/** The clickpad: a raised ring. */
val PAD = Face(listOf(Color(0xFF3D3D40), Color(0xFF2A2A2D), Color(0xFF202023)), listOf(
    Sh(0f, 0f, B.copy(.65f), spread = 1.5f), Sh(18f, 36f, B.copy(.55f)),
    Sh(2f, 0f, W.copy(.12f), inset = true), Sh(-5f, 12f, B.copy(.4f), inset = true), Sh(14f, 28f, W.copy(.035f), inset = true)),
    angle = 160f, stops = listOf(0f, .52f, 1f))

/** Its dished centre (OK). */
val WELL = Face(listOf(Color(0xFF121214), Color(0xFF262629)), listOf(
    Sh(0f, 0f, B.copy(.75f), spread = 1.5f), Sh(1.5f, 0f, W.copy(.08f)),
    Sh(9f, 18f, B.copy(.7f), inset = true), Sh(-2f, 5f, W.copy(.05f), inset = true)))

val WELL_DOWN = Face(listOf(Color(0xFF0D0D0F), Color(0xFF202023)), listOf(
    Sh(0f, 0f, B.copy(.8f), spread = 1.5f), Sh(1f, 0f, W.copy(.06f)), Sh(12f, 22f, B.copy(.8f), inset = true)))

fun flat(c: Color, shadows: List<Sh>) = Face(listOf(c, c), shadows)

/** Rounded rect; corner radius [cr] (px), or a pill/circle when null (every shape on the remote). */
private fun stadium(w: Float, h: Float, grow: Float = 0f, dy: Float = 0f, cr: Float? = null) = android.graphics.Path().apply {
    val r = max(0f, (cr ?: (min(w, h) / 2f)) + grow)
    addRoundRect(RectF(-grow, -grow + dy, w + grow, h + grow + dy), r, r, android.graphics.Path.Direction.CW)
}

fun stadiumPath(w: Float, h: Float, cr: Float? = null) = Path().apply {
    addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(cr ?: (min(w, h) / 2f))))
}

/** CSS blur length -> Android blur radius (CSS sigma is blur/2; Android sigma ~ 0.577 r + 0.5). */
private fun paint(c: Color, blur: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = c.toArgb()
    if (blur > 0f) maskFilter = BlurMaskFilter(max(0.5f, blur * 0.866f - 0.87f), BlurMaskFilter.Blur.NORMAL)
}

/**
 * Renders a face with its shadows into a bitmap once, in software, so blur works on every
 * Android version and the per-frame cost is one bitmap draw. [u] is pixels per board pixel.
 */
private fun render(f: Face, w: Float, h: Float, u: Float, cr: Float?): Pair<ImageBitmap, Float> {
    val outer = f.shadows.filter { !it.inset }
    val pad = (outer.maxOfOrNull { (abs(it.dy) + it.blur * 1.3f + it.spread) * u } ?: 0f) + 2f
    val bmp = Bitmap.createBitmap((w + pad * 2).toInt() + 1, (h + pad * 2).toInt() + 1, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.translate(pad, pad)
    for (s in outer.asReversed()) c.drawPath(stadium(w, h, s.spread * u, s.dy * u, cr), paint(s.color, s.blur * u))

    val a = Math.toRadians(f.angle.toDouble())
    val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
    val len = abs(w * dx) + abs(h * dy)
    val cx = w / 2; val cy = h / 2
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2,
            f.colors.map { it.toArgb() }.toIntArray(), f.stops?.toFloatArray(), Shader.TileMode.CLAMP)
    }
    val body = stadium(w, h, cr = cr)
    c.drawPath(body, fill)

    c.save()
    c.clipPath(body)
    for (s in f.shadows.filter { it.inset }.asReversed()) {
        val hole = android.graphics.Path().apply { addRect(-w, -h, w * 2, h * 2, android.graphics.Path.Direction.CW) }
        hole.op(stadium(w, h, -s.spread * u, s.dy * u, cr), android.graphics.Path.Op.DIFFERENCE)
        c.drawPath(hole, paint(s.color, s.blur * u))
    }
    c.restore()
    return bmp.asImageBitmap() to pad
}

private class FaceCache { var w = 0f; var h = 0f; val m = HashMap<Face, Pair<ImageBitmap, Float>>() }

/**
 * Paints [face] (with its CSS shadows) behind the content, then the tilt sheen on top when [sheen].
 * Bitmaps are cached per face and size, so switching between rest and pressed is free after the first time.
 */
fun Modifier.softFace(face: Face, sheen: Boolean = true, corner: Dp? = null, dpUnits: Boolean = false): Modifier = composed {
    val k = LocalK.current
    val density = LocalDensity.current.density
    val tilt = LocalTilt.current
    // Remote faces use board pixels; sheet faces give their shadows in dp ([dpUnits]).
    val u = if (dpUnits) density else k * density
    val cr = corner?.let { it.value * density }
    val cache = remember(u, cr) { FaceCache() }
    drawBehind {
        if (size.width != cache.w || size.height != cache.h) { cache.m.clear(); cache.w = size.width; cache.h = size.height }
        val (img, pad) = cache.m.getOrPut(face) { render(face, size.width, size.height, u, cr) }
        drawImage(img, Offset(-pad, -pad))
        if (sheen) clipPath(stadiumPath(size.width, size.height, cr)) { sheen(tilt.value, 0.07f) }
    }
}

/* ---- Sheet faces: the remote's charcoal soft-touch language at sheet scale (lengths in dp). ---- */

/** Raised card / secondary button / round header button. */
val CARD = Face(listOf(Color(0xFF333336), Color(0xFF242427)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(4f, 10f, B.copy(.45f)),
    Sh(1f, 0f, W.copy(.09f), inset = true), Sh(-2f, 3f, B.copy(.3f), inset = true)))

val CARD_DOWN = Face(listOf(Color(0xFF222225), Color(0xFF303033)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(1f, 2f, B.copy(.45f)), Sh(2f, 6f, B.copy(.5f), inset = true)))

/** Recessed well: text fields, code boxes, switch and segmented tracks. */
val HOLLOW = Face(listOf(Color(0xFF141416), Color(0xFF1F1F22)), listOf(
    Sh(0f, 0f, B.copy(.7f), spread = 1f), Sh(1f, 0f, W.copy(.06f)),
    Sh(3f, 6f, B.copy(.6f), inset = true)))

/** Primary button: a light soft-touch key. */
val LIGHT = Face(listOf(Color(0xFFF7F7F9), Color(0xFFD6D6DA)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(4f, 10f, B.copy(.45f)),
    Sh(1f, 0f, W.copy(.9f), inset = true), Sh(-2f, 3f, B.copy(.18f), inset = true)))

val LIGHT_DOWN = Face(listOf(Color(0xFFD0D0D4), Color(0xFFEDEDF0)), listOf(
    Sh(0f, 0f, B.copy(.6f), spread = 1f), Sh(1f, 2f, B.copy(.45f)), Sh(2f, 6f, B.copy(.3f), inset = true)))

val DANGER = Face(listOf(Color(0xFF4A2626), Color(0xFF341B1B)), CARD.shadows)
val DANGER_DOWN = Face(listOf(Color(0xFF341B1B), Color(0xFF452424)), CARD_DOWN.shadows)

/** Clip helper for ripples and pressed tints so they stay inside the pill. */
fun DrawScope.insideStadium(block: DrawScope.() -> Unit) = clipPath(stadiumPath(size.width, size.height), block = block)
