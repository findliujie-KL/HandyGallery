package com.lmxyapp.handygallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.PhotoSort
import com.lmxyapp.handygallery.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** One zoom level of the photo wall. Pinching moves between them. */
private sealed interface Wall {
    /** Compact: equal square cells at least [cell] wide. */
    data class Squares(val cell: Dp) : Wall

    /** Enlarged: rows about [rowHeight] tall where every photo keeps its own aspect ratio. */
    data class Justified(val rowHeight: Dp) : Wall
}

/** Most enlarged first; Store.DEFAULT_GRID_ZOOM is the Gallery-like four-column squares. */
private val walls = listOf(Wall.Justified(420.dp), Wall.Justified(260.dp), Wall.Justified(150.dp), Wall.Squares(88.dp), Wall.Squares(58.dp))

/** Justified rows are laid out on a grid this many columns wide, each photo spanning its share of them. */
private const val Units = 60
private val Gap = 2.dp

private sealed interface GridRow {
    /** [sub] marks a folder heading inside a month. */
    data class Header(val key: String, val label: String, val sub: Boolean = false) : GridRow

    /** [height] null means a square cell. [thumb] is the thumbnail size to load, larger for larger cells. */
    data class Cell(val photo: Photo, val span: Int = 1, val height: Dp? = null, val thumb: Int = 512) : GridRow
}

/**
 * Splits [photos] into month groups by [sort]; null means one unlabelled group. With [byFolder] each
 * month is split again wherever the folder changes, which expects a month's photos to arrive folder by folder.
 */
private fun buildRows(
    photos: List<Photo>,
    sort: PhotoSort?,
    byFolder: Boolean,
    wall: Wall,
    width: Dp,
    monthLabel: (YearMonth) -> String,
): List<GridRow> {
    val rows = ArrayList<GridRow>(photos.size + 24)
    fun addGroup(group: List<Photo>) {
        when (wall) {
            is Wall.Justified -> justify(group, wall.rowHeight, width, rows)
            is Wall.Squares -> group.mapTo(rows) { GridRow.Cell(it) }
        }
    }
    if (sort == null) {
        addGroup(photos)
        return rows
    }
    val zone = ZoneId.systemDefault()
    var last: YearMonth? = null
    var lastBucket = 0L
    var group = ArrayList<Photo>()
    for (photo in photos) {
        val month = YearMonth.from(Instant.ofEpochMilli(photo.time(sort)).atZone(zone))
        val newMonth = month != last
        if (newMonth || (byFolder && photo.bucketId != lastBucket)) {
            addGroup(group)
            group = ArrayList()
            if (newMonth) rows += GridRow.Header("h$month", monthLabel(month))
            if (byFolder) rows += GridRow.Header("h$month/${photo.bucketId}", photo.bucketName, sub = true)
            last = month
            lastBucket = photo.bucketId
        }
        group += photo
    }
    addGroup(group)
    return rows
}

/**
 * Packs [group] into rows that each fill [width]: photos are added to a row until it would be shorter
 * than [target], then the row's height is set so their true aspect ratios exactly fill the width.
 */
private fun justify(group: List<Photo>, target: Dp, width: Dp, out: MutableList<GridRow>) {
    // Extreme panoramas and slivers are capped so one photo can't flatten or dominate a row.
    fun aspect(p: Photo) = if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.4f, 2.5f) else 1f
    val fills = width / target
    var row = ArrayList<Photo>()
    var sum = 0f

    fun flush(complete: Boolean) {
        if (row.isEmpty()) return
        // A trailing short row keeps the target height and simply doesn't reach the right edge.
        val height = if (complete) (width - Gap * (row.size - 1)) / sum else target
        val shares = row.map { aspect(it) / (if (complete) sum else fills) * Units }
        val spans = shares.map { it.toInt().coerceAtLeast(1) }.toMutableList()
        if (complete) {
            // Hand the columns lost to rounding down to the photos that lost the most.
            val byLoss = shares.indices.sortedByDescending { shares[it] - spans[it] }
            var spare = Units - spans.sum()
            var i = 0
            while (spare > 0) { spans[byLoss[i++ % byLoss.size]]++; spare-- }
        }
        // Enlarged cells are several times the size of the square ones and need a sharper thumbnail.
        val thumb = if (target >= 400.dp) 1536 else 1024
        row.forEachIndexed { i, photo -> out += GridRow.Cell(photo, spans[i], height, thumb) }
        row = ArrayList()
        sum = 0f
    }

    for (photo in group) {
        row += photo
        sum += aspect(photo)
        if (sum >= fills) flush(complete = true)
    }
    flush(complete = false)
}

