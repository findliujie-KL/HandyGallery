package com.findliujie.fullresshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class StrokeKind { Pen, Mosaic, Blur }

enum class FigureKind { Arrow, Rect, Oval, MosaicRect, BlurRect }

/**
 * Everything drawn on top of a photo. Positions are normalized to the *original* image (0..1 on each
 * axis) and lengths are fractions of its shorter side, so items are unaffected by later crops or
 * rotations and render identically on the preview and at full resolution.
 */
sealed interface EditItem {
    /** Whether this hides image content (mosaic or blur) rather than drawing on top of it. */
    val covers: Boolean

    /** A freehand line. */
    data class Stroke(val kind: StrokeKind, val color: Int, val width: Float, val points: List<Offset>) : EditItem {
        override val covers: Boolean get() = kind != StrokeKind.Pen
    }

    /**
     * A shape defined by two points: an arrow from [a] to [b], or the box with [a] and [b] as opposite
     * corners. Unlike a stroke it stays an object that can be moved and resized after it is drawn.
     */
    data class Figure(val kind: FigureKind, val color: Int, val width: Float, val a: Offset, val b: Offset) : EditItem {
        override val covers: Boolean get() = kind == FigureKind.MosaicRect || kind == FigureKind.BlurRect
    }

    /** [size] is the text height; [angle] is in degrees, in the original image's orientation. */
    data class Label(
        val text: String,
        val color: Int,
        val boxed: Boolean,
        val center: Offset,
        val size: Float,
        val angle: Float,
    ) : EditItem {
        override val covers: Boolean get() = false
    }
}

/**
 * A complete, non-destructive edit: the original image, [items] on it, then [turns] quarter-turns
 * clockwise, then [crop] (normalized to the rotated image).
 */
data class EditState(
    val turns: Int = 0,
    val crop: Rect = Rect(0f, 0f, 1f, 1f),
    val items: List<EditItem> = emptyList(),
) {
    /** One more quarter-turn clockwise, keeping the same region cropped. */
    fun rotated(): EditState =
        copy(turns = (turns + 1) % 4, crop = Rect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right))
}

object Edits {
    /** A heavily downscaled copy of the image: drawn back up unfiltered it is a mosaic, filtered a blur. */
    fun coverSource(src: Bitmap): Bitmap {
        val block = max(1, min(src.width, src.height) / 50)
        return Bitmap.createScaledBitmap(src, max(1, src.width / block), max(1, src.height / block), true)
    }

    /** Pixel size of the edited result for a [w] x [h] original. */
    fun outputSize(state: EditState, w: Int, h: Int): Pair<Float, Float> {
        val sideways = state.turns % 2 == 1
        return state.crop.width * (if (sideways) h else w) to state.crop.height * (if (sideways) w else h)
    }

    /** Maps pixel coordinates of the [w] x [h] original onto [dst], where the rotated and cropped result is shown. */
    fun matrix(state: EditState, w: Int, h: Int, dst: RectF): Matrix = Matrix().apply {
        setScale(1f / w, 1f / h)
        when (state.turns) {
            1 -> { postRotate(90f); postTranslate(1f, 0f) }
            2 -> { postRotate(180f); postTranslate(1f, 1f) }
            3 -> { postRotate(270f); postTranslate(0f, 1f) }
        }
        postTranslate(-state.crop.left, -state.crop.top)
        postScale(dst.width() / state.crop.width, dst.height() / state.crop.height)
        postTranslate(dst.left, dst.top)
    }

    /** Draws the edited image into [dst]. [cover] must come from [coverSource] of [base]. */
    fun render(canvas: Canvas, dst: RectF, state: EditState, base: Bitmap, cover: Bitmap) {
        canvas.save()
        canvas.clipRect(dst)
        canvas.concat(matrix(state, base.width, base.height, dst))
        canvas.drawBitmap(base, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val w = base.width.toFloat()
        val h = base.height.toFloat()
        // Mosaic hides image content, so it goes under pen marks and text regardless of the order they were made in.
        val (under, over) = state.items.partition { it.covers }
        (under + over).forEach { item ->
            when (item) {
                is EditItem.Stroke -> drawStroke(canvas, item, w, h, cover)
                is EditItem.Figure -> drawFigure(canvas, item, w, h, cover)
                is EditItem.Label -> drawLabel(canvas, item, w, h)
            }
        }
        canvas.restore()
    }

    private fun inkPaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = width
        this.color = color
    }

