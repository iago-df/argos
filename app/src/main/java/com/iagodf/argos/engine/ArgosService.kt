package com.iagodf.argos.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Rect
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.iagodf.argos.data.Apps
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Policy
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr

/**
 * the blocking engine. android tells it what is on screen and what you tapped. nothing is drawn on
 * top of the apps: a tap on the reels tab takes you back to the tab you were on, a reel that opens
 * anyway is left with a back press, and a swipe that should not happen is undone
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
    private val tracker = ReelTracker()
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
    private var chatSince = 0L
    private var chatOrigin: Set<String>? = null

    /** the bottom tab you were on in each app, to go back to it from reels or shorts */
    private val lastTab = HashMap<String, Pair<String?, String?>>()
    /** instagram is on the explore grid (or the search results) */
    private var onExplore = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Lang.load(this)
        Store.init(this)
        Diag.load(this)
        overlay = Overlay(this)
        com.iagodf.argos.lock.Guard.start(this)
        friction = FrictionEngine(this, overlay)
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
        val feedApp = pkg == Apps.INSTAGRAM || pkg == Apps.YOUTUBE
        // taps are read on the engine thread, so a copy is kept (the event itself is reused by android)
        @Suppress("DEPRECATION")
        val copy = if (feedApp && (type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)) AccessibilityEvent.obtain(e) else null
        val note = if (Diag.on && (feedApp || Apps.isBrowser(this, pkg))) describe(e) else null
        work.post { safe { handle(pkg, type, copy, note) } }
    }

    private fun describe(e: AccessibilityEvent) = buildString {
        append(AccessibilityEvent.eventTypeToString(e.eventType)).append(' ').append(e.packageName)
        e.className?.let { append(" cls=").append(it) }
        e.contentDescription?.let { append(" d=\"").append(it.toString().take(50)).append('"') }
        if (e.text.isNotEmpty()) append(" t=\"").append(e.text.joinToString(" ").take(50)).append('"')
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            append(" from=").append(e.fromIndex).append(" to=").append(e.toIndex).append(" n=").append(e.itemCount)
            append(" dy=").append(e.scrollDeltaY).append(" y=").append(e.scrollY)
        }
    }

    /** an exception in an accessibility service makes android switch it off for good, so nothing escapes */
    private inline fun safe(block: () -> Unit) {
        try { block() } catch (t: Throwable) { android.util.Log.w("Argos", "engine", t) }
    }

    private fun handle(pkg: String, type: Int, tap: AccessibilityEvent?, note: String?) {
        if (note != null) Diag.line(this, note)
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && (lockCheck(pkg) || focusCheck(pkg))) return
        if (pkg in SETTINGS && Store.committed) { schedule(pkg); return }
        val feedApp = pkg == Apps.INSTAGRAM || pkg == Apps.YOUTUBE
        val web = !feedApp && Apps.isBrowser(this, pkg)
        if (!feedApp && pkg != packageName && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { friction.inReels(false); tracker.reset() }
        if (tap != null) tapped(pkg, type, tap)
        if (!feedApp && !web) return
        schedule(pkg, type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_VIEW_CLICKED)
    }

    /**
     * a tap on the reels or shorts tab: the app is sent straight back to the tab you were on, so the
     * feed never gets going. a long press on a reel in explore (the preview) is closed right away
     */
    private fun tapped(pkg: String, type: Int, e: AccessibilityEvent) {
        val src = runCatching { e.source }.getOrNull()
        val id = src?.viewIdResourceName?.substringAfter(":id/")
        val label = (e.contentDescription ?: src?.contentDescription ?: e.text.firstOrNull() ?: src?.text)?.toString()?.trim()
        val r = Rect().also { src?.getBoundsInScreen(it) }
        val h = resources.displayMetrics.heightPixels
        val bottomBar = r.top > h * .8f
        val pol = policy()
        val now = SystemClock.elapsedRealtime()
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val reels = pkg == Apps.INSTAGRAM && pol.reels && (id == "clips_tab" || (bottomBar && label.equals("Reels", true)))
            val shorts = pkg == Apps.YOUTUBE && pol.shorts && bottomBar && label.equals("Shorts", true)
            if (reels || shorts) leaveTab(pkg, if (reels) Feed.Reels else Feed.Shorts)
        } else if (pkg == Apps.INSTAGRAM && onExplore && (pol.reels || pol.explore) && src?.isEditable != true && !bottomBar) {
            lastAction = now
            performGlobalAction(GLOBAL_ACTION_BACK)
            overlay.toast(Feed.Explore.toast, " · Argos")
            Store.count("explore")
        }
    }

    private fun leaveTab(pkg: String, feed: Feed) {
        lastAction = SystemClock.elapsedRealtime()
        overlay.toast(feed.toast, " · Argos")
        Store.count(feed.key)
        val (id, label) = lastTab[pkg] ?: (null to null)
        val root = rootOf(pkg)
        val node = root?.let { r ->
            id?.let { r.findAccessibilityNodeInfosByViewId("$pkg:id/$it").firstOrNull() } ?: Screen(r).tab(id, label)
        }
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) performGlobalAction(GLOBAL_ACTION_BACK)
    }

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
    private fun rootOf(pkg: String): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it }
        return windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() == pkg }
    }

    private fun inspect(pkg: String) {
        val root = rootOf(pkg) ?: return
        val s = Screen(root)
        Diag.screen(this, pkg, s)
        if (pkg in SETTINGS) {
            if (Store.committed && s.dangerForArgos) {
                lastAction = 0
                block("guard", tr("Argos está bloqueado. Desactívalo desde la app.", "Argos is locked. Deactivate it from the app."))
            }
            return
        }
        // the shade, a dialog or split screen on top: nothing to do until the app is back
        if (topApp() != pkg) return
        val pol = policy()
        s.selectedTab()?.let { t ->
            val l = s.label(t)
            if (t.id != "clips_tab" && !l.equals("Reels", true) && !l.equals("Shorts", true)) lastTab[pkg] = t.id to l
        }
        when (pkg) {
            Apps.INSTAGRAM -> instagram(s, pol)
            Apps.YOUTUBE -> {
                if (!s.ytShorts) { tracker.reset(); friction.inReels(false); return }
                if (pol.shorts) { block("shorts", Feed.Shorts.toast); return }
                friction.inReels(true)
                follow(pkg, s, s.ytPlayer, chat = false)
            }
            else -> {
                val url = s.url ?: return
                if (pol.web && WEB.containsMatchIn(url)) { block("web", tr("Bloqueado en la web", "Blocked on the web")); return }
                // while locked, the words and sites you listed: in the address, a search or what you are typing
                if (Store.committed) {
                    val u = runCatching { java.net.URLDecoder.decode(url.replace("+", " "), "UTF-8") }.getOrDefault(url).lowercase()
                    if (Store.lockWords().any { u.contains(it) }) block("web", tr("Bloqueado mientras Argos está bloqueado", "Blocked while Argos is locked"))
                }
            }
        }
    }

    private fun instagram(s: Screen, pol: Policy) {
        val now = SystemClock.elapsedRealtime()
        val viewer = s.igReelsViewer
        val thread = s.igDirectThread
        onExplore = !viewer && !thread && (
            "search_tab" in s.selected || s.selectedDescs.any { it.startsWith("Search", true) || it.startsWith("Buscar", true) } ||
                s.hasPart("action_bar_search") || s.hasPart("explore")
            )
        if (thread && !viewer) lastThreadAt = now
        if (!viewer) {
            chatSince = 0; chatOrigin = null
            tracker.reset(); friction.inReels(false)
            if (pol.reels && s.igReelsTab) block("reels", Feed.Reels.toast)
            return
        }
        // a reel opened from a chat, while that is allowed: only that one
        val fromChat = pol.reels && pol.dmSingle && (chatSince > 0 || now - lastThreadAt < 5000)
        if (fromChat && chatSince == 0L) chatSince = now
        // the fallback: a reel that opened some other way (a link, the feed, explore)
        if (pol.reels && !fromChat) { block("reels", Feed.Reels.toast); return }
        friction.inReels(!pol.reels)
        follow(Apps.INSTAGRAM, s, s.igPlayer, chat = fromChat)
    }

    /**
     * follows the reels one by one. a swipe that only goes halfway, or scrolling the comments, is not a
     * new reel. when one really arrives: from a chat it goes back to the reel you were sent, with friction
     * it goes back if you did not stay long enough (or did not swipe enough times)
     */
    private fun follow(pkg: String, s: Screen, player: List<String>, chat: Boolean) {
        // the comments, the share sheet or the keyboard are on top of the reel: nothing changes reel there
        if (s.hasPart("bottom_sheet") || s.hasPart("comment") || s.hasPart("share_sheet") || imeOpen()) return
        val now = SystemClock.elapsedRealtime()
        val sig = s.reelSignature(player)
        val wasEmpty = tracker.first
        when (tracker.feed(sig, now)) {
            ReelTracker.Change.None -> {
                if (wasEmpty && !tracker.first) { if (chat) chatOrigin = sig else friction.entered() }
                if (tracker.settling) work.postDelayed({ schedule(pkg) }, ReelTracker.HOLD + 40)
            }
            ReelTracker.Change.Back -> Unit
            ReelTracker.Change.New -> if (chat) {
                bounce(s, player, orLeave = true)
                overlay.toast(tr("Solo este.", "Only that one."), tr(" Ni uno más.", " No more."))
                Store.count("reels")
                // a pull to refresh can not be undone: if the reel you were sent is not back, leave
                work.postDelayed({
                    safe {
                        val o = chatOrigin
                        if (o != null && !tracker.isOn(o)) { lastAction = SystemClock.elapsedRealtime(); performGlobalAction(GLOBAL_ACTION_BACK) }
                    }
                }, 1300)
            } else if (!friction.next(now - tracker.previousSince)) bounce(s, player, orLeave = false)
        }
    }

    /** moves the reels back by one, as if you had swiped down */
    private fun bounce(s: Screen, player: List<String>, orLeave: Boolean) {
        val p = s.pager(player)
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val done = p != null && runCatching {
            (p.actionList.any { it.id == up } && p.performAction(up)) || p.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }.getOrDefault(false)
        if (done) return
        if (orLeave) { lastAction = SystemClock.elapsedRealtime(); performGlobalAction(GLOBAL_ACTION_BACK); return }
        val w = s.bounds.width().toFloat(); val h = s.bounds.height().toFloat()
        val path = android.graphics.Path().apply { moveTo(w / 2, s.bounds.top + h * .35f); lineTo(w / 2, s.bounds.top + h * .75f) }
        runCatching { dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 180)).build(), null, null) }
    }

    private var essentials: Set<String> = emptySet()
    private var essentialsAt = 0L

    /** an app paused by the lock (when Argos is not device owner it can not be paused, so it is sent home) */
    private fun lockCheck(pkg: String): Boolean {
        if (pkg == packageName || !Store.committed || pkg !in Store.lockApps()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - essentialsAt > 5 * 60_000L || essentials.isEmpty()) { essentials = Apps.essentials(this); essentialsAt = now }
        if (pkg in essentials) return false
        if (now - lastAction < 800) return true
        lastAction = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlay.toast(tr("Pausada mientras Argos está bloqueado", "Paused while Argos is locked"))
        Store.count("apps")
        return true
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
