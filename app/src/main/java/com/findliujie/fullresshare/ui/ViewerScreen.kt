package com.findliujie.fullresshare.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findliujie.fullresshare.GalleryViewModel
import com.findliujie.fullresshare.Media
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.Screen
import com.findliujie.fullresshare.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Scrim = Color.Black.copy(alpha = 0.55f)
private val StarYellow = Color(0xFFFFC107)

@Composable
fun ViewerScreen(vm: GalleryViewModel, screen: Screen.Viewer, actions: PhotoActions) {
    val photos = vm.photosFor(screen.source)
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val pager = rememberPagerState(
        initialPage = photos.indexOfFirst { it.id == screen.currentId }.coerceAtLeast(0),
    ) { photos.size }
    val current = photos[pager.currentPage.coerceIn(photos.indices)]
    LaunchedEffect(current.id) { screen.currentId = current.id }
    var chrome by remember { mutableStateOf(true) }
    val inTrash = screen.source == Source.Trash

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, key = { photos[it].id }, beyondViewportPageCount = 1) { page ->
            ZoomableImage(photos[page], onTap = { chrome = !chrome })
        }
        if (chrome) CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Scrim).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Box(Modifier.weight(1f))
                if (!inTrash) {
                    val favorite = current.id in vm.store.favorites
                    IconButton(onClick = { actions.setFavorite(listOf(current), !favorite) }) {
                        if (favorite) Icon(Icons.Filled.Star, contentDescription = "Remove from favourites", tint = StarYellow)
                        else Icon(FrsIcons.StarBorder, contentDescription = "Favourite")
                    }
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Info") }, onClick = { menu = false; vm.open(Screen.Info(current)) })
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Scrim).navigationBarsPadding().padding(vertical = 4.dp),
            ) {
                if (inTrash) {
                    BarAction(Icons.Filled.Refresh, "Restore") { actions.restore(listOf(current)) }
                    BarAction(Icons.Filled.Delete, "Delete") { actions.delete(listOf(current)) }
                } else {
                    var share by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Row { BarAction(Icons.Filled.Share, "Share") { share = true } }
                        ShareMenu(
                            share, { share = false },
                            onFullRes = { actions.shareFullRes(listOf(current)) },
                            onStock = { actions.shareStock(listOf(current)) },
                        )
                    }
                    if (current.id in vm.store.marks) BarAction(FrsIcons.Undo, "Undo FullResShare prep") { actions.unprep(listOf(current)) }
                    else BarAction(FrsIcons.Prep, "Prep for FullResShare") { actions.prep(listOf(current)) }
                    BarAction(Icons.Filled.Edit, "Edit") { vm.open(Screen.Editor(current)) }
                    BarAction(Icons.Filled.Delete, "Trash") { actions.trash(listOf(current)) }
                }
            }
        }
    }
}

/** Icon above label, each taking an equal share of the bar; long labels wrap to a second line. */
@Composable
private fun RowScope.BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ZoomableImage(photo: Photo, onTap: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photo.cacheKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { Media.decode(context, photo.uri, maxEdge = 3000).asImageBitmap() }.getOrNull()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Single-finger drags at 1x belong to the pager; only take over when zooming or zoomed.
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 8f)
                            val maxX = size.width * (scale - 1) / 2
                            val maxY = size.height * (scale - 1) / 2
                            val moved = offset + event.calculatePan()
                            offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded == null) CircularProgressIndicator(color = Color.White)
        else Image(
            loaded,
            contentDescription = photo.name,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}
