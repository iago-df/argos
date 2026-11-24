package com.iagodf.argos.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.iagodf.argos.data.Apps
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Policy
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr

/**
 * the blocking engine. android tells it what is on screen and it covers the way into reels,
 * explore and shorts with a lock. if a reel opens anyway, it gets you out with a back press
 */
class ArgosService : AccessibilityService() {
    /*
     * everything runs on its own thread. the service lives in the app process, so reading a window
     * of Argos itself from the main thread would wait on the main thread forever
     */
    private val thread = android.os.HandlerThread("argos-engine").apply { start() }
    private val work = Handler(thread.looper)
    private lateinit var overlay: Overlay
    private lateinit var friction: FrictionEngine
    private lateinit var shields: Shields
    private lateinit var chat: ChatGuard
    private var lastAction = 0L
    @Volatile private var pendingPkg: String? = null
    private var queued = false
    private var lastCheck = 0L
    private val check = Runnable {
        queued = false
        lastCheck = SystemClock.elapsedRealtime()
        pendingPkg?.let { safe { inspect(it) } }
    }

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
        shields = Shields(this, overlay)
        chat = ChatGuard(this, overlay)
        running = true
    }

    override fun onDestroy() {
        running = false
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val type = e.eventType
        work.post { safe { handle(pkg, type) } }
    }

    /** an exception in an accessibility service makes android switch it off for good, so nothing escapes */
    private inline fun safe(block: () -> Unit) {
        try { block() } catch (t: Throwable) { android.util.Log.w("Argos", "engine", t) }
    }

    private fun handle(pkg: String, type: Int) {
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && focusCheck(pkg)) return
        if (pkg in SETTINGS && Store.committed) { schedule(pkg); return }
        val feedApp = pkg == Apps.INSTAGRAM || pkg == Apps.YOUTUBE
        val web = !feedApp && Apps.isBrowser(this, pkg)
        if (!feedApp && pkg != packageName && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) friction.inReels(false)
        // another app came on top (or the notification shade): the covers go away with the app they cover
        if (!feedApp && !web && pkg != packageName && (shields.any || chat.on) && topApp() != shieldsFor) {
            shields.clear(); chat.set(false)
        }
        if (feedApp && type == AccessibilityEvent.TYPE_VIEW_SCROLLED && friction.active) friction.swiped()
        if (!feedApp && !web) return
        schedule(pkg, type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    private var shieldsFor: String? = null

    /** the app on top, under argos' own windows and the small system bars. null while the shade or a dialog covers it */
    private fun topApp(): String? {
        val all = windows.sortedByDescending { it.layer }
        val h = all.maxOfOrNull { w -> Rect().also { w.getBoundsInScreen(it) }.height() } ?: 0
        for (w in all) {
            when (w.type) {
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> continue
                AccessibilityWindowInfo.TYPE_SYSTEM -> {
                    val r = Rect().also { w.getBoundsInScreen(it) }
                    if (r.height() < h * .3f) continue
                    return null
                }
                AccessibilityWindowInfo.TYPE_APPLICATION -> return w.root?.packageName?.toString()
                else -> return null
            }
        }
        return null
    }

    private fun imeOpen() = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    /** the policy changes at most by the minute, so it is not worked out again for every event */
    private var pol: Policy? = null
    private var polAt = 0L
    private fun policy(): Policy {
        val now = SystemClock.elapsedRealtime()
        val p = pol
        if (p != null && now - polAt < 1000) return p
        return Policy.now().also { pol = it; polAt = now }
    }

    /**
     * content changes come in bursts and, while a video plays, they never stop. so the screen is read
     * at most every 250 ms instead of waiting for calm (that wait never ended on a playing reel)
     */
    private fun schedule(pkg: String, fast: Boolean = true) {
        pendingPkg = pkg
        if (queued) return
        queued = true
        val since = SystemClock.elapsedRealtime() - lastCheck
        work.postDelayed(check, if (fast) 40 else (250 - since).coerceIn(0, 250))
    }

    /** the window of that app. the active one can be the keyboard or a system bar on real phones */
    private fun rootOf(pkg: String): android.view.accessibility.AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it }
        return windows.asSequence()
            .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() == pkg }
    }

    private fun inspect(pkg: String) {
        val root = rootOf(pkg) ?: return
        val s = Screen(root)
        if (pkg in SETTINGS) {
            if (Store.committed && s.dangerForArgos) {
                lastAction = 0
                block("guard", tr("Argos está bloqueado. Desactívalo desde la app.", "Argos is locked. Deactivate it from the app."))
            }
            return
        }
        val pol = policy()
        if (topApp() != pkg) {
            // the app is not really in front (shade, dialog, split screen): nothing to cover
            if (shieldsFor == pkg) { shields.clear(); chat.set(false) }
            return
        }
        shieldsFor = pkg
        when (pkg) {
            Apps.INSTAGRAM -> instagram(s, pol)
            Apps.YOUTUBE -> {
                friction.inReels(s.ytShorts && !pol.shorts)
                chat.set(false)
                shields.show(if (pol.shorts && !s.ytShorts) s.entryPoints(setOf("Shorts")).map { it to Feed.Shorts } else emptyList(), null)
                if (pol.shorts && s.ytShorts) block("shorts", Feed.Shorts.toast)
            }
            else -> {
                chat.set(false)
                // the reels and shorts tabs of the mobile sites, at the bottom
                val tabs = if (!pol.web) emptyList() else
                    s.entryPoints(setOf("Shorts"), bottomOnly = true).map { it to Feed.Shorts } +
                        s.entryPoints(setOf("Reels"), bottomOnly = true).map { it to Feed.Reels }
                shields.show(tabs, null)
                val url = s.url
                if (pol.web && url != null && WEB.containsMatchIn(url)) block("web", tr("Bloqueado en la web", "Blocked on the web"))
            }
        }
    }

    private fun instagram(s: Screen, pol: Policy) {
        val now = SystemClock.elapsedRealtime()
        val viewer = s.igReelsViewer
        if (s.igDirectThread && !viewer) lastThreadAt = now
        if (!viewer) dmReelSince = 0
        // a reel opened from a chat, while that is allowed
        val fromChat = viewer && pol.dmSingle && (dmReelSince > 0 || now - lastThreadAt < 5000)
        if (fromChat && dmReelSince == 0L) dmReelSince = now
        friction.inReels(viewer && !pol.reels && !fromChat)

        val tabs = if (pol.reels && !viewer) s.entryPoints(setOf("Reels"), setOf("clips_tab")).map { it to Feed.Reels } else emptyList()
        val onSearch = "search_tab" in s.selected || s.selectedDescs.any { it.startsWith("Search", true) || it.startsWith("Buscar", true) } ||
            s.hasPart("action_bar_search") || s.hasPart("explore")
        val grid = if (pol.explore && onSearch && !viewer) s.exploreGrid() else null
        shields.show(tabs, grid)

        // the comments, the share sheet or the keyboard need their swipes, so the guard steps aside for them
        val sheet = s.hasPart("bottom_sheet") || s.hasPart("comment") || s.hasPart("share_sheet") || imeOpen()
        chat.set(fromChat && pol.reels && !sheet)

        // the fallback: a reel that opened some other way (a link, the feed)
        if (!fromChat && pol.reels && (viewer || s.igReelsTab)) block("reels", Feed.Reels.toast)
    }

    private var launchable: Set<String> = emptySet()
    private var launchableAt = 0L

    /** during focus, any real app that is not allowed goes back home */
    private fun focusCheck(pkg: String): Boolean {
        if (pkg == packageName) return false
        val pol = policy()
        if (!pol.focus) return false
        val now = SystemClock.elapsedRealtime()
        if (now - launchableAt > 5 * 60_000L || launchable.isEmpty()) {
            launchable = Apps.launchable(this).map { it.pkg }.toSet(); launchableAt = now
        }
        if (pkg !in launchable || pkg in Store.allowed() || pkg in Apps.alwaysAllowed(this)) return false
        if (now - lastAction < 800) return true
        lastAction = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        shields.clear(); chat.set(false)
        overlay.toast(tr("Focus: solo lo esencial", "Focus: only the essentials"))
        Store.count("focus")
        return true
    }

    private fun block(kind: String, msg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAction < 1200) return
        lastAction = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        overlay.toast(msg, " · Argos")
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
