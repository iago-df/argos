package com.iagodf.argos.ui.schedules

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.iagodf.argos.data.Kind
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Schedule
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.BigButton
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.Inter
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.Toggle
import com.iagodf.argos.ui.blocks.waitText
import com.iagodf.argos.ui.dashed
import com.iagodf.argos.ui.home.hhmm
import com.iagodf.argos.ui.softShadow
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u
import com.iagodf.argos.ui.us
import java.time.LocalDate

private fun dayLetters() = if (Lang.en) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("L", "M", "X", "J", "V", "S", "D")
private fun dayShort() = if (Lang.en) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

fun daysText(d: Int): String = when (d) {
    Schedule.EVERY_DAY -> tr("Todos los días", "Every day")
    Schedule.WEEKDAYS -> tr("Lun – Vie", "Mon – Fri")
    0b1100000 -> tr("Fines de semana", "Weekends")
    0 -> tr("Ningún día", "No days")
    else -> (0..6).filter { d and (1 shl it) != 0 }.joinToString(", ") { dayShort()[it] }
}

fun scheduleSub(s: Schedule): String {
    val time = if (s.allDay) "24 h" else "${hhmm(s.start)} – ${hhmm(s.end)}"
    val night = s.kind == Kind.Focus && s.end < s.start
    return "${daysText(s.days)} · $time" + if (night) " · Focus" else ""
}

/** the schedules, at the bottom of the Focus tab: focus that turns on by itself, or reels only at some hours */
@Composable
fun SchedulesSection() {
    Store.rev.intValue
    Column {
        T(tr("Horarios", "Schedules"), 16, 650, modifier = Modifier.padding(horizontal = 22.u))
        T(
            tr("Focus que se enciende solo, o Reels y Shorts fuera solo a ciertas horas.", "Focus that turns on by itself, or Reels and Shorts gone only at some hours."),
            13, 400, C.Muted, Modifier.padding(start = 22.u, end = 22.u, top = 4.u, bottom = 14.u),
        )
        WeekStrip()
        Gap(24)
        Store.schedules().forEach { s ->
            ScheduleCard(s)
            Gap(12)
        }
        Box(
            Modifier.padding(horizontal = 16.u).fillMaxWidth().height(60.u).dashed(Color(0xFFCFCBE0), 24.u)
                .clip(RoundedCornerShape(24.u)).tap { openEditor(null) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.u)) {
                Icon(Ic.plus, 18, Color(0xFF6F6A88), 2f)
                T(tr("Nuevo horario", "New schedule"), 15, 600, Color(0xFF6F6A88))
            }
        }
        Gap(16)
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), bg = C.BlueTint, shadow = false) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.u)) {
                Icon(Ic.lock, 18, C.Blue, 2f)
                val txt = buildAnnotatedString {
                    if (Store.committed) {
                        append(tr("Relajar un horario se aplica en ", "Relaxing a schedule applies in "))
                        withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(tr("${Store.RELAX_DAYS} días", "${Store.RELAX_DAYS} days")) }
                    } else {
                        append(tr("Cuando bloquees Argos, relajar esperará ", "Once Argos is locked, relaxing waits "))
                        withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(tr("${Store.RELAX_DAYS} días", "${Store.RELAX_DAYS} days")) }
                    }
                }
                androidx.compose.foundation.text.BasicText(
                    txt, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight(500), fontSize = 13.us, color = Color(0xFF3C3A5A)),
                )
            }
        }
    }
}

@Composable
private fun WeekStrip() {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.u), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val on = d == today
            val active = Store.schedules().any { it.on && it.days and (1 shl i) != 0 }
            Column(
                Modifier.width(40.u).height(56.u).softShadow(6.dp, RoundedCornerShape(20.u), .06f).clip(RoundedCornerShape(20.u))
                    .background(if (on) C.Ink else Color.White),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                T(dayLetters()[i], 15, 600, if (on) Color.White else C.Ink)
                T(d.dayOfMonth.toString(), 11, 500, if (on) Color.White.copy(alpha = .6f) else C.Sub, Modifier.padding(top = 3.u))
                Box(Modifier.padding(top = 3.u).size(4.u).clip(RoundedCornerShape(2.u)).background(if (active) (if (on) C.Peach else C.Blue) else Color.Transparent))
            }
        }
    }
}

@Composable
private fun ScheduleCard(s: Schedule) {
    val night = s.kind == Kind.Focus && s.end < s.start
    val (bg, tint, icon) = when {
        s.kind == Kind.NoReels -> Triple(Color(0xFFE9ECFF), C.Blue, Ic.reels)
        night -> Triple(Color(0xFFFFE9DF), C.Orange, Ic.clock)
        else -> Triple(C.Ink, Color.White, Ic.moon)
    }
    val wait = Store.pendingFor("sched:${s.id}")
    Card(Modifier.padding(horizontal = 16.u).fillMaxWidth().height(80.u), padding = 0, onClick = { openEditor(s) }) {
        Row(Modifier.fillMaxWidth().height(80.u).padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
            Tile(icon, if (tint == Color.White) bg else tint, size = 44)
            Spacer(Modifier.width(12.u))
            Column(Modifier.weight(1f)) {
                T(s.label, 15, 650, lines = 1)
                T(wait?.let { waitText(it.applyAt) } ?: scheduleSub(s), 12, 400, if (wait != null) C.Orange else C.Muted, Modifier.padding(top = 2.u), lines = 1)
            }
            Spacer(Modifier.width(10.u))
            Toggle(s.on) { on ->
                Store.saveSchedule(s.copy(on = on))
                if (!on && Store.committed) Toaster.show(tr("Relajar espera ${Store.RELAX_DAYS} días", "Relaxing waits ${Store.RELAX_DAYS} days"))
            }
        }
    }
}

