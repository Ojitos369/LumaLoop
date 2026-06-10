package com.ojitos369.lumaloop.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import com.ojitos369.lumaloop.SlideshowWallpaperService
import com.ojitos369.lumaloop.ui.theme.neumorphic
import com.ojitos369.lumaloop.ui.theme.neumorphicInset

@Composable
fun PreviewScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(32.dp)
                .neumorphic(cornerRadius = 28.dp)
                .padding(horizontal = 28.dp, vertical = 36.dp)
        ) {
            // Concave icon well
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .neumorphicInset(cornerRadius = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = "Wallpaper Preview",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Test your slideshow wallpaper configuration",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    try {
                        // Launch wallpaper chooser for our service
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, SlideshowWallpaperService::class.java)
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general wallpaper picker
                        val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                        context.startActivity(intent)
                    }
                }
            ) {
                Text("Test Wallpaper")
            }
        }
    }
}
