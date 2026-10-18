package com.lmxyapp.handygallery.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.lmxyapp.handygallery.R

/**
 * Small looping animations that show a gesture on a simplified screen: a finger (a translucent disc)
 * doing the thing on stand-in photos (coloured tiles). They are drawn here rather than shipped as
 * video, so they follow the theme and cost nothing in size.
 */
enum class Demo(val millis: Int) {
    Select(3600), Tiles(4400), Switch(3600), Share(4000), FrontRow(3600),
    Sort(4800), Similar(3800), Compare(3600),
}

/** Stand-ins for photos. */
private val Swatches = listOf(
    Color(0xFF8AB4F8), Color(0xFFF28B82), Color(0xFFFDD663), Color(0xFF81C995),
    Color(0xFFC58AF9), Color(0xFFFCAD70), Color(0xFF78D9EC), Color(0xFFFF8BCB),
)

private fun swatch(i: Int) = Swatches[((i % Swatches.size) + Swatches.size) % Swatches.size]

/** How far [t] is through the stretch from [a] to [b], eased in and out. */
private fun step(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

private fun mix(a: Float, b: Float, x: Float) = a + (b - a) * x
private fun mix(a: Offset, b: Offset, x: Float) = Offset(mix(a.x, b.x, x), mix(a.y, b.y, x))

private class Ink(
    val surface: Color,
    val tile: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val measurer: TextMeasurer,
    val labels: Map<String, String>,
)

@Composable
fun DemoView(demo: Demo, modifier: Modifier) {
    val loop by rememberInfiniteTransition(label = "demo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(demo.millis, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val scheme = MaterialTheme.colorScheme
    val ink = Ink(
        surface = scheme.surface,
        tile = scheme.surfaceVariant,
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        measurer = rememberTextMeasurer(),
        labels = mapOf(
            "wall" to stringResource(R.string.photos),
            "favourites" to stringResource(R.string.favourites),
            "screenshots" to stringResource(R.string.screenshots),
            "folder" to stringResource(R.string.demo_folder),
            "fullres" to stringResource(R.string.share_full_res),
            "stock" to stringResource(R.string.share_stock),
            "original" to stringResource(R.string.demo_original),
            "trash2" to stringResource(R.string.similar_trash_n, 2),
            "wechat" to stringResource(R.string.demo_picker),
        ),
    )
    Canvas(modifier) {
        drawRoundRect(ink.surface, cornerRadius = CornerRadius(dp(16f)))
        clipRect {
            when (demo) {
                Demo.Select -> select(loop, ink)
                Demo.Tiles -> tiles(loop, ink)
                Demo.Switch -> switch(loop, ink)
                Demo.Share -> share(loop, ink)
                Demo.FrontRow -> frontRow(loop, ink)
                Demo.Sort -> sort(loop, ink)
                Demo.Similar -> similar(loop, ink)
                Demo.Compare -> compare(loop, ink)
            }
        }
    }
}

private fun DrawScope.dp(v: Float) = v * density

/** The fingertip: a translucent disc, with a ring that swells while it is holding down. */
private fun DrawScope.finger(at: Offset, ink: Ink, alpha: Float = 1f, hold: Float = 0f) {
    if (alpha <= 0f) return
    if (hold > 0f) drawCircle(ink.text.copy(alpha = 0.18f * alpha * (1 - hold * 0.5f)), radius = dp(16f + 14f * hold), center = at)
    drawCircle(ink.text.copy(alpha = 0.28f * alpha), radius = dp(15f), center = at)
    drawCircle(ink.surface.copy(alpha = 0.9f * alpha), radius = dp(15f), center = at, style = Stroke(dp(2f)))
}

private fun DrawScope.photo(rect: Rect, index: Int, alpha: Float = 1f, corner: Float = 6f) {
    drawRoundRect(swatch(index).copy(alpha = alpha), rect.topLeft, rect.size, CornerRadius(dp(corner)))
}

/** A stand-in picture with something in it: sky, a sun and a hill, so zooming and cropping read. */
private fun DrawScope.scene(rect: Rect, index: Int, alpha: Float = 1f) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        drawRect(swatch(index).copy(alpha = alpha), rect.topLeft, rect.size)
        drawCircle(Color.White.copy(alpha = 0.85f * alpha), radius = rect.minDimension * 0.12f, center = Offset(rect.left + rect.width * 0.7f, rect.top + rect.height * 0.3f))
        val hill = Path().apply {
            moveTo(rect.left, rect.bottom)
            lineTo(rect.left, rect.top + rect.height * 0.7f)
            quadraticTo(rect.left + rect.width * 0.35f, rect.top + rect.height * 0.4f, rect.left + rect.width * 0.7f, rect.top + rect.height * 0.72f)
            lineTo(rect.right, rect.top + rect.height * 0.62f)
            lineTo(rect.right, rect.bottom)
            close()
        }
        drawPath(hill, Color.Black.copy(alpha = 0.22f * alpha))
    }
}

