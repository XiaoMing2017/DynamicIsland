package com.dynamicisland.ui

import android.animation.*
import android.content.Context
import android.graphics.*
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
    private fun sp(v: Float) = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics).toInt()

    private var pillW  = dp(prefs.pillWidthDp.toFloat())
    private var pillH  = dp(prefs.pillHeightDp.toFloat())
    private var expdH  = dp(prefs.expandedHeightDp.toFloat())

    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoCollapseRunnable: Runnable? = null

    // ── views ──────────────────────────────────────────────────────────────
    private val pill: FrameLayout

    // Collapsed: just the pill shape (with optional tiny dot)
    private val dotView: View

    // Expanded: icon + text
    private val expdRoot: LinearLayout
    private val iconView: TextView   // emoji / letter icon
    private val textCol: LinearLayout
    private val tvApp: TextView
    private val tvTitle: TextView
    private val tvBody: TextView

    // Music expanded: icon + song info + waveform bars
    private val musicRoot: LinearLayout
    private val tvSongTitle: TextView
    private val tvArtist: TextView
    private val waveView: WaveView

    private val musicPkgs = setOf(
        "com.netease.cloudmusic","com.tencent.qqmusic",
        "com.kugou.android","com.spotify.music","com.apple.android.music",
        "com.miui.player","com.google.android.apps.youtube.music"
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        clipChildren = false; clipToPadding = false

        // ── PILL ──────────────────────────────────────────────────────────
        pill = FrameLayout(context).apply {
            background = pillBg()
            elevation = dp(12f).toFloat()
            clipChildren = false; clipToPadding = false
            layoutParams = LayoutParams(pillW, pillH)
        }
        addView(pill)

        // ── COLLAPSED DOT ─────────────────────────────────────────────────
        dotView = View(context).apply {
            val s = dp(7f)
            background = GradientDrawable().also { it.shape = GradientDrawable.OVAL; it.setColor(0xFF30D158.toInt()) }
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER }
        }
        pill.addView(dotView)

        // ── EXPANDED: notification style ──────────────────────────────────
        expdRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f; visibility = GONE
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        iconView = TextView(context).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            val s = dp(38f)
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(10f) }
        }
        textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
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
        expdRoot.addView(iconView); expdRoot.addView(textCol)
        pill.addView(expdRoot)

        // ── EXPANDED: music style ─────────────────────────────────────────
        musicRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f; visibility = GONE
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        val albumArt = FrameLayout(context).apply {
            val s = dp(40f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8f).toFloat()
                setColor(0xFF1DB954.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(10f) }
        }
        val noteIcon = TextView(context).apply {
            text = "🎵"; textSize = 18f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        albumArt.addView(noteIcon)

        val musicText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvSongTitle = TextView(context).apply {
            textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tvArtist = TextView(context).apply {
            textSize = 10f; setTextColor(0x88FFFFFF.toInt())
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        musicText.addView(tvSongTitle); musicText.addView(tvArtist)

        waveView = WaveView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32f), dp(24f)).also { it.marginStart = dp(8f) }
        }

        musicRoot.addView(albumArt); musicRoot.addView(musicText); musicRoot.addView(waveView)
        pill.addView(musicRoot)

        // tap to collapse
        pill.setOnClickListener { if (isExpanded) collapse() }
    }

    // ── PUBLIC ─────────────────────────────────────────────────────────────
    fun updatePrefs(p: IslandPrefs) {
        prefs = p
        pillW = dp(p.pillWidthDp.toFloat())
        pillH = dp(p.pillHeightDp.toFloat())
        expdH = dp(p.expandedHeightDp.toFloat())
        if (!isExpanded) { pill.layoutParams.width = pillW; pill.layoutParams.height = pillH; pill.requestLayout() }
    }

    fun showNotification(pkg: String, appName: String, title: String, body: String) {
        handler.post {
            autoCollapseRunnable?.let { handler.removeCallbacks(it) }
            val isMusic = musicPkgs.contains(pkg)
            val accent  = accentFor(pkg)

            if (isMusic) showMusic(title, appName, accent)
            else         showNotif(pkg, appName, title, body, accent)

            autoCollapseRunnable = Runnable { collapse() }.also { handler.postDelayed(it, 5000) }
        }
    }

    // ── PRIVATE ────────────────────────────────────────────────────────────
    private fun showNotif(pkg: String, appName: String, title: String, body: String, accent: Int) {
        iconView.text = iconFor(pkg)
        tvApp.text   = appName.uppercase()
        tvTitle.text = title
        tvBody.text  = body
        expdRoot.visibility = VISIBLE
        musicRoot.visibility = GONE
        expandTo(accent, false)
    }

    private fun showMusic(songTitle: String, artist: String, accent: Int) {
        tvSongTitle.text = songTitle
        tvArtist.text    = artist
        waveView.start()
        musicRoot.visibility = VISIBLE
        expdRoot.visibility  = GONE
        expandTo(accent, true)
    }

    private fun expandTo(accent: Int, isMusic: Boolean) {
        pill.background = pillBg(accent)
        val screenW = context.resources.displayMetrics.widthPixels
        val targetW = (screenW * prefs.widthPercent / 100f).toInt()
        val targetH = dp(prefs.expandedHeightDp.toFloat())

        if (isExpanded) {
            // Already open — pulse and refresh content
            pill.animate().scaleX(1.06f).scaleY(1.06f).setDuration(80)
                .withEndAction { pill.animate().scaleX(1f).scaleY(1f).setDuration(130).start() }.start()
            val activeView = if (isMusic) musicRoot else expdRoot
            activeView.animate().alpha(0f).setDuration(80).withEndAction {
                activeView.animate().alpha(1f).setDuration(150).start()
            }.start()
            return
        }
        isExpanded = true

        dotView.animate().alpha(0f).setDuration(100).start()
        val activeView = if (isMusic) musicRoot else expdRoot

        val wAnim = ValueAnimator.ofInt(pillW, targetW).apply {
            duration = 440; interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { pill.layoutParams.width  = it.animatedValue as Int; pill.requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(pillH, targetH).apply {
            duration = 440; interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { pill.layoutParams.height = it.animatedValue as Int; pill.requestLayout() }
        }
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
        activeView.alpha = 0f
        activeView.animate().alpha(1f).setStartDelay(230).setDuration(200).start()
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        waveView.stop()

        expdRoot.animate().alpha(0f).setDuration(120).start()
        musicRoot.animate().alpha(0f).setDuration(120).start()

        val wAnim = ValueAnimator.ofInt(pill.layoutParams.width, pillW).apply {
            duration = 370; interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener { pill.layoutParams.width  = it.animatedValue as Int; pill.requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(pill.layoutParams.height, pillH).apply {
            duration = 370; interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                pill.layoutParams.height = it.animatedValue as Int; pill.requestLayout()
                if (it.animatedFraction > 0.9f) {
                    pill.background = pillBg()
                    expdRoot.visibility = GONE; musicRoot.visibility = GONE
                }
            }
        }
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
        dotView.animate().alpha(1f).setStartDelay(200).setDuration(250).start()
        handler.postDelayed({ dotView.animate().alpha(0f).setDuration(500).start() }, 3500)
    }

    private fun accentFor(pkg: String) = when {
        musicPkgs.contains(pkg)                          -> 0xFF1DB954.toInt()
        pkg.contains("dialer") || pkg.contains("phone")  -> 0xFF0A84FF.toInt()
        pkg.contains("mm")                               -> 0xFF07C160.toInt()
        pkg.contains("qq")                               -> 0xFF12B7F5.toInt()
        pkg.contains("rimet") || pkg.contains("lark")    -> 0xFF3370FF.toInt()
        else                                             -> 0xFFBF5AF2.toInt()
    }

    private fun iconFor(pkg: String) = when {
        pkg.contains("mm")                               -> "💬"
        pkg.contains("qq")                               -> "🐧"
        pkg.contains("dialer") || pkg.contains("phone")  -> "📞"
        pkg.contains("rimet")                            -> "📎"
        pkg.contains("lark")                             -> "🪶"
        pkg.contains("map") || pkg.contains("navi")      -> "🗺️"
        else                                             -> "🔔"
    }

    private fun pillBg(accent: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(30f).toFloat()
        setColor(if (accent != 0) blend(0xFF0A0A0A.toInt(), accent, 0.10f) else 0xFF0A0A0A.toInt())
        if (accent != 0) setStroke(dp(1f), blend(accent, Color.TRANSPARENT, 0.3f))
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

// ── Animated waveform bars for music ─────────────────────────────────────────
class WaveView(context: Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1DB954.toInt() }
    private val bars = 4
    private val heights = FloatArray(bars) { 0.3f + Math.random().toFloat() * 0.5f }
    private var animating = false
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!animating) return
            for (i in heights.indices) heights[i] = 0.2f + Math.random().toFloat() * 0.8f
            invalidate()
            handler.postDelayed(this, 120)
        }
    }

    fun start() { animating = true; handler.post(tick) }
    fun stop()  { animating = false; handler.removeCallbacks(tick); for (i in heights.indices) heights[i] = 0.3f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val bw = w / (bars * 2 - 1)
        for (i in 0 until bars) {
            val bh = h * heights[i]
            val left  = i * bw * 2
            val top   = (h - bh) / 2f
            canvas.drawRoundRect(left, top, left + bw, top + bh, bw / 2, bw / 2, paint)
        }
    }
}
