package com.dynamicisland.service

import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicisland.utils.IslandPrefs

class NotificationService : NotificationListenerService() {

    companion object {
        var instance: NotificationService? = null

        val APP_NAMES = mapOf(
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
            "com.miui.player"             to "小米音乐",
            "com.google.android.apps.youtube.music" to "YouTube Music"
        )

        val MUSIC_PKGS = setOf(
            "com.netease.cloudmusic","com.tencent.qqmusic","com.kugou.android",
            "com.spotify.music","com.miui.player","com.google.android.apps.youtube.music",
            "com.apple.android.music"
        )

        val CALL_PKGS = setOf(
            "com.android.dialer","com.google.android.dialer",
            "com.miui.phone","com.samsung.android.incallui"
        )
    }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { super.onDestroy(); instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = IslandPrefs(applicationContext)
        val pkg = sbn.packageName ?: return
        if (prefs.blacklist.contains(pkg)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString("android.title") ?: return
        val text  = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank()) return

        val appName = APP_NAMES[pkg] ?: getAppLabel(pkg)
        val icon    = getAppIcon(pkg)
        val isCall  = CALL_PKGS.contains(pkg)
        val isMusic = MUSIC_PKGS.contains(pkg)
        val pendingIntent = sbn.notification.contentIntent

        FloatingService.instance?.onNewNotification(
            pkg, appName, title, text, icon, isCall, isMusic, pendingIntent
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Could dismiss island if matching pkg
    }

    fun getAppLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg.substringAfterLast(".") }

    fun getAppIcon(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) { null }
}
