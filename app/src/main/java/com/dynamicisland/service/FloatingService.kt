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
    }

    private lateinit var windowManager: WindowManager
    private lateinit var islandView: IslandView
    private lateinit var prefs: IslandPrefs
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var isAdded = false

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NotificationService.ACTION_NOTIFICATION -> {
                    val removed = intent.getBooleanExtra(NotificationService.EXTRA_REMOVED, false)
                    if (removed) return
                    val pkg     = intent.getStringExtra(NotificationService.EXTRA_PACKAGE) ?: return
                    val title   = intent.getStringExtra(NotificationService.EXTRA_TITLE) ?: return
                    val text    = intent.getStringExtra(NotificationService.EXTRA_TEXT) ?: ""
                    val appName = intent.getStringExtra(NotificationService.EXTRA_APP_NAME) ?: ""
                    islandView.showNotification(pkg, appName, title, text)
                }
                ACTION_UPDATE_PREFS -> {
                    applyPrefs()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = IslandPrefs(this)
        startForegroundNotification()
        setupWindowManager()
        val filter = IntentFilter().apply {
            addAction(NotificationService.ACTION_NOTIFICATION)
            addAction(ACTION_UPDATE_PREFS)
        }
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private fun startForegroundNotification() {
        val channelId = "dynamic_island_service"
        val channel = NotificationChannel(channelId, "灵动岛服务",
            NotificationManager.IMPORTANCE_LOW).apply { description = "保持灵动岛运行" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("灵动岛运行中")
            .setContentText("点击进入设置")
            .setSmallIcon(R.drawable.ic_island)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    private fun setupWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        islandView = IslandView(this, prefs)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = prefs.positionY
        }

        windowManager.addView(islandView, layoutParams)
        isAdded = true
    }

    private fun applyPrefs() {
        if (!isAdded) return
        // Update Y position immediately
        layoutParams.y = prefs.positionY
        windowManager.updateViewLayout(islandView, layoutParams)
        // Tell IslandView about new size prefs
        islandView.updatePrefs(prefs)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isAdded) { windowManager.removeView(islandView); isAdded = false }
        try { unregisterReceiver(notificationReceiver) } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
