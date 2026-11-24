package com.iagodf.argos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** the pastel background of every Argos screen: peach top right, lilac left, blue at the bottom */
@Composable
fun AgBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(C.Bg).drawBehind {
        val d = max(size.width, size.height)
        fun glow(x: Float, y: Float, c: Color, r: Float) = drawRect(
            Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(x * size.width, y * size.height), r * d * 1.1f)
        )
        glow(.9f, -.05f, Color(0xFFFFD7C4), .45f)
        glow(-.1f, .25f, Color(0xFFD9D2FF), .5f)
        glow(.5f, 1.1f, Color(0xFFD6E2FF), .55f)
    })
}

@Composable
fun T(
    text: String, size: Number = 15, weight: Int = 500, color: Color = C.Ink,
    modifier: Modifier = Modifier, ls: Float = 0f, lh: Float = 0f, align: TextAlign? = null,
    brush: Brush? = null, lines: Int = Int.MAX_VALUE, tabular: Boolean = false,
) {
    val sz = size.us
    BasicText(
        text, modifier,
        style = TextStyle(
            fontFamily = Inter, fontWeight = FontWeight(weight), fontSize = sz,
            letterSpacing = sz * ls, lineHeight = if (lh > 0) sz * lh else androidx.compose.ui.unit.TextUnit.Unspecified,
            color = color, textAlign = align ?: TextAlign.Unspecified,
            fontFeatureSettings = if (tabular) "tnum" else null,
        ).let { if (brush != null) it.copy(brush = brush) else it },
        maxLines = lines, overflow = TextOverflow.Ellipsis,
    )
}

/** click without the grey ripple, the app has its own press feedback */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)
}

fun Modifier.softShadow(radius: Dp, shape: Shape, strength: Float = .1f) =
    shadow(radius, shape, clip = false, ambientColor = C.Shadow.copy(alpha = strength), spotColor = C.Shadow.copy(alpha = strength * 2))

/** white rounded card, radius 24 like the video */
@Composable
fun Card(
    modifier: Modifier = Modifier, bg: Color = Color.White, brush: Brush? = null, radius: Number = 24,
    padding: Number = 16, shadow: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.u)
    Column(
        modifier
            .then(if (shadow) Modifier.softShadow(10.dp, shape, .07f) else Modifier)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(bg))
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .padding(padding.u),
        content = content,
    )
}

@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = .7f, stiffness = 500f), label = "tg")
    val bg by animateColorAsState(if (on) C.Blue else C.ToggleOff, tween(220), label = "tgc")
    Box(
        modifier.size(46.u, 28.u).clip(RoundedCornerShape(14.u)).background(bg)
            .tap(enabled) { onChange(!on) }
            .then(if (!enabled) Modifier.background(Color.White.copy(alpha = .35f)) else Modifier)
    ) {
        Box(
            Modifier.padding(3.u).offset(x = 18.u * x).size(22.u)
                .softShadow(2.dp, CircleShape, .3f).clip(CircleShape).background(Color.White)
        )
    }
}

/** small rounded tag, like "Unbreakable" on the home card */
@Composable
fun Pill(text: String, bg: Color, color: Color, icon: IconData? = null, iconColor: Color = color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.u)).background(bg).padding(horizontal = 10.u, vertical = 6.u),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.u),
    ) {
        if (icon != null) Icon(icon, 14, iconColor, 2.2f)
        T(text, 12, 600, color)
    }
}

/** the big black button at the bottom of Focus and Deactivate */
@Composable
fun BigButton(
    text: String, modifier: Modifier = Modifier, icon: IconData? = null, bg: Color = C.Ink, color: Color = Color.White,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    val a by animateFloatAsState(if (enabled) 1f else .45f, label = "bb")
    Row(
        modifier.fillMaxWidth().height(56.u).softShadow(14.dp, RoundedCornerShape(28.u), if (bg == C.Ink) .25f else .08f)
            .clip(RoundedCornerShape(28.u)).background(bg.copy(alpha = bg.alpha * a)).tap(enabled, onClick),
        horizontalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, 19, color, 2f)
        T(text, 17, 650, color)
    }
}

