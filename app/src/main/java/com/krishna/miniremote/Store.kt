package com.krishna.miniremote

import android.content.Context

/** App-private storage (backup disabled in the manifest). Tokens only ever live here. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("mini_remote", Context.MODE_PRIVATE)

    fun loadDevices(): List<Device> = Device.listFromJson(p.getString("devices", null))
    fun saveDevices(l: List<Device>) = p.edit().putString("devices", Device.listToJson(l)).apply()
    fun activeId(): String? = p.getString("active", null)
    fun saveActive(id: String?) = p.edit().putString("active", id).apply()

    fun loadSettings(): Settings {
        val d = Settings()
        val hex = p.getString("accent", d.accentHex) ?: d.accentHex
        return Settings(
            haptics = p.getBoolean("haptics", d.haptics),
            sound = p.getBoolean("sound", d.sound),
            accentHex = if (Regex("^[0-9A-Fa-f]{6}$").matches(hex)) hex else d.accentHex,
            scrubStepDp = p.getFloat("step", d.scrubStepDp).coerceIn(24f, 64f),
            keepAwake = p.getBoolean("awake", d.keepAwake),
        )
    }

    fun saveSettings(s: Settings) = p.edit()
        .putBoolean("haptics", s.haptics).putBoolean("sound", s.sound)
        .putString("accent", s.accentHex).putFloat("step", s.scrubStepDp)
        .putBoolean("awake", s.keepAwake).apply()
}
