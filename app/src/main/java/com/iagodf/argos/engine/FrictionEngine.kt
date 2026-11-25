package com.iagodf.argos.engine

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import com.iagodf.argos.data.Friction
import com.iagodf.argos.data.Store
import kotlin.math.ceil

/**
 * when reels are allowed, every reel costs a bit more to leave. nothing pops up: if you swipe too
 * early, or not enough times, the swipe does not take and you are back on the reel you were on.
 * optionally the screen slowly loses its colour
 */
class FrictionEngine(private val svc: AccessibilityService, private val ov: Overlay) {
    private val main = Handler(Looper.getMainLooper())
    private var inReels = false
    private var gray: View? = null
    private var daltonizerOn = false
    private var tries = 0

    val active: Boolean get() = inReels

    fun inReels(on: Boolean) {
        if (on == inReels) return
        inReels = on
        tries = 0
        if (on) paint(Store.frictionCount()) else {
            ov.remove(gray); gray = null
            setMono(false)
        }
    }

    /** the first reel you open counts as one */
    fun entered() {
        if (!inReels) return
        paint(Store.frictionTick())
    }

    /**
     * a swipe reached the next reel after [stayedMs] on the last one. true lets it through (and counts it),
     * false means it has to go back
     */
    fun next(stayedMs: Long): Boolean {
        if (!inReels) return true
        val f = Store.friction()
        val n = Store.frictionCount()
        if (stayedMs < waitMs(f, n) || tries < extraSwipes(f, n)) { tries++; return false }
        tries = 0
        paint(Store.frictionTick())
        return true
    }

    /** the pause grows with every reel and reaches the slider value at reel 20. 0 is off */
    private fun waitMs(f: Friction, n: Int): Long =
        if (f.maxPause <= 0 || n <= 0) 0 else (ceil(f.maxPause * n.coerceAtMost(20) / 20.0) * 1000).toLong()

    /** slower swiping: from reel 5 the next one takes one more swipe, from reel 10 two, from 15 three */
    private fun extraSwipes(f: Friction, n: Int): Int = if (!f.slow) 0 else (n / 5).coerceAtMost(3)

    /** grey veil that grows with each reel, and real black and white if Argos may change that setting */
    private fun paint(n: Int) {
        if (!Store.friction().gray) return
        val alpha = (n * 9).coerceAtMost(150)
        main.post {
            if (!inReels) return@post
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
}
