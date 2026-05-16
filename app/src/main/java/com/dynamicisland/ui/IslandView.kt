package com.dynamicisland.ui

import android.animation.*
import android.app.PendingIntent
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.*
import android.widget.*
import com.dynamicisland.utils.IslandPrefs

class IslandView(context: Context, private var prefs: IslandPrefs) : FrameLayout(context) {

    private val d = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    private var pillW = dp(prefs.pillWidthDp.toFloat())
    private var pillH = dp(prefs.pillHeightDp.toFloat())

    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoCollapseJob: Runnable? = null
    private var currentPendingIntent: PendingIntent? = null
    private var currentPkg: String = ""

    // Notification queue for stacking
    data class NotifItem(
        val pkg: String, val appName: String, val title: String,
        val text: String, val icon: Drawable?, val isCall: Boolean,
        val isMusic: Boolean, val pendingIntent: PendingIntent?
    )
    private val queue = ArrayDeque<NotifItem>()
    private var currentIndex = 0

    // Views
    private val pill: FrameLayout
    private val dotIndicator: LinearLayout // stacked dots

    // Normal notification layout
    private val notifRoot: LinearLayout
    private val appIconView: ImageView
    private val appIconFallback: TextView
    private val textCol: LinearLayout
    private val tvApp: TextView
    private val tvTitle: TextView
    private val tvBody: TextView

    // Call layout
    private val callRoot: LinearLayout
    private val callIconView: TextView
    private val callTextCol: LinearLayout
    private val callName: TextView
    private val callStatus: TextView
    private val callAccept: TextView
    private val callDecline: TextView

    // Music layout
    private val musicRoot: LinearLayout
    private val musicIconView: TextView
    private val musicTextCol: LinearLayout
    private val tvSong: TextView
    private val tvArtist: TextView
    private val waveView: WaveView

    // Stack indicator
    private val stackDots: LinearLayout

    init {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        clipChildren = false; clipToPadding = false

        pill = FrameLayout(context).apply {
            background = pillBg()
            elevation = dp(14f).toFloat()
            clipChildren = false; clipToPadding = false
            layoutParams = LayoutParams(pillW, pillH)
        }
        addView(pill)

        // ── NOTIF LAYOUT ─────────────────────────────────────────────────
        notifRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f; visibility = GONE
            setPadding(dp(10f), dp(6f), dp(12f), dp(6f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val iconFrame = FrameLayout(context).apply {
            val s = dp(36f)
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(8f) }
        }
        appIconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(0, 0, 0, 0)
            }
        }
        appIconFallback = TextView(context).apply {
            textSize = 20f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        iconFrame.addView(appIconView); iconFrame.addView(appIconFallback)

        textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvApp = TextView(context).apply {
            textSize = 9f; setTextColor(0x88FFFFFF.toInt()); letterSpacing = 0.08f
        }
        tvTitle = TextView(context).apply {
            textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tvBody = TextView(context).apply {
            textSize = 11f; setTextColor(0xAAFFFFFF.toInt())
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(tvApp); textCol.addView(tvTitle); textCol.addView(tvBody)
        notifRoot.addView(iconFrame); notifRoot.addView(textCol)
        pill.addView(notifRoot)

        // ── CALL LAYOUT ──────────────────────────────────────────────────
        callRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f; visibility = GONE
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        callIconView = TextView(context).apply {
            text = "📞"; textSize = 22f; gravity = Gravity.CENTER
            val s = dp(38f)
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(8f) }
        }
        callTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        callName = TextView(context).apply {
            textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        callStatus = TextView(context).apply {
            text = "来电"; textSize = 10f; setTextColor(0xFF30D158.toInt())
        }
        callTextCol.addView(callName); callTextCol.addView(callStatus)

        val btnAccept = buildCallBtn("✓", 0xFF30D158.toInt())
        val btnDecline = buildCallBtn("✕", 0xFFFF453A.toInt())
        btnAccept.setOnClickListener { currentPendingIntent?.send(); collapse() }
        btnDecline.setOnClickListener { collapse() }

        callAccept = btnAccept; callDecline = btnDecline
        callRoot.addView(callIconView); callRoot.addView(callTextCol)
        callRoot.addView(btnDecline); callRoot.addView(buildSpacer(dp(6f))); callRoot.addView(btnAccept)
        pill.addView(callRoot)

        // ── MUSIC LAYOUT ─────────────────────────────────────────────────
        musicRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f; visibility = GONE
            setPadding(dp(10f), dp(6f), dp(12f), dp(6f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        musicIconView = TextView(context).apply {
            text = "🎵"; textSize = 22f; gravity = Gravity.CENTER
            val s = dp(38f)
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(8f) }
        }
        musicTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvSong = TextView(context).apply {
            textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tvArtist = TextView(context).apply {
            textSize = 10f; setTextColor(0x88FFFFFF.toInt())
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        musicTextCol.addView(tvSong); musicTextCol.addView(tvArtist)
        waveView = WaveView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30f), dp(22f)).also { it.marginStart = dp(8f) }
        }
        musicRoot.addView(musicIconView); musicRoot.addView(musicTextCol); musicRoot.addView(waveView)
        pill.addView(musicRoot)

        // ── STACK DOTS ────────────────────────────────────────────────────
        stackDots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(6f)
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(4f) }
        }
        pill.addView(stackDots)

        // ── GESTURES ──────────────────────────────────────────────────────
        setupGestures()
    }

