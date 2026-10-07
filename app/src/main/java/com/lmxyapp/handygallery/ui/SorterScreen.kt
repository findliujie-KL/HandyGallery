package com.lmxyapp.handygallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lmxyapp.handygallery.GalleryViewModel
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A folder offered in the tray. [cover] is null for one that will only exist once a photo is moved into it. */
private class Target(val dir: String, val name: String, val cover: Photo?)

/** One thing the user did, so it can be undone: which photo, on which page, and what it was set to before. */
private class Step(val page: Int, val id: Long, val before: String?)

/**
 * Sort mode: goes through the photos of the session held in [Store] one at a time, as a single card.
 * Tapping a folder in the tray marks the photo to move there, swiping up marks it rejected, swiping
 * left keeps it as it is, and swiping right goes back one. Each action plays out before the next photo
 * appears: a swipe uncovers a coloured bar naming it, and a filed photo shrinks into its folder's tile.
 * Nothing touches the files until the user confirms on the review page; [onApply] then gets the moves
 * and the rejects, and a callback to run once they are done.
 */
@Composable
fun SorterScreen(vm: GalleryViewModel, onApply: (List<Pair<Photo, String>>, List<Photo>, () -> Unit) -> Unit) {
    val store = vm.store
    val byId = remember(vm.sorted) { vm.sorted.associateBy { it.id } }
    val photos = remember(store.sortIds, byId) { store.sortIds.mapNotNull(byId::get) }

    fun leave() {
        store.clearSort()
        vm.back()
    }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { leave() }
        return
    }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var at by remember { mutableIntStateOf(store.sortAt.coerceIn(photos.indices)) }
    val page = at.coerceIn(photos.indices)
    val current = photos[page]
    LaunchedEffect(page) { store.moveSortTo(page) }

    val decisions = store.sortDecisions
    var reviewing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Step>() }
    /** Folders named this session, in the order they were made. */
    val created = remember { mutableStateListOf<String>() }

    // The card: where the finger has dragged it, how far it has shrunk towards a folder tile, and how
    // far the next photo has faded in. [busy] is set while an action plays out, so a second one can't start.
    val card = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val fly = remember { Animatable(0f) }
    var flyTo by remember { mutableStateOf(Offset.Zero) }
    val appear = remember { Animatable(1f) }
    var busy by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf(IntSize(1, 1)) }
    var areaCentre by remember { mutableStateOf(Offset.Zero) }
    val tileCentres = remember { mutableStateMapOf<String, Offset>() }
    var pulsed by remember { mutableStateOf<String?>(null) }
    val pulse = remember { Animatable(1f) }
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }

    // The gesture callbacks below are handed over as function references, which Compose keeps from the
    // first composition. So anything that changes is read at the moment of the call, never captured.
    val latestPhotos by rememberUpdatedState(photos)
    fun nowPage() = at.coerceIn(latestPhotos.indices)

    /** Brings the photo at [index] in, or opens the review page when the last one has been passed. */
    suspend fun show(index: Int) {
        if (index > latestPhotos.lastIndex) reviewing = true else {
            appear.snapTo(0f)
            at = index.coerceAtLeast(0)
        }
        card.snapTo(Offset.Zero)
        fly.snapTo(0f)
        appear.animateTo(1f, tween(160))
    }

    fun record(photo: Photo, decision: String?) {
        history += Step(nowPage(), photo.id, store.sortDecisions[photo.id])
        store.decideSort(photo.id, decision)
    }

    /** Finishes a swipe that went far enough: the card leaves, the bar it uncovered holds a moment, then the next photo. */
    fun swipeOut(to: Offset, next: Int, act: (Photo) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            card.animateTo(to, tween(170))
            act(latestPhotos[nowPage()])
            delay(150)
            show(next)
            busy = false
        }
    }

    fun file(dir: String) {
        if (busy) return
        busy = true
        scope.launch {
            val photo = latestPhotos[nowPage()]
            // Towards the folder's tile; straight down if the tile isn't on screen (a folder only just named).
            flyTo = tileCentres[dir]?.let { it - areaCentre } ?: Offset(0f, area.height.toFloat())
            fly.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
            // Its own folder means "leave it where it is", which also withdraws any earlier decision.
            record(photo, if (dir == Media.relativeDir(photo)) null else dir)
            pulsed = dir
            pulse.animateTo(1.16f, tween(90))
            pulse.animateTo(1f, tween(130))
            show(nowPage() + 1)
            busy = false
        }
    }

    fun undo() {
        if (busy) return
        val step = history.removeLastOrNull() ?: return
        store.decideSort(step.id, step.before)
        scope.launch { show(step.page) }
    }

    fun dragUp(delta: Float) {
        if (!busy) scope.launch { card.snapTo(Offset(0f, (card.value.y + delta).coerceAtMost(0f))) }
    }
    fun dragSideways(delta: Float) {
        // There is nothing before the first photo, so it won't go to the right.
        if (!busy) scope.launch { card.snapTo(Offset((card.value.x + delta).let { if (nowPage() == 0) it.coerceAtMost(0f) else it }, 0f)) }
    }
    fun release() {
        if (busy) return
        val offset = card.value
        val here = nowPage()
        when {
            offset.y < -threshold -> swipeOut(Offset(0f, -area.height.toFloat()), here + 1) { record(it, Store.SORT_REJECT) }
            // Keeping changes nothing, but it is a step all the same, so undo comes back to this photo.
            offset.x < -threshold -> swipeOut(Offset(-area.width.toFloat(), 0f), here + 1) { history += Step(here, it.id, store.sortDecisions[it.id]) }
            offset.x > threshold -> swipeOut(Offset(area.width.toFloat(), 0f), here - 1) {}
            else -> scope.launch { card.animateTo(Offset.Zero) }
        }
    }

    // A short buzz the moment a drag has gone far enough that letting go will act.
    val armed by remember { derivedStateOf { card.value.y < -threshold || abs(card.value.x) > threshold } }
    LaunchedEffect(armed) { if (armed && !busy) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val existing = remember(vm.sorted, store.folderSort, store.folderOrder) {
        destinations(Media.folders(vm.sorted, store.folderSort, store.folderOrder))
    }
    // The tray keeps one order for the whole session: folders named here first, as they were made, then
    // the Folders tab's order. Nothing moves when a folder is used.
    val targets = remember(existing, decisions, created.toList()) {
        val known = existing.map { Target(it.dir, it.folder.name, it.folder.cover) }
        val knownDirs = known.map { it.dir }.toSet()
        val fresh = (created + decisions.values.filter { it != Store.SORT_REJECT }).distinct().filter { it !in knownDirs }
            .map { Target(it, it.trimEnd('/').substringAfterLast('/'), null) }
        fresh + known
    }
    val counts = remember(decisions) { decisions.values.groupingBy { it }.eachCount() }
    fun nameOf(dir: String) = targets.firstOrNull { it.dir == dir }?.name ?: dir.trimEnd('/').substringAfterLast('/')

    // Back never throws decisions away: with some made it goes to the review page, which is where to leave from.
    BackHandler {
        if (reviewing) reviewing = false else if (decisions.isEmpty()) leave() else reviewing = true
    }

    if (naming) NewFolderDialog(
        onCreate = { name ->
            naming = false
            val dir = "Pictures/$name/"
            if (dir !in created) created += dir
            file(dir)
        },
        onDismiss = { naming = false },
    )

    DarkTheme {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            if (reviewing) Review(
                decisions = decisions,
                photo = byId::get,
                nameOf = { nameOf(it) },
                onKeep = { id -> store.decideSort(id, null) },
                onBack = { reviewing = false },
                onDiscard = ::leave,
                onLater = vm::back,
                onApply = {
                    val moves = decisions.filterValues { it != Store.SORT_REJECT }.mapNotNull { (id, dir) -> byId[id]?.let { it to dir } }
                    val rejects = decisions.filterValues { it == Store.SORT_REJECT }.keys.mapNotNull(byId::get)
                    onApply(moves, rejects, ::leave)
                },
            ) else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (decisions.isEmpty()) leave() else reviewing = true }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                    }
                    Text("${page + 1} / ${photos.size}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = ::undo, enabled = history.isNotEmpty()) {
                        Icon(FrsIcons.Undo, contentDescription = stringResource(R.string.undo))
                    }
                    TextButton(onClick = { reviewing = true }) { Text(stringResource(R.string.done)) }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds().onGloballyPositioned {
                        area = it.size
                        areaCentre = it.boundsInRoot().center
                    },
                ) {
                    // The bar a swipe uncovers: it is exactly as big as the gap the card has left behind.
                    val offset = card.value
                    val density = LocalDensity.current
                    if (offset.y < 0f) ActionBar(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(with(density) { (-offset.y).toDp() }),
                        Color(0xFFD93025), Icons.Filled.Delete, stringResource(R.string.sort_reject), armed,
                    )
                    if (offset.x < 0f) ActionBar(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(with(density) { (-offset.x).toDp() }),
                        Color(0xFF1E8E3E), Icons.Filled.Check, stringResource(R.string.keep), armed,
                    )
                    if (offset.x > 0f) ActionBar(
                        Modifier.align(Alignment.CenterStart).fillMaxHeight().width(with(density) { offset.x.toDp() }),
                        Color(0xFF5F6368), Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sort_previous), armed,
                    )

                    // The photo after this one, composed out of sight so it is already loaded when its turn comes.
                    photos.getOrNull(page + 1)?.let { upcoming ->
                        key(upcoming.id) {
                            Box(Modifier.fillMaxSize().alpha(0f)) {
                                ZoomableImage(upcoming, active = false, unzoom = 0, onZoomed = {}, onTap = {}, onVerticalDrag = {}, onVerticalDragEnd = {})
                            }
                        }
                    }

                    key(current.id) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val shrink = fly.value
                                translationX = card.value.x + flyTo.x * shrink
                                translationY = card.value.y + flyTo.y * shrink
                                scaleX = 1f - 0.9f * shrink
                                scaleY = 1f - 0.9f * shrink
                                alpha = appear.value * (1f - 0.4f * shrink)
                            },
                        ) {
                            ZoomableImage(
                                current,
                                active = true,
                                unzoom = 0,
                                onZoomed = {},
                                onTap = {},
                                onVerticalDrag = ::dragUp,
                                onVerticalDragEnd = ::release,
                                onHorizontalDrag = ::dragSideways,
                                onHorizontalDragEnd = ::release,
                            )
                        }
                    }

                    // What has been decided for this photo, when coming back to it; tapping withdraws it.
                    if (!busy) decisions[current.id]?.let { decision ->
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable { record(current, null) }
                                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (decision == Store.SORT_REJECT) stringResource(R.string.sort_rejected)
                                else stringResource(R.string.sort_move_to, nameOf(decision)),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.undo), modifier = Modifier.padding(start = 6.dp).size(16.dp))
                        }
                    }
                }

                Text(
                    stringResource(R.string.sort_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                val here = Media.relativeDir(current)
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(196.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "new") {
                        TrayTile(stringResource(R.string.new_folder), chosen = false, dimmed = false, count = 0, onClick = { naming = true }) {
                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }
                    }
                    items(targets, key = { it.dir }) { target ->
                        TrayTile(
                            target.name,
                            chosen = !busy && decisions[current.id] == target.dir,
                            // The folder the photo is already in: tapping it keeps the photo there.
                            dimmed = target.dir == here,
                            count = counts[target.dir] ?: 0,
                            onClick = { file(target.dir) },
                            modifier = Modifier
                                // Where the tile is, so a filed photo can fly to it; and a small bounce as it lands.
                                .onGloballyPositioned { tileCentres[target.dir] = it.boundsInRoot().center }
                                .graphicsLayer {
                                    val bounce = if (pulsed == target.dir) pulse.value else 1f
                                    scaleX = bounce
                                    scaleY = bounce
                                },
                        ) {
                            if (target.cover != null) Thumb(target.cover, Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(FrsIcons.Folder, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The coloured strip behind a card being swiped away, naming what letting go will do. It is dimmer
 * until the swipe is [armed], and its icon and label only show once there is room for them.
 */
@Composable
private fun ActionBar(modifier: Modifier, color: Color, icon: ImageVector, label: String, armed: Boolean) {
    val grow by animateFloatAsState(if (armed) 1.25f else 1f, label = "armed")
    Box(modifier.clipToBounds().background(if (armed) color else color.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(Modifier.wrapContentSize(unbounded = true), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = grow; scaleY = grow })
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A folder in the tray: its picture, its name, and how many photos are waiting to go into it. */
@Composable
private fun TrayTile(
    name: String,
    chosen: Boolean,
    dimmed: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).alpha(if (dimmed) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 2.dp).size(64.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (chosen) Modifier.border(2.5.dp, accent, RoundedCornerShape(12.dp)) else Modifier),
            ) { picture() }
            if (count > 0) Text(
                count.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(CircleShape).background(accent).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** Everything decided so far, to confirm before any file is touched. Rejected photos can be tapped to keep them. */
@Composable
private fun Review(
    decisions: Map<Long, String>,
    photo: (Long) -> Photo?,
    nameOf: (String) -> String,
    onKeep: (Long) -> Unit,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
    onLater: () -> Unit,
    onApply: () -> Unit,
) {
    val moves = decisions.values.filter { it != Store.SORT_REJECT }.groupingBy { it }.eachCount().toList()
    val rejects = decisions.filterValues { it == Store.SORT_REJECT }.keys.mapNotNull(photo)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(stringResource(R.string.sort_review), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (decisions.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.sort_none), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 24.dp))
            }
            if (moves.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ReviewHeading(stringResource(R.string.sort_moves, moves.sumOf { it.second })) }
                items(moves, key = { "m" + it.first }, span = { GridItemSpan(maxLineSpan) }) { (dir, count) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(nameOf(dir), style = MaterialTheme.typography.bodyLarge)
                            Text(dir.trimEnd('/'), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Text(count.toString(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (rejects.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        ReviewHeading(stringResource(R.string.sort_rejects, rejects.size))
                        Text(
                            stringResource(R.string.sort_rescue_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                items(rejects, key = { it.id }) { rejected ->
                    Thumb(rejected, Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onKeep(rejected.id) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f))
            TextButton(onClick = onLater) { Text(stringResource(R.string.sort_later)) }
            Button(onClick = onApply, enabled = decisions.isNotEmpty(), modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.apply)) }
        }
    }
}

@Composable
private fun ReviewHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}
