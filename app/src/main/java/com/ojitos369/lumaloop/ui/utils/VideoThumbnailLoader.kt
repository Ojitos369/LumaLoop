package com.ojitos369.lumaloop.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoThumbnailLoader {
    
    suspend fun loadThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            // Get frame at 1 second or first frame
            retriever.getFrameAtTime(1000000) // 1 second in microseconds
        } catch (e: Exception) {
            Log.e("VideoThumbnailLoader", "Error loading video thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("VideoThumbnailLoader", "Error releasing retriever", e)
            }
        }
    }
}
