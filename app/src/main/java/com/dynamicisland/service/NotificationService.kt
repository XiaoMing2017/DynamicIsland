package com.dynamicisland.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    companion object {
        // Singleton so FloatingService can call us directly
        var instance: NotificationService? = null

        private val APP_NAMES = mapOf(
            "com.tencent.mm"              to "微信",
            "com.tencent.mobileqq"        to "QQ",
            "com.alibaba.android.rimet"   to "钉钉",
            "com.lark.android"            to "飞书",
            "com.netease.cloudmusic"      to "网易云音乐",
            "com.tencent.qqmusic"         to "QQ音乐",
            "com.kugou.android"           to "酷狗音乐",
            "com.spotify.music"           to "Spotify",
            "com.autonavi.minimap"        to "高德地图",
            "com.baidu.BaiduMap"          to "百度地图",
            "com.android.dialer"          to "来电",
            "com.google.android.dialer"   to "来电",
            "com.android.systemui"        to "系统"
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
        val extras = sbn.notification.extras ?: return
        val title   = extras.getString("android.title") ?: return
        val text    = extras.getCharSequence("android.text")?.toString() ?: ""
        val pkg     = sbn.packageName ?: return
        val appName = APP_NAMES[pkg] ?: getAppLabel(pkg)

        if (title.isBlank()) return

        // Call FloatingService directly — no broadcast needed
        FloatingService.instance?.onNewNotification(pkg, appName, title, text)
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
}
