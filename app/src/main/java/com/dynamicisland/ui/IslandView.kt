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

    private val dp = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * dp).toInt()

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
        "com.kugou.android", "com.spotify.music", "com.apple.android.music"
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        pill = FrameLayout(context).apply {
            background = buildPillBg()
            elevation = dp(8f).toFloat()
            layoutParams = LayoutParams(collapsedW, collapsedH)
        }
        addView(pill)

        collapsedDot = View(context).apply {
            background = buildDotDrawable(Color.parseColor("#30D158"))
            alpha = 0f
            val size = dp(7f)
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        }
        pill.addView(collapsedDot)

        expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        tvAppName = TextView(context).apply {
            textSize = 9f
            setTextColor(Color.parseColor("#88FFFFFF"))
            letterSpacing = 0.1f
        }
        tvTitle = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        tvBody = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#99FFFFFF"))
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
            val lp = pill.layoutParams
            lp.width = collapsedW
            pill.layoutParams = lp
        }
    }

    fun showNotification(pkg: String, appName: String, title: String, body: String) {
        handler.post {
            collapseRunnable?.let { handler.removeCallbacks(it) }

            val accent = when {
                musicPackages.contains(pkg)                          -> Color.parseColor("#30D158")
                pkg.contains("dialer") || pkg.contains("phone")     -> Color.parseColor("#0A84FF")
                pkg.contains("mm") || pkg.contains("wechat")        -> Color.parseColor("#07C160")
                pkg.contains("qq")                                   -> Color.parseColor("#12B7F5")
                else                                                 -> Color.parseColor("#BF5AF2")
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

    private fun expand(accentColor: Int) {
        if (isExpanded) {
            pill.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80)
                .withEndAction { pill.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }.start()
            return
        }
        isExpanded = true
        pill.background = buildPillBg(accentColor)

        val display = context.resources.displayMetrics
        val targetW = (display.widthPixels * prefs.widthPercent / 100f).toInt()
        val targetH = dp(prefs.expandedHeightDp.toFloat())

        val wAnim = ValueAnimator.ofInt(collapsedW, targetW).apply {
            duration = 400; interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { pill.layoutParams.also { lp -> lp.width = it.animatedValue as Int; pill.layoutParams = lp } }
        }
        val hAnim = ValueAnimator.ofInt(collapsedH, targetH).apply {
            duration = 400; interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { pill.layoutParams.also { lp -> lp.height = it.animatedValue as Int; pill.layoutParams = lp } }
        }
        collapsedDot.animate().alpha(0f).setDuration(150).start()
        expandedLayout.animate().alpha(1f).setStartDelay(200).setDuration(200).start()
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        expandedLayout.animate().alpha(0f).setDuration(150).start()

        val wAnim = ValueAnimator.ofInt(pill.layoutParams.width, collapsedW).apply {
            duration = 350; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { pill.layoutParams.also { lp -> lp.width = it.animatedValue as Int; pill.layoutParams = lp } }
        }
        val hAnim = ValueAnimator.ofInt(pill.layoutParams.height, collapsedH).apply {
            duration = 350; interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                pill.layoutParams.also { lp -> lp.height = it.animatedValue as Int; pill.layoutParams = lp }
                if (it.animatedFraction > 0.8f) pill.background = buildPillBg()
            }
        }
        collapsedDot.animate().alpha(1f).setStartDelay(200).setDuration(200).start()
        AnimatorSet().apply { playTogether(wAnim, hAnim); start() }
        handler.postDelayed({ collapsedDot.animate().alpha(0f).setDuration(300).start() }, 3000)
    }

    private fun buildPillBg(accentColor: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(28f).toFloat()
        if (accentColor != 0) {
            setColor(blendColors(Color.parseColor("#0D0D0D"), accentColor, 0.08f))
            setStroke(dp(1f), blendColors(accentColor, Color.TRANSPARENT, 0.3f))
        } else {
            setColor(Color.parseColor("#0D0D0D"))
        }
    }

    private fun buildDotDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.argb(
            (Color.alpha(c1) * inv + Color.alpha(c2) * ratio).toInt(),
            (Color.red(c1)   * inv + Color.red(c2)   * ratio).toInt(),
            (Color.green(c1) * inv + Color.green(c2) * ratio).toInt(),
            (Color.blue(c1)  * inv + Color.blue(c2)  * ratio).toInt()
        )
    }
}
