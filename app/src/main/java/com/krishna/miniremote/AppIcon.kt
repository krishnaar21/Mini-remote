package com.krishna.miniremote

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The app icon designs; the launcher shows the one whose alias is enabled. Pebble is the default. */
object AppIcon {
    val all = listOf("pebble" to "Pebble", "dial" to "Dial", "paper" to "Paper", "midnight" to "Midnight", "red" to "Red", "mono" to "Mono")

    fun current(ctx: Context): String = all.firstOrNull { (k, _) ->
        val st = ctx.packageManager.getComponentEnabledSetting(component(ctx, k))
        st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && k == DEFAULT)
    }?.first ?: DEFAULT

    private const val DEFAULT = "pebble"

    /**
     * Someone who picked an icon before Pebble existed has that one switched on, and Pebble is on by
     * default too: two launcher entries. Keep their pick and switch Pebble off. Run at start.
     */
    fun reconcile(ctx: Context) = runCatching {
        val pm = ctx.packageManager
        val picked = all.any { (k, _) -> k != DEFAULT && pm.getComponentEnabledSetting(component(ctx, k)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
        if (picked && pm.getComponentEnabledSetting(component(ctx, DEFAULT)) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            pm.setComponentEnabledSetting(component(ctx, DEFAULT), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    private fun component(ctx: Context, key: String) = ComponentName(ctx, "${ctx.packageName}.Icon${key.replaceFirstChar { it.uppercase() }}")

    /** Switches the launcher icon (the home screen may take a moment to update). */
    fun set(ctx: Context, key: String) {
        val pm = ctx.packageManager
        // Enable the new one first so there's always a launcher entry.
        pm.setComponentEnabledSetting(component(ctx, key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all.filter { it.first != key }.forEach {
            pm.setComponentEnabledSetting(component(ctx, it.first), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    fun bg(key: String) = when (key) { "pebble" -> R.drawable.ic_app_pebble_bg; "paper" -> R.drawable.ic_app_paper_bg; "midnight" -> R.drawable.ic_app_midnight_bg; "red" -> R.drawable.ic_app_red_bg; "mono" -> R.drawable.ic_app_mono_bg; "dial" -> R.drawable.ic_app_dial_bg; else -> R.drawable.ic_app_pebble_bg }
    fun fg(key: String) = when (key) { "pebble" -> R.drawable.ic_app_pebble_fg; "paper" -> R.drawable.ic_app_paper_fg; "midnight" -> R.drawable.ic_app_midnight_fg; "red" -> R.drawable.ic_app_red_fg; "mono" -> R.drawable.ic_app_mono_fg; "dial" -> R.drawable.ic_app_dial_fg; else -> R.drawable.ic_app_pebble_fg }
}
