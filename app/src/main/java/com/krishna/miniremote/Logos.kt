package com.krishna.miniremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * App logos from Simple Icons (simpleicons.org), an open-source (CC0) set of brand icons,
 * drawn with AndroidSVG. Each logo is fetched once over HTTPS, kept in the app's cache folder,
 * and drawn in the tile's text colour. Brands Simple Icons doesn't carry fall back to initials.
 * Off with Settings › App logos (then nothing is downloaded).
 */
object Logos {
    private const val HOST = "cdn.simpleicons.org"
    private val mem = LruCache<String, Bitmap>(48)
    private val misses = HashSet<String>()
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false).build()
    }

    /** Simple Icons slug for an app name ("Prime Video" -> "primevideo"). */
    fun slugFor(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** The logo as a [px]-square bitmap in colour [hex] (RRGGBB), or null if there isn't one. */
    suspend fun get(ctx: Context, slug: String, hex: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (slug.isEmpty() || !slug.all { it.isLetterOrDigit() }) return@withContext null
        val key = "$slug-$hex-$px"
        mem.get(key)?.let { return@withContext it }
        if (slug in misses) return@withContext null
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        val file = File(dir, "$slug.svg")
        val svgText = if (file.exists()) file.readText() else runCatching {
            // Fetched in white; the colour is applied when drawing.
            http.newCall(Request.Builder().url("https://$HOST/$slug/ffffff").build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.length < 100_000 && it.contains("<svg") }
            }
        }.getOrNull()?.also { file.writeText(it) }
        if (svgText == null) { synchronized(misses) { misses += slug }; return@withContext null }
        runCatching {
            val svg = SVG.getFromString(svgText.replace("#ffffff", "#$hex").replace("#FFFFFF", "#$hex").replace("fill=\"white\"", "fill=\"#$hex\""))
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp))
            bmp
        }.getOrNull()?.also { mem.put(key, it) }
    }

    /**
     * The app's own website icon (full colour, PNG) for apps Simple Icons doesn't carry, through
     * Google's public favicon service. Only the website's domain is sent. Cached like the logos.
     */
    suspend fun siteIcon(ctx: Context, domain: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (domain.isEmpty() || !domain.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return@withContext null
        val key = "site:$domain"
        mem.get(key)?.let { return@withContext it }
        if (key in misses) return@withContext null
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        val file = File(dir, "site-$domain.png")
        val bytes = if (file.exists()) file.readBytes() else runCatching {
            http.newCall(Request.Builder().url("https://www.google.com/s2/favicons?domain=$domain&sz=128").build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()?.takeIf { it.size in 200..300_000 }
            }
        }.getOrNull()?.also { file.writeBytes(it) }
        val bmp = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        // The service returns a tiny generic globe when a site has no icon: treat that as none.
        if (bmp == null || bmp.width < 48) { synchronized(misses) { misses += key }; return@withContext null }
        mem.put(key, bmp)
        bmp
    }

    /** Removes downloaded logos (with "Erase all data"). */
    fun clear(ctx: Context) { runCatching { File(ctx.cacheDir, "logos").deleteRecursively() }; mem.evictAll() }
}
