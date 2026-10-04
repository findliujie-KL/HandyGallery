package com.findliujie.fullresshare.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material icons that aren't in material-icons-core, so we don't pull in the whole extended set. */
object FrsIcons {
    private fun icon(name: String, path: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
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

    /** "Send to the top": the prep-for-FullResShare action. */
    val Prep by lazy {
        icon("Prep", "M8,11h3v10h2V11h3l-4,-4 -4,4zM4,3v2h16V3H4z")
    }
}
