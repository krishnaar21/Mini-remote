package com.krishna.miniremote.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How an app's real icon sits on a quick access tile, drawn at the tile's exact pixel size so
 * it's never stretched or cropped (the same rules as the iOS app):
 * - plain edges (Netflix, YouTube, Prime): the icon at about three quarters of the tile's short
 *   side, its own edge pixels carried out to the tile's edges, in whole pixels so no seam shows;
 * - busy or very rounded artwork (SonyLIV, Disney+): the icon whole, on a flat fill of its own
 *   border colour, its edge feathered into that fill so no border shows;
 * - a glyph on nothing (see-through): null, and the caller shows it as a plate on the tile colour.
 * On a dark theme a black icon background is lifted to the key grey so it doesn't melt away.
 */
object LogoFit {
    private class Stats(val opaque: Boolean, val busy: Boolean, val sides: Boolean, val lum: Double)

    private fun stats(b: Bitmap): Stats? = runCatching {
        val w = 64; val h = 64
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        fun lum(c: Int) = (0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) + 0.114 * android.graphics.Color.blue(c)) / 255.0
        fun alpha(c: Int) = android.graphics.Color.alpha(c) / 255.0
        val i = 3
        val corners = listOf(s.getPixel(i, i), s.getPixel(w - 1 - i, i), s.getPixel(i, h - 1 - i), s.getPixel(w - 1 - i, h - 1 - i))
        val opaque = corners.all { alpha(it) > 0.8 }
        val l = ArrayList<Double>()
        for (t in 0..15) {
            val x = i + (w - 2 * i - 1) * t / 15; val y = i + (h - 2 * i - 1) * t / 15
            listOf(s.getPixel(x, i), s.getPixel(x, h - 1 - i), s.getPixel(i, y), s.getPixel(w - 1 - i, y)).forEach { l += lum(it) }
        }
        val m = l.average()
        val sd = sqrt(l.map { (it - m) * (it - m) }.average())
        // The middle of each side: solid even when the corners are rounded off.
        val sides = listOf(s.getPixel(w / 2, i), s.getPixel(w / 2, h - 1 - i), s.getPixel(i, h / 2), s.getPixel(w - 1 - i, h / 2)).all { alpha(it) > 0.8 }
        if (s !== b) s.recycle()
        Stats(opaque, sd > 0.16, sides, m)
    }.getOrNull()

    private fun crop(b: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap? =
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > b.width || y + h > b.height) null else runCatching { Bitmap.createBitmap(b, x, y, w, h) }.getOrNull()

    /** A [pw] × [ph] canvas; with [lift], key grey underneath and drawing in Lighten mode, so black becomes grey. */
    private fun canvas(pw: Int, ph: Int, lift: Boolean): Pair<Bitmap, Paint> {
        val out = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (lift) {
            Canvas(out).drawColor(Look.p.high.toArgb())
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        }
        return out to p
    }

    /** The icon fitted to a [pw] × [ph] px tile, or null to show it as a plate. */
    fun render(img: Bitmap, pw: Int, ph: Int): Bitmap? = runCatching {
        if (pw <= 0 || ph <= 0) return null
        var src = img
        val orig = stats(img) ?: return null
        var st = orig
        var trimmed = false
        // Store artwork often comes already rounded (see-through corners, solid sides): trim to
        // just inside the rounding so its own background can carry on.
        if (!st.opaque && st.sides) {
            val inset = (min(src.width, src.height) * 0.14f).roundToInt()
            crop(src, inset, inset, src.width - 2 * inset, src.height - 2 * inset)?.let { inner ->
                val s2 = stats(inner)
                if (s2 != null && s2.opaque) { src = inner; st = s2; trimmed = true }
            }
        }
        val lift = !Look.p.light && st.lum < 0.14
        if (!st.opaque && !orig.sides) return null
        if (!st.opaque || st.busy) return ambient(img, pw, ph, lift)

        // Trim the soft, anti-aliased outer pixels.
        val t = (min(src.width, src.height) * 0.06f).roundToInt()
        val core = crop(src, t, t, src.width - 2 * t, src.height - 2 * t) ?: return null
        val sw = core.width; val sh = core.height
        if (sw <= 6 || sh <= 6) return null
        val (out, paint) = canvas(pw, ph, lift)
        val c = Canvas(out)
        // A trimmed icon has lost its outer margin: draw it a little smaller so the mark keeps its size.
        val k = min(pw, ph) * (if (trimmed) 0.58f else 0.74f) / max(sw, sh)
        // Whole pixels everywhere, so the carried-out edges meet the icon without a hairline seam.
        val iw = (sw * k).roundToInt(); val ih = (sh * k).roundToInt()
        val x0 = (pw - iw) / 2; val y0 = (ph - ih) / 2
        val x1 = x0 + iw; val y1 = y0 + ih
        // Edge colours come from a few pixels inside (the outermost can carry a tint); each band
        // tucks 2 px under the icon, which is drawn last on top.
        val e = 3; val o = 2
        val band = Paint(paint).apply { isFilterBitmap = false; isAntiAlias = false }
        fun piece(sx: Int, sy: Int, w: Int, h: Int, l: Int, tp: Int, r: Int, b: Int) {
            if (r <= l || b <= tp) return
            c.drawBitmap(core, Rect(sx, sy, sx + w, sy + h), Rect(l, tp, r, b), band)
        }
        piece(0, e, sw, 1, x0, 0, x1, y0 + o)                      // top
        piece(0, sh - 1 - e, sw, 1, x0, y1 - o, x1, ph)            // bottom
        piece(e, 0, 1, sh, 0, y0, x0 + o, y1)                      // left
        piece(sw - 1 - e, 0, 1, sh, x1 - o, y0, pw, y1)            // right
        piece(e, e, 1, 1, 0, 0, x0 + o, y0 + o)                    // corners
        piece(sw - 1 - e, e, 1, 1, x1 - o, 0, pw, y0 + o)
        piece(e, sh - 1 - e, 1, 1, 0, y1 - o, x0 + o, ph)
        piece(sw - 1 - e, sh - 1 - e, 1, 1, x1 - o, y1 - o, pw, ph)
        // The icon itself, its outer pixel trimmed again so no soft edge line is left.
        c.drawBitmap(core, Rect(1, 1, sw - 1, sh - 1), RectF(x0 + k, y0 + k, x1 - k, y1 - k), paint)
        out
    }.getOrNull()

    /**
     * Busy or very rounded artwork: the whole icon, its proportions kept, on a flat fill of its
     * own border colour; the icon's edge is feathered into that fill.
     */
    private fun ambient(icon: Bitmap, pw: Int, ph: Int, lift: Boolean): Bitmap? {
        val W = pw.toFloat(); val H = ph.toFloat(); val short = min(W, H)
        val side = min(icon.width, icon.height)
        if (side <= 0) return null
        // The background: one flat colour taken from the icon's own border, so the tile reads as
        // a solid fill like the other logos.
        val (out, paint) = canvas(pw, ph, lift)
        val c = Canvas(out)
        c.drawRect(0f, 0f, W, H, Paint(paint).apply { color = ringColor(icon) })
        // The icon at 78% of the tile's short side, centred, through a rounded mask a little inside
        // its edge, softened, so the edge melts into the fill.
        val f = short * 0.78f
        val fg = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val fc = Canvas(fg)
        val r = RectF((W - f) / 2, (H - f) / 2, (W + f) / 2, (H + f) / 2)
        val kf = f / side
        fc.drawBitmap(icon, null, RectF(W / 2 - icon.width * kf / 2, H / 2 - icon.height * kf / 2, W / 2 + icon.width * kf / 2, H / 2 + icon.height * kf / 2),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        // The mask is its own full-size layer, so everything outside it is cleared too.
        val maskBmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val inner = RectF(r).apply { inset(f * 0.03f, f * 0.03f) }
        Canvas(maskBmp).drawRoundRect(inner, f * 0.2f, f * 0.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter(max(1f, f * 0.035f * 1.6f), BlurMaskFilter.Blur.NORMAL)
        })
        fc.drawBitmap(maskBmp, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        maskBmp.recycle()
        c.drawBitmap(fg, 0f, 0f, paint)
        fg.recycle()
        return out
    }

    /** The icon's border colour: the ring just inside any rounding, its middle third by brightness. */
    private fun ringColor(b: Bitmap): Int {
        val fallback = android.graphics.Color.rgb(51, 51, 56)
        return runCatching {
            val w = 48
            val s = Bitmap.createScaledBitmap(b, w, w, true)
            val i = w / 6
            val ring = ArrayList<Int>()
            for (t in 0..23) {
                val a = i + (w - 2 * i - 1) * t / 23
                for ((x, y) in listOf(a to i, a to w - 1 - i, i to a, w - 1 - i to a)) {
                    val px = s.getPixel(x, y)
                    if (android.graphics.Color.alpha(px) > 200) ring += px
                }
            }
            if (s !== b) s.recycle()
            if (ring.isEmpty()) return fallback
            fun lum(c: Int) = 0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) + 0.114 * android.graphics.Color.blue(c)
            ring.sortBy { lum(it) }
            // Average the middle third: a steady colour, not a stray highlight or shadow.
            val mid = ring.subList(ring.size / 3, max(ring.size / 3 + 1, ring.size * 2 / 3))
            android.graphics.Color.rgb(
                mid.sumOf { android.graphics.Color.red(it) } / mid.size,
                mid.sumOf { android.graphics.Color.green(it) } / mid.size,
                mid.sumOf { android.graphics.Color.blue(it) } / mid.size,
            )
        }.getOrDefault(fallback)
    }
}
