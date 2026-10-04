package com.lmxyapp.handygallery.ui

import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Source
import com.lmxyapp.handygallery.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B8754),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD5F0E1),
    onSecondaryContainer = Color(0xFF0B3D26),
    background = Color.White,
    surface = Color.White,
    surfaceContainer = Color(0xFFF4F7F5),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD3A0),
    onPrimary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF1F4A36),
    onSecondaryContainer = Color(0xFFD5F0E1),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceContainer = Color(0xFF1C1F1D),
)

@Composable
fun FrsTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) DarkColors else LightColors, content = content)
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun Source.title(): String = when (this) {
    is Source.Named -> stringResource(title)
    is Source.Folder -> name
}

@Composable
fun BusyCard(message: String) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(message)
        }
    }
}

@Composable
fun BusyDialog(message: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        BusyCard(message)
    }
}

/** The two ways to share, offered wherever there is a Share action. */
@Composable
fun ShareMenu(expanded: Boolean, onDismiss: () -> Unit, onFullRes: () -> Unit, onStock: () -> Unit) {
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.share_full_res)) }, onClick = { onDismiss(); onFullRes() })
        DropdownMenuItem(text = { Text(stringResource(R.string.share_stock)) }, onClick = { onDismiss(); onStock() })
    }
}

@Composable
fun ShareIconButton(onFullRes: () -> Unit, onStock: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share)) }
        ShareMenu(open, { open = false }, onFullRes, onStock)
    }
}

/**
 * Puts photos in the front row. When they are [alreadyThere] it asks which is wanted: move them to
 * the front again, or take them out.
 */
@Composable
fun FrontRowButton(alreadyThere: Boolean, onPrep: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { if (alreadyThere) open = true else onPrep() }) {
            Icon(
                FrsIcons.Prep,
                contentDescription = stringResource(R.string.prep),
                tint = if (alreadyThere) FrontRowTint else LocalContentColor.current,
            )
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.front_row_again)) }, onClick = { open = false; onPrep() })
            DropdownMenuItem(text = { Text(stringResource(R.string.undo_prep)) }, onClick = { open = false; onRemove() })
        }
    }
}

private val FrontRowTint = Color(0xFF2BBE76)

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

private val thumbCache = object : LruCache<String, ImageBitmap>(160 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Whichever thumbnail of [photo] is already in memory, larger first: an instant stand-in while the real image loads. */
fun cachedThumb(photo: Photo): ImageBitmap? =
    thumbCache.get("${photo.cacheKey}@1024") ?: thumbCache.get("${photo.cacheKey}@512")

@Composable
fun Thumb(photo: Photo, modifier: Modifier = Modifier, size: Int = 512) {
    val context = LocalContext.current
    val key = "${photo.cacheKey}@$size"
    var bitmap by remember(key) { mutableStateOf(thumbCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(photo.uri, Size(size, size), null).asImageBitmap() }
                    .getOrNull()
                    ?.also { thumbCache.put(key, it) }
            }
        }
    }
    Box(modifier.background(Color.Gray.copy(alpha = 0.2f))) {
        bitmap?.let { Image(it, contentDescription = photo.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** For screens drawn on black (the editor), whatever the system theme. */
@Composable
fun DarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
