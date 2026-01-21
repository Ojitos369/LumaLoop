package com.ojitos369.lumaloop.ui.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileHelper {
    
    suspend fun copyToPrivateStorage(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            
            // Get MIME type to determine extension
            val mimeType = context.contentResolver.getType(sourceUri)
            val extension = when {
                mimeType?.startsWith("video/") == true -> {
                    // Try to get actual video extension
                    when {
                        mimeType.contains("mp4") -> ".mp4"
                        mimeType.contains("3gp") -> ".3gp"
                        mimeType.contains("webm") -> ".webm"
                        else -> ".mp4"
                    }
                }
                mimeType?.startsWith("image/") == true -> {
                    when {
                        mimeType.contains("png") -> ".png"
                        mimeType.contains("webp") -> ".webp"
                        else -> ".jpg"
                    }
                }
                else -> ".jpg"
            }
            
            // Create app-specific directory
            val mediaDir = File(context.filesDir, "slideshow_media")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            // Create unique filename
            val fileName = "media_${System.currentTimeMillis()}$extension"
            val destFile = File(mediaDir, fileName)
            
            // Copy file
            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Return content URI via FileProvider (not file://)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        } catch (e: Exception) {
            Log.e("FileHelper", "Error copying file to private storage", e)
            null
        }
    }
}
