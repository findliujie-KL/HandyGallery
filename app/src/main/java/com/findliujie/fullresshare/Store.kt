package com.findliujie.fullresshare

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * A prep we did: [time] is when the frs_ marker was written, [originalModified] (seconds) is the
 * file's modification time before that, kept so the prep can be undone completely.
 */
data class Mark(val time: Long, val originalModified: Long)

/**
 * App-local state that MediaStore can't hold for us without a system prompt per change:
 * favourites, which photos we have prepped, and view preferences.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("gallery", Context.MODE_PRIVATE)

    var favorites by mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
        private set

    /** Photo id -> the prep currently on it. */
    var marks by mutableStateOf(
        prefs.getStringSet("marks", emptySet())!!.mapNotNull { entry ->
            // "id:time:originalModified"; entries from before undo existed lack the last field.
            val fields = entry.split(':').mapNotNull { it.toLongOrNull() }
            if (fields.size < 2) null else fields[0] to Mark(fields[1], fields.getOrElse(2) { 0 })
        }.toMap(),
    )
        private set

    var photoSort by mutableStateOf(enumValues<PhotoSort>()[prefs.getInt("photoSort", 0)])
        private set
    var folderSort by mutableStateOf(enumValues<FolderSort>()[prefs.getInt("folderSort", 0)])
        private set

    /** Index into the photo wall's zoom levels. */
    var gridZoom by mutableIntStateOf(prefs.getInt("gridZoom", DEFAULT_GRID_ZOOM))
        private set

    fun setFavorite(ids: Collection<Long>, favorite: Boolean) {
        favorites = if (favorite) favorites + ids else favorites - ids.toSet()
        prefs.edit { putStringSet("favorites", favorites.map { it.toString() }.toSet()) }
    }

    fun mark(entries: Map<Long, Mark>) = saveMarks(marks + entries)

    fun unmark(ids: Collection<Long>) = saveMarks(marks - ids.toSet())

    /** Drops favourites and marks whose photo no longer exists. */
    fun keepOnly(existing: Set<Long>) {
        if (!existing.containsAll(marks.keys)) saveMarks(marks.filterKeys { it in existing })
        if (!existing.containsAll(favorites)) setFavorite(favorites - existing, false)
    }

    private fun saveMarks(next: Map<Long, Mark>) {
        marks = next
        prefs.edit { putStringSet("marks", next.map { "${it.key}:${it.value.time}:${it.value.originalModified}" }.toSet()) }
    }

    fun sortPhotos(sort: PhotoSort) {
        photoSort = sort
        prefs.edit { putInt("photoSort", sort.ordinal) }
    }

    fun sortFolders(sort: FolderSort) {
        folderSort = sort
        prefs.edit { putInt("folderSort", sort.ordinal) }
    }

    fun zoomGrid(level: Int) {
        gridZoom = level
        prefs.edit { putInt("gridZoom", level) }
    }

    companion object {
        const val DEFAULT_GRID_ZOOM = 2
    }
}