/** a chunky icon with a soft fill of its colour, no square behind it */
@Composable
fun Tile(icon: IconData, accent: Color, size: Number = 38, modifier: Modifier = Modifier) {
    Box(modifier.size(size.u), contentAlignment = Alignment.Center) {
        Icon(icon, size.toFloat() * .74f, accent, 2.3f, fill = accent.copy(alpha = .2f))
    }
}

/** two colour gradient used by the app tiles, light corner top left */
fun tileBrush(c1: Long, c2: Long) = Brush.linearGradient(listOf(Color(c2), Color(c1)))

/** a row inside a card: title, optional subtitle and something on the right */
@Composable
fun ItemRow(
    title: String, sub: String? = null, divider: Boolean = true, lead: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, trail: @Composable RowScope.() -> Unit,
) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier).padding(vertical = 11.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lead != null) { lead(); Spacer(Modifier.width(10.u)) }
            Column(Modifier.weight(1f)) {
                T(title, 15, 500)
                if (sub != null) T(sub, 12, 400, C.Sub, Modifier.padding(top = 2.u))
            }
            trail()
        }
    }
}

/** progress ring that starts at 12 o'clock, peach to blue */
@Composable
fun Ring(progress: Float, size: Number, stroke: Number, track: Color = C.Track, brush: Brush = C.peachBlueDeep, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    val sw = stroke.u
    Box(modifier.size(size.u), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = sw.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (p > 0f) rotate(-90f) {
                drawArc(brush, 0f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** white circle button of the header (bell, settings) */
@Composable
fun CircleButton(icon: IconData, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        Modifier.size(40.u).softShadow(6.dp, CircleShape, .08f).clip(CircleShape).background(Color.White).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, 20, C.Ink, 1.9f)
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.u, end = 10.u).size(8.u).clip(CircleShape).background(C.Coral))
    }
}

/** "Argos" with the small orb on the left and the round buttons on the right */
@Composable
fun Header(title: String = "Argos", modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.u).height(40.u), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).then(if (onBack != null) Modifier.tap(onClick = onBack) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.u),
        ) {
            if (onBack != null) Icon(Ic.back, 22, C.Ink, 2.1f) else Orb(30, glow = false)
            T(title, 18, 700, ls = -.02f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.u), content = actions)
    }
}

/** big title of each screen: a light part and a bold part ("Focus mode"), wraps by words */
@Composable
fun Title(light: String?, bold: String, modifier: Modifier = Modifier, lightFirst: Boolean = true, size: Number = 34) {
    val sz = size.us
    val text = androidx.compose.ui.text.buildAnnotatedString {
        val l = androidx.compose.ui.text.SpanStyle(color = C.Muted, fontWeight = FontWeight(300))
        val b = androidx.compose.ui.text.SpanStyle(color = C.Ink, fontWeight = FontWeight(700))
        if (light != null && lightFirst) { pushStyle(l); append("$light "); pop() }
        pushStyle(b); append(bold); pop()
        if (light != null && !lightFirst) { pushStyle(l); append(" $light"); pop() }
    }
    BasicText(
        text, modifier.padding(horizontal = 22.u),
        style = TextStyle(fontFamily = Inter, fontSize = sz, letterSpacing = sz * -.04f, lineHeight = sz * 1.08f),
    )
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier) =
    T(text, 14, 400, C.Muted, modifier.padding(horizontal = 22.u))

@Composable
fun Gap(h: Number) = Spacer(Modifier.height(h.u))

/** dashed outline card ("New schedule") */
fun Modifier.dashed(color: Color, radius: Dp) = drawBehind {
    val w = 2.dp.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
    )
}

/** thin outline used on the glass bits */
fun Modifier.hairline(radius: Dp, color: Color = Color.White.copy(alpha = .9f)) = border(1.dp, color, RoundedCornerShape(radius))