private fun DrawScope.label(text: String, at: Offset, ink: Ink, color: Color = ink.text, sizeSp: Float = 12f, centred: Boolean = true, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val layout = ink.measurer.measure(text, TextStyle(fontSize = sizeSp.sp, color = color))
    val left = if (centred) at.x - layout.size.width / 2f else at.x
    drawText(layout, topLeft = Offset(left, at.y - layout.size.height / 2f), alpha = alpha)
}

private fun DrawScope.pill(text: String, centre: Offset, ink: Ink, fill: Color, textColor: Color, alpha: Float = 1f, sizeSp: Float = 12f) {
    if (alpha <= 0f) return
    val layout = ink.measurer.measure(text, TextStyle(fontSize = sizeSp.sp, color = textColor))
    val w = layout.size.width + dp(20f)
    val h = layout.size.height + dp(8f)
    drawRoundRect(fill.copy(alpha = fill.alpha * alpha), Offset(centre.x - w / 2, centre.y - h / 2), Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.size.height / 2f), alpha = alpha)
}

private fun DrawScope.check(centre: Offset, ink: Ink, alpha: Float = 1f) {
    if (alpha <= 0f) return
    drawCircle(ink.accent.copy(alpha = alpha), radius = dp(8f), center = centre)
    val p = Path().apply {
        moveTo(centre.x - dp(4f), centre.y)
        lineTo(centre.x - dp(1f), centre.y + dp(3f))
        lineTo(centre.x + dp(4.5f), centre.y - dp(3f))
    }
    drawPath(p, Color.White.copy(alpha = alpha), style = Stroke(dp(2f)))
}

/** Lays [count] photos out [columns] across from [top], with [gap] between, filling the width. */
private fun DrawScope.cells(columns: Float, count: Int, top: Float, gap: Float, scroll: Float = 0f): List<Rect> {
    val side = (size.width - gap * (columns + 1)) / columns
    return (0 until count).map { i ->
        val col = i % columns.toInt().coerceAtLeast(1)
        val row = i / columns.toInt().coerceAtLeast(1)
        Rect(Offset(gap + col * (side + gap), top + row * (side + gap) - scroll), Size(side, side))
    }
}

// ---- Photo wall ----

private fun DrawScope.select(t: Float, ink: Ink) {
    val rects = cells(4f, 12, dp(10f), dp(5f))
    // Held on the first photo, then slid across the next three.
    val path = listOf(0, 1, 2, 3, 7)
    val hold = step(t, 0.1f, 0.3f)
    val travel = step(t, 0.35f, 0.75f) * (path.size - 1)
    val segment = travel.toInt().coerceAtMost(path.size - 2)
    val at = mix(rects[path[segment]].center, rects[path[segment + 1]].center, travel - segment)
    val reached = if (hold >= 1f) path.take(segment + 1 + if (travel - segment > 0.5f) 1 else 0).toSet() else emptySet()
    val clear = step(t, 0.9f, 0.98f)
    rects.forEachIndexed { i, r ->
        val picked = i in reached && clear < 1f
        val inset = if (picked) dp(5f) else 0f
        photo(Rect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), i)
        if (picked) check(Offset(r.left + dp(12f), r.top + dp(12f)), ink, 1 - clear)
    }
    finger(at, ink, step(t, 0.04f, 0.1f) * (1 - step(t, 0.8f, 0.88f)), hold = if (t < 0.35f) hold else 0f)
}

