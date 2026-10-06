package com.lmxyapp.handygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** A folder photos can be moved or copied into, with its path relative to the phone's storage. */
class Destination(val folder: Folder, val dir: String)

/** The folders among [folders] that the system lets an app put pictures into. */
fun destinations(folders: List<Folder>): List<Destination> = folders.mapNotNull { folder ->
    Media.relativeDir(folder.cover)?.takeIf(Media::canHoldImages)?.let { Destination(folder, it) }
}

/**
 * Asks where to put photos: one of [choices], or a new folder the user names. [onPick] gets the chosen
 * folder's relative path.
 */
@Composable
fun FolderPickerDialog(title: String, choices: List<Destination>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    // Characters a folder name can't contain, and names that would climb out of the Pictures folder.
    val clean = name.trim()
    val valid = clean.isNotEmpty() && clean.none { it in "\\/:*?\"<>|" } && clean != "." && clean != ".."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (naming) stringResource(R.string.new_folder) else title) },
        text = {
            if (naming) OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) },
            ) else Column {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    item {
                        PickRow(stringResource(R.string.new_folder), null, onClick = { naming = true }) {
                            Box(
                                Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                    }
                    items(choices, key = { it.folder.id }) { choice ->
                        PickRow(choice.folder.name, choice.dir.trimEnd('/'), onClick = { onPick(choice.dir) }) {
                            Thumb(choice.folder.cover, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        }
                    }
                }
                Text(
                    stringResource(R.string.folder_picker_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            if (naming) TextButton(onClick = { onPick("Pictures/$clean/") }, enabled = valid) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = { if (naming) naming = false else onDismiss() }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PickRow(title: String, detail: String?, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}
