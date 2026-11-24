package com.iagodf.argos

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr
import com.iagodf.argos.ui.AgBackground
import com.iagodf.argos.ui.C
import com.iagodf.argos.ui.CircleButton
import com.iagodf.argos.ui.Header
import com.iagodf.argos.ui.Ic
import com.iagodf.argos.ui.LocalU
import com.iagodf.argos.ui.NavBar
import com.iagodf.argos.ui.Tab
import com.iagodf.argos.ui.U
import com.iagodf.argos.ui.ToastHost
import com.iagodf.argos.ui.SheetHost
import com.iagodf.argos.ui.focus.FocusScreen
import com.iagodf.argos.ui.friction.FrictionScreen
import com.iagodf.argos.ui.schedules.SchedulesScreen
import com.iagodf.argos.ui.blocks.BlocksScreen
import com.iagodf.argos.ui.home.ActivitySheet
import com.iagodf.argos.ui.home.HomeScreen
import com.iagodf.argos.ui.u

class ArgosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.load(this)
        Store.init(this)
    }
}

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

/** screens that open on top of a tab and keep its nav item */
enum class Sub { None, Friction, Lock }

fun tabLabel(t: Tab) = when (t) {
    Tab.Home -> tr("Inicio", "Home")
    Tab.Blocks -> tr("Bloqueos", "Blocks")
    Tab.Focus -> "Focus"
    Tab.Schedules -> tr("Horarios", "Schedules")
}

@Composable
fun App() {
    Lang.en
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var sub by rememberSaveable { mutableStateOf(Sub.None) }
    var activity by rememberSaveable { mutableStateOf(false) }
    if (sub != Sub.None) BackHandler { sub = Sub.None }
    else if (tab != Tab.Home) BackHandler { tab = Tab.Home }

    Box(Modifier.fillMaxSize()) {
        AgBackground()
        AnimatedContent(tab to sub, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "screen") { (t, s) ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 6.u)) {
                val back = if (s != Sub.None) ({ sub = Sub.None }) else null
                Header(onBack = back) {
                    if (t == Tab.Home && s == Sub.None) CircleButton(Ic.bell, { activity = true }, dot = Store.blockedToday() > 0)
                }
                Spacer(Modifier.height(18.u))
                when {
                    t == Tab.Home -> HomeScreen(
                        openFocus = { tab = Tab.Focus }, openSchedules = { tab = Tab.Schedules }, openLock = { sub = Sub.Lock },
                    )
                    t == Tab.Focus -> FocusScreen()
                    t == Tab.Schedules -> SchedulesScreen()
                    t == Tab.Blocks && s == Sub.Friction -> FrictionScreen()
                    t == Tab.Blocks -> BlocksScreen(openFriction = { sub = Sub.Friction })
                    else -> Unit
                }
                Spacer(Modifier.navigationBarsPadding().height(120.u))
            }
        }
        NavBar(tab, ::tabLabel, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.u)) {
            tab = it; sub = Sub.None
        }
        ActivitySheet(activity) { activity = false }
        SheetHost()
        ToastHost()
    }
}
