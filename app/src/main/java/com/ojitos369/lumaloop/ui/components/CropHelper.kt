package com.ojitos369.lumaloop.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.core.content.ContextCompat
import com.yalantis.ucrop.UCrop
import com.ojitos369.lumaloop.R
import java.io.File

object CropHelper {
    
    fun launchCrop(
        context: Context,
        sourceUri: Uri,
        cropLauncher: ActivityResultLauncher<Intent>,
        aspectRatios: List<Pair<String, Pair<Int, Int>>> = listOf(
            "Auto" to (16 to 9),
            "9:16" to (9 to 16),
            "16:9" to (16 to 9),
            "4:3" to (4 to 3),
            "3:4" to (3 to 4),
            "1:1" to (1 to 1)
        )
    ) {
        Log.d("CropHelper", "launchCrop called with sourceUri: $sourceUri")
        val options = UCrop.Options()
        
        //Get screen dimensions
        val (width, height) = getScreenDimensions(context)

        // Add Wallpaper option to the beginning of the list
        val wallpaperRatio = "Wallpaper" to (width to height)
        val allAspectRatios = listOf(wallpaperRatio) + aspectRatios
        
        // Set aspect ratio options
        val aspectRatioArray = allAspectRatios.map { (name, ratio) ->
            com.yalantis.ucrop.model.AspectRatio(name, ratio.first.toFloat(), ratio.second.toFloat())
        }.toTypedArray()
        
        options.setAspectRatioOptions(0, *aspectRatioArray) // 0 = Wallpaper default
        
        // Set colors to match Material 3 theme
        options.setToolbarColor(ContextCompat.getColor(context, R.color.primaryColor))
        options.setStatusBarColor(ContextCompat.getColor(context, R.color.primaryDarkColor))
        options.setActiveControlsWidgetColor(ContextCompat.getColor(context, R.color.primaryColor))
        
        // Create temporary destination in cache
        val destinationFileName = "crop_temp_${System.currentTimeMillis()}.jpg"
        val cacheFile = File(context.cacheDir, destinationFileName)
        val destinationUri = Uri.fromFile(cacheFile)
        Log.d("CropHelper", "Destination URI created: $destinationUri")
        
        // Create UCrop intent
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withMaxResultSize(1920, 1920)
            .withOptions(options)
        
        val intent = uCrop.getIntent(context)
        Log.d("CropHelper", "Launching UCrop intent")
        cropLauncher.launch(intent)
    }
    
    private fun getScreenDimensions(context: Context): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = (context as? android.app.Activity)?.windowManager?.currentWindowMetrics
            val insets = windowMetrics?.windowInsets?.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
            )
            val width = (windowMetrics?.bounds?.width() ?: 1080) - (insets?.left ?: 0) - (insets?.right ?: 0)
            val height = (windowMetrics?.bounds?.height() ?: 1920) - (insets?.top ?: 0) - (insets?.bottom ?: 0)
            width to height
        } else {
            val displayMetrics = context.resources.displayMetrics
            displayMetrics.widthPixels to displayMetrics.heightPixels
        }
    }
}

@Composable
fun CropButton(
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(
            Icons.Default.Crop,
            contentDescription = "Crop",
            tint = if (enabled) Color.White else Color.Gray
        )
    }
}
