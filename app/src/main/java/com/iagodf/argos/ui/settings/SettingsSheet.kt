package com.iagodf.argos.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.tr
import com.iagodf.argos.engine.Diag
import com.iagodf.argos.ui.Toggle
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.ItemRow
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.setup.openSetup
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u

fun openSettings() = Sheets.open(null) { SettingsBody() }

@Composable
private fun SettingsBody() {
    val ctx = LocalContext.current
    T(tr("Ajustes", "Settings"), 22, 700, ls = -.03f, modifier = Modifier.padding(top = 4.u, bottom = 14.u))

    T(tr("Idioma", "Language"), 13, 600, C.Muted, Modifier.padding(bottom = 8.u))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.u)).background(C.Bg).padding(4.u),
        horizontalArrangement = Arrangement.spacedBy(4.u),
    ) {
        listOf(true to "English", false to "Español").forEach { (en, label) ->
            val on = Lang.en == en
            val bg by animateColorAsState(if (on) Color.White else Color.Transparent, label = "lang")
            Box(
                Modifier.weight(1f).height(44.u).clip(RoundedCornerShape(14.u)).background(bg).tap { Lang.set(ctx, en) },
                contentAlignment = Alignment.Center,
            ) { T(label, 15, 600, if (on) C.Ink else C.Muted) }
        }
    }
    Gap(18)
    ItemRow(tr("Configurar", "Setup"), tr("Permisos y modo inquebrantable", "Permissions and unbreakable mode"), divider = false,
        lead = { Tile(Ic.shield, C.Ink) }, onClick = { openSetup() }) { Icon(Ic.chev, 18, C.Chev, 2f) }
    // a recording of what instagram and youtube show, to tune argos to a real phone
    var rec by remember { mutableStateOf(Diag.on) }
    var size by remember { mutableLongStateOf(Diag.size(ctx)) }
    ItemRow(
        tr("Grabar pantallas", "Record screens"),
        if (rec) tr("Grabando. Usa Instagram y vuelve", "Recording. Use Instagram and come back")
        else tr("Para ajustar Argos a tu móvil", "To tune Argos to your phone"),
        lead = { Tile(Ic.eye, C.Orange) },
    ) { Toggle(rec, onChange = { Diag.set(ctx, it); rec = it; size = Diag.size(ctx) }) }
    if (size > 0) {
        ItemRow(
            tr("Enviar grabación", "Send recording"),
            tr("${size / 1024} KB · incluye textos cortos de la pantalla", "${size / 1024} KB · includes short texts from the screen"),
            lead = { Tile(Ic.info, C.Blue) }, onClick = { Diag.share(ctx) },
        ) { Icon(Ic.chev, 18, C.Chev, 2f) }
    }
    ItemRow("GitHub", "github.com/iago-df/argos",
        lead = { Tile(Ic.globe, C.Blue) },
        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iago-df/argos")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
    ) { Icon(Ic.chev, 18, C.Chev, 2f) }
    Gap(14)
    val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    T(
        tr("Argos $version · todo se queda en tu móvil", "Argos $version · everything stays on your phone"),
        12, 500, C.Sub, Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
