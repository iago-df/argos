package com.iagodf.argos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** the dark toast of the video ("Reels blocked by Argos") */
object Toaster {
    var msg by mutableStateOf<Pair<String, Long>?>(null)
    fun show(text: String) { msg = text to System.nanoTime() }
}

@Composable
fun ToastHost() {
    val m = Toaster.msg
    LaunchedEffect(m) { if (m != null) { delay(2600); if (Toaster.msg == m) Toaster.msg = null } }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.u), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            m != null,
            enter = slideInVertically(spring(dampingRatio = .6f)) { -it } + fadeIn() + scaleIn(initialScale = .9f),
            exit = slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(250)),
        ) {
            var last by androidx.compose.runtime.remember { mutableStateOf("") }
            if (m != null) last = m.first
            Row(
                Modifier.padding(horizontal = 24.u).softShadow(14.dp, RoundedCornerShape(20.u), .3f)
                    .clip(RoundedCornerShape(20.u)).background(Color(0xEB15131D))
                    .padding(start = 14.u, end = 18.u, top = 12.u, bottom = 12.u),
                horizontalArrangement = Arrangement.spacedBy(9.u), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.lock, 16, C.BlueSoft, 2.2f)
                T(last, 14, 600, Color.White)
            }
        }
    }
}
