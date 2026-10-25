package com.lmxyapp.handygallery.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lmxyapp.handygallery.Folder
import com.lmxyapp.handygallery.FolderSort
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.PhotoSort
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Screen
import com.lmxyapp.handygallery.Source
import com.lmxyapp.handygallery.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: GalleryViewModel, actions: PhotoActions, onRequestPermission: () -> Unit) {
    val all = vm.photosFor(Source.All)
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    // Photos that are trashed or deleted stay selected until they are gone from the list; taking them
    // out here is what closes selection mode afterwards.
    LaunchedEffect(all) {
        val picked = selection ?: return@LaunchedEffect
        if (picked.isEmpty()) return@LaunchedEffect
        val present = all.mapTo(HashSet()) { it.id }
        val kept = picked.filterTo(HashSet()) { it in present }
        if (kept.size != picked.size) selection = kept.ifEmpty { null }
    }
    val selecting = selection != null
    BackHandler(selecting) { selection = null }
    // Picking photos to compare is selecting with a different top bar; it ends with the selection.
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(selecting) { if (!selecting) picking = false }
    var showHidden by remember { mutableStateOf(false) }
    val hidden = vm.store.hiddenFolders
    val favourites = vm.photosFor(Source.Favorites)
    val favouritesName = stringResource(R.string.favourites)
    val frontRow = vm.photosFor(Source.FrontRow)
    val frontRowName = stringResource(R.string.front_row_album)
    val folders = remember(vm.sorted, vm.store.folderSort, vm.store.folderOrder, favourites, favouritesName, frontRow, frontRowName) {
        // Favourites and Front Row sit among the folders as cards of their own, as long as there is
        // something in them. Front Row's cover is the photo most recently put there.
        fun card(id: Long, name: String, photos: List<Photo>, cover: Photo?) = cover?.let {
            Folder(id, name, it, photos.size, photos.sumOf { p -> p.size }, it.time(PhotoSort.Taken))
        }
        val cards = listOfNotNull(
            card(Media.FAVORITES_ID, favouritesName, favourites, favourites.maxByOrNull { it.time(PhotoSort.Taken) }),
            card(Media.FRONT_ROW_ID, frontRowName, frontRow, frontRow.firstOrNull()),
        )
        Media.folders(vm.sorted, vm.store.folderSort, vm.store.folderOrder, cards)
    }

    // The tiles above the wall. A folder that has since gone, or a collection with nothing in it yet,
    // leaves its tile out rather than showing an empty one.
    val tiles = vm.store.wallShortcuts.mapNotNull { key ->
        when {
            key == Store.SHORTCUT_SCREENSHOTS ->
                Tile(key, Source.Screenshots, vm.photosFor(Source.Screenshots).firstOrNull(), HgIcons.Screenshot, Color.White)
            key == Store.SHORTCUT_FRONT_ROW ->
                Tile(key, Source.FrontRow, vm.photosFor(Source.FrontRow).firstOrNull(), HgIcons.Prep, Color.White)
            key == Store.SHORTCUT_FAVOURITES -> Tile(key, Source.Favorites, favourites.firstOrNull(), Icons.Filled.Star, Color(0xFFFFC107))
            key.startsWith(Store.SHORTCUT_FOLDER) -> {
                val id = key.removePrefix(Store.SHORTCUT_FOLDER).toLongOrNull()
                vm.wallFolders.firstOrNull { it.id == id }?.let { Tile(key, Source.Folder(it.id, it.name), it.cover, null, Color.White) }
            }
            else -> null
        }
    }.filter { it.cover != null }
    val header: (@Composable () -> Unit)? = if (tiles.isEmpty()) null else {
        {
            TipAnchor(Tips.SHORTCUTS, R.string.tip_shortcuts, enabled = tiles.size > 1) {
                ShortcutRow(tiles, enabled = !selecting, onOpen = vm::open) { keys ->
                    // Shortcuts whose folder is missing have no tile to drag; they keep their place at the end.
                    vm.store.chooseWallShortcuts(keys + vm.store.wallShortcuts.filter { it !in keys })
                }
            }
        }
    }

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null && picking) PickTopBar(picked.size, onCancel = { selection = null }) {
                val chosen = all.filter { it.id in picked }
                selection = null
                actions.compare(chosen)
            }
            else if (picked != null) SelectionTopBar(all, picked, vm.store.marks.keys, vm.store.favorites, inTrash = false, actions, removing = vm.leaving.isNotEmpty()) { selection = it }
            else CenterAlignedTopAppBar(
                title = {
                    TipAnchor(Tips.WALL, R.string.tip_wall, enabled = vm.homeTab == 0 && all.isNotEmpty()) { Text(stringResource(R.string.app_name)) }
                },
                navigationIcon = { AppIcon(Modifier.padding(start = 14.dp)) },
                actions = {
                    TipAnchor(Tips.TOOLS, R.string.tip_tools) {
                    ToolsButton(
                        onSort = { actions.sort(all) }.takeIf { all.isNotEmpty() },
                        onSimilar = { vm.open(Screen.Similar) },
                        onCompare = {
                            // Picking happens on the photo wall.
                            vm.homeTab = 0
                            picking = true
                            selection = emptySet()
                        },
                    )
                    }
                    MainMenu(
                        onHelp = { vm.open(Screen.Help) },
                        onTrash = { vm.open(Screen.Collection(Source.Trash)) },
                        onSettings = { vm.open(Screen.Settings) },
                    )
                },
            )
        },
        bottomBar = {
            if (!selecting) NavigationBar {
                NavigationBarItem(
                    selected = vm.homeTab == 0,
                    onClick = { vm.homeTab = 0 },
                    icon = { Icon(HgIcons.Image, contentDescription = null) },
                    label = { Text(stringResource(R.string.photos)) },
                )
                NavigationBarItem(
                    selected = vm.homeTab == 1,
                    onClick = { vm.homeTab = 1 },
                    icon = { Icon(HgIcons.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.folders)) },
                )
            }
        },
    ) { padding ->
        when {
            !vm.canRead -> EmptyHint(stringResource(R.string.need_access), Modifier.padding(padding)) {
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.grant_access)) }
            }
            vm.homeTab == 0 -> PhotoGrid(
                photos = all,
                sort = vm.store.photoSort,
                selection = selection,
                onSelectionChange = { selection = it },
                onOpen = { vm.open(Screen.Viewer(Source.All, it.id)) },
                zoom = vm.store.gridZoom,
                onZoom = vm.store::zoomGrid,
                byFolder = vm.store.wallByFolder,
                focusId = vm.focus,
                frontRow = vm.store.marks.keys,
                favorites = vm.store.favorites,
                leaving = vm.leaving,
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                bottomPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                header = header,
                headingAction = {
                    SortButton(
                        stringResource(vm.store.photoSort.label),
                        enumValues<PhotoSort>().map { stringResource(it.label) },
                        Modifier.padding(end = 8.dp),
                    ) { vm.store.sortPhotos(enumValues<PhotoSort>()[it]) }
                },
            )
            else -> FolderGrid(
                folders = remember(folders, hidden, showHidden) { folders.filter { showHidden || it.id !in hidden } },
                hidden = hidden,
                hiddenCount = folders.count { it.id in hidden },
                showHidden = showHidden,
                onShowHidden = { showHidden = it },
                offWall = vm.store.wallHidden,
                sortLabel = stringResource(vm.store.folderSort.label),
                manual = vm.store.folderSort == FolderSort.Manual,
                onSort = {
                    val sort = enumValues<FolderSort>()[it]
                    // The first time, Manual starts from the order on screen rather than reshuffling it.
                    if (sort == FolderSort.Manual && vm.store.folderOrder.isEmpty()) vm.store.chooseFolderOrder(folders.map { f -> f.id })
                    vm.store.sortFolders(sort)
                },
                onOpen = {
                    vm.open(
                        Screen.Collection(
                            when (it.id) {
                                Media.FAVORITES_ID -> Source.Favorites
                                Media.FRONT_ROW_ID -> Source.FrontRow
                                else -> Source.Folder(it.id, it.name)
                            },
                        ),
                    )
                },
                onHide = { folder, hide -> vm.store.setFolderHidden(folder.id, hide) },
                onWall = { folder, show ->
                    vm.store.chooseWallHidden(if (show) vm.store.wallHidden - folder.id else vm.store.wallHidden + folder.id)
                },
                onReorder = { shown ->
                    // Hidden folders are not on screen to be dragged; they keep their places among the rest.
                    val moved = shown.toSet()
                    val next = shown.iterator()
                    vm.store.chooseFolderOrder(folders.map { f -> if (f.id in moved) next.next() else f.id })
                },
                padding = padding,
            )
        }
    }
}

