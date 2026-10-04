package com.lmxyapp.handygallery.ui

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import java.text.NumberFormat
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

/** Everything read from the file itself for the details page; null or blank where the file doesn't say. */
private class Shot(
    val make: String?,
    val model: String?,
    val lens: String?,
    val software: String?,
    val aperture: Double?,
    val exposure: Double?,
    val iso: Int?,
    val focal: Double?,
    val focal35: Int?,
    val bias: Double?,
    val flash: Int?,
    val whiteBalance: Int?,
    val latLong: DoubleArray?,
    val altitude: Double?,
)

private fun readShot(context: Context, photo: Photo): Shot? = runCatching {
    val exif = Media.openOriginal(context, photo.uri).use { ExifInterface(it) }
    fun text(tag: String) = exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
    fun number(tag: String) = if (exif.hasAttribute(tag)) exif.getAttributeDouble(tag, 0.0) else null
    fun whole(tag: String) = if (exif.hasAttribute(tag)) exif.getAttributeInt(tag, 0) else null
    Shot(
        make = text(ExifInterface.TAG_MAKE),
        model = text(ExifInterface.TAG_MODEL),
        lens = text(ExifInterface.TAG_LENS_MODEL),
        software = text(ExifInterface.TAG_SOFTWARE),
        aperture = number(ExifInterface.TAG_F_NUMBER)?.takeIf { it > 0 },
        exposure = number(ExifInterface.TAG_EXPOSURE_TIME)?.takeIf { it > 0 },
        iso = whole(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.takeIf { it > 0 },
        focal = number(ExifInterface.TAG_FOCAL_LENGTH)?.takeIf { it > 0 },
        focal35 = whole(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.takeIf { it > 0 },
        bias = number(ExifInterface.TAG_EXPOSURE_BIAS_VALUE),
        flash = whole(ExifInterface.TAG_FLASH),
        whiteBalance = whole(ExifInterface.TAG_WHITE_BALANCE),
        latLong = exif.latLong,
        altitude = if (exif.hasAttribute(ExifInterface.TAG_GPS_ALTITUDE)) exif.getAltitude(0.0) else null,
    )
}.getOrNull()

/**
 * Full-screen page listing everything known about [photo], reached from the viewer's menu.
 * [frontRowSince] is when it was put in the front row, if it is there.
 */
@Composable
fun DetailsPage(photo: Photo, frontRowSince: Long?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val shot by produceState<Shot?>(null, photo.cacheKey) {
        value = withContext(Dispatchers.IO) { readShot(context, photo) }
    }
    val stamp = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM) }
    fun date(millis: Long) = if (millis > 0) stamp.format(Date(millis)) else null

    // The empty pointerInput stops touches reaching the photo underneath.
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.details), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            }
            // Values can be long-pressed and copied, which is mostly wanted for the path and coordinates.
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    Section(R.string.details_file)
                    Fact(R.string.d_name, photo.name)
                    Fact(R.string.d_folder, photo.bucketName)
                    Fact(R.string.d_path, photo.path)
                    Fact(R.string.d_type, photo.mime)
                    Fact(
                        R.string.d_size,
                        "${Formatter.formatFileSize(context, photo.size)} (${NumberFormat.getIntegerInstance().format(photo.size)} B)",
                    )
                    if (photo.width > 0 && photo.height > 0) Fact(
                        R.string.d_dimensions,
                        String.format(Locale.US, "%d × %d (%.1f MP)", photo.width, photo.height, photo.width.toLong() * photo.height / 1e6),
                    )

                    Section(R.string.details_dates)
                    Fact(R.string.sort_taken, date(photo.dateTaken))
                    Fact(R.string.sort_modified, date(photo.dateModified * 1000))
                    Fact(R.string.sort_added, date(photo.dateAdded * 1000))
                    Fact(R.string.prepped_for, frontRowSince?.let(::date))

                    val s = shot
                    if (s != null) {
                        if (listOf(s.make, s.model, s.lens, s.software).any { it != null }) {
                            Section(R.string.camera)
                            Fact(R.string.d_make, s.make)
                            Fact(R.string.d_model, s.model)
                            Fact(R.string.d_lens, s.lens)
                            Fact(R.string.d_software, s.software)
                        }
                        if (listOf(s.aperture, s.exposure, s.iso, s.focal).any { it != null }) {
                            Section(R.string.details_shooting)
                            Fact(R.string.d_aperture, s.aperture?.let { "f/${trim(it)}" })
                            Fact(R.string.d_shutter, s.exposure?.let { if (it < 1) "1/${Math.round(1 / it)} s" else "${trim(it)} s" })
                            Fact(R.string.d_iso, s.iso?.toString())
                            Fact(R.string.d_focal, s.focal?.let { "${trim(it)} mm" })
                            Fact(R.string.d_focal35, s.focal35?.let { "$it mm" })
                            Fact(R.string.d_bias, s.bias?.let { String.format(Locale.US, "%+.1f EV", it) })
                            // Bit 0 of the flash value says whether it fired.
                            Fact(R.string.d_flash, s.flash?.let { stringResource(if (it and 1 == 1) R.string.flash_fired else R.string.flash_off) })
                            Fact(R.string.d_white_balance, s.whiteBalance?.let { stringResource(if (it == 0) R.string.wb_auto else R.string.wb_manual) })
                        }
                        if (s.latLong != null) {
                            Section(R.string.details_location)
                            Fact(R.string.d_coordinates, String.format(Locale.US, "%.6f, %.6f", s.latLong[0], s.latLong[1]))
                            Fact(R.string.d_altitude, s.altitude?.let { String.format(Locale.US, "%.0f m", it) })
                        }
                    }
                }
            }
        }
    }
}

/** Drops a pointless ".0": 1.7 stays, 4.0 becomes 4. */
private fun trim(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

@Composable
private fun Section(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

/** One labelled value; left out entirely when there is nothing to show. */
@Composable
private fun Fact(@StringRes label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(132.dp).padding(end = 12.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
