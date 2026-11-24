package com.iagodf.argos.engine

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.iagodf.argos.R

/** the lock icon of the video drawn on a plain view (overlays can not use compose easily) */
class LockIcon(ctx: Context, private val tint: Int) : View(ctx) {
    private val paths = listOf(
        "M8 10.5h8a3 3 0 0 1 3 3v4.5a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-4.5a3 3 0 0 1 3 -3z",
        "M8.2 10.5V7.8a3.8 3.8 0 0 1 7.6 0v2.7", "M12 14.5v2.5",
    ).map { PathParser.createPathFromPathData(it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = tint
    }

    override fun onDraw(c: Canvas) {
        val s = width / 24f
        c.save(); c.scale(s, s); paths.forEach { c.drawPath(it, paint) }; c.restore()
    }
}

/** small windows drawn by the accessibility service over other apps */
class Overlay(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var toast: View? = null
    private val dp = svc.resources.displayMetrics.density
    val inter: Typeface? = runCatching { ResourcesCompat.getFont(svc, R.font.inter) }.getOrNull()

    fun px(v: Float) = (v * dp).toInt()

    fun params(touchable: Boolean, full: Boolean) = WindowManager.LayoutParams(
        if (full) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        if (full) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    /** "Reels blocked · Argos", the same dark pill as the video. the accent part goes in the soft blue */
    fun toast(text: String, accent: String? = null) = main.post {
        toast?.let { runCatching { wm.removeView(it) } }
        val pill = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(Color.argb(235, 21, 19, 29)) }
            setPadding(px(14f), px(12f), px(18f), px(12f))
            elevation = 12 * dp
            addView(LockIcon(svc, Color.rgb(159, 176, 255)), LinearLayout.LayoutParams(px(18f), px(18f)).apply { marginEnd = px(9f) })
            addView(TextView(svc).apply {
                this.text = android.text.SpannableStringBuilder(text).apply {
                    if (accent != null) append(accent, android.text.style.ForegroundColorSpan(Color.rgb(159, 176, 255)), 0)
                }
                setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(inter, 600, false)
            })
        }
        val lp = params(false, false).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = px(56f) }
        runCatching { wm.addView(pill, lp) }.onFailure { android.util.Log.w("Argos", "toast", it); return@post }
        toast = pill
        // with "remove animations" on, the pill just appears and leaves
        val moving = ValueAnimator.areAnimatorsEnabled()
        if (moving) {
            pill.alpha = 0f; pill.translationY = -30 * dp; pill.scaleX = .9f; pill.scaleY = .9f
            pill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(1.6f)).setDuration(420).start()
        }
        main.postDelayed({
            val gone = { runCatching { wm.removeView(pill) }; if (toast == pill) toast = null }
            if (moving) pill.animate().alpha(0f).translationY(-20 * dp).setDuration(300).withEndAction { gone() }.start() else gone()
        }, 2400)
    }

    fun remove(v: View?) { if (v != null) main.post { runCatching { wm.removeView(v) } } }

    fun add(v: View, lp: WindowManager.LayoutParams) = runCatching { wm.addView(v, lp) }.isSuccess

    fun fade(v: View, to: Float, ms: Long = 300) {
        ValueAnimator.ofFloat(v.alpha, to).apply { duration = ms; addUpdateListener { v.alpha = it.animatedValue as Float } }.start()
    }
}