private fun openEditor(s: Schedule?) {
    Sheets.open(if (s == null) tr("Nuevo horario", "New schedule") else tr("Editar horario", "Edit schedule")) { Editor(s) }
}

@Composable
private fun Editor(orig: Schedule?) {
    var name by remember { mutableStateOf(orig?.label ?: "") }
    var kind by remember { mutableStateOf(orig?.kind ?: Kind.Focus) }
    var days by remember { mutableStateOf(orig?.days ?: Schedule.WEEKDAYS) }
    var start by remember { mutableStateOf(orig?.start ?: 9 * 60) }
    var end by remember { mutableStateOf(orig?.end ?: 14 * 60) }
    var allDay by remember { mutableStateOf(orig?.allDay ?: false) }

    Label(tr("Nombre", "Name"))
    BasicTextField(
        name, { name = it.take(28) }, singleLine = true,
        textStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 16.us, color = C.Ink),
        cursorBrush = SolidColor(C.Blue),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.u)).background(C.Bg).padding(horizontal = 16.u, vertical = 14.u),
        decorationBox = { inner ->
            if (name.isEmpty()) T(tr("Ej. Estudio", "E.g. Study"), 16, 500, C.Chev)
            inner()
        },
    )

    Label(tr("Qué hace", "What it does"))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.u)).background(C.Bg).padding(4.u),
        horizontalArrangement = Arrangement.spacedBy(4.u),
    ) {
        listOf(Kind.Focus to "Focus", Kind.NoReels to tr("Sin Reels ni Shorts", "No Reels or Shorts")).forEach { (k, l) ->
            val bg by animateColorAsState(if (kind == k) Color.White else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).height(42.u).clip(RoundedCornerShape(14.u)).background(bg).tap { kind = k },
                contentAlignment = Alignment.Center,
            ) { T(l, 14, 600, if (kind == k) C.Ink else C.Muted, lines = 1) }
        }
    }

    Label(tr("Días", "Days"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dayLetters().forEachIndexed { i, l ->
            val on = days and (1 shl i) != 0
            Box(
                Modifier.size(40.u).clip(RoundedCornerShape(20.u)).background(if (on) C.Ink else C.Bg).tap { days = days xor (1 shl i) },
                contentAlignment = Alignment.Center,
            ) { T(l, 15, 600, if (on) Color.White else C.Ink) }
        }
    }

    Label(tr("Horas", "Hours"))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.u), verticalAlignment = Alignment.CenterVertically) {
        T(tr("Todo el día", "All day"), 15, 500, modifier = Modifier.weight(1f))
        Toggle(allDay) { allDay = it }
    }
    if (!allDay) {
        TimeRow(tr("Empieza", "Starts"), start) { start = it }
        TimeRow(tr("Termina", "Ends"), end) { end = it }
    }
    Gap(20)
    val valid = days != 0 && (allDay || start != end)
    BigButton(tr("Guardar", "Save"), enabled = valid) {
        val s = Schedule(
            orig?.id ?: Store.newScheduleId(), name.ifBlank { if (kind == Kind.Focus) "Focus" else tr("Sin Reels", "No Reels") },
            kind, days, if (allDay) 0 else start, if (allDay) 0 else end, orig?.on ?: true,
        )
        Store.saveSchedule(s)
        if (orig != null && Store.pendingFor("sched:${s.id}") != null) Toaster.show(tr("Relajar espera ${Store.RELAX_DAYS} días", "Relaxing waits ${Store.RELAX_DAYS} days"))
        Sheets.close()
    }
    if (orig != null) {
        Gap(10)
        BigButton(tr("Borrar horario", "Delete schedule"), bg = C.Bg, color = C.Coral) {
            Store.deleteSchedule(orig)
            if (orig.on && Store.committed) Toaster.show(tr("Borrar espera ${Store.RELAX_DAYS} días", "Deleting waits ${Store.RELAX_DAYS} days"))
            Sheets.close()
        }
    }
}

@Composable
private fun Label(t: String) = T(t, 13, 600, C.Muted, Modifier.padding(top = 18.u, bottom = 8.u))

@Composable
private fun TimeRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.u), verticalAlignment = Alignment.CenterVertically) {
        T(label, 15, 500, modifier = Modifier.weight(1f))
        Step(Ic.minus) { onChange((value - 15 + 1440) % 1440) }
        T(hhmm(value), 20, 650, modifier = Modifier.width(84.u), align = androidx.compose.ui.text.style.TextAlign.Center, tabular = true)
        Step(Ic.add) { onChange((value + 15) % 1440) }
    }
}

@Composable
private fun Step(icon: com.iagodf.argos.ui.IconData, onClick: () -> Unit) {
    Box(Modifier.size(40.u).clip(RoundedCornerShape(20.u)).background(C.Bg).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, 18, C.Ink, 2f)
    }
}
