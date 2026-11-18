package com.krishna.miniremote

import android.content.Context

/** App-private storage (backup disabled in the manifest). Tokens only ever live here. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("mini_remote", Context.MODE_PRIVATE)

    /** Saved TVs (with their pairing tokens and pins), encrypted with [Vault]. Older plain copies are moved over. */
    fun loadDevices(): List<Device> {
        p.getString("devices_v", null)?.let { return Device.listFromJson(Vault.open(it)) }
        val legacy = p.getString("devices", null) ?: return emptyList()
        val l = Device.listFromJson(legacy)
        saveDevices(l)
        return l
    }
    fun saveDevices(l: List<Device>) {
        val e = p.edit().remove("devices")
        runCatching { Vault.seal(Device.listToJson(l)) }
            .onSuccess { e.putString("devices_v", it) }
            .onFailure { e.putString("devices", Device.listToJson(l)) } // no Keystore: still app-private
        e.apply()
    }

    /** Erase everything this app stored. */
    fun clearAll() = p.edit().clear().commit()
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
            controlStyle = p.getString("control", d.controlStyle)?.takeIf { it == "dial" || it == "trackpad" } ?: d.controlStyle,
            profileName = p.getString("profileName", d.profileName) ?: d.profileName,
            volumeKeys = p.getBoolean("volumeKeys", d.volumeKeys),
            logos = p.getBoolean("logos", d.logos),
            autoKeyboard = p.getBoolean("autoKeyboard", d.autoKeyboard),
            notifRemote = p.getBoolean("notifRemote", d.notifRemote),
            layout = p.getString("layout", d.layout)?.takeIf { it in setOf("cards", "strip") } ?: d.layout,
        )
    }

    fun saveSettings(s: Settings) = p.edit()
        .putBoolean("haptics", s.haptics).putFloat("strength", s.hapticStrength)
        .putBoolean("sound", s.sound).putFloat("soundLevel", s.soundLevel)
        .putString("accent", s.accentHex).putFloat("step", s.scrubStepDp)
        .putBoolean("gyro", s.gyro).putBoolean("awake", s.keepAwake)
        .putBoolean("autoupdate", s.autoUpdate)
        .putString("control", s.controlStyle).putString("profileName", s.profileName)
        .putBoolean("volumeKeys", s.volumeKeys).putBoolean("logos", s.logos).putString("layout", s.layout)
        .putBoolean("autoKeyboard", s.autoKeyboard).putBoolean("notifRemote", s.notifRemote).apply()

    fun loadShortcuts(): List<Shortcut> = Shortcut.listFromJson(p.getString("shortcuts", null))
    fun saveShortcuts(l: List<Shortcut>) = p.edit().putString("shortcuts", Shortcut.listToJson(l)).apply()

    /** Whether the one-time local network explainer has been shown. */
    fun localSeen(): Boolean = p.getBoolean("localSeen", false)
    fun setLocalSeen() = p.edit().putBoolean("localSeen", true).apply()
}
