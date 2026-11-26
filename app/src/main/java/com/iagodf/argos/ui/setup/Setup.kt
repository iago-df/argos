package com.iagodf.argos.ui.setup

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iagodf.argos.data.Usage
import com.iagodf.argos.data.tr
import com.iagodf.argos.engine.ArgosService
import com.iagodf.argos.lock.Lock
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u

/** recomposes the setup bits every time you come back from the system settings */
object SetupState {
    var rev by androidx.compose.runtime.mutableIntStateOf(0)
}

private fun notifOk(ctx: Context) =
    Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** the steps that make Argos work, in order. the last two are only for the unbreakable mode */
fun setupDone(ctx: Context): Pair<Int, Int> {
    val steps = listOf(ArgosService.enabled(ctx), Usage.granted(ctx), notifOk(ctx))
    return steps.count { it } to steps.size
}

/** true when the blocking service is really running, refreshed every time you come back to the app */
@Composable
fun serviceOn(): Boolean {
    val ctx = LocalContext.current
    LifecycleResumeEffect(Unit) { SetupState.rev++; onPauseOrDispose { } }
    SetupState.rev
    return ArgosService.enabled(ctx)
}

/** red banner when the blocks are on but nothing can enforce them */
@Composable
fun PausedCard(modifier: Modifier = Modifier) {
    if (serviceOn()) return
    Card(modifier.padding(horizontal = 16.u).fillMaxWidth(), bg = Color(0xFFFFE9E6), shadow = false, onClick = { openSetup() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.iagodf.argos.ui.Tile(Ic.info, C.Coral)
            Spacer(Modifier.width(12.u))
            Column(Modifier.weight(1f)) {
                T(tr("Argos está en pausa", "Argos is paused"), 15, 650)
                T(
                    tr("Activa su servicio de accesibilidad para que bloquee", "Turn on its accessibility service so it can block"),
                    12, 400, C.Soft, Modifier.padding(top = 2.u),
                )
            }
            Icon(Ic.chev, 18, C.Ink, 2f)
        }
    }
}

@Composable
fun SetupCard() {
    val ctx = LocalContext.current
    LifecycleResumeEffect(Unit) { SetupState.rev++; onPauseOrDispose { } }
    SetupState.rev
    val (done, total) = setupDone(ctx)
    if (done == total) return
    Card(
        Modifier.padding(start = 16.u, end = 16.u, bottom = 16.u).fillMaxWidth(),
        brush = Brush.linearGradient(listOf(Color(0xFFFFE6DA), Color(0xFFE6E1FF))),
        onClick = { openSetup() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.iagodf.argos.ui.Tile(Ic.shield, C.Ink)
            Spacer(Modifier.width(12.u))
            Column(Modifier.weight(1f)) {
                T(tr("Termina de configurar Argos", "Finish setting up Argos"), 15, 650)
                T(tr("$done de $total pasos · sin esto no bloquea", "$done of $total steps · it can not block without it"), 12, 400, C.Soft, Modifier.padding(top = 2.u))
            }
            Icon(Ic.chev, 18, C.Ink, 2f)
        }
    }
}

/** system pages, always opened fresh so an old settings screen does not come back instead */
object SystemPages {
    private fun go(ctx: Context, i: Intent) = runCatching {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }.isSuccess

    /**
     * accessibility with Argos highlighted in the list. android does not let other apps open the switch
     * itself, this is as close as it gets. with the adb permission it is switched on right here instead
     */
    fun accessibility(ctx: Context) {
        if (Lock.enableService(ctx)) {
            SetupState.rev++
            Toaster.show(tr("Servicio activado", "Service turned on"))
            return
        }
        val key = ArgosService.component(ctx).flattenToString()
        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", android.os.Bundle().apply { putString(":settings:fragment_args_key", key) })
        go(ctx, i)
    }

    fun appInfo(ctx: Context) =
        go(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null)))

    fun usage(ctx: Context) = go(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    /**
     * android 13+ marks apps installed from a downloaded file as "restricted": their accessibility switch
     * is greyed out until you allow it from the app info page. apps can not read that flag, but they can
     * read how they were installed, which is what android looks at
     */
    fun sideloaded(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val src = runCatching { ctx.packageManager.getInstallSourceInfo(ctx.packageName).packageSource }.getOrNull()
        return src == android.content.pm.PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ||
            src == android.content.pm.PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    }
}

