package com.dynamicisland.service

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
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
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var isAdded = false

    private val prefsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_UPDATE_PREFS) applyPrefs()
        }
    }

    // Called directly by NotificationService
    fun onNewNotification(pkg: String, appName: String, title: String, text: String) {
        islandView.showNotification(pkg, appName, title, text)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = IslandPrefs(this)
        startForegroundNotification()
        setupWindowManager()
        registerReceiver(prefsReceiver, IntentFilter(ACTION_UPDATE_PREFS), RECEIVER_NOT_EXPORTED)
    }

    private fun startForegroundNotification() {
        val channelId = "dynamic_island_service"
        val channel = NotificationChannel(channelId, "灵动岛服务",
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("灵动岛运行中")
            .setSmallIcon(R.drawable.ic_island)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notif)
    }

    private fun setupWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Get real status bar height so we position correctly
        val statusBarHeight = getStatusBarHeight()

        islandView = IslandView(this, prefs)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // y=0 puts us at absolute top of screen.
            // Add statusBarHeight so we sit just below status bar,
            // plus any user offset.
            y = statusBarHeight + prefs.positionY
        }

        windowManager.addView(islandView, layoutParams)
        isAdded = true
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 60
    }

    private fun applyPrefs() {
        if (!isAdded) return
        layoutParams.y = getStatusBarHeight() + prefs.positionY
        windowManager.updateViewLayout(islandView, layoutParams)
        islandView.updatePrefs(prefs)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (isAdded) { windowManager.removeView(islandView); isAdded = false }
        try { unregisterReceiver(prefsReceiver) } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
