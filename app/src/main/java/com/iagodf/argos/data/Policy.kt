package com.iagodf.argos.data

import java.time.LocalDateTime

/** what is blocked right now, mixing the toggles, a manual focus and the schedules */
data class Policy(
    val reels: Boolean,
    val explore: Boolean,
    val dmSingle: Boolean,
    val shorts: Boolean,
    val web: Boolean,
    val focus: Boolean,
    val focusUntil: Long,
    val bySchedule: Schedule?,
) {
    /** reels are allowed somewhere, so friction applies there */
    val reelsAllowed: Boolean get() = !reels || !shorts

    companion object {
        fun now(t: LocalDateTime = LocalDateTime.now(), ms: Long = System.currentTimeMillis()): Policy {
            Store.applyDue()
            val active = Store.schedules().filter { it.activeAt(t) }
            val noReels = active.any { it.kind == Kind.NoReels }
            val focusSched = active.firstOrNull { it.kind == Kind.Focus }
            val manual = Store.manualFocusOn(ms)
            return Policy(
                reels = Store.isOn(Block.IgReels) || noReels,
                explore = Store.isOn(Block.IgExplore) || noReels,
                dmSingle = Store.isOn(Block.IgDmSingle),
                shorts = Store.isOn(Block.YtShorts) || noReels,
                web = Store.isOn(Block.Web) || noReels,
                focus = manual || focusSched != null,
                focusUntil = if (manual) Store.focusUntil else 0,
                bySchedule = focusSched ?: active.firstOrNull(),
            )
        }

        /** the next schedule that will start, for the home card */
        fun next(t: LocalDateTime = LocalDateTime.now()): Pair<Schedule, LocalDateTime>? =
            Store.schedules().filter { !it.allDay && !it.activeAt(t) }.mapNotNull { s -> s.nextStart(t)?.let { s to it } }.minByOrNull { it.second }
    }
}
