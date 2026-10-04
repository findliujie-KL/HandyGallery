package com.findliujie.fullresshare.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.findliujie.fullresshare.Media
import androidx.compose.ui.res.stringResource
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.R
import com.findliujie.fullresshare.PhotoSort
import com.findliujie.fullresshare.TopTagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

private val infoTags = arrayOf(
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_USER_COMMENT,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(photo: Photo, onBack: () -> Unit) {
    val context = LocalContext.current
    val exif by produceState(emptyMap<String, String>(), photo.cacheKey) {
        value = withContext(Dispatchers.IO) { Media.readExif(context, photo.uri, infoTags) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Entry(photo.bucketName, photo.path.orEmpty())
            Entry(
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(photo.time(PhotoSort.Taken))),
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(photo.time(PhotoSort.Taken))),
            )
            Entry(
                photo.name,
                listOfNotNull(
                    if (photo.width > 0) String.format(Locale.US, "%.1fMP", photo.width.toLong() * photo.height / 1e6) else null,
                    if (photo.width > 0) "${photo.width} x ${photo.height}" else null,
                    Formatter.formatShortFileSize(context, photo.size),
                ).joinToString(" • "),
            )
            val camera = listOfNotNull(exif[ExifInterface.TAG_MAKE], exif[ExifInterface.TAG_MODEL]).joinToString(" ")
            val exposure = listOfNotNull(
                exif[ExifInterface.TAG_F_NUMBER]?.let { "f/$it" },
                exif[ExifInterface.TAG_EXPOSURE_TIME]?.toDoubleOrNull()?.let { if (it in 0.0..1.0 && it > 0) "1/${Math.round(1 / it)}" else "${it}s" },
                exif[ExifInterface.TAG_FOCAL_LENGTH]?.let { value ->
                    val parts = value.split('/').mapNotNull { it.toDoubleOrNull() }
                    if (parts.size == 2 && parts[1] != 0.0) String.format(Locale.US, "%.2fmm", parts[0] / parts[1]) else null
                },
                exif[ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY]?.let { "ISO$it" },
            ).joinToString(" • ")
            if (camera.isNotBlank() || exposure.isNotBlank()) Entry(camera.ifBlank { stringResource(R.string.camera) }, exposure)
            TopTagger.find(exif[ExifInterface.TAG_USER_COMMENT])?.let { marker ->
                val stamped = TopTagger.timeOf(marker)?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                Entry(stringResource(R.string.prepped_for), listOfNotNull(stamped, marker).joinToString("\n"))
            }
        }
    }
}

@Composable
private fun Entry(title: String, detail: String) {
    Column(Modifier.padding(vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