private fun DrawScope.tiles(t: Float, ink: Ink) {
    val names = listOf(ink.labels.getValue("favourites"), ink.labels.getValue("screenshots"), ink.labels.getValue("folder"))
    // First a folder of one's own is ticked in the list of shortcuts...
    val list = 1f - step(t, 0.36f, 0.44f)
    if (list > 0f) {
        val rowH = dp(30f)
        val top = (size.height - rowH * 3) / 2
        val ticked = step(t, 0.2f, 0.24f)
        names.forEachIndexed { i, name ->
            val y = top + i * rowH + rowH / 2
            val box = Offset(dp(28f), y)
            val on = i < 2 || ticked > 0.5f
            drawRoundRect(
                if (on) ink.accent.copy(alpha = list) else ink.muted.copy(alpha = list),
                Offset(box.x - dp(7f), box.y - dp(7f)), Size(dp(14f), dp(14f)), CornerRadius(dp(3f)),
                style = if (on) androidx.compose.ui.graphics.drawscope.Fill else Stroke(dp(1.5f)),
            )
            label(name, Offset(dp(48f), y), ink, ink.text, 12f, centred = false, alpha = list)
        }
        finger(Offset(dp(28f), top + rowH * 2.5f), ink, step(t, 0.08f, 0.14f) * (1 - step(t, 0.28f, 0.34f)))
    }
    // ...then its tile is above the wall, and held and dragged to the front.
    val row = step(t, 0.4f, 0.48f)
    if (row <= 0f) return
    val gap = dp(10f)
    val w = (size.width - gap * 4) / 3
    val h = w * 0.62f
    val y = (size.height - h) / 2
    fun slot(i: Float) = Offset(gap + i * (w + gap), y)
    val carry = step(t, 0.6f, 0.82f)
    val lift = step(t, 0.5f, 0.58f) * (1 - step(t, 0.84f, 0.9f))
    val positions = listOf(mix(0f, 1f, step(t, 0.62f, 0.72f)), mix(1f, 2f, step(t, 0.7f, 0.8f)), mix(2f, 0f, carry))
    for (i in listOf(0, 1, 2)) {
        val o = slot(positions[i])
        val scale = (if (i == 2) 1f + 0.08f * lift else 1f) * (if (i == 2) mix(0.6f, 1f, row) else 1f)
        val alpha = if (i == 2) row else 1f
        withTransform({ scale(scale, scale, Offset(o.x + w / 2, o.y + h / 2)) }) {
            photo(Rect(o, Size(w, h)), i * 3 + 1, alpha = alpha, corner = 10f)
            label(names[i], Offset(o.x + dp(8f), o.y + h - dp(12f)), ink, Color.White, 10f, centred = false, alpha = alpha)
        }
    }
    val held = slot(positions[2]) + Offset(w / 2, h / 2)
    finger(held, ink, step(t, 0.46f, 0.52f) * (1 - step(t, 0.86f, 0.92f)), hold = if (t < 0.6f) lift else 0f)
}

// ---- Viewing ----

