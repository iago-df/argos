package com.iagodf.argos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** what can be switched off inside other apps */
enum class Block(val key: String) {
    IgReels("ig_reels"), IgExplore("ig_explore"), IgDmSingle("ig_dm"), YtShorts("yt_shorts"), Web("web");
}

enum class Kind { Focus, NoReels }

/** days is a bit mask, bit 0 = monday. start and end are minutes of the day, end can be smaller (overnight) */
data class Schedule(
    val id: Long, val name: String, val kind: Kind, val days: Int, val start: Int, val end: Int, val on: Boolean,
) {
    val allDay: Boolean get() = start == end

    fun activeAt(t: LocalDateTime): Boolean {
        if (!on) return false
        val m = t.hour * 60 + t.minute
        val dow = t.dayOfWeek.value - 1
        fun day(d: Int) = days and (1 shl ((d + 7) % 7)) != 0
        return when {
            allDay -> day(dow)
            start < end -> day(dow) && m in start until end
            // overnight: the part after midnight belongs to the day it started
            else -> (day(dow) && m >= start) || (day(dow - 1) && m < end)
        }
    }

    /** next time this schedule starts after t, null if it never does */
    fun nextStart(t: LocalDateTime): LocalDateTime? {
        if (!on || days == 0) return null
        for (i in 0..7) {
            val d = t.toLocalDate().plusDays(i.toLong())
            if (days and (1 shl (d.dayOfWeek.value - 1)) == 0) continue
            val s = d.atTime(start / 60, start % 60)
            if (s.isAfter(t)) return s
        }
        return null
    }

    /** the default schedules follow the app language, the ones you name keep your name */
    val label: String
        get() = DEFAULT_NAMES.firstOrNull { name in it.toList() }?.let { tr(it.first, it.second) } ?: name

    fun toJson() = JSONObject().put("id", id).put("name", name).put("kind", kind.name).put("days", days)
        .put("start", start).put("end", end).put("on", on)

    companion object {
        fun from(o: JSONObject) = Schedule(
            o.getLong("id"), o.getString("name"), Kind.valueOf(o.getString("kind")), o.getInt("days"),
            o.getInt("start"), o.getInt("end"), o.getBoolean("on"),
        )

        private val DEFAULT_NAMES = listOf(
            "Focus total" to "Deep focus", "Sin Reels ni Shorts" to "No Reels or Shorts", "Noche" to "Night",
        )

        const val WEEKDAYS = 0b0011111
        const val EVERY_DAY = 0b1111111
    }
}

/** a change that makes Argos softer. once you commit it waits before it applies */
data class Pending(val key: String, val applyAt: Long, val payload: String) {
    fun toJson() = JSONObject().put("key", key).put("at", applyAt).put("p", payload)

    companion object {
        fun from(o: JSONObject) = Pending(o.getString("key"), o.getLong("at"), o.optString("p"))
    }
}

data class Friction(val maxPause: Int = 8, val gray: Boolean = true, val slow: Boolean = true, val noReset: Boolean = true)

/**
 * everything is saved locally in SharedPreferences. the ui reads [rev] so it recomposes on every change,
 * the accessibility service reads the same prefs
 */
object Store {
    const val RELAX_DAYS = 7
    const val DEACTIVATE_DAYS = 30
    private const val DAY = 86_400_000L

