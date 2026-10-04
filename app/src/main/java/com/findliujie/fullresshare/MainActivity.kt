package com.findliujie.fullresshare

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findliujie.fullresshare.share.FileSharer
import com.findliujie.fullresshare.ui.BusyDialog
import com.findliujie.fullresshare.ui.CollectionScreen
import com.findliujie.fullresshare.ui.DarkTheme
import com.findliujie.fullresshare.ui.EditorScreen
import com.findliujie.fullresshare.ui.ExportMode
import com.findliujie.fullresshare.ui.FrsTheme
import com.findliujie.fullresshare.ui.HomeScreen
import com.findliujie.fullresshare.ui.InfoScreen
import com.findliujie.fullresshare.ui.PhotoActions
import com.findliujie.fullresshare.ui.ViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    private var photos by mutableStateOf(emptyList<Photo>())
    private var trashed by mutableStateOf(emptyList<Photo>())
    var canRead by mutableStateOf(Media.canRead(app))
        private set

    /** Every non-trashed photo in the user's chosen order. */
    val sorted by derivedStateOf { photos.sortedByDescending { it.time(store.photoSort) } }
    private val screenshots by derivedStateOf { sorted.filter { it.isScreenshot } }
    private val shared by derivedStateOf {
        val marks = store.marks
        photos.filter { it.id in marks }.sortedByDescending { marks[it.id]?.time }
    }
    private val favorites by derivedStateOf { sorted.filter { it.id in store.favorites } }

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var homeTab by mutableIntStateOf(0)
    var busy by mutableStateOf<String?>(null)

    /** What to do once the user approves the system prompt currently showing. */
    var afterConsent: (() -> Unit)? = null

    fun photosFor(source: Source): List<Photo> = when (source) {
        Source.All -> sorted
        Source.Screenshots -> screenshots
        Source.FullResShared -> shared
        Source.Favorites -> favorites
        Source.Trash -> trashed
        is Source.Folder -> sorted.filter { it.bucketId == source.id }
    }

    fun open(screen: Screen) {
        stack += screen
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun reload() {
        canRead = Media.canRead(getApplication())
        viewModelScope.launch {
            photos = Media.load(getApplication(), trashed = false)
            trashed = Media.load(getApplication(), trashed = true).sortedByDescending { it.dateModified }
            // MediaStore reuses ids, so a mark left behind by a deleted photo would attach itself to a new one.
            if (photos.isNotEmpty()) store.keepOnly((photos + trashed).mapTo(HashSet()) { it.id })
        }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FrsTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
    }
}

@Composable
private fun App(vm: GalleryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    /** Shows the busy dialog around [block] and reports failures. */
    fun runBusy(message: String, block: suspend () -> Unit) {
        scope.launch {
            vm.busy = message
            try {
                block()
            } catch (e: Exception) {
                toast("Failed: ${e.message}")
            } finally {
                vm.busy = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.reload()
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val then = vm.afterConsent
        vm.afterConsent = null
        if (result.resultCode == Activity.RESULT_OK) then?.invoke()
    }

    /** Launches a MediaStore write/trash/delete request; [then] runs only if the user (or MANAGE_MEDIA) allows it. */
    fun withConsent(request: PendingIntent, then: () -> Unit) {
        vm.afterConsent = then
        consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    val resolver = context.contentResolver
    val actions = remember {
        PhotoActions(
            shareFullRes = { photos ->
                runBusy(if (photos.size == 1) "Preparing original…" else "Zipping ${photos.size} photos…") {
                    FileSharer.launch(context, FileSharer.prepare(context.applicationContext, photos.map { it.uri }))
                }
            },
            shareStock = { photos -> FileSharer.launchStock(context, photos) },
            prep = { photos ->
                withConsent(MediaStore.createWriteRequest(resolver, photos.map { it.uri })) {
                    runBusy("Writing marker…") {
                        val existing = vm.store.marks
                        val marks = withContext(Dispatchers.IO) {
                            photos.mapNotNull { photo ->
                                // Re-prepping keeps the modification time from before the first prep, so undo still goes all the way back.
                                val original = existing[photo.id]?.originalModified ?: photo.dateModified
                                TopTagger.tag(context, photo)?.let { photo.id to Mark(it, original) }
                            }.toMap()
                        }
                        vm.store.mark(marks)
                        vm.reload()
                        toast(
                            if (marks.size == photos.size) "Prepped ${marks.size} — now at the top of WeChat's picker"
                            else "Prepped ${marks.size}, ${photos.size - marks.size} failed",
                        )
                    }
                }
            },
            unprep = { photos ->
                val existing = vm.store.marks
                val marked = photos.filter { it.id in existing }
                if (marked.isEmpty()) toast("Nothing here is prepped")
                else withConsent(MediaStore.createWriteRequest(resolver, marked.map { it.uri })) {
                    runBusy("Removing marker…") {
                        withContext(Dispatchers.IO) {
                            marked.forEach { TopTagger.untag(context, it, existing.getValue(it.id).originalModified) }
                        }
                        vm.store.unmark(marked.map { it.id })
                        vm.reload()
                        toast("Undid prep for ${marked.size}")
                    }
                }
            },
            trash = { photos -> withConsent(MediaStore.createTrashRequest(resolver, photos.map { it.uri }, true)) { vm.reload() } },
            restore = { photos -> withConsent(MediaStore.createTrashRequest(resolver, photos.map { it.uri }, false)) { vm.reload() } },
            delete = { photos -> withConsent(MediaStore.createDeleteRequest(resolver, photos.map { it.uri })) { vm.reload() } },
            setFavorite = { photos, favorite -> vm.store.setFavorite(photos.map { it.id }, favorite) },
        )
    }

    fun export(photo: Photo, edit: EditState, mode: ExportMode) {
        fun render(message: String, finish: suspend (android.graphics.Bitmap) -> Unit) = runBusy(message) {
            val bitmap = Edits.renderFull(context.applicationContext, photo, edit)
            try {
                finish(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        when (mode) {
            ExportMode.SaveCopy -> render("Saving copy…") {
                Media.saveCopy(context.applicationContext, photo, it)
                vm.reload()
                vm.back()
                toast("Saved as a new photo")
            }
            ExportMode.ReplaceOriginal -> withConsent(MediaStore.createWriteRequest(resolver, listOf(photo.uri))) {
                render("Replacing original…") {
                    Media.replaceOriginal(context.applicationContext, photo, it)
                    vm.reload()
                    vm.back()
                    toast("Original replaced")
                }
            }
            ExportMode.Clipboard -> render("Copying…") {
                FileSharer.copyToClipboard(context.applicationContext, it)
                toast("Edited image copied")
            }
        }
    }

    LaunchedEffect(Unit) { if (!vm.canRead) permissionLauncher.launch(Media.permissions) }
    BackHandler(vm.stack.size > 1, vm::back)

    when (val screen = vm.stack.last()) {
        Screen.Home -> HomeScreen(
            vm = vm,
            actions = actions,
            onRequestPermission = { permissionLauncher.launch(Media.permissions) },
            onManageMedia = {
                if (Build.VERSION.SDK_INT >= 31) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
        )
        is Screen.Collection -> CollectionScreen(vm, screen.source, actions)
        is Screen.Viewer -> ViewerScreen(vm, screen, actions)
        is Screen.Info -> InfoScreen(screen.photo, vm::back)
        is Screen.Editor -> DarkTheme {
            EditorScreen(screen.photo, onCancel = vm::back) { edit, mode -> export(screen.photo, edit, mode) }
        }
    }
    vm.busy?.let { BusyDialog(it) }
}
