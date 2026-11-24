package com.iagodf.argos.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.PathParser
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** what a cover keeps you out of */
enum class Feed(val key: String) {
    Reels("reels"), Shorts("shorts"), Explore("explore");

    val toast: String
        get() = when (this) {
            Reels -> tr("Reels bloqueados", "Reels blocked")
            Shorts -> tr("Shorts bloqueados", "Shorts blocked")
            Explore -> tr("Explorar bloqueado", "Explore blocked")
        }
}

private val LOCK = listOf(
    "M8 10.5h8a3 3 0 0 1 3 3v4.5a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-4.5a3 3 0 0 1 3 -3z",
    "M8.2 10.5V7.8a3.8 3.8 0 0 1 7.6 0v2.7", "M12 14.5v2.5",
).map { PathParser.createPathFromPathData(it) }

/**
 * the lock badge of the video: a dark round button with a white ring and a white lock.
 * on a short chip it becomes a pill with the label next to the lock
 */
class LockBadge(ctx: Context, private val label: String? = null, private val maxSize: Float = 52f) : View(ctx) {
    private val dp = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    var pill = false

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun typeface(t: Typeface?) { text.typeface = Typeface.create(t, 600, false) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (pill && label != null) {
            val r = RectF(1.5f * dp, 1.5f * dp, w - 1.5f * dp, h - 1.5f * dp)
            gradient(r)
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, ring)
            r.inset(1.5f * dp, 1.5f * dp)
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
            val icon = r.height() * .52f
            text.textSize = r.height() * .4f
            val tw = text.measureText(label)
            val x0 = r.centerX() - (icon + 5 * dp + tw) / 2
            lock(c, x0, r.centerY() - icon / 2, icon)
            c.drawText(label, x0 + icon + 5 * dp, r.centerY() - (text.ascent() + text.descent()) / 2, text)
            return
        }
        val d = min(min(w, h) * .9f, maxSize * dp)
        val cx = w / 2; val cy = h / 2; val rad = d / 2
        shadow.setShadowLayer(4 * dp, 0f, 2 * dp, Color.argb(90, 40, 30, 120))
        c.drawCircle(cx, cy, rad - 1, shadow.apply { color = Color.WHITE })
        c.drawCircle(cx, cy, rad, ring)
        val inner = rad - d * 3f / 54f
        gradient(RectF(cx - inner, cy - inner, cx + inner, cy + inner))
        c.drawCircle(cx, cy, inner, fill)
        val icon = d * 24f / 54f
        lock(c, cx - icon / 2, cy - icon / 2, icon)
    }

    /** the 145 degree gradient of the video, from #2b2a45 to #14121d */
    private fun gradient(r: RectF) {
        val a = (145 - 90) * PI / 180
        val dx = (cos(a) * r.width() / 2).toFloat(); val dy = (sin(a) * r.height() / 2).toFloat()
        fill.shader = LinearGradient(r.centerX() - dx, r.centerY() - dy, r.centerX() + dx, r.centerY() + dy,
            Color.rgb(0x2b, 0x2a, 0x45), Color.rgb(0x14, 0x12, 0x1d), Shader.TileMode.CLAMP)
    }

    private fun lock(c: Canvas, x: Float, y: Float, size: Float) {
        val s = size / 24f
        stroke.strokeWidth = 2f
        c.save(); c.translate(x, y); c.scale(s, s); LOCK.forEach { c.drawPath(it, stroke) }; c.restore()
    }

    fun pop() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        scaleX = 0f; scaleY = 0f
        animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(2.2f)).setDuration(380).start()
    }

    /** the little "no" of the video: a shake that dies out */
    fun shake() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            addUpdateListener { val p = it.animatedFraction; rotation = (sin(p * PI * 6) * 14 * (1 - p)).toFloat() }
        }.start()
        animate().scaleX(.9f).scaleY(.9f).setDuration(90).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start() }.start()
    }
}

private fun placed(r: Rect, touchable: Boolean = true) = WindowManager.LayoutParams(
    r.width(), r.height(),
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT,
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = r.left; y = r.top
    if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}

