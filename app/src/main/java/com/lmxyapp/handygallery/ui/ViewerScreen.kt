package com.lmxyapp.handygallery.ui

import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Screen
import com.lmxyapp.handygallery.Source
import com.lmxyapp.handygallery.share.FileSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val Scrim = Color.Black.copy(alpha = 0.55f)
private val StarYellow = Color(0xFFFFC107)

/** The last few photos decoded at screen size, so swiping back to one is instant. */
private val screenCache = LruCache<String, ImageBitmap>(6)

/**
 * Full-screen photo. It is drawn over the screen it was opened from, so the vertical gestures can
 * move it continuously: drag up and the details sheet rises under the photo, drag down and the photo
 * shrinks away to reveal the wall behind it.
 *
 * [barBelow] is the height of the bottom bar on the screen underneath, not counting the system's own
 * navigation area. While the photo is leaving it is cut off there, so it goes behind that bar instead
 * of sliding over it.
 */
@Composable
fun ViewerScreen(vm: GalleryViewModel, screen: Screen.Viewer, actions: PhotoActions, barBelow: Dp = 0.dp) {
    val photos = vm.photosFor(screen.source)
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    // Switching the photo between the wall and its folder hands this composable a new [screen]. Only the
    // pager is rebuilt for it; everything else stays, so the photo holds still instead of the whole
    // viewer fading out and back in.
    val pager = key(screen) {
        rememberPagerState(
            initialPage = photos.indexOfFirst { it.id == screen.currentId }.coerceAtLeast(0),
        ) { photos.size }
    }
    // The photo itself doesn't move, so two things say what happened: the film strip slides down and
    // away and the new set slides up in its place, and a label names where the photo is now being
    // seen. [strip] is the screen (and its pager) whose strip is on show, which lags the switch by the
    // time the old one takes to leave.
    var strip by remember { mutableStateOf(screen to pager) }
    val stripAway = remember { Animatable(0f) }
    val placeLabel = remember { Animatable(0f) }
    /** Where the photo was being seen before the last switch, for the label's "from" side. */
    var cameFrom by remember { mutableStateOf(screen.source) }
    /** 0 while the label's highlight is on the place the photo came from, 1 once it has slid over to where it is now. */
    val placeSlide = remember { Animatable(0f) }
    // The top bar's button turns over to its new icon during a switch. Whether one is under way has to
    // be known in the very frame the screen changes, before the effect below has run, or the button
    // would show its new icon for a frame and then jump back to the old one to start turning. So it is
    // worked out from these two rather than from a flag the effect sets: [settled] is the screen whose
    // switch has finished, and [slideFor] the screen [placeSlide] is currently counting for.
    var settled by remember { mutableStateOf(screen) }
    var slideFor by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        if (screen !== strip.first) {
            cameFrom = strip.first.source
            placeSlide.snapTo(0f)
            slideFor = screen
            // The label shows first, with the highlight still on where the photo came from.
            placeLabel.animateTo(1f, tween(140))
            // Then the highlight crosses to the destination in exactly the time the film strip takes to
            // slide out and the new one to slide in.
            launch { placeSlide.animateTo(1f, tween(StripOut + StripIn, easing = FastOutSlowInEasing)) }
            stripAway.animateTo(1f, tween(StripOut, easing = FastOutSlowInEasing))
            strip = screen to pager
            stripAway.animateTo(0f, tween(StripIn, easing = FastOutSlowInEasing))
            settled = screen
            delay(900)
            placeLabel.animateTo(0f, tween(260))
        }
    }
    val current = photos[pager.currentPage.coerceIn(photos.indices)]
    LaunchedEffect(current.id) {
        screen.currentId = current.id
        vm.focus = current.id
    }
    var chrome by remember { mutableStateOf(true) }
    val inTrash = screen.source == Source.Trash
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    /** The full details page, opened from the menu; swiping up gives the short sheet instead. */
    var details by remember { mutableStateOf(false) }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(160)) }

    // The empty pointerInput stops touches falling through to the screen underneath.
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {}) {
        val height = constraints.maxHeight.toFloat()
        val sheetHeight = height * 0.55f
        /**
         * How far the photo has been pulled, in pixels: negative while the details sheet is drawn up
         * (down to -sheetHeight when fully open), positive while the photo is being swiped away.
         */
        val pull = remember { Animatable(0f) }
        var sheetOpen by remember { mutableStateOf(false) }
        /** Whether the photo on screen is zoomed in, and a counter bumped to ask it to zoom back out. */
        var zoomed by remember { mutableStateOf(false) }
        var unzoom by remember { mutableIntStateOf(0) }

        fun drag(delta: Float) {
            scope.launch { pull.snapTo((pull.value + delta).coerceIn(-sheetHeight, height)) }
        }

        fun closeSheet() {
            scope.launch {
                sheetOpen = false
                pull.animateTo(0f)
            }
        }

        /** Finishes a drag: to whichever resting place it had moved clearly towards. */
        fun settle() {
            val threshold = with(density) { 72.dp.toPx() }
            val value = pull.value
            scope.launch {
                when {
                    sheetOpen -> if (value > -sheetHeight + threshold) {
                        sheetOpen = false
                        pull.animateTo(0f)
                    } else pull.animateTo(-sheetHeight)
                    value < -threshold -> {
                        sheetOpen = true
                        pull.animateTo(-sheetHeight)
                    }
                    value > threshold -> {
                        pull.animateTo(height, tween(180))
                        vm.back()
                    }
                    else -> pull.animateTo(0f)
                }
            }
        }
        /** Leaves the way a downward swipe does, so Back and the arrow get the same transition. */
        fun leave() {
            scope.launch {
                pull.animateTo(height, tween(180))
                vm.back()
            }
        }

        // Back undoes one thing at a time; a handler composed later takes priority over an earlier one.
        // So: close the details sheet, else zoom back out, else leave.
        BackHandler(onBack = ::leave)
        BackHandler(zoomed) { unzoom++ }
        BackHandler(sheetOpen, ::closeSheet)

        /** 0 at rest, 1 once the photo has been dragged far enough down to be clearly leaving. */
        fun away() = (pull.value / (height * 0.4f)).coerceIn(0f, 1f)

        val barPx = if (barBelow > 0.dp) with(density) { barBelow.toPx() } + WindowInsets.navigationBars.getBottom(density) else 0f
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.drawWithContent {
                if (pull.value > 0f && barPx > 0f) clipRect(bottom = size.height - barPx) { this@drawWithContent.drawContent() }
                else drawContent()
            },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - away() }.background(Color.Black))
            HorizontalPager(
                pager,
                // When the viewer moves to another list, the outgoing pager can still be asked about a
                // page the new, shorter list doesn't have; that must not be an index out of range.
                key = { photos.getOrNull(it)?.id ?: -1L - it },
                beyondViewportPageCount = 1,
                modifier = Modifier.graphicsLayer {
                    // Going down the photo follows the finger and shrinks; going up it rises at half
                    // speed so it stays in view above the sheet.
                    val value = pull.value
                    translationY = if (value > 0f) value else value * 0.5f
                    val shrink = 1f - 0.25f * away()
                    scaleX = shrink
                    scaleY = shrink
                },
            ) { page ->
                val photo = photos.getOrNull(page) ?: return@HorizontalPager
                // A photo that has just been trashed or deleted shrinks into the bin button before the next takes its place.
                val going by animateFloatAsState(if (photo.id in vm.leaving) 1f else 0f, tween(300, easing = FastOutSlowInEasing), label = "leaving")
                var centre by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier.fillMaxSize().onGloballyPositioned { centre = it.boundsInRoot().center }.graphicsLayer {
                        if (going > 0f) {
                            val bin = TrashSpot.centre
                            // Straight down if the bar with the bin is hidden.
                            val path = if (bin.isUnspecified) Offset(0f, size.height / 2f) else bin - centre
                            translationX = path.x * going
                            translationY = path.y * going
                            scaleX = 1f - 0.92f * going
                            scaleY = 1f - 0.92f * going
                            alpha = ((1f - going) / 0.2f).coerceAtMost(1f)
                        }
                    },
                ) {
                    ZoomableImage(
                        photo,
                        active = page == pager.currentPage,
                        unzoom = unzoom,
                        onZoomed = { zoomed = it },
                        onTap = { if (sheetOpen) closeSheet() else chrome = !chrome },
                        onVerticalDrag = ::drag,
                        onVerticalDragEnd = ::settle,
                    )
                }
            }

            if (chrome && pull.value == 0f) CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Scrim).statusBarsPadding().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Box(Modifier.weight(1f))
                    if (!inTrash) {
                        // The same photo, seen from the other side: among its folder's photos, or on the wall.
                        // During a switch the button's icon turns over like a card, in step with the label and
                        // the film strip: the old icon turns edge-on, and the new one turns to face.
                        val turn = if (screen === settled) 1f else if (slideFor === screen) placeSlide.value else 0f
                        val flip = Modifier.graphicsLayer {
                            rotationY = if (turn < 0.5f) turn * 180f else (turn - 1f) * 180f
                            cameraDistance = 12f * this.density
                        }
                        // What the button showed before the switch: the wall icon if the photo was in a folder.
                        val before = if (settled.source is Source.Folder) FrsIcons.Collage else FrsIcons.Folder
                        if (screen.source is Source.Folder) {
                            val wall = vm.photosFor(Source.All)
                            val onWall = remember(current.id, wall) { wall.any { it.id == current.id } }
                            IconButton(onClick = { vm.showOnWall(current) }, enabled = onWall) {
                                Icon(
                                    if (turn < 0.5f) before else FrsIcons.Collage,
                                    contentDescription = stringResource(if (onWall) R.string.show_on_wall else R.string.not_on_wall),
                                    modifier = flip,
                                )
                            }
                        } else {
                            IconButton(onClick = { vm.showInFolder(current) }) {
                                Icon(
                                    if (turn < 0.5f) before else FrsIcons.Folder,
                                    contentDescription = stringResource(R.string.show_in_folder),
                                    modifier = flip,
                                )
                            }
                        }
                        val favorite = current.id in vm.store.favorites
                        IconButton(onClick = { actions.setFavorite(listOf(current), !favorite) }) {
                            if (favorite) Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.unfavourite), tint = StarYellow)
                            else Icon(FrsIcons.StarBorder, contentDescription = stringResource(R.string.favourite))
                        }
                        FrontRowButton(
                            alreadyThere = current.id in vm.store.marks,
                            onPrep = { actions.prep(listOf(current)) },
                            onRemove = { actions.unprep(listOf(current)) },
                        )
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.details)) },
                                onClick = {
                                    menu = false
                                    details = true
                                },
                            )
                            if (!inTrash) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_folder)) },
                                    onClick = { menu = false; actions.move(listOf(current)) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_to_folder)) },
                                    onClick = { menu = false; actions.copy(listOf(current)) },
                                )
                            }
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                    // Just above the film strip, for a moment after switching: where the photo was being
                    // seen, an arrow, and where it is being seen now.
                    if (placeLabel.value > 0f) {
                        // Where the two pills sit within the row, so one white highlight can travel between them.
                        var fromBox by remember { mutableStateOf(Rect.Zero) }
                        var toBox by remember { mutableStateOf(Rect.Zero) }
                        Row(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                                .graphicsLayer { alpha = placeLabel.value }
                                .drawBehind {
                                    fun pill(box: Rect, color: Color) =
                                        drawRoundRect(color, box.topLeft, box.size, CornerRadius(box.height / 2f))
                                    pill(fromBox, Color.Black.copy(alpha = 0.7f))
                                    pill(toBox, Color.Black.copy(alpha = 0.7f))
                                    if (!toBox.isEmpty) pill(lerp(fromBox, toBox, placeSlide.value), Color.White)
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Each name is dark while the highlight is under it and white otherwise.
                            PlacePill(cameFrom, lerp(Color.Black, Color.White, placeSlide.value), Modifier.onGloballyPositioned { fromBox = it.boundsInParent() })
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                            PlacePill(screen.source, lerp(Color.White, Color.Black, placeSlide.value), Modifier.onGloballyPositioned { toBox = it.boundsInParent() })
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(Scrim)) {
                    Box(
                        Modifier.clipToBounds().graphicsLayer {
                            translationY = stripAway.value * size.height
                            alpha = 1f - stripAway.value
                        },
                    ) {
                        key(strip.first) { FilmStrip(vm.photosFor(strip.first.source), strip.second) }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        if (inTrash) {
                            BarAction(Icons.Filled.Refresh, stringResource(R.string.restore)) { actions.restore(listOf(current)) }
                            BarAction(Icons.Filled.Delete, stringResource(R.string.delete), Modifier.trashSpot(vm.leaving.isNotEmpty())) { actions.delete(listOf(current)) }
                        } else {
                            var share by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                Row { BarAction(Icons.Filled.Share, stringResource(R.string.share)) { share = true } }
                                ShareMenu(
                                    share, { share = false },
                                    onFullRes = { actions.shareFullRes(listOf(current)) },
                                    onStock = { actions.shareStock(listOf(current)) },
                                    onEditElsewhere = { FileSharer.editElsewhere(context, current) },
                                )
                            }
                            BarAction(FrsIcons.Crop, stringResource(R.string.crop)) { vm.open(Screen.Editor(current, crop = true)) }
                            BarAction(Icons.Filled.Edit, stringResource(R.string.edit)) { vm.open(Screen.Editor(current)) }
                            BarAction(Icons.Filled.Delete, stringResource(R.string.trash), Modifier.trashSpot(vm.leaving.isNotEmpty())) { actions.trash(listOf(current)) }
                        }
                    }
                    }
                }
            }

            // Details sheet: parked just below the screen and drawn up by a negative pull.
            if (pull.value < 0f) Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { sheetHeight.toDp() })
                    .graphicsLayer { translationY = sheetHeight + pull.value }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { _, delta -> drag(delta) }
                    },
            ) {
                InfoContent(current, Modifier.navigationBarsPadding())
            }
        }

        // Composed last, so it covers the viewer and its Back handler comes before the ones above.
        if (details) DetailsPage(current, vm.store.marks[current.id]?.time) { details = false }
    }
}

