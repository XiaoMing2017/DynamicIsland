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
import com.dynamicisland.service.NotificationService
import com.dynamicisland.utils.IslandPrefs
import kotlin.math.abs

class IslandView(context: Context, private var prefs: IslandPrefs) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private var pillW = dp(prefs.pillWidthDp.toFloat())
    private var pillH = dp(prefs.pillHeightDp.toFloat())

    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoCollapseJob: Runnable? = null
    private var currentPendingIntent: PendingIntent? = null
    private var currentPkg: String = ""

    data class NotifItem(
        val pkg: String, val appName: String, val title: String,
        val text: String, val icon: Drawable?, val isCall: Boolean,
        val isMusic: Boolean, val pendingIntent: PendingIntent?
    )

    private val queue = ArrayDeque<NotifItem>()
    private var currentIndex = 0

    // Root pill
    private val pill: FrameLayout

    // Normal notification
    private val notifRoot: LinearLayout
    private val appIconImg: ImageView
    private val appIconEmoji: TextView
    private val tvApp: TextView
    private val tvTitle: TextView
    private val tvBody: TextView

    // Call
    private val callRoot: LinearLayout
    private val callName: TextView

    // Music
    private val musicRoot: LinearLayout
    private val tvSong: TextView
    private val tvArtist: TextView
    private val waveView: WaveView

    // Stack dots
    private val stackDots: LinearLayout

    init {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        clipChildren = false
        clipToPadding = false

        // ── PILL ──────────────────────────────────────────────────────────
        pill = FrameLayout(context).apply {
            background = pillBg()
            elevation = dp(14f).toFloat()
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(pillW, pillH)
        }
        addView(pill)

        // ── NOTIF LAYOUT ──────────────────────────────────────────────────
        notifRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
            visibility = GONE
            setPadding(dp(10f), dp(5f), dp(12f), dp(5f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val iconFrame = FrameLayout(context).apply {
            val s = dp(34f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
        }
        appIconImg = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        appIconEmoji = TextView(context).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        iconFrame.addView(appIconImg)
        iconFrame.addView(appIconEmoji)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvApp = TextView(context).apply {
            textSize = 9f
            setTextColor(0x88FFFFFF.toInt())
            letterSpacing = 0.06f
        }
        tvTitle = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        tvBody = TextView(context).apply {
            textSize = 11f
            setTextColor(0xAAFFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        textCol.addView(tvApp)
        textCol.addView(tvTitle)
        textCol.addView(tvBody)
        notifRoot.addView(iconFrame)
        notifRoot.addView(textCol)
        pill.addView(notifRoot)

        // ── CALL LAYOUT ───────────────────────────────────────────────────
        callRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
            visibility = GONE
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val callIcon = TextView(context).apply {
            text = "📞"
            textSize = 20f
            gravity = Gravity.CENTER
            val s = dp(34f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
        }
        val callTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        callName = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val callStatusTv = TextView(context).apply {
            text = "来电"
            textSize = 10f
            setTextColor(0xFF30D158.toInt())
        }
        callTextCol.addView(callName)
        callTextCol.addView(callStatusTv)

        val btnDecline = makeCallBtn("✕", 0xFFFF453A.toInt())
        val btnAccept  = makeCallBtn("✓", 0xFF30D158.toInt())
        btnDecline.setOnClickListener { collapse() }
        btnAccept.setOnClickListener  { tryOpenApp(); collapse() }

        callRoot.addView(callIcon)
        callRoot.addView(callTextCol)
        callRoot.addView(btnDecline)
        callRoot.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(6f), 1) })
        callRoot.addView(btnAccept)
        pill.addView(callRoot)

        // ── MUSIC LAYOUT ──────────────────────────────────────────────────
        musicRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
            visibility = GONE
            setPadding(dp(10f), dp(5f), dp(12f), dp(5f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val musicIconWrap = FrameLayout(context).apply {
            val s = dp(36f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(7f).toFloat()
                setColor(0xFF1A3A2A.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8f) }
        }
        val musicNote = TextView(context).apply {
            text = "🎵"
            textSize = 17f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        musicIconWrap.addView(musicNote)

        val musicTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvSong = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tvArtist = TextView(context).apply {
            textSize = 10f
            setTextColor(0x88FFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        musicTextCol.addView(tvSong)
        musicTextCol.addView(tvArtist)

        waveView = WaveView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28f), dp(20f)).apply { marginStart = dp(8f) }
        }
        musicRoot.addView(musicIconWrap)
        musicRoot.addView(musicTextCol)
        musicRoot.addView(waveView)
        pill.addView(musicRoot)

        // ── STACK DOTS ────────────────────────────────────────────────────
        stackDots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(5f)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(3f)
            }
        }
        pill.addView(stackDots)

        setupGestures()
    }

    private fun makeCallBtn(label: String, color: Int) = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        val s = dp(30f)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(s, s)
    }

    private fun setupGestures() {
        var downY = 0f
        var downX = 0f
        pill.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { downY = ev.rawY; downX = ev.rawX; true }
                MotionEvent.ACTION_UP -> {
                    val dy = ev.rawY - downY
                    val dx = ev.rawX - downX
                    when {
                        dy < -dp(25f) && isExpanded -> { tryOpenApp(); collapse(); true }
                        dy >  dp(25f) && isExpanded -> { collapse(); true }
                        abs(dx) < dp(12f) && abs(dy) < dp(12f) -> {
                            if (isExpanded) {
                                if (queue.size > 1) cycleNext()
                                else { tryOpenApp(); collapse() }
                            }
                            true
                        }
                        else -> false
                    }
                }
                else -> true
            }
        }
    }

    private fun tryOpenApp() {
        try { currentPendingIntent?.send() } catch (_: Exception) {}
    }

    // ── PUBLIC API ────────────────────────────────────────────────────────

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
        icon: Drawable?, isCall: Boolean, isMusic: Boolean, pendingIntent: PendingIntent?
    ) {
        handler.post {
            val item = NotifItem(pkg, appName, title, text, icon, isCall, isMusic, pendingIntent)
            val idx = queue.indexOfFirst { it.pkg == pkg }
            if (idx >= 0) queue[idx] = item else queue.addLast(item)

            autoCollapseJob?.let { handler.removeCallbacks(it) }
            currentIndex = queue.size - 1
            showItem(queue[currentIndex])

            val delay = if (isCall) 30_000L else 6_000L
            autoCollapseJob = Runnable { collapse() }.also { handler.postDelayed(it, delay) }
        }
    }

    // ── PRIVATE ───────────────────────────────────────────────────────────

    private fun showItem(item: NotifItem) {
        currentPendingIntent = item.pendingIntent
        currentPkg = item.pkg
        val accent = accentFor(item.pkg)
        when {
            item.isCall  -> showCall(item)
            item.isMusic -> showMusic(item, accent)
            else         -> showNotif(item, accent)
        }
        updateStackDots()
    }

    private fun showNotif(item: NotifItem, accent: Int) {
        if (item.icon != null) {
            appIconImg.setImageDrawable(item.icon)
            appIconImg.alpha = 1f; appIconEmoji.alpha = 0f
        } else {
            appIconEmoji.text = iconEmojiFor(item.pkg)
            appIconImg.alpha = 0f; appIconEmoji.alpha = 1f
        }
        tvApp.text   = item.appName.uppercase()
        tvTitle.text = item.title
        tvBody.text  = item.text
        activateLayout(notifRoot, accent)
    }

    private fun showCall(item: NotifItem) {
        callName.text = item.title
        activateLayout(callRoot, 0xFF0A84FF.toInt())
    }

    private fun showMusic(item: NotifItem, accent: Int) {
        tvSong.text   = item.title
        tvArtist.text = item.appName
        waveView.start()
        activateLayout(musicRoot, 0xFF1DB954.toInt())
    }

    private fun activateLayout(target: LinearLayout, accent: Int) {
        listOf(notifRoot, callRoot, musicRoot).forEach {
            if (it != target) { it.alpha = 0f; it.visibility = GONE }
        }
        target.visibility = VISIBLE

        if (isExpanded) {
            pill.background = pillBg(accent)
            target.animate().alpha(0f).setDuration(80).withEndAction {
                target.animate().alpha(1f).setDuration(160).start()
            }.start()
            pill.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80)
                .withEndAction { pill.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start()
        } else {
            expand(accent, target)
        }
    }

    private fun expand(accent: Int, target: LinearLayout) {
        isExpanded = true
        pill.background = pillBg(accent)
        val screenW = context.resources.displayMetrics.widthPixels
        val targetW = (screenW * prefs.widthPercent / 100f).toInt()
        val targetH = dp(prefs.expandedHeightDp.toFloat())

        AnimatorSet().apply {
            playTogether(
                ofInt(pillW, targetW) { pill.layoutParams.width  = it; pill.requestLayout() }.spring(),
                ofInt(pillH, targetH) { pill.layoutParams.height = it; pill.requestLayout() }.spring()
            )
            start()
        }
        target.alpha = 0f
        target.animate().alpha(1f).setStartDelay(220).setDuration(200).start()
        stackDots.animate().alpha(1f).setStartDelay(300).setDuration(200).start()
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        waveView.stop()
        queue.clear()

        listOf(notifRoot, callRoot, musicRoot, stackDots).forEach {
            it.animate().alpha(0f).setDuration(120).start()
        }
        val fromW = pill.layoutParams.width
        val fromH = pill.layoutParams.height
        AnimatorSet().apply {
            playTogether(
                ofInt(fromW, pillW) { pill.layoutParams.width  = it; pill.requestLayout() }.decel(),
                ofInt(fromH, pillH) { v ->
                    pill.layoutParams.height = v; pill.requestLayout()
                    if (v <= pillH + dp(4f)) {
                        pill.background = pillBg()
                        listOf(notifRoot, callRoot, musicRoot).forEach { it.visibility = GONE }
                    }
                }.decel()
            )
            start()
        }
    }

    private fun cycleNext() {
        if (queue.isEmpty()) return
        currentIndex = (currentIndex + 1) % queue.size
        autoCollapseJob?.let { handler.removeCallbacks(it) }
        showItem(queue[currentIndex])
        autoCollapseJob = Runnable { collapse() }.also { handler.postDelayed(it, 6000) }
    }

    private fun updateStackDots() {
        stackDots.removeAllViews()
        if (queue.size <= 1) { stackDots.alpha = 0f; return }
        repeat(queue.size) { i ->
            stackDots.addView(View(context).apply {
                val s = dp(4f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == currentIndex) Color.WHITE else 0x55FFFFFF.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(3f) }
            })
        }
    }

    private fun ofInt(from: Int, to: Int, update: (Int) -> Unit) =
        ValueAnimator.ofInt(from, to).apply { addUpdateListener { update(it.animatedValue as Int) } }

    private fun ValueAnimator.spring() = apply { duration = 430; interpolator = OvershootInterpolator(1.3f) }
    private fun ValueAnimator.decel()  = apply { duration = 370; interpolator = DecelerateInterpolator(2.2f) }

    private fun accentFor(pkg: String) = when {
        NotificationService.MUSIC_PKGS.contains(pkg)             -> 0xFF1DB954.toInt()
        NotificationService.CALL_PKGS.contains(pkg)              -> 0xFF0A84FF.toInt()
        pkg.contains("mm")                                       -> 0xFF07C160.toInt()
        pkg.contains("qq")                                       -> 0xFF12B7F5.toInt()
        pkg.contains("rimet") || pkg.contains("lark")            -> 0xFF3370FF.toInt()
        else                                                     -> 0xFFBF5AF2.toInt()
    }

    private fun iconEmojiFor(pkg: String) = when {
        pkg.contains("mm")                              -> "💬"
        pkg.contains("qq")                              -> "🐧"
        NotificationService.CALL_PKGS.contains(pkg)    -> "📞"
        NotificationService.MUSIC_PKGS.contains(pkg)   -> "🎵"
        pkg.contains("rimet")                           -> "📎"
        pkg.contains("lark")                            -> "🪶"
        pkg.contains("map") || pkg.contains("navi")    -> "🗺"
        else                                            -> "🔔"
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

class WaveView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1DB954.toInt() }
    private val barHeights = FloatArray(4) { 0.35f }
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            for (i in barHeights.indices) barHeights[i] = 0.2f + Math.random().toFloat() * 0.75f
            invalidate()
            handler.postDelayed(this, 110)
        }
    }
    fun start() { if (running) return; running = true; handler.post(tick) }
    fun stop()  { running = false; handler.removeCallbacks(tick); for (i in barHeights.indices) barHeights[i] = 0.35f; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val bw = w / (barHeights.size * 2 - 1)
        barHeights.forEachIndexed { i, bh ->
            val barH = h * bh; val l = i * bw * 2; val t = (h - barH) / 2f
            canvas.drawRoundRect(l, t, l + bw, t + barH, bw / 2, bw / 2, paint)
        }
    }
}
