package com.findliujie.fullresshare.ui

import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.findliujie.fullresshare.Folder
import com.findliujie.fullresshare.FolderSort
import com.findliujie.fullresshare.GalleryViewModel
import com.findliujie.fullresshare.Media
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.PhotoSort
import com.findliujie.fullresshare.Screen
import com.findliujie.fullresshare.Source

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    actions: PhotoActions,
    onRequestPermission: () -> Unit,
    onManageMedia: () -> Unit,
) {
    val context = LocalContext.current
    val all = vm.photosFor(Source.All)
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    val selecting = selection != null
    BackHandler(selecting) { selection = null }

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null) SelectionTopBar(all, picked, inTrash = false, actions) { selection = it }
            else CenterAlignedTopAppBar(
                title = { Text("FullResShare") },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Favourites") },
                            onClick = { menu = false; vm.open(Screen.Collection(Source.Favorites)) },
                        )
                        if (vm.homeTab == 0) DropdownMenuItem(
                            text = { Text("Select items") },
                            onClick = { menu = false; selection = emptySet() },
                        )
                        DropdownMenuItem(
                            text = { Text("Trash") },
                            onClick = { menu = false; vm.open(Screen.Collection(Source.Trash)) },
                        )
                        if (Build.VERSION.SDK_INT >= 31) DropdownMenuItem(
                            text = { Text(if (MediaStore.canManageMedia(context)) "System prompts: skipped" else "Skip system prompts…") },
                            onClick = { menu = false; onManageMedia() },
                        )
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
                    label = { Text("Photos") },
                )
                NavigationBarItem(
                    selected = vm.homeTab == 1,
                    onClick = { vm.homeTab = 1 },
                    icon = { Icon(FrsIcons.Folder, contentDescription = null) },
                    label = { Text("Folders") },
                )
            }
        },
    ) { padding ->
        val top = Modifier.padding(top = padding.calculateTopPadding())
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        when {
            !vm.canRead -> EmptyHint("Photo access is needed to show your gallery", Modifier.padding(padding)) {
                Button(onClick = onRequestPermission) { Text("Grant access") }
            }
            vm.homeTab == 0 -> PhotoGrid(
                photos = all,
                sort = vm.store.photoSort,
                selection = selection,
                onSelectionChange = { selection = it },
                onOpen = { vm.open(Screen.Viewer(Source.All, it.id)) },
                zoom = vm.store.gridZoom,
                onZoom = vm.store::zoomGrid,
                modifier = top,
                bottomPadding = bottom,
                header = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).alpha(if (selecting) 0.4f else 1f),
                            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        ) {
                            Shortcut(Source.Screenshots, vm.photosFor(Source.Screenshots).firstOrNull(), !selecting, vm::open)
                            Shortcut(Source.FullResShared, vm.photosFor(Source.FullResShared).firstOrNull(), !selecting, vm::open)
                        }
                        SortButton(vm.store.photoSort.label, enumValues<PhotoSort>().map { it.label }) {
                            vm.store.sortPhotos(enumValues<PhotoSort>()[it])
                        }
                    }
                },
            )
            else -> FolderGrid(
                folders = remember(vm.sorted, vm.store.folderSort) { Media.folders(vm.sorted, vm.store.folderSort) },
                sortLabel = vm.store.folderSort.label,
                onSort = { vm.store.sortFolders(enumValues<FolderSort>()[it]) },
                onOpen = { vm.open(Screen.Collection(Source.Folder(it.id, it.name))) },
                padding = padding,
            )
        }
    }
}

@Composable
private fun Shortcut(source: Source, cover: Photo?, enabled: Boolean, onOpen: (Screen) -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled) { onOpen(Screen.Collection(source)) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp))
        if (cover != null) Thumb(cover, shape)
        else Box(shape.then(Modifier.clip(RoundedCornerShape(16.dp)))) {
            Box(Modifier.fillMaxSize().alpha(0.5f), contentAlignment = Alignment.Center) {
                Icon(FrsIcons.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(source.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Right-aligned "current sort ↕" control that opens the list of choices. */
@Composable
private fun SortButton(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
        // The menu anchors to its parent, so it needs a box the size of the button, not the full-width row.
        Box {
            TextButton(onClick = { open = true }) {
                Text(current)
                Icon(FrsIcons.Sort, contentDescription = "Change sort order", Modifier.padding(start = 4.dp).size(18.dp))
            }
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { i, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(i) })
                }
            }
        }
    }
}

@Composable
private fun FolderGrid(
    folders: List<Folder>,
    sortLabel: String,
    onSort: (Int) -> Unit,
    onOpen: (Folder) -> Unit,
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
            SortButton(sortLabel, enumValues<FolderSort>().map { it.label }, onSort)
        }
        items(folders, key = { it.id }) { folder ->
            Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpen(folder) }) {
                Thumb(folder.cover, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                )
                Text(
                    "${folder.count} items · ${Formatter.formatShortFileSize(context, folder.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
        }
    }
}
