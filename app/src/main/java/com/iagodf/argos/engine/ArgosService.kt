package com.iagodf.argos.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.iagodf.argos.data.Apps
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Policy
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr

/**
 * the blocking engine. android tells it what is on screen and it gets you out
 * of reels, explore and shorts with a back press and a small toast
 */
class ArgosService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var overlay: Overlay
    private var lastAction = 0L
    private var pendingPkg: String? = null
    private val check = Runnable { pendingPkg?.let { inspect(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Lang.load(this)
        Store.init(this)
        overlay = Overlay(this)
        running = true
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg != Apps.INSTAGRAM && pkg != Apps.YOUTUBE) return
        // content changes come in bursts, read the screen once they calm down
        pendingPkg = pkg
        main.removeCallbacks(check)
        main.postDelayed(check, if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 60 else 180)
    }

    private fun inspect(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val pol = Policy.now()
        val s = Screen(root)
        when (pkg) {
            Apps.INSTAGRAM -> when {
                pol.reels && (s.igReelsViewer || s.igReelsTab) -> block("reels", tr("Reels bloqueados por Argos", "Reels blocked by Argos"))
                pol.explore && s.igExplore -> block("explore", tr("Explorar bloqueado por Argos", "Explore blocked by Argos"))
            }
            Apps.YOUTUBE -> if (pol.shorts && s.ytShorts) block("shorts", tr("Shorts bloqueados por Argos", "Shorts blocked by Argos"))
        }
    }

    private fun block(kind: String, msg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAction < 1200) return
        lastAction = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        overlay.toast(msg)
        Store.count(kind)
    }

    companion object {
        @Volatile var running = false

        fun component(ctx: Context) = ComponentName(ctx, ArgosService::class.java)

        fun enabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = component(ctx)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
