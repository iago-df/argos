package com.iagodf.argos.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * a flat read of what is on screen: view ids, selected ids, texts, descriptions and where things are.
 * only what the user can actually see counts: apps keep old screens alive behind the current one,
 * and reading those made argos press back on screens that had nothing to do with reels.
 * instagram and youtube change their ids now and then, so the checks look for several of them
 */
private val DANGER = listOf(
    "Uninstall", "Desinstalar", "Force stop", "Forzar", "Clear storage", "Clear data", "Borrar almacenamiento", "Borrar datos",
    "Use Argos", "Usar Argos", "Deactivate", "Desactivar", "Device admin", "Administrador", "Stop Argos", "¿Detener", "Turn off",
)

private val DOMAIN = Regex("""^[\w.-]+\.[a-z]{2,}(/|$)""")

class Node(
    val id: String?, val desc: String?, val text: String?, val cls: String,
    val clickable: Boolean, val selected: Boolean, val scrollable: Boolean, val editable: Boolean,
    val rect: Rect, val parent: Int, val depth: Int,
)

class Screen(root: AccessibilityNodeInfo?, limit: Int = 1200) {
    val nodes = ArrayList<Node>()
    val ids = HashSet<String>()
    val selected = HashSet<String>()
    val descs = ArrayList<String>()
    val selectedDescs = ArrayList<String>()
    val texts = ArrayList<Pair<String, String>>() // id to text
    val allText = ArrayList<String>()
    val bounds = Rect()

