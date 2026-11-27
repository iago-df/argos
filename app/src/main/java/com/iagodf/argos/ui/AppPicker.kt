package com.iagodf.argos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.iagodf.argos.data.Apps
import com.iagodf.argos.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * a list of the phone's apps with a switch each, for the allowed apps of Focus and the paused apps of the lock.
 * the list and the icons are read in the background and kept, and only the rows on screen are drawn,
 * so the sheet opens at once and scrolls (and drags down) like the others
 */
@Composable
fun AppPicker(
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    exclude: (android.content.Context) -> Set<String> = { emptySet() },
    header: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(Apps.cachedLaunchable()?.let { list -> list to emptySet<String>() }) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { Apps.launchableCached(ctx, refresh = true) to exclude(ctx) }
    }
    val shown = apps?.let { (list, out) -> list.filter { it.pkg !in out } }
    LazyColumn(Modifier.fillMaxWidth()) {
        item { header() }
        if (shown == null) item { T(tr("Cargando apps…", "Loading apps…"), 14, 500, C.Muted, Modifier.padding(vertical = 18.u)) }
        else itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
            ItemRow(a.label, divider = i > 0, lead = { AppIcon(a.pkg, 38) }) {
                Toggle(a.pkg in selected) { on -> onToggle(a.pkg, on) }
            }
        }
    }
}

/** an app icon, drawn in the background the first time and kept after that */
@Composable
fun AppIcon(pkg: String, size: Number) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.u.roundToPx() }
    var bmp by remember(pkg, px) { mutableStateOf<ImageBitmap?>(Apps.cachedIcon(pkg, px)?.asImageBitmap()) }
    if (bmp == null) LaunchedEffect(pkg, px) {
        bmp = withContext(Dispatchers.IO) { Apps.icon(ctx, pkg, px)?.asImageBitmap() }
    }
    Box(Modifier.size(size.u).softShadow(6.dp, RoundedCornerShape(17.u), .12f).clip(RoundedCornerShape(17.u)).background(Color.White)) {
        bmp?.let { Image(it, null, Modifier.size(size.u)) }
    }
}
