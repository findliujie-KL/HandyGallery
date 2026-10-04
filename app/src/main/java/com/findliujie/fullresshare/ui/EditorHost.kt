package com.findliujie.fullresshare.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.findliujie.fullresshare.EditState
import com.findliujie.fullresshare.Edits
import com.findliujie.fullresshare.Media
import com.findliujie.fullresshare.Photo
import com.findliujie.fullresshare.R
import com.findliujie.fullresshare.Store
import com.findliujie.fullresshare.share.FileSharer
import kotlinx.coroutines.launch

/**
 * The editor plus everything needed to act on its result, so it works the same inside the gallery
 * and when another app opens an image with us.
 *
 * [directWrite] says the caller granted write access to the image itself, which is how a non-MediaStore
 * image can be replaced. [onSaved] receives the new copy's uri, or null when the original was replaced.
 * [onDeleted] is called after the user bins a screenshot they have finished with.
 */
@Composable
fun EditorHost(
    photo: Photo,
    store: Store,
    directWrite: Boolean,
    onClose: () -> Unit,
    onSaved: (Uri?) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var afterConsent by remember { mutableStateOf<(() -> Unit)?>(null) }
    var copied by remember { mutableStateOf<EditState?>(null) }
    var offerDelete by remember { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val then = afterConsent
        afterConsent = null
        if (result.resultCode == Activity.RESULT_OK) then?.invoke()
    }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    /** Renders [edit] at full resolution under a busy dialog and hands the result to [finish]. */
    fun render(message: Int, edit: EditState, finish: suspend (Bitmap) -> Unit) {
        scope.launch {
            busy = context.getString(message)
            try {
                val bitmap = Edits.renderFull(app, photo, edit)
                try {
                    finish(bitmap)
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                toast(context.getString(R.string.failed, e.message))
            } finally {
                busy = null
            }
        }
    }

    val inMediaStore = photo.uri.authority == MediaStore.AUTHORITY
    fun export(edit: EditState, mode: ExportMode) {
        when (mode) {
            ExportMode.SaveCopy -> render(R.string.saving_copy, edit) {
                val saved = Media.saveCopy(app, photo, it)
                toast(context.getString(R.string.saved_copy))
                onSaved(saved)
            }
            ExportMode.ReplaceOriginal -> {
                val replace = {
                    render(R.string.replacing, edit) {
                        Media.replaceOriginal(app, photo, it)
                        toast(context.getString(R.string.replaced))
                        onSaved(null)
                    }
                }
                if (!inMediaStore) replace()
                else try {
                    afterConsent = replace
                    val request = MediaStore.createWriteRequest(context.contentResolver, listOf(photo.uri))
                    consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                } catch (e: Exception) {
                    // Not every media uri can be written, e.g. one from the system photo picker.
                    afterConsent = null
                    toast(context.getString(R.string.failed, e.message))
                }
            }
            ExportMode.Clipboard -> render(R.string.copying, edit) {
                FileSharer.copyToClipboard(app, it)
                copied = edit
                // A screenshot edited and copied has usually served its purpose, so offer to clear it away.
                // Otherwise nothing is shown: Android's own clipboard preview already confirms the copy.
                if (inMediaStore && photo.isScreenshot) offerDelete = true
            }
        }
    }

    DarkTheme {
        EditorScreen(
            photo = photo,
            startInCrop = store.startInCrop,
            confirmExit = store.confirmExit,
            canReplace = Media.canReplace(photo) && (inMediaStore || directWrite),
            copied = copied,
            onCancel = onClose,
            onExport = ::export,
        )
        busy?.let { BusyDialog(it) }
        if (offerDelete) AlertDialog(
            onDismissRequest = { offerDelete = false },
            title = { Text(stringResource(R.string.screenshot_copied)) },
            text = { Text(stringResource(R.string.delete_screenshot_question)) },
            confirmButton = {
                TextButton(onClick = {
                    offerDelete = false
                    try {
                        afterConsent = onDeleted
                        val request = MediaStore.createTrashRequest(context.contentResolver, listOf(photo.uri), true)
                        consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } catch (e: Exception) {
                        afterConsent = null
                        toast(context.getString(R.string.failed, e.message))
                    }
                }) { Text(stringResource(R.string.move_to_trash)) }
            },
            dismissButton = { TextButton(onClick = { offerDelete = false }) { Text(stringResource(R.string.keep)) } },
        )
    }
}
