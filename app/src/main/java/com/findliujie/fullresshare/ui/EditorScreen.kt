package com.findliujie.fullresshare.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findliujie.fullresshare.EditItem
import com.findliujie.fullresshare.EditState
import com.findliujie.fullresshare.Edits
import com.findliujie.fullresshare.Media
import androidx.compose.ui.res.stringResource
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.R
import com.findliujie.fullresshare.FigureKind
import com.findliujie.fullresshare.StrokeKind
import com.findliujie.fullresshare.fitRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

enum class ExportMode { SaveCopy, ReplaceOriginal, Clipboard }

private enum class ToolMode { Pen, Mosaic }

/** A label being typed: [index] is its position in the items, or -1 for a new one. */
private class TextEntry(val index: Int, val label: EditItem.Label)

private val Accent = Color(0xFF07C160)
private val palette = listOf(
    Color.White, Color.Black, Color(0xFFFA5151), Color(0xFFFF8F1F),
    Color(0xFFFFC300), Color(0xFF91D300), Color(0xFF07C160), Color(0xFF10AEFF),
)

/** Stroke widths as they appear on screen, in dp; zooming in therefore draws finer lines. */
private val sizes = listOf(3f to R.string.width_thin, 6f to R.string.width_medium, 12f to R.string.width_thick)

/** Mosaic and blur are this many times wider than a pen line of the same size setting. */
private const val CoverWidthFactor = 4

/** What a drag with the pen tool creates. */
private enum class PenShape { Free, Arrow, Rect, Oval }
private val FullCrop = Rect(0f, 0f, 1f, 1f)
private const val MinCrop = 0.05f

/**
 * [startInCrop] opens straight onto the crop and rotate screen. With [confirmExit], leaving with
 * unsaved edits asks first. [canReplace] says whether overwriting the original is possible at all.
 * [copied] is the edit last put on the clipboard; leaving with exactly that edit counts as saved.
 */
