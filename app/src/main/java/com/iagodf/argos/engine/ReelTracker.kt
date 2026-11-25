package com.iagodf.argos.engine

/**
 * knows which reel is on screen and when it really changed.
 *
 * a reel is told apart by its signature (author, caption, audio, see [Screen.reelSignature]).
 * a new signature only counts once it holds still for a moment: halfway through a swipe both reels
 * are on screen, and a swipe that does not get there ends on the same reel it started from
 */
class ReelTracker {
    enum class Change { None, New, Back }

    private var current: Set<String>? = null
    private var previous: Set<String>? = null
    private var candidate: Set<String>? = null
    private var candidateAt = 0L

    /** when the reel on screen arrived, and the one before it */
    var since = 0L; private set
    var previousSince = 0L; private set

    /** a change is waiting to hold still, read the screen again soon */
    val settling: Boolean get() = candidate != null

    val first: Boolean get() = current == null

    fun reset() { current = null; previous = null; candidate = null }

    fun isOn(sig: Set<String>?) = sig != null && current?.let { alike(it, sig) } == true

    fun feed(sig: Set<String>?, now: Long): Change {
        if (sig == null) return Change.None
        val cur = current
        if (cur == null) { current = sig; since = now; return Change.None }
        if (alike(sig, cur)) {
            // the same reel, maybe with a label that changed (follow, like): keep the newest look
            candidate = null; current = sig
            return Change.None
        }
        val c = candidate
        if (c == null || !alike(sig, c)) { candidate = sig; candidateAt = now; return Change.None }
        if (now - candidateAt < HOLD) return Change.None
        candidate = null
        val back = previous?.let { alike(it, sig) } == true
        if (back) {
            // returned to the reel before: it keeps the time it already had
            val t = since; since = previousSince; previousSince = t
        } else {
            previousSince = since; since = now
        }
        previous = cur; current = sig
        return if (back) Change.Back else Change.New
    }

    companion object {
        const val HOLD = 220L

        /** two looks of the same reel share at least half of what they say */
        fun alike(a: Set<String>, b: Set<String>): Boolean {
            val both = a.count { it in b }
            val all = a.size + b.size - both
            return all == 0 || both * 2 >= all
        }
    }
}
