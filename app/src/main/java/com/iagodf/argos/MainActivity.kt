package com.iagodf.argos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.iagodf.argos.ui.AgBackground
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.Header
import com.iagodf.argos.ui.LocalU
import com.iagodf.argos.ui.NavBar
import com.iagodf.argos.ui.Tab
import com.iagodf.argos.ui.Title
import com.iagodf.argos.ui.U
import com.iagodf.argos.ui.u

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(SystemBarStyle.light(clear, C.Ink.toArgb()), SystemBarStyle.light(clear, C.Ink.toArgb()))
        setContent {
            val cfg = LocalConfiguration.current
            val fs = LocalDensity.current.fontScale
            val u = remember(cfg.screenWidthDp, fs) { U(minOf(cfg.screenWidthDp, 480) / 354f, fs) }
            CompositionLocalProvider(LocalU provides u) { App() }
        }
    }
}

@Composable
fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    Box(Modifier.fillMaxSize()) {
        AgBackground()
        Column(Modifier.statusBarsPadding().padding(top = 10.u)) {
            Header()
            Title(null, tab.name, Modifier.padding(top = 18.u))
        }
        NavBar(tab, { it.name }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.u)) { tab = it }
    }
}