@Composable
fun EditorScreen(
    photo: Photo,
    startInCrop: Boolean,
    confirmExit: Boolean,
    canReplace: Boolean,
    copied: EditState?,
    onCancel: () -> Unit,
    onExport: (EditState, ExportMode) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // The preview works on a downsized copy; export replays the same EditState on the full image.
    val base by produceState<Bitmap?>(null, photo.cacheKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { Media.decode(context, photo.uri, maxEdge = 2048, mutable = true) }.getOrNull()
        }
    }
    val cover = remember(base) { base?.let(Edits::coverSource) }

    val history = remember { mutableStateListOf(EditState()) }
    var index by remember { mutableIntStateOf(0) }
    val state = history[index]
    /** The in-progress result of the current gesture, shown instead of [state] until it is committed. */
    var live by remember { mutableStateOf<EditState?>(null) }

    fun commit(next: EditState) {
        if (next == history[index]) return
        while (history.size > index + 1) history.removeAt(history.lastIndex)
        history += next
        index++
    }

    var tool by remember { mutableStateOf<ToolMode?>(null) }
    var erasing by remember { mutableStateOf(false) }
    var shape by remember { mutableStateOf(PenShape.Free) }
    /** Mosaic as a box dragged out over an area, rather than brushed on. */
    var area by remember { mutableStateOf(false) }
    var blur by remember { mutableStateOf(false) }
    var color by remember { mutableIntStateOf(palette[2].toArgb()) }
    var widthDp by remember { mutableFloatStateOf(sizes[1].first) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropDraft by remember { mutableStateOf(if (startInCrop) EditState() else null) }
    var asking by remember { mutableStateOf(false) }

    // Read through state rather than captured: a reference to a local function is cached across
    // recompositions and would otherwise keep seeing the values from the first one.
    val latestCopied by rememberUpdatedState(copied)
    val latestConfirmExit by rememberUpdatedState(confirmExit)
    val latestOnCancel by rememberUpdatedState(onCancel)

    fun leave() {
        val current = history[index]
        if (latestConfirmExit && current != EditState() && current != latestCopied) asking = true else latestOnCancel()
    }
    // Registered before the crop and text screens' own handlers, which therefore take priority while open.
    BackHandler(onBack = ::leave)
    var entry by remember { mutableStateOf<TextEntry?>(null) }
    var draggingLabel by remember { mutableStateOf(false) }
    var binHot by remember { mutableStateOf(false) }
    /** True while a mosaic or blur stroke is being brushed, which is shown as a red trail. */
    var brushing by remember { mutableStateOf(false) }

    val bitmap = base
    if (bitmap == null || cover == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }
    val bw = bitmap.width
    val bh = bitmap.height

    /** Original-image pixels -> canvas pixels, including the current zoom and pan. */
    fun viewMatrix(s: EditState): Matrix {
        val (ow, oh) = Edits.outputSize(s, bw, bh)
        val r = fitRect(canvasSize.width.toFloat(), canvasSize.height.toFloat(), ow, oh)
        return Edits.matrix(s, bw, bh, r).apply {
            postScale(zoom, zoom)
            postTranslate(pan.x, pan.y)
        }
    }

    fun newLabel() {
        val s = history[index]
        val view = viewMatrix(s)
        val center = floatArrayOf(canvasSize.width / 2f, canvasSize.height / 2f)
        Matrix().also(view::invert).mapPoints(center)
        val size = with(density) { 28.sp.toPx() } / view.mapRadius(1f) / min(bw, bh)
        // Counter the image's rotation so new text reads upright as currently displayed.
        entry = TextEntry(-1, EditItem.Label("", color, false, Offset(center[0] / bw, center[1] / bh), size, -90f * s.turns))
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val draft = cropDraft
            if (draft != null) CropPanel(
                bitmap, cover, draft,
                onChange = { cropDraft = it },
                onCancel = { cropDraft = null },
                onDone = {
                    commit(history[index].copy(turns = draft.turns, crop = draft.crop))
                    cropDraft = null
                    zoom = 1f
                    pan = Offset.Zero
                },
            )
            else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::leave) { Text(stringResource(R.string.cancel), color = Color.White) }
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { index-- }, enabled = index > 0) { Icon(FrsIcons.Undo, contentDescription = stringResource(R.string.undo)) }
                    IconButton(onClick = { index++ }, enabled = index < history.lastIndex) {
                        Icon(FrsIcons.Undo, contentDescription = stringResource(R.string.redo), Modifier.graphicsLayer { scaleX = -1f })
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { canvasSize = it }) {
                    val shown = live ?: state
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(bitmap) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = history[index]
                                val view = viewMatrix(start)
                                val inverse = Matrix().also(view::invert)
                                val scale = view.mapRadius(1f)
                                fun toBase(p: Offset): Offset {
                                    val point = floatArrayOf(p.x, p.y)
                                    inverse.mapPoints(point)
                                    return Offset(point[0] / bw, point[1] / bh)
                                }

                                val origin = toBase(down.position)
                                val reach = 12.dp.toPx() / scale
                                // Text and shapes can be grabbed whatever tool is active, as in WeChat. The eraser is
                                // the exception for shapes: there a touch means "remove", not "move".
                                val grabbed = start.items.indexOfLast {
                                    when (it) {
                                        is EditItem.Label -> Edits.hitLabel(it, origin, bw, bh, reach)
                                        is EditItem.Figure -> !erasing && Edits.hitFigure(it, origin, bw, bh, reach)
                                        is EditItem.Stroke -> false
                                    }
                                }
                                fun replaced(s: EditState, item: EditItem) =
                                    s.copy(items = s.items.toMutableList().also { it[grabbed] = item })

                                // Touching an arrow's end or a box's corner drags just that point, which is how a shape
                                // is reshaped; touching it anywhere else moves the whole thing.
                                val heldFigure = start.items.getOrNull(grabbed) as? EditItem.Figure
                                val corner = heldFigure?.let { f ->
                                    val isArrow = f.kind == FigureKind.Arrow
                                    val points = if (isArrow) listOf(f.a, f.b) else listOf(f.a, f.b, Offset(f.a.x, f.b.y), Offset(f.b.x, f.a.y))
                                    points.filter { hypot((it.x - origin.x) * bw, (it.y - origin.y) * bh) <= reach * 2 }
                                        .minByOrNull { hypot((it.x - origin.x) * bw, (it.y - origin.y) * bh) }
                                }
                                /** [heldFigure] with its grabbed [corner] moved to [p]. */
                                fun reshaped(f: EditItem.Figure, from: Offset, p: Offset): EditItem.Figure = when {
                                    f.kind == FigureKind.Arrow -> if (from == f.a) f.copy(a = p) else f.copy(b = p)
                                    // The corner opposite the one held stays where it is.
                                    else -> f.copy(a = Offset(f.a.x + f.b.x - from.x, f.a.y + f.b.y - from.y), b = p)
                                }

                                var working = start
                                var drawn: EditItem? = null
                                var multi = false
                                var moved = false
                                var taken = false
                                do {
                                    val event = awaitPointerEvent()
                                    // Nothing sits on top of the canvas, so an already-consumed event means the system took
                                    // the touch over, as it does for a back swipe from the screen edge. Whatever the touch
                                    // had started must not become an edit.
                                    if (event.changes.any { it.isConsumed }) {
                                        taken = true
                                        break
                                    }
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        multi = true
                                        val centroid = event.calculateCentroid()
                                        val shift = event.calculatePan()
                                        if (grabbed >= 0) {
                                            // Two fingers on text or a shape resize, rotate and move it.
                                            val by = toBase(centroid) - toBase(centroid - shift)
                                            working = replaced(
                                                working,
                                                when (val item = working.items[grabbed]) {
                                                    is EditItem.Label -> item.copy(
                                                        size = (item.size * event.calculateZoom()).coerceIn(0.01f, 2f),
                                                        angle = item.angle + event.calculateRotation(),
                                                        center = item.center + by,
                                                    )
                                                    is EditItem.Figure ->
                                                        Edits.resizeFigure(item, event.calculateZoom(), event.calculateRotation(), bw, bh)
                                                            .let { it.copy(a = it.a + by, b = it.b + by) }
                                                    is EditItem.Stroke -> item
                                                },
                                            )
                                            live = working
                                        } else {
                                            // Two fingers elsewhere zoom and pan the canvas; abandon anything the first finger began.
                                            drawn = null
                                            working = start
                                            live = null
                                            val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, 8f)
                                            val target = (pan - centroid) * (newZoom / zoom) + centroid + shift
                                            zoom = newZoom
                                            pan = Offset(
                                                target.x.coerceIn(size.width * (1 - newZoom), 0f),
                                                target.y.coerceIn(size.height * (1 - newZoom), 0f),
                                            )
                                        }
                                    } else if (!multi && pressed.size == 1) {
                                        val position = pressed[0].position
                                        if ((position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                        val p = toBase(position)
                                        val mode = tool
                                        when {
                                            heldFigure != null && corner != null -> if (moved) {
                                                working = replaced(start, reshaped(heldFigure, corner, p))
                                                live = working
                                            }
                                            grabbed >= 0 -> if (moved) {
                                                val by = p - origin
                                                working = replaced(
                                                    start,
                                                    when (val item = start.items[grabbed]) {
                                                        is EditItem.Label -> item.copy(center = item.center + by)
                                                        is EditItem.Figure -> item.copy(a = item.a + by, b = item.b + by)
                                                        is EditItem.Stroke -> item
                                                    },
                                                )
                                                live = working
                                                draggingLabel = true
                                                binHot = position.y > size.height - 96.dp.toPx()
                                            }
                                            mode == null -> Unit
                                            erasing -> {
                                                val radius = 14.dp.toPx() / scale
                                                val kept = working.items.filterNot {
                                                    it.covers == (mode == ToolMode.Mosaic) && when (it) {
                                                        is EditItem.Stroke -> Edits.hitStroke(it, p, bw, bh, radius)
                                                        is EditItem.Figure -> Edits.hitFigure(it, p, bw, bh, radius)
                                                        is EditItem.Label -> false
                                                    }
                                                }
                                                if (kept.size != working.items.size) {
                                                    working = working.copy(items = kept)
                                                    live = working
                                                }
                                            }
                                            moved -> {
                                                val covering = mode == ToolMode.Mosaic
                                                val width = widthDp.dp.toPx() * (if (covering) CoverWidthFactor else 1) / scale / min(bw, bh)
                                                val figure = when {
                                                    covering -> if (!area) null else if (blur) FigureKind.BlurRect else FigureKind.MosaicRect
                                                    shape == PenShape.Arrow -> FigureKind.Arrow
                                                    shape == PenShape.Rect -> FigureKind.Rect
                                                    shape == PenShape.Oval -> FigureKind.Oval
                                                    else -> null
                                                }
                                                drawn = if (figure != null) EditItem.Figure(figure, color, width, origin, p) else {
                                                    val kind = if (!covering) StrokeKind.Pen else if (blur) StrokeKind.Blur else StrokeKind.Mosaic
                                                    val line = drawn as? EditItem.Stroke ?: EditItem.Stroke(kind, color, width, listOf(origin))
                                                    line.copy(points = line.points + p)
                                                }
                                                brushing = covering && figure == null
                                                working = start.copy(items = start.items + drawn)
                                                live = working
                                            }
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })

                                val held = start.items.getOrNull(grabbed)
                                when {
                                    taken -> Unit
                                    held is EditItem.Label && !moved && !multi -> entry = TextEntry(grabbed, held)
                                    held != null && binHot -> commit(start.copy(items = start.items.filterIndexed { i, _ -> i != grabbed }))
                                    else -> commit(working)
                                }
                                live = null
                                brushing = false
                                draggingLabel = false
                                binHot = false
                            }
                        },
                    ) {
                        val (ow, oh) = Edits.outputSize(shown, bw, bh)
                        val r = fitRect(size.width, size.height, ow, oh)
                        withTransform({
                            translate(pan.x, pan.y)
                            scale(zoom, zoom, Offset.Zero)
                        }) {
                            drawIntoCanvas {
                                Edits.render(it.nativeCanvas, r, shown, bitmap, cover)
                                val active = if (brushing) shown.items.lastOrNull() else null
                                Edits.drawGuides(it.nativeCanvas, r, shown, bw, bh, active, 1.dp.toPx() / zoom)
                            }
                        }
                    }
                    if (draggingLabel) Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (binHot) Color(0xFFD32F2F) else Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(stringResource(if (binHot) R.string.release_to_delete else R.string.drag_to_delete), Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // Options for the active tool; fixed height so the photo doesn't jump as tools change.
                Column(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Bottom) {
                    if (tool == ToolMode.Pen) Row(
                        Modifier.fillMaxWidth().height(44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        palette.forEach { c -> ColorDot(c, selected = color == c.toArgb() && !erasing) { color = c.toArgb(); erasing = false } }
                    }
                    if (tool != null) Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { erasing = !erasing }) {
                            Icon(FrsIcons.Eraser, contentDescription = stringResource(R.string.eraser), tint = if (erasing) Accent else Color.White)
                        }
                        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(Color.Gray))
                        if (tool == ToolMode.Pen) {
                            val shapes = listOf(
                                Triple(PenShape.Free, FrsIcons.Brush, R.string.draw),
                                Triple(PenShape.Arrow, FrsIcons.ArrowShape, R.string.arrow),
                                Triple(PenShape.Rect, FrsIcons.RectShape, R.string.rectangle),
                                Triple(PenShape.Oval, FrsIcons.OvalShape, R.string.oval),
                            )
                            shapes.forEach { (value, icon, label) ->
                                OptionIcon(icon, stringResource(label), shape == value && !erasing) { shape = value; erasing = false }
                            }
                        } else {
                            Choice(stringResource(R.string.pixel), !blur && !erasing) { blur = false; erasing = false }
                            Choice(stringResource(R.string.blur), blur && !erasing) { blur = true; erasing = false }
                            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(Color.Gray))
                            OptionIcon(FrsIcons.Brush, stringResource(R.string.brush), !area && !erasing) { area = false; erasing = false }
                            OptionIcon(FrsIcons.RectShape, stringResource(R.string.area), area && !erasing) { area = true; erasing = false }
                        }
                        Box(Modifier.weight(1f))
                        // Each dot is drawn at the width it stands for; mosaic's are wider, so they are shown at half size.
                        val dotScale = if (tool == ToolMode.Mosaic) CoverWidthFactor / 2f else 1f
                        sizes.forEach { (value, label) ->
                            WidthDot((value * dotScale).dp, stringResource(label), widthDp == value) { widthDp = value }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    fun pick(mode: ToolMode) {
                        tool = if (tool == mode) null else mode
                        erasing = false
                    }
                    ToolIcon(Icons.Filled.Edit, stringResource(R.string.draw), tool == ToolMode.Pen) { pick(ToolMode.Pen) }
                    ToolIcon(FrsIcons.Text, stringResource(R.string.text), false, ::newLabel)
                    ToolIcon(FrsIcons.Crop, stringResource(R.string.crop_rotate), false) { cropDraft = history[index] }
                    ToolIcon(FrsIcons.Mosaic, stringResource(R.string.mosaic), tool == ToolMode.Mosaic) { pick(ToolMode.Mosaic) }
                    Box(Modifier.weight(1f))
                    val changed = state != EditState()
                    ExportButton(FrsIcons.SaveCopy, stringResource(R.string.save_copy), changed) { onExport(state, ExportMode.SaveCopy) }
                    ExportButton(FrsIcons.Replace, stringResource(R.string.replace), changed && canReplace) { onExport(state, ExportMode.ReplaceOriginal) }
                    ExportButton(FrsIcons.Copy, stringResource(R.string.copy), true) { onExport(state, ExportMode.Clipboard) }
                }
            }

            if (asking) AlertDialog(
                onDismissRequest = { asking = false },
                title = { Text(stringResource(R.string.unsaved_title)) },
                text = { Text(stringResource(R.string.unsaved_text)) },
                confirmButton = {
                    TextButton(onClick = { asking = false; onExport(history[index], ExportMode.SaveCopy) }) {
                        Text(stringResource(R.string.save_copy))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { asking = false; onCancel() }) { Text(stringResource(R.string.discard)) }
                        TextButton(onClick = { asking = false }) { Text(stringResource(R.string.keep_editing)) }
                    }
                },
            )

            entry?.let { current ->
                TextEntryOverlay(
                    initial = current.label,
                    onCancel = { entry = null },
                    onDone = { label ->
                        val s = history[index]
                        val items = s.items.toMutableList()
                        when {
                            label.text.isBlank() -> if (current.index >= 0) items.removeAt(current.index)
                            current.index >= 0 -> items[current.index] = label
                            else -> items += label
                        }
                        commit(s.copy(items = items))
                        entry = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = if (active) Accent else Color.White) }
}

