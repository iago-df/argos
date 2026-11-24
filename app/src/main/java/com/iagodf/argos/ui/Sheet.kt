package com.iagodf.argos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/** one sheet at a time, drawn above everything by [SheetHost] so screens can open it from anywhere */
object Sheets {
    var visible by mutableStateOf(false); private set
    var title by mutableStateOf<String?>(null); private set
    var body by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null); private set

    fun open(title: String?, body: @Composable ColumnScope.() -> Unit) {
        this.title = title; this.body = body; visible = true
    }

    fun close() { visible = false }
}

@Composable
fun SheetHost() = Sheet(Sheets.visible, Sheets::close, Sheets.title) { Sheets.body?.invoke(this) }

/**
 * bottom sheet with the app look. it follows the finger, and when you let go it leaves from right there
 * with the speed of your swipe (or springs back if you did not pull far enough)
 */
@Composable
fun Sheet(open: Boolean, onClose: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val screen = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var y by remember { mutableFloatStateOf(screen) }
    var present by remember { mutableStateOf(open) }
    var height by remember { mutableFloatStateOf(screen) }
    var flingSpeed by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var anim by remember { mutableStateOf<Job?>(null) }

    fun animateTo(target: Float, v: Float = 0f, done: () -> Unit = {}) {
        anim?.cancel()
        anim = scope.launch {
            val spec = if (target > 0f && v <= 0f) tween<Float>(230, easing = FastOutLinearInEasing)
            else spring(dampingRatio = if (target == 0f) .78f else 1f, stiffness = Spring.StiffnessMediumLow)
            animate(y, target, v, spec) { value, _ -> y = value }
            done()
        }
    }

    /** let go: leave from here with the swipe speed, or spring back up */
    fun release(v: Float) {
        if (y > height * .28f || v > 1600f) { flingSpeed = v.coerceAtLeast(0f); onClose() }
        else animateTo(0f, v)
    }

    fun dragBy(d: Float) {
        anim?.cancel()
        // a bit of resistance when pulling up, free when pulling down
        y = (y + if (y + d < 0f) d * .25f else d).coerceAtLeast(-40f)
    }

    LaunchedEffect(open) {
        if (open) {
            present = true
            if (y <= 0f) y = screen
            animateTo(0f)
        } else if (present) {
            val v = flingSpeed
            flingSpeed = 0f
            animateTo(height + 40f, v) { present = false }
        }
    }

    // the list inside scrolls first; once it is at the top, pulling down moves the sheet
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && y > 0f) {
                    val use = maxOf(available.y, -y)
                    dragBy(use)
                    return Offset(0f, use)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (y != 0f) { release(available.y); return available }
                return Velocity.Zero
            }
        }
    }

    if (!present) return
    if (open) BackHandler(onBack = onClose)

    val shown = (1f - y / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }.background(C.Ink.copy(alpha = .35f)).tap(enabled = open, onClick = onClose))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val maxH = (LocalConfiguration.current.screenHeightDp * .88f).dp
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxH)
                .onSizeChanged { height = it.height.toFloat() }
                .offset { IntOffset(0, y.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 30.u, topEnd = 30.u)).background(Color.White)
                .tap { }
                .nestedScroll(nested)
                .draggable(
                    rememberDraggableState { dragBy(it) }, Orientation.Vertical, enabled = open,
                    onDragStopped = { v -> release(v) },
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
