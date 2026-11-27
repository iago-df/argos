package com.iagodf.argos.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Gap
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.Icon
import com.iagodf.argos.ui.ItemRow
import com.iagodf.argos.ui.Sheets
import com.iagodf.argos.ui.T
import com.iagodf.argos.ui.Tile
import com.iagodf.argos.ui.tap
import com.iagodf.argos.ui.u
import com.iagodf.argos.ui.us

/** sites and words that are out of every browser. once Argos is locked, taking one out waits like any relax */
private val SUGGESTED = listOf("youtube", "instagram", "tiktok", "reels", "shorts", "facebook", "twitch", "x.com")

@OptIn(ExperimentalLayoutApi::class)
fun openWords() = Sheets.open(tr("Webs y palabras", "Sites and words")) {
    Store.rev.intValue
    val words = Store.lockWords()
    var input by remember { mutableStateOf("") }
    fun add(w: String) { if (w.isNotBlank()) Store.setLockWords(words + w); input = "" }

    T(
        tr("En cualquier navegador: si la dirección, lo que buscas o lo que escribes contiene una de estas, te saca. Así el navegador no es un atajo.",
            "In any browser: if the address, your search or what you type contains one of these, you are taken out. So the browser is not a way around."),
        14, 400, C.Muted, Modifier.padding(bottom = 12.u), lh = 1.35f,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(48.u).clip(RoundedCornerShape(16.u)).background(C.Bg).padding(horizontal = 14.u),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (input.isEmpty()) T(tr("youtube, tiktok.com…", "youtube, tiktok.com…"), 15, 400, C.Sub)
            BasicTextField(
                input, { input = it.replace("\n", "") }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.us, color = C.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(input) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.padding(start = 8.u).height(48.u).clip(RoundedCornerShape(16.u)).background(C.Ink).tap { add(input) }.padding(horizontal = 16.u),
            contentAlignment = Alignment.Center,
        ) { T(tr("Añadir", "Add"), 14, 600, Color.White) }
    }
    val missing = SUGGESTED.filter { it !in words }
    if (missing.isNotEmpty()) {
        Gap(12)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.u), verticalArrangement = Arrangement.spacedBy(8.u)) {
            missing.forEach { w ->
                Box(Modifier.clip(RoundedCornerShape(14.u)).background(C.BlueTint).tap { add(w) }.padding(horizontal = 12.u, vertical = 7.u)) {
                    T("+ $w", 13, 600, C.Blue)
                }
            }
        }
    }
    Gap(14)
    if (Store.committed) T(
        tr("Quitar una espera ${Store.RELAX_DAYS} días.", "Removing one waits ${Store.RELAX_DAYS} days."),
        13, 500, C.Orange, Modifier.padding(bottom = 6.u),
    )
    words.forEachIndexed { i, w ->
        ItemRow(w, divider = i > 0, lead = { Tile(Ic.globe, C.Blue, size = 32) }) {
            Box(Modifier.clip(RoundedCornerShape(12.u)).tap { Store.setLockWords(words - w) }.padding(6.u)) { Icon(Ic.close, 18, C.Chev, 2.2f) }
        }
    }
}

