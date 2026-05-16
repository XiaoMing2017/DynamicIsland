package com.dynamicisland

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dynamicisland.databinding.ActivityMainBinding
import com.dynamicisland.service.FloatingService
import com.dynamicisland.service.NotificationService
import com.dynamicisland.utils.IslandPrefs

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: IslandPrefs

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = IslandPrefs(this)
        updateStatus()
        setupSliders()
        setupButtons()
    }

    private fun setupButtons() {
        b.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else toast("已开启 ✓")
        }
        b.btnNotification.setOnClickListener {
            if (!isNLEnabled()) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            else toast("已开启 ✓")
        }
        b.btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) { toast("请先开启悬浮窗权限"); return@setOnClickListener }
            if (!isNLEnabled())                  { toast("请先开启通知监听权限"); return@setOnClickListener }
            startForegroundService(Intent(this, FloatingService::class.java))
            toast("灵动岛已启动！")
            b.btnStart.text = "运行中 ✓"; b.btnStart.isEnabled = false
        }
        b.btnStop.setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            b.btnStart.text = "启动灵动岛"; b.btnStart.isEnabled = true; toast("已停止")
        }

        // Test button
        b.btnTest.setOnClickListener {
            val svc = FloatingService.instance ?: run { toast("请先启动灵动岛"); return@setOnClickListener }
            val icon = getAppIconSafe("com.tencent.mm")
            listOf(
                listOf("com.tencent.mm",       "微信",      "张三: 你好啊，在干嘛",  "下午好！", false, false),
                listOf("com.netease.cloudmusic","网易云",    "晴天 - 周杰伦",         "专辑：叶惠美", false, true),
                listOf("com.android.dialer",   "来电",      "李四",                  "", true, false),
                listOf("com.tencent.mobileqq", "QQ",       "群消息 (3)",             "王五: 今晚打游戏吗", false, false)
            ).random().let { r ->
                svc.onNewNotification(
                    r[0] as String, r[1] as String, r[2] as String, r[3] as String,
                    icon, r[4] as Boolean, r[5] as Boolean, null
                )
            }
        }

        // Blacklist management
        b.btnBlacklist.setOnClickListener { showBlacklistDialog() }
    }

    private fun showBlacklistDialog() {
        val installedApps = packageManager.getInstalledApplications(0)
            .filter { pm -> pm.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString() }

        val blacklist = prefs.blacklist.toMutableSet()
        val labels = installedApps.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
        val checked = installedApps.map { blacklist.contains(it.packageName) }.toBooleanArray()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("屏蔽以下 app 的通知")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = installedApps[which].packageName
                if (isChecked) blacklist.add(pkg) else blacklist.remove(pkg)
            }
            .setPositiveButton("保存") { _, _ -> prefs.blacklist = blacklist; toast("已保存") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getAppIconSafe(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) { null }

    private fun setupSliders() {
        // Y offset: -60 to +60
        b.seekY.max = 120; b.seekY.progress = prefs.offsetY + 60
        b.labelY.text = "纵向位置：${prefs.offsetY}px"
        b.seekY.on { v -> prefs.offsetY = v - 60; b.labelY.text = "纵向位置：${v - 60}px（负=上移）"; push() }

        // X offset: -100 to +100
        b.seekX.max = 200; b.seekX.progress = prefs.offsetX + 100
        b.labelX.text = "横向位置：${prefs.offsetX}px"
        b.seekX.on { v -> prefs.offsetX = v - 100; b.labelX.text = "横向位置：${v - 100}px"; push() }

        // Expanded width
        b.seekWidth.max = 50; b.seekWidth.progress = prefs.widthPercent - 50
        b.labelWidth.text = "展开宽度：${prefs.widthPercent}%"
        b.seekWidth.on { v -> prefs.widthPercent = v + 50; b.labelWidth.text = "展开宽度：${v + 50}%"; push() }

        // Pill width
        b.seekPillW.max = 100; b.seekPillW.progress = prefs.pillWidthDp - 60
        b.labelPillW.text = "收起宽度：${prefs.pillWidthDp}dp"
        b.seekPillW.on { v -> prefs.pillWidthDp = v + 60; b.labelPillW.text = "收起宽度：${v + 60}dp"; push() }

        // Pill height
        b.seekPillH.max = 30; b.seekPillH.progress = prefs.pillHeightDp - 20
        b.labelPillH.text = "收起高度：${prefs.pillHeightDp}dp"
        b.seekPillH.on { v -> prefs.pillHeightDp = v + 20; b.labelPillH.text = "收起高度：${v + 20}dp"; push() }

        // Expanded height
        b.seekExpdH.max = 60; b.seekExpdH.progress = prefs.expandedHeightDp - 50
        b.labelExpdH.text = "展开高度：${prefs.expandedHeightDp}dp"
        b.seekExpdH.on { v -> prefs.expandedHeightDp = v + 50; b.labelExpdH.text = "展开高度：${v + 50}dp"; push() }
    }

    private fun SeekBar.on(f: (Int) -> Unit) = setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, v: Int, u: Boolean) = f(v)
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) {}
    })

    private fun push() = sendBroadcast(Intent(FloatingService.ACTION_UPDATE_PREFS))

    override fun onResume() { super.onResume(); updateStatus() }

    private fun updateStatus() {
        val o = Settings.canDrawOverlays(this); val n = isNLEnabled()
        b.statusOverlay.text = if (o) "✓ 悬浮窗权限" else "✗ 悬浮窗权限"
        b.statusOverlay.setTextColor(if (o) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
        b.statusNotification.text = if (n) "✓ 通知监听权限" else "✗ 通知监听权限"
        b.statusNotification.setTextColor(if (n) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
        b.btnStart.isEnabled = o && n
    }

    private fun isNLEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
