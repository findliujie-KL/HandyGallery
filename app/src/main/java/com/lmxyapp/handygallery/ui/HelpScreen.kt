package com.lmxyapp.handygallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Screen
import com.lmxyapp.handygallery.Source

/** One thing the Help page explains: a moving picture of it, a title and a line or two of text. */
private class Topic(val demo: Demo, val title: Int, val text: Int, val tryIt: (() -> Unit)? = null)

private class Part(val title: Int, val topics: List<Topic>)

/**
 * How the app works, by place: each topic shows its gesture as a short looping animation, and the
 * tools can be tried straight from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(vm: GalleryViewModel, actions: PhotoActions) {
    val wall = vm.photosFor(Source.All)
    val parts = listOf(
        Part(
            R.string.help_wall,
            listOf(
                Topic(Demo.Select, R.string.help_select, R.string.help_select_text),
                Topic(Demo.Tiles, R.string.help_tiles, R.string.help_tiles_text),
            ),
        ),
        Part(
            R.string.help_viewing,
            listOf(
                Topic(Demo.Switch, R.string.help_switch, R.string.help_switch_text),
            ),
        ),
        Part(
            R.string.help_sending,
            listOf(
                Topic(Demo.Share, R.string.help_share, R.string.help_share_text),
                Topic(Demo.FrontRow, R.string.help_front_row, R.string.help_front_row_text),
            ),
        ),
        Part(
            R.string.help_tools,
            listOf(
                Topic(Demo.Sort, R.string.sort_mode, R.string.help_sort_text, { actions.sort(wall) }.takeIf { wall.isNotEmpty() }),
                Topic(Demo.Similar, R.string.similar_photos, R.string.help_similar_text) { vm.open(Screen.Similar) },
                Topic(Demo.Compare, R.string.compare_photos, R.string.help_compare_text),
            ),
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            parts.forEach { part ->
                item(key = "h" + part.title) {
                    Text(
                        stringResource(part.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(part.topics, key = { it.demo.name }) { topic -> TopicCard(topic) }
            }
        }
    }
}

@Composable
private fun TopicCard(topic: Topic) {
    Card(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        DemoView(topic.demo, Modifier.padding(12.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text(stringResource(topic.title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(topic.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            topic.tryIt?.let { go ->
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = go, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.try_it)) }
                }
            }
        }
    }
}
