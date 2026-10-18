package com.lmxyapp.handygallery

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.coroutines.resume

enum class PhotoSort(@StringRes val label: Int) { Taken(R.string.sort_taken), Added(R.string.sort_added), Modified(R.string.sort_modified) }

enum class FolderSort(@StringRes val label: Int) {
    Recent(R.string.fsort_recent), NameAsc(R.string.fsort_name_asc), NameDesc(R.string.fsort_name_desc), Size(R.string.fsort_size),

    /** The order the user arranged; added last so saved choices of the others keep their position. */
    Manual(R.string.fsort_manual),
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
    /** Seconds; for a photo in the trash, when the system will delete it for good. 0 otherwise. */
    val expires: Long = 0,
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
    /** Id of the Favourites entry on the Folders tab, which is a collection rather than a real folder. */
    const val FAVORITES_ID = Long.MIN_VALUE
    const val FRONT_ROW_ID = Long.MIN_VALUE + 1

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
            MediaStore.Images.Media.DATE_EXPIRES,
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
                        expires = c.getLong(13),
                    )
                }
            }
        }
        out
    }

    /**
     * Reorders [photos] (already newest first by [sort]) so that within each month a folder's photos sit
     * together. Folders come in the order of their newest photo that month, and keep time order inside.
     */
    fun byMonthAndFolder(photos: List<Photo>, sort: PhotoSort): List<Photo> {
        val zone = ZoneId.systemDefault()
        val out = ArrayList<Photo>(photos.size)
        var last: YearMonth? = null
        val buckets = LinkedHashMap<Long, MutableList<Photo>>()
        fun flush() {
            buckets.values.forEach(out::addAll)
            buckets.clear()
        }
        for (photo in photos) {
            val month = YearMonth.from(Instant.ofEpochMilli(photo.time(sort)).atZone(zone))
            if (month != last) {
                flush()
                last = month
            }
            buckets.getOrPut(photo.bucketId) { ArrayList() } += photo
        }
        flush()
        return out
    }

    /**
     * [order] is the user's arrangement of bucket ids, used by [FolderSort.Manual]. [extra] is an entry
     * that isn't a real folder (Favourites): it leads the list, except in manual order where it is
     * arranged like any other.
     */
    fun folders(photos: List<Photo>, sort: FolderSort, order: List<Long> = emptyList(), extras: List<Folder> = emptyList()): List<Folder> {
        val real = photos.groupBy { it.bucketId }.map { (id, items) ->
            val cover = items.maxBy { it.time(PhotoSort.Taken) }
            Folder(id, cover.bucketName, cover, items.size, items.sumOf { it.size }, cover.time(PhotoSort.Taken))
        }
        if (extras.isNotEmpty() && sort != FolderSort.Manual) return extras + folders(photos, sort, order)
        val folders = real + extras
        return when (sort) {
            FolderSort.Recent -> folders.sortedByDescending { it.latest }
            FolderSort.NameAsc -> folders.sortedBy { it.name.lowercase() }
            FolderSort.NameDesc -> folders.sortedByDescending { it.name.lowercase() }
            FolderSort.Size -> folders.sortedByDescending { it.size }
            FolderSort.Manual -> {
                val place = order.withIndex().associate { it.value to it.index }
                // Folders that appeared after the user arranged things go to the end, newest first.
                folders.sortedWith(compareBy<Folder> { place[it.id] ?: Int.MAX_VALUE }.thenByDescending { it.latest })
            }
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
        // For an image from the media library, its folder tells us whether it is a screenshot.
        var bucket = ""
        var path: String? = null
        if (uri.authority == MediaStore.AUTHORITY) runCatching {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    bucket = c.getString(0).orEmpty()
                    path = c.getString(1)
                }
            }
        }
        return Photo(
            id = -1, uri = uri, name = name, mime = resolver.getType(uri) ?: type ?: "image/*",
            dateTaken = 0, dateAdded = 0, dateModified = 0, path = path,
            bucketId = 0, bucketName = bucket, size = size, width = 0, height = 0,
        )
    }

    /**
     * Finds the media-library photos behind [uris] shared from another app. A media uri names its
     * photo directly; anything else (e.g. Google Photos' own uris) is matched by file name and size.
     */
    suspend fun resolve(context: Context, uris: List<Uri>): List<Photo> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val ids = HashSet<Long>()
        val named = ArrayList<Pair<String, Long>>()
        for (uri in uris) {
            val direct = if (uri.authority == MediaStore.AUTHORITY) uri.lastPathSegment?.toLongOrNull() else null
            if (direct != null) {
                ids += direct
                continue
            }
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { named += it to c.getLong(1) }
                }
            }
        }
        load(context, trashed = false).filter { photo -> photo.id in ids || named.any { it.first == photo.name && it.second == photo.size } }
    }

    /** Saves [bitmap] as a new JPEG next to the app's other edits, keeping [source]'s key metadata. */
    suspend fun saveCopy(context: Context, source: Photo, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val exif = readExif(context, source.uri)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${source.name.substringBeforeLast('.')}_edit_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HandyGallery")
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

    /** "Pictures/Trips/" for a photo stored there; null when it isn't on the phone's own storage. */
    fun relativeDir(photo: Photo): String? {
        val root = Environment.getExternalStorageDirectory().path + "/"
        val path = photo.path ?: return null
        if (!path.startsWith(root)) return null
        return path.removePrefix(root).substringBeforeLast('/', "") + "/"
    }

    /** The system only lets an app put pictures under these two top-level folders. */
    fun canHoldImages(dir: String) = dir.startsWith("DCIM/") || dir.startsWith("Pictures/")

    /**
     * Copies [photo] byte for byte into [dir] (a path like "Pictures/Trips/"). A name already taken
     * there gets a number added by the system. The copy keeps the original's modification time, so a
     * picture with no capture date of its own still sorts beside the original rather than under today.
     */
    suspend fun copyTo(context: Context, photo: Photo, dir: String): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, photo.name)
            put(MediaStore.Images.Media.MIME_TYPE, photo.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, dir)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Cannot create image")
        try {
            openOriginal(context, photo.uri).use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
        runCatching {
            resolver.query(target, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }?.let { path ->
                if (File(path).setLastModified(photo.dateModified * 1000)) MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        }
        target
    }

    /**
     * Moves [photo]'s file into [dir]; its id, and so its favourite and front-row status, stay the same.
     * Caller must already hold write access. If the name is taken there, a numbered one is tried.
     */
    fun moveTo(context: Context, photo: Photo, dir: String): Boolean {
        fun attempt(name: String?) = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, dir)
                if (name != null) put(MediaStore.Images.Media.DISPLAY_NAME, name)
            }
            context.contentResolver.update(photo.uri, values, null, null) > 0
        }.getOrDefault(false)
        if (attempt(null)) return true
        val stem = photo.name.substringBeforeLast('.')
        val extension = photo.name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return (1..5).any { attempt("$stem ($it)$extension") }
    }

    fun canReplace(photo: Photo) = photo.mime in setOf("image/jpeg", "image/png", "image/webp")

    /**
     * Overwrites [photo] with [bitmap] in its own format. Caller must already hold write access. A photo
     * in MediaStore is first kept as it was (see [Originals]), so the edit can be reverted.
     */
    suspend fun replaceOriginal(context: Context, photo: Photo, bitmap: Bitmap): Unit = withContext(Dispatchers.IO) {
        val format = when (photo.mime) {
            "image/jpeg" -> Bitmap.CompressFormat.JPEG
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> error("Replace supports JPEG, PNG and WebP only")
        }
        val exif = readExif(context, photo.uri)
        val inMediaStore = photo.uri.authority == MediaStore.AUTHORITY
        if (inMediaStore) Originals.keep(context, photo)
        context.contentResolver.openOutputStream(photo.uri, "wt")!!.use { bitmap.compress(format, 95, it) }
        writeExif(context, photo.uri, exif)
        if (inMediaStore) rescan(context, photo)
    }

    /**
     * Has MediaStore read [photo]'s file again and waits until it has. Until then it reports the old
     * modification time, size and dimensions, and as the caches are keyed on those, the gallery would go
     * on showing the photo as it was before it changed.
     */
    suspend fun rescan(context: Context, photo: Photo) {
        val path = photo.path ?: return
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { done ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> if (done.isActive) done.resume(Unit) }
            }
        }
    }
}