/**
 * Thumbnail wall grouped by month. Long-press starts a selection and dragging extends it over a range.
 *
 * Pinching moves between zoom levels the way Samsung Gallery does: the current layout scales with the
 * fingers and fades out while the next one fades in around the same photo, then it settles on release.
 *
 * [header] scrolls above the photos; [headingAction] sits at the right of the first month heading.
 * [byFolder] adds a folder heading wherever the folder changes within a month; [photos] must then be
 * ordered that way (see Media.byMonthAndFolder).
 *
 * [selection] null means not in selection mode. Don't give this top content padding: drag hit-testing
 * assumes item offsets start at the grid's own origin.
 */
@Composable
fun PhotoGrid(
    photos: List<Photo>,
    sort: PhotoSort?,
    selection: Set<Long>?,
    onSelectionChange: (Set<Long>?) -> Unit,
    onOpen: (Photo) -> Unit,
    zoom: Int,
    onZoom: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null,
    headingAction: (@Composable () -> Unit)? = null,
    byFolder: Boolean = false,
) = BoxWithConstraints(modifier.clipToBounds()) {
    val density = LocalDensity.current
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    val locale = LocalConfiguration.current.locales[0]
    val thisYearFormat = DateTimeFormatter.ofPattern(stringResource(R.string.month_this_year), locale)
    val otherYearFormat = DateTimeFormatter.ofPattern(stringResource(R.string.month_other_year), locale)

    val width = maxWidth
    val rowsCache = remember(photos, sort, byFolder, width, locale) { HashMap<Int, List<GridRow>>() }
    fun rowsFor(level: Int) = rowsCache.getOrPut(level) {
        val thisYear = Year.now().value
        buildRows(photos, sort, byFolder, walls[level], width) { (if (it.year == thisYear) thisYearFormat else otherYearFormat).format(it) }
    }

    /** How big a cell is at [level], used as the scale between two levels. */
    fun cellPx(level: Int): Float = with(density) {
        when (val wall = walls[level]) {
            is Wall.Justified -> wall.rowHeight.toPx()
            is Wall.Squares -> {
                val gap = Gap.toPx()
                val columns = ((widthPx + gap) / (wall.cell.toPx() + gap)).toInt().coerceAtLeast(1)
                (widthPx - gap * (columns - 1)) / columns
            }
        }
    }

    // Each level keeps its own scroll position so a half-finished pinch can fall back to where it was.
    val states = remember { HashMap<Int, LazyGridState>() }
    fun stateFor(level: Int) = states.getOrPut(level) { LazyGridState() }

    /** The settled level on screen. Mirrors [zoom], but changes in the same frame a pinch commits. */
    var shown by remember { mutableIntStateOf(zoom.coerceIn(walls.indices)) }
    /** The level being cross-faded to, with [progress] 0 (all [shown]) to 1 (all [target]). */
    var target by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pivot by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val settling = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(zoom) { if (target == null) shown = zoom.coerceIn(walls.indices) }

    val headerCount = if (header != null) 1 else 0

    /** Opens [to] underneath [shown], scrolled so the photo under the pinch stays under it. */
    val begin by rememberUpdatedState { to: Int ->
        val visible = stateFor(shown).layoutInfo.visibleItemsInfo.filter { it.key is Long }
        val anchor = visible.firstOrNull {
            pivot.x >= it.offset.x && pivot.x < it.offset.x + it.size.width &&
                pivot.y >= it.offset.y && pivot.y < it.offset.y + it.size.height
        } ?: visible.firstOrNull()
        val state = LazyGridState()
        if (anchor != null) {
            val index = rowsFor(to).indexOfFirst { it is GridRow.Cell && it.photo.id == anchor.key }
            val top = pivot.y - (pivot.y - anchor.offset.y) * cellPx(to) / cellPx(shown)
            if (index >= 0) state.requestScrollToItem(index + headerCount, -top.roundToInt())
        }
        states[to] = state
        target = to
        progress = 0f
    }
    val commit by rememberUpdatedState { to: Int ->
        shown = to
        target = null
        progress = 0f
        onZoom(to)
    }

    val gestures = Modifier.pointerInput(widthPx, heightPx) {
        // Taken in the Initial pass so two fingers never scroll the grid or start a selection.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var spread = 1f
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    if (!pinching) {
                        pinching = true
                        settling[0]?.cancel()
                        // Pick up a transition that was still settling rather than jumping.
                        spread = target?.let { (cellPx(it) / cellPx(shown)).pow(progress) } ?: 1f
                        if (target == null) pivot = event.calculateCentroid()
                    }
                    spread *= event.calculateZoom()
                    while (true) {
                        // Spreading the fingers enlarges, which is the lower index.
                        val next = if (spread > 1.001f) shown - 1 else if (spread < 0.999f) shown + 1 else -1
                        if (next !in walls.indices) {
                            target = null
                            progress = 0f
                            spread = 1f
                            break
                        }
                        if (target != next) begin(next)
                        val ratio = cellPx(next) / cellPx(shown)
                        val reached = ln(spread) / ln(ratio)
                        if (reached < 1f) {
                            progress = reached
                            break
                        }
                        // Passed the next level completely: make it the base and carry on from there.
                        commit(next)
                        spread /= ratio
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })

            val pending = target
            if (pinching && pending != null) settling[0] = scope.launch {
                val end = if (progress > 0.4f) 1f else 0f
                animate(progress, end, animationSpec = tween(220, easing = FastOutSlowInEasing)) { value, _ -> progress = value }
                if (end == 1f) commit(pending) else {
                    target = null
                    progress = 0f
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().then(gestures)) {
        val incoming = target
        listOfNotNull(shown, incoming).distinct().forEach { level ->
            key(level) {
                WallLayer(
                    rows = rowsFor(level),
                    photos = photos,
                    wall = walls[level],
                    state = stateFor(level),
                    // Only a settled wall takes taps and selections; mid-transition there are two on screen.
                    interactive = incoming == null,
                    selection = selection,
                    onSelectionChange = onSelectionChange,
                    onOpen = onOpen,
                    bottomPadding = bottomPadding,
                    header = header,
                    headingAction = headingAction,
                    modifier = Modifier.graphicsLayer {
                        val to = target
                        if (to != null) {
                            val ratio = cellPx(to) / cellPx(shown)
                            // Both layers show the pinched photo at the same size throughout the fade.
                            val scale = if (level == to) ratio.pow(progress - 1f) else ratio.pow(progress)
                            scaleX = scale
                            scaleY = scale
                            alpha = (if (level == to) progress else 1f - progress).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(pivot.x / widthPx, pivot.y / heightPx)
                        }
                    },
                )
            }
        }
    }
}

/** Where a long-press drag started and what was selected before it, so dragging back can shrink the range. */
private class DragSelect {
    var anchor = -1
    var base = emptySet<Long>()
    var position = Offset.Zero
}

/** One zoom level's grid, with drag-to-select. */
@Composable
private fun WallLayer(
    rows: List<GridRow>,
    photos: List<Photo>,
    wall: Wall,
    state: LazyGridState,
    interactive: Boolean,
    selection: Set<Long>?,
    onSelectionChange: (Set<Long>?) -> Unit,
    onOpen: (Photo) -> Unit,
    bottomPadding: PaddingValues,
    header: (@Composable () -> Unit)?,
    headingAction: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    val firstHeading = remember(rows) { rows.indexOfFirst { it is GridRow.Header } }
    val indexById = remember(photos) { HashMap<Long, Int>(photos.size).apply { photos.forEachIndexed { i, p -> put(p.id, i) } } }
    val drag = remember { DragSelect() }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val currentSelection by rememberUpdatedState(selection)
    val changeSelection by rememberUpdatedState(onSelectionChange)

    fun indexAt(position: Offset): Int? {
        val hit = state.layoutInfo.visibleItemsInfo.firstOrNull {
            position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
                position.y >= it.offset.y && position.y < it.offset.y + it.size.height
        }
        return (hit?.key as? Long)?.let(indexById::get)
    }

    val extendTo by rememberUpdatedState { position: Offset ->
        val index = indexAt(position)
        if (drag.anchor >= 0 && index != null) {
            val range = minOf(drag.anchor, index)..maxOf(drag.anchor, index)
            changeSelection(drag.base + range.map { photos[it].id })
        }
    }

    // While the finger rests near an edge no drag events arrive, so the selection is extended from here too.
    LaunchedEffect(autoScroll) {
        if (autoScroll != 0f) while (isActive) {
            state.scrollBy(autoScroll)
            extendTo(drag.position)
            delay(10)
        }
    }

    // The month each row falls under, for the scroller's label.
    val months = remember(rows) {
        var current: String? = null
        rows.map { row ->
            if (row is GridRow.Header && !row.sub) current = row.label
            current
        }
    }
    val headerCount = if (header != null) 1 else 0
    val density = LocalDensity.current
    val viewport by remember(state) { derivedStateOf { state.layoutInfo.viewportSize } }
    val extent = remember(rows, wall, viewport.width, headerCount, density) {
        measureWall(rows, wall, viewport.width.toFloat(), headerCount, density)
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (wall is Wall.Squares) GridCells.Adaptive(wall.cell) else GridCells.Fixed(Units),
            state = state,
            contentPadding = bottomPadding,
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
            userScrollEnabled = interactive,
            modifier = Modifier.fillMaxSize().pointerInput(photos, interactive) {
                if (!interactive) return@pointerInput
                val edge = 96.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val index = indexAt(press.position) ?: return@awaitEachGesture
                    drag.anchor = index
                    drag.base = currentSelection.orEmpty()
                    drag.position = press.position
                    changeSelection(drag.base + photos[index].id)

                    // The stock detectDragGesturesAfterLongPress loses the drag to the grid's own scrolling,
                    // which sees moves first. Taking them in the Initial pass keeps the gesture ours; consuming
                    // the release also stops the cell under the finger from treating it as a tap.
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        drag.position = change.position
                        val fromBottom = size.height - change.position.y
                        autoScroll = when {
                            fromBottom < edge -> (edge - fromBottom) / 4
                            change.position.y < edge -> -(edge - change.position.y) / 4
                            else -> 0f
                        }
                        extendTo(change.position)
                    }
                    drag.anchor = -1
                    autoScroll = 0f
                }
            },
        ) {
            if (header != null) item(key = "top", span = { GridItemSpan(maxLineSpan) }, contentType = "top") { header() }
            items(
                count = rows.size,
                key = { when (val row = rows[it]) { is GridRow.Header -> row.key; is GridRow.Cell -> row.photo.id } },
                span = { when (val row = rows[it]) { is GridRow.Header -> GridItemSpan(maxLineSpan); is GridRow.Cell -> GridItemSpan(row.span) } },
                contentType = { if (rows[it] is GridRow.Header) "header" else "cell" },
            ) { i ->
                when (val row = rows[i]) {
                    is GridRow.Header -> if (row.sub) Text(
                        row.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                    ) else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
                        )
                        if (i == firstHeading && headingAction != null) Box(Modifier.padding(top = 10.dp)) { headingAction() }
                    }
                    is GridRow.Cell -> {
                        val photo = row.photo
                        val selected = selection?.contains(photo.id) == true
                        PhotoCell(photo, row.height, row.thumb, selecting = selection != null, selected = selected, enabled = interactive) {
                            if (selection == null) onOpen(photo)
                            else onSelectionChange(if (selected) selection - photo.id else selection + photo.id)
                        }
                    }
                }
            }
        }
        if (interactive) FastScroller(
            state = state,
            extent = extent,
            label = { index -> months.getOrNull((index - headerCount).coerceIn(0, (months.size - 1).coerceAtLeast(0))) },
            bottom = bottomPadding.calculateBottomPadding(),
        )
    }
}

