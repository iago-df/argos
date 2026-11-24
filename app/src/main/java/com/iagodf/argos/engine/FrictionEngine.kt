package com.iagodf.argos.engine

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import kotlin.math.ceil

/**
 * when reels are allowed, every reel costs a bit more: a pause that grows,
 * the screen losing colour and swipes that take longer
 */
class FrictionEngine(private val svc: AccessibilityService, private val ov: Overlay) {
    private val main = Handler(Looper.getMainLooper())
    private var inReels = false
    private var gray: View? = null
    private var pause: View? = null
    private var lastSwipe = 0L
    private var daltonizerOn = false

    val active: Boolean get() = inReels

    fun inReels(on: Boolean) {
        if (on == inReels) return
        inReels = on
        if (on) paint(Store.frictionCount()) else {
            ov.remove(gray); gray = null
            ov.remove(pause); pause = null
            setMono(false)
        }
    }

    /** a new reel came in */
    fun swiped() {
        if (!inReels) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSwipe < 700 || pause != null) return
        lastSwipe = now
        val n = Store.frictionTick()
        paint(n)
        val f = Store.friction()
        var secs = if (n <= 1) 0.0 else ceil(f.maxPause * n / 20.0).coerceIn(1.0, f.maxPause.toDouble())
        if (f.slow) secs += (n * .12).coerceAtMost(3.0)
        if (secs >= .5) showPause(secs.toInt().coerceAtLeast(1), n)
    }

    /** grey veil that grows with each reel, and real black and white if Argos may change that setting */
    private fun paint(n: Int) {
        if (!Store.friction().gray) return
        val alpha = (n * 9).coerceAtMost(150)
        main.post {
            if (gray == null && alpha > 0) {
                val v = View(svc)
                if (ov.add(v, ov.params(false, true))) gray = v
            }
            gray?.setBackgroundColor(Color.argb(alpha, 128, 128, 132))
        }
        if (n >= 10) setMono(true)
    }

    private fun setMono(on: Boolean) {
        if (on == daltonizerOn) return
        if (svc.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val cr = svc.contentResolver
            Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", if (on) 1 else 0)
            if (on) Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
            daltonizerOn = on
        }
    }

    private fun showPause(secs: Int, n: Int) = main.post {
        val big = TextView(svc).apply {
            setTextColor(Color.rgb(21, 19, 29)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 54f)
            typeface = Typeface.create(ov.inter, 650, false); gravity = Gravity.CENTER
        }
        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = ov.px(30f).toFloat(); setColor(Color.WHITE) }
            setPadding(ov.px(34f), ov.px(26f), ov.px(34f), ov.px(26f))
            elevation = ov.px(20f).toFloat()
            addView(TextView(svc).apply {
                text = tr("Pausa", "Pause"); setTextColor(Color.rgb(141, 137, 160)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(ov.inter, 500, false); gravity = Gravity.CENTER
            })
            addView(big)
            addView(TextView(svc).apply {
                text = tr("Reel $n de hoy", "Reel $n today"); setTextColor(Color.rgb(74, 92, 255)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create(ov.inter, 600, false); gravity = Gravity.CENTER
            })
        }
        val root = FrameLayout(svc).apply {
            setBackgroundColor(Color.argb(150, 21, 19, 29))
            isClickable = true // eats the touches while it lasts
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            alpha = 0f
        }
        if (!ov.add(root, ov.params(true, true))) return@post
        pause = root
        ov.fade(root, 1f, 200)
        var left = secs
        val tick = object : Runnable {
            override fun run() {
                if (pause != root) return
                if (left <= 0) {
                    ov.remove(root); pause = null; return
                }
                big.text = "${left}s"
                left--
                main.postDelayed(this, 1000)
            }
        }
        tick.run()
    }
}
