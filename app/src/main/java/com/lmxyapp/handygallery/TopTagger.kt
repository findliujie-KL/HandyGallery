package com.lmxyapp.handygallery

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.system.Os
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stamps a photo with an `frs_YYYYMMDDHHmm` marker (local time) in the EXIF UserComment. Rewriting
 * the file bumps its modification time, which is what floats it to the top of WeChat's image picker.
 *
 * Callers must already hold write access to the uri (MediaStore.createWriteRequest).
 */
object TopTagger {
    private val exifWritable = setOf("image/jpeg", "image/png", "image/webp")
    private val stampFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val markerPattern = Regex("""frs_\d{12}""")

    fun marker(time: Long): String = "frs_" + stampFormat.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

    /** The marker inside an EXIF UserComment, if it carries one. */
    fun find(comment: String?): String? = comment?.let { markerPattern.find(it)?.value }

    /** When [marker] was written, in local time. */
    fun timeOf(marker: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(marker.removePrefix("frs_"), stampFormat) }.getOrNull()

    /** Returns the timestamp written, or null if the file could not be touched at all. */
    fun tag(context: Context, photo: Photo): Long? {
        val now = System.currentTimeMillis()
        val ok = (photo.mime in exifWritable && attempt(context, photo.uri) { writeExif(context, it, marker(now)) }) ||
            // Formats ExifInterface can't write (HEIC, GIF...) still get their modification time bumped.
            attempt(context, photo.uri) { touch(context, it) }
        if (!ok) return null
        photo.path?.let { MediaScannerConnection.scanFile(context, arrayOf(it), null, null) }
        return now
    }

    /**
     * Undoes [tag]: removes the marker and, when known, puts back the modification time the file had
     * before ([originalModified], seconds) so it also leaves the "recent" position prep gave it.
     */
    fun untag(context: Context, photo: Photo, originalModified: Long) {
        if (photo.mime in exifWritable) attempt(context, photo.uri) { clearExif(context, it) }
        val path = photo.path ?: return
        // Works through the plain file path because the write request also covers path access.
        if (originalModified > 0) runCatching { File(path).setLastModified(originalModified * 1000) }
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    }

    private fun clearExif(context: Context, uri: Uri) {
        context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
            val marker = find(comment) ?: return
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, comment!!.replace(marker, "").trim().ifEmpty { null })
            exif.saveAttributes()
        }
    }

    private fun attempt(context: Context, uri: Uri, block: (Uri) -> Unit): Boolean =
        runCatching { Media.withBestUri(context, uri, block) }.isSuccess

    private fun writeExif(context: Context, uri: Uri, value: String) {
        context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, value)
            exif.saveAttributes()
        }
    }

    private fun touch(context: Context, uri: Uri) {
        context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
            val first = ByteArray(1)
            if (Os.pread(pfd.fileDescriptor, first, 0, 1, 0) == 1) Os.pwrite(pfd.fileDescriptor, first, 0, 1, 0)
        }
    }
}
