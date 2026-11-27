package com.iagodf.argos.ui.lock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iagodf.argos.data.tr
import com.iagodf.argos.lock.Lock
import com.iagodf.argos.lock.SetupApp
import com.iagodf.argos.ui.BigButton
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Card
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.Toaster
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u

/**
 * the optional part: making Argos device owner, once. without a computer through Argos Setup (Android 11+),
 * or with a computer and a cable. nothing changes until the lock is turned on
 */
@Composable
fun UnbreakableCard() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { rev++; onPauseOrDispose { } }
    rev
    val owner = Lock.isOwner(ctx)
    val settings = Lock.canWriteSettings(ctx)
    val done = owner && settings

    Card(Modifier.padding(horizontal = 16.u).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(Ic.shield, C.Ink)
            Spacer(Modifier.width(10.u))
            T(tr("Modo inquebrantable", "Unbreakable mode"), 16, 650, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(12.u)).background(if (done) C.BlueTint else C.Bg).padding(horizontal = 10.u, vertical = 5.u)) {
                T(if (done) tr("Listo", "Ready") else tr("Opcional", "Optional"), 12, 600, if (done) C.Blue else C.Muted)
            }
        }
        T(
            when {
                done -> tr("Argos es propietario del dispositivo. No cambia nada hasta que actives el bloqueo.", "Argos is device owner. Nothing changes until you turn the lock on.")
                owner -> tr("Argos ya es propietario. Falta el segundo permiso, para que su servicio vuelva a encenderse solo.", "Argos is already device owner. The second permission is missing, so its service can turn itself back on.")
                else -> tr("Sin él, Argos se podría desinstalar aunque esté bloqueado. Se hace una sola vez y no cambia nada hasta que actives el bloqueo.",
                    "Without it, Argos could be uninstalled even when locked. It is done once and changes nothing until you turn the lock on.")
            },
            13, 400, C.Muted, Modifier.padding(top = 10.u), lh = 1.35f,
        )
        if (!done) {
            Gap(14)
            if (SetupApp.supported) {
                BigButton(tr("Hacerlo sin ordenador", "Do it without a computer"), icon = Ic.phone) {
                    if (!SetupApp.open(ctx)) Toaster.show(tr("Deja que Argos instale apps y vuelve a tocar", "Let Argos install apps and tap again"))
                }
                T(
                    tr("Instala Argos Setup, que va dentro de Argos. Te guía, hace los comandos ella sola y luego se borra.",
                        "Installs Argos Setup, which comes inside Argos. It guides you, runs the commands on its own and then removes itself."),
                    12, 400, C.Muted, Modifier.padding(top = 8.u, bottom = 10.u), lh = 1.35f,
                )
            }
            BigButton(tr("Con ordenador y cable", "With a computer and a cable"), bg = Color.White, color = C.Ink) { cableSheet() }
        }
    }
}

private fun cableSheet() = Sheets.open(tr("Con ordenador y cable", "With a computer and a cable")) {
    val ctx = LocalContext.current
    T(
        tr("Sirve en cualquier Android y no usa la Wi-Fi. Se hace una sola vez; al actualizar Argos no hay que repetirlo.",
            "Works on any Android and does not use Wi-Fi. It is done once; updating Argos does not need it again."),
        14, 400, C.Muted, Modifier.padding(bottom = 14.u), lh = 1.35f,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.u)) {
        listOf(
            tr("Quita todas las cuentas de Ajustes → Cuentas (Google, Outlook, Samsung, WhatsApp…) y cualquier perfil de trabajo, carpeta segura o segundo espacio. Ten a mano sus contraseñas.",
                "Remove every account in Settings → Accounts (Google, Outlook, Samsung, WhatsApp…) and any work profile, secure folder or second space. Keep their passwords at hand."),
            tr("En el ordenador, descarga \"SDK Platform-Tools\" de developer.android.com y descomprímelo. Ahí está adb.",
                "On the computer, download \"SDK Platform-Tools\" from developer.android.com and unzip it. adb is in there."),
            tr("En el móvil: Información del teléfono, toca 7 veces Número de compilación. Luego Opciones de desarrollador → Depuración USB.",
                "On the phone: About phone, tap Build number 7 times. Then Developer options → USB debugging."),
            tr("Conecta el cable, acepta \"¿Permitir depuración USB?\" y, en una terminal dentro de esa carpeta, comprueba que adb devices muestra tu móvil.",
                "Plug the cable in, accept \"Allow USB debugging?\" and, in a terminal inside that folder, check that adb devices lists your phone."),
            tr("Ejecuta los dos comandos. El primero responde \"Success\"; el segundo no dice nada. Si dice que hay cuentas, quita la que falte y repítelo.",
                "Run the two commands. The first one answers \"Success\"; the second one says nothing. If it says there are accounts, remove the one left and run it again."),
            tr("Desconecta el cable y vuelve a añadir tus cuentas.", "Unplug the cable and add your accounts back."),
        ).forEachIndexed { i, t ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.u)) {
                T("${i + 1}", 13, 700, C.Blue, Modifier.width(14.u))
                T(t, 14, 400, C.Body, Modifier.weight(1f), lh = 1.35f)
            }
        }
    }
    Gap(16)
    Command(Lock.ADB_OWNER, Lock.isOwner(ctx))
    Gap(8)
    Command(Lock.ADB_SETTINGS, Lock.canWriteSettings(ctx))
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
