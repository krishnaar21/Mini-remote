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
        fun pick(v: Float, opts: List<Pair<Float, String>>, def: Float) = opts.map { it.first }.firstOrNull { it == v } ?: def
        return Settings(
            haptics = p.getBoolean("haptics", d.haptics),
            hapticStrength = pick(p.getFloat("strength", d.hapticStrength), HAPTIC_STRENGTHS, d.hapticStrength),
            sound = p.getBoolean("sound", d.sound),
            soundLevel = pick(p.getFloat("soundLevel", d.soundLevel), SOUND_LEVELS, d.soundLevel),
            // Only colours from the palette; anything else (or an old removed colour) falls back to the default.
            accentHex = ACCENTS.firstOrNull { it.second.equals(hex, true) }?.second ?: d.accentHex,
            scrubStepDp = p.getFloat("step", d.scrubStepDp).coerceIn(24f, 60f),
            gyro = p.getBoolean("gyro", d.gyro),
            keepAwake = p.getBoolean("awake", d.keepAwake),
            autoUpdate = p.getBoolean("autoupdate", d.autoUpdate),
        )
    }

    fun saveSettings(s: Settings) = p.edit()
        .putBoolean("haptics", s.haptics).putFloat("strength", s.hapticStrength)
        .putBoolean("sound", s.sound).putFloat("soundLevel", s.soundLevel)
        .putString("accent", s.accentHex).putFloat("step", s.scrubStepDp)
        .putBoolean("gyro", s.gyro).putBoolean("awake", s.keepAwake)
        .putBoolean("autoupdate", s.autoUpdate).apply()

    /** Whether the one-time local network explainer has been shown. */
    fun localSeen(): Boolean = p.getBoolean("localSeen", false)
    fun setLocalSeen() = p.edit().putBoolean("localSeen", true).apply()
}
