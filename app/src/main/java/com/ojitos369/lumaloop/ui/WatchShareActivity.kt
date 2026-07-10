package com.ojitos369.lumaloop.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ojitos369.lumaloop.ui.theme.SlideshowWallpaperTheme
import com.ojitos369.lumaloop.ui.utils.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target "LumaLoopWatch": everything shared here is copied into the
 * LumaLoopWatch album (the watch gallery) without touching the phone
 * wallpaper gallery. Compression happens later, at sync time.
 *
 * The activity stays visible with a progress indicator until the copies
 * finish: the read grants on the shared uris only live as long as this
 * activity, and killing it mid-copy is what used to corrupt imports.
 */
class WatchShareActivity : ComponentActivity() {

    private var progressText by mutableStateOf("Preparing...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND ->
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) } ?: emptyList()
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            SlideshowWallpaperTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Adding to watch gallery",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            var imported = 0
            // NonCancellable per item: never leave a half-written file
            withContext(Dispatchers.IO + NonCancellable) {
                uris.forEachIndexed { index, uri ->
                    withContext(Dispatchers.Main) {
                        progressText = "Copying ${index + 1} of ${uris.size}..."
                    }
                    // Plain copy; compression happens when syncing to the watch
                    val result = MediaStoreHelper.copyToPublicAlbum(
                        this@WatchShareActivity, uri, null, MediaStoreHelper.WATCH_ALBUM_NAME
                    )
                    if (result != null) imported++
                }
            }
            val message = if (imported == 0) "Could not import the shared items"
                else "Added $imported of ${uris.size}. Open the Watch tab and tap Sync"
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
