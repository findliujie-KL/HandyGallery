package com.lmxyapp.handygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Store
import kotlinx.coroutines.delay

/**
 * One-time tips: a small bubble pointing at a control the first time its screen is shown. Each is
 * shown once; Settings can bring them all back. Only one is up at a time: the first on screen that
 * hasn't been seen, then the next once that one is dismissed.
 */
object Tips {
    /** Set when the activity starts; without it no tips are shown. */
    var store: Store? = null

    /** Tips whose controls are on screen now, in the order they appeared. */
    internal val waiting = mutableStateListOf<String>()

    const val WALL = "wall"
    const val TOOLS = "tools"
    const val SHORTCUTS = "shortcuts"
    const val FOLDER_ORDER = "folderOrder"
    const val SWITCH = "switch"
    const val FRONT_ROW = "frontRow"
    const val SAVE = "save"
}

/** False under a screen that is covered by another (the viewer is drawn over the screen it came from). */
val LocalTipsAllowed = compositionLocalOf { true }

/** Wraps [content] (the control the tip is about) and, if it is this tip's turn, points a bubble at it. */
@Composable
fun TipAnchor(id: String, text: Int, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val store = Tips.store
    // Not before the welcome pages are done, and not on a screen something else is covering.
    val unseen = store != null && store.welcomed && enabled && LocalTipsAllowed.current && id !in store.seenTips
    DisposableEffect(id, unseen) {
        if (unseen && id !in Tips.waiting) Tips.waiting += id
        onDispose { Tips.waiting.remove(id) }
    }
    val turn = unseen && Tips.waiting.firstOrNull { it !in store!!.seenTips } == id
    // A moment's pause, so the bubble doesn't arrive while the screen is still sliding in.
    var ready by remember(id) { mutableStateOf(false) }
    LaunchedEffect(turn) {
        ready = false
        if (turn) {
            delay(700)
            ready = true
        }
    }
    Box(modifier) {
        content()
        if (turn && ready) TipBubble(stringResource(text)) { store!!.sawTip(id) }
    }
}

@Composable
private fun TipBubble(text: String, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.roundToPx() }
    val gap = with(density) { 6.dp.roundToPx() }
    // Where along its edge the arrow sits, and whether the bubble is below the control or above it;
    // worked out when the bubble is placed, and only read when it is drawn.
    val arrowX = remember { mutableFloatStateOf(0f) }
    var below by remember { mutableStateOf(true) }
    val provider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val fitsBelow = anchorBounds.bottom + gap + popupContentSize.height <= windowSize.height - margin
                below = fitsBelow
                arrowX.floatValue = (anchorBounds.center.x - x).toFloat()
                val y = if (fitsBelow) anchorBounds.bottom + gap else anchorBounds.top - gap - popupContentSize.height
                return IntOffset(x, y)
            }
        }
    }
    val fill = MaterialTheme.colorScheme.inverseSurface
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .drawBehind {
                    val arrow = 8.dp.toPx()
                    val ax = arrowX.floatValue.coerceIn(arrow * 2, size.width - arrow * 2)
                    val path = Path().apply {
                        if (below) {
                            moveTo(ax - arrow, arrow)
                            lineTo(ax, 0f)
                            lineTo(ax + arrow, arrow)
                        } else {
                            moveTo(ax - arrow, size.height - arrow)
                            lineTo(ax, size.height)
                            lineTo(ax + arrow, size.height - arrow)
                        }
                        close()
                    }
                    drawPath(path, fill)
                }
                .padding(top = if (below) 7.dp else 0.dp, bottom = if (below) 0.dp else 7.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fill)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
            Text(
                stringResource(R.string.tip_got_it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
