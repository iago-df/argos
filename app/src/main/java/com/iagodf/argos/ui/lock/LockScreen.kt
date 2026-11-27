package com.iagodf.argos.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.iagodf.argos.data.Apps
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
import com.iagodf.argos.ui.Toggle
import com.iagodf.argos.ui.focus.AppIcon
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u
import com.iagodf.argos.ui.us
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val DAY = 86_400_000.0

/**
 * the lock tab. before locking: everything the lock will add (paused apps, blocked words) is set up here,
 * and nothing happens until you lock. once locked: the 30 day request to switch it off
 */
@Composable
fun LockScreen() {
    Store.rev.intValue
    val ctx = LocalContext.current
    val owner = Lock.isOwner(ctx)
    val committed = Store.committed

    Column {
        if (committed) Title(tr("Bloqueo", "Lock"), tr("activo", "on")) else Title(tr("Bloqueo", "Lock"), tr("total", "it down"))
        Gap(10)
        com.iagodf.argos.ui.Subtitle(
            if (committed) tr("Endurecer, al instante. Relajar, ${Store.RELAX_DAYS} días. Apagarlo, ${Store.DEACTIVATE_DAYS}.", "Stricter, right away. Softer, ${Store.RELAX_DAYS} days. Off, ${Store.DEACTIVATE_DAYS}.")
            else tr("Déjalo todo listo. No pasa nada hasta que lo actives.", "Get it all ready. Nothing happens until you turn it on."),
        )
        Gap(18)

        StatusCard(committed)
        if (Lock.TESTING) {
            Gap(10)
            T(
                tr("Versión de pruebas: el bloqueo se puede quitar al momento. Más adelante solo quedará la espera de ${Store.DEACTIVATE_DAYS} días.",
                    "Test version: the lock can be switched off right away. Later only the ${Store.DEACTIVATE_DAYS} day wait will be left."),
                12, 500, C.Blue, Modifier.padding(horizontal = 22.u), lh = 1.35f,
            )
        }
        Gap(22)

        Section(tr("Qué bloquea", "What it blocks"))
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 0) {
            Column(Modifier.padding(horizontal = 16.u, vertical = 4.u)) {
                val apps = Store.lockApps().count { Apps.installed(ctx, it) }
                ItemRow(
                    tr("Apps pausadas", "Paused apps"),
                    if (apps == 0) tr("Ninguna todavía", "None yet") else tr("$apps apps no se abrirán", "$apps apps will not open"),
                    divider = false, lead = { Tile(Ic.apps, C.Coral) }, onClick = { openApps() },
                ) { Icon(Ic.chev, 18, C.Chev, 2f) }
                ItemRow(
                    tr("Bloqueos, webs, Focus y horarios", "Blocks, sites, Focus and schedules"),
                    tr("Se quedan protegidos tal como los tengas", "They stay protected as you set them"),
                    lead = { Tile(Ic.cal, C.Orange) },
                ) {}
            }
        }
        Store.pendingFor("lock_apps")?.let { PendingNote(tr("Apps pausadas", "Paused apps"), it.applyAt) }
        Gap(22)

        Section(tr("Qué no se podrá hacer", "What you will not be able to do"))
        Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 0) {
            Column(Modifier.padding(horizontal = 16.u, vertical = 4.u)) {
                Protect(Ic.trash, tr("Desinstalar Argos", "Uninstall Argos"), owner, false)
                Protect(Ic.shield, tr("Forzar su detención o borrar sus datos", "Force stop it or wipe its data"), owner, true)
                Protect(Ic.restart, tr("Quitarlo reiniciando o en modo seguro", "Remove it by restarting or in safe mode"), owner, true)
                Protect(Ic.clock, tr("Cambiar la hora para saltarte horarios", "Change the time to skip schedules"), owner, true)
            }
        }
        T(
            tr("El resto del móvil sigue igual: tus otras apps se instalan, actualizan y desinstalan como siempre.",
                "The rest of the phone stays the same: your other apps install, update and uninstall as always."),
            12, 500, C.Muted, Modifier.padding(start = 22.u, end = 22.u, top = 8.u), lh = 1.35f,
        )
        Gap(22)
        if (!committed || !owner) {
            UnbreakableCard()
            Gap(24)
        }

        Column(Modifier.padding(horizontal = 16.u), verticalArrangement = Arrangement.spacedBy(10.u)) {
            when {
                !committed -> BigButton(tr("Activar el bloqueo", "Turn the lock on"), icon = Ic.lock) { confirmLock() }
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
            if (committed && Lock.TESTING) BigButton(tr("Quitar ya (pruebas)", "Switch off now (testing)"), bg = Color.White, color = C.Coral) { confirmUnlockNow() }
            if (!committed && owner) BigButton(tr("Quitar el modo inquebrantable", "Drop unbreakable mode"), bg = Color.White, color = C.Ink) { confirmDropOwner() }
        }
    }
}