@Composable
private fun ExportButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Accent)
        Text(label, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
    }
}

@Composable
private fun OptionIcon(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = if (selected) Accent else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A line-width choice shown as a dot of that width. */
@Composable
private fun WidthDot(diameter: Dp, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(diameter.coerceAtLeast(4.dp)).clip(CircleShape).background(if (selected) Accent else Color.White))
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Accent else Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(if (selected) 28.dp else 20.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 1.5.dp, Color.White, CircleShape),
        )
    }
}

/** WeChat-style text entry: the photo dims, the keyboard opens, colours and a boxed toggle sit above it. */
@Composable
private fun TextEntryOverlay(initial: EditItem.Label, onCancel: () -> Unit, onDone: (EditItem.Label) -> Unit) {
    BackHandler(onBack = onCancel)
    var text by remember { mutableStateOf(TextFieldValue(initial.text, TextRange(initial.text.length))) }
    var color by remember { mutableIntStateOf(initial.color) }
    var boxed by remember { mutableStateOf(initial.boxed) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Color.White) }
            Box(Modifier.weight(1f))
            Button(
                onClick = { onDone(initial.copy(text = text.text.trim(), color = color, boxed = boxed)) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            ) { Text(stringResource(R.string.done)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            val ink = Color(color)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    color = if (!boxed) ink else if (ink.luminance() > 0.6f) Color.Black else Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier
                    .focusRequester(focus)
                    .then(if (boxed) Modifier.background(ink, RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { boxed = !boxed }) {
                Icon(
                    FrsIcons.Text,
                    contentDescription = stringResource(R.string.text_background),
                    tint = if (boxed) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .then(if (boxed) Modifier.background(Color.White) else Modifier.border(1.5.dp, Color.White, RoundedCornerShape(4.dp))),
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                palette.forEach { c -> ColorDot(c, color == c.toArgb()) { color = c.toArgb() } }
            }
        }
    }
}

// Which sides of the crop frame a drag moves, as a bitmask; Move drags the whole frame.
private const val Left = 1
private const val Right = 2
private const val Top = 4
private const val Bottom = 8
private const val Move = 16

private fun grabCrop(frame: Rect, p: Offset, reach: Float): Int {
    if (p.x < frame.left - reach || p.x > frame.right + reach || p.y < frame.top - reach || p.y > frame.bottom + reach) return 0
    var grab = 0
    val left = abs(p.x - frame.left)
    val right = abs(p.x - frame.right)
    val top = abs(p.y - frame.top)
    val bottom = abs(p.y - frame.bottom)
    if (left <= reach && left <= right) grab = grab or Left else if (right <= reach) grab = grab or Right
    if (top <= reach && top <= bottom) grab = grab or Top else if (bottom <= reach) grab = grab or Bottom
    return if (grab == 0) Move else grab
}

private fun dragCrop(crop: Rect, grab: Int, dx: Float, dy: Float): Rect = when {
    grab == 0 -> crop
    grab == Move -> crop.translate(dx.coerceIn(-crop.left, 1f - crop.right), dy.coerceIn(-crop.top, 1f - crop.bottom))
    else -> Rect(
        left = if (grab and Left != 0) (crop.left + dx).coerceIn(0f, crop.right - MinCrop) else crop.left,
        top = if (grab and Top != 0) (crop.top + dy).coerceIn(0f, crop.bottom - MinCrop) else crop.top,
        right = if (grab and Right != 0) (crop.right + dx).coerceIn(crop.left + MinCrop, 1f) else crop.right,
        bottom = if (grab and Bottom != 0) (crop.bottom + dy).coerceIn(crop.top + MinCrop, 1f) else crop.bottom,
    )
}

/** The crop and rotate screen: the whole (rotated) image with a draggable frame over it. */
@Composable
private fun CropPanel(
    bitmap: Bitmap,
    cover: Bitmap,
    draft: EditState,
    onChange: (EditState) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val current by rememberUpdatedState(draft)
    val whole = draft.copy(crop = FullCrop)
    val (rw, rh) = Edits.outputSize(whole, bitmap.width, bitmap.height)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Canvas(
            Modifier.weight(1f).fillMaxWidth().pointerInput(bitmap, draft.turns) {
                val r = fitRect(size.width.toFloat(), size.height.toFloat(), rw, rh, 24.dp.toPx())
                var grab = 0
                detectDragGestures(
                    onDragStart = { p ->
                        val c = current.crop
                        val frame = Rect(
                            r.left + c.left * r.width(), r.top + c.top * r.height(),
                            r.left + c.right * r.width(), r.top + c.bottom * r.height(),
                        )
                        grab = grabCrop(frame, p, 28.dp.toPx())
                    },
                    onDrag = { _, delta ->
                        onChange(current.copy(crop = dragCrop(current.crop, grab, delta.x / r.width(), delta.y / r.height())))
                    },
                )
            },
        ) {
            val r = fitRect(size.width, size.height, rw, rh, 24.dp.toPx())
            drawIntoCanvas { Edits.render(it.nativeCanvas, r, whole, bitmap, cover) }
            val c = draft.crop
            val frame = Rect(
                r.left + c.left * r.width(), r.top + c.top * r.height(),
                r.left + c.right * r.width(), r.top + c.bottom * r.height(),
            )
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset(r.left, r.top), Size(r.width(), frame.top - r.top))
            drawRect(dim, Offset(r.left, frame.bottom), Size(r.width(), r.bottom - frame.bottom))
            drawRect(dim, Offset(r.left, frame.top), Size(frame.left - r.left, frame.height))
            drawRect(dim, Offset(frame.right, frame.top), Size(r.right - frame.right, frame.height))
            val thin = 1.dp.toPx()
            for (i in 1..2) {
                val x = frame.left + frame.width * i / 3
                val y = frame.top + frame.height * i / 3
                drawLine(Color.White.copy(alpha = 0.6f), Offset(x, frame.top), Offset(x, frame.bottom), thin)
                drawLine(Color.White.copy(alpha = 0.6f), Offset(frame.left, y), Offset(frame.right, y), thin)
            }
            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))
            // Thick corner and edge marks show where the frame can be grabbed.
            val arm = 18.dp.toPx()
            val thick = 4.dp.toPx()
            for (x in listOf(frame.left, frame.right)) for (y in listOf(frame.top, frame.bottom)) {
                drawLine(Color.White, Offset(x, y), Offset(x + if (x == frame.left) arm else -arm, y), thick)
                drawLine(Color.White, Offset(x, y), Offset(x, y + if (y == frame.top) arm else -arm), thick)
            }
            val mid = frame.center
            for (y in listOf(frame.top, frame.bottom)) drawLine(Color.White, Offset(mid.x - arm / 2, y), Offset(mid.x + arm / 2, y), thick)
            for (x in listOf(frame.left, frame.right)) drawLine(Color.White, Offset(x, mid.y - arm / 2), Offset(x, mid.y + arm / 2), thick)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(draft.rotated()) }) { Icon(FrsIcons.RotateRight, contentDescription = stringResource(R.string.rotate)) }
            Box(Modifier.weight(1f))
            val pristine = draft.turns == 0 && draft.crop == FullCrop
            TextButton(onClick = { onChange(draft.copy(turns = 0, crop = FullCrop)) }, enabled = !pristine) {
                Text(stringResource(R.string.reset), color = if (pristine) Color.Gray else Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
            Box(Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.apply), tint = Accent) }
        }
    }
}
