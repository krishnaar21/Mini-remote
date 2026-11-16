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

enum class RemoteKey(val word: String) {
    UP("Up"), DOWN("Down"), LEFT("Left"), RIGHT("Right"), OK("OK"),
    BACK("Back"), HOME("Home"), PLAY_PAUSE("Play / Pause"), POWER("Power"),
    VOL_UP("Volume +"), VOL_DOWN("Volume -"), MUTE("Mute"), VOICE("Voice")
}

data class Device(
    val id: String,
    val name: String,
    val host: String,
    val brand: Brand,
    val token: String? = null,
    /** SHA-256 of the TV's TLS certificate, recorded on first pairing (Samsung / LG wss). */
    val pin: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("host", host).put("brand", brand.name)
        .put("token", token ?: JSONObject.NULL)
        .put("pin", pin ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): Device? = runCatching {
            Device(
                id = o.getString("id"),
                name = o.getString("name"),
                host = o.getString("host"),
                brand = Brand.valueOf(o.getString("brand")),
                token = if (o.isNull("token")) null else o.getString("token"),
                pin = if (!o.has("pin") || o.isNull("pin")) null else o.getString("pin"),
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
)

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