    /** Makes [paint] lay down the pixelated (or, with [smooth], blurred) image instead of a colour. */
    private fun useCover(paint: Paint, cover: Bitmap, w: Float, h: Float, smooth: Boolean) {
        paint.isFilterBitmap = smooth
        paint.shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(w / cover.width, h / cover.height) })
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: EditItem.Stroke, w: Float, h: Float, cover: Bitmap) {
        if (stroke.points.isEmpty()) return
        val paint = inkPaint(stroke.color, stroke.width * min(w, h))
        if (stroke.covers) useCover(paint, cover, w, h, smooth = stroke.kind == StrokeKind.Blur)
        canvas.drawPath(strokePath(stroke, w, h), paint)
    }

    private fun strokePath(stroke: EditItem.Stroke, w: Float, h: Float) = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * w, first.y * h)
        stroke.points.forEach { lineTo(it.x * w, it.y * h) }
    }

    // Outlining a brush stroke means merging every overlapping segment, which is too slow to redo each frame.
    private val outlines = WeakHashMap<EditItem.Stroke, Path>()
    private var outlinesFor = 0 to 0

    /** The outer edge of [stroke]'s ink, in pixels of a [w] x [h] image. */
    private fun outline(stroke: EditItem.Stroke, w: Int, h: Int): Path {
        if (outlinesFor != w to h) {
            outlines.clear()
            outlinesFor = w to h
        }
        return outlines.getOrPut(stroke) {
            val edge = Path()
            inkPaint(0, stroke.width * min(w, h)).getFillPath(strokePath(stroke, w.toFloat(), h.toFloat()), edge)
            // The raw outline loops back on itself wherever the brush overlapped; a self-union leaves only the outside.
            edge.apply { op(this, Path.Op.UNION) }
        }
    }

    /**
     * Editor-only overlay showing where mosaic and blur have been applied, since the effect itself can
     * be invisible on plain areas. Never part of the exported image.
     *
     * [active] is the stroke being brushed right now, shown as a red trail. [dp] is one screen dp
     * expressed in [dst]'s units, so lines keep the same weight whatever the zoom.
     */
    fun drawGuides(canvas: Canvas, dst: RectF, state: EditState, w: Int, h: Int, active: EditItem?, dp: Float) {
        if (state.items.none { it.covers }) return
        val toDst = matrix(state, w, h, dst)
        val unit = dp / toDst.mapRadius(1f)
        canvas.save()
        canvas.clipRect(dst)
        canvas.concat(toDst)
        // White dashes over a dark line, so the edge reads on both light and dark photos.
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * unit
            color = 0x99000000.toInt()
        }
        val dashes = Paint(dark).apply {
            color = Color.WHITE
            pathEffect = DashPathEffect(floatArrayOf(6 * unit, 4 * unit), 0f)
        }
        fun dashed(path: Path) {
            canvas.drawPath(path, dark)
            canvas.drawPath(path, dashes)
        }
        for (item in state.items) {
            if (!item.covers) continue
            when (item) {
                is EditItem.Stroke ->
                    if (item === active) {
                        canvas.drawPath(strokePath(item, w.toFloat(), h.toFloat()), inkPaint(0x66FF0000, item.width * min(w, h)))
                    } else if (item.points.isNotEmpty()) dashed(outline(item, w, h))
                is EditItem.Figure -> {
                    val box = RectF(
                        min(item.a.x, item.b.x) * w, min(item.a.y, item.b.y) * h,
                        max(item.a.x, item.b.x) * w, max(item.a.y, item.b.y) * h,
                    )
                    dashed(Path().apply { addRect(box, Path.Direction.CW) })
                    // Corner handles: dragging one resizes the box.
                    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                    for (x in floatArrayOf(box.left, box.right)) for (y in floatArrayOf(box.top, box.bottom)) {
                        canvas.drawCircle(x, y, 4.5f * unit, fill)
                        canvas.drawCircle(x, y, 4.5f * unit, dark)
                    }
                }
                is EditItem.Label -> Unit
            }
        }
        canvas.restore()
    }

    private fun drawFigure(canvas: Canvas, figure: EditItem.Figure, w: Float, h: Float, cover: Bitmap) {
        val paint = inkPaint(figure.color, figure.width * min(w, h))
        val ax = figure.a.x * w
        val ay = figure.a.y * h
        val bx = figure.b.x * w
        val by = figure.b.y * h
        val box = RectF(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
        when (figure.kind) {
            FigureKind.Arrow -> {
                canvas.drawLine(ax, ay, bx, by, paint)
                val angle = atan2(by - ay, bx - ax)
                val head = paint.strokeWidth * 5
                for (wing in floatArrayOf(2.6f, -2.6f)) {
                    canvas.drawLine(bx, by, bx + head * cos(angle + wing), by + head * sin(angle + wing), paint)
                }
            }
            FigureKind.Rect -> canvas.drawRect(box, paint)
            FigureKind.Oval -> canvas.drawOval(box, paint)
            FigureKind.MosaicRect, FigureKind.BlurRect -> {
                paint.style = Paint.Style.FILL
                useCover(paint, cover, w, h, smooth = figure.kind == FigureKind.BlurRect)
                canvas.drawRect(box, paint)
            }
        }
    }

    private fun labelPaint(label: EditItem.Label, unit: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = label.size * unit
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /** Width and height of the label's box, in pixels of the original image. */
    private fun labelBox(label: EditItem.Label, paint: Paint): Pair<Float, Float> {
        val lines = label.text.lines()
        val pad = paint.textSize * 0.35f
        return lines.maxOf { paint.measureText(it) } + 2 * pad to lines.size * paint.fontSpacing + 2 * pad
    }

    private fun drawLabel(canvas: Canvas, label: EditItem.Label, w: Float, h: Float) {
        val paint = labelPaint(label, min(w, h))
        val (bw, bh) = labelBox(label, paint)
        canvas.save()
        canvas.translate(label.center.x * w, label.center.y * h)
        canvas.rotate(label.angle)
        if (label.boxed) {
            val corner = paint.textSize * 0.3f
            canvas.drawRoundRect(-bw / 2, -bh / 2, bw / 2, bh / 2, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.color })
            paint.color = if (Color.luminance(label.color) > 0.6f) Color.BLACK else Color.WHITE
        } else {
            paint.color = label.color
            paint.setShadowLayer(paint.textSize * 0.08f, 0f, 0f, 0x80000000.toInt())
        }
        val lines = label.text.lines()
        val top = -lines.size * paint.fontSpacing / 2 - paint.fontMetrics.ascent
        lines.forEachIndexed { i, line -> canvas.drawText(line, 0f, top + i * paint.fontSpacing, paint) }
        canvas.restore()
    }

    /** Whether [point] (normalized) falls on [label], with [slop] pixels of the original image to spare. */
    fun hitLabel(label: EditItem.Label, point: Offset, w: Int, h: Int, slop: Float): Boolean {
        val (bw, bh) = labelBox(label, labelPaint(label, min(w, h).toFloat()))
        val dx = (point.x - label.center.x) * w
        val dy = (point.y - label.center.y) * h
        val radians = Math.toRadians(-label.angle.toDouble())
        val x = dx * cos(radians) - dy * sin(radians)
        val y = dx * sin(radians) + dy * cos(radians)
        return abs(x) <= bw / 2 + slop && abs(y) <= bh / 2 + slop
    }

    /** Whether [point] (normalized) is within [radius] pixels of [stroke]'s ink. */
    fun hitStroke(stroke: EditItem.Stroke, point: Offset, w: Int, h: Int, radius: Float): Boolean {
        val reach = radius + stroke.width * min(w, h) / 2
        val px = point.x * w
        val py = point.y * h
        val points = stroke.points
        if (points.size == 1) return hypot(points[0].x * w - px, points[0].y * h - py) <= reach
        return points.zipWithNext().any { (a, b) -> segmentDistance(px, py, a.x * w, a.y * h, b.x * w, b.y * h) <= reach }
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val length2 = vx * vx + vy * vy
        val t = if (length2 == 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / length2).coerceIn(0f, 1f)
        return hypot(ax + t * vx - px, ay + t * vy - py)
    }

    /**
     * Whether [point] (normalized) touches [figure] within [radius] pixels: on the line of an arrow or
     * outline, or anywhere inside a mosaic box.
     */
    fun hitFigure(figure: EditItem.Figure, point: Offset, w: Int, h: Int, radius: Float): Boolean {
        val reach = radius + figure.width * min(w, h) / 2
        val px = point.x * w
        val py = point.y * h
        val ax = figure.a.x * w
        val ay = figure.a.y * h
        val bx = figure.b.x * w
        val by = figure.b.y * h
        val left = min(ax, bx)
        val right = max(ax, bx)
        val top = min(ay, by)
        val bottom = max(ay, by)
        return when (figure.kind) {
            FigureKind.Arrow -> segmentDistance(px, py, ax, ay, bx, by) <= reach
            FigureKind.MosaicRect, FigureKind.BlurRect ->
                px >= left - radius && px <= right + radius && py >= top - radius && py <= bottom + radius
            FigureKind.Rect -> listOf(
                segmentDistance(px, py, left, top, right, top), segmentDistance(px, py, right, top, right, bottom),
                segmentDistance(px, py, right, bottom, left, bottom), segmentDistance(px, py, left, bottom, left, top),
            ).min() <= reach
            FigureKind.Oval -> {
                val rx = ((right - left) / 2).coerceAtLeast(1f)
                val ry = ((bottom - top) / 2).coerceAtLeast(1f)
                // Distance from the outline, estimated along the ray from the centre.
                val radial = hypot((px - (left + right) / 2) / rx, (py - (top + bottom) / 2) / ry)
                abs(radial - 1f) * min(rx, ry) <= reach
            }
        }
    }

    /**
     * [figure] grown by [zoom] about its own centre and, for arrows, turned by [degrees]. Boxes stay
     * upright. Worked in pixels because the two axes of the image are normalized by different lengths.
     */
    fun resizeFigure(figure: EditItem.Figure, zoom: Float, degrees: Float, w: Int, h: Int): EditItem.Figure {
        val cx = (figure.a.x + figure.b.x) / 2 * w
        val cy = (figure.a.y + figure.b.y) / 2 * h
        val turn = if (figure.kind == FigureKind.Arrow) Math.toRadians(degrees.toDouble()) else 0.0
        fun place(o: Offset): Offset {
            val dx = (o.x * w - cx) * zoom
            val dy = (o.y * h - cy) * zoom
            val x = dx * cos(turn) - dy * sin(turn)
            val y = dx * sin(turn) + dy * cos(turn)
            return Offset(((cx + x) / w).toFloat(), ((cy + y) / h).toFloat())
        }
        return figure.copy(a = place(figure.a), b = place(figure.b))
    }

    /** Renders [state] on the full-resolution image. */
    suspend fun renderFull(context: Context, photo: Photo, state: EditState): Bitmap = withContext(Dispatchers.Default) {
        // Software-decoded: a hardware bitmap can't be drawn onto the software canvas below.
        val base = Media.decode(context, photo.uri, maxPixels = 40_000_000, mutable = true)
        val cover = coverSource(base)
        try {
            val (w, h) = outputSize(state, base.width, base.height)
            val out = Bitmap.createBitmap(
                w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888, true, base.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB),
            )
            render(Canvas(out), RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), state, base, cover)
            out
        } finally {
            base.recycle()
            cover.recycle()
        }
    }
}

/** Largest rect with the given aspect ratio centered inside a [w] x [h] container, [inset] from its edges. */
fun fitRect(w: Float, h: Float, contentW: Float, contentH: Float, inset: Float = 0f): RectF {
    val scale = min((w - 2 * inset) / contentW, (h - 2 * inset) / contentH)
    val rw = contentW * scale
    val rh = contentH * scale
    return RectF((w - rw) / 2, (h - rh) / 2, (w + rw) / 2, (h + rh) / 2)
}
