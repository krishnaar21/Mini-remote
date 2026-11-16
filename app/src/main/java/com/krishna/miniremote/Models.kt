package com.krishna.miniremote

import org.json.JSONArray
import org.json.JSONObject

enum class Brand(val label: String) {
    ROKU("Roku"),
    SAMSUNG("Samsung"),
    LG("LG webOS"),
    ANDROID_TV("Android / Fire / Google TV (soon)")
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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("host", host).put("brand", brand.name)
        .put("token", token ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): Device? = runCatching {
            Device(
                id = o.getString("id"),
                name = o.getString("name"),
                host = o.getString("host"),
                brand = Brand.valueOf(o.getString("brand")),
                token = if (o.isNull("token")) null else o.getString("token"),
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
    val sound: Boolean = true,
    val accentHex: String = "0A84FF",
    val scrubStepDp: Float = 36f,
    val keepAwake: Boolean = true,
)

val ACCENTS = listOf(
    "Blue" to "0A84FF",
    "Green" to "30D158",
    "Purple" to "BF5AF2",
    "Pink" to "FF375F",
    "Yellow" to "FFD60A",
    "Silver" to "E5E5EA",
)

enum class Link { IDLE, CONNECTING, READY, ERROR }