fun openSetup() = Sheets.open(tr("Configurar", "Setup")) { SetupBody() }

@Composable
private fun SetupBody() {
    val ctx = LocalContext.current
    LifecycleResumeEffect(Unit) { SetupState.rev++; onPauseOrDispose { } }
    SetupState.rev
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SetupState.rev++ }

    val serviceOk = ArgosService.enabled(ctx)
    Step(
        1, tr("Servicio de accesibilidad", "Accessibility service"),
        tr("Para ver Reels y Shorts y sacarte de ahí. Te llevo a la lista: toca Argos y actívalo.", "To see Reels and Shorts and get you out. It opens the list: tap Argos and turn it on."),
        serviceOk,
    ) { SystemPages.accessibility(ctx) }
    if (!serviceOk && SystemPages.sideloaded(ctx)) RestrictedGuide()
    Step(
        2, tr("Acceso de uso", "Usage access"),
        tr("Para calcular el tiempo que recuperas.", "To work out the time you recover."),
        Usage.granted(ctx),
    ) { SystemPages.usage(ctx) }
    Step(
        3, tr("Notificaciones", "Notifications"),
        tr("Una notificación discreta mantiene a Argos despierto.", "A quiet notification keeps Argos awake."),
        notifOk(ctx),
    ) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Gap(22)
    T(tr("Modo inquebrantable", "Unbreakable mode"), 17, 700)
    T(
        tr(
            "Opcional. Se hace una sola vez desde cualquier ordenador y no cambia nada hasta que actives el bloqueo en la pestaña Bloqueo.",
            "Optional. It is done once from any computer and changes nothing until you turn the lock on in the Lock tab.",
        ),
        14, 400, C.Muted, Modifier.padding(top = 6.u, bottom = 10.u), lh = 1.35f,
    )
    Column(Modifier.padding(bottom = 12.u), verticalArrangement = Arrangement.spacedBy(8.u)) {
        listOf(
            tr("Quita todas las cuentas de Ajustes → Cuentas (Google, Outlook, Samsung, WhatsApp…) y cualquier perfil de trabajo, carpeta segura o segundo espacio. Ten a mano sus contraseñas: luego las vuelves a añadir.",
                "Remove every account in Settings → Accounts (Google, Outlook, Samsung, WhatsApp…) and any work profile, secure folder or second space. Keep their passwords at hand: you add them back afterwards."),
            tr("En el ordenador, descarga \"SDK Platform-Tools\" de developer.android.com y descomprímelo. Ahí está adb.",
                "On the computer, download \"SDK Platform-Tools\" from developer.android.com and unzip it. adb is in there."),
            tr("En el móvil: Ajustes → Información del teléfono, toca 7 veces Número de compilación. Luego Opciones de desarrollador → Depuración USB.",
                "On the phone: Settings → About phone, tap Build number 7 times. Then Developer options → USB debugging."),
            tr("Conecta el cable, acepta \"¿Permitir depuración USB?\" y, en una terminal dentro de esa carpeta, comprueba que adb devices muestra tu móvil.",
                "Plug the cable in, accept \"Allow USB debugging?\" and, in a terminal inside that folder, check that adb devices lists your phone."),
            tr("Ejecuta los dos comandos. El primero responde \"Success\"; el segundo no dice nada. Si dice que hay cuentas, quita la que falte y repítelo.",
                "Run the two commands. The first one answers \"Success\"; the second one says nothing. If it says there are accounts, remove the one left and run it again."),
            tr("Desconecta el cable (el ordenador ya no hace falta) y vuelve a añadir tus cuentas.",
                "Unplug the cable (the computer is not needed anymore) and add your accounts back."),
        ).forEachIndexed { i, t ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.u)) {
                T("${i + 1}", 13, 700, C.Blue, Modifier.width(14.u))
                T(t, 13, 400, C.Body, Modifier.weight(1f), lh = 1.35f)
            }
        }
    }
    Command(Lock.ADB_OWNER, Lock.isOwner(ctx))
    Gap(8)
    Command(Lock.ADB_SETTINGS, Lock.canWriteSettings(ctx))
    T(
        tr(
            "El primero hace a Argos propietario del dispositivo; el segundo le deja volver a encender su servicio y usar el blanco y negro real. Hasta que actives el bloqueo puedes quitarlo con un botón en la pestaña Bloqueo. Al actualizar Argos no hay que repetirlos.",
            "The first one makes Argos device owner; the second lets it turn its own service back on and use real black and white. Until you turn the lock on you can drop it with a button in the Lock tab. Updating Argos does not need them again.",
        ),
        13, 400, C.Muted, Modifier.padding(top = 10.u), lh = 1.35f,
    )
}

