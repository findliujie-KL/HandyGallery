package com.lmxyapp.handygallery.share

import android.net.Uri
import androidx.core.content.FileProvider

/**
 * Stock FileProvider reports image/jpeg for a .jpg, which lets the receiver (WeChat) treat it as a
 * picture and recompress it. Reporting a generic type keeps it on the "file" path.
 *
 * Clipboard images are the exception: paste targets need to see a real image type.
 */
class HgFileProvider : FileProvider() {
    override fun getType(uri: Uri): String = when {
        uri.pathSegments.firstOrNull() == "clip" -> FileSharer.MIME_CLIP
        uri.lastPathSegment?.endsWith(".zip", ignoreCase = true) == true -> FileSharer.MIME_ZIP
        else -> FileSharer.MIME_FILE
    }
}
