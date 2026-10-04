package com.findliujie.fullresshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class StrokeKind { Pen, Arrow, Mosaic, Blur }

/**
 * Everything drawn on top of a photo. Positions are normalized to the *original* image (0..1 on each
 * axis) and lengths are fractions of its shorter side, so items are unaffected by later crops or
 * rotations and render identically on the preview and at full resolution.
 */
sealed interface EditItem {
    data class Stroke(val kind: StrokeKind, val color: Int, val width: Float, val points: List<Offset>) : EditItem {
        val covers: Boolean get() = kind == StrokeKind.Mosaic || kind == StrokeKind.Blur
    }

    /** [size] is the text height; [angle] is in degrees, in the original image's orientation. */
    data class Label(
        val text: String,
        val color: Int,
        val boxed: Boolean,
        val center: Offset,
        val size: Float,
        val angle: Float,
    ) : EditItem
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
        val (under, over) = state.items.partition { it is EditItem.Stroke && it.covers }
        (under + over).forEach { item ->
            when (item) {
                is EditItem.Stroke -> drawStroke(canvas, item, w, h, cover)
                is EditItem.Label -> drawLabel(canvas, item, w, h)
            }
        }
        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, stroke: EditItem.Stroke, w: Float, h: Float, cover: Bitmap) {
        if (stroke.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.width * min(w, h)
            color = stroke.color
        }
        val a = stroke.points.first()
        val b = stroke.points.last()
        if (stroke.kind == StrokeKind.Arrow) {
            canvas.drawLine(a.x * w, a.y * h, b.x * w, b.y * h, paint)
            val angle = atan2((b.y - a.y) * h, (b.x - a.x) * w)
            val head = paint.strokeWidth * 5
            for (wing in floatArrayOf(2.6f, -2.6f)) {
                canvas.drawLine(b.x * w, b.y * h, b.x * w + head * cos(angle + wing), b.y * h + head * sin(angle + wing), paint)
            }
            return
        }
        if (stroke.covers) {
            paint.isFilterBitmap = stroke.kind == StrokeKind.Blur
            paint.shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(w / cover.width, h / cover.height) })
            }
        }
        val path = Path().apply {
            moveTo(a.x * w, a.y * h)
            stroke.points.forEach { lineTo(it.x * w, it.y * h) }
        }
        canvas.drawPath(path, paint)
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
        val points = if (stroke.kind == StrokeKind.Arrow) listOf(stroke.points.first(), stroke.points.last()) else stroke.points
        if (points.size == 1) return hypot(points[0].x * w - px, points[0].y * h - py) <= reach
        return points.zipWithNext().any { (a, b) ->
            val ax = a.x * w
            val ay = a.y * h
            val vx = b.x * w - ax
            val vy = b.y * h - ay
            val length2 = vx * vx + vy * vy
            val t = if (length2 == 0f) 0f else (((px - ax) * vx + (py - ay) * vy) / length2).coerceIn(0f, 1f)
            hypot(ax + t * vx - px, ay + t * vy - py) <= reach
        }
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
