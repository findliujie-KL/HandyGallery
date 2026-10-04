package com.lmxyapp.handygallery.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.lmxyapp.handygallery.AppLocale
import com.lmxyapp.handygallery.R
import com.lmxyapp.handygallery.Store
import com.lmxyapp.handygallery.Trial
import com.lmxyapp.handygallery.ui.BusyCard
import com.lmxyapp.handygallery.ui.FrsTheme
import kotlinx.coroutines.launch

/** Share target: takes images from any app and forwards them as a file (one image) or a zip (several). */
class ReceiveActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Trial.blocks(this)) {
            finish()
            return
        }
        val uris = incomingUris()
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.no_image, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val theme = Store.get(this).theme
        setContent {
            FrsTheme(theme) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BusyCard(if (uris.size == 1) getString(R.string.preparing_original) else getString(R.string.zipping, uris.size))
                }
            }
        }
        // A config change would restart the copy; the work is already running from the first onCreate.
        if (savedInstanceState != null) return
        lifecycleScope.launch {
            try {
                FileSharer.launch(this@ReceiveActivity, FileSharer.prepare(applicationContext, uris))
            } catch (e: Exception) {
                Toast.makeText(this@ReceiveActivity, getString(R.string.failed, e.message), Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    private fun incomingUris(): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
}
