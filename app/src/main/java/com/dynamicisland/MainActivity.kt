package com.dynamicisland

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dynamicisland.databinding.ActivityMainBinding
import com.dynamicisland.service.FloatingService
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

        b.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else toast("悬浮窗权限已开启 ✓")
        }
        b.btnNotification.setOnClickListener {
            if (!isNLEnabled()) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            else toast("通知权限已开启 ✓")
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
        b.btnTest.setOnClickListener {
            val svc = FloatingService.instance
            if (svc == null) { toast("请先启动灵动岛"); return@setOnClickListener }
            listOf(
                Triple("com.tencent.mm",       "微信",     "张三: 你在吗？有空吗"),
                Triple("com.netease.cloudmusic","网易云音乐","晴天 - 周杰伦"),
                Triple("com.android.dialer",   "来电",     "李四 正在呼叫"),
                Triple("com.tencent.mobileqq", "QQ",      "你有一条新消息")
            ).random().let { (pkg, app, title) -> svc.onNewNotification(pkg, app, title, "点击查看") }
        }
    }

    private fun setupSliders() {
        // Y offset: range -60 to +60, default -8
        b.seekY.max = 120
        b.seekY.progress = prefs.offsetY + 60
        b.labelY.text = "纵向位置：${prefs.offsetY}px（负数=上移进状态栏）"
        b.seekY.on { v -> prefs.offsetY = v - 60; b.labelY.text = "纵向位置：${v - 60}px（负数=上移进状态栏）"; push() }

        // X offset: -100 to +100
        b.seekX.max = 200
        b.seekX.progress = prefs.offsetX + 100
        b.labelX.text = "横向位置：${prefs.offsetX}px（负数=左移）"
        b.seekX.on { v -> prefs.offsetX = v - 100; b.labelX.text = "横向位置：${v - 100}px（负数=左移）"; push() }

        // Expanded width
        b.seekWidth.max = 50
        b.seekWidth.progress = prefs.widthPercent - 50
        b.labelWidth.text = "展开宽度：${prefs.widthPercent}%"
        b.seekWidth.on { v -> prefs.widthPercent = v + 50; b.labelWidth.text = "展开宽度：${v + 50}%"; push() }

        // Pill width
        b.seekPillW.max = 100
        b.seekPillW.progress = prefs.pillWidthDp - 60
        b.labelPillW.text = "收起宽度：${prefs.pillWidthDp}dp"
        b.seekPillW.on { v -> prefs.pillWidthDp = v + 60; b.labelPillW.text = "收起宽度：${v + 60}dp"; push() }

        // Pill height
        b.seekPillH.max = 30
        b.seekPillH.progress = prefs.pillHeightDp - 20
        b.labelPillH.text = "收起高度：${prefs.pillHeightDp}dp"
        b.seekPillH.on { v -> prefs.pillHeightDp = v + 20; b.labelPillH.text = "收起高度：${v + 20}dp"; push() }

        // Expanded height
        b.seekExpdH.max = 60
        b.seekExpdH.progress = prefs.expandedHeightDp - 50
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
        b.statusOverlay.text = if (o) "✓ 悬浮窗权限已开启" else "✗ 悬浮窗权限未开启"
        b.statusOverlay.setTextColor(if (o) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
        b.statusNotification.text = if (n) "✓ 通知权限已开启" else "✗ 通知权限未开启"
        b.statusNotification.setTextColor(if (n) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
        b.btnStart.isEnabled = o && n
    }

    private fun isNLEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
