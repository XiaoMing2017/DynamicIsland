package com.dynamicisland.utils

import android.content.Context

class IslandPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("island_prefs", Context.MODE_PRIVATE)

    var positionY: Int
        get() = prefs.getInt("position_y", 0)
        set(v) = prefs.edit().putInt("position_y", v).apply()

    var widthPercent: Int
        get() = prefs.getInt("width_percent", 75)
        set(v) = prefs.edit().putInt("width_percent", v).apply()

    var pillWidthDp: Int
        get() = prefs.getInt("pill_width_dp", 110)
        set(v) = prefs.edit().putInt("pill_width_dp", v).apply()

    var expandedHeightDp: Int
        get() = prefs.getInt("expanded_height_dp", 64)
        set(v) = prefs.edit().putInt("expanded_height_dp", v).apply()
}
