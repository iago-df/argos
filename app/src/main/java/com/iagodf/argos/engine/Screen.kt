package com.iagodf.argos.engine

import android.view.accessibility.AccessibilityNodeInfo

/**
 * a flat read of what is on screen: view ids, selected ids, texts and descriptions.
 * instagram and youtube change their ids now and then, so the checks look for several of them
 */
class Screen(root: AccessibilityNodeInfo?, limit: Int = 600) {
    val ids = HashSet<String>()
    val selected = HashSet<String>()
    val descs = ArrayList<String>()
    val selectedDescs = ArrayList<String>()
    val texts = ArrayList<Pair<String, String>>() // id to text

    init {
        if (root != null) {
            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)
            var n = 0
            while (stack.isNotEmpty() && n < limit) {
                val node = stack.removeLast()
                n++
                val id = node.viewIdResourceName?.substringAfter(":id/")
                if (id != null) {
                    ids.add(id)
                    if (node.isSelected) selected.add(id)
                }
                node.contentDescription?.toString()?.let { d ->
                    descs.add(d)
                    if (node.isSelected) selectedDescs.add(d)
                }
                if (id != null && node.text != null) texts.add(id to node.text.toString())
                for (i in 0 until node.childCount) node.getChild(i)?.let { stack.add(it) }
            }
        }
    }

    fun has(vararg any: String) = any.any { it in ids }
    fun hasPart(part: String) = ids.any { it.contains(part) }
    fun textOf(vararg id: String): String? = texts.firstOrNull { it.first in id }?.second

    // ---------- instagram ----------

    val igReelsViewer: Boolean
        get() = has(
            "clips_viewer_view_pager", "clips_video_container", "clips_viewer_container", "clips_single_media_component",
            "clips_ufi_component", "clips_media_component", "clips_viewer_root",
        ) || hasPart("clips_viewer")

    val igReelsTab: Boolean get() = "clips_tab" in selected || selectedDescs.any { it.equals("Reels", true) }

    val igExplore: Boolean
        get() = ("search_tab" in selected || selectedDescs.any { it.equals("Search and explore", true) || it.equals("Buscar y explorar", true) }) &&
            (hasPart("explore") || has("recycler_view", "grid_recycler_view"))

    val igDirectThread: Boolean
        get() = has("row_thread_composer_edittext", "direct_thread_title", "thread_title", "direct_thread_recycler_view") || hasPart("direct_thread")

    // ---------- browsers ----------

    /** what the address bar of the browser says, without the scheme */
    val url: String?
        get() = textOf(
            "url_bar", "mozac_browser_toolbar_url_view", "location_bar_edit_text", "url_field", "omnibarTextInput",
            "search_box", "address_bar_edit_text", "url",
        )?.trim()?.lowercase()?.removePrefix("https://")?.removePrefix("http://")

    // ---------- youtube ----------

    val ytShorts: Boolean
        get() = has(
            "reel_player_page_container", "reel_recycler", "reel_watch_player", "shorts_player_container", "reel_player_overlay",
            "reel_time_bar",
        ) || selectedDescs.any { it.equals("Shorts", true) }
}
