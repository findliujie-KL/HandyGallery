package com.lmxyapp.handygallery.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.PhotoSort
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Similar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Groups of photos that look alike, for clearing out bursts and duplicate copies. In each group the
 * photos to keep are ticked, starting with the one most worth keeping; tapping a photo changes its
 * tick, and the unticked ones can then be trashed. Holding a photo, or Compare, opens the group full
 * screen to look closely before deciding. Groups the user chooses to keep all of are settled: they
 * move to the end of the list and stay there on later visits.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SimilarScreen(vm: GalleryViewModel, actions: PhotoActions) {
    val context = LocalContext.current
    val app = context.applicationContext
    val photos = vm.sorted

    // Analysing is only ever started by the user. Fingerprints from earlier runs are kept, so opening
    // this screen shows the groups already known straight away, and a run only looks at photos that
    // are new since the last one.
    val scope = rememberCoroutineScope()
    var hashes by remember { mutableStateOf<Map<String, Long>?>(null) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var todo by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { hashes = withContext(Dispatchers.IO) { Similar.load(app) } }
    val pending = hashes?.let { known -> photos.count { it.cacheKey !in known } } ?: 0

    fun analyse() {
        if (running) return
        running = true
        scope.launch {
            hashes = withContext(Dispatchers.IO) {
                val known = HashMap(hashes.orEmpty())
                val missing = photos.filter { it.cacheKey !in known }
                todo = missing.size
                done = 0
                missing.forEachIndexed { i, photo ->
                    Similar.fingerprint(app, photo)?.let { known[photo.cacheKey] = it }
                    if (i % 8 == 7) done = i + 1
                }
                // Forget photos that are gone, so the file doesn't grow for ever.
                known.keys.retainAll(photos.mapTo(HashSet()) { it.cacheKey })
                Similar.save(app, known)
                known
            }
            running = false
        }
    }

    val groups by produceState<List<List<Photo>>?>(null, photos, hashes) {
        val ready = hashes
        value = if (ready == null) null else withContext(Dispatchers.Default) { Similar.groups(photos, ready) }
    }

    // Which photos the user has ticked or unticked by hand. Without an entry, a group's first photo
    // (the suggested one) is kept and the rest are not.
    val choices = remember { mutableStateMapOf<Long, Boolean>() }
    fun kept(photo: Photo, group: List<Photo>) = choices[photo.id] ?: (photo.id == group.first().id)
    fun toggle(photo: Photo, group: List<Photo>) {
        choices[photo.id] = !kept(photo, group)
    }

    // Settled groups (every photo in them confirmed as kept) go after the ones still to decide.
    val settledIds = vm.store.similarKept
    fun settled(group: List<Photo>) = group.all { it.id in settledIds }
    fun keepAll(group: List<Photo>) {
        group.forEach { choices.remove(it.id) }
        vm.store.keepSimilar(group.map { it.id }, true)
    }

    /** The group open full screen for a close look, and which of its photos to start on. */
    var comparing by remember { mutableStateOf<Pair<List<Photo>, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.similar_photos))
                        groups?.filter { !settled(it) }?.takeIf { it.isNotEmpty() }?.let { found ->
                            val spare = found.sumOf { group -> group.count { !kept(it, group) } }
                            val bytes = found.sumOf { group -> group.filter { !kept(it, group) }.sumOf { it.size } }
                            Text(
                                stringResource(R.string.similar_summary, found.size, spare, Formatter.formatShortFileSize(context, bytes)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val found = groups
        when {
            // Never analysed: say what this is and wait to be asked.
            !running && hashes?.isEmpty() == true -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.similar_intro, photos.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = ::analyse) { Text(stringResource(R.string.similar_analyse)) }
            }
            running || found == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.similar_scanning, done, todo), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { if (todo > 0) done.toFloat() / todo else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
            found.isEmpty() && pending == 0 -> EmptyHint(stringResource(R.string.similar_none), Modifier.padding(padding))
            else -> LazyColumn(contentPadding = padding) {
                // Photos added since the last run aren't in these results until the user asks again.
                if (pending > 0) item(key = "pending") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.similar_pending, pending),
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = ::analyse) { Text(stringResource(R.string.similar_analyse)) }
                    }
                }
                item(key = "hint") {
                    Text(
                        stringResource(R.string.similar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val open = found.filter { !settled(it) }
                val closed = found.filter { settled(it) }
                items(open, key = { it.first().id }) { group ->
                    val going = group.filter { !kept(it, group) }
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(group.first().time(PhotoSort.Taken))) + " · " + group.first().bucketName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(group, key = { _, photo -> photo.id }) { index, photo ->
                                Choice(
                                    photo,
                                    keep = kept(photo, group),
                                    modifier = Modifier.size(112.dp).combinedClickable(
                                        onClick = { toggle(photo, group) },
                                        onLongClick = { comparing = group to index },
                                    ),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { keepAll(group) }) {
                                Text(stringResource(if (group.size == 2) R.string.similar_keep_both else R.string.similar_keep_all))
                            }
                            TextButton(onClick = { comparing = group to 0 }) { Text(stringResource(R.string.similar_compare)) }
                            TextButton(onClick = { actions.trash(going) }, enabled = going.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.similar_trash_n, going.size),
                                    color = if (going.isEmpty()) Color.Unspecified else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                    }
                }
                // Groups already confirmed as "keep all": out of the way, but still here to change one's mind.
                if (closed.isNotEmpty()) {
                    item(key = "closed") {
                        Text(
                            stringResource(R.string.similar_kept_heading, closed.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
                        )
                    }
                    items(closed, key = { "k" + it.first().id }) { group ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).alpha(0.6f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                group.take(4).forEach { Thumb(it, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) }
                                if (group.size > 4) Text("+${group.size - 4}", Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
                            }
                            TextButton(onClick = { vm.store.keepSimilar(group.map { it.id }, false) }) { Text(stringResource(R.string.undo)) }
                        }
                    }
                }
            }
        }
    }

    comparing?.let { (group, start) ->
        Compare(
            group = group,
            start = start,
            kept = { kept(it, group) },
            onToggle = { toggle(it, group) },
            onTrash = {
                comparing = null
                actions.trash(group.filter { !kept(it, group) })
            },
            onKeepAll = {
                comparing = null
                keepAll(group)
            },
            onClose = { comparing = null },
        )
    }
}

