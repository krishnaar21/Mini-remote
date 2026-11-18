package com.krishna.miniremote

import org.json.JSONArray
import org.json.JSONObject

enum class Brand(val label: String) {
    ROKU("Roku"),
    SAMSUNG("Samsung"),
    LG("LG webOS"),
    // Enum name kept for saved devices. Fire TV (Fire OS) doesn't speak this protocol.
    ANDROID_TV("Android / Google TV")
}

enum class RemoteKey(val word: String, val digit: Char? = null) {
    UP("Up"), DOWN("Down"), LEFT("Left"), RIGHT("Right"), OK("OK"),
    BACK("Back"), HOME("Home"), PLAY_PAUSE("Play / Pause"), POWER("Power"),
    VOL_UP("Volume +"), VOL_DOWN("Volume -"), MUTE("Mute"), VOICE("Voice"),
    REWIND("Rewind"), FAST_FORWARD("Fast forward"), CH_UP("Channel +"), CH_DOWN("Channel -"),
    GUIDE("TV Guide"), CH_LIST("Channel list"), INFO("Info"), INPUT("Input"), MENU("Menu"), AUDIO("Audio"),
    SUBTITLES("Subtitles"), SETTINGS("Settings"), RED("Red"), GREEN("Green"), YELLOW("Yellow"), BLUE("Blue"),
    N0("0", '0'), N1("1", '1'), N2("2", '2'), N3("3", '3'), N4("4", '4'),
    N5("5", '5'), N6("6", '6'), N7("7", '7'), N8("8", '8'), N9("9", '9');

    companion object {
        fun forDigit(c: Char): RemoteKey? = entries.firstOrNull { it.digit == c }
    }
}

data class Device(
    val id: String,
    val name: String,
    val host: String,
    val brand: Brand,
    val token: String? = null,
    /** SHA-256 of the TV's TLS certificate, recorded on first pairing (Samsung / LG wss). */
    val pin: String? = null,
    /** The TV's network (MAC) address, saved after connecting, for waking it (Wake-on-LAN). */
    val mac: String? = null,
    /** Bluetooth address once the TV has paired with the phone as a Bluetooth remote (Android). */
    val bt: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("host", host).put("brand", brand.name)
        .put("token", token ?: JSONObject.NULL)
        .put("pin", pin ?: JSONObject.NULL)
        .put("mac", mac ?: JSONObject.NULL)
        .put("bt", bt ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): Device? = runCatching {
            Device(
                id = o.getString("id"),
                name = o.getString("name"),
                host = o.getString("host"),
                brand = Brand.valueOf(o.getString("brand")),
                token = if (o.isNull("token")) null else o.getString("token"),
                pin = if (!o.has("pin") || o.isNull("pin")) null else o.getString("pin"),
                mac = if (!o.has("mac") || o.isNull("mac")) null else o.getString("mac"),
                bt = if (!o.has("bt") || o.isNull("bt")) null else o.getString("bt"),
            )
        }.getOrNull()

        fun listToJson(l: List<Device>): String = JSONArray().also { a -> l.forEach { a.put(it.toJson()) } }.toString()
        fun listFromJson(s: String?): List<Device> = runCatching {
            val a = JSONArray(s ?: "[]")
            (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

data class Settings(
    val haptics: Boolean = true,
    /** 0.6 Light, 1 Medium, 1.5 Strong. */
    val hapticStrength: Float = 1f,
    val sound: Boolean = true,
    /** 0.5 Soft, 1 Medium, 1.7 Loud. */
    val soundLevel: Float = 1f,
    val accentHex: String = "FF453A",
    /** 24..60, in steps of 2. */
    val scrubStepDp: Float = 36f,
    /** Button highlights follow the phone's tilt. */
    val gyro: Boolean = true,
    val keepAwake: Boolean = true,
    /** Check the latest-build release on launch and download a newer APK in the background. */
    val autoUpdate: Boolean = true,
    /** What Home opens with: "dial" or "trackpad". Swiping switches only until the app restarts. */
    val controlStyle: String = "dial",
    /** Shown on Home as "Hello, <first name>!". */
    val profileName: String = "",
    /** Optional, kept on this phone (and in backups): shown on the profile. */
    val profileEmail: String = "",
    /** The phone's own volume buttons change the TV's volume while the app is open. */
    val volumeKeys: Boolean = true,
    /** Download app logos from Simple Icons for Quick access and app details. */
    val logos: Boolean = true,
    /** Open the typing sheet by itself when the TV focuses a text box (Android TV). */
    val autoKeyboard: Boolean = true,
    /** While connected and the app is in the background, a notification with volume and play keys. */
    val notifRemote: Boolean = true,
    /** Look: "auto" (follows the phone), "dark", "light" or "navy" (see ui.Palettes). */
    val theme: String = "dark",
    /** Close TV menus opened from here (Settings, Input…) with Back after 10 s of no use. */
    val autoClose: Boolean = true,
    /** The welcome pages and the guided tour have been seen. */
    val onboarded: Boolean = false,
    val toured: Boolean = false,
    /** Keys, dial and trackpad drawn raised (light and shadow) instead of flat. */
    val depth: Boolean = false,
    /** Home layout: "strip" (Black) or "cards" (see ui.HomeLayout). */
    val layout: String = "strip",
    /** Where quick access apps live: "home" (a third page of circles) or "more" (top of the More sheet). */
    val appsIn: String = "home",
)

enum class ShortcutKind { APP, CHANNEL }

/**
 * Something on the Channels tab the user saved: a TV channel (value = its number) or an app.
 * For apps, [brand] null means [value] is a [Catalog] key (works on any TV); otherwise
 * [value] is that brand's own app id. [added] = created with "Add", not just pinned.
 */
data class Shortcut(
    val id: String,
    val kind: ShortcutKind,
    val name: String,
    val value: String,
    val brand: Brand? = null,
    val icon: String? = null,
    val pinned: Boolean = false,
    val added: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("name", name).put("value", value)
        .put("brand", brand?.name ?: JSONObject.NULL).put("icon", icon ?: JSONObject.NULL)
        .put("pinned", pinned).put("added", added)

    companion object {
        fun fromJson(o: JSONObject): Shortcut? = runCatching {
            Shortcut(
                id = o.getString("id"),
                kind = ShortcutKind.valueOf(o.getString("kind")),
                name = o.getString("name"),
                value = o.getString("value"),
                brand = if (o.isNull("brand")) null else Brand.valueOf(o.getString("brand")),
                icon = if (!o.has("icon") || o.isNull("icon")) null else o.getString("icon"),
                pinned = o.optBoolean("pinned"),
                added = o.optBoolean("added"),
            )
        }.getOrNull()

        fun listToJson(l: List<Shortcut>): String = JSONArray().also { a -> l.forEach { a.put(it.toJson()) } }.toString()
        fun listFromJson(s: String?): List<Shortcut> = runCatching {
            val a = JSONArray(s ?: "[]")
            (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

val HAPTIC_STRENGTHS = listOf(0.6f to "Light", 1f to "Medium", 1.5f to "Strong")
val SOUND_LEVELS = listOf(0.5f to "Soft", 1f to "Medium", 1.7f to "Loud")

val ACCENTS = listOf(
    "Red" to "FF453A",
    "Green" to "30D158",
    "Blue" to "0A84FF",
    "Purple" to "BF5AF2",
    "Pink" to "FF375F",
    "White" to "F5F5F7",
)

enum class Link { IDLE, CONNECTING, READY, ERROR }

/** Shown while pairing an Android / Google TV. [error] is from the previous attempt. */
data class PairPrompt(val tvName: String, val error: String?)
