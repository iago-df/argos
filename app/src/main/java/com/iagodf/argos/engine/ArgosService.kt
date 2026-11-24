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
    private lateinit var friction: FrictionEngine
    private var lastAction = 0L
    private var pendingPkg: String? = null
    private val check = Runnable { pendingPkg?.let { inspect(it) } }

    // a reel opened from a chat: it plays, but the next one does not
    private var lastThreadAt = -10_000L
    private var dmReelSince = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Lang.load(this)
        Store.init(this)
        overlay = Overlay(this)
        com.iagodf.argos.lock.Guard.start(this)
        friction = FrictionEngine(this, overlay)
        running = true
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && focusCheck(pkg)) return
        if (pkg in SETTINGS && Store.committed) { schedule(pkg); return }
        val feedApp = pkg == Apps.INSTAGRAM || pkg == Apps.YOUTUBE
        if (!feedApp && pkg != packageName && e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) friction.inReels(false)
        if (feedApp && e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && friction.active) friction.swiped()
        if (!feedApp && pkg !in Apps.browsers) return
        if (pkg == Apps.INSTAGRAM && e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && dmReelSince > 0 &&
            SystemClock.elapsedRealtime() - dmReelSince > 1500
        ) {
            dmReelSince = 0
            block("reels", tr("Solo este. Ni uno más.", "Only that one. No more."))
            return
        }
        schedule(pkg, e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    /** content changes come in bursts, read the screen once they calm down */
    private fun schedule(pkg: String, fast: Boolean = true) {
        pendingPkg = pkg
        main.removeCallbacks(check)
        main.postDelayed(check, if (fast) 60 else 180)
    }

    private fun inspect(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val s = Screen(root)
        if (pkg in SETTINGS) {
            if (Store.committed && s.dangerForArgos) {
                lastAction = 0
                block("guard", tr("Argos está bloqueado. Desactívalo desde la app.", "Argos is locked. Deactivate it from the app."))
            }
            return
        }
        val pol = Policy.now()
        if (pkg == Apps.INSTAGRAM && !s.igReelsViewer && !s.igDirectThread) dmReelSince = 0
        // reels that are allowed right now get friction
        friction.inReels(
            (pkg == Apps.INSTAGRAM && s.igReelsViewer && !pol.reels && dmReelSince == 0L) ||
                (pkg == Apps.YOUTUBE && s.ytShorts && !pol.shorts)
        )
        when (pkg) {
            Apps.INSTAGRAM -> when {
                s.igDirectThread -> { lastThreadAt = SystemClock.elapsedRealtime(); dmReelSince = 0 }
                s.igReelsViewer && !s.igReelsTab && fromChat(pol.dmSingle) -> Unit
                pol.reels && (s.igReelsViewer || s.igReelsTab) -> block("reels", tr("Reels bloqueados por Argos", "Reels blocked by Argos"))
                pol.explore && s.igExplore -> block("explore", tr("Explorar bloqueado por Argos", "Explore blocked by Argos"))
            }
            in Apps.browsers -> {
                val url = s.url
                if (pol.web && url != null && WEB.containsMatchIn(url)) block("web", tr("Bloqueado en la web por Argos", "Blocked on the web by Argos"))
            }
            Apps.YOUTUBE -> if (pol.shorts && s.ytShorts) block("shorts", tr("Shorts bloqueados por Argos", "Shorts blocked by Argos"))
        }
    }

    private var launchable: Set<String> = emptySet()
    private var launchableAt = 0L

    /** during focus, any real app that is not allowed goes back home */
    private fun focusCheck(pkg: String): Boolean {
        if (pkg == packageName) return false
        val pol = Policy.now()
        if (!pol.focus) return false
        val now = SystemClock.elapsedRealtime()
        if (now - launchableAt > 5 * 60_000L || launchable.isEmpty()) {
            launchable = Apps.launchable(this).map { it.pkg }.toSet(); launchableAt = now
        }
        if (pkg !in launchable || pkg in Store.allowed() || pkg in Apps.alwaysAllowed(this)) return false
        if (now - lastAction < 800) return true
        lastAction = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlay.toast(tr("Focus: solo lo esencial", "Focus: only the essentials"))
        Store.count("focus")
        return true
    }

    /** true while the reel on screen was opened from a dm and that is allowed */
    private fun fromChat(allowed: Boolean): Boolean {
        if (!allowed) return false
        val now = SystemClock.elapsedRealtime()
        if (dmReelSince == 0L && now - lastThreadAt < 5000) dmReelSince = now
        return dmReelSince > 0
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

        private val SETTINGS = setOf(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.miui.securitycenter", "com.coloros.safecenter",
        )

        /** the endless feeds on the web */
        val WEB = Regex("""(^|[./])(youtube\.com/shorts|instagram\.com/(reels?|explore)|tiktok\.com|facebook\.com/reel)""")

        fun component(ctx: Context) = ComponentName(ctx, ArgosService::class.java)

        fun enabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = component(ctx)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
