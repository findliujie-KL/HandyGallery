package com.findliujie.fullresshare.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.findliujie.fullresshare.Media
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileSharer {
    const val MIME_FILE = "application/octet-stream"
    const val MIME_ZIP = "application/zip"
    const val MIME_CLIP = "image/jpeg"
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    class Prepared(val file: File, val mime: String)

    private fun authority(context: Context) = "${context.packageName}.files"

    /** Receivers may read lazily, so earlier outputs are only cleaned up once they are a day old. */
    private fun freshDir(context: Context, name: String, now: Long): File {
        val root = File(context.cacheDir, name)
        root.listFiles()?.filter { now - it.lastModified() > KEEP_MS }?.forEach { it.deleteRecursively() }
        return root.apply { mkdirs() }
    }

    /** Copies one image byte-for-byte, or packs several into a zip, under cache/shared. */
    suspend fun prepare(context: Context, uris: List<Uri>): Prepared = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Nothing to share" }
        val now = System.currentTimeMillis()
        val dir = File(freshDir(context, "shared", now), now.toString()).apply { mkdirs() }
        val names = uniqueNames(uris.mapIndexed { i, uri -> displayName(context, uri, now, i) })

        if (uris.size == 1) {
            val out = File(dir, names[0])
            Media.openOriginal(context, uris[0]).use { input -> out.outputStream().use { input.copyTo(it) } }
            Prepared(out, MIME_FILE)
        } else {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val zip = File(dir, "Photos_$stamp.zip")
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                // Images are already compressed; deflating them again only costs time.
                z.setLevel(Deflater.NO_COMPRESSION)
                uris.forEachIndexed { i, uri ->
                    z.putNextEntry(ZipEntry(names[i]))
                    Media.openOriginal(context, uri).use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            Prepared(zip, MIME_ZIP)
        }
    }

    /** "Share Full Res": hands the prepared file over as a generic file so receivers don't recompress it. */
    fun launch(context: Context, prepared: Prepared) {
        val uri = FileProvider.getUriForFile(context, authority(context), prepared.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = prepared.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData(prepared.file.name, arrayOf(prepared.mime), ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(context, send, context.getString(R.string.share_full_res))
    }

    /** "Stock Image Share": the ordinary image share, where the receiving app decides about compression. */
    fun launchStock(context: Context, photos: List<Photo>) {
        if (photos.isEmpty()) return
        val uris = ArrayList(photos.map { it.uri })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(photos[0].mime)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("image/*")
        }
        send.clipData = ClipData("Photos", arrayOf("image/*"), ClipData.Item(uris[0])).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startChooser(context, send, context.getString(R.string.share_stock))
    }

    private fun startChooser(context: Context, send: Intent, title: String) {
        val chooser = Intent.createChooser(send, title).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ReceiveActivity::class.java)))
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /** Puts [bitmap] on the clipboard as an image, without adding it to the gallery. */
    suspend fun copyToClipboard(context: Context, bitmap: Bitmap) {
        val file = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            File(freshDir(context, "clip", now), "edited_$now.jpg").also { out ->
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData("Edited image", arrayOf(MIME_CLIP), ClipData.Item(uri)))
    }

    private fun displayName(context: Context, uri: Uri, now: Long, index: Int): String {
        val resolver = context.contentResolver
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val name = (queried ?: "IMG_${now}_${index + 1}").replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (name.substringAfterLast('.', "").length in 1..5) return name
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
        return "$name.$ext"
    }

    private fun uniqueNames(names: List<String>): List<String> {
        val seen = HashSet<String>()
        return names.map { name ->
            var candidate = name
            var n = 2
            while (!seen.add(candidate.lowercase())) {
                candidate = "${name.substringBeforeLast('.')}($n).${name.substringAfterLast('.')}"
                n++
            }
            candidate
        }
    }
}
