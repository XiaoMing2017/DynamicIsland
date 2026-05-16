package com.dynamicisland.utils

import android.content.Context

class IslandPrefs(context: Context) {
    private val p = context.getSharedPreferences("island_prefs", Context.MODE_PRIVATE)

    var offsetY: Int
        get() = p.getInt("offset_y", -8)
        set(v) = p.edit().putInt("offset_y", v).apply()

    var offsetX: Int
        get() = p.getInt("offset_x", 0)
        set(v) = p.edit().putInt("offset_x", v).apply()

    var widthPercent: Int
        get() = p.getInt("width_percent", 80)
        set(v) = p.edit().putInt("width_percent", v).apply()

    var pillWidthDp: Int
        get() = p.getInt("pill_width_dp", 120)
        set(v) = p.edit().putInt("pill_width_dp", v).apply()

    var pillHeightDp: Int
        get() = p.getInt("pill_height_dp", 34)
        set(v) = p.edit().putInt("pill_height_dp", v).apply()

    var expandedHeightDp: Int
        get() = p.getInt("expanded_height_dp", 70)
        set(v) = p.edit().putInt("expanded_height_dp", v).apply()

    // Blacklisted package names (comma-separated)
    var blacklist: Set<String>
        get() = p.getStringSet("blacklist", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("blacklist", v).apply()
}
