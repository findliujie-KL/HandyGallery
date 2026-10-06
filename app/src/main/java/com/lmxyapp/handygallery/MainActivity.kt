package com.lmxyapp.handygallery

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lmxyapp.handygallery.share.FileSharer
import com.lmxyapp.handygallery.ui.BusyDialog
import com.lmxyapp.handygallery.ui.CollectionScreen
import com.lmxyapp.handygallery.ui.EditorHost
import com.lmxyapp.handygallery.ui.FolderPickerDialog
import com.lmxyapp.handygallery.ui.FrsTheme
import com.lmxyapp.handygallery.ui.HomeScreen
import com.lmxyapp.handygallery.ui.PhotoActions
import com.lmxyapp.handygallery.ui.SettingsScreen
import com.lmxyapp.handygallery.ui.isDark
import com.lmxyapp.handygallery.ui.ViewerScreen
import com.lmxyapp.handygallery.ui.destinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Photos on their way to another folder, held while the folder is being chosen. */
private class Transfer(val photos: List<Photo>, val copy: Boolean)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store.get(app)

    private var photos by mutableStateOf(emptyList<Photo>())
    private var trashed by mutableStateOf(emptyList<Photo>())
    var canRead by mutableStateOf(Media.canRead(app))
        private set

    /** Every non-trashed photo in the user's chosen order. */
    val sorted by derivedStateOf { photos.sortedByDescending { it.time(store.photoSort) } }
    private val screenshots by derivedStateOf { sorted.filter { it.isScreenshot } }

    /**
     * The photo wall: no screenshots (they have their own shortcut) and no folders the user has switched off.
     * When months are split by folder the list is in that order too, so swiping in the viewer and
     * drag-selecting follow what the wall shows.
     */
    private val wall by derivedStateOf {
        val hidden = store.wallHidden
        val shown = sorted.filter { !it.isScreenshot && it.bucketId !in hidden }
        if (store.wallByFolder) Media.byMonthAndFolder(shown, store.photoSort) else shown
    }
    /** The folders offered when choosing what the wall shows. Screenshots never appear there, so theirs is left out. */
    val wallFolders by derivedStateOf { Media.folders(sorted.filter { !it.isScreenshot }, FolderSort.NameAsc) }

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
        Source.All -> wall
        Source.Screenshots -> screenshots
        Source.FullResShared -> shared
        Source.Favorites -> favorites
        Source.Trash -> trashed
        is Source.Folder -> sorted.filter { it.bucketId == source.id }
    }

    fun open(screen: Screen) {
        stack += screen
    }

    /** Goes straight to [source] with only the home screen behind it, as a direct link does. */
    fun jumpTo(source: Source) {
        if (stack.size > 1) stack.removeRange(1, stack.size)
        homeTab = 0
        stack += Screen.Collection(source)
    }

    fun back() {
        if (stack.size > 1) {
            if (stack.last() is Screen.Viewer) focus = null
            stack.removeAt(stack.lastIndex)
        }
    }

    /**
     * The photo open in the viewer. The grid underneath keeps it in view, so closing the viewer lands
     * on that photo however far the user swiped.
     */
    var focus by mutableStateOf<Long?>(null)

    /** Re-opens [photo] among its own folder's photos; the folder's grid is what the viewer closes onto. */
    fun showInFolder(photo: Photo) {
        val folder = Source.Folder(photo.bucketId, photo.bucketName)
        stack.removeRange(1, stack.size)
        homeTab = 1
        stack += Screen.Collection(folder)
        stack += Screen.Viewer(folder, photo.id)
        focus = photo.id
    }

    /** Re-opens [photo] among the photo wall's photos; the wall is what the viewer closes onto. */
    fun showOnWall(photo: Photo) {
        stack.removeRange(1, stack.size)
        homeTab = 0
        stack += Screen.Viewer(Source.All, photo.id)
        focus = photo.id
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

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start follows the link; a recreated activity keeps the screen it was on.
        if (savedInstanceState == null) followLink(intent)
        setContent {
            val dark = isDark(vm.store.theme)
            // System bar icons must contrast with the app's theme, which may differ from the system's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FrsTheme(vm.store.theme) { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        followLink(intent)
    }

    /**
     * Opens the section a direct link names: handygallery://screenshots or handygallery://frontrow.
     * The launcher shortcuts (res/xml/shortcuts.xml) arrive this way too.
     */
    private fun followLink(intent: Intent?) {
        val link = intent?.data ?: return
        if (link.scheme != "handygallery") return
        when (link.host) {
            "screenshots" -> vm.jumpTo(Source.Screenshots)
            "frontrow" -> vm.jumpTo(Source.FullResShared)
        }
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
                toast(context.getString(R.string.failed, e.message))
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

    /** Photos waiting for the user to say which folder they go to, and whether as copies. */
    var transfer by remember { mutableStateOf<Transfer?>(null) }

    fun copyInto(photos: List<Photo>, dir: String) = runBusy(context.getString(R.string.copying)) {
        val done = photos.count { runCatching { Media.copyTo(context, it, dir) }.isSuccess }
        vm.reload()
        toast(
            if (done == photos.size) context.getString(R.string.copied_ok, done)
            else context.getString(R.string.copied_partial, done, photos.size - done),
        )
    }

    fun moveInto(photos: List<Photo>, dir: String) {
        // Ones already in that folder have nowhere to go.
        val moving = photos.filter { Media.relativeDir(it) != dir }
        if (moving.isEmpty()) return
        withConsent(MediaStore.createWriteRequest(resolver, moving.map { it.uri })) {
            runBusy(context.getString(R.string.moving)) {
                val done = withContext(Dispatchers.IO) { moving.count { Media.moveTo(context, it, dir) } }
                vm.reload()
                toast(
                    if (done == moving.size) context.getString(R.string.moved_ok, done)
                    else context.getString(R.string.moved_partial, done, moving.size - done),
                )
            }
        }
    }

    transfer?.let { job ->
        FolderPickerDialog(
            title = stringResource(if (job.copy) R.string.copy_to_folder else R.string.move_to_folder),
            choices = remember(vm.wallFolders) { destinations(Media.folders(vm.sorted, FolderSort.NameAsc)) },
            onPick = { dir ->
                transfer = null
                if (job.copy) copyInto(job.photos, dir) else moveInto(job.photos, dir)
            },
            onDismiss = { transfer = null },
        )
    }

    val actions = remember {
        PhotoActions(
            shareFullRes = { photos ->
                if (!Trial.blocks(context)) runBusy(if (photos.size == 1) context.getString(R.string.preparing_original) else context.getString(R.string.zipping, photos.size)) {
                    FileSharer.launch(context, FileSharer.prepare(context.applicationContext, photos.map { it.uri }))
                }
            },
            shareStock = { photos -> FileSharer.launchStock(context, photos) },
            prep = { photos ->
                if (!Trial.blocks(context)) withConsent(MediaStore.createWriteRequest(resolver, photos.map { it.uri })) {
                    runBusy(context.getString(R.string.writing_marker)) {
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
                            if (marks.size == photos.size) context.getString(R.string.prepped_ok, marks.size)
                            else context.getString(R.string.prepped_partial, marks.size, photos.size - marks.size),
                        )
                    }
                }
            },
            unprep = { photos ->
                val existing = vm.store.marks
                val marked = photos.filter { it.id in existing }
                if (marked.isNotEmpty()) withConsent(MediaStore.createWriteRequest(resolver, marked.map { it.uri })) {
                    runBusy(context.getString(R.string.removing_marker)) {
                        withContext(Dispatchers.IO) {
                            marked.forEach { TopTagger.untag(context, it, existing.getValue(it.id).originalModified) }
                        }
                        vm.store.unmark(marked.map { it.id })
                        vm.reload()
                        toast(context.getString(R.string.undid_prep, marked.size))
                    }
                }
            },
            trash = { photos -> withConsent(MediaStore.createTrashRequest(resolver, photos.map { it.uri }, true)) { vm.reload() } },
            restore = { photos -> withConsent(MediaStore.createTrashRequest(resolver, photos.map { it.uri }, false)) { vm.reload() } },
            delete = { photos -> withConsent(MediaStore.createDeleteRequest(resolver, photos.map { it.uri })) { vm.reload() } },
            setFavorite = { photos, favorite -> vm.store.setFavorite(photos.map { it.id }, favorite) },
            move = { photos -> transfer = Transfer(photos, copy = false) },
            copy = { photos -> transfer = Transfer(photos, copy = true) },
        )
    }

    LaunchedEffect(Unit) { if (!vm.canRead) permissionLauncher.launch(Media.permissions) }
    BackHandler(vm.stack.size > 1, vm::back)

    // Both layers live in one Box: taking the viewer away must redraw what is underneath, and a child
    // removed straight from the composition's root was leaving the old frame on screen.
    Box(Modifier.fillMaxSize()) {
        // The viewer is drawn over the screen it was opened from rather than replacing it. Swiping the
        // photo away then reveals that screen directly, and the wall keeps its scroll position meanwhile.
        val top = vm.stack.last()
        val base = if (top is Screen.Viewer && vm.stack.size > 1) vm.stack[vm.stack.lastIndex - 1] else top
        when (val screen = base) {
            Screen.Home -> HomeScreen(
                vm = vm,
                actions = actions,
                onRequestPermission = { permissionLauncher.launch(Media.permissions) },
            )
            Screen.Settings -> SettingsScreen(
                store = vm.store,
                wallFolders = vm.wallFolders,
                onBack = vm::back,
                onLanguage = { tag ->
                    vm.store.chooseLanguage(tag)
                    // Resources are bound to the activity's context, so a new language needs a new activity.
                    (context as? Activity)?.recreate()
                },
                onManageMedia = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")),
                        )
                    }
                },
            )
            is Screen.Collection -> CollectionScreen(vm, screen.source, actions)
            is Screen.Viewer -> Unit
            is Screen.Editor -> EditorHost(
                photo = screen.photo,
                store = vm.store,
                directWrite = false,
                startInCrop = screen.crop || vm.store.startInCrop,
                onClose = vm::back,
                onSaved = {
                    vm.reload()
                    vm.back()
                },
                onDeleted = {
                    vm.reload()
                    vm.back()
                },
            )
        }
        if (top is Screen.Viewer) ViewerScreen(vm, top, actions)
    }
    vm.busy?.let { BusyDialog(it) }
}
