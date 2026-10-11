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

    /**
     * Index into the photo wall's zoom levels. Stored under a new key since a level was added at the
     * large end; a choice saved under the old key is one index lower.
     */
    var gridZoom by mutableIntStateOf(prefs.getInt("wallZoom", prefs.getInt("gridZoom", DEFAULT_GRID_ZOOM - 1) + 1))
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

    /**
     * What the tiles above the photo wall open, in order: "screenshots", "frontrow", "favourites", or
     * "folder:<bucket id>" for a folder the user picked. At most [MAX_SHORTCUTS].
     */
    var wallShortcuts by mutableStateOf((prefs.getString("wallShortcuts", null) ?: DEFAULT_SHORTCUTS).split('|').filter { it.isNotEmpty() })
        private set

    fun chooseWallShortcuts(keys: List<String>) {
        wallShortcuts = keys.take(MAX_SHORTCUTS)
        prefs.edit { putString("wallShortcuts", wallShortcuts.joinToString("|")) }
    }

    /**
     * Sort mode's session, kept so it survives the app being closed: the photos being gone through in
     * order, how far the user has got, and what has been decided. A decision is the folder to move the
     * photo to (a path like "Pictures/Trips/") or [SORT_REJECT]; nothing is done to any file until the
     * user confirms.
     */
    var sortIds by mutableStateOf(prefs.getString("sortIds", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() })
        private set
    var sortAt by mutableIntStateOf(prefs.getInt("sortAt", 0))
        private set
    var sortDecisions by mutableStateOf(
        prefs.getStringSet("sortDecisions", emptySet())!!.mapNotNull { entry ->
            val id = entry.substringBefore('|').toLongOrNull()
            if (id == null || '|' !in entry) null else id to entry.substringAfter('|')
        }.toMap(),
    )
        private set

    fun startSort(ids: List<Long>) {
        sortIds = ids
        sortAt = 0
        sortDecisions = emptyMap()
        prefs.edit {
            putString("sortIds", ids.joinToString(","))
            putInt("sortAt", 0)
            remove("sortDecisions")
        }
    }

    fun moveSortTo(index: Int) {
        if (index == sortAt) return
        sortAt = index
        prefs.edit { putInt("sortAt", index) }
    }

    /** Records what to do with a photo; null withdraws the decision. */
    fun decideSort(id: Long, decision: String?) {
        sortDecisions = if (decision == null) sortDecisions - id else sortDecisions + (id to decision)
        prefs.edit { putStringSet("sortDecisions", sortDecisions.map { "${it.key}|${it.value}" }.toSet()) }
    }

    fun clearSort() = startSort(emptyList())

    /**
     * Photos the user has looked at among similar ones and chosen to keep all of. A group made up only
     * of these is settled; if another look-alike joins it later, it comes up for a decision again.
     */
    var similarKept by mutableStateOf(prefs.getStringSet("similarKept", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
        private set

    fun keepSimilar(ids: Collection<Long>, keep: Boolean) {
        similarKept = if (keep) similarKept + ids else similarKept - ids.toSet()
        prefs.edit { putStringSet("similarKept", similarKept.map { it.toString() }.toSet()) }
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
        if (!existing.containsAll(similarKept)) keepSimilar(similarKept - existing, false)
    }

    private fun saveMarks(next: Map<Long, Mark>) {
        marks = next
        prefs.edit { putStringSet("marks", next.map { "${it.key}:${it.value.time}:${it.value.originalModified}" }.toSet()) }
    }

    fun sortPhotos(sort: PhotoSort) {
        photoSort = sort
        prefs.edit { putInt("photoSort", sort.ordinal) }
    }

    /** Bucket ids in the order the user arranged the Folders tab. */
    var folderOrder by mutableStateOf(prefs.getString("folderOrder", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() })
        private set

    fun chooseFolderOrder(ids: List<Long>) {
        folderOrder = ids
        prefs.edit { putString("folderOrder", ids.joinToString(",")) }
    }

    fun sortFolders(sort: FolderSort) {
        folderSort = sort
        prefs.edit { putInt("folderSort", sort.ordinal) }
    }

    fun zoomGrid(level: Int) {
        gridZoom = level
        prefs.edit { putInt("wallZoom", level) }
    }

    companion object {
        const val DEFAULT_GRID_ZOOM = 3
        const val MAX_SHORTCUTS = 3

        /** A sort-mode decision that isn't a folder: the photo is to go to the trash. */
        const val SORT_REJECT = "*reject"
        const val SHORTCUT_SCREENSHOTS = "screenshots"
        const val SHORTCUT_FRONT_ROW = "frontrow"
        const val SHORTCUT_FAVOURITES = "favourites"
        const val SHORTCUT_FOLDER = "folder:"
        private const val DEFAULT_SHORTCUTS = "$SHORTCUT_SCREENSHOTS|$SHORTCUT_FRONT_ROW|$SHORTCUT_FAVOURITES"

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
