package com.dynamicisland.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IslandAccessibilityService : AccessibilityService() {

    companion object {
        var instance: IslandAccessibilityService? = null

        // Known music app packages
        val MUSIC_PKGS = setOf(
            "com.netease.cloudmusic", "com.tencent.qqmusic", "com.kugou.android",
            "com.spotify.music", "com.miui.player", "com.google.android.apps.youtube.music",
            "com.apple.android.music", "com.xiami.player"
        )

        // Known call packages
        val CALL_PKGS = setOf(
            "com.android.incallui", "com.android.dialer", "com.google.android.dialer",
            "com.miui.phone", "com.samsung.android.incallui", "com.huawei.incallui",
            "com.oppo.incallui", "com.vivo.incallui"
        )

        // Known nav packages
        val NAV_PKGS = setOf(
            "com.autonavi.minimap", "com.baidu.BaiduMap",
            "com.google.android.apps.maps", "com.tencent.map"
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        when {
            // ── CALL ──────────────────────────────────────────────────────
            CALL_PKGS.contains(pkg) -> handleCall(event, pkg)

            // ── MUSIC ─────────────────────────────────────────────────────
            MUSIC_PKGS.contains(pkg) -> handleMusic(event, pkg)

            // ── NAVIGATION ────────────────────────────────────────────────
            NAV_PKGS.contains(pkg) -> handleNav(event, pkg)
        }
    }

    private fun handleCall(event: AccessibilityEvent, pkg: String) {
        val svc = FloatingService.instance ?: return
        val root = rootInActiveWindow ?: return

        // Try to extract caller name from call screen
        val callerName = findText(root, listOf(
            "caller_name", "contact_name", "name", "caller"
        )) ?: event.text?.firstOrNull()?.toString() ?: "未知来电"

        val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
        svc.onNewNotification(pkg, "来电", callerName, "正在呼叫...", icon, true, false, null)
        root.recycle()
    }

    private fun handleMusic(event: AccessibilityEvent, pkg: String) {
        val svc = FloatingService.instance ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val root = rootInActiveWindow ?: return

        // Try to find song title and artist from the music app UI
        val songTitle = findText(root, listOf(
            "song_name", "title", "music_title", "song_title",
            "tv_name", "tv_song", "songname", "track_name"
        )) ?: event.text?.firstOrNull()?.toString()

        val artist = findText(root, listOf(
            "artist_name", "artist", "tv_artist", "singer",
            "author_name", "musician"
        )) ?: ""

        if (songTitle.isNullOrBlank()) { root.recycle(); return }

        val appName = NotificationService.APP_NAMES[pkg]
            ?: try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
               catch (e: Exception) { "音乐" }

        val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
        svc.onNewNotification(pkg, appName, songTitle, artist, icon, false, true, null)
        root.recycle()
    }

    private fun handleNav(event: AccessibilityEvent, pkg: String) {
        val svc = FloatingService.instance ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        val instruction = findText(root, listOf(
            "instruction", "direction", "nav_instruction",
            "guide_text", "turn_instruction"
        )) ?: event.text?.firstOrNull()?.toString()

        if (instruction.isNullOrBlank()) { root.recycle(); return }

        val appName = when (pkg) {
            "com.autonavi.minimap" -> "高德地图"
            "com.baidu.BaiduMap"  -> "百度地图"
            else                  -> "导航"
        }
        val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
        svc.onNewNotification(pkg, appName, instruction, "导航中", icon, false, false, null)
        root.recycle()
    }

    /**
     * Try to find a TextView by common resource-id keywords.
     * Falls back to scanning all text nodes.
     */
    private fun findText(root: AccessibilityNodeInfo, idKeywords: List<String>): String? {
        // First pass: match by viewIdResourceName
        for (keyword in idKeywords) {
            val nodes = root.findAccessibilityNodeInfosByViewId(
                "${root.packageName}:id/$keyword"
            )
            if (nodes.isNotEmpty()) {
                val text = nodes.firstOrNull { !it.text.isNullOrBlank() }?.text?.toString()
                nodes.forEach { it.recycle() }
                if (!text.isNullOrBlank()) return text
            }
        }
        // Second pass: scan all visible text nodes, pick the longest non-trivial one
        return scanAllText(root)
            .filter { it.length in 2..80 }
            .maxByOrNull { it.length }
    }

    private fun scanAllText(node: AccessibilityNodeInfo): List<String> {
        val results = mutableListOf<String>()
        if (!node.text.isNullOrBlank()) results.add(node.text.toString())
        if (!node.contentDescription.isNullOrBlank()) results.add(node.contentDescription.toString())
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> results.addAll(scanAllText(child)); child.recycle() }
        }
        return results
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Auto-start the floating service when accessibility is granted
        try {
            startForegroundService(Intent(this, FloatingService::class.java))
        } catch (e: Exception) {}
    }
}
