package com.iagodf.argos.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "time recovered today": how long you used to spend on the endless feeds before Argos,
 * minus what you spent today. read from android's usage stats
 */
object Usage {
    private val feeds = setOf(
        Apps.INSTAGRAM, Apps.YOUTUBE, "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.facebook.katana", "com.snapchat.android",
    )

    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** foreground time of the feed apps between two moments, from the raw events */
    private fun feedTime(ctx: Context, from: Long, to: Long): Long {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(from, to)
        val open = HashMap<String, Long>()
        var total = 0L
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.packageName !in feeds) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(e.packageName)?.let { total += e.timeStamp - it }
            }
        }
        open.values.forEach { total += to - it }
        return total
    }

    /** average daily feed time in the 7 days before Argos was installed */
    fun baseline(ctx: Context): Long {
        val inst = java.time.Instant.ofEpochMilli(Store.installedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val days = (1..7).map { inst.minusDays(it.toLong()) }
        val totals = days.map { d -> feedTime(ctx, ms(d.atStartOfDay()), ms(d.plusDays(1).atStartOfDay())) }
        val used = totals.filter { it > 0 }
        return if (used.isEmpty()) 0 else used.sum() / used.size
    }

    fun today(ctx: Context): Long {
        val start = ms(LocalDate.now().atStartOfDay())
        return feedTime(ctx, start, System.currentTimeMillis())
    }

    /** minutes recovered so far today */
    fun recoveredMinutes(ctx: Context): Int {
        val fallback = Store.blockedToday() * 4
        if (!granted(ctx)) return fallback
        val base = runCatching { baseline(ctx) }.getOrDefault(0L)
        if (base <= 0) return fallback
        // the old habit is spread over the waking hours, 8:00 to 24:00
        val now = LocalDateTime.now()
        val awake = ((now.hour * 60 + now.minute - 8 * 60).coerceIn(0, 16 * 60)) / (16 * 60f)
        val rec = base * awake - runCatching { today(ctx) }.getOrDefault(0L)
        return (rec / 60_000).toInt().coerceAtLeast(fallback.coerceAtMost(30))
    }
}

fun fmtMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