/** Where each grid item's line starts and how tall it is, in pixels, and the height of the whole wall. */
private class Extent(val tops: FloatArray, val heights: FloatArray, val total: Float)

/**
 * Works out the wall's full layout ahead of time so the scroller can place itself by distance rather
 * than by item count; rows hold different numbers of photos, and counting items made the handle
 * speed up and slow down. Heading heights are estimates, which is close enough for this.
 */
private fun measureWall(rows: List<GridRow>, wall: Wall, widthPx: Float, headerCount: Int, density: Density): Extent = with(density) {
    val gap = Gap.toPx()
    val columns = if (wall is Wall.Squares) ((widthPx + gap) / (wall.cell.toPx() + gap)).toInt().coerceAtLeast(1) else Units
    val square = (widthPx - gap * (columns - 1)) / columns
    val count = rows.size + headerCount
    val tops = FloatArray(count)
    val heights = FloatArray(count)
    var y = 0f
    var used = 0
    var lineHeight = 0f
    var lineStart = 0

    fun endLine(until: Int) {
        if (until > lineStart) {
            for (k in lineStart until until) {
                tops[k] = y
                heights[k] = lineHeight + gap
            }
            y += lineHeight + gap
        }
        lineStart = until
        used = 0
        lineHeight = 0f
    }

    if (headerCount == 1) {
        lineHeight = 110.dp.toPx()
        endLine(1)
    }
    rows.forEachIndexed { r, row ->
        val i = r + headerCount
        val span = when (row) {
            is GridRow.Header -> columns
            is GridRow.Cell -> row.span.coerceAtMost(columns)
        }
        if (used + span > columns) endLine(i)
        used += span
        val height = when (row) {
            is GridRow.Header -> (if (row.sub) 36.dp else 64.dp).toPx()
            is GridRow.Cell -> row.height?.toPx() ?: square
        }
        lineHeight = maxOf(lineHeight, height)
    }
    endLine(count)
    Extent(tops, heights, y)
}

