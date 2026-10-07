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
    BackHandler(selection != null) { selection = null }

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null) SelectionTopBar(photos, picked, vm.store.marks.keys, vm.store.favorites, inTrash = source == Source.Trash, actions) { selection = it }
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
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.select_items)) },
                            onClick = { menu = false; selection = emptySet() },
                        )
                        // Not for the trash, and not for Front Row, which is a list rather than a place to tidy.
                        if (source != Source.Trash && source != Source.FullResShared && photos.isNotEmpty()) DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_mode)) },
                            onClick = { menu = false; actions.sort(photos) },
                        )
                    }
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
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            bottomPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        )
    }
}