private fun DrawScope.switch(t: Float, ink: Ink) {
    drawRect(Color.Black, size = size)
    val w = size.width * 0.5f
    val h = size.height * 0.55f
    scene(Rect(Offset((size.width - w) / 2, dp(26f)), Size(w, h)), 4)
    // The button at the top right that swaps between the photo wall and the photo's folder.
    val button = Offset(size.width - dp(22f), dp(16f))
    val tapped = step(t, 0.2f, 0.25f)
    drawCircle(Color.White.copy(alpha = 0.15f + 0.25f * tapped * (1 - step(t, 0.3f, 0.4f))), radius = dp(12f), center = button)
    val flip = step(t, 0.25f, 0.45f)
    drawRect(Color.White, Offset(button.x - dp(6f), button.y - dp(5f)), Size(dp(12f), dp(10f) * (1 - flip) + dp(1f)))
    // The two places, with the highlight crossing from one to the other.
    val y = dp(26f) + h + dp(18f)
    val from = Offset(size.width / 2 - dp(56f), y)
    val to = Offset(size.width / 2 + dp(56f), y)
    val shown = step(t, 0.25f, 0.32f) * (1 - step(t, 0.85f, 0.95f))
    val slide = step(t, 0.35f, 0.7f)
    val hl = mix(from, to, slide)
    drawRoundRect(Color.White.copy(alpha = shown), Offset(hl.x - dp(44f), y - dp(11f)), Size(dp(88f), dp(22f)), CornerRadius(dp(11f)))
    label(ink.labels.getValue("wall"), from, ink, lerp(Color.Black, Color.White, slide), 11f, alpha = shown)
    label("→", Offset(size.width / 2, y), ink, Color.White, 12f, alpha = shown)
    label(ink.labels.getValue("folder"), to, ink, lerp(Color.White, Color.Black, slide), 11f, alpha = shown)
    // The strip of neighbouring photos below, swapped for the folder's.
    val stripY = y + dp(24f)
    val change = step(t, 0.3f, 0.55f)
    for (i in -3..3) {
        val x = size.width / 2 + i * dp(26f) - dp(11f)
        photo(Rect(Offset(x, stripY), Size(dp(22f), dp(22f))), if (change < 0.5f) i + 6 else i * 2, alpha = 0.4f + 0.6f * kotlin.math.abs(change - 0.5f) * 2f, corner = 3f)
    }
    finger(button, ink, step(t, 0.08f, 0.16f) * (1 - step(t, 0.28f, 0.36f)))
}

// ---- Sending at full resolution ----

private fun DrawScope.share(t: Float, ink: Ink) {
    val w = size.width * 0.42f
    val h = size.height * 0.6f
    scene(Rect(Offset(dp(14f), dp(14f)), Size(w, h)), 2)
    pill("48 MP", Offset(dp(14f) + w / 2, dp(14f) + h + dp(14f)), ink, ink.tile, ink.muted, sizeSp = 11f)
    // The share sheet rises with its two choices; the full-resolution one is taken.
    val sheet = step(t, 0.1f, 0.3f) * (1 - step(t, 0.55f, 0.65f))
    val sheetTop = size.height - dp(70f) * sheet
    drawRoundRect(ink.tile, Offset(0f, sheetTop), Size(size.width, dp(80f)), CornerRadius(dp(14f)))
    val row1 = Offset(size.width / 2, sheetTop + dp(20f))
    val tap = step(t, 0.38f, 0.45f) * (1 - step(t, 0.5f, 0.56f))
    drawRoundRect(ink.accent.copy(alpha = 0.25f * tap), Offset(dp(8f), row1.y - dp(13f)), Size(size.width - dp(16f), dp(26f)), CornerRadius(dp(8f)))
    label(ink.labels.getValue("fullres"), row1, ink, ink.text, 12f, alpha = sheet)
    label(ink.labels.getValue("stock"), row1 + Offset(0f, dp(26f)), ink, ink.muted, 12f, alpha = sheet)
    finger(row1 + Offset(dp(40f), 0f), ink, step(t, 0.3f, 0.36f) * (1 - step(t, 0.5f, 0.56f)))
    // It arrives in the chat untouched.
    val arrive = step(t, 0.62f, 0.8f) * (1 - step(t, 0.93f, 0.99f))
    val bw = size.width * 0.34f
    val bx = size.width - bw - dp(16f)
    val by = dp(16f) + (1 - arrive) * dp(20f)
    drawRoundRect(Color(0xFF95EC69).copy(alpha = arrive), Offset(bx - dp(6f), by - dp(6f)), Size(bw + dp(12f), bw * 1.3f + dp(30f)), CornerRadius(dp(10f)))
    scene(Rect(Offset(bx, by), Size(bw, bw * 1.3f)), 2, alpha = arrive)
    label(ink.labels.getValue("original") + " · 48 MP", Offset(bx + bw / 2, by + bw * 1.3f + dp(12f)), ink, Color.Black, 10f, alpha = arrive)
}

