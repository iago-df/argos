package com.iagodf.argos.ui.friction

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.iagodf.argos.data.Friction
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.IconData
import com.iagodf.argos.ui.ItemRow
import com.iagodf.argos.ui.Subtitle
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.Toggle
import com.iagodf.argos.ui.blocks.waitText
import com.iagodf.argos.ui.softShadow
import com.iagodf.argos.ui.u
import kotlin.math.roundToInt

private const val MAX = 20

private fun save(f: Friction) {
    val old = Store.friction()
    Store.setFriction(f)
    if (Store.committed && Store.pendingFor("friction") != null && f != old)
        Toaster.show(tr("Relajar espera ${Store.RELAX_DAYS} días", "Relaxing waits ${Store.RELAX_DAYS} days"))
}

@Composable
fun FrictionScreen() {
    Store.rev.intValue
    val f = Store.friction()
    Column {
        Title(null, tr("Fricción", "Friction"))
        Gap(10)
        Subtitle(tr("Cuando los Reels estén permitidos", "When Reels are allowed"))
        Gap(18)

        // the pause grows reel after reel, this is the one you get at reel 20
        var drag by remember(f.maxPause) { mutableIntStateOf(f.maxPause) }
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tile(Ic.clock, C.Blue)
                Spacer(Modifier.width(10.u))
                T(tr("Pausa entre reels", "Pause between reels"), 15, 650, modifier = Modifier.weight(1f))
                T("+$drag s", 18, 700, C.Blue)
            }
            Slider(drag, { drag = it }) { save(f.copy(maxPause = drag)) }
            Row(Modifier.fillMaxWidth().padding(top = 14.u)) {
                T("Reel 1", 12, 500, C.Sub)
                Spacer(Modifier.weight(1f))
                T("Reel 10", 12, 500, C.Sub)
                Spacer(Modifier.weight(1f))
                T("Reel 20", 12, 500, C.Sub)
            }
        }
        Store.pendingFor("friction")?.let {
            T(waitText(it.applyAt), 12, 500, C.Orange, Modifier.padding(start = 22.u, top = 8.u))
        }
        Gap(14)
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 0) {
            Column(Modifier.padding(horizontal = 16.u, vertical = 4.u)) {
                Line(Ic.drop, Color(0xFFF2F2F5), Color(0xFF6F6A88), tr("Blanco y negro", "Black and white"), tr("Pierde color poco a poco", "Loses colour little by little"), false, f.gray) {
                    save(f.copy(gray = it))
                }
                Line(Ic.slow, Color(0xFFFFF0E8), C.Orange, tr("Deslizar más lento", "Slower swiping"), tr("Cada vez cuesta más", "Every time it costs more"), true, f.slow) {
                    save(f.copy(slow = it))
                }
                Line(Ic.restart, C.BlueTint, C.Blue, tr("No se reinicia", "Does not reset"), tr("Aunque cierres la app", "Even if you close the app"), true, f.noReset) {
                    save(f.copy(noReset = it))
                }
            }
        }
        Gap(14)
        T(
            tr("Hoy llevas ${Store.frictionCount()} reels.", "${Store.frictionCount()} reels so far today."),
            13, 500, C.Muted, Modifier.padding(horizontal = 22.u),
        )
    }
}

@Composable
private fun Line(icon: IconData, bg: Color, tint: Color, title: String, sub: String, divider: Boolean, on: Boolean, set: (Boolean) -> Unit) {
    ItemRow(title, sub, divider, lead = { Tile(icon, tint) }) { Toggle(on, onChange = set) }
}

@Composable
private fun Slider(value: Int, onValue: (Int) -> Unit, onDone: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 26.u, start = 4.u, end = 4.u).height(26.u)) {
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        fun at(x: Float) = (1 + (x / w).coerceIn(0f, 1f) * (MAX - 1)).roundToInt()
        val frac = (value - 1f) / (MAX - 1)
        val mw = maxWidth
        Box(
            Modifier.fillMaxWidth().fillMaxHeight()
                .pointerInput(w) { detectTapGestures { onValue(at(it.x)); onDone() } }
                .pointerInput(w) { detectHorizontalDragGestures(onDragEnd = onDone) { c, _ -> onValue(at(c.position.x)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(8.u).clip(RoundedCornerShape(4.u)).background(C.Track))
            Box(
                Modifier.fillMaxWidth(frac.coerceAtLeast(.02f)).height(8.u).clip(RoundedCornerShape(4.u))
                    .background(Brush.horizontalGradient(listOf(C.Peach, C.Blue)))
            )
            Box(
                Modifier.offset(x = mw * frac - 13.u).size(26.u).softShadow(4.dp, CircleShape, .3f)
                    .clip(CircleShape).background(Color.White)
            )
        }
    }
}
