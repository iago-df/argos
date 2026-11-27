package com.iagodf.argos.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class Tab(val icon: IconData) { Home(Ic.home), Blocks(Ic.lock), Focus(Ic.moon), Lock(Ic.shield) }

/** dark pill at the bottom, the active tab is a white pill with its name */
@Composable
fun NavBar(selected: Tab, labels: (Tab) -> String, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(33.u)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.u).height(66.u)
            .softShadow(18.dp, shape, .35f).clip(shape).background(C.Ink).padding(horizontal = 8.u),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Row(
                Modifier.height(50.u).widthIn(min = 50.u).clip(RoundedCornerShape(25.u))
                    .background(if (on) Color.White else Color.Transparent)
                    .tap { onSelect(t) }
                    .animateContentSize(spring(dampingRatio = .8f, stiffness = 600f))
                    .padding(horizontal = if (on) 16.u else 0.u),
                horizontalArrangement = Arrangement.spacedBy(7.u, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(t.icon, 20, C.Ink, 2.1f)
                    T(labels(t), 14, 600)
                } else Box(Modifier.widthIn(min = 50.u), contentAlignment = Alignment.Center) { Icon(t.icon, 22, Color.White, 1.9f) }
            }
        }
    }
}
