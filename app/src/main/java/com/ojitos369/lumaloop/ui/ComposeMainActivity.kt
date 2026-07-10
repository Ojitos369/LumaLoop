package com.ojitos369.lumaloop.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ojitos369.lumaloop.ui.theme.SlideshowWallpaperTheme
import com.ojitos369.lumaloop.ui.utils.WatchRepo
import kotlinx.coroutines.launch

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Reconcile the LumaLoopWatch album (picks up files added externally)
        // and push manifest + config to the watch on startup
        if (WatchRepo.isEnabled(this)) {
            lifecycleScope.launch { WatchRepo.pushAll(this@ComposeMainActivity) }
        }
        
        // Handle shared media from gallery
        val sharedUris = when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        
        setContent {
            SlideshowWallpaperTheme {
                MainScreen(sharedUris = sharedUris, activity = this)
            }
        }
    }
}