/**
 * covers drawn right on top of the buttons that lead into reels and shorts, so you never get in.
 * the engine says what should be covered, this keeps the windows in place
 */
class Shields(private val svc: AccessibilityService, private val ov: Overlay) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val dp = svc.resources.displayMetrics.density

    private class Placed(val feed: Feed, var rect: Rect, val view: View, val badge: LockBadge)

    private val tabs = ArrayList<Placed>()
    private var cover: Placed? = null
    private var shownAt = 0L
    @Volatile var any = false; private set

    fun show(tabRects: List<Pair<Rect, Feed>>, grid: Rect?) = main.post {
        syncTabs(tabRects)
        syncCover(grid)
        any = tabs.isNotEmpty() || cover != null
        if (any) shownAt = SystemClock.elapsedRealtime()
    }

    fun clear() { if (any) show(emptyList(), null) }

    private fun syncTabs(want: List<Pair<Rect, Feed>>) {
        // a badge that was just there comes back without the pop, so a quick re-read does not blink
        val fresh = SystemClock.elapsedRealtime() - shownAt > 900
        val keep = ArrayList<Placed>()
        for ((r, feed) in want) {
            val old = tabs.firstOrNull { it.feed == feed && it !in keep && Rect.intersects(it.rect, r) }
            if (old != null) {
                if (old.rect != r) { old.rect = Rect(r); runCatching { wm.updateViewLayout(old.view, placed(r)) } }
                keep.add(old)
                continue
            }
            val chip = r.height() < 40 * dp && r.width() > r.height() * 1.5f
            val badge = LockBadge(svc, if (chip) feed.name else null).apply { pill = chip; typeface(ov.inter) }
            badge.setOnTouchListener { v, e -> tapped(e, badge, feed); true }
            if (runCatching { wm.addView(badge, placed(r)) }.isSuccess) {
                if (fresh) badge.pop()
                keep.add(Placed(feed, Rect(r), badge, badge))
            }
        }
        tabs.filter { it !in keep }.forEach { runCatching { wm.removeView(it.view) } }
        tabs.clear(); tabs.addAll(keep)
    }

    private fun tapped(e: MotionEvent, badge: LockBadge, feed: Feed) {
        if (e.actionMasked != MotionEvent.ACTION_UP) return
        badge.shake()
        ov.toast(feed.toast, " · Argos")
        Store.count(feed.key)
    }

    /** the explore grid, hidden under a calm panel. the search bar above it keeps working */
    private fun syncCover(r: Rect?) {
        val c = cover
        if (r == null) { if (c != null) { runCatching { wm.removeView(c.view) }; cover = null }; return }
        if (c != null) {
            if (c.rect != r) { c.rect = Rect(r); runCatching { wm.updateViewLayout(c.view, placed(r)) } }
            return
        }
        val dark = (svc.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val badge = LockBadge(svc)
        val panel = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (dark) Color.rgb(0x0f, 0x0e, 0x15) else Color.rgb(0xf6, 0xf5, 0xfb))
            setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), (40 * dp).toInt())
            addView(badge, LinearLayout.LayoutParams((76 * dp).toInt(), (76 * dp).toInt()))
            addView(TextView(svc).apply {
                text = tr("Explorar está apagado", "Explore is off")
                setTextColor(if (dark) Color.WHITE else Color.rgb(0x15, 0x13, 0x1d))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); typeface = Typeface.create(ov.inter, 700, false)
                letterSpacing = -.02f; gravity = Gravity.CENTER
                setPadding(0, (16 * dp).toInt(), 0, (4 * dp).toInt())
            })
            addView(TextView(svc).apply {
                text = tr("Buscar sigue funcionando: usa la barra de arriba.", "Search still works: use the bar above.")
                setTextColor(Color.rgb(0x8d, 0x89, 0xa0))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.create(ov.inter, 400, false)
                gravity = Gravity.CENTER
            })
            // taps and long presses stop here: no reel opens and none plays as a preview
            setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_UP) { badge.shake(); ov.toast(Feed.Explore.toast, " · Argos") }; true }
        }
        if (runCatching { wm.addView(panel, placed(r)) }.isSuccess) {
            if (ValueAnimator.areAnimatorsEnabled()) panel.alpha = 0f
            panel.animate().alpha(1f).setDuration(160).start()
            badge.pop()
            cover = Placed(Feed.Explore, Rect(r), panel, badge)
            Store.count(Feed.Explore.key)
        }
    }
}

