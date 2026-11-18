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

    /** For the app's own website: follows HTTPS redirects (sites often serve icons from a CDN), never to plain HTTP. */
    private val site by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(false).build()
    }

    private fun fetchPng(c: OkHttpClient, url: String): ByteArray? = runCatching {
        c.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android) MiniRemote").build()).execute().use { r ->
            if (!r.isSuccessful || r.request.url.scheme != "https") null
            else r.body?.bytes()?.takeIf { it.size in 200..400_000 }
        }
    }.getOrNull()

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
     * The app's own icon (full colour) for apps Simple Icons doesn't carry: first the official
     * website's app icon (apple-touch-icon, 180px), then Google's public favicon service. Only
     * the website is contacted, with no personal data. Cached like the logos.
     */
    suspend fun siteIcon(ctx: Context, domain: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (domain.isEmpty() || !domain.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return@withContext null
        val key = "site:$domain"
        mem.get(key)?.let { return@withContext it }
        if (key in misses) return@withContext null
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        val file = File(dir, "site-$domain.png")
        fun decode(b: ByteArray?) = b?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        var bmp = if (file.exists()) decode(file.readBytes()) else null
        if (bmp == null) {
            val www = if (domain.count { it == '.' } == 1) "www.$domain" else domain
            val tries = listOf(
                site to "https://$www/apple-touch-icon.png",
                site to "https://$domain/apple-touch-icon.png",
                site to "https://$www/apple-touch-icon-precomposed.png",
                http to "https://www.google.com/s2/favicons?domain=$domain&sz=256",
            )
            for ((c, url) in tries) {
                val b = fetchPng(c, url) ?: continue
                val d = decode(b) ?: continue
                // Too small (or the generic globe) isn't a logo.
                if (d.width < 96) continue
                file.writeBytes(b); bmp = d; break
            }
        }
        if (bmp == null) { synchronized(misses) { misses += key }; return@withContext null }
        mem.put(key, bmp)
        bmp
    }

    /**
     * The app's real icon, the one its makers publish on Google Play, found by Android package
     * name (the Android TV id, e.g. com.netflix.ninja). Only the package name is sent to Google
     * Play; the image comes from Google's image host. Cached like the others.
     */
    /**
     * The official app icon from Apple's App Store (iTunes lookup: JSON with a 512 px artwork
     * link), for apps whose Play listing doesn't give one reliably. Keyed by the Android
     * package; the result is checked against the expected name so a wrong id can't show
     * another app's icon.
     */
    private val appStore = mapOf(
        "com.amazon.avod.thirdpartyclient" to (545519333L to "prime"),
        "in.startv.hotstar" to (934459219L to "hotstar"),
        "com.sonyliv" to (587794258L to "sony"),
        "com.graymatrix.did" to (743691886L to "zee"),
        "com.hulu.plus" to (376510438L to "hulu"),
        "com.peacocktv.peacockandroid" to (1508186374L to "peacock"),
    )

    private fun storeIcon(ctx: Context, pkg: String): Bitmap? {
        val (id, word) = appStore[pkg] ?: return null
        val file = File(File(ctx.cacheDir, "logos").apply { mkdirs() }, "ios-$id.png")
        fun decode(b: ByteArray?) = b?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (file.exists()) decode(file.readBytes())?.let { return it }
        for (country in listOf("in", "us")) {
            val json = runCatching {
                site.newCall(Request.Builder().url("https://itunes.apple.com/lookup?id=$id&country=$country").build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string()?.take(200_000)
                }
            }.getOrNull() ?: continue
            val item = runCatching { org.json.JSONObject(json).getJSONArray("results").optJSONObject(0) }.getOrNull() ?: continue
            if (word !in item.optString("trackName").lowercase()) continue
            val art = item.optString("artworkUrl512").takeIf { it.startsWith("https://") && "mzstatic.com" in it } ?: continue
            val url = art.substringBeforeLast('/') + "/256x256bb.png"
            val b = fetchPng(site, url) ?: fetchPng(site, art) ?: continue
            val d = decode(b) ?: continue
            file.writeBytes(b)
            return d
        }
        return null
    }

    suspend fun playIcon(ctx: Context, pkg: String): Bitmap? = withContext(Dispatchers.IO) {
        if (pkg.isEmpty() || !pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' } || '.' !in pkg) return@withContext null
        // Apps known to need it: the App Store icon first.
        if (pkg in appStore) {
            mem.get("ios:$pkg")?.let { return@withContext it }
            storeIcon(ctx, pkg)?.let { mem.put("ios:$pkg", it); return@withContext it }
        }
        val key = "play:$pkg"
        mem.get(key)?.let { return@withContext it }
        if (key in misses) return@withContext null
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        val file = File(dir, "play-$pkg.png")
        fun decode(b: ByteArray?) = b?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        var bmp = if (file.exists()) decode(file.readBytes()) else null
        if (bmp == null) {
            val html = runCatching {
                site.newCall(Request.Builder().url("https://play.google.com/store/apps/details?id=$pkg&hl=en")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36").build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string()?.take(600_000)
                }
            }.getOrNull()
            // The listing's og:image is the app icon on play-lh.googleusercontent.com; ask for 256 px.
            val img = html?.let {
                (Regex("<meta[^>]*og:image\"[^>]*content=\"(https://play-lh\\.googleusercontent\\.com/[^\"]+)\"").find(it)
                    ?: Regex("<meta[^>]*content=\"(https://play-lh\\.googleusercontent\\.com/[^\"]+)\"[^>]*og:image").find(it))?.groupValues?.get(1)
                    ?.takeIf { !it.contains(PLAY_LOGO) }
                    ?: Regex("(https://play-lh\\.googleusercontent\\.com/[A-Za-z0-9_\\-]+)=[^\"]*\"[^>]*alt=\"Icon image\"").find(it)?.groupValues?.get(1)?.takeIf { !it.contains(PLAY_LOGO) }
            }
                ?.substringBefore('=')?.let { "$it=s256" }
            val b = img?.let { fetchPng(site, it) }
            val d = decode(b)
            if (d != null && d.width >= 96) { file.writeBytes(b!!); bmp = d }
        }
        if (bmp == null) { synchronized(misses) { misses += key }; return@withContext null }
        mem.put(key, bmp)
        bmp
    }

    /** The Google Play logo's image id: what a blocked or generic page shows instead of the app's icon. */
    private const val PLAY_LOGO = "OSPwLdQHHx16DNvMT8GxkBvpzj5x9WG9"

    /** Removes downloaded logos (with "Erase all data"). */
    fun clear(ctx: Context) { runCatching { File(ctx.cacheDir, "logos").deleteRecursively() }; mem.evictAll() }
}