/**
 * A row of small thumbnails with the current photo in the middle. It follows the pager, and
 * scrolling or tapping it moves the pager, the way Samsung Gallery's strip does.
 */
@Composable
private fun FilmStrip(photos: List<Photo>, pager: PagerState) {
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = pager.currentPage)
    val scope = rememberCoroutineScope()
    val itemWidth = 40.dp
    val gap = 2.dp
    val step = with(LocalDensity.current) { (itemWidth + gap).toPx() }

    // Only a scroll the user started may drive the pager; otherwise the strip's own catch-up animation
    // would feed back into the pager it is following.
    val dragged by strip.interactionSource.collectIsDraggedAsState()
    var userDriven by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) userDriven = true }
    LaunchedEffect(strip.isScrollInProgress) { if (!strip.isScrollInProgress) userDriven = false }

    LaunchedEffect(pager.currentPage) { if (!userDriven) strip.animateScrollToItem(pager.currentPage) }
    val count by rememberUpdatedState(photos.size)
    LaunchedEffect(strip, pager) {
        snapshotFlow {
            if (!userDriven) -1 else strip.firstVisibleItemIndex + if (strip.firstVisibleItemScrollOffset > step / 2) 1 else 0
        }.collect { index -> if (index in 0 until count && index != pager.currentPage) pager.scrollToPage(index) }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        LazyRow(
            state = strip,
            // Padding of half the width lets the first and last thumbnails reach the middle.
            contentPadding = PaddingValues(horizontal = (maxWidth - itemWidth) / 2),
            horizontalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = rememberSnapFlingBehavior(strip, SnapPosition.Center),
        ) {
            items(photos.size, key = { photos.getOrNull(it)?.id ?: -1L - it }) { i ->
                val shape = RoundedCornerShape(4.dp)
                Thumb(
                    photos[i],
                    Modifier
                        .size(itemWidth, 52.dp)
                        .clip(shape)
                        .then(if (i == pager.currentPage) Modifier.border(2.dp, Color.White, shape) else Modifier)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        }
    }
}

/** How long the old film strip takes to slide away and the new one to slide in, in milliseconds. */
private const val StripOut = 300
private const val StripIn = 400

/** One end of the switch label: a collection's icon and name in [ink]. Its background is drawn by the label itself. */
@Composable
private fun PlacePill(source: Source, ink: Color, modifier: Modifier) {
    val icon = when (source) {
        is Source.Folder -> FrsIcons.Folder
        Source.Screenshots -> FrsIcons.Screenshot
        Source.FullResShared -> FrsIcons.Prep
        Source.Favorites -> Icons.Filled.Star
        else -> FrsIcons.Collage
    }
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(
            if (source == Source.All) stringResource(R.string.photo_wall) else source.title(),
            color = ink,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).widthIn(max = 120.dp),
        )
    }
}

