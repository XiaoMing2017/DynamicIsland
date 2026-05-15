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

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    // Sizes from prefs
    private var collapsedW = dp(prefs.pillWidthDp.toFloat())
    private val collapsedH = dp(32f)
    private var expandedH  = dp(prefs.expandedHeightDp.toFloat())

    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private var collapseRunnable: Runnable? = null

    private val pill: FrameLayout
    private val collapsedDot: View
    private val expandedLayout: LinearLayout
    private val tvAppName: TextView
    private val tvTitle: TextView
    private val tvBody: TextView

    private val musicPackages = setOf(
        "com.netease.cloudmusic", "com.tencent.qqmusic",
        "com.kugou.android", "com.spotify.music"
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        // Pill
        pill = FrameLayout(context).apply {
            background = buildPillBg()
            elevation = dp(8f).toFloat()
            layoutParams = LayoutParams(collapsedW, collapsedH)
        }
        addView(pill)

        // Active dot
        collapsedDot = View(context).apply {
            background = buildDot(Color.parseColor("#30D158"))
            alpha = 0f
            val s = dp(7f)
            layoutParams = FrameLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER }
        }
        pill.addView(collapsedDot)

        // Expanded content
        expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        tvAppName = TextView(context).apply {
            textSize = 9f
            setTextColor(Color.parseColor("#88FFFFFF"))
            letterSpacing = 0.08f
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
            setTextColor(Color.parseColor("#AAFFFFFF"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        expandedLayout.addView(tvAppName)
        expandedLayout.addView(tvTitle)
        expandedLayout.addView(tvBody)
        pill.addView(expandedLayout)

        pill.setOnClickListener { if (isExpanded) collapse() }
    }

    fun updatePrefs(newPrefs: IslandPrefs) {
        prefs = newPrefs
        collapsedW = dp(prefs.pillWidthDp.toFloat())
        expandedH  = dp(prefs.expandedHeightDp.toFloat())
        if (!isExpanded) {
            pill.layoutParams.width = collapsedW
            pill.requestLayout()
        }
    }

    fun showNotification(pkg: String, appName: String, title: String, body: String) {
        handler.post {
            collapseRunnable?.let { handler.removeCallbacks(it) }

            val accent = when {
                musicPackages.contains(pkg)                      -> Color.parseColor("#30D158")
                pkg.contains("dialer") || pkg.contains("phone") -> Color.parseColor("#0A84FF")
                pkg.contains("mm")                              -> Color.parseColor("#07C160")
                pkg.contains("qq")                              -> Color.parseColor("#12B7F5")
                else                                            -> Color.parseColor("#BF5AF2")
            }

            (collapsedDot.background as GradientDrawable).setColor(accent)
            tvAppName.text = appName.uppercase()
            tvTitle.text = title
            tvBody.text = body

            expand(accent)

            collapseRunnable = Runnable { collapse() }.also {
                handler.postDelayed(it, 5000)
            }
        }
    }

    private fun expand(accent: Int) {
        if (isExpanded) {
            // Pulse if already expanded
            pill.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80)
                .withEndAction { pill.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                .start()
            return
        }
        isExpanded = true
        pill.background = buildPillBg(accent)

        val screenW = context.resources.displayMetrics.widthPixels
        val targetW = (screenW * prefs.widthPercent / 100f).toInt()
        val targetH = dp(prefs.expandedHeightDp.toFloat())

        val wAnim = ValueAnimator.ofInt(collapsedW, targetW).apply {
            duration = 420; interpolator = OvershootInterpolator(1.3f)
            addUpdateListener { pill.layoutParams.width  = it.animatedValue as Int; pill.requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(collapsedH, targetH).apply {
            duration = 420; interpolator = OvershootInterpolator(1.3f)
            addUpdateListener { pill.layoutParams.height = it.animatedValue as Int; pill.requestLayout() }
        }
        collapsedDot.animate().alpha(0f).setDuration(150).start()
        expandedLayout.animate().alpha(1f).setStartDelay(220).setDuration(200).start()
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        expandedLayout.animate().alpha(0f).setDuration(150).start()

        val wAnim = ValueAnimator.ofInt(pill.layoutParams.width, collapsedW).apply {
            duration = 360; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { pill.layoutParams.width  = it.animatedValue as Int; pill.requestLayout() }
        }
        val hAnim = ValueAnimator.ofInt(pill.layoutParams.height, collapsedH).apply {
            duration = 360; interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                pill.layoutParams.height = it.animatedValue as Int; pill.requestLayout()
                if (it.animatedFraction > 0.85f) pill.background = buildPillBg()
            }
        }
        collapsedDot.animate().alpha(1f).setStartDelay(180).setDuration(220).start()
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
        handler.postDelayed({ collapsedDot.animate().alpha(0f).setDuration(400).start() }, 3500)
    }

    private fun buildPillBg(accent: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(28f).toFloat()
        if (accent != 0) {
            setColor(blend(Color.parseColor("#0D0D0D"), accent, 0.1f))
            setStroke(dp(1f), blend(accent, Color.TRANSPARENT, 0.25f))
        } else {
            setColor(Color.parseColor("#0D0D0D"))
        }
    }

    private fun buildDot(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
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
