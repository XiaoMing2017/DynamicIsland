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

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: IslandPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = IslandPrefs(this)
        updateStatus()
        setupSliders()

        binding.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "悬浮窗权限已开启 ✓", Toast.LENGTH_SHORT).show()
        }

        binding.btnNotification.setOnClickListener {
            if (!isNotificationListenerEnabled())
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            else Toast.makeText(this, "通知权限已开启 ✓", Toast.LENGTH_SHORT).show()
        }

        binding.btnStart.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) ->
                    Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                !isNotificationListenerEnabled() ->
                    Toast.makeText(this, "请先开启通知监听权限", Toast.LENGTH_SHORT).show()
                else -> {
                    startForegroundService(Intent(this, FloatingService::class.java))
                    Toast.makeText(this, "灵动岛已启动！", Toast.LENGTH_SHORT).show()
                    binding.btnStart.text = "运行中 ✓"
                    binding.btnStart.isEnabled = false
                }
            }
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            binding.btnStart.text = "启动灵动岛"
            binding.btnStart.isEnabled = true
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        }

        // Test button — directly calls FloatingService
        binding.btnTest.setOnClickListener {
            val service = FloatingService.instance
            if (service == null) {
                Toast.makeText(this, "请先启动灵动岛", Toast.LENGTH_SHORT).show()
            } else {
                val messages = listOf(
                    Triple("com.tencent.mm",      "微信",     "张三: 在吗？"),
                    Triple("com.netease.cloudmusic","网易云音乐","Blinding Lights - The Weeknd"),
                    Triple("com.android.dialer",  "来电",     "李四 正在呼叫"),
                    Triple("com.tencent.mobileqq","QQ",      "你有一条新消息")
                )
                val pick = messages.random()
                service.onNewNotification(pick.first, pick.second, pick.third, "点击查看详情")
            }
        }
    }

    private fun setupSliders() {
        binding.seekY.max = 150
        binding.seekY.progress = prefs.positionY
        binding.labelY.text = "微调位置（当前 ${prefs.positionY}px）"
        binding.seekY.onChange { v ->
            prefs.positionY = v
            binding.labelY.text = "微调位置（当前 ${v}px）"
            notifyUpdate()
        }

        binding.seekWidth.max = 50
        binding.seekWidth.progress = prefs.widthPercent - 50
        binding.labelWidth.text = "展开宽度：${prefs.widthPercent}%"
        binding.seekWidth.onChange { v ->
            prefs.widthPercent = v + 50
            binding.labelWidth.text = "展开宽度：${v + 50}%"
            notifyUpdate()
        }

        binding.seekPillWidth.max = 80
        binding.seekPillWidth.progress = prefs.pillWidthDp - 80
        binding.labelPillWidth.text = "收起宽度：${prefs.pillWidthDp}dp"
        binding.seekPillWidth.onChange { v ->
            prefs.pillWidthDp = v + 80
            binding.labelPillWidth.text = "收起宽度：${v + 80}dp"
            notifyUpdate()
        }

        binding.seekHeight.max = 48
        binding.seekHeight.progress = prefs.expandedHeightDp - 32
        binding.labelHeight.text = "展开高度：${prefs.expandedHeightDp}dp"
        binding.seekHeight.onChange { v ->
            prefs.expandedHeightDp = v + 32
            binding.labelHeight.text = "展开高度：${v + 32}dp"
            notifyUpdate()
        }
    }

    private fun SeekBar.onChange(block: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, user: Boolean) = block(v)
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun notifyUpdate() = sendBroadcast(Intent(FloatingService.ACTION_UPDATE_PREFS))

    override fun onResume() { super.onResume(); updateStatus() }

    private fun updateStatus() {
        val o = Settings.canDrawOverlays(this)
        val n = isNotificationListenerEnabled()
        binding.statusOverlay.apply {
            text = if (o) "✓ 悬浮窗权限已开启" else "✗ 悬浮窗权限未开启"
            setTextColor(if (o) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
        }
        binding.statusNotification.apply {
            text = if (n) "✓ 通知权限已开启" else "✗ 通知权限未开启"
            setTextColor(if (n) 0xFF30D158.toInt() else 0xFFFF453A.toInt())
        }
        binding.btnStart.isEnabled = o && n
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any {
            val cn = ComponentName.unflattenFromString(it)
            cn != null && cn.packageName == packageName
        }
    }
}