    init {
        if (root != null) {
            root.getBoundsInScreen(bounds)
            val stack = ArrayDeque<Triple<AccessibilityNodeInfo, Int, Int>>()
            stack.add(Triple(root, -1, 0))
            while (stack.isNotEmpty() && nodes.size < limit) {
                val (node, parent, depth) = stack.removeLast()
                // hidden screens and pages scrolled away are skipped with everything inside them
                if (!node.isVisibleToUser && depth > 0) continue
                val id = node.viewIdResourceName?.substringAfter(":id/")
                val r = Rect().also { node.getBoundsInScreen(it) }
                val me = nodes.size
                val n = Node(
                    id, node.contentDescription?.toString(), node.text?.toString(), node.className?.toString() ?: "",
                    node.isClickable, node.isSelected, node.isScrollable, node.isEditable, r, parent, depth,
                )
                nodes.add(n)
                if (id != null) {
                    ids.add(id)
                    if (n.selected) selected.add(id)
                }
                n.desc?.let { d -> descs.add(d); if (n.selected) selectedDescs.add(d) }
                if (id != null && n.text != null) texts.add(id to n.text)
                n.text?.let { allText.add(it) }
                for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.add(Triple(it, me, depth + 1)) }
            }
        }
    }

    fun has(vararg any: String) = any.any { it in ids }
    fun hasPart(part: String) = ids.any { it.contains(part) }
    fun textOf(vararg id: String): String? = texts.firstOrNull { it.first in id }?.second

    private fun label(n: Node) = (n.desc ?: n.text)?.trim()

    /**
     * the buttons that lead into a feed: a tab or a chip called "Reels" or "Shorts" (or with one of [tabIds]).
     * when the label sits on a child, the tappable parent is the one covered
     */
    fun entryPoints(names: Set<String>, tabIds: Set<String> = emptySet(), bottomOnly: Boolean = false): List<Rect> {
        val w = bounds.width().coerceAtLeast(1)
        val h = bounds.height().coerceAtLeast(1)
        val out = ArrayList<Rect>()
        nodes.forEachIndexed { i, n ->
            val named = n.id in tabIds || label(n)?.let { l -> names.any { l.equals(it, true) } } == true
            if (!named) return@forEachIndexed
            // the tappable thing: this node, or its parent when the label is on an icon inside it
            var t: Node? = n
            if (!n.clickable) t = nodes.getOrNull(n.parent)?.takeIf { it.clickable }
            val r = t?.rect ?: if (n.id in tabIds) n.rect else return@forEachIndexed
            if (r.isEmpty || r.width() > w * .42f || r.height() > h * .14f) return@forEachIndexed
            if (bottomOnly && r.top < bounds.top + h * .82f) return@forEachIndexed
            if (out.none { Rect.intersects(it, r) }) out.add(Rect(r))
        }
        return out
    }

    /**
     * the grid of the instagram explore page (and of the search results), under the search bar.
     * the typeahead list of accounts is left alone: its rows are as wide as the screen
     */
    fun exploreGrid(): Rect? {
        val w = bounds.width().coerceAtLeast(1)
        val h = bounds.height().coerceAtLeast(1)
        // the search bar: an input (or something called search) near the top, as low as a bar
        val search = nodes.filter { n ->
            (n.editable || n.id?.contains("search", true) == true) && n.rect.top < bounds.top + h * .3f &&
                n.rect.width() > w * .4f && n.rect.height() in 1..(h * .12f).toInt()
        }.maxByOrNull { it.rect.bottom } ?: return null
        val lists = nodes.withIndex().filter { (_, n) -> n.scrollable && n.rect.height() > h * .35f && n.rect.width() > w * .8f && n.rect.top >= search.rect.top }
        for ((i, list) in lists) {
            // tiles: a third (or two thirds) of the width, a bit tall, without text, at most two levels down
            var tiles = 0
            nodes.forEach { n ->
                if (n.depth - list.depth !in 1..3 || !isInside(n, i)) return@forEach
                val tw = n.rect.width()
                if (tw in (list.rect.width() * .2f).toInt()..(list.rect.width() * .7f).toInt() && n.rect.height() > tw * .6f && n.text.isNullOrBlank()) tiles++
            }
            if (tiles >= 3) {
                val top = maxOf(list.rect.top, search.rect.bottom)
                return Rect(list.rect.left, top, list.rect.right, list.rect.bottom).takeIf { it.height() > h * .25f }
            }
        }
        return null
    }

    private fun isInside(n: Node, ancestor: Int): Boolean {
        var p = n.parent
        while (p >= 0) { if (p == ancestor) return true; p = nodes[p].parent }
        return false
    }

    // ---------- instagram ----------

    val igReelsViewer: Boolean
        get() = has(
            "clips_viewer_view_pager", "clips_video_container", "clips_viewer_container", "clips_single_media_component",
            "clips_ufi_component", "clips_media_component", "clips_viewer_root",
        ) || hasPart("clips_viewer")

    val igReelsTab: Boolean get() = "clips_tab" in selected

    val igDirectThread: Boolean
        get() = has("row_thread_composer_edittext", "direct_thread_title", "thread_title", "direct_thread_recycler_view") || hasPart("direct_thread")

    // ---------- browsers ----------

    /** what the address bar of the browser says, without the scheme */
    val url: String?
        get() = (
            textOf(
                "url_bar", "mozac_browser_toolbar_url_view", "location_bar_edit_text", "url_field", "omnibarTextInput",
                "search_box", "address_bar_edit_text", "url", "toolbar_url", "addressbarEdit", "url_text",
            ) ?: texts.firstOrNull { (id, t) -> ("url" in id.lowercase() || "address" in id.lowercase() || "location" in id.lowercase()) && DOMAIN.containsMatchIn(t) }?.second
            )?.trim()?.lowercase()?.removePrefix("https://")?.removePrefix("http://")

    // ---------- self protection ----------

    /** a settings page about Argos that could switch it off or remove it */
    val dangerForArgos: Boolean
        get() {
            val all = allText + descs
            if (all.none { it.contains("Argos") }) return false
            return all.any { t -> DANGER.any { t.contains(it, ignoreCase = true) } }
        }

    // ---------- youtube ----------

    /** the shorts player itself. the shelf of shorts on the home feed is not the player */
    val ytShorts: Boolean
        get() = has(
            "reel_player_page_container", "reel_recycler", "reel_watch_player", "shorts_player_container", "reel_player_overlay",
            "reel_time_bar", "reel_watch_fragment_root", "reel_progress_bar", "reel_dyn_remix",
        ) || hasPart("reel_player") || hasPart("shorts_player")
}
