package com.findliujie.fullresshare

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PhotoSort(@StringRes val label: Int) { Taken(R.string.sort_taken), Added(R.string.sort_added), Modified(R.string.sort_modified) }

enum class FolderSort(@StringRes val label: Int) {
    Recent(R.string.fsort_recent), NameAsc(R.string.fsort_name_asc), NameDesc(R.string.fsort_name_desc), Size(R.string.fsort_size)
}

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    /** Milliseconds, 0 when the file carries no capture time. */
    val dateTaken: Long,
    /** Seconds. */
    val dateAdded: Long,
    /** Seconds. */
    val dateModified: Long,
    val path: String?,
    val bucketId: Long,
    val bucketName: String,
    val size: Long,
    /** As displayed, i.e. after applying EXIF orientation. */
    val width: Int,
    val height: Int,
) {
    /** Changes whenever the pixels may have, so caches don't serve a stale image after an in-place edit. */
    val cacheKey: String get() = "$id-$dateModified"

    val isScreenshot: Boolean
        get() = bucketName.equals("Screenshots", ignoreCase = true) || path?.contains("/Screenshots/", ignoreCase = true) == true

    fun time(sort: PhotoSort): Long = when (sort) {
        // Fall back to "added", not "modified": prepping a photo rewrites it and must not move it in the timeline.
        PhotoSort.Taken -> if (dateTaken > 0) dateTaken else dateAdded * 1000
        PhotoSort.Added -> dateAdded * 1000
        PhotoSort.Modified -> dateModified * 1000
    }
}

data class Folder(val id: Long, val name: String, val cover: Photo, val count: Int, val size: Long, val latest: Long)

object Media {
    val permissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_IMAGES)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }.toTypedArray()

    private fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun canRead(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 34 -> has(context, Manifest.permission.READ_MEDIA_IMAGES) ||
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> has(context, Manifest.permission.READ_MEDIA_IMAGES)
        else -> has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** MediaStore redacts GPS EXIF unless the original is explicitly requested. */
    private fun original(context: Context, uri: Uri): Uri? =
        if (uri.authority == MediaStore.AUTHORITY && has(context, Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            MediaStore.setRequireOriginal(uri)
        } else null

    /** Runs [block] against the unredacted uri when possible, falling back to the plain one. */
    fun <T> withBestUri(context: Context, uri: Uri, block: (Uri) -> T): T {
        original(context, uri)?.let { o -> runCatching { return block(o) } }
        return block(uri)
    }

    fun openOriginal(context: Context, uri: Uri): InputStream =
        withBestUri(context, uri) { context.contentResolver.openInputStream(it) ?: error("Cannot read $uri") }

    suspend fun load(context: Context, trashed: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        if (!canRead(context)) return@withContext emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        val out = ArrayList<Photo>()
        runCatching {
            context.contentResolver.query(collection, projection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    // WIDTH/HEIGHT describe the stored pixels; a 90° or 270° EXIF orientation means it displays sideways to that.
                    val sideways = c.getInt(12) % 180 != 0
                    out += Photo(
                        id = id,
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        name = c.getString(1) ?: id.toString(),
                        mime = c.getString(2) ?: "image/*",
                        dateTaken = c.getLong(3),
                        dateAdded = c.getLong(4),
                        dateModified = c.getLong(5),
                        path = c.getString(6),
                        bucketId = c.getLong(7),
                        bucketName = c.getString(8) ?: "Internal storage",
                        size = c.getLong(9),
                        width = c.getInt(if (sideways) 11 else 10),
                        height = c.getInt(if (sideways) 10 else 11),
                    )
                }
            }
        }
        out
    }

    fun folders(photos: List<Photo>, sort: FolderSort): List<Folder> {
        val folders = photos.groupBy { it.bucketId }.map { (id, items) ->
            val cover = items.maxBy { it.time(PhotoSort.Taken) }
            Folder(id, cover.bucketName, cover, items.size, items.sumOf { it.size }, cover.time(PhotoSort.Taken))
        }
        return when (sort) {
            FolderSort.Recent -> folders.sortedByDescending { it.latest }
            FolderSort.NameAsc -> folders.sortedBy { it.name.lowercase() }
            FolderSort.NameDesc -> folders.sortedByDescending { it.name.lowercase() }
            FolderSort.Size -> folders.sortedByDescending { it.size }
        }
    }

    /** Decodes with EXIF orientation applied, downsampled to fit [maxEdge] and [maxPixels]. */
    fun decode(
        context: Context,
        uri: Uri,
        maxEdge: Int = Int.MAX_VALUE,
        maxPixels: Long = Long.MAX_VALUE,
        mutable: Boolean = false,
    ): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        if (mutable) {
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            d.isMutableRequired = true
        }
        val w = info.size.width
        val h = info.size.height
        val scale = min(
            maxEdge.toFloat() / max(w, h),
            sqrt(maxPixels.toDouble() / (w.toLong() * h)).toFloat(),
        )
        if (scale < 1f) {
            d.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }
    }

    // Re-encoding drops all metadata; these are the tags worth carrying over to an edited image.
    private val keptTags = arrayOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_USER_COMMENT,
    )

    fun readExif(context: Context, uri: Uri, tags: Array<String> = keptTags): Map<String, String> = runCatching {
        val exif = openOriginal(context, uri).use { ExifInterface(it) }
        tags.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeExif(context: Context, uri: Uri, tags: Map<String, String>) {
        if (tags.isEmpty()) return
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                ExifInterface(pfd.fileDescriptor).apply {
                    tags.forEach { (tag, value) -> setAttribute(tag, value) }
                    saveAttributes()
                }
            }
        }
    }

    /** Describes an image another app handed us to edit; it may not be in MediaStore at all. */
    fun photoFor(context: Context, uri: Uri, type: String?): Photo {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment ?: "image"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    size = c.getLong(1)
                }
            }
        }
        return Photo(
            id = -1, uri = uri, name = name, mime = resolver.getType(uri) ?: type ?: "image/*",
            dateTaken = 0, dateAdded = 0, dateModified = 0, path = null,
            bucketId = 0, bucketName = "", size = size, width = 0, height = 0,
        )
    }

    /** Saves [bitmap] as a new JPEG next to the app's other edits, keeping [source]'s key metadata. */
    suspend fun saveCopy(context: Context, source: Photo, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val exif = readExif(context, source.uri)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${source.name.substringBeforeLast('.')}_edit_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FullResShare")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Cannot create image")
        try {
            resolver.openOutputStream(target)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            writeExif(context, target, exif)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
        target
    }

    fun canReplace(photo: Photo) = photo.mime in setOf("image/jpeg", "image/png", "image/webp")

    /** Overwrites [photo] with [bitmap] in its own format. Caller must already hold write access. */
    suspend fun replaceOriginal(context: Context, photo: Photo, bitmap: Bitmap): Unit = withContext(Dispatchers.IO) {
        val format = when (photo.mime) {
            "image/jpeg" -> Bitmap.CompressFormat.JPEG
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> error("Replace supports JPEG, PNG and WebP only")
        }
        val exif = readExif(context, photo.uri)
        context.contentResolver.openOutputStream(photo.uri, "wt")!!.use { bitmap.compress(format, 95, it) }
        writeExif(context, photo.uri, exif)
    }
}