/**
 * a reel sent in a chat: it plays, you can tap, like, comment and go back, but you can not swipe to
 * the next one or pull down for a new one. a clear window catches the touch; taps and sideways swipes
 * are played back to instagram as they were, swipes up and down stop here
 */
class ChatGuard(private val svc: AccessibilityService, private val ov: Overlay) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val dp = svc.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(svc).scaledTouchSlop.toFloat()
    private var view: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var replaying = false
    @Volatile var on = false; private set

    fun set(want: Boolean) {
        if (want == on) return
        on = want
        main.post { if (want) add() else remove() }
    }

    private fun remove() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null; replaying = false
    }

    private fun add() {
        if (view != null) return
        val lock = LockBadge(svc).apply { alpha = 0f }
        val root = FrameLayout(svc)
        root.addView(lock, FrameLayout.LayoutParams((68 * dp).toInt(), (68 * dp).toInt(), Gravity.CENTER))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val pts = ArrayList<Pair<Float, Float>>()
        var down = 0L
        var mode = 0 // 0 not sure yet, 1 up or down (stopped), 2 tap or sideways (played back)
        root.setOnTouchListener { _, e ->
            if (replaying) { if (e.actionMasked == MotionEvent.ACTION_DOWN) bounced = true; return@setOnTouchListener true }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pts.clear(); pts.add(e.rawX to e.rawY); down = e.eventTime; mode = 0 }
                MotionEvent.ACTION_MOVE -> {
                    pts.add(e.rawX to e.rawY)
                    val dx = e.rawX - pts[0].first; val dy = e.rawY - pts[0].second
                    if (mode == 0 && abs(dy) > slop * 1.5f && abs(dy) > abs(dx)) mode = 1
                    else if (mode == 0 && abs(dx) > slop * 1.5f) mode = 2
                    if (mode == 1) {
                        lock.alpha = min(1f, abs(dy) / (60 * dp))
                        lock.translationY = dy / 2
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == 1) {
                        lock.animate().translationY(0f).alpha(0f).setStartDelay(250).setDuration(260).start()
                        lock.shake()
                        ov.toast(tr("Solo este.", "Only that one."), tr(" Ni uno más.", " No more."))
                        Store.count("reels")
                    } else replay(ArrayList(pts), e.eventTime - down, moved = mode == 2)
                }
                MotionEvent.ACTION_CANCEL -> lock.animate().alpha(0f).translationY(0f).setDuration(200).start()
            }
            true
        }
        if (runCatching { wm.addView(root, p) }.isSuccess) { view = root; lp = p }
    }

    /**
     * gives the touch to the app underneath: the window lets touches through, the gesture is played, then it
     * closes again. android applies the "let through" a moment later; if the played touch still lands here,
     * it is played again a bit later
     */
    private fun replay(pts: List<Pair<Float, Float>>, ms: Long, moved: Boolean) {
        val v = view ?: return
        val p = lp ?: return
        val path = Path()
        path.moveTo(pts[0].first.coerceAtLeast(0f), pts[0].second.coerceAtLeast(0f))
        if (moved) pts.drop(1).forEach { (x, y) -> path.lineTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms.coerceIn(1, 8000))).build()
        replaying = true
        bounced = false
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(v, p) }
        fun done() = main.postDelayed({
            if (view == v) {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                runCatching { wm.updateViewLayout(v, p) }
            }
            replaying = false
        }, 60)
        fun send(left: Int) {
            bounced = false
            val cb = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { after() }
                override fun onCancelled(d: GestureDescription?) { after() }
                fun after() { if (bounced && left > 0 && view == v) main.postDelayed({ send(left - 1) }, 70) else done() }
            }
            if (!runCatching { svc.dispatchGesture(g, cb, main) }.getOrDefault(false)) done()
        }
        main.postDelayed({ send(4) }, 50)
    }

    private var bounced = false
}
