package com.iagodf.argos.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.iagodf.argos.data.Block
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.IconData
import com.iagodf.argos.ui.ItemRow
import com.iagodf.argos.ui.Subtitle
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.Toggle
import com.iagodf.argos.ui.tileBrush
import com.iagodf.argos.ui.u
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "turns off on 2 Dec" for a change that is waiting */
fun waitText(at: Long): String {
    val d = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val f = DateTimeFormatter.ofPattern(if (Lang.en) "d MMM" else "d 'de' MMM", Lang.locale)
    return tr("Se aplica el ", "Applies on ") + d.format(f)
}

/** changing something: tells you if it has to wait */
fun change(b: Block, on: Boolean) {
    Store.set(b, on)
    val softer = if (b == Block.IgDmSingle) on else !on
    if (softer && Store.committed) Toaster.show(tr("Relajar espera ${Store.RELAX_DAYS} días", "Relaxing waits ${Store.RELAX_DAYS} days"))
}

@Composable
fun BlocksScreen(openFriction: () -> Unit) {
    Store.rev.intValue
    Column {
        Title(null, tr("Bloqueos", "Blocks"))
        Gap(10)
        Subtitle(tr("Elige qué desaparece de tus apps", "Choose what disappears from your apps"))
        Gap(18)
        if (!com.iagodf.argos.ui.setup.serviceOn()) {
            com.iagodf.argos.ui.setup.PausedCard()
            Gap(12)
        }
        Group(
            Ic.camera, Color(0xFFFF5C8A), "Instagram",
            listOf(
                Triple(Block.IgReels, "Reels", null),
                Triple(Block.IgExplore, tr("Explorar", "Explore"), tr("Tapa la cuadrícula, buscar sigue", "Hides the grid, search still works")),
                Triple(Block.IgDmSingle, tr("Reels que te envían", "Reels sent to you"), tr("Ver solo ese, sin deslizar", "Watch only that one, no swiping")),
            ),
        )
        Gap(12)
        Group(Ic.tv, Color(0xFFFF4D4D), "YouTube", listOf(Triple(Block.YtShorts, "Shorts", null)))
        Gap(12)
        Group(
            Ic.globe, C.Blue, tr("Navegador", "Browser"),
            listOf(Triple(Block.Web, tr("Reels y Shorts en la web", "Reels and Shorts on the web"), null)),
        )
        Gap(12)
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 0, onClick = openFriction) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.u, vertical = 14.u), verticalAlignment = Alignment.CenterVertically) {
                Tile(Ic.slow, C.Orange)
                Spacer(Modifier.width(12.u))
                Column(Modifier.weight(1f)) {
                    T(tr("Fricción", "Friction"), 16, 650)
                    T(tr("Cada reel cuesta un poco más", "Every reel costs a bit more"), 12, 400, C.Muted, Modifier.padding(top = 2.u))
                }
                Icon(Ic.chev, 18, C.Chev, 2f)
            }
        }
    }
}

@Composable
private fun Group(icon: IconData, accent: Color, name: String, rows: List<Triple<Block, String, String?>>) {
    Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 0) {
        Column(Modifier.padding(start = 16.u, end = 16.u, top = 12.u, bottom = 4.u)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.u), verticalAlignment = Alignment.CenterVertically) {
                Tile(icon, accent)
                Spacer(Modifier.width(12.u))
                T(name, 16, 650, modifier = Modifier.weight(1f))
                Icon(Ic.chev, 18, C.Chev, 2f)
            }
            rows.forEach { (b, label, sub) ->
                val wait = Store.pendingFor(if (b == Block.IgDmSingle) "dm" else "block:${b.key}")
                ItemRow(label, wait?.let { waitText(it.applyAt) } ?: sub) {
                    Toggle(Store.isOn(b)) { change(b, it) }
                }
            }
        }
    }
}
