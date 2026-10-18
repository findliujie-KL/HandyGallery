package com.lmxyapp.handygallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Untouched copies of photos whose edits were saved over them, so the edits can be undone. Each is
 * kept in the app's own storage under the photo's MediaStore id, holding the file exactly as it was
 * before its first saved edit, with that file's modification time.
 */
object Originals {
    private fun dir(context: Context) = File(context.filesDir, "originals")

    private fun file(context: Context, id: Long) = File(dir(context), id.toString())

    /** Ids of the photos that have an original kept. */
    suspend fun ids(context: Context): Set<Long> = withContext(Dispatchers.IO) {
        dir(context).list()?.mapNotNullTo(HashSet()) { it.toLongOrNull() } ?: emptySet()
    }

    /**
     * Keeps [photo] as it is now, before an edit is saved over it. A photo edited again keeps the copy
     * from before its first edit, so reverting always goes all the way back.
     */
    suspend fun keep(context: Context, photo: Photo): Unit = withContext(Dispatchers.IO) {
        val target = file(context, photo.id)
        if (target.exists()) return@withContext
        val partial = File(dir(context).apply { mkdirs() }, "${photo.id}.part")
        Media.openOriginal(context, photo.uri).use { input -> partial.outputStream().use { input.copyTo(it) } }
        partial.setLastModified(photo.dateModified * 1000)
        if (!partial.renameTo(target)) error("Cannot keep the original")
    }

    /** Puts [photo] back as it was before it was edited and forgets the kept copy. Caller must hold write access. */
    suspend fun restore(context: Context, photo: Photo): Unit = withContext(Dispatchers.IO) {
        val kept = file(context, photo.id)
        if (!kept.exists()) error("No original kept")
        kept.inputStream().use { input -> context.contentResolver.openOutputStream(photo.uri, "wt")!!.use { input.copyTo(it) } }
        // Its modification time goes back too, so it sorts where it did before the edit.
        photo.path?.let { File(it).setLastModified(kept.lastModified()) }
        kept.delete()
        Media.rescan(context, photo)
    }

    /** Forgets originals whose photo no longer exists; MediaStore reuses ids, so they could attach to another photo. */
    suspend fun keepOnly(context: Context, existing: Set<Long>): Unit = withContext(Dispatchers.IO) {
        dir(context).listFiles()?.forEach { file ->
            val id = file.name.toLongOrNull()
            if (id == null || id !in existing) file.delete()
        }
    }
}
