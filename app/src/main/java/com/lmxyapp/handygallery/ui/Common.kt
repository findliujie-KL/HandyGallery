package com.lmxyapp.handygallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Source
import com.lmxyapp.handygallery.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
fun HgTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
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

/**
 * The two ways to share, offered wherever there is a Share action. Where a single photo is on show,
 * [onEditElsewhere] adds handing it to another editor.
 */
@Composable
fun ShareMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onFullRes: () -> Unit,
    onStock: () -> Unit,
    onEditElsewhere: (() -> Unit)? = null,
) {
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.share_full_res)) }, onClick = { onDismiss(); onFullRes() })
        DropdownMenuItem(text = { Text(stringResource(R.string.share_stock)) }, onClick = { onDismiss(); onStock() })
        if (onEditElsewhere != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.edit_elsewhere)) }, onClick = { onDismiss(); onEditElsewhere() })
        }
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
                HgIcons.Prep,
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

/**
 * Where the bin button currently on screen is, in root coordinates, so that photos being trashed or
 * deleted can fly into it. Whichever bin button is showing (the selection bar's or the viewer's) sets it.
 */
object TrashSpot {
    var centre by mutableStateOf(Offset.Unspecified)
}

/** Reports this element as the bin photos fly into, and makes it swell a little while [receiving] them. */
@Composable
fun Modifier.trashSpot(receiving: Boolean): Modifier {
    val swell by animateFloatAsState(if (receiving) 1.35f else 1f, label = "bin")
    return onGloballyPositioned { TrashSpot.centre = it.boundsInRoot().center }.graphicsLayer {
        scaleX = swell
        scaleY = swell
    }
}

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
    thumbCache.get("${photo.cacheKey}@1536") ?: thumbCache.get("${photo.cacheKey}@1024") ?: thumbCache.get("${photo.cacheKey}@512")

/**
 * The system's thumbnail when it is good enough, otherwise one decoded from the photo itself. For some
 * files (large exports from editing apps, mostly) the system returns the tiny preview embedded in the
 * file, which is a blur at any real size. Decoding a large photo is slow, so those results are kept on
 * disk (see [ThumbStore]) and only made once.
 */
private fun loadThumb(context: Context, photo: Photo, size: Int): ImageBitmap? {
    val name = "${photo.cacheKey}@$size"
    ThumbStore.read(context, name)?.let { return it.asImageBitmap() }
    val system = runCatching { context.contentResolver.loadThumbnail(photo.uri, Size(size, size), null) }.getOrNull()
    // What a proper thumbnail's longer side would be: the size asked for, or the photo's own if smaller.
    val expected = if (photo.width > 0 && photo.height > 0) minOf(size, maxOf(photo.width, photo.height)) else size
    if (system != null && maxOf(system.width, system.height) >= expected * 0.6f) return system.asImageBitmap()
    val own = runCatching { Media.decode(context, photo.uri, maxEdge = size) }.getOrNull() ?: return system?.asImageBitmap()
    ThumbStore.write(context, name, own)
    return own.asImageBitmap()
}

/**
 * Thumbnails this app had to decode itself, kept in the cache folder between launches. Names carry the
 * photo's modification time, so an edited photo gets a new entry and the old one ages out. The folder
 * is held to [LIMIT] by dropping the entries that have gone unused longest.
 */
private object ThumbStore {
    private const val LIMIT = 300L * 1024 * 1024
    private var pruned = false

    private fun dir(context: Context) = File(context.cacheDir, "thumbs")

    fun read(context: Context, name: String): Bitmap? {
        val file = File(dir(context), name)
        if (!file.exists()) return null
        // Marks it as recently used for the pruning below.
        file.setLastModified(System.currentTimeMillis())
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    fun write(context: Context, name: String, bitmap: Bitmap) {
        runCatching {
            val dir = dir(context).apply { mkdirs() }
            prune(dir)
            // Written under another name first, so a half-written file is never read back as a thumbnail.
            val partial = File(dir, "$name.part")
            // JPEG is quick to write and read; only images with transparency need a format that keeps it.
            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
            partial.outputStream().use { bitmap.compress(format, 88, it) }
            partial.renameTo(File(dir, name))
        }
    }

    /** Once per launch: trims the folder back under the limit, oldest use first. */
    @Synchronized
    private fun prune(dir: File) {
        if (pruned) return
        pruned = true
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= LIMIT) break
            total -= file.length()
            file.delete()
        }
    }
}

@Composable
fun Thumb(photo: Photo, modifier: Modifier = Modifier, size: Int = 512) {
    val context = LocalContext.current
    val key = "${photo.cacheKey}@$size"
    var bitmap by remember(key) { mutableStateOf(thumbCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { loadThumb(context, photo, size)?.also { thumbCache.put(key, it) } }
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