/** Icon above label, each taking an equal share of the bar; long labels wrap onto further lines. */
@Composable
private fun RowScope.BarAction(icon: ImageVector, label: String, iconModifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = iconModifier)
        Text(label, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
    }
}

/** How far a photo is zoomed in and where it has been panned to. Two photos given the same one zoom and pan together. */
class ZoomState {
    val scale = mutableFloatStateOf(1f)
    val offset = mutableStateOf(Offset.Zero)
}

/**
 * One photo: pinch or double-tap to zoom. At normal size a vertical drag is reported through
 * [onVerticalDrag] (pixels moved) and [onVerticalDragEnd], for the viewer to turn into the details
 * sheet or a dismissal. Sideways swipes are left to the pager, unless [onHorizontalDrag] is given, in
 * which case they are reported the same way.
 *
 * Only the [active] page reports its zoom through [onZoomed]; a change of [unzoom] asks it to zoom
 * back out, and a page that stops being active resets itself.
 */
@Composable
internal fun ZoomableImage(
    photo: Photo,
    active: Boolean,
    unzoom: Int,
    onZoomed: (Boolean) -> Unit,
    onTap: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
    onHorizontalDrag: ((Float) -> Unit)? = null,
    onHorizontalDragEnd: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    shared: ZoomState? = null,
) {
    val context = LocalContext.current
    val key = photo.cacheKey
    val configuration = LocalConfiguration.current
    // Twice the screen's short side: sharp when fitted and up to 2x zoom, without decoding every pixel
    // of a large photo just to show it at screen size.
    val screenEdge = with(LocalDensity.current) { minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.toPx() }.toInt() * 2

    // Three stages, each replacing the last: the wall's thumbnail (usually already in memory, so the
    // photo appears at once), a screen-sized decode, and the full image only if the user zooms in.
    var placeholder by remember(key) { mutableStateOf(cachedThumb(photo)) }
    LaunchedEffect(key) {
        if (placeholder == null) placeholder = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(photo.uri, Size(512, 512), null).asImageBitmap() }.getOrNull()
        }
    }
    val fitted by produceState(screenCache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching { Media.decode(context, photo.uri, maxEdge = screenEdge).asImageBitmap() }.getOrNull()
        }?.also { screenCache.put(key, it) }
    }
    // Its own zoom, unless it has been given one to share with another photo.
    val zoom = shared ?: remember { ZoomState() }
    var scale by zoom.scale
    var detail by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    val wantsDetail = scale > 2f && maxOf(photo.width, photo.height) > screenEdge
    LaunchedEffect(key, wantsDetail) {
        if (wantsDetail && detail == null) detail = withContext(Dispatchers.IO) {
            runCatching { Media.decode(context, photo.uri, maxEdge = 6000, maxPixels = 40_000_000).asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = detail ?: fitted ?: placeholder
    var offset by zoom.offset
    val scope = rememberCoroutineScope()
    /** The double-tap zoom animation in flight, so a second double-tap or a pinch can take over from it. */
    var zooming by remember { mutableStateOf<Job?>(null) }

    /** Eases to [toScale] and [toOffset]; shared by double-tap and Back. */
    fun zoomTo(toScale: Float, toOffset: Offset) {
        val fromScale = scale
        val fromOffset = offset
        zooming?.cancel()
        zooming = scope.launch {
            animate(0f, 1f, animationSpec = tween(240, easing = FastOutSlowInEasing)) { t, _ ->
                scale = fromScale + (toScale - fromScale) * t
                offset = fromOffset + (toOffset - fromOffset) * t
            }
        }
    }

    val reportZoomed by rememberUpdatedState(onZoomed)
    val isZoomed = scale > 1f
    LaunchedEffect(active, isZoomed) { if (active) reportZoomed(isZoomed) }
    LaunchedEffect(unzoom) { if (unzoom > 0 && active && scale > 1f) zoomTo(1f, Offset.Zero) }
    LaunchedEffect(active) {
        if (!active) {
            zooming?.cancel()
            scale = 1f
            offset = Offset.Zero
        }
    }
    val tap by rememberUpdatedState(onTap)
    val verticalDrag by rememberUpdatedState(onVerticalDrag)
    val verticalDragEnd by rememberUpdatedState(onVerticalDragEnd)
    val horizontalDrag by rememberUpdatedState(onHorizontalDrag)
    val horizontalDragEnd by rememberUpdatedState(onHorizontalDragEnd)
    val longPress by rememberUpdatedState(onLongPress)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var travelled = Offset.Zero
                    // Decided once the finger has clearly moved: null until then.
                    var vertical: Boolean? = null
                    // A sideways drag that this handler reports itself, when the caller asked for those.
                    var sideways = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            // Zooming or zoomed: this handler owns the gesture. Only real movement interrupts a
                            // double-tap animation; the tap's own release arrives here too and must not stop it.
                            if (event.changes.size > 1 || event.changes.any { it.positionChanged() }) zooming?.cancel()
                            if (vertical == true) verticalDragEnd()
                            if (sideways) horizontalDragEnd()
                            sideways = false
                            vertical = false
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 8f)
                            val maxX = size.width * (scale - 1) / 2
                            val maxY = size.height * (scale - 1) / 2
                            val moved = offset + event.calculatePan()
                            offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            val change = event.changes[0]
                            val step = change.positionChange()
                            travelled += step
                            if (vertical == null && travelled.getDistance() > viewConfiguration.touchSlop) {
                                vertical = abs(travelled.y) > abs(travelled.x)
                                sideways = vertical == false && horizontalDrag != null
                            }
                            if (vertical == true) {
                                verticalDrag(step.y)
                                change.consume()
                            } else if (sideways) {
                                horizontalDrag?.invoke(step.x)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (vertical == true) verticalDragEnd()
                    if (sideways) horizontalDragEnd()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onLongPress = { longPress?.invoke() },
                    onDoubleTap = { tapped ->
                        val toScale = if (scale > 1f) 1f else 2.5f
                        // Zooming in keeps the tapped point under the finger, as far as the edges allow.
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val maxX = size.width * (toScale - 1) / 2
                        val maxY = size.height * (toScale - 1) / 2
                        val wanted = (centre - tapped) * (toScale - 1)
                        zoomTo(toScale, Offset(wanted.x.coerceIn(-maxX, maxX), wanted.y.coerceIn(-maxY, maxY)))
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