/**
 * Photos the user picked themselves, compared full screen. Unlike similar groups nothing is
 * suggested: every photo starts as kept, and only the ones marked Remove are trashed.
 */
@Composable
fun CompareScreen(vm: GalleryViewModel, ids: List<Long>, actions: PhotoActions) {
    val byId = vm.sorted.associateBy { it.id }
    val group = ids.mapNotNull { byId[it] }
    if (group.size < 2) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val removing = remember { mutableStateMapOf<Long, Boolean>() }
    fun kept(photo: Photo) = removing[photo.id] != true
    Compare(
        group = group,
        start = 0,
        kept = ::kept,
        onToggle = { removing[it.id] = kept(it) },
        onTrash = {
            val going = group.filter { !kept(it) }
            vm.back()
            actions.trash(going)
        },
        onKeepAll = vm::back,
        onClose = vm::back,
        keepLabel = R.string.compare_done,
    )
}

/** The colours and letters that tie each stacked pane to its photo in the strip. */
private val PaneA = Color(0xFF64B5F6)
private val PaneB = Color(0xFFFFB74D)

/**
 * A photo in a similar group: ticked if it is being kept, dimmed with a bin mark if it is to go.
 * [outline] rings it, and [tag] puts a letter on it in the same colour, to show it is the one on
 * screen (or which pane it is in).
 */
