package com.krishna.miniremote

/**
 * Popular streaming apps and their ids on each kind of TV. Ids differ per platform, and a
 * TV only shows the ones it has an id for. Apps that aren't listed here still appear under
 * "On your TV" when the TV can list its apps (Roku, Samsung, LG), or can be added by id.
 *
 * Android / Google TV ids are package names, launched with market://launch?id=…
 */
data class CatalogApp(
    val key: String, val name: String, val bg: Long, val ink: Long, val ids: Map<Brand, String>,
    /** Who publishes it, for the app's details sheet. */
    val vendor: String = "",
    /** Android / Google TV app link, tried before the package name (some TVs ignore market:// launches). */
    val link: String? = null,
    /** Simple Icons slug for its logo (see [Logos]). */
    val logo: String = "",
)

object Catalog {
    private val R = Brand.ROKU
    private val S = Brand.SAMSUNG
    private val L = Brand.LG
    private val A = Brand.ANDROID_TV

    val apps: List<CatalogApp> = listOf(
        CatalogApp("netflix", "Netflix", 0xFF0B0B0B, 0xFFE50914,
            mapOf(R to "12", S to "3201907018807", L to "netflix", A to "com.netflix.ninja"), vendor = "Netflix, Inc.", link = "https://www.netflix.com/title", logo = "netflix"),
        CatalogApp("youtube", "YouTube", 0xFFFF0000, 0xFFFFFFFF,
            mapOf(R to "837", S to "111299001912", L to "youtube.leanback.v4", A to "com.google.android.youtube.tv"), vendor = "Google LLC", link = "https://www.youtube.com", logo = "youtube"),
        CatalogApp("prime", "Prime Video", 0xFF0F79AF, 0xFFFFFFFF,
            mapOf(R to "13", S to "3201910019365", L to "amazon", A to "com.amazon.amazonvideo.livingroom"), vendor = "Amazon", link = "https://app.primevideo.com", logo = "primevideo"),
        CatalogApp("hotstar", "JioHotstar", 0xFF0C1B4D, 0xFFFFFFFF,
            mapOf(A to "in.startv.hotstar.dplus.tv"), vendor = "JioStar", logo = "hotstar"),
        CatalogApp("disney", "Disney+", 0xFF0E2A5C, 0xFFFFFFFF,
            mapOf(R to "291097", S to "3201901017640", L to "com.disney.disneyplus-prod", A to "com.disney.disneyplus"), vendor = "Disney", link = "https://www.disneyplus.com", logo = "disneyplus"),
        CatalogApp("sonyliv", "SonyLIV", 0xFF111111, 0xFFF5F5F5,
            mapOf(A to "com.sonyliv"), vendor = "Sony Pictures Networks India", logo = "sonyliv"),
        CatalogApp("zee5", "ZEE5", 0xFF6B21A8, 0xFFFFFFFF,
            mapOf(A to "com.graymatrix.did"), vendor = "Zee Entertainment", logo = "zee5"),
        CatalogApp("spotify", "Spotify", 0xFF1DB954, 0xFF0B0B0B,
            mapOf(R to "22297", S to "3201606009684", L to "spotify-beehive", A to "com.spotify.tv.android"), vendor = "Spotify AB", logo = "spotify"),
        CatalogApp("appletv", "Apple TV", 0xFF3A3A3D, 0xFFFFFFFF,
            mapOf(R to "551012", S to "3201807016597", L to "com.apple.appletv", A to "com.apple.atve.androidtv.appletv"), vendor = "Apple Inc.", logo = "appletv"),
        CatalogApp("ytmusic", "YouTube Music", 0xFF3A3A3D, 0xFFFF4E45,
            mapOf(A to "com.google.android.youtube.tvmusic"), vendor = "Google LLC", logo = "youtubemusic"),
        CatalogApp("plex", "Plex", 0xFF1F1F1F, 0xFFE5A00D,
            mapOf(R to "13535", S to "3201512006963", A to "com.plexapp.android"), vendor = "Plex, Inc.", logo = "plex"),
        CatalogApp("hulu", "Hulu", 0xFF1CE783, 0xFF0B0B0B,
            mapOf(R to "2285", A to "com.hulu.livingroomplus"), vendor = "Hulu, LLC", logo = "hulu"),
        CatalogApp("max", "Max", 0xFF002BE7, 0xFFFFFFFF,
            mapOf(R to "61322", A to "com.wbd.stream"), vendor = "Warner Bros. Discovery", logo = "max"),
        CatalogApp("peacock", "Peacock", 0xFF0B0B0B, 0xFFFFFFFF,
            mapOf(R to "593099", A to "com.peacocktv.peacockandroid"), vendor = "NBCUniversal", logo = "peacock"),
        CatalogApp("paramount", "Paramount+", 0xFF0064FF, 0xFFFFFFFF,
            mapOf(R to "31440", A to "com.cbs.ott"), vendor = "Paramount", logo = "paramountplus"),
        CatalogApp("tubi", "Tubi", 0xFF7408FF, 0xFFFFF200,
            mapOf(R to "41468", A to "com.tubitv"), vendor = "Tubi, Inc.", logo = "tubi"),
        CatalogApp("pluto", "Pluto TV", 0xFF2B2B2B, 0xFFFFF200,
            mapOf(R to "74519", A to "tv.pluto.android"), vendor = "Paramount", logo = "plutotv"),
        CatalogApp("rokuchannel", "The Roku Channel", 0xFF6C3C97, 0xFFFFFFFF,
            mapOf(R to "151908"), vendor = "Roku, Inc.", logo = "roku"),
    )

    fun byKey(key: String): CatalogApp? = apps.firstOrNull { it.key == key }
}
