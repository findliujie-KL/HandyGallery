package com.lmxyapp.handygallery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material icons that aren't in material-icons-core, so we don't pull in the whole extended set. */
object HgIcons {
    /** With [evenOdd], a shape drawn inside another cuts a hole in it, as a picture inside a frame. */
    private fun icon(name: String, path: String, evenOdd: Boolean = false) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                addPathNodes(path),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
            .build()

    val StarBorder by lazy {
        icon("StarBorder", "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z")
    }
    val Image by lazy {
        icon("Image", "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z")
    }
    val Folder by lazy {
        icon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }
    /** Tiles of different sizes: stands for the photo wall. */
    val Collage by lazy {
        icon("Collage", "M3,13h8V3H3v10zM3,21h8v-6H3v6zM13,21h8V11h-8v10zM13,3v6h8V3h-8z")
    }
    /** An arrow leaving a box: open this in another app. */
    val OpenElsewhere by lazy {
        icon("OpenElsewhere", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }
    val Sort by lazy {
        icon("Sort", "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3V14h2V6.99h3L9,3z")
    }
    val Crop by lazy {
        icon("Crop", "M17,15h2V7c0,-1.1 -0.9,-2 -2,-2H9v2h8v8zM7,17V1H5v4H1v2h4v10c0,1.1 0.9,2 2,2h10v4h2v-4h4v-2H7z")
    }
    val Undo by lazy {
        icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }

    val Text by lazy { icon("Text", "M5,4v3h5.5v12h3V7H19V4z") }
    val Mosaic by lazy {
        icon("Mosaic", "M4,4h4v4h-4zM12,4h4v4h-4zM8,8h4v4h-4zM16,8h4v4h-4zM4,12h4v4h-4zM12,12h4v4h-4zM8,16h4v4h-4zM16,16h4v4h-4z")
    }
    val Eraser by lazy {
        icon("Eraser", "M15.14,3c-0.51,0 -1.02,0.2 -1.41,0.59L2.59,14.73c-0.78,0.77 -0.78,2.04 0,2.83L5.03,20h7.66l8.72,-8.73c0.79,-0.77 0.79,-2.04 0,-2.83l-4.85,-4.85C16.17,3.2 15.65,3 15.14,3zM17,18l-2,2h7v-2h-5z")
    }
    val RotateRight by lazy {
        icon("RotateRight", "M4.64,19.37c3.03,3.03 7.67,3.44 11.15,1.25l-1.46,-1.46c-2.66,1.43 -6.04,1.03 -8.28,-1.21 -2.73,-2.73 -2.73,-7.17 0,-9.9C7.42,6.69 9.21,6.03 11,6.03V9l4,-4 -4,-4v3.01c-2.3,0 -4.61,0.87 -6.36,2.63 -3.52,3.51 -3.52,9.21 0,12.73zM11,13l6,6 6,-6 -6,-6 -6,6z")
    }

    /** Save the edit as a new photo. */
    val SaveCopy by lazy {
        icon("SaveCopy", "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM19,11h-4v4h-2v-4H9V9h4V5h2v4h4v2z")
    }

    /** Overwrite the original photo with the edit. */
    val Replace by lazy {
        icon("Replace", "M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10v4z")
    }

    /** Copy the edit to the clipboard. */
    val Copy by lazy {
        icon("Copy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
    }

    val Brush by lazy {
        icon("Brush", "M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z")
    }
    val ArrowShape by lazy { icon("ArrowShape", "M9,5v2h6.59L4,18.59 5.41,20 17,8.41V15h2V5H9z") }
    val RectShape by lazy {
        icon("RectShape", "M19,5H5c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,17H5V7h14v10z")
    }
    val OvalShape by lazy {
        icon("OvalShape", "M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z")
    }

    val Screenshot by lazy {
        icon("Screenshot", "M17,1.01L7,1C5.9,1 5,1.9 5,3v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3C19,1.9 18.1,1.01 17,1.01zM17,18H7V6h10V18zM9.5,8.5H12V7H8v4h1.5V8.5zM12,17h4v-4h-1.5v2.5H12V17z")
    }

    /** "Send to the top": the Front Row action. */
    val Prep by lazy {
        icon("Prep", "M8,11h3v10h2V11h3l-4,-4 -4,4zM4,3v2h16V3H4z")
    }

    /** A toolbox: the tools menu. */
    val Toolbox by lazy {
        icon("Toolbox", "M18,16h-2v-1H8v1H6v-1H2v5h20v-5h-4zM20,8h-3V6c0,-1.1 -0.9,-2 -2,-2H9c-1.1,0 -2,0.9 -2,2v2H4c-1.1,0 -2,0.9 -2,2v4h4v-2h2v2h8v-2h2v2h4v-4c0,-1.1 -0.9,-2 -2,-2zM15,8H9V6h6v2z")
    }
    /** A photo with an arrow taking it into a folder: sort mode, filing photos away one by one. */
    val SortCards by lazy {
        icon(
            "SortCards",
            "M2,2h10v10H2zM4,4v6h6V4z" +
                "M5,9l2,-2.5 1.5,1.5 1.5,-1.5V9z" +
                "M13,5.25h4.75v4.25h1.75l-2.5,3 -2.5,-3h1.75V6.75H13z" +
                "M13,13h3.5l1.5,1.5H21c0.55,0 1,0.45 1,1V21c0,0.55 -0.45,1 -1,1h-8c-0.55,0 -1,-0.45 -1,-1v-7c0,-0.55 0.45,-1 1,-1z",
            evenOdd = true,
        )
    }
    /**
     * The same picture twice, one in front of the other: similar photos. Drawn with the outlines going
     * one way and the holes the other, so the front photo covers the back one instead of cutting into it.
     */
    val Similar by lazy {
        icon(
            "Similar",
            "M2,2h12v10H2zM3.5,3.5v7h9v-7z" +
                "M4.5,9.5l2,-2.5 1.5,1.8 1.2,-1.4 1.8,2.1z" +
                "M8,9h14v12H8z" +
                "M9.5,19.5H20.5l-3.2,-3.9 -1.8,2.2 -2.2,-2.8z" +
                "M18.5,11.2a1.3,1.3 0 1,0 0,2.6a1.3,1.3 0 1,0 0,-2.6z",
        )
    }
    /** A frame split down the middle: compare photos. */
    val Compare by lazy {
        icon("Compare", "M10,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h5v2h2V1h-2v2zM10,18H5l5,-6v6zM19,3h-5v2h5v13l-5,-6v9h5c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z")
    }
    val Help by lazy {
        icon("Help", "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z")
    }
}