@Composable
private fun Step(n: Int, title: String, sub: String, ok: Boolean, button: String = tr("Abrir", "Open"), open: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.u), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.u).clip(CircleShape).background(if (ok) C.Blue else C.Bg),
            contentAlignment = Alignment.Center,
        ) { if (ok) Icon(Ic.check, 16, Color.White, 2.6f) else T("$n", 14, 700, C.Muted) }
        Spacer(Modifier.width(12.u))
        Column(Modifier.weight(1f)) {
            T(title, 15, 600)
            T(sub, 12, 400, C.Muted, Modifier.padding(top = 2.u), lh = 1.3f)
        }
        Spacer(Modifier.width(10.u))
        if (!ok) Box(
            Modifier.clip(RoundedCornerShape(16.u)).background(C.Ink).tap(onClick = open).padding(horizontal = 14.u, vertical = 8.u),
        ) { T(button, 13, 600, Color.White) }
    }
}

/** what the "restricted setting" warning is and the taps that allow it */
@Composable
private fun RestrictedGuide() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(start = 44.u, bottom = 8.u).clip(RoundedCornerShape(18.u)).background(C.Bg).padding(14.u),
        verticalArrangement = Arrangement.spacedBy(8.u),
    ) {
        T(tr("¿Sale en gris o dice \"acceso denegado\"?", "Greyed out, or \"access denied\"?"), 14, 650)
        T(
            tr(
                "Android lo hace con cualquier app que no venga de la Play Store y avisa de que \"puede poner en riesgo tus datos\". Argos no envía nada fuera de tu móvil. Para permitirlo:",
                "Android does it with any app that does not come from the Play Store and warns it \"could put your data at risk\". Argos sends nothing off your phone. To allow it:",
            ),
            13, 400, C.Body, lh = 1.35f,
        )
        listOf(
            tr("Abre la información de Argos con el botón de abajo.", "Open Argos app info with the button below."),
            tr("Toca ⋮ arriba a la derecha → Permitir ajustes restringidos, y confirma.", "Tap ⋮ at the top right → Allow restricted settings, and confirm."),
            tr("Vuelve y toca Abrir otra vez: ya se podrá activar.", "Come back and tap Open again: now it can be turned on."),
        ).forEachIndexed { i, t ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.u)) {
                T("${i + 1}.", 13, 700, C.Blue)
                T(t, 13, 500, C.Ink, lh = 1.35f)
            }
        }
        Box(
            Modifier.padding(top = 4.u).clip(RoundedCornerShape(16.u)).background(Color.White).tap { SystemPages.appInfo(ctx) }
                .padding(horizontal = 14.u, vertical = 9.u),
        ) { T(tr("Información de Argos", "Argos app info"), 13, 600, C.Blue) }
    }
}

@Composable
private fun Command(cmd: String, ok: Boolean) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.u)).background(C.Ink)
            .tap {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("adb", cmd))
                Toaster.show(tr("Copiado", "Copied"))
            }
            .padding(horizontal = 14.u, vertical = 12.u),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.u),
    ) {
        T(cmd.replace(" android.permission", "\nandroid.permission"), 11, 500, Color.White.copy(alpha = .9f), Modifier.weight(1f), lh = 1.35f)
        if (ok) Icon(Ic.check, 16, C.BlueSoft, 2.6f) else T(tr("Copiar", "Copy"), 12, 600, C.BlueSoft)
    }
}

