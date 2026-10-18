package com.lmxyapp.handygallery.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Screen
import com.lmxyapp.handygallery.Source

/** One collection (a folder, Screenshots, FullResShared, Favourites or Trash) as a photo grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(vm: GalleryViewModel, source: Source, actions: PhotoActions) {
    val context = LocalContext.current
    val photos = vm.photosFor(source)
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    // Photos that are trashed or deleted stay selected until they are gone from the list; taking them
    // out here is what closes selection mode afterwards.
    LaunchedEffect(photos) {
        val picked = selection ?: return@LaunchedEffect
        if (picked.isEmpty()) return@LaunchedEffect
        val present = photos.mapTo(HashSet()) { it.id }
        val kept = picked.filterTo(HashSet()) { it in present }
        if (kept.size != picked.size) selection = kept.ifEmpty { null }
    }
    BackHandler(selection != null) { selection = null }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(selection == null) { if (selection == null) picking = false }

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null && picking) PickTopBar(picked.size, onCancel = { selection = null }) {
                val chosen = photos.filter { it.id in picked }
                selection = null
                actions.compare(chosen)
            }
            else if (picked != null) SelectionTopBar(photos, picked, vm.store.marks.keys, vm.store.favorites, inTrash = source == Source.Trash, actions, removing = vm.leaving.isNotEmpty()) { selection = it }
            else TopAppBar(
                title = {
                    Column {
                        Text(source.title())
                        if (photos.isNotEmpty()) Text(
                            stringResource(R.string.items_size, photos.size, Formatter.formatShortFileSize(context, photos.sumOf { it.size })),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // The tools work on this page's photos: sorting them, or picking among them to compare.
                    // Not in the trash, and Front Row, a list rather than a place to tidy, isn't sorted.
                    if (source != Source.Trash) ToolsButton(
                        onSort = { actions.sort(photos) }.takeIf { source != Source.FullResShared && photos.isNotEmpty() },
                        onSimilar = { vm.open(Screen.Similar) },
                        onCompare = {
                            picking = true
                            selection = emptySet()
                        },
                    )
                },
            )
        },
    ) { padding ->
        if (photos.isEmpty()) EmptyHint(
            stringResource(
                when (source) {
                    Source.FullResShared -> R.string.empty_shared
                    Source.Favorites -> R.string.empty_favourites
                    Source.Trash -> R.string.empty_trash
                    else -> R.string.empty_photos
                },
            ),
            Modifier.padding(padding),
        )
        else PhotoGrid(
            photos = photos,
            // These two have their own order (newest marker / as trashed), so month headers would mislead.
            sort = if (source == Source.FullResShared || source == Source.Trash) null else vm.store.photoSort,
            selection = selection,
            onSelectionChange = { selection = it },
            onOpen = { vm.open(Screen.Viewer(source, it.id)) },
            zoom = vm.store.gridZoom,
            onZoom = vm.store::zoomGrid,
            focusId = vm.focus,
            // On the Front Row and Favourites pages every photo would carry that page's badge, which says nothing.
            frontRow = if (source == Source.FullResShared || source == Source.Trash) emptySet() else vm.store.marks.keys,
            favorites = if (source == Source.Favorites || source == Source.Trash) emptySet() else vm.store.favorites,
            leaving = vm.leaving,
            showExpiry = source == Source.Trash,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            bottomPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        )
    }
}
