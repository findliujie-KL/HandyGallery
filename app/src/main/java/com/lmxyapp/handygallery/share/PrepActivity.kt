package com.lmxyapp.handygallery.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.lmxyapp.handygallery.AppLocale
import com.lmxyapp.handygallery.Mark
import com.lmxyapp.handygallery.Media
import com.lmxyapp.handygallery.Photo
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Store
import com.lmxyapp.handygallery.TopTagger
import com.lmxyapp.handygallery.Trial
import com.lmxyapp.handygallery.ui.BusyCard
import com.lmxyapp.handygallery.ui.HgTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target: puts the shared photos in the Front Row without opening the gallery. Unlike
 * [ReceiveActivity] this changes the photos themselves, so they must be ones in the media library.
 */
class PrepActivity : ComponentActivity() {
    private var photos = emptyList<Photo>()
    private var shared = 0

    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) prep() else finish()
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Trial.blocks(this)) {
            finish()
            return
        }
        val uris = incomingUris()
        shared = uris.size
        if (uris.isEmpty()) return leave(getString(R.string.no_image))
        if (!Media.canRead(this)) return leave(getString(R.string.need_access))

        val store = Store.get(this)
        setContent {
            HgTheme(store.theme) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BusyCard(getString(R.string.writing_marker)) }
            }
        }
        lifecycleScope.launch {
            photos = Media.resolve(applicationContext, uris)
            if (photos.isEmpty()) return@launch leave(getString(R.string.prep_not_found))
            val request = MediaStore.createWriteRequest(contentResolver, photos.map { it.uri })
            consent.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    private fun prep() {
        val store = Store.get(this)
        lifecycleScope.launch {
            val existing = store.marks
            val marks = withContext(Dispatchers.IO) {
                photos.mapNotNull { photo ->
                    val original = existing[photo.id]?.originalModified ?: photo.dateModified
                    TopTagger.tag(applicationContext, photo)?.let { photo.id to Mark(it, original) }
                }.toMap()
            }
            store.mark(marks)
            // Photos that weren't in the gallery count as failed along with any that couldn't be written.
            leave(
                if (marks.size == shared) getString(R.string.prepped_ok, marks.size)
                else getString(R.string.prepped_partial, marks.size, shared - marks.size),
            )
        }
    }

    private fun leave(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun incomingUris(): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
}