private fun DrawScope.frontRow(t: Float, ink: Ink) {
    label(ink.labels.getValue("wechat"), Offset(dp(12f), dp(14f)), ink, ink.muted, 11f, centred = false)
    val rects = cells(4f, 12, dp(28f), dp(4f))
    // The chosen photo starts well down the picker, and once in the Front Row it is first.
    val chosen = 6
    val move = step(t, 0.35f, 0.7f)
    val back = step(t, 0.9f, 0.98f)
    val order = (0 until 12).filter { it != chosen }
    order.forEachIndexed { slot, i ->
        val from = rects[if (slot >= chosen) slot + 1 else slot]
        val to = rects[slot + 1]
        val p = move * (1 - back)
        photo(Rect(mix(from.topLeft, to.topLeft, p), from.size), i + 1, alpha = 0.55f, corner = 3f)
    }
    val p = move * (1 - back)
    val r = Rect(mix(rects[chosen].topLeft, rects[0].topLeft, p), rects[0].size)
    photo(r, 1, corner = 3f)
    drawRoundRect(ink.accent, r.topLeft, r.size, CornerRadius(dp(3f)), style = Stroke(dp(2.5f) * step(t, 0.25f, 0.3f) * (1 - back)))
    // The Front Row button being tapped.
    val button = Offset(size.width - dp(20f), dp(14f))
    drawCircle(ink.accent.copy(alpha = 0.2f + 0.5f * step(t, 0.2f, 0.25f) * (1 - step(t, 0.3f, 0.4f))), radius = dp(11f), center = button)
    drawLine(ink.text, button + Offset(-dp(5f), -dp(6f)), button + Offset(dp(5f), -dp(6f)), dp(2f))
    drawLine(ink.text, button + Offset(0f, -dp(3f)), button + Offset(0f, dp(6f)), dp(2f))
    drawLine(ink.text, button + Offset(0f, -dp(3f)), button + Offset(-dp(4f), dp(1f)), dp(2f))
    drawLine(ink.text, button + Offset(0f, -dp(3f)), button + Offset(dp(4f), dp(1f)), dp(2f))
    finger(button, ink, step(t, 0.08f, 0.16f) * (1 - step(t, 0.28f, 0.36f)))
}

// ---- Tools ----

private fun DrawScope.sort(t: Float, ink: Ink) {
    val red = Color(0xFFD93025)
    val green = Color(0xFF1E8E3E)
    drawRect(red.copy(alpha = 0.25f + 0.5f * (step(t, 0.12f, 0.2f) * (1 - step(t, 0.28f, 0.34f)))), size = Size(size.width, dp(14f)))
    drawRect(green.copy(alpha = 0.25f + 0.5f * (step(t, 0.45f, 0.52f) * (1 - step(t, 0.6f, 0.66f)))), size = Size(dp(14f), size.height))
    // Folders along the bottom; the third one receives the last photo.
    val trayY = size.height - dp(40f)
    val tile = dp(32f)
    val trayX = (0 until 3).map { size.width / 2 + (it - 1) * (tile + dp(12f)) - tile / 2 }
    trayX.forEachIndexed { i, x ->
        val hit = if (i == 2) step(t, 0.86f, 0.9f) * (1 - step(t, 0.94f, 0.98f)) else 0f
        drawRoundRect(swatch(i + 5).copy(alpha = 0.7f), Offset(x, trayY), Size(tile, tile), CornerRadius(dp(6f)))
        if (hit > 0f) drawRoundRect(ink.accent, Offset(x, trayY), Size(tile, tile), CornerRadius(dp(6f)), style = Stroke(dp(3f) * hit))
    }
    val cardW = size.width * 0.34f
    val cardH = (trayY - dp(30f)) * 0.95f
    val home = Offset(size.width / 2, dp(22f) + cardH / 2)
    // Three photos: rejected upward, kept to the left, filed into a folder.
    data class Go(val start: Float, val to: Offset, val shrink: Boolean)
    val goes = listOf(
        Go(0.1f, Offset(size.width / 2, -cardH), false),
        Go(0.42f, Offset(-cardW, home.y), false),
        Go(0.74f, Offset(trayX[2] + tile / 2, trayY + tile / 2), true),
    )
    goes.forEachIndexed { i, go ->
        val appear = if (i == 0) 1f else step(t, goes[i - 1].start + 0.14f, goes[i - 1].start + 0.2f)
        val p = step(t, go.start + 0.04f, go.start + 0.16f)
        if (appear <= 0f || p >= 1f) return@forEachIndexed
        val c = mix(home, go.to, p)
        val s = if (go.shrink) mix(1f, 0.15f, p) else 1f
        scene(Rect(Offset(c.x - cardW * s / 2, c.y - cardH * s / 2), Size(cardW * s, cardH * s)), i * 2 + 1, alpha = appear)
        val fingerAt = if (i == 2) Offset(trayX[2] + tile / 2, trayY + tile / 2) else mix(home, mix(home, go.to, 0.4f), p)
        finger(fingerAt, ink, step(t, go.start - 0.04f, go.start) * (1 - step(t, go.start + 0.14f, go.start + 0.18f)))
    }
}

