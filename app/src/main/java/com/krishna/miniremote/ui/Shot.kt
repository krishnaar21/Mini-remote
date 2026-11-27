package com.krishna.miniremote.ui

/**
 * CI screenshot runs (debug builds over adb only, see MainActivity): which screen to open, on
 * pretend TVs. Read once by the screen that owns it, so nothing reopens afterwards.
 */
object Shot {
    const val EXTRA = "shot"
    var name: String? = null
    /** A Settings row to open once Settings shows ("look", "apps"). */
    var openSetting: String? = null

    /** True once, for the screen named [s]. */
    fun take(s: String): Boolean = if (name == s) { name = null; true } else false
}
