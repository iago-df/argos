package com.iagodf.argos.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * a recording of what instagram and youtube show to argos (ids, labels, short texts) and of the events
 * it gets. instagram changes its screens often; with this file the checks can be fixed for a real phone.
 * off by default, kept in the app's own folder, and only leaves the phone if you share it
 */
object Diag {
    @Volatile var on = false; private set
    private var last = ""
    private var lastAt = 0L
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)

    fun load(ctx: Context) { on = prefs(ctx).getBoolean("on", false) }

    fun set(ctx: Context, v: Boolean) {
        if (v) file(ctx).delete()
        on = v
        prefs(ctx).edit().putBoolean("on", v).apply()
    }

    fun file(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "argos-screens.txt")

    fun size(ctx: Context): Long = file(ctx).takeIf { it.exists() }?.length() ?: 0

    @Synchronized
    fun line(ctx: Context, s: String) {
        if (!on) return
        runCatching {
            val f = file(ctx)
            if (f.length() < 4_000_000) f.appendText("${LocalTime.now().format(clock)} $s\n")
        }
    }

    /** the whole screen, again only when it changed or every few seconds while it stays the same */
    fun screen(ctx: Context, pkg: String, s: Screen) {
        if (!on) return
        val key = pkg + s.ids.sorted().joinToString(",") + s.allText.take(6).joinToString("|")
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt < 600 || (key == last && now - lastAt < 4000)) return
        last = key; lastAt = now
        val b = StringBuilder("=== screen $pkg, ${s.nodes.size} nodes\n")
        for (n in s.nodes) {
            b.append("  ".repeat(n.depth.coerceAtMost(40)))
            b.append(n.cls.substringAfterLast('.'))
            n.id?.let { b.append(" #").append(it) }
            n.text?.let { b.append(" t=\"").append(it.replace('\n', ' ').take(50)).append('"') }
            n.desc?.let { b.append(" d=\"").append(it.replace('\n', ' ').take(50)).append('"') }
            if (n.clickable) b.append(" C")
            if (n.selected) b.append(" SEL")
            if (n.scrollable) b.append(" SCROLL")
            if (n.editable) b.append(" EDIT")
            b.append(" [").append(n.rect.left).append(',').append(n.rect.top).append(' ')
                .append(n.rect.width()).append('x').append(n.rect.height()).append("]\n")
        }
        line(ctx, b.toString())
    }

    fun share(ctx: Context) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file(ctx))
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "argos-screens.txt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
