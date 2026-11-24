package com.iagodf.argos.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iagodf.argos.data.Policy
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.Usage
import com.iagodf.argos.data.fmtMinutes
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.IconData
import com.iagodf.argos.ui.Pill
import com.iagodf.argos.ui.Ring
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.u
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun hhmm(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern("HH:mm"))

fun hhmm(min: Int): String = hhmm(LocalTime.of((min / 60) % 24, min % 60))

@Composable
fun HomeScreen(openFocus: () -> Unit, openSchedules: () -> Unit, openLock: () -> Unit, top: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    Store.rev.intValue
    // refresh once a minute: the time recovered and the schedules move with the clock
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    tick
    val pol = Policy.now()
    // usage stats are slow to read, so off the main thread
    val minutes by androidx.compose.runtime.produceState(Store.blockedToday() * 4, tick, Store.rev.intValue) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Usage.recoveredMinutes(ctx) }
    }

    Column {
        top()
        val greet = when (LocalTime.now().hour) {
            in 5..11 -> tr("Buenos días", "Good morning")
            in 12..19 -> tr("Buenas tardes", "Good afternoon")
            else -> tr("Buenas noches", "Good evening")
        }
        T(greet, 15, 500, C.Muted, Modifier.padding(horizontal = 22.u))
        Title(tr("Hoy has", "Today you've"), tr("recuperado", "recovered"), Modifier.padding(top = 6.u))
        T(fmtMinutes(minutes), 66, 650, brush = C.blueText, ls = -.05f, lh = 1.1f, modifier = Modifier.padding(start = 22.u, top = 2.u))
        Gap(12)

        // dark card: what is blocked right now
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), bg = C.Ink, padding = 18, onClick = openLock) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(tr("Bloqueos activos", "Active blocks"), 16, 600, Color.White, Modifier.weight(1f))
                if (Store.committed) Pill(tr("Inquebrantable", "Unbreakable"), Color.White.copy(alpha = .12f), Color.White, Ic.shield, C.BlueSoft)
                else Pill(tr("Sin candado", "Not locked"), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .6f), Ic.lock, Color.White.copy(alpha = .6f))
            }
            Row(Modifier.padding(top = 16.u), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.u)) {
                val items = listOf(
                    Triple(Ic.reels, "Reels", pol.reels),
                    Triple(Ic.tv, "Shorts", pol.shorts),
                    Triple(Ic.globe, tr("Webs", "Web"), pol.web),
                )
                val share = (items.count { it.third } + (if (pol.explore) 1 else 0) + (if (pol.focus) 1 else 0)) / 5f
                Ring(share.coerceAtLeast(.04f), 92, 9, Color.White.copy(alpha = .12f), C.peachBlue)
                Column {
                    items.forEach { (icon, label, on) -> BlockLine(icon, label, on) }
                }
            }
        }
        Gap(14)

        Row(Modifier.padding(horizontal = 16.u), horizontalArrangement = Arrangement.spacedBy(14.u)) {
            val focusText = when {
                pol.focusUntil > 0 -> tr("Hasta las ", "Until ") + hhmm(java.time.Instant.ofEpochMilli(pol.focusUntil).atZone(java.time.ZoneId.systemDefault()).toLocalTime())
                pol.focus && pol.bySchedule != null -> "${hhmm(pol.bySchedule.start)} – ${hhmm(pol.bySchedule.end)}"
                else -> Store.schedules().firstOrNull { it.on && it.kind == com.iagodf.argos.data.Kind.Focus }
                    ?.let { "${hhmm(it.start)} – ${hhmm(it.end)}" }
                    ?: tr("Toca para empezar", "Tap to start")
            }
            SmallCard(
                Modifier.weight(1f), Brush.linearGradient(listOf(Color(0xFFE6E1FF), Color(0xFFF4F1FF))),
                Ic.moon, C.Ink, if (pol.focus) tr("Focus activo", "Focus on") else "Focus", focusText, openFocus,
            )
            val d = Store.streakDays()
            SmallCard(
                Modifier.weight(1f), Brush.linearGradient(listOf(Color(0xFFFFE6DA), Color(0xFFFFF3EC))),
                Ic.flame, Color(0xFFFF8A5C), tr("Racha", "Streak"),
                when (d) {
                    0 -> tr("Empieza hoy", "Starts today")
                    1 -> tr("1 día sin reels", "1 day without reels")
                    else -> tr("$d días sin reels", "$d days without reels")
                }, openSchedules,
            )
        }
        Gap(14)

        val next = Policy.next()
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth().height(68.u), padding = 0, onClick = openSchedules) {
            Row(Modifier.fillMaxWidth().height(68.u).padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
                Tile(Ic.clock, C.BlueTint, tint = C.Blue)
                Spacer(Modifier.width(12.u))
                Column(Modifier.weight(1f)) {
                    T(tr("Próximo bloqueo", "Next block"), 14, 600)
                    T(
                        next?.let { (s, at) ->
                            val day = if (at.toLocalDate() == LocalDateTime.now().toLocalDate()) "" else at.dayOfWeek
                                .getDisplayName(java.time.format.TextStyle.SHORT, com.iagodf.argos.data.Lang.locale) + " "
                            "${s.label} · $day${hhmm(at.toLocalTime())}"
                        } ?: tr("Sin horarios activos", "No schedules on"),
                        12, 400, C.Muted, lines = 1,
                    )
                }
                Icon(Ic.chev, 18, C.Chev, 2f)
            }
        }
    }
}

@Composable
private fun BlockLine(icon: IconData, label: String, on: Boolean) {
    Row(Modifier.height(26.u), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.u)) {
        Icon(icon, 16, Color.White, 2f)
        T(label, 14, 500, Color.White.copy(alpha = .85f))
        T(if (on) tr("bloqueado", "blocked") else tr("permitido", "allowed"), 14, 500, if (on) C.BlueSoft else Color.White.copy(alpha = .4f), Modifier.padding(start = 6.u))
    }
}

@Composable
private fun SmallCard(modifier: Modifier, brush: Brush, icon: IconData, tile: Color, title: String, sub: String, onClick: () -> Unit) {
    Card(modifier.heightIn(min = 104.u), brush = brush, onClick = onClick) {
        Tile(icon, tile, size = 34, radius = 11, iconSize = 17)
        T(title, 16, 650, modifier = Modifier.padding(top = 10.u), lines = 1)
        T(sub, 12, 400, C.Soft, lines = 1)
    }
}
