package com.iagodf.argos.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.ItemRow
import com.iagodf.argos.ui.Sheet
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.u

/** the bell: what Argos stopped today */
@Composable
fun ActivitySheet(open: Boolean, onClose: () -> Unit) {
    Sheet(open, onClose, tr("Hoy", "Today")) {
        Store.rev.intValue
        val rows = listOf(
            Triple(Ic.reels, tr("Reels", "Reels"), "reels"),
            Triple(Ic.search, tr("Explorar", "Explore"), "explore"),
            Triple(Ic.tv, "Shorts", "shorts"),
            Triple(Ic.globe, tr("Webs", "Web"), "web"),
            Triple(Ic.moon, tr("Apps durante Focus", "Apps during Focus"), "focus"),
        )
        val total = Store.blockedToday()
        T(
            if (total == 0) tr("Nada que parar todavía. Bien.", "Nothing to stop yet. Nice.")
            else tr("Argos te ha sacado $total veces", "Argos got you out $total times"),
            15, 500, C.Muted, Modifier.padding(bottom = 8.u),
        )
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { i, (icon, label, key) ->
                ItemRow(label, divider = i > 0, lead = { Tile(icon, C.BlueTint, tint = C.Blue, size = 34, radius = 11, iconSize = 17) }) {
                    T(Store.blockedToday(key).toString(), 18, 700, if (Store.blockedToday(key) > 0) C.Blue else C.Chev)
                }
            }
        }
    }
}