@Composable
private fun StatusCard(committed: Boolean) {
    Card(Modifier.padding(horizontal = 16.u).fillMaxWidth(), padding = 20) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val left = ((Store.deactNeeded - Store.deactAccum) / DAY).let { ceil(it).toInt().coerceAtLeast(0) }
            val prog = if (!committed || !Store.deactRequested) 0f else Store.deactAccum / Store.deactNeeded.toFloat()
            Ring(prog, 130, 11) {
                if (!committed) Orb(64, glow = false)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(if (Store.deactRequested) "$left" else "${Store.DEACTIVATE_DAYS}", 42, 700, ls = -.05f, lh = 1f)
                    T(if (left == 1 && Store.deactRequested) tr("día", "day") else tr("días", "days"), 13, 500, C.Muted)
                }
            }
            Gap(16)
            val (head, sub) = when {
                !committed -> tr("Todavía no está activo", "Not on yet") to
                    tr("Ajusta lo de abajo y actívalo cuando lo tengas claro.", "Set up what is below and turn it on when you are sure.")
                Store.deactReady -> tr("Ya puedes desactivarlo", "You can switch it off now") to tr("Han pasado los ${Store.DEACTIVATE_DAYS} días", "The ${Store.DEACTIVATE_DAYS} days are over")
                Store.deactRequested -> {
                    val d = LocalDate.now().plusDays(left.toLong())
                    val f = DateTimeFormatter.ofPattern(if (Lang.en) "d MMM" else "d 'de' MMM", Lang.locale)
                    tr("Disponible el ${d.format(f)}", "Ready on ${d.format(f)}") to tr("No cuenta mientras hay un bloqueo", "It does not count while a block is on")
                }
                else -> tr("Bloqueo activo", "Lock on") to tr("Para apagarlo, pide la desactivación: ${Store.DEACTIVATE_DAYS} días", "To switch it off, request it: ${Store.DEACTIVATE_DAYS} days")
            }
            T(head, 15, 600, align = TextAlign.Center)
            T(sub, 13, 400, C.Muted, Modifier.padding(top = 4.u), align = TextAlign.Center)
        }
    }
}

@Composable
private fun Section(text: String) = T(text, 13, 600, C.Muted, Modifier.padding(start = 22.u, bottom = 8.u))

@Composable
private fun PendingNote(what: String, at: Long) =
    T("$what: " + com.iagodf.argos.ui.blocks.waitText(at), 12, 500, C.Orange, Modifier.padding(start = 22.u, top = 8.u))

@Composable
private fun Protect(icon: IconData, label: String, on: Boolean, divider: Boolean) {
    ItemRow(label, divider = divider, lead = { Tile(icon, C.Ink, size = 34) }) {
        Icon(Ic.lock, 17, if (on && Store.committed) C.Blue else C.Chev, 2f)
    }
}

// ---------- what it blocks ----------

private fun openApps() = Sheets.open(tr("Apps pausadas", "Paused apps")) {
    Store.rev.intValue
    val ctx = LocalContext.current
    T(
        tr("Mientras el bloqueo esté activo, estas apps se pausan: salen en gris y no se abren. Teléfono, mensajes, contactos, cámara, galería, correo y reloj no se pueden pausar.",
            "While the lock is on, these apps are paused: greyed out, they do not open. Phone, messages, contacts, camera, gallery, email and clock can not be paused."),
        14, 400, C.Muted, Modifier.padding(bottom = 10.u), lh = 1.35f,
    )
    if (Store.committed) T(
        tr("Quitar apps de la lista espera ${Store.RELAX_DAYS} días.", "Taking apps off the list waits ${Store.RELAX_DAYS} days."),
        13, 500, C.Orange, Modifier.padding(bottom = 10.u),
    )
    val all = remember { val e = Apps.essentials(ctx); Apps.launchable(ctx).filter { it.pkg !in e } }
    val sel = Store.lockApps()
    all.forEachIndexed { i, a ->
        ItemRow(a.label, divider = i > 0, lead = { AppIcon(a.pkg, 38) }) {
            Toggle(a.pkg in sel) { on -> Store.setLockApps(if (on) sel + a.pkg else sel - a.pkg) }
        }
    }
}

// ---------- turning it on and off ----------

