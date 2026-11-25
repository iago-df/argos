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

/** likes, views, counts and times: they change while you watch */
private val NUMBERS = Regex("""^[\d\s.,:%+·kKmMbB-]*(\s?(mil|M|K|views|visualizaciones|likes|me gusta))?$""")

/** labels that flip when tapped, so the same reel would look like a new one */
private val FLIPS = setOf(
    "follow", "following", "seguir", "siguiendo", "subscribe", "subscribed", "suscribirse", "suscrito", "like", "unlike",
    "me gusta", "liked", "más", "more", "less", "menos", "…", "...",
)

class Node(
    val id: String?, val desc: String?, val text: String?, val cls: String,
    val clickable: Boolean, val selected: Boolean, val scrollable: Boolean, val editable: Boolean,
    val rect: Rect, val parent: Int, val depth: Int,
)

class Screen(root: AccessibilityNodeInfo?, limit: Int = 1200) {
    val nodes = ArrayList<Node>()
    /** the live nodes, same order as [nodes], to act on them */
    val infos = ArrayList<AccessibilityNodeInfo>()
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
                infos.add(node)
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

    fun label(n: Node) = (n.desc ?: n.text)?.trim()

    private fun isInside(n: Node, ancestor: Int): Boolean {
        var p = n.parent
        while (p >= 0) { if (p == ancestor) return true; p = nodes[p].parent }
        return false
    }

    /**
     * what makes the reel on screen this reel and not another: the author, the caption, the audio.
     * read only inside the player (so the comments do not count), without numbers (likes change)
     * and without buttons that flip when you tap them. null when there is nothing to tell reels apart
     */
    fun reelSignature(player: List<String>): Set<String>? {
        val box = nodes.indexOfFirst { n -> n.id != null && player.any { n.id.contains(it) } }
        val out = HashSet<String>()
        nodes.forEachIndexed { i, n ->
            if (box >= 0 && i != box && !isInside(n, box)) return@forEachIndexed
            n.text?.trim()?.let { t -> if (t.length >= 2 && !NUMBERS.matches(t) && t.lowercase() !in FLIPS) out.add(t.take(80)) }
            n.desc?.trim()?.let { d -> if (d.length >= 25 && !NUMBERS.matches(d)) out.add(d.take(80)) }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** the selected tab of the bottom bar, by id or by its label */
    fun selectedTab(): Node? {
        val h = bounds.height()
        return nodes.firstOrNull { it.selected && it.rect.top > bounds.top + h * .8f && it.rect.width() < bounds.width() * .4f && (it.id != null || label(it) != null) }
    }

    /** a tab of the bottom bar, found again by id or label, to tap it */
    fun tab(id: String?, label: String?): AccessibilityNodeInfo? {
        val h = bounds.height()
        val i = nodes.indexOfFirst { n ->
            n.rect.top > bounds.top + h * .8f && ((id != null && n.id == id) || (id == null && label != null && label(n) == label))
        }
        if (i < 0) return null
        var k = i
        while (k >= 0 && !nodes[k].clickable) k = nodes[k].parent
        return infos.getOrNull(if (k >= 0) k else i)
    }

    /** the scrolling list that holds the reels, to move it back by one */
    fun pager(player: List<String>): AccessibilityNodeInfo? {
        val i = nodes.indexOfFirst { n -> n.scrollable && n.id != null && player.any { n.id.contains(it) } }
            .takeIf { it >= 0 } ?: nodes.indices.filter { nodes[it].scrollable }.maxByOrNull { nodes[it].rect.height() } ?: return null
        return infos.getOrNull(i)
    }

    // ---------- instagram ----------

    /** where the reels sit inside instagram and youtube */
    val igPlayer = listOf("clips_viewer_view_pager", "clips_viewer")
    val ytPlayer = listOf("reel_recycler", "reel_player_page_container", "reel_watch", "shorts_player")

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
