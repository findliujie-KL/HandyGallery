package com.lmxyapp.handygallery.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.Folder
import com.lmxyapp.handygallery.FolderSort
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.PhotoSort
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Screen
import com.lmxyapp.handygallery.Source

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: GalleryViewModel, actions: PhotoActions, onRequestPermission: () -> Unit) {
    val all = vm.photosFor(Source.All)
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    val selecting = selection != null
    BackHandler(selecting) { selection = null }
    var showHidden by remember { mutableStateOf(false) }
    var choosingFolders by remember { mutableStateOf(false) }
    val hidden = vm.store.hiddenFolders

    if (choosingFolders) WallFolderDialog(
        // Screenshots never appear on the wall, so their folder is not offered here.
        folders = remember(vm.sorted) { Media.folders(vm.sorted.filter { !it.isScreenshot }, FolderSort.NameAsc) },
        hidden = vm.store.wallHidden,
        onDone = {
            vm.store.chooseWallHidden(it)
            choosingFolders = false
        },
        onDismiss = { choosingFolders = false },
    )

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null) SelectionTopBar(all, picked, vm.store.marks.keys, inTrash = false, actions) { selection = it }
            else CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = { AppIcon(Modifier.padding(start = 14.dp)) },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        @Composable
                        fun item(label: Int, onClick: () -> Unit) =
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; onClick() })
                        item(R.string.favourites) { vm.open(Screen.Collection(Source.Favorites)) }
                        if (vm.homeTab == 0) {
                            item(R.string.select_items) { selection = emptySet() }
                            item(R.string.wall_folders) { choosingFolders = true }
                        } else if (hidden.isNotEmpty()) item(if (showHidden) R.string.stop_showing_hidden else R.string.show_hidden) {
                            showHidden = !showHidden
                        }
                        item(R.string.trash) { vm.open(Screen.Collection(Source.Trash)) }
                        item(R.string.settings) { vm.open(Screen.Settings) }
                    }
                },
            )
        },
        bottomBar = {
            if (!selecting) NavigationBar {
                NavigationBarItem(
                    selected = vm.homeTab == 0,
                    onClick = { vm.homeTab = 0 },
                    icon = { Icon(FrsIcons.Image, contentDescription = null) },
                    label = { Text(stringResource(R.string.photos)) },
                )
                NavigationBarItem(
                    selected = vm.homeTab == 1,
                    onClick = { vm.homeTab = 1 },
                    icon = { Icon(FrsIcons.Folder, contentDescription = null) },
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
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                bottomPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                header = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).alpha(if (selecting) 0.4f else 1f),
                            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Mirror images of each other: icon outside, thumbnail towards the centre.
                            Shortcut(Source.Screenshots, FrsIcons.Screenshot, iconFirst = true, vm.photosFor(Source.Screenshots).firstOrNull(), !selecting, vm::open)
                            Shortcut(Source.FullResShared, FrsIcons.Prep, iconFirst = false, vm.photosFor(Source.FullResShared).firstOrNull(), !selecting, vm::open)
                        }
                    }
                },
                headingAction = {
                    SortButton(
                        stringResource(vm.store.photoSort.label),
                        enumValues<PhotoSort>().map { stringResource(it.label) },
                        Modifier.padding(end = 8.dp),
                    ) { vm.store.sortPhotos(enumValues<PhotoSort>()[it]) }
                },
            )
            else -> FolderGrid(
                folders = remember(vm.sorted, vm.store.folderSort, hidden, showHidden) {
                    Media.folders(vm.sorted, vm.store.folderSort).filter { showHidden || it.id !in hidden }
                },
                hidden = hidden,
                sortLabel = stringResource(vm.store.folderSort.label),
                onSort = { vm.store.sortFolders(enumValues<FolderSort>()[it]) },
                onOpen = { vm.open(Screen.Collection(Source.Folder(it.id, it.name))) },
                onHide = { folder, hide -> vm.store.setFolderHidden(folder.id, hide) },
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
private fun WallFolderDialog(folders: List<Folder>, hidden: Set<Long>, onDone: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
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

@Composable
private fun FolderCheck(name: String, count: Int?, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
    }
}

/** A collection's latest photo with the collection's icon beside it; the icon stands in for a text label. */
@Composable
private fun Shortcut(source: Source, icon: ImageVector, iconFirst: Boolean, cover: Photo?, enabled: Boolean, onOpen: (Screen) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled) { onOpen(Screen.Collection(source)) }.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun badge() = Icon(icon, contentDescription = source.title(), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        if (iconFirst) badge()
        val shape = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp))
        if (cover != null) Thumb(cover, shape) else Box(shape.background(MaterialTheme.colorScheme.surfaceContainer))
        if (!iconFirst) badge()
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
                Icon(FrsIcons.Sort, contentDescription = stringResource(R.string.change_sort), Modifier.padding(start = 4.dp).size(18.dp))
            }
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { i, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(i) })
                }
            }
        }
    }
}

/** Folder cards. Long-pressing one offers to hide it, or to unhide it when hidden ones are being shown. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGrid(
    folders: List<Folder>,
    hidden: Set<Long>,
    sortLabel: String,
    onSort: (Int) -> Unit,
    onOpen: (Folder) -> Unit,
    onHide: (Folder, Boolean) -> Unit,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + 16.dp,
            end = padding.calculateEndPadding(direction) + 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SortButton(sortLabel, enumValues<FolderSort>().map { stringResource(it.label) }, onPick = onSort)
        }
        items(folders, key = { it.id }) { folder ->
            val isHidden = folder.id in hidden
            var menu by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(onClick = { onOpen(folder) }, onLongClick = { menu = true })
                    .alpha(if (isHidden) 0.45f else 1f),
            ) {
                Thumb(folder.cover, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
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
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isHidden) R.string.unhide_folder else R.string.hide_folder)) },
                        onClick = { menu = false; onHide(folder, !isHidden) },
                    )
                }
            }
        }
    }
}