    val rev = mutableIntStateOf(0)
    private lateinit var sp: SharedPreferences
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("argos", Context.MODE_PRIVATE)
        if (!sp.contains("installed")) firstRun(ctx)
        applyDue()
    }

    private fun bump() { rev.intValue++ }

    private fun firstRun(ctx: Context) {
        val e = sp.edit().putLong("installed", System.currentTimeMillis())
        Block.entries.forEach { e.putBoolean(it.key, it != Block.IgDmSingle) }
        e.putBoolean(Block.IgDmSingle.key, true)
        e.putString("schedules", JSONArray(defaultSchedules().map { it.toJson() }).toString())
        e.putStringSet("allowed", Apps.defaultAllowed(ctx))
        e.apply()
        streakCheck()
    }

    private fun defaultSchedules() = listOf(
        Schedule(1, tr("Focus total", "Deep focus"), Kind.Focus, Schedule.WEEKDAYS, 9 * 60, 14 * 60, false),
        Schedule(2, tr("Sin Reels ni Shorts", "No Reels or Shorts"), Kind.NoReels, Schedule.EVERY_DAY, 0, 0, true),
        Schedule(3, tr("Noche", "Night"), Kind.Focus, Schedule.EVERY_DAY, 23 * 60, 8 * 60, false),
    )

    // ---------- commitment ----------

    val committed: Boolean get() = sp.getBoolean("committed", false)

    fun commit() {
        sp.edit().putBoolean("committed", true).putLong("committed_at", System.currentTimeMillis()).apply()
        bump()
    }

    /** drops the commitment (only after a finished deactivation) */
    fun uncommit() {
        sp.edit().putBoolean("committed", false).remove("pending").apply()
        bump()
    }

    /** stricter applies now; softer waits [RELAX_DAYS] days when committed */
    private fun relax(key: String, payload: String = "", apply: () -> Unit) {
        if (!committed) { apply(); return }
        val list = pending().filter { it.key != key } + Pending(key, System.currentTimeMillis() + RELAX_DAYS * DAY, payload)
        savePending(list)
    }

    fun pending(): List<Pending> {
        val raw = sp.getString("pending", null) ?: return emptyList()
        val a = JSONArray(raw)
        return (0 until a.length()).map { Pending.from(a.getJSONObject(it)) }
    }

    fun pendingFor(key: String) = pending().firstOrNull { it.key == key }

    private fun savePending(l: List<Pending>) {
        sp.edit().putString("pending", JSONArray(l.map { it.toJson() }).toString()).apply()
        bump()
    }

    private fun dropPending(key: String) {
        if (pending().any { it.key == key }) savePending(pending().filter { it.key != key })
    }

    /** applies the soft changes whose wait is over */
    fun applyDue() {
        val now = System.currentTimeMillis()
        val (due, rest) = pending().partition { it.applyAt <= now }
        if (due.isEmpty()) return
        savePending(rest)
        due.forEach { p ->
            val k = p.key
            when {
                k.startsWith("block:") -> Block.entries.firstOrNull { it.key == k.removePrefix("block:") }
                    ?.let { sp.edit().putBoolean(it.key, false).apply() }
                k.startsWith("sched:") -> {
                    val id = k.removePrefix("sched:").toLong()
                    if (p.payload == "delete") writeSchedules(schedules().filter { it.id != id })
                    else writeSchedules(schedules().map { if (it.id == id) Schedule.from(JSONObject(p.payload)) else it })
                }
                k == "dm" -> sp.edit().putBoolean(Block.IgDmSingle.key, true).apply()
                k == "friction" -> writeFriction(fromJson(JSONObject(p.payload)))
                k == "allowed" -> sp.edit().putStringSet("allowed", p.payload.split(",").filter { it.isNotBlank() }.toSet()).apply()
            }
        }
        streakCheck()
        bump()
    }

    // ---------- blocks ----------

    fun isOn(b: Block) = sp.getBoolean(b.key, false)

    fun set(b: Block, on: Boolean) {
        // "watch only that one" is the soft option of dm reels: turning it on relaxes
        val stricter = if (b == Block.IgDmSingle) !on else on
        if (stricter) {
            dropPending("block:${b.key}")
            sp.edit().putBoolean(b.key, on).apply()
        } else if (b == Block.IgDmSingle) {
            relax("dm") { sp.edit().putBoolean(b.key, true).apply() }
        } else relax("block:${b.key}") { sp.edit().putBoolean(b.key, false).apply() }
        streakCheck()
        bump()
    }

    // ---------- schedules ----------

    fun schedules(): List<Schedule> {
        val raw = sp.getString("schedules", null) ?: return emptyList()
        val a = JSONArray(raw)
        return (0 until a.length()).map { Schedule.from(a.getJSONObject(it)) }
    }

    private fun writeSchedules(l: List<Schedule>) {
        sp.edit().putString("schedules", JSONArray(l.map { it.toJson() }).toString()).apply()
        bump()
    }

    fun saveSchedule(s: Schedule) {
        val old = schedules().firstOrNull { it.id == s.id }
        if (old == null || isStricter(s, old)) {
            dropPending("sched:${s.id}")
            writeSchedules(if (old == null) schedules() + s else schedules().map { if (it.id == s.id) s else it })
        } else relax("sched:${s.id}", s.toJson().toString()) { writeSchedules(schedules().map { if (it.id == s.id) s else it }) }
        streakCheck()
    }

    fun deleteSchedule(s: Schedule) {
        if (!s.on) { writeSchedules(schedules().filter { it.id != s.id }); return }
        relax("sched:${s.id}", "delete") { writeSchedules(schedules().filter { it.id != s.id }) }
    }

    /** a schedule is stricter when it covers at least the same minutes of the week, and more */
    private fun isStricter(new: Schedule, old: Schedule): Boolean {
        if (new.on && !old.on) return true
        if (!new.on) return false
        if (new.kind != old.kind) return new.kind == Kind.Focus
        val base = LocalDate.of(2024, 1, 1).atStartOfDay() // a monday
        var gained = false
        for (m in 0 until 7 * 24 * 60 step 15) {
            val t = base.plusMinutes(m.toLong())
            val a = new.activeAt(t)
            val b = old.activeAt(t)
            if (b && !a) return false
            if (a && !b) gained = true
        }
        return gained || new == old
    }

    fun newScheduleId() = (schedules().maxOfOrNull { it.id } ?: 0) + 1

    // ---------- focus ----------

    fun allowed(): Set<String> = sp.getStringSet("allowed", emptySet()) ?: emptySet()

    fun setAllowed(s: Set<String>) {
        val old = allowed()
        if (old.containsAll(s)) {
            dropPending("allowed"); sp.edit().putStringSet("allowed", s).apply(); bump()
        } else relax("allowed", s.joinToString(",")) { sp.edit().putStringSet("allowed", s).apply(); bump() }
    }

    val focusMinutes: Int get() = sp.getInt("focus_min", 120)
    fun setFocusMinutes(m: Int) { sp.edit().putInt("focus_min", m).apply(); bump() }

    val focusUntil: Long get() = sp.getLong("focus_until", 0)
    val focusStarted: Long get() = sp.getLong("focus_started", 0)

    fun startFocus() {
        val now = System.currentTimeMillis()
        sp.edit().putLong("focus_started", now).putLong("focus_until", now + focusMinutes * 60_000L).apply()
        bump()
    }

    /** ending a focus early is only possible before you commit */
    fun stopFocus(): Boolean {
        if (committed) return false
        sp.edit().putLong("focus_until", 0).apply(); bump(); return true
    }

    fun manualFocusOn(now: Long = System.currentTimeMillis()) = focusUntil > now

    // ---------- friction ----------

    fun friction(): Friction = Friction(
        sp.getInt("fr_pause", 8), sp.getBoolean("fr_gray", true), sp.getBoolean("fr_slow", true), sp.getBoolean("fr_noreset", true),
    )

    private fun writeFriction(f: Friction) {
        sp.edit().putInt("fr_pause", f.maxPause).putBoolean("fr_gray", f.gray).putBoolean("fr_slow", f.slow)
            .putBoolean("fr_noreset", f.noReset).apply()
        bump()
    }

    private fun Friction.toJson() = JSONObject().put("p", maxPause).put("g", gray).put("s", slow).put("n", noReset)
    private fun fromJson(o: JSONObject) = Friction(o.getInt("p"), o.getBoolean("g"), o.getBoolean("s"), o.getBoolean("n"))

    fun setFriction(f: Friction) {
        val o = friction()
        val stricter = f.maxPause >= o.maxPause && (f.gray || !o.gray) && (f.slow || !o.slow) && (f.noReset || !o.noReset)
        if (stricter) { dropPending("friction"); writeFriction(f) } else relax("friction", f.toJson().toString()) { writeFriction(f) }
    }

    /** reels watched in a row while reels are allowed, it decides the pause of the next one */
    fun frictionCount(now: Long = System.currentTimeMillis()): Int {
        val day = LocalDate.now(zone).toString()
        if (sp.getString("fr_day", "") != day) return 0
        if (!friction().noReset && now - sp.getLong("fr_last", 0) > 10 * 60_000L) return 0
        return sp.getInt("fr_count", 0)
    }

    fun frictionTick(): Int {
        val n = frictionCount() + 1
        sp.edit().putString("fr_day", LocalDate.now(zone).toString()).putInt("fr_count", n)
            .putLong("fr_last", System.currentTimeMillis()).apply()
        return n
    }

    // ---------- stats ----------

    private fun statKey(kind: String, d: LocalDate = LocalDate.now(zone)) = "st:$d:$kind"

    fun count(kind: String) {
        val k = statKey(kind)
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).putString("last_block", "$kind@${System.currentTimeMillis()}").apply()
        bump()
    }

    fun blockedToday(kind: String? = null): Int =
        if (kind != null) sp.getInt(statKey(kind), 0) else listOf("reels", "explore", "shorts", "web", "focus").sumOf { blockedToday(it) }

    /** days in a row with reels and shorts blocked all day */
    fun streakDays(): Int {
        val since = sp.getString("streak_since", null) ?: return 0
        return (LocalDate.now(zone).toEpochDay() - LocalDate.parse(since).toEpochDay()).toInt().coerceAtLeast(0)
    }

    private fun reelsAlwaysBlocked(): Boolean {
        if (isOn(Block.IgReels) && isOn(Block.YtShorts)) return true
        return schedules().any { it.on && it.kind == Kind.NoReels && it.allDay && it.days == Schedule.EVERY_DAY }
    }

    private fun streakCheck() {
        val on = reelsAlwaysBlocked()
        val has = sp.contains("streak_since")
        if (on && !has) sp.edit().putString("streak_since", LocalDate.now(zone).toString()).apply()
        if (!on && has) sp.edit().remove("streak_since").apply()
    }

    val installedAt: Long get() = sp.getLong("installed", System.currentTimeMillis())

    // ---------- deactivation ----------

    val deactRequested: Boolean get() = sp.getBoolean("deact", false)
    val deactAccum: Long get() = sp.getLong("deact_ms", 0)
    val deactNeeded: Long get() = DEACTIVATE_DAYS * DAY
    val deactReady: Boolean get() = deactRequested && deactAccum >= deactNeeded

    fun requestDeactivation() {
        sp.edit().putBoolean("deact", true).putLong("deact_ms", 0).putLong("deact_tick", -1).apply(); bump()
    }

    fun cancelDeactivation() {
        sp.edit().putBoolean("deact", false).putLong("deact_ms", 0).apply(); bump()
    }

    /**
     * called every minute by the guard. uses the monotonic clock so changing the time does nothing,
     * and the time with a block running (focus) does not count
     */
    fun deactTick(elapsed: Long, blocking: Boolean) {
        if (!deactRequested) return
        val last = sp.getLong("deact_tick", -1)
        val delta = if (last < 0 || elapsed < last) 0 else (elapsed - last).coerceAtMost(5 * 60_000L)
        val acc = deactAccum + if (blocking) 0 else delta
        sp.edit().putLong("deact_tick", elapsed).putLong("deact_ms", acc).apply()
        bump()
    }

    fun finishDeactivation() {
        sp.edit().putBoolean("deact", false).putLong("deact_ms", 0).putLong("focus_until", 0).apply()
        Block.entries.forEach { sp.edit().putBoolean(it.key, it == Block.IgDmSingle).apply() }
        writeSchedules(schedules().map { it.copy(on = false) })
        uncommit()
    }
}
