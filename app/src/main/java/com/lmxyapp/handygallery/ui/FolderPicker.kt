package com.lmxyapp.handygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.Folder
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.R
import kotlinx.coroutines.launch

/** A folder photos can be moved or copied into, with its path relative to the phone's storage. */
class Destination(val folder: Folder, val dir: String)

/** The folders among [folders] that the system lets an app put pictures into. */
fun destinations(folders: List<Folder>): List<Destination> = folders.mapNotNull { folder ->
    Media.relativeDir(folder.cover)?.takeIf(Media::canHoldImages)?.let { Destination(folder, it) }
}

/**
 * Asks where to put photos, as a sheet of folder tiles that slides up from the bottom: one of
 * [choices], or a new folder the user names. [onPick] gets the chosen folder's relative path once the
 * sheet has slid away again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(title: String, choices: List<Destination>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    fun pick(dir: String) {
        scope.launch { sheet.hide() }.invokeOnCompletion { onPick(dir) }
    }

    if (naming) NewFolderDialog(
        onCreate = {
            naming = false
            pick("Pictures/$it/")
        },
        onDismiss = { naming = false },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "new") {
                FolderTile(stringResource(R.string.new_folder), onClick = { naming = true }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            items(choices, key = { it.folder.id }) { choice ->
                FolderTile(choice.folder.name, onClick = { pick(choice.dir) }) { Thumb(choice.folder.cover, Modifier.fillMaxSize()) }
            }
        }
        Text(
            stringResource(R.string.folder_picker_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        )
    }
}

/** A folder to tap: its picture with its name underneath. */
@Composable
private fun FolderTile(name: String, onClick: () -> Unit, picture: @Composable () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 2.dp).size(64.dp).clip(RoundedCornerShape(12.dp))) { picture() }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
        )
    }
}

/** Asks for the name of a folder to create inside Pictures; [onCreate] gets the name, already checked. */
@Composable
fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = name.trim()
    val valid = clean.isNotEmpty() && clean.none { it in "\\/:*?\"<>|" } && clean != "." && clean != ".."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.folder_name)) })
        },
        confirmButton = { TextButton(onClick = { onCreate(clean) }, enabled = valid) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
