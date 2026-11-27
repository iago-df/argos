package com.iagodf.argos.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iagodf.argos.data.Apps
import com.iagodf.argos.data.Policy
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.BigButton
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.Ring
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.home.hhmm
import com.iagodf.argos.ui.softShadow
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

@Composable
fun FocusScreen() {
    Store.rev.intValue
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val pol = Policy.now()
    val manual = Store.manualFocusOn(now)

    Column {
        if (com.iagodf.argos.data.Lang.en) Title("mode", "Focus", lightFirst = false) else Title("Modo", "Focus")
        Gap(22)

        // ring with the countdown
        val total = Store.focusMinutes * 60_000L
        val left = if (manual) Store.focusUntil - now else total
        val prog = if (manual) 1f - left.toFloat() / (Store.focusUntil - Store.focusStarted).coerceAtLeast(1) else 0f
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Ring(prog, 172, 12, Color(0xFFE7E4F2), Brush.linearGradient(listOf(C.Peach, C.Blue)), Modifier.tap(!manual) { Sheets.open(tr("Duración", "Length")) { LengthBody() } }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(
                        when {
                            manual -> tr("En Focus", "In Focus")
                            pol.focus -> tr("Por horario", "By schedule")
                            else -> tr("Listo", "Ready")
                        }, 13, 500, C.Muted,
                    )
                    T(clock(left), 34, 650, ls = -.04f, tabular = true)
                }
            }
        }
        Gap(24)

        Row(Modifier.fillMaxWidth().padding(horizontal = 22.u), verticalAlignment = Alignment.CenterVertically) {
            T(tr("Apps permitidas", "Allowed apps"), 16, 650, modifier = Modifier.weight(1f))
            T(tr("Editar", "Edit"), 13, 500, C.Blue, Modifier.tap { Sheets.open(tr("Apps permitidas", "Allowed apps")) { AllowedBody() } }.padding(4.u))
        }
        Gap(14)
        AllowedGrid(Store.allowed().toList(), checks = pol.focus)
        Gap(24)

        val until = if (manual) hhmm(Instant.ofEpochMilli(Store.focusUntil).atZone(ZoneId.systemDefault()).toLocalTime()) else ""
        Box(Modifier.padding(horizontal = 16.u)) {
            when {
                !manual -> BigButton(tr("Activar Focus", "Activate Focus"), icon = Ic.moon) {
                    Store.startFocus()
                    Toaster.show(tr("Focus hasta las ", "Focus until ") + hhmm(Instant.ofEpochMilli(Store.focusUntil).atZone(ZoneId.systemDefault()).toLocalTime()))
                }
                Store.committed -> BigButton(tr("Focus hasta las $until", "Focus until $until"), icon = Ic.lock, enabled = false) {}
                else -> BigButton(tr("Terminar Focus", "End Focus"), bg = Color.White, color = C.Ink) { Store.stopFocus() }
            }
        }
        Gap(34)
        com.iagodf.argos.ui.schedules.SchedulesSection()
    }
}

@Composable
private fun AllowedGrid(pkgs: List<String>, checks: Boolean) {
    val ctx = LocalContext.current
    val apps = remember(pkgs) { pkgs.filter { Apps.installed(ctx, it) }.map { it to Apps.label(ctx, it) }.sortedBy { it.second.lowercase() } }
    if (apps.isEmpty()) {
        T(tr("Ninguna. Toca Editar para elegir.", "None yet. Tap Edit to pick them."), 14, 400, C.Muted, Modifier.padding(horizontal = 22.u))
        return
    }
    FlowRow(Modifier.padding(horizontal = 10.u), verticalArrangement = Arrangement.spacedBy(14.u)) {
        apps.forEach { (pkg, label) ->
            Column(Modifier.width(108.u), horizontalAlignment = Alignment.CenterHorizontally) {
                val pop by animateFloatAsState(if (checks) 1f else 0f, spring(dampingRatio = .45f, stiffness = 400f), label = "ck")
                Box {
                    AppIcon(pkg, 56)
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(6.u, (-6).u).scale(pop).size(22.u).clip(CircleShape)
                            .background(C.Blue).border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.check, 12, Color.White, 3f) }
                }
                T(label, 12, 500, C.Body, Modifier.padding(top = 7.u), lines = 1)
            }
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Number) {
    val ctx = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.u.roundToPx() }
    val bmp = remember(pkg, px) { Apps.icon(ctx, pkg, px)?.asImageBitmap() }
    Box(Modifier.size(size.u).softShadow(6.dp, RoundedCornerShape(17.u), .12f).clip(RoundedCornerShape(17.u)).background(Color.White)) {
        if (bmp != null) Image(bmp, null, Modifier.size(size.u))
    }
}

@Composable
private fun LengthBody() {
    Store.rev.intValue
    run {
        listOf(25, 60, 90, 120, 180, 240).forEach { m ->
            val on = Store.focusMinutes == m
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.u).clip(RoundedCornerShape(18.u))
                    .background(if (on) C.Ink else C.Bg).tap { Store.setFocusMinutes(m); Sheets.close() }.padding(horizontal = 18.u, vertical = 14.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min", 16, 600, if (on) Color.White else C.Ink, Modifier.weight(1f))
                if (on) Icon(Ic.check, 18, Color.White, 2.4f)
            }
        }
    }
}

@Composable
private fun AllowedBody() {
    Store.rev.intValue
    val ctx = LocalContext.current
    run {
        T(
            tr("Durante Focus solo se abren estas. Llamadas y ajustes siempre funcionan.", "During Focus only these open. Calls and settings always work."),
            14, 400, C.Muted, Modifier.padding(bottom = 10.u),
        )
        if (Store.committed) T(
            tr("Quitar apps es relajar: espera ${Store.RELAX_DAYS} días.", "Removing apps relaxes Focus: it waits ${Store.RELAX_DAYS} days."),
            13, 500, C.Orange, Modifier.padding(bottom = 10.u),
        )
        val all = remember { Apps.launchable(ctx) }
        val sel = Store.allowed()
        all.forEachIndexed { i, a ->
            com.iagodf.argos.ui.ItemRow(a.label, divider = i > 0, lead = { AppIcon(a.pkg, 38) }) {
                com.iagodf.argos.ui.Toggle(a.pkg in sel) { on -> Store.setAllowed(if (on) sel + a.pkg else sel - a.pkg) }
            }
        }
    }
}
