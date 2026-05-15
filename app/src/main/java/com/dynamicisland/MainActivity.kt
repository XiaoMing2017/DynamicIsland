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
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "悬浮窗权限已开启 ✓", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNotification.setOnClickListener {
            if (!isNotificationListenerEnabled()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                Toast.makeText(this, "通知权限已开启 ✓", Toast.LENGTH_SHORT).show()
            }
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
            Toast.makeText(this, "灵动岛已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSliders() {
        // Y position (0–200px)
        binding.seekY.max = 200
        binding.seekY.progress = prefs.positionY
        binding.labelY.text = "距顶部距离：${prefs.positionY}px"
        binding.seekY.setOnSeekBarChangeListener(seekListener { v ->
            prefs.positionY = v
            binding.labelY.text = "距顶部距离：${v}px"
            notifyUpdate()
        })

        // Expanded width (50–100%)
        binding.seekWidth.max = 50
        binding.seekWidth.progress = prefs.widthPercent - 50
        binding.labelWidth.text = "展开宽度：${prefs.widthPercent}%"
        binding.seekWidth.setOnSeekBarChangeListener(seekListener { v ->
            prefs.widthPercent = v + 50
            binding.labelWidth.text = "展开宽度：${v + 50}%"
            notifyUpdate()
        })

        // Collapsed pill width (80–160dp)
        binding.seekPillWidth.max = 80
        binding.seekPillWidth.progress = prefs.pillWidthDp - 80
        binding.labelPillWidth.text = "收起宽度：${prefs.pillWidthDp}dp"
        binding.seekPillWidth.setOnSeekBarChangeListener(seekListener { v ->
            prefs.pillWidthDp = v + 80
            binding.labelPillWidth.text = "收起宽度：${v + 80}dp"
            notifyUpdate()
        })

        // Expanded height (32–80dp)
        binding.seekHeight.max = 48
        binding.seekHeight.progress = prefs.expandedHeightDp - 32
        binding.labelHeight.text = "展开高度：${prefs.expandedHeightDp}dp"
        binding.seekHeight.setOnSeekBarChangeListener(seekListener { v ->
            prefs.expandedHeightDp = v + 32
            binding.labelHeight.text = "展开高度：${v + 32}dp"
            notifyUpdate()
        })
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, v: Int, user: Boolean) = onChange(v)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun notifyUpdate() {
        sendBroadcast(Intent(FloatingService.ACTION_UPDATE_PREFS))
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = isNotificationListenerEnabled()
        binding.statusOverlay.text = if (overlayOk) "✓ 悬浮窗权限已开启" else "✗ 悬浮窗权限未开启"
        binding.statusNotification.text = if (notifOk) "✓ 通知权限已开启" else "✗ 通知权限未开启"
        binding.btnStart.isEnabled = overlayOk && notifOk
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            flat.split(":").forEach {
                val cn = ComponentName.unflattenFromString(it)
                if (cn != null && TextUtils.equals(packageName, cn.packageName)) return true
            }
        }
        return false
    }
}
