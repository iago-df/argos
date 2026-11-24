package com.iagodf.argos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iagodf.argos.R

// colours taken from the promo video
object C {
    val Bg = Color(0xFFF6F5FB)
    val Ink = Color(0xFF15131D)
    val Muted = Color(0xFF8D89A0)
    val Sub = Color(0xFF9A96AB)
    val Soft = Color(0xFF7F7B93)
    val Body = Color(0xFF4B4866)
    val Chev = Color(0xFFB3AFC4)
    val Line = Color(0xFFF0EEF5)
    val Track = Color(0xFFECE9F5)
    val ToggleOff = Color(0xFFDCD8E6)
    val Blue = Color(0xFF4A5CFF)
    val BlueSoft = Color(0xFF9FB0FF)
    val BlueTint = Color(0xFFEEF0FF)
    val Lilac = Color(0xFFA58FFF)
    val Peach = Color(0xFFFFAB83)
    val Coral = Color(0xFFFF6F61)
    val Orange = Color(0xFFFF7A4D)
    val Shadow = Color(0xFF3C288C)

    val blueText = Brush.verticalGradient(0f to Color(0xFF6D86FF), .7f to Color(0xFF3C4FF0), 1f to Color(0xFF3443D6))
    val peachBlue = Brush.linearGradient(listOf(Peach, Color(0xFF6D86FF)))
    val peachBlueDeep = Brush.linearGradient(listOf(Peach, Blue))
}

@OptIn(ExperimentalTextApi::class)
private fun vf(w: Int) = Font(
    R.font.inter, weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w))
)

val Inter = FontFamily(listOf(300, 400, 500, 550, 600, 650, 700, 750).map { vf(it) })

/**
 * every size in the app is measured in px of the phone in the promo video (354 px wide),
 * so it scales with the real width and looks the same on any phone
 */
class U(val k: Float, private val fontScale: Float) {
    fun dp(px: Number): Dp = (px.toFloat() * k).dp
    fun sp(px: Number): TextUnit = (px.toFloat() * k / fontScale.coerceIn(.85f, 1.15f)).sp
}

val LocalU = staticCompositionLocalOf { U(1.1f, 1f) }

val Number.u: Dp
    @Composable @ReadOnlyComposable get() = LocalU.current.dp(this)

val Number.us: TextUnit
    @Composable @ReadOnlyComposable get() = LocalU.current.sp(this)