/**
 * A handle on the right edge that appears while the wall is scrolling and can be dragged to move
 * through the whole wall quickly; while it is held, a bubble beside it names the month reached.
 */
@Composable
private fun BoxScope.FastScroller(state: LazyGridState, extent: Extent, label: (Int) -> String?, bottom: Dp) {
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true else {
            delay(600)
            shown = false
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "scroller")

    val viewportHeight = state.layoutInfo.viewportSize.height
    // Not worth showing on a wall of only a few screens; and once faded out it must not take touches.
    if (alpha < 0.01f || viewportHeight == 0 || extent.tops.isEmpty() || extent.total < viewportHeight * 4f) return
    /** The furthest the wall can scroll. */
    val range = (extent.total - viewportHeight).coerceAtLeast(1f)

    var dragFraction by remember { mutableFloatStateOf(0f) }

    /** How far down the wall is, 0 to 1, by distance. */
    fun fraction(): Float {
        if (dragging) return dragFraction
        val first = state.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
        val i = first.index.coerceIn(extent.tops.indices)
        // The line's real height can differ a little from the estimate; scaling keeps the handle from
        // hopping as one line gives way to the next.
        val real = first.size.height + state.layoutInfo.mainAxisItemSpacing
        val within = if (real > 0) state.firstVisibleItemScrollOffset.toFloat() / real * extent.heights[i] else 0f
        return ((extent.tops[i] + within) / range).coerceIn(0f, 1f)
    }
    val handleHeight = 52.dp

    BoxWithConstraints(
        Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(top = 8.dp, bottom = bottom + 8.dp).graphicsLayer { this.alpha = alpha },
    ) {
        val trackPx = constraints.maxHeight - with(LocalDensity.current) { handleHeight.toPx() }
        val currentFraction by rememberUpdatedState(::fraction)
        val currentRange by rememberUpdatedState(range)
        val currentExtent by rememberUpdatedState(extent)
        val currentTrack by rememberUpdatedState(trackPx)
        var track by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }

        Row(
            Modifier.onGloballyPositioned { track = it }.offset { IntOffset(0, (fraction() * trackPx).roundToInt()) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text = label(state.firstVisibleItemIndex)
            if (dragging && text != null) Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(end = 10.dp),
            ) {
                Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            val arrows = MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    // Wider than the pill that is drawn, so it is easy to catch.
                    .size(36.dp, handleHeight)
                    .onGloballyPositioned { handle = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            // The handle moves as it is dragged, so its own coordinates shift under the finger;
                            // positions are taken in the track's coordinates, which stay put.
                            fun inTrack(local: Offset): Float {
                                val from = handle
                                val to = track
                                return if (from != null && to != null && from.isAttached && to.isAttached) to.localPositionOf(from, local).y else local.y
                            }
                            val from = currentFraction()
                            val grab = inTrack(down.position) - from * currentTrack
                            dragFraction = from
                            dragging = true
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                dragFraction = ((inTrack(change.position) - grab) / currentTrack).coerceIn(0f, 1f)
                                // The last line that starts at or above the wanted distance, then the rest as an offset.
                                val y = dragFraction * currentRange
                                val tops = currentExtent.tops
                                val found = tops.binarySearch(y)
                                val index = (if (found >= 0) found else -found - 2).coerceIn(tops.indices)
                                val into = (y - tops[index]).coerceIn(0f, (currentExtent.heights[index] - 1f).coerceAtLeast(0f))
                                state.requestScrollToItem(index, into.roundToInt())
                            }
                            dragging = false
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(18.dp, handleHeight),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val x = size.width / 2
                        val w = 3.5.dp.toPx()
                        val stroke = 1.5.dp.toPx()
                        // An up and a down chevron.
                        listOf(size.height / 2 - 6.dp.toPx() to -1f, size.height / 2 + 6.dp.toPx() to 1f).forEach { (y, dir) ->
                            val tip = Offset(x, y + dir * w * 0.6f)
                            drawLine(arrows, Offset(x - w, y - dir * w * 0.4f), tip, stroke, StrokeCap.Round)
                            drawLine(arrows, Offset(x + w, y - dir * w * 0.4f), tip, stroke, StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(photo: Photo, height: Dp?, thumb: Int, selecting: Boolean, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val inset by animateDpAsState(if (selected) 12.dp else 0.dp, label = "inset")
    val primary = MaterialTheme.colorScheme.primary
    Box(
        (if (height == null) Modifier.aspectRatio(1f) else Modifier.height(height))
            .background(if (selected) primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(enabled, onClick = onClick),
    ) {
        Thumb(
            photo,
            Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(if (selected) 8.dp else 0.dp)),
            size = thumb,
        )
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) primary else Color.Black.copy(alpha = 0.15f))
                    .then(if (selected) Modifier else Modifier.border(2.dp, Color.White, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected), tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Callbacks for acting on a set of photos; shared by the selection bar and the viewer. */
class PhotoActions(
    val shareFullRes: (List<Photo>) -> Unit,
    val shareStock: (List<Photo>) -> Unit,
    val prep: (List<Photo>) -> Unit,
    val unprep: (List<Photo>) -> Unit,
    val trash: (List<Photo>) -> Unit,
    val restore: (List<Photo>) -> Unit,
    val delete: (List<Photo>) -> Unit,
    val setFavorite: (List<Photo>, Boolean) -> Unit,
)

/**
 * Top bar shown while selecting: close, count, then the actions for the selected photos.
 * [marked] are the photo ids already in the front row; when everything selected is, the button offers a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    photos: List<Photo>,
    selection: Set<Long>,
    marked: Set<Long>,
    inTrash: Boolean,
    actions: PhotoActions,
    onSelectionChange: (Set<Long>?) -> Unit,
) {
    /** Hands the selected photos to [action] and leaves selection mode. */
    fun act(action: (List<Photo>) -> Unit) {
        val picked = photos.filter { it.id in selection }
        onSelectionChange(null)
        if (picked.isNotEmpty()) action(picked)
    }
    TopAppBar(
        title = { Text(selection.size.toString()) },
        navigationIcon = {
            IconButton(onClick = { onSelectionChange(null) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_selection))
            }
        },
        actions = {
            if (inTrash) {
                IconButton(onClick = { act(actions.restore) }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.restore)) }
                IconButton(onClick = { act(actions.delete) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_permanently)) }
            } else {
                ShareIconButton(onFullRes = { act(actions.shareFullRes) }, onStock = { act(actions.shareStock) })
                FrontRowButton(
                    alreadyThere = selection.isNotEmpty() && marked.containsAll(selection),
                    onPrep = { act(actions.prep) },
                    onRemove = { act(actions.unprep) },
                )
                IconButton(onClick = { act(actions.trash) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.move_to_trash)) }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_all)) },
                        onClick = { menu = false; onSelectionChange(photos.map { it.id }.toSet()) },
                    )
                    if (!inTrash) DropdownMenuItem(
                        text = { Text(stringResource(R.string.favourite)) },
                        onClick = { menu = false; act { actions.setFavorite(it, true) } },
                    )
                }
            }
        },
    )
}
