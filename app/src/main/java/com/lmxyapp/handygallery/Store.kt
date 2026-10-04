package com.lmxyapp.handygallery

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
class Store private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(AppLocale.PREFS, Context.MODE_PRIVATE)

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

    var theme by mutableStateOf(enumValues<ThemeMode>()[prefs.getInt("theme", 0)])
        private set

    /** Language tag, or empty to follow the system. Takes effect when the activity is recreated. */
    var language by mutableStateOf(prefs.getString(AppLocale.KEY, "").orEmpty())
        private set

    /** Bucket ids the user has hidden from the Folders tab. */
    var hiddenFolders by mutableStateOf(prefs.getStringSet("hiddenFolders", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
        private set

    /** Whether the editor opens straight onto the crop and rotate screen. */
    var startInCrop by mutableStateOf(prefs.getBoolean("startInCrop", false))
        private set

    /** Whether leaving the editor with unsaved edits asks first. */
    var confirmExit by mutableStateOf(prefs.getBoolean("confirmExit", true))
        private set

    fun chooseStartInCrop(on: Boolean) {
        startInCrop = on
        prefs.edit { putBoolean("startInCrop", on) }
    }

    fun chooseConfirmExit(on: Boolean) {
        confirmExit = on
        prefs.edit { putBoolean("confirmExit", on) }
    }

    fun chooseTheme(mode: ThemeMode) {
        theme = mode
        prefs.edit { putInt("theme", mode.ordinal) }
    }

    fun chooseLanguage(tag: String) {
        language = tag
        // Synchronous: the activity is recreated straight after and reads this in attachBaseContext.
        prefs.edit(commit = true) { putString(AppLocale.KEY, tag) }
    }

    /**
     * Bucket ids left off the photo wall. Stored as exclusions rather than as the chosen set, so a
     * folder that appears later (a new app's pictures, say) shows up without the user having to add it.
     */
    var wallHidden by mutableStateOf(prefs.getStringSet("wallHidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
        private set

    fun chooseWallHidden(ids: Set<Long>) {
        wallHidden = ids
        prefs.edit { putStringSet("wallHidden", ids.map { it.toString() }.toSet()) }
    }

    /** Whether each month on the photo wall is split into a subsection per folder. */
    var wallByFolder by mutableStateOf(prefs.getBoolean("wallByFolder", false))
        private set

    fun chooseWallByFolder(on: Boolean) {
        wallByFolder = on
        prefs.edit { putBoolean("wallByFolder", on) }
    }

    fun setFolderHidden(id: Long, hidden: Boolean) {
        hiddenFolders = if (hidden) hiddenFolders + id else hiddenFolders - id
        prefs.edit { putStringSet("hiddenFolders", hiddenFolders.map { it.toString() }.toSet()) }
    }

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

        @Volatile
        private var instance: Store? = null

        /**
         * The process-wide store. Several activities write marks; separate instances would each hold a
         * stale copy and overwrite one another's changes.
         */
        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
    }
}
