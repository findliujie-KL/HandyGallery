package com.lmxyapp.handygallery

import androidx.annotation.StringRes

/** A set of photos the user can browse. */
sealed interface Source {
    /** A fixed collection named by a string resource. */
    sealed class Named(@StringRes val title: Int) : Source

    data object All : Named(R.string.photos)
    data object Screenshots : Named(R.string.screenshots)

    /** Photos this app has stamped with an frs_YYYYMMDDHHmm marker, most recently stamped first. */
    data object FullResShared : Named(R.string.fullresshared)
    data object Favorites : Named(R.string.favourites)
    data object Trash : Named(R.string.trash)
    data class Folder(val id: Long, val name: String) : Source
}

sealed interface Screen {
    data object Home : Screen
    data class Collection(val source: Source) : Screen

    /** [currentId] follows the pager so returning from the editor lands on the same photo. */
    class Viewer(val source: Source, var currentId: Long) : Screen
    /** [crop] opens the editor on its crop and rotate screen regardless of the setting. */
    data class Editor(val photo: Photo, val crop: Boolean = false) : Screen
    data object Settings : Screen

    /** Sort mode. What it is going through lives in the Store, so the same session can be picked up later. */
    data object Sorter : Screen
}