/** first of the confirmations: what is going to happen */
private fun confirmLock() = Sheets.open(tr("¿Activar el bloqueo?", "Turn the lock on?")) {
    val ctx = LocalContext.current
    val apps = Store.lockApps().count { Apps.installed(ctx, it) }
    Points(
        listOfNotNull(
            if (apps > 0) tr("$apps apps se pausarán.", "$apps apps will be paused.") else null,
            tr("Endurecer algo seguirá siendo al instante. Quitar o suavizar algo esperará ${Store.RELAX_DAYS} días.", "Making things stricter stays instant. Removing or softening anything waits ${Store.RELAX_DAYS} days."),
            tr("Apagarlo pedirá ${Store.DEACTIVATE_DAYS} días, que no cuentan durante los bloqueos.", "Switching it off takes ${Store.DEACTIVATE_DAYS} days that do not count during blocks."),
            if (Lock.isOwner(ctx)) tr("No se podrá desinstalar. La única salida rápida es restablecer el móvil de fábrica.", "It can not be uninstalled. The only quick way out is a factory reset.")
            else tr("Sin el modo propietario aún se podría desinstalar.", "Without device owner it could still be uninstalled."),
        )
    )
    Gap(18)
    BigButton(tr("Continuar", "Continue")) { Sheets.close(); confirmLockHold() }
}

/** the last one: hold the button for three seconds, so it can not happen by accident */
private fun confirmLockHold() = Sheets.open(tr("Última confirmación", "Last confirmation")) {
    val ctx = LocalContext.current
    T(
        tr("Mantén pulsado el botón 3 segundos para activar el bloqueo.", "Hold the button for 3 seconds to turn the lock on."),
        15, 500, C.Body, Modifier.padding(bottom = 18.u), lh = 1.35f,
    )
    HoldButton(tr("Mantén para bloquear", "Hold to lock")) {
        Store.commit(); Lock.sync(ctx); Sheets.close()
        Toaster.show(tr("Bloqueo activo", "Lock on"))
    }
    Gap(10)
    BigButton(tr("Mejor no", "Not now"), bg = Color.White, color = C.Ink) { Sheets.close() }
}

@Composable
private fun HoldButton(label: String, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxWidth().height(56.u).clip(RoundedCornerShape(28.u)).background(C.Ink)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val run = scope.launch {
                        p.animateTo(1f, tween(3000, easing = LinearEasing))
                        onDone()
                    }
                    tryAwaitRelease()
                    if (p.value < 1f) { run.cancel(); scope.launch { p.animateTo(0f, tween(250)) } }
                })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p.value).background(C.Blue))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.lock, 19, Color.White, 2f)
            T(label, 17, 650, Color.White)
        }
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
    BigButton(tr("Solicitar", "Request")) { Store.requestDeactivation(); Sheets.close() }
}

private fun confirmUnlockNow() = Sheets.open(tr("¿Quitar el bloqueo ya?", "Switch the lock off now?")) {
    val ctx = LocalContext.current
    Points(
        listOf(
            tr("Solo existe en la versión de pruebas.", "This only exists in the test version."),
            tr("Las apps dejan de estar pausadas y Argos vuelve a como estaba antes de bloquearlo.", "Apps stop being paused and Argos goes back to how it was before locking."),
            tr("El modo propietario se queda: no hace falta repetir los comandos.", "Device owner stays: the commands are not needed again."),
        )
    )
    Gap(18)
    BigButton(tr("Quitar el bloqueo", "Switch it off"), bg = C.Coral) {
        Lock.unlockNow(ctx); Sheets.close()
        Toaster.show(tr("Bloqueo quitado", "Lock off"))
    }
}

private fun confirmDropOwner() = Sheets.open(tr("¿Quitar el modo inquebrantable?", "Drop unbreakable mode?")) {
    val ctx = LocalContext.current
    Points(
        listOf(
            tr("Argos deja de ser propietario del dispositivo ahora mismo.", "Argos stops being device owner right now."),
            tr("Podrás desinstalarlo como cualquier app.", "You will be able to uninstall it like any app."),
            tr("Para volver a tenerlo hay que repetir los comandos desde el ordenador.", "To get it back the commands have to be run again from a computer."),
        )
    )
    Gap(18)
    BigButton(tr("Quitarlo", "Drop it"), bg = C.Coral) {
        val ok = Lock.dropOwner(ctx); Sheets.close()
        Toaster.show(if (ok) tr("Listo. Ya puedes desinstalar Argos.", "Done. You can uninstall Argos now.") else tr("No se ha podido", "It did not work"))
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
