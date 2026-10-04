package com.findliujie.fullresshare

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.findliujie.fullresshare.ui.EditorHost
import com.findliujie.fullresshare.ui.FrsTheme

/** Lets other apps open an image in our editor through the standard "edit" intent. */
class EditActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val style = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val store = Store(this)
        val photo = Media.photoFor(this, uri, intent.type)
        val directWrite = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0

        setContent {
            FrsTheme(store.theme) {
                EditorHost(
                    photo = photo,
                    store = store,
                    directWrite = directWrite,
                    onClose = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onSaved = { copy ->
                        // By convention the caller gets back the image that now holds the edit.
                        val result = Intent().setDataAndType(copy ?: uri, if (copy != null) "image/jpeg" else photo.mime)
                        setResult(RESULT_OK, result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        finish()
                    },
                )
            }
        }
    }
}
