package com.dynamicisland

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dynamicisland.databinding.ActivityMainBinding
import com.dynamicisland.service.FloatingService
import com.dynamicisland.utils.IslandPrefs

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: IslandPrefs

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
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
            if (!isNLEnabled()) { toast("请先开启通知监听权限"); return@setOnClickListener }
            startForegroundService(Intent(this, FloatingService::class.java))
            toast("灵动岛已启动！")
            b.btnStart.text = "运行中 ✓"
            b.btnStart.isEnabled = false
        }
        b.btnStop.setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            b.btnStart.text = "启动灵动岛"
            b.btnStart.isEnabled = true
            toast("已停止")
        }
        b.btnTest.setOnClickListener {
            val svc = FloatingService.instance ?: run { toast("请先启动灵动岛"); return@setOnClickListener }
            val icon = try { packageManager.getApplicationIcon("com.tencent.mm") } catch (e: Exception) { null }
            val cases = listOf(
                arrayOf("com.tencent.mm",        "微信",      "张三: 你好，在干嘛呢",    "下午好！",          false, false),
                arrayOf("com.netease.cloudmusic", "网易云音乐", "晴天 - 周杰伦",          "专辑：叶惠美",      false, true),
                arrayOf("com.android.dialer",     "来电",      "李四",                  "",                 true,  false),
                arrayOf("com.tencent.mobileqq",   "QQ",        "群消息 (5)",             "王五: 今晚打游戏吗", false, false)
            )
            val pick = cases.random()
            @Suppress("UNCHECKED_CAST")
            svc.onNewNotification(
                pick[0] as String, pick[1] as String, pick[2] as String, pick[3] as String,
                icon, pick[4] as Boolean, pick[5] as Boolean, null
            )
        }
        b.btnBlacklist.setOnClickListener { showBlacklistDialog() }
    }

    private fun showBlacklistDialog() {
        val apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString() }
        val blacklist = prefs.blacklist.toMutableSet()
        val labels  = apps.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
        val checked = apps.map { blacklist.contains(it.packageName) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("屏蔽以下 App 的通知")
            .setMultiChoiceItems(labels, checked) { _, i, on ->
                if (on) blacklist.add(apps[i].packageName) else blacklist.remove(apps[i].packageName)
            }
            .setPositiveButton("保存") { _, _ -> prefs.blacklist = blacklist; toast("已保存") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupSliders() {
        slider(b.seekY, b.labelY, prefs.offsetY, -60, 60, "纵向位置", "px（负=上移）") { prefs.offsetY = it }
        slider(b.seekX, b.labelX, prefs.offsetX, -100, 100, "横向位置", "px") { prefs.offsetX = it }
        slider(b.seekWidth, b.labelWidth, prefs.widthPercent, 50, 100, "展开宽度", "%") { prefs.widthPercent = it }
        slider(b.seekPillW, b.labelPillW, prefs.pillWidthDp, 60, 180, "收起宽度", "dp") { prefs.pillWidthDp = it }
        slider(b.seekPillH, b.labelPillH, prefs.pillHeightDp, 20, 50, "收起高度", "dp") { prefs.pillHeightDp = it }
        slider(b.seekExpdH, b.labelExpdH, prefs.expandedHeightDp, 50, 110, "展开高度", "dp") { prefs.expandedHeightDp = it }
    }

    private fun slider(
        bar: SeekBar, label: TextView,
        current: Int, min: Int, max: Int,
        name: String, unit: String,
        save: (Int) -> Unit
    ) {
        bar.max = max - min
        bar.progress = current - min
        label.text = "$name：$current$unit"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, u: Boolean) {
                val real = v + min
                label.text = "$name：$real$unit"
                save(real)
                sendBroadcast(Intent(FloatingService.ACTION_UPDATE_PREFS))
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    override fun onResume() { super.onResume(); updateStatus() }

    private fun updateStatus() {
        val o = Settings.canDrawOverlays(this)
        val n = isNLEnabled()
        b.statusOverlay.text = if (o) "✓ 悬浮窗" else "✗ 悬浮窗"
        b.statusOverlay.setTextColor(if (o) 0xFF27AE60.toInt() else 0xFFE74C3C.toInt())
        b.statusNotification.text = if (n) "✓ 通知监听" else "✗ 通知监听"
        b.statusNotification.setTextColor(if (n) 0xFF27AE60.toInt() else 0xFFE74C3C.toInt())
        b.btnStart.isEnabled = o && n
    }

    private fun isNLEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
