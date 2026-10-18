package com.lmxyapp.handygallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.R

/**
 * The toolbox button beside the menu: Sort photos, Similar photos and Compare photos, each with its
 * icon and a line on what it is for. [onSort] is null where there is nothing to sort.
 */
@Composable
fun ToolsButton(onSort: (() -> Unit)?, onSimilar: () -> Unit, onCompare: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) { Icon(FrsIcons.Toolbox, contentDescription = stringResource(R.string.tools)) }
        DropdownMenu(open, { open = false }) {
            @Composable
            fun tool(icon: ImageVector, title: Int, detail: Int, onClick: () -> Unit) = DropdownMenuItem(
                leadingIcon = { Icon(icon, contentDescription = null) },
                text = {
                    Column(Modifier.widthIn(max = 240.dp).padding(vertical = 6.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { open = false; onClick() },
            )
            if (onSort != null) tool(FrsIcons.SortCards, R.string.sort_mode, R.string.tool_sort_desc, onSort)
            tool(FrsIcons.Similar, R.string.similar_photos, R.string.tool_similar_desc, onSimilar)
            tool(FrsIcons.Compare, R.string.compare_photos, R.string.tool_compare_desc, onCompare)
        }
    }
}

/** The main menu: Help, Trash and Settings, and nothing else. */
@Composable
fun MainMenu(onHelp: () -> Unit, onTrash: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(open, { open = false }) {
            @Composable
            fun item(icon: ImageVector, label: Int, onClick: () -> Unit) = DropdownMenuItem(
                leadingIcon = { Icon(icon, contentDescription = null) },
                text = { Text(stringResource(label)) },
                onClick = { open = false; onClick() },
            )
            item(FrsIcons.Help, R.string.help, onHelp)
            item(Icons.Filled.Delete, R.string.trash, onTrash)
            item(Icons.Filled.Settings, R.string.settings, onSettings)
        }
    }
}

/** The top bar while picking photos to compare: how many are picked, and Compare once there are two. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickTopBar(count: Int, onCancel: () -> Unit, onCompare: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.pick_to_compare), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.picked_n, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_selection)) }
        },
        actions = {
            Button(onClick = onCompare, enabled = count >= 2, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.compare))
            }
        },
    )
}
