package com.ojitos369.lumaloop.ui.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreHelper {
    private const val ALBUM_NAME = "LumaLoop"
    private const val TAG = "MediaStoreHelper"
    
    suspend fun copyToPublicAlbum(context: Context, sourceUri: Uri, originalName: String? = null): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
            
            // Get MIME type
            val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
            val isVideo = mimeType.startsWith("video/")
            
            // Determine file extension
            val extension = when {
                mimeType.contains("mp4") -> ".mp4"
                mimeType.contains("3gp") -> ".3gp"
                mimeType.contains("webm") -> ".webm"
                mimeType.contains("png") -> ".png"
                mimeType.contains("webp") -> ".webp"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                else -> if (isVideo) ".mp4" else ".jpg"
            }
            
            val displayName = if (originalName != null) {
                "$originalName$extension"
            } else {
                "media_${System.currentTimeMillis()}$extension"
            }
            
            // Prepare content values
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            // Choose collection
            val collection = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }
            
            // Insert into MediaStore
            val newUri = context.contentResolver.insert(collection, contentValues)
                ?: return@withContext null
            
            // Copy content
            context.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            
            // Mark as not pending
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(newUri, contentValues, null, null)
            }
            
            inputStream.close()
            Log.d(TAG, "Successfully copied to album: $newUri")
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to public album", e)
            null
        }
    }
    
    fun getAlbumContent(context: Context): List<Uri> {
        val albumUris = mutableListOf<Uri>()
        
        try {
            // Query images
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            
            val imageSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                null
            }
            
            val imageSelectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%Pictures/$ALBUM_NAME%")
            } else {
                null
            }
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                imageSelection,
                imageSelectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    albumUris.add(uri)
                }
            }
            
            // Query videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.RELATIVE_PATH
            )
            
            val videoSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            } else {
                null
            }
            
            val videoSelectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%Pictures/$ALBUM_NAME%")
            } else {
                null
            }
            
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                videoSelectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    albumUris.add(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting album content", e)
        }
        
        return albumUris
    }

    fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDisplayName Exception", e)
            null
        }
    }

    fun getRealPathFromUri(context: Context, contentUri: Uri): String? {
        var cursor: android.database.Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(contentUri, proj, null, null, null)
            val column_index = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor?.moveToFirst()
            if (column_index != null) cursor?.getString(column_index) else null
        } catch (e: Exception) {
            Log.e(TAG, "getRealPathFromUri Exception", e)
            null
        } finally {
            cursor?.close()
        }
    }
}
