package com.dynamicisland.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIFICATION = "com.dynamicisland.NOTIFICATION"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_REMOVED = "removed"

        // Singleton for direct access
        var instance: NotificationService? = null

        private val APP_NAMES = mapOf(
            "com.tencent.mm" to "微信",
            "com.tencent.mobileqq" to "QQ",
            "com.alibaba.android.rimet" to "钉钉",
            "com.lark.android" to "飞书",
            "com.netease.cloudmusic" to "网易云音乐",
            "com.tencent.qqmusic" to "QQ音乐",
            "com.kugou.android" to "酷狗音乐",
            "com.spotify.music" to "Spotify",
            "com.google.android.apps.maps" to "地图导航",
            "com.autonavi.minimap" to "高德地图",
            "com.baidu.BaiduMap" to "百度地图",
            "com.android.dialer" to "电话",
            "com.google.android.dialer" to "电话",
            "com.android.systemui" to "系统"
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val pkg = sbn.packageName
        val appName = APP_NAMES[pkg] ?: getAppLabel(pkg)

        if (title.isBlank()) return

        // Broadcast to FloatingService
        val intent = Intent(ACTION_NOTIFICATION).apply {
            putExtra(EXTRA_PACKAGE, pkg)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_APP_NAME, appName)
            putExtra(EXTRA_REMOVED, false)
        }
        sendBroadcast(intent)

        // Also call directly for immediate response
        FloatingService.instance?.onNewNotification(pkg, appName, title, text)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val intent = Intent(ACTION_NOTIFICATION).apply {
            putExtra(EXTRA_PACKAGE, sbn.packageName)
            putExtra(EXTRA_REMOVED, true)
        }
        sendBroadcast(intent)
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