@Composable
private fun Choice(photo: Photo, keep: Boolean, modifier: Modifier, outline: Color? = null, tag: String? = null) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (outline != null) Modifier.border(2.5.dp, outline, RoundedCornerShape(10.dp)) else Modifier),
    ) {
        Thumb(photo, Modifier.fillMaxSize().alpha(if (keep) 1f else 0.4f))
        if (tag != null && outline != null) PaneTag(tag, outline, Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp))
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (keep) primary else Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (keep) Icons.Filled.Check else Icons.Filled.Delete,
                contentDescription = stringResource(if (keep) R.string.keep else R.string.similar_remove),
                tint = if (keep) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * One group full screen, to look closely before deciding. Stacked, two of its photos sit one above
 * the other and zoom and pan together, so the same spot can be compared in both; one at a time, the
 * photos are swiped through. Either way each photo can be set to stay or go.
 */
@Composable
private fun Compare(
    group: List<Photo>,
    start: Int,
    kept: (Photo) -> Boolean,
    onToggle: (Photo) -> Unit,
    onTrash: () -> Unit,
    onKeepAll: () -> Unit,
    onClose: () -> Unit,
    keepLabel: Int? = null,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val going = group.count { !kept(it) }
    var stacked by remember { mutableStateOf(true) }
    // On a wide screen (the phone on its side, or unfolded) the two panes sit side by side instead.
    val config = LocalConfiguration.current
    val wide = config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 600
    val pager = rememberPagerState(initialPage = start.coerceIn(group.indices)) { group.size }
    // Stacked: which photos are in the two panes, and which pane a tap in the strip fills.
    val zoom = remember { ZoomState() }
    var top by remember { mutableIntStateOf(start.coerceIn(group.indices)) }
    var bottom by remember { mutableIntStateOf(if (start == 0) 1 else 0) }
    var activePane by remember { mutableIntStateOf(1) }
    fun describe(photo: Photo) = listOfNotNull(
        if (photo.width > 0) "${photo.width} × ${photo.height}" else null,
        Formatter.formatShortFileSize(context, photo.size),
        photo.bucketName,
    ).joinToString(" · ")

    DarkTheme {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back)) }
                    Text(
                        if (stacked) "" else "${pager.currentPage + 1} / ${group.size}",
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { stacked = !stacked }) {
                        Text(stringResource(if (stacked) R.string.similar_one_by_one else if (wide) R.string.similar_side_by_side else R.string.similar_stacked))
                    }
                    TextButton(onClick = onTrash, enabled = going > 0) {
                        Text(stringResource(R.string.similar_trash_n, going), color = if (going > 0) MaterialTheme.colorScheme.error else Color.Gray)
                    }
                }

                if (stacked) {
                    val several = group.size > 2
                    // With more than two alike the panes are lettered and coloured, and so are their photos
                    // in the strip, so it is clear which pane is which.
                    val first = @Composable { modifier: Modifier ->
                        Pane(group[top], zoom, kept(group[top]), describe(group[top]), "A".takeIf { several }, PaneA, several && activePane == 0, modifier, { activePane = 0 }) { keep ->
                            if (kept(group[top]) != keep) onToggle(group[top])
                        }
                    }
                    val second = @Composable { modifier: Modifier ->
                        Pane(group[bottom], zoom, kept(group[bottom]), describe(group[bottom]), "B".takeIf { several }, PaneB, several && activePane == 1, modifier, { activePane = 1 }) { keep ->
                            if (kept(group[bottom]) != keep) onToggle(group[bottom])
                        }
                    }
                    if (wide) Row(Modifier.weight(1f).fillMaxWidth()) {
                        first(Modifier.weight(1f).fillMaxHeight())
                        Box(Modifier.width(2.dp))
                        second(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        first(Modifier.weight(1f).fillMaxWidth())
                        Box(Modifier.height(2.dp))
                        second(Modifier.weight(1f).fillMaxWidth())
                    }
                    // The strip chooses which photo goes in the pane that is outlined.
                    if (several) Text(
                        stringResource(R.string.similar_pick_for, if (activePane == 0) "A" else "B"),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (several) LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        itemsIndexed(group, key = { _, photo -> photo.id }) { index, photo ->
                            Choice(
                                photo,
                                keep = kept(photo),
                                outline = if (index == top) PaneA else if (index == bottom) PaneB else null,
                                tag = if (index == top) "A" else if (index == bottom) "B" else null,
                                modifier = Modifier.size(56.dp).clickable {
                                    // Asking for the photo already in the other pane swaps the two.
                                    if (activePane == 0) {
                                        if (index == bottom) bottom = top
                                        top = index
                                    } else {
                                        if (index == top) top = bottom
                                        bottom = index
                                    }
                                },
                            )
                        }
                    }
                } else {
                    val current = group[pager.currentPage.coerceIn(group.indices)]
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { group[it].id }) { page ->
                        ZoomableImage(
                            group[page],
                            active = page == pager.currentPage,
                            unzoom = 0,
                            onZoomed = {},
                            onTap = {},
                            onVerticalDrag = {},
                            onVerticalDragEnd = {},
                        )
                    }
                    Text(describe(current), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    Text(
                        current.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        itemsIndexed(group, key = { _, photo -> photo.id }) { index, photo ->
                            Choice(
                                photo,
                                keep = kept(photo),
                                outline = if (index == pager.currentPage) Color.White else null,
                                modifier = Modifier.size(64.dp).clickable { scope.launch { pager.animateScrollToPage(index) } },
                            )
                        }
                    }
                    // The decision for the photo on show: one of the two is always lit.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val keeping = kept(current)
                        Decide(Icons.Filled.Delete, stringResource(R.string.similar_remove), !keeping, Color(0xFFD93025), Modifier.weight(1f)) {
                            if (keeping) onToggle(current)
                        }
                        Decide(Icons.Filled.Check, stringResource(R.string.keep), keeping, Color(0xFF1E8E3E), Modifier.weight(1f)) {
                            if (!keeping) onToggle(current)
                        }
                    }
                }
                TextButton(onClick = onKeepAll, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)) {
                    Text(stringResource(keepLabel ?: if (group.size == 2) R.string.similar_keep_both else R.string.similar_keep_all))
                }
            }
        }
    }
}

