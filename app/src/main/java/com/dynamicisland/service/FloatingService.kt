package com.dynamicisland.service

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.core.app.NotificationCompat
import com.dynamicisland.R
import com.dynamicisland.ui.IslandView
import com.dynamicisland.utils.IslandPrefs

class FloatingService : Service() {

    companion object {
        const val ACTION_UPDATE_PREFS = "com.dynamicisland.UPDATE_PREFS"
        var instance: FloatingService? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var islandView: IslandView
    private lateinit var prefs: IslandPrefs
    private lateinit var wlp: WindowManager.LayoutParams
    private var isAdded = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == ACTION_UPDATE_PREFS) applyPrefs()
        }
    }

    fun onNewNotification(pkg: String, appName: String, title: String, text: String) {
        islandView.showNotification(pkg, appName, title, text)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = IslandPrefs(this)
        startFg()
        setupWindow()
        registerReceiver(receiver, IntentFilter(ACTION_UPDATE_PREFS), RECEIVER_NOT_EXPORTED)
    }

    private fun startFg() {
        val ch = "island_svc"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "灵动岛", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, NotificationCompat.Builder(this, ch)
            .setContentTitle("灵动岛运行中").setSmallIcon(R.drawable.ic_island)
            .setPriority(NotificationCompat.PRIORITY_LOW).build())
    }

    private fun setupWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        islandView = IslandView(this, prefs)

        wlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_LAYOUT_NO_LIMITS lets the view go above status bar with negative Y
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = prefs.offsetX
            y = prefs.offsetY  // Negative = inside status bar, covering camera hole
        }

        windowManager.addView(islandView, wlp)
        isAdded = true
    }

    private fun applyPrefs() {
        if (!isAdded) return
        wlp.x = prefs.offsetX
        wlp.y = prefs.offsetY
        windowManager.updateViewLayout(islandView, wlp)
        islandView.updatePrefs(prefs)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (isAdded) { windowManager.removeView(islandView); isAdded = false }
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    override fun onBind(i: Intent?): IBinder? = null
}