private fun DrawScope.similar(t: Float, ink: Ink) {
    val gap = dp(10f)
    val w = (size.width - gap * 4) / 3
    val y = dp(16f)
    val rects = (0 until 3).map { Rect(Offset(gap + it * (w + gap), y), Size(w, w)) }
    val reset = step(t, 0.92f, 0.98f)
    val going = listOf(0f, step(t, 0.25f, 0.3f), step(t, 0.5f, 0.55f)).map { it * (1 - reset) }
    rects.forEachIndexed { i, r ->
        scene(r, 0, alpha = 1f - 0.6f * going[i])
        if (going[i] < 0.5f) check(Offset(r.right - dp(12f), r.top + dp(12f)), ink)
        else {
            drawCircle(Color(0xFFD93025), radius = dp(8f), center = Offset(r.right - dp(12f), r.top + dp(12f)))
            drawLine(Color.White, Offset(r.right - dp(15f), r.top + dp(12f)), Offset(r.right - dp(9f), r.top + dp(12f)), dp(2f))
        }
    }
    val trashAt = Offset(size.width - dp(56f), y + w + dp(26f))
    val ready = step(t, 0.55f, 0.62f) * (1 - reset)
    pill(ink.labels.getValue("trash2"), trashAt, ink, Color(0xFFD93025).copy(alpha = 0.2f + 0.8f * ready), if (ready > 0.5f) Color.White else ink.muted, sizeSp = 11f)
    val at = if (t < 0.4f) rects[1].center else rects[2].center
    finger(at, ink, (step(t, 0.15f, 0.22f) * (1 - step(t, 0.32f, 0.38f))) + (step(t, 0.42f, 0.48f) * (1 - step(t, 0.58f, 0.64f))))
}

private fun DrawScope.compare(t: Float, ink: Ink) {
    drawRect(Color.Black, size = size)
    val zoom = 1f + 1.2f * (step(t, 0.2f, 0.55f) * (1 - step(t, 0.8f, 0.95f)))
    val gap = dp(3f)
    val paneH = (size.height - gap) / 2
    val panes = listOf(Rect(Offset(0f, 0f), Size(size.width, paneH)), Rect(Offset(0f, paneH + gap), Size(size.width, paneH)))
    val colours = listOf(Color(0xFF64B5F6), Color(0xFFFFB74D))
    panes.forEachIndexed { i, pane ->
        clipRect(pane.left, pane.top, pane.right, pane.bottom) {
            val w = pane.width * 0.5f * zoom
            val h = pane.height * 0.95f * zoom
            val focus = Offset(pane.left + pane.width * 0.5f, pane.top + pane.height * 0.45f)
            scene(Rect(Offset(focus.x - w * 0.55f, focus.y - h * 0.4f), Size(w, h)), i + 2)
        }
        drawCircle(colours[i], radius = dp(9f), center = Offset(pane.left + dp(16f), pane.top + dp(16f)))
        label(if (i == 0) "A" else "B", Offset(pane.left + dp(16f), pane.top + dp(16f)), ink, Color.Black, 10f)
    }
    val c = panes[0].center
    val spread = mix(dp(14f), dp(48f), step(t, 0.2f, 0.55f))
    val shown = step(t, 0.1f, 0.2f) * (1 - step(t, 0.58f, 0.66f))
    finger(c + Offset(-spread, -spread * 0.4f), ink, shown)
    finger(c + Offset(spread, spread * 0.4f), ink, shown)
}
