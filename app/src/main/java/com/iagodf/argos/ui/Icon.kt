package com.iagodf.argos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

class IconData(val paths: List<String>, val filled: Boolean = false) {
    val parsed: List<Path> by lazy { paths.map { PathParser().parsePathString(it).toPath() } }

    /** shapes that close on themselves (rects, circles, outlines) get the soft fill, plain strokes do not */
    val closed: List<Boolean> by lazy { paths.map { it.contains('z', true) || CIRCLE.matches(it.trim()) } }

    private companion object {
        val CIRCLE = Regex("""M[^a-zA-Z]+a[^a-zA-Z]+a[^a-zA-Z]+""")
    }
}

/** draws one of the video icons: 24x24 viewbox, round strokes */
@Composable
fun Icon(icon: IconData, size: Number = 24, color: Color = C.Ink, stroke: Float = 1.8f, modifier: Modifier = Modifier, fill: Color? = null) {
    val paths = remember(icon) { icon.parsed }
    val closed = remember(icon) { icon.closed }
    Canvas(modifier.size(size.u)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val st = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (fill != null) paths.forEachIndexed { i, p -> if (closed[i]) drawPath(p, fill, style = Fill) }
            paths.forEach { p ->
                if (icon.filled) drawPath(p, color, style = Fill)
                drawPath(p, color, style = st)
            }
        }
    }
}
