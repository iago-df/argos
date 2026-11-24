package com.iagodf.argos.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.lock.Lock
import com.iagodf.argos.ui.BigButton
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.IconData
import com.iagodf.argos.ui.ItemRow
import com.iagodf.argos.ui.Orb
import com.iagodf.argos.ui.Ring
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.u
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val DAY = 86_400_000.0

/** commit to Argos, and later ask to switch it off: 30 days that do not count while a block is on */
@Composable
fun LockScreen() {
    Store.rev.intValue
    val ctx = LocalContext.current
    val owner = Lock.isOwner(ctx)
    val committed = Store.committed

    Column {
        if (committed) Title(tr("Desactivar", "Deactivate"), "Argos") else Title(tr("Bloquear", "Lock"), "Argos")
        Gap(22)

        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 20) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val left = ((Store.deactNeeded - Store.deactAccum) / DAY).let { ceil(it).toInt().coerceAtLeast(0) }
                val prog = when {
                    !committed -> 0f
                    !Store.deactRequested -> 0f
                    else -> (Store.deactAccum / Store.deactNeeded.toFloat())
                }
                Ring(prog, 130, 11) {
                    if (!committed) Orb(64, glow = false)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(if (Store.deactRequested) "$left" else "${Store.DEACTIVATE_DAYS}", 42, 700, ls = -.05f, lh = 1f)
                        T(if (left == 1 && Store.deactRequested) tr("día", "day") else tr("días", "days"), 13, 500, C.Muted)
                    }
                }
                Gap(16)
                val (head, sub) = when {
                    !committed -> tr("Hazlo inquebrantable", "Make it unbreakable") to
                        tr("Endurecer, al instante. Relajar, ${Store.RELAX_DAYS} días. Apagarlo, ${Store.DEACTIVATE_DAYS}.", "Stricter, right away. Softer, ${Store.RELAX_DAYS} days. Off, ${Store.DEACTIVATE_DAYS}.")
                    Store.deactReady -> tr("Ya puedes desactivarlo", "You can switch it off now") to tr("Han pasado los ${Store.DEACTIVATE_DAYS} días", "The ${Store.DEACTIVATE_DAYS} days are over")
                    Store.deactRequested -> {
                        val d = LocalDate.now().plusDays(left.toLong())
                        val f = DateTimeFormatter.ofPattern(if (Lang.en) "d MMM" else "d 'de' MMM", Lang.locale)
                        tr("Disponible el ${d.format(f)}", "Ready on ${d.format(f)}") to tr("No cuenta mientras hay un bloqueo", "It does not count while a block is on")
                    }
                    else -> tr("Pide la desactivación", "Request deactivation") to tr("Se cumplirá dentro de ${Store.DEACTIVATE_DAYS} días", "It will be ready in ${Store.DEACTIVATE_DAYS} days")
                }
                T(head, 15, 600, align = TextAlign.Center)
                T(sub, 13, 400, C.Muted, Modifier.padding(top = 4.u), align = TextAlign.Center)
            }
        }
        Gap(14)

        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 0) {
            Column(Modifier.padding(horizontal = 16.u, vertical = 4.u)) {
                Protect(Ic.trash, tr("No se puede desinstalar", "Can not be uninstalled"), owner, false)
                Protect(Ic.restart, tr("Reiniciar no lo quita", "Restarting does not remove it"), owner, true)
                Protect(Ic.clock, tr("Cambiar la hora no sirve", "Changing the time does not help"), owner, true)
                Protect(Ic.shield, tr("Ni modo seguro ni forzar detención", "No safe mode, no force stop"), owner, true)
            }
        }
        if (!owner) {
            Gap(10)
            T(
                tr("Para que sea inquebrantable, haz de Argos el propietario del dispositivo (Ajustes → Configurar).", "To make it truly unbreakable, set Argos as device owner (Settings → Setup)."),
                12, 500, C.Orange, Modifier.padding(horizontal = 22.u),
            )
        }
        Gap(22)

        Box(Modifier.padding(horizontal = 16.u)) {
            when {
                !committed -> BigButton(tr("Bloquear Argos", "Lock Argos"), icon = Ic.lock) { confirmLock() }
                Store.deactReady -> BigButton(tr("Desactivar Argos", "Deactivate Argos"), bg = C.Coral) {
                    Store.finishDeactivation(); Lock.release(ctx)
                    Toaster.show(tr("Argos desactivado. Ya puedes desinstalarlo.", "Argos is off. You can uninstall it now."))
                }
                Store.deactRequested -> BigButton(tr("Cancelar la solicitud", "Cancel the request"), bg = Color.White, color = C.Ink) {
                    Store.cancelDeactivation()
                    Toaster.show(tr("Bien hecho. Seguimos.", "Good call. Keep going."))
                }
                else -> BigButton(tr("Solicitar desactivación", "Request deactivation")) { confirmRequest() }
            }
        }
    }
}

@Composable
private fun Protect(icon: IconData, label: String, on: Boolean, divider: Boolean) {
    ItemRow(label, divider = divider, lead = { Tile(icon, C.Ink, size = 34) }) {
        Icon(Ic.lock, 17, if (on && Store.committed) C.Blue else C.Chev, 2f)
    }
}

private fun confirmLock() = Sheets.open(tr("¿Bloquear Argos?", "Lock Argos?")) {
    val ctx = LocalContext.current
    Points(
        listOf(
            tr("Activar bloqueos y endurecer horarios sigue siendo al instante.", "Turning blocks on and making schedules stricter stays instant."),
            tr("Quitar o suavizar algo esperará ${Store.RELAX_DAYS} días.", "Removing or softening anything will wait ${Store.RELAX_DAYS} days."),
            tr("Apagar Argos pedirá ${Store.DEACTIVATE_DAYS} días que no cuentan durante los bloqueos.", "Switching Argos off asks for ${Store.DEACTIVATE_DAYS} days that do not count during blocks."),
            if (Lock.isOwner(ctx)) tr("No se podrá desinstalar. La única salida rápida es restablecer el móvil de fábrica.", "It can not be uninstalled. The only quick way out is a factory reset.")
            else tr("Sin el modo propietario aún se podría desinstalar.", "Without device owner it could still be uninstalled."),
        )
    )
    Gap(18)
    BigButton(tr("Sí, bloquear", "Yes, lock it"), icon = Ic.lock) {
        Store.commit(); Lock.sync(ctx); Sheets.close()
        Toaster.show(tr("Argos es inquebrantable", "Argos is unbreakable"))
    }
}

private fun confirmRequest() = Sheets.open(tr("¿Pedir la desactivación?", "Request deactivation?")) {
    Points(
        listOf(
            tr("Empieza una cuenta de ${Store.DEACTIVATE_DAYS} días.", "A ${Store.DEACTIVATE_DAYS} day countdown starts."),
            tr("Mientras haya un bloqueo activo, no avanza.", "While a block is running it does not move."),
            tr("Puedes cancelarla cuando quieras.", "You can cancel it any time."),
        )
    )
    Gap(18)
    BigButton(tr("Solicitar", "Request")) {
        Store.requestDeactivation(); Sheets.close()
    }
}

@Composable
private fun Points(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.u)) {
        items.forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(10.u)) {
                Icon(Ic.check, 18, C.Blue, 2.4f, Modifier.padding(top = 1.u))
                T(it, 15, 500, C.Body, lh = 1.35f)
            }
        }
    }
}
