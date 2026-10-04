package com.findliujie.fullresshare.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.PhotoSort
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One zoom level of the photo wall. Pinching steps between them. */
private sealed interface Wall {
    /** Compact: equal square cells at least [cell] wide. */
    data class Squares(val cell: Dp) : Wall

    /** Enlarged: rows about [rowHeight] tall where every photo keeps its own aspect ratio. */
    data class Justified(val rowHeight: Dp) : Wall
}

/** Most enlarged first; [Store.DEFAULT_GRID_ZOOM] is the Gallery-like four-column squares. */
private val walls = listOf(Wall.Justified(260.dp), Wall.Justified(150.dp), Wall.Squares(88.dp), Wall.Squares(58.dp))

/** Justified rows are laid out on a grid this many columns wide, each photo spanning its share of them. */
private const val Units = 60
private val Gap = 2.dp

private sealed interface GridRow {
    data class Header(val key: String, val label: String) : GridRow

    /** [height] null means a square cell. */
    data class Cell(val photo: Photo, val span: Int = 1, val height: Dp? = null) : GridRow
}

/** Splits [photos] into month groups by [sort]; null means one unlabelled group. */
private fun buildRows(photos: List<Photo>, sort: PhotoSort?, wall: Wall, width: Dp): List<GridRow> {
    val rows = ArrayList<GridRow>(photos.size + 24)
    fun addGroup(group: List<Photo>) {
        if (wall is Wall.Justified) justify(group, wall.rowHeight, width, rows) else group.mapTo(rows) { GridRow.Cell(it) }
    }
    if (sort == null) {
        addGroup(photos)
        return rows
    }
    val zone = ZoneId.systemDefault()
    val thisYear = Year.now(zone).value
    var last: YearMonth? = null
    var group = ArrayList<Photo>()
    for (photo in photos) {
        val month = YearMonth.from(Instant.ofEpochMilli(photo.time(sort)).atZone(zone))
        if (month != last) {
            addGroup(group)
            group = ArrayList()
            last = month
            val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            rows += GridRow.Header("h$month", if (month.year == thisYear) name else "$name ${month.year}")
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
        row.forEachIndexed { i, photo -> out += GridRow.Cell(photo, spans[i], height) }
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

/** Where a long-press drag started and what was selected before it, so dragging back can shrink the range. */
private class DragSelect {
    var anchor = -1
    var base = emptySet<Long>()
    var position = Offset.Zero
}

/**
 * Month-grouped thumbnail grid. Long-press starts a selection; keep dragging to extend it over a
 * range, with the grid scrolling when the finger nears the top or bottom edge.
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
) = BoxWithConstraints(modifier) {
    val wall = walls[zoom.coerceIn(walls.indices)]
    val rows = remember(photos, sort, wall, maxWidth) { buildRows(photos, sort, wall, maxWidth) }
    val currentZoom by rememberUpdatedState(zoom)
    val changeZoom by rememberUpdatedState(onZoom)
    val indexById = remember(photos) { HashMap<Long, Int>(photos.size).apply { photos.forEachIndexed { i, p -> put(p.id, i) } } }
    val state = rememberLazyGridState()
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

    LazyVerticalGrid(
        columns = if (wall is Wall.Squares) GridCells.Adaptive(wall.cell) else GridCells.Fixed(Units),
        state = state,
        contentPadding = bottomPadding,
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            // Pinch steps through the zoom levels. Taken in the Initial pass so two fingers never scroll the grid.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var spread = 1f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        spread *= event.calculateZoom()
                        val step = if (spread > 1.3f) -1 else if (spread < 0.77f) 1 else 0
                        if (step != 0) {
                            spread = 1f
                            val next = (currentZoom + step).coerceIn(walls.indices)
                            if (next != currentZoom) changeZoom(next)
                        }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }.pointerInput(photos) {
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
                is GridRow.Header -> Text(
                    row.label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
                )
                is GridRow.Cell -> {
                    val photo = row.photo
                    val selected = selection?.contains(photo.id) == true
                    PhotoCell(photo, row.height, selecting = selection != null, selected = selected) {
                        if (selection == null) onOpen(photo)
                        else onSelectionChange(if (selected) selection - photo.id else selection + photo.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(photo: Photo, height: Dp?, selecting: Boolean, selected: Boolean, onClick: () -> Unit) {
    val inset by animateDpAsState(if (selected) 12.dp else 0.dp, label = "inset")
    val primary = MaterialTheme.colorScheme.primary
    Box(
        (if (height == null) Modifier.aspectRatio(1f) else Modifier.height(height))
            .background(if (selected) primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Thumb(
            photo,
            Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(if (selected) 8.dp else 0.dp)),
            // Enlarged cells are several times the size of the square ones and need a sharper thumbnail.
            size = if (height == null) 512 else 1024,
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
                if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
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

/** Top bar shown while selecting: close, count, then the actions for the selected photos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    photos: List<Photo>,
    selection: Set<Long>,
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
            IconButton(onClick = { onSelectionChange(null) }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
        },
        actions = {
            if (inTrash) {
                IconButton(onClick = { act(actions.restore) }) { Icon(Icons.Filled.Refresh, contentDescription = "Restore") }
                IconButton(onClick = { act(actions.delete) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete permanently") }
            } else {
                ShareIconButton(onFullRes = { act(actions.shareFullRes) }, onStock = { act(actions.shareStock) })
                IconButton(onClick = { act(actions.prep) }) { Icon(FrsIcons.Prep, contentDescription = "Prep for FullResShare") }
                IconButton(onClick = { act(actions.trash) }) { Icon(Icons.Filled.Delete, contentDescription = "Move to trash") }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = { menu = false; onSelectionChange(photos.map { it.id }.toSet()) },
                    )
                    if (!inTrash) {
                        DropdownMenuItem(
                            text = { Text("Favourite") },
                            onClick = { menu = false; act { actions.setFavorite(it, true) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Undo FullResShare prep") },
                            onClick = { menu = false; act(actions.unprep) },
                        )
                    }
                }
            }
        },
    )
}