/** The launcher icon, drawn from its two layers the way a launcher would: the middle two-thirds, rounded. */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    Box(modifier.size(32.dp).clip(RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
        // The layers are 108 units across with 72 visible, so they are drawn 1.5x the icon's size and cropped.
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, Modifier.requiredSize(48.dp))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.requiredSize(48.dp))
    }
}

/** Lets the user tick which folders contribute to the photo wall. [hidden] are the ones currently left off. */
@Composable
fun WallFolderDialog(folders: List<Folder>, hidden: Set<Long>, onDone: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    var off by remember { mutableStateOf(hidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wall_folders)) },
        text = {
            LazyColumn {
                item {
                    val all = folders.none { it.id in off }
                    FolderCheck(stringResource(R.string.select_all), null, all) {
                        off = if (all) folders.map { it.id }.toSet() else emptySet()
                    }
                }
                listItems(folders, key = { it.id }) { folder ->
                    val shown = folder.id !in off
                    FolderCheck(folder.name, folder.count, shown) { off = if (shown) off + folder.id else off - folder.id }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(off) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The things a wall shortcut can open, as key and name: the three built-in collections, then [folders]. */
@Composable
fun shortcutChoices(folders: List<Folder>): List<Pair<String, String>> = listOf(
    Store.SHORTCUT_SCREENSHOTS to stringResource(R.string.screenshots),
    Store.SHORTCUT_FRONT_ROW to stringResource(R.string.front_row_album),
    Store.SHORTCUT_FAVOURITES to stringResource(R.string.favourites),
) + folders.map { Store.SHORTCUT_FOLDER + it.id to it.name }

/** Lets the user tick up to [Store.MAX_SHORTCUTS] of [choices] for the tiles above the photo wall, in the order ticked. */
@Composable
fun ShortcutsDialog(choices: List<Pair<String, String>>, chosen: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(chosen) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wall_shortcuts)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.shortcuts_limit, Store.MAX_SHORTCUTS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn {
                    listItems(choices, key = { it.first }) { (key, name) ->
                        val on = key in picked
                        // Once the limit is reached the rest can't be ticked until one is unticked.
                        FolderCheck(name, null, on, enabled = on || picked.size < Store.MAX_SHORTCUTS) {
                            picked = if (on) picked - key else picked + key
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderCheck(name: String, count: Int?, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled, onClick = onToggle).alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
    }
}

/**
 * One tile above the photo wall; [key] is its entry in the shortcut setting. [icon] null means a folder
 * the user picked, which is labelled by name instead.
 */
private class Tile(val key: String, val source: Source, val cover: Photo?, val icon: ImageVector?, val tint: Color)

private val TileSize = 104.dp
private val TileGap = 12.dp

/**
 * The tiles above the photo wall. Holding one picks it up; dragging it sideways moves it past its
 * neighbours, and [onReorder] gets the new order of keys when it is dropped.
 */
@Composable
private fun ShortcutRow(tiles: List<Tile>, enabled: Boolean, onOpen: (Screen) -> Unit, onReorder: (List<String>) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val base = tiles.map { it.key }
    val step = with(LocalDensity.current) { (TileSize + TileGap).toPx() }
    val tilePx = with(LocalDensity.current) { TileSize.toPx() }
    // Everything about a drag is tied to the order the tiles are laid out in. When a drop changes that
    // order, this starts again from rest in the same frame, so nothing slides twice.
    key(base) {
        /** Where each tile is headed during a drag; the row itself is not re-laid-out until the drop. */
        var order by remember { mutableStateOf(base) }
        var dragged by remember { mutableStateOf<String?>(null) }
        var travelled by remember { mutableFloatStateOf(0f) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val count = base.size
                        // The tiles are centred in the row; find the one under the finger, if any.
                        val into = press.position.x - (size.width - (count * step - TileGap.toPx())) / 2f
                        val from = (into / step).toInt()
                        if (into < 0f || from >= count || into - from * step > tilePx) return@awaitEachGesture
                        val key = base[from]
                        dragged = key
                        travelled = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        // Taken in the Initial pass so the wall doesn't scroll under the drag; consuming the
                        // release also stops the tile from treating it as a tap.
                        var moved = false
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            travelled = change.position.x - press.position.x
                            if (kotlin.math.abs(travelled) > viewConfiguration.touchSlop) moved = true
                            val to = (from + travelled / step).roundToInt().coerceIn(0, count - 1)
                            if (order.indexOf(key) != to) order = (base - key).toMutableList().apply { add(to, key) }
                        }
                        dragged = null
                        if (moved && order != base) onReorder(order)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterHorizontally),
        ) {
            tiles.forEachIndexed { index, tile ->
                // How far this tile's destination is from where it is laid out.
                val slide by animateFloatAsState((order.indexOf(tile.key) - index) * step, label = "tile")
                val lifted = dragged == tile.key
                ShortcutTile(
                    tile, enabled, onOpen,
                    Modifier.zIndex(if (lifted) 1f else 0f).graphicsLayer {
                        translationX = if (lifted) travelled else slide
                        if (lifted) alpha = 0.85f
                    },
                )
            }
        }
    }
}

/** A collection's latest photo, with a badge inside it saying which collection it is. */
@Composable
private fun ShortcutTile(tile: Tile, enabled: Boolean, onOpen: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val title = tile.source.title()
    Box(modifier.size(TileSize).clip(RoundedCornerShape(16.dp)).clickable(enabled) { onOpen(Screen.Collection(tile.source)) }) {
        if (tile.cover != null) Thumb(tile.cover, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
        val scrim = Color.Black.copy(alpha = 0.6f)
        if (tile.icon != null) Box(
            Modifier.align(Alignment.BottomStart).padding(6.dp).size(28.dp).clip(CircleShape).background(scrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tile.icon, contentDescription = title, tint = tile.tint, modifier = Modifier.size(18.dp))
        } else Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(scrim)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Right-aligned "current sort ↕" control that opens the list of choices. */
@Composable
private fun SortButton(
    current: String,
    options: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        // The menu anchors to its parent, so it needs a box the size of the button, not the full-width row.
        Box {
            TextButton(onClick = { open = true }) {
                Text(current)
                Icon(HgIcons.Sort, contentDescription = stringResource(R.string.change_sort), Modifier.padding(start = 4.dp).size(18.dp))
            }
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { i, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(i) })
                }
            }
        }
    }
}

/**
 * Folder cards. Long-pressing one offers to hide it (or unhide it when hidden ones are being shown) and
 * to take it off the photo wall or put it back. Folders in [offWall] carry a badge on their cover.
 *
 * In [manual] order a long-press picks the folder up instead: dragging it moves it among the others
 * and [onReorder] reports the new order when it is dropped. Letting go without dragging opens the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGrid(
    folders: List<Folder>,
    hidden: Set<Long>,
    hiddenCount: Int,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    offWall: Set<Long>,
    sortLabel: String,
    manual: Boolean,
    onSort: (Int) -> Unit,
    onOpen: (Folder) -> Unit,
    onHide: (Folder, Boolean) -> Unit,
    onWall: (Folder, Boolean) -> Unit,
    onReorder: (List<Long>) -> Unit,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val direction = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val grid = rememberLazyGridState()
    /** The folders as shown; while one is being dragged this runs ahead of [folders]. */
    var order by remember(folders) { mutableStateOf(folders) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var dragged by remember { mutableStateOf<Long?>(null) }
    /** Where the finger is, and where on the folder it took hold, both in the grid's coordinates. */
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    /** Moves the dragged folder into the slot its centre is over. */
    fun settle() {
        val id = dragged ?: return
        val items = grid.layoutInfo.visibleItemsInfo
        val mine = items.firstOrNull { it.key == id } ?: return
        val from = order.indexOfFirst { it.id == id }
        // The grid has not laid out the previous move yet; acting on the old layout would apply it twice.
        // (Grid indices are one ahead of the list's because of the sort control above the folders.)
        if (from < 0 || mine.index != from + 1) return
        val centre = finger - grab + Offset(mine.size.width / 2f, mine.size.height / 2f)
        val over = items.firstOrNull {
            it.key is Long && it.key != id &&
                centre.x >= it.offset.x && centre.x < it.offset.x + it.size.width &&
                centre.y >= it.offset.y && centre.y < it.offset.y + it.size.height
        } ?: return
        // Otherwise the grid follows its first visible folder when that one is moved, and the page jumps.
        grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        order = order.toMutableList().apply { add(over.index - 1, removeAt(from)) }
    }

    // While the finger rests near an edge no drag events arrive, so the grid is moved along from here.
    LaunchedEffect(autoScroll) {
        if (autoScroll != 0f) while (isActive) {
            grid.scrollBy(autoScroll)
            settle()
            delay(10)
        }
    }

    val dragging = if (!manual) Modifier else Modifier.pointerInput(folders) {
        val edge = 96.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val item = grid.layoutInfo.visibleItemsInfo.firstOrNull {
                it.key is Long &&
                    press.position.x >= it.offset.x && press.position.x < it.offset.x + it.size.width &&
                    press.position.y >= it.offset.y && press.position.y < it.offset.y + it.size.height
            } ?: return@awaitEachGesture
            val id = item.key as Long
            finger = press.position
            grab = press.position - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
            dragged = id
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            // Taken in the Initial pass so the grid's own scrolling never sees the drag; consuming the
            // release also stops the folder under the finger from treating it as a tap.
            var moved = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id } ?: break
                change.consume()
                if (!change.pressed) break
                if (!moved && (change.position - press.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (moved) {
                    finger = change.position
                    val fromBottom = size.height - change.position.y
                    autoScroll = when {
                        fromBottom < edge -> (edge - fromBottom) / 4
                        change.position.y < edge -> -(edge - change.position.y) / 4
                        else -> 0f
                    }
                    settle()
                }
            }
            autoScroll = 0f
            dragged = null
            if (moved) onReorder(order.map { it.id }) else menuFor = id
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = grid,
        // Top and side insets are ordinary padding rather than content padding, so the positions the
        // grid reports for its folders are in the same coordinates as the touches.
        modifier = Modifier
            .padding(
                start = padding.calculateStartPadding(direction) + 16.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                top = padding.calculateTopPadding(),
            )
            .then(dragging),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TipAnchor(Tips.FOLDER_ORDER, R.string.tip_folder_order, enabled = manual) {
                SortButton(sortLabel, enumValues<FolderSort>().map { stringResource(it.label) }, onPick = onSort)
            }
        }
        items(order, key = { it.id }) { folder ->
            val isHidden = folder.id in hidden
            // Screenshots are never on the wall, whatever the user picks, so that folder gets no choice.
            val favouritesCard = folder.id == Media.FAVORITES_ID
            val frontRowCard = folder.id == Media.FRONT_ROW_ID
            val virtual = favouritesCard || frontRowCard
            val screenshots = !virtual && folder.cover.isScreenshot
            // Favourites and Front Row are not folders, so being on or off the wall doesn't apply to them.
            val onWallNow = virtual || (!screenshots && folder.id !in offWall)
            val lifted = dragged == folder.id
            Column(
                Modifier
                    .then(
                        if (lifted) Modifier.zIndex(1f).graphicsLayer {
                            // Drawn where the finger has it, whichever slot the grid currently gives it.
                            val mine = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == folder.id }
                            if (mine != null) {
                                translationX = finger.x - grab.x - mine.offset.x
                                translationY = finger.y - grab.y - mine.offset.y
                            }
                            alpha = 0.85f
                        } else Modifier.animateItem(),
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onClick = { onOpen(folder) },
                        // In manual order the long-press belongs to the drag gesture above.
                        onLongClick = if (manual) null else { { menuFor = folder.id } },
                    )
                    .alpha(if (isHidden) 0.45f else 1f),
            ) {
                Box {
                    Thumb(folder.cover, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
                    if (!onWallNow) OffWallBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
                    if (virtual) Box(
                        Modifier.align(Alignment.TopStart).padding(8.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (favouritesCard) Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                        else Icon(HgIcons.Prep, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                )
                Text(
                    stringResource(R.string.items_size, folder.count, Formatter.formatShortFileSize(context, folder.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
                DropdownMenu(menuFor == folder.id, { menuFor = null }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isHidden) R.string.unhide_folder else R.string.hide_folder)) },
                        onClick = { menuFor = null; onHide(folder, !isHidden) },
                    )
                    if (!screenshots && !virtual) DropdownMenuItem(
                        text = { Text(stringResource(if (onWallNow) R.string.wall_hide else R.string.wall_show)) },
                        onClick = { menuFor = null; onWall(folder, !onWallNow) },
                    )
                }
            }
        }
        // Hidden folders are out of the way, but a line at the end says how many there are and brings them back.
        if (hiddenCount > 0) item(key = "hidden", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.hidden_folders, hiddenCount, hiddenCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                )
                TextButton(onClick = { onShowHidden(!showHidden) }) {
                    Text(stringResource(if (showHidden) R.string.hide_hidden else R.string.show))
                }
            }
        }
    }
}

/** The photo-wall icon struck through, on a dark disc so it reads over any cover. */
@Composable
private fun OffWallBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Icon(
            HgIcons.Image,
            contentDescription = stringResource(R.string.not_on_wall),
            tint = Color.White,
            modifier = Modifier.size(18.dp).drawWithContent {
                drawContent()
                val inset = 1.dp.toPx()
                // A dark line under the white one keeps the stroke visible where it crosses the icon.
                drawLine(Color.Black, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 4.5.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 2.dp.toPx(), StrokeCap.Round)
            },
        )
    }
}
