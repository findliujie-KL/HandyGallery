package com.lmxyapp.handygallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.PhotoSort
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.TopTagger
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

/** A photo's details, laid out for the sheet that slides up over the viewer. */
@Composable
fun InfoContent(photo: Photo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exif by produceState(emptyMap<String, String>(), photo.cacheKey) {
        value = withContext(Dispatchers.IO) { Media.readExif(context, photo.uri, infoTags) }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Grab handle: hints that the sheet can be dragged back down.
        Box(
            Modifier
                .padding(top = 10.dp, bottom = 4.dp)
                .size(36.dp, 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally),
        )
        Text(stringResource(R.string.info), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
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
            // The marker text is an implementation detail; the user only needs to know when.
            Entry(stringResource(R.string.prepped_for), stamped.orEmpty())
        }
    }
}

@Composable
private fun Entry(title: String, detail: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