    private fun buildCallBtn(label: String, color: Int): TextView {
        return TextView(context).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            val s = dp(32f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
    }

    private fun buildSpacer(w: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun setupGestures() {
        var startY = 0f
        var startX = 0f

        pill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; startX = event.rawX; false }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - startY
                    val dx = event.rawX - startX
                    when {
                        dy < -dp(30f).toFloat() && isExpanded -> {
                            // Swipe up: open current app
                            currentPendingIntent?.send(); collapse(); true
                        }
                        dy > dp(30f).toFloat() && isExpanded -> {
                            // Swipe down: dismiss
                            collapse(); true
                        }
                        Math.abs(dx) < dp(15f) && Math.abs(dy) < dp(15f) -> {
                            // Tap
                            if (isExpanded) {
                                if (queue.size > 1) cycleNext()
                                else { currentPendingIntent?.send(); collapse() }
                            }
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }
    }

    // ── PUBLIC ────────────────────────────────────────────────────────────

    fun updatePrefs(p: IslandPrefs) {
        prefs = p
        pillW = dp(p.pillWidthDp.toFloat())
        pillH = dp(p.pillHeightDp.toFloat())
        if (!isExpanded) {
            pill.layoutParams.width  = pillW
            pill.layoutParams.height = pillH
            pill.requestLayout()
        }
    }

    fun showNotification(
        pkg: String, appName: String, title: String, text: String,
        icon: Drawable?, isCall: Boolean, isMusic: Boolean,
        pendingIntent: PendingIntent?
    ) {
        handler.post {
            val item = NotifItem(pkg, appName, title, text, icon, isCall, isMusic, pendingIntent)

            // Replace existing same-pkg item or enqueue
            val idx = queue.indexOfFirst { it.pkg == pkg }
            if (idx >= 0) queue[idx] = item else queue.addLast(item)

            autoCollapseJob?.let { handler.removeCallbacks(it) }

            currentIndex = queue.size - 1
            showItem(queue[currentIndex])

            autoCollapseJob = Runnable { collapse() }.also {
                handler.postDelayed(it, if (isCall) 30_000L else 6_000L)
            }
        }
    }

    // ── PRIVATE ───────────────────────────────────────────────────────────

    private fun showItem(item: NotifItem) {
        currentPendingIntent = item.pendingIntent
        currentPkg = item.pkg
        val accent = accentFor(item.pkg)

        when {
            item.isCall  -> showCall(item, accent)
            item.isMusic -> showMusic(item, accent)
            else         -> showNotif(item, accent)
        }
        updateStackDots()
    }

    private fun showNotif(item: NotifItem, accent: Int) {
        // App icon
        if (item.icon != null) {
            appIconView.setImageDrawable(item.icon)
            appIconView.alpha = 1f; appIconFallback.alpha = 0f
        } else {
            appIconFallback.text = iconEmojiFor(item.pkg)
            appIconView.alpha = 0f; appIconFallback.alpha = 1f
        }
        tvApp.text = item.appName.uppercase()
        tvTitle.text = item.title
        tvBody.text = item.text
        activateLayout(notifRoot, accent)
    }

    private fun showCall(item: NotifItem, accent: Int) {
        callName.text = item.title
        activateLayout(callRoot, 0xFF0A84FF.toInt())
    }

    private fun showMusic(item: NotifItem, accent: Int) {
        tvSong.text = item.title
        tvArtist.text = item.appName
        waveView.start()
        activateLayout(musicRoot, 0xFF1DB954.toInt())
    }

    private fun activateLayout(activeRoot: LinearLayout, accent: Int) {
        // Hide all, show active
        listOf(notifRoot, callRoot, musicRoot).forEach {
            if (it != activeRoot) { it.alpha = 0f; it.visibility = GONE }
        }
        activeRoot.visibility = VISIBLE

        if (isExpanded) {
            pill.background = pillBg(accent)
            activeRoot.animate().alpha(0f).setDuration(80).withEndAction {
                activeRoot.animate().alpha(1f).setDuration(160).start()
            }.start()
            pill.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80)
                .withEndAction { pill.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start()
        } else {
            expand(accent, activeRoot)
        }
    }

    private fun expand(accent: Int, activeRoot: LinearLayout) {
        isExpanded = true
        pill.background = pillBg(accent)

        val screenW = context.resources.displayMetrics.widthPixels
        val targetW = (screenW * prefs.widthPercent / 100f).toInt()
        val targetH = dp(prefs.expandedHeightDp.toFloat())

        val wAnim = ValueAnimator.ofInt(pillW, targetW).apply {
            duration = 420; interpolator = OvershootInterpolator(1.3f)
            addUpdateListener { pill.layoutParams.width  = it.animatedValue as Int; pill.requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(pillH, targetH).apply {
            duration = 420; interpolator = OvershootInterpolator(1.3f)
            addUpdateListener { pill.layoutParams.height = it.animatedValue as Int; pill.requestLayout() }
        }
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
        activeRoot.alpha = 0f
        activeRoot.animate().alpha(1f).setStartDelay(220).setDuration(200).start()
        stackDots.animate().alpha(1f).setStartDelay(300).setDuration(200).start()
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        waveView.stop()
        queue.clear()

        listOf(notifRoot, callRoot, musicRoot, stackDots).forEach {
            it.animate().alpha(0f).setDuration(130).start()
        }

        val wAnim = ValueAnimator.ofInt(pill.layoutParams.width, pillW).apply {
            duration = 360; interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener { pill.layoutParams.width  = it.animatedValue as Int; pill.requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(pill.layoutParams.height, pillH).apply {
            duration = 360; interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                pill.layoutParams.height = it.animatedValue as Int; pill.requestLayout()
                if (it.animatedFraction > 0.9f) {
                    pill.background = pillBg()
                    listOf(notifRoot, callRoot, musicRoot).forEach { v -> v.visibility = GONE }
                }
            }
        }
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
    }

    private fun cycleNext() {
        currentIndex = (currentIndex + 1) % queue.size
        val item = queue[currentIndex]
        autoCollapseJob?.let { handler.removeCallbacks(it) }
        showItem(item)
        autoCollapseJob = Runnable { collapse() }.also { handler.postDelayed(it, 6000) }
    }

    private fun updateStackDots() {
        stackDots.removeAllViews()
        if (queue.size <= 1) { stackDots.alpha = 0f; return }
        queue.forEachIndexed { i, _ ->
            val dot = View(context).apply {
                val s = dp(4f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == currentIndex) Color.WHITE else 0x55FFFFFF.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(3f) }
            }
            stackDots.addView(dot)
        }
    }

    private fun accentFor(pkg: String) = when {
        NotificationService.MUSIC_PKGS.contains(pkg)                -> 0xFF1DB954.toInt()
        NotificationService.CALL_PKGS.contains(pkg)                  -> 0xFF0A84FF.toInt()
        pkg.contains("mm")                                           -> 0xFF07C160.toInt()
        pkg.contains("qq")                                           -> 0xFF12B7F5.toInt()
        pkg.contains("rimet") || pkg.contains("lark")                -> 0xFF3370FF.toInt()
        else                                                         -> 0xFFBF5AF2.toInt()
    }

    private fun iconEmojiFor(pkg: String) = when {
        pkg.contains("mm")                                           -> "💬"
        pkg.contains("qq")                                           -> "🐧"
        NotificationService.CALL_PKGS.contains(pkg)                  -> "📞"
        NotificationService.MUSIC_PKGS.contains(pkg)                 -> "🎵"
        pkg.contains("rimet")                                        -> "📎"
        pkg.contains("lark")                                         -> "🪶"
        pkg.contains("map") || pkg.contains("navi")                  -> "🗺️"
        else                                                         -> "🔔"
    }

    private fun pillBg(accent: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(30f).toFloat()
        setColor(if (accent != 0) blend(0xFF0A0A0A.toInt(), accent, 0.12f) else 0xFF0A0A0A.toInt())
        if (accent != 0) setStroke(dp(1f), blend(accent, Color.TRANSPARENT, 0.25f))
    }

    private fun blend(c1: Int, c2: Int, r: Float): Int {
        val i = 1f - r
        return Color.argb(
            (Color.alpha(c1)*i + Color.alpha(c2)*r).toInt(),
            (Color.red(c1)*i   + Color.red(c2)*r).toInt(),
            (Color.green(c1)*i + Color.green(c2)*r).toInt(),
            (Color.blue(c1)*i  + Color.blue(c2)*r).toInt()
        )
    }
}

// ── Animated waveform for music ───────────────────────────────────────────────
class WaveView(context: Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1DB954.toInt() }
    private val bars = 4
    private val h = FloatArray(bars) { 0.4f }
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            for (i in h.indices) h[i] = 0.2f + Math.random().toFloat() * 0.75f
            invalidate()
            handler.postDelayed(this, 110)
        }
    }
    fun start() { running = true; handler.post(tick) }
    fun stop()  { running = false; handler.removeCallbacks(tick); for (i in h.indices) h[i] = 0.35f; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val ht = height.toFloat()
        val bw = w / (bars * 2 - 1)
        for (i in 0 until bars) {
            val bh = ht * h[i]; val l = i * bw * 2; val t = (ht - bh) / 2f
            canvas.drawRoundRect(l, t, l + bw, t + bh, bw / 2, bw / 2, paint)
        }
    }
}
