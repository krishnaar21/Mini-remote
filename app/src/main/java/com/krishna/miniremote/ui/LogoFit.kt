package com.krishna.miniremote.ui

import android.graphics.Bitmap
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
 * - busy or very rounded artwork (SonyLIV, Disney+): the art fills the tile edge to edge, its
 *   rounded corners cropped off, its sides carried outward on wider tiles with a feathered join;
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
        // Corners take the colour from the middle of the top and bottom rows: the icon's own corner
        // pixel can sit in its rounding and come out lighter (the patches Netflix showed).
        piece(sw / 2, e, 1, 1, 0, 0, x0 + o, y0 + o)
        piece(sw / 2, e, 1, 1, x1 - o, 0, pw, y0 + o)
        piece(sw / 2, sh - 1 - e, 1, 1, 0, y1 - o, x0 + o, ph)
        piece(sw / 2, sh - 1 - e, 1, 1, x1 - o, y1 - o, pw, ph)
        // The icon itself, its outer pixel trimmed again so no soft edge line is left.
        c.drawBitmap(core, Rect(1, 1, sw - 1, sh - 1), RectF(x0 + k, y0 + k, x1 - k, y1 - k), paint)
        out
    }.getOrNull()

    /**
     * Busy or very rounded artwork (SonyLIV, Disney+): the art itself fills the tile edge to edge.
     * Its rounded corners are cropped off, it's drawn as tall as the tile (a circle clips the rest),
     * and on a wider tile its left and right edges carry on outward as a smooth fade of their own
     * colours, the art's sides feathered into them. No plate, no tile inside a tile.
     */
    private fun ambient(icon: Bitmap, pw: Int, ph: Int, lift: Boolean): Bitmap? {
        // Just inside the rounding of an iOS-style icon (radius about 22%): corners come out opaque.
        val t = (min(icon.width, icon.height) * 0.08f).roundToInt()
        val src = crop(icon, t, t, icon.width - 2 * t, icon.height - 2 * t) ?: return null
        val (out, paint) = canvas(pw, ph, lift)
        val c = Canvas(out)
        // As tall as the tile: a circle (or a wider art) clips the sides; a wider tile carries them on.
        val k = ph.toFloat() / src.height
        val iw = (src.width * k).roundToInt(); val ih = ph
        val x0 = ((pw - iw) / 2f).roundToInt()
        val art = Rect(x0, 0, x0 + iw, ih)
        if (x0 > 0) {
            // Each side: its edge column squeezed to a few pixels (so it's a smooth fade, not
            // streaks), then stretched across the margin; 4 px tucked under the art.
            fun side(x: Int, l: Int, r: Int) {
                val col = crop(src, x, 0, 1, src.height) ?: return
                val soft = Bitmap.createScaledBitmap(col, 1, 10, true)
                c.drawBitmap(soft, null, RectF(l.toFloat(), 0f, r.toFloat(), ph.toFloat()), paint)
            }
            side(2, 0, x0 + 4)
            side(src.width - 3, x0 + iw - 4, pw)
            // The art, its left and right edges faded over the first and last 10% so they melt in.
            val layer = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            lc.drawBitmap(src, null, art, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val f = max(1f, iw * 0.1f)
            val fade = android.graphics.LinearGradient(art.left.toFloat(), 0f, art.right.toFloat(), 0f,
                intArrayOf(0, -0x1000000, -0x1000000, 0), floatArrayOf(0f, f / iw, 1f - f / iw, 1f), android.graphics.Shader.TileMode.CLAMP)
            lc.drawRect(art, Paint().apply { shader = fade; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            c.drawBitmap(layer, 0f, 0f, paint)
            layer.recycle()
        } else {
            c.drawBitmap(src, null, art, paint)
        }
        return out
    }
}
