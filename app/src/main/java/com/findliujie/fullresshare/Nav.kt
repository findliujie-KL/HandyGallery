package com.findliujie.fullresshare

/** A set of photos the user can browse. */
sealed interface Source {
    val title: String

    data object All : Source { override val title = "Photos" }
    data object Screenshots : Source { override val title = "Screenshots" }

    /** Photos this app has stamped with an frs_YYYYMMDDHHmm marker, most recently stamped first. */
    data object FullResShared : Source { override val title = "FullResShared" }
    data object Favorites : Source { override val title = "Favourites" }
    data object Trash : Source { override val title = "Trash" }
    data class Folder(val id: Long, val name: String) : Source { override val title get() = name }
}

sealed interface Screen {
    data object Home : Screen
    data class Collection(val source: Source) : Screen

    /** [currentId] follows the pager so returning from the editor or info page lands on the same photo. */
    class Viewer(val source: Source, var currentId: Long) : Screen
    data class Info(val photo: Photo) : Screen
    data class Editor(val photo: Photo) : Screen
}
