package com.iagodf.argos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** bottom sheet with the app look: white, round top, drag down or tap outside to close */
@Composable
fun Sheet(open: Boolean, onClose: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (open) BackHandler(onBack = onClose)
    AnimatedVisibility(open, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(C.Ink.copy(alpha = .35f)).tap(onClick = onClose))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            open,
            enter = slideInVertically(tween(260)) { it },
            exit = slideOutVertically(tween(200)) { it },
        ) {
            var drag by remember { mutableFloatStateOf(0f) }
            val maxH = (LocalConfiguration.current.screenHeightDp * .88f).dp
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxH)
                    .offset { IntOffset(0, drag.roundToInt().coerceAtLeast(0)) }
                    .clip(RoundedCornerShape(topStart = 30.u, topEnd = 30.u)).background(Color.White)
                    .tap { }
                    .draggable(
                        rememberDraggableState { drag = (drag + it).coerceAtLeast(0f) }, Orientation.Vertical,
                        onDragStopped = { v -> if (drag > 220f || v > 1800f) onClose(); drag = 0f },
                    )
                    .navigationBarsPadding().padding(start = 20.u, end = 20.u, bottom = 18.u),
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.u), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.u, 5.u).clip(RoundedCornerShape(3.u)).background(C.ToggleOff))
                }
                if (title != null) T(title, 22, 700, ls = -.03f, modifier = Modifier.padding(top = 4.u, bottom = 14.u))
                Column(Modifier.verticalScroll(rememberScrollState()), content = content)
            }
        }
    }
}
