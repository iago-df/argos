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
            Box(Modifier.size(38.u).clip(CircleShape).background(C.Coral), contentAlignment = Alignment.Center) {
                Icon(Ic.info, 20, Color.White, 2.2f)
            }
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
            Box(Modifier.size(38.u).clip(RoundedCornerShape(12.u)).background(C.Ink), contentAlignment = Alignment.Center) {
                Icon(Ic.shield, 19, Color.White, 2f)
            }
            Spacer(Modifier.width(12.u))
            Column(Modifier.weight(1f)) {
                T(tr("Termina de configurar Argos", "Finish setting up Argos"), 15, 650)
                T(tr("$done de $total pasos · sin esto no bloquea", "$done of $total steps · it can not block without it"), 12, 400, C.Soft, Modifier.padding(top = 2.u))
            }
            Icon(Ic.chev, 18, C.Ink, 2f)
        }
    }
}

fun openSetup() = Sheets.open(tr("Configurar", "Setup")) { SetupBody() }

@Composable
private fun SetupBody() {
    val ctx = LocalContext.current
    LifecycleResumeEffect(Unit) { SetupState.rev++; onPauseOrDispose { } }
    SetupState.rev
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SetupState.rev++ }

    Step(
        1, tr("Servicio de accesibilidad", "Accessibility service"),
        tr("Para ver Reels y Shorts en pantalla y sacarte de ahí. Busca Argos y actívalo.", "To see Reels and Shorts on screen and get you out. Find Argos and turn it on."),
        ArgosService.enabled(ctx),
    ) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Step(
        2, tr("Acceso de uso", "Usage access"),
        tr("Para calcular el tiempo que recuperas.", "To work out the time you recover."),
        Usage.granted(ctx),
    ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Step(
        3, tr("Notificaciones", "Notifications"),
        tr("Una notificación discreta mantiene a Argos despierto.", "A quiet notification keeps Argos awake."),
        notifOk(ctx),
    ) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Gap(22)
    T(tr("Modo inquebrantable", "Unbreakable mode"), 17, 700)
    T(
        tr(
            "Opcional y desde un ordenador. Quita antes las cuentas del móvil (Ajustes → Cuentas), activa la depuración USB y ejecuta:",
            "Optional, from a computer. First remove the accounts from the phone (Settings → Accounts), turn on USB debugging and run:",
        ),
        14, 400, C.Muted, Modifier.padding(top = 6.u, bottom = 10.u), lh = 1.35f,
    )
    Command(Lock.ADB_OWNER, Lock.isOwner(ctx))
    Gap(8)
    Command(Lock.ADB_SETTINGS, Lock.canWriteSettings(ctx))
    T(
        tr(
            "El primero impide desinstalarlo, el modo seguro y cambiar la hora. El segundo le deja volver a encender su servicio y usar el blanco y negro real. Después, bloquea Argos desde la tarjeta de Inicio.",
            "The first one blocks uninstalling, safe mode and changing the time. The second lets it turn its own service back on and use real black and white. Then lock Argos from the Home card.",
        ),
        13, 400, C.Muted, Modifier.padding(top = 10.u), lh = 1.35f,
    )
}

@Composable
private fun Step(n: Int, title: String, sub: String, ok: Boolean, open: () -> Unit) {
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
        ) { T(tr("Abrir", "Open"), 13, 600, Color.White) }
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
        T(cmd, 11, 500, Color.White.copy(alpha = .9f), Modifier.weight(1f), lh = 1.35f)
        if (ok) Icon(Ic.check, 16, C.BlueSoft, 2.6f) else T(tr("Copiar", "Copy"), 12, 600, C.BlueSoft)
    }
}