/**
 * One of the two photos compared together, stacked or side by side. It shares [zoom] with the other and shows what sets it apart in a
 * corner. Its decision is a pair of buttons, Remove and Keep, with the one in force lit: tapping a
 * button always does what that button says. With [tag] it is lettered in [accent], and [selected]
 * outlines it as the pane the strip will fill.
 */
@Composable
private fun Pane(
    photo: Photo,
    zoom: ZoomState,
    keep: Boolean,
    details: String,
    tag: String?,
    accent: Color,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onDecide: (Boolean) -> Unit,
) {
    Box(
        modifier
            .clipToBounds()
            .then(if (selected) Modifier.border(2.5.dp, accent) else Modifier),
    ) {
        key(photo.id) {
            ZoomableImage(photo, active = true, unzoom = 0, onZoomed = {}, onTap = onSelect, onVerticalDrag = {}, onVerticalDragEnd = {}, shared = zoom)
        }
        Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tag != null) PaneTag(tag, accent, Modifier.padding(end = 6.dp).size(24.dp))
            Text(
                details,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.65f))) {
            Segment(Icons.Filled.Delete, stringResource(R.string.similar_remove), !keep, Color(0xFFD93025)) { onDecide(false) }
            Segment(Icons.Filled.Check, stringResource(R.string.keep), keep, Color(0xFF1E8E3E)) { onDecide(true) }
        }
    }
}

/** Half of a pane's Remove/Keep pair: filled with [color] when it is the choice in force, plain otherwise. */
@Composable
private fun Segment(icon: ImageVector, label: String, chosen: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (chosen) color else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (chosen) 1f else 0.7f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The letter that names a pane, on a disc of the pane's colour. */
@Composable
private fun PaneTag(tag: String, color: Color, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(tag, color = Color.Black, style = MaterialTheme.typography.labelSmall)
    }
}

/** One of the two decision buttons; filled with [color] when it is the one in force. */
@Composable
private fun Decide(icon: ImageVector, label: String, chosen: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (chosen) color else Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
