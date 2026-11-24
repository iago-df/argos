package com.iagodf.argos.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val orbColors = listOf(
    Color(0xFFFFB08A), Color(0xFFFF8FB8), Color(0xFFA99BFF), Color(0xFF6F8DFF),
    Color(0xFFA9E1FF), Color(0xFFFFCFA6), Color(0xFFFFB08A),
)

/** the Argos eye: slowly spinning colours, a soft white rim and a dark pupil */
@Composable
fun Orb(size: Number, glow: Boolean = true, spin: Boolean = true, modifier: Modifier = Modifier) {
    val s = size.u
    val rot by rememberInfiniteTransition(label = "orb").animateFloat(
        0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Box(
        modifier.size(s)
            .then(if (glow) Modifier.shadow(s * .3f, CircleShape, ambientColor = Color(0xFF6E6EFF), spotColor = Color(0xFF6E6EFF)) else Modifier)
            .clip(CircleShape).background(Color(0xFFEFE9FF))
    ) {
        val blur = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(maxOf(2.dp, s * .08f)) else Modifier
        Canvas(Modifier.fillMaxSize().then(blur)) {
            rotate(if (spin) rot else 0f) {
                val r = this.size.minDimension * .75f
                drawCircle(Brush.sweepGradient(orbColors, center), r, center)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0f), .3f to Color.White.copy(alpha = 0f),
                    .72f to Color.White.copy(alpha = .55f), 1f to Color.White.copy(alpha = .9f), center = center, radius = r,
                ), r,
            )
            val pr = r * .34f
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFF0F0C26), .55f to Color(0xFF1D1A52), .74f to Color(0x003232A0), center = center, radius = pr,
                ), pr,
            )
            // little reflection on the eye
            val hl = Offset(this.size.width * .45f, this.size.height * .35f)
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = .95f), Color.White.copy(alpha = 0f)), hl, r * .14f),
                topLeft = Offset(hl.x - r * .14f, hl.y - r * .1f),
                size = androidx.compose.ui.geometry.Size(r * .28f, r * .2f),
            )
        }
    }
}
