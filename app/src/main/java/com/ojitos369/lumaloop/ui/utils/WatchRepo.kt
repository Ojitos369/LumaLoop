package com.ojitos369.lumaloop.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Watch media repository and sync coordinator.
 *
 * - Watch media lives in the public MediaStore album "LumaLoopWatch",
 *   independent from the phone wallpaper album.
 * - The watch never stores the full set: the phone pushes a small manifest
 *   (item ids + types) plus a config data item, and streams individual
 *   files on demand when the watch face requests them (see
 *   [com.ojitos369.lumaloop.service.WatchMediaProviderService]).
 */
object WatchRepo {

    private const val TAG = "WatchRepo"

    const val PATH_MANIFEST = "/lumaloop/manifest"
    const val PATH_CONFIG = "/lumaloop/config"
    const val PATH_FETCH = "/lumaloop/fetch"
    const val PATH_MEDIA_PREFIX = "/lumaloop/media/"

    private const val KEY_ENABLED = "watch_enabled"
    private const val KEY_INTERVAL = "watch_interval"
    private const val KEY_SHUFFLE = "watch_shuffle"
    private const val KEY_SHOW_DATE = "watch_show_date"
    private const val KEY_SHOW_COMPLICATIONS = "watch_show_complications"
    private const val KEY_CLOCK_POSITION = "watch_clock_position" // top|center|bottom
    private const val KEY_AMBIENT_MEDIA = "watch_ambient_media"
    private const val KEY_ORDER_LIST = "watch_order_list"
    private const val KEY_MANIFEST_MAP = "watch_manifest_map"

    private const val MAX_DIMENSION = 640
    private const val JPEG_QUALITY = 82
    private const val MAX_UNCOMPRESSED_VIDEO_BYTES = 4L * 1024 * 1024

    private fun prefs(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)

    // ---------------------------------------------------------------- settings

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, v).apply()

    fun interval(context: Context) = prefs(context).getInt(KEY_INTERVAL, 30)
    fun setInterval(context: Context, v: Int) =
        prefs(context).edit().putInt(KEY_INTERVAL, v.coerceAtLeast(5)).apply()

    fun shuffle(context: Context) = prefs(context).getBoolean(KEY_SHUFFLE, false)
    fun setShuffle(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHUFFLE, v).apply()

    fun showDate(context: Context) = prefs(context).getBoolean(KEY_SHOW_DATE, true)
    fun setShowDate(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_DATE, v).apply()

    fun showComplications(context: Context) = prefs(context).getBoolean(KEY_SHOW_COMPLICATIONS, true)
    fun setShowComplications(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_COMPLICATIONS, v).apply()

    fun clockPosition(context: Context) = prefs(context).getString(KEY_CLOCK_POSITION, "center") ?: "center"
    fun setClockPosition(context: Context, v: String) =
        prefs(context).edit().putString(KEY_CLOCK_POSITION, v).apply()

    fun ambientMedia(context: Context) = prefs(context).getBoolean(KEY_AMBIENT_MEDIA, true)
    fun setAmbientMedia(context: Context, v: Boolean) =
        prefs(context).edit().putBoolean(KEY_AMBIENT_MEDIA, v).apply()

    // ---------------------------------------------------------------- items

    /**
     * Watch set = contents of the LumaLoopWatch album, kept in the stored
     * order. New album files (e.g. dropped there from the system gallery)
     * are appended automatically; missing ones are pruned.
     */
    fun getWatchItems(context: Context): List<Uri> {
        val albumUris = MediaStoreHelper
            .getAlbumContent(context, MediaStoreHelper.WATCH_ALBUM_NAME)
            .toSet()
        val stored = (prefs(context).getString(KEY_ORDER_LIST, "") ?: "")
            .split("\n").filter { it.isNotBlank() }.map { Uri.parse(it) }
        val kept = stored.filter { it in albumUris }
        val appended = albumUris.filter { it !in kept.toSet() }
        val result = kept + appended
        if (result != stored) saveOrder(context, result)
        return result
    }

    private fun saveOrder(context: Context, uris: List<Uri>) {
        prefs(context).edit()
            .putString(KEY_ORDER_LIST, uris.joinToString("\n") { it.toString() })
            .apply()
    }

    /** Copies media into the LumaLoopWatch album. Returns the new uris. */
    suspend fun importToWatchAlbum(context: Context, uris: List<Uri>): List<Uri> {
        val imported = mutableListOf<Uri>()
        for (uri in uris) {
            val newUri = MediaStoreHelper.copyToPublicAlbum(
                context, uri, null, MediaStoreHelper.WATCH_ALBUM_NAME
            )
            if (newUri != null) imported.add(newUri) else Log.w(TAG, "Import failed: $uri")
        }
        if (imported.isNotEmpty()) {
            saveOrder(context, getWatchItems(context)) // reconcile appends them
        }
        return imported
    }

    /** Adds an already-app-owned file (e.g. edited video) to the album. */
    suspend fun addFileToWatchAlbum(context: Context, file: File): Uri? =
        MediaStoreHelper.copyToPublicAlbum(
            context, Uri.fromFile(file), file.nameWithoutExtension,
            MediaStoreHelper.WATCH_ALBUM_NAME
        )

    /** Removes items from the watch set and deletes them from the album. */
    suspend fun removeFromWatch(context: Context, uris: Collection<Uri>): Int =
        withContext(Dispatchers.IO) {
            var removed = 0
            uris.forEach { uri ->
                clearWatchCrop(context, uri)
                try {
                    context.contentResolver.delete(uri, null, null)
                    removed++
                } catch (e: Exception) {
                    Log.e(TAG, "Could not delete $uri", e)
                }
            }
            saveOrder(context, getWatchItems(context))
            removed
        }

    fun isVideo(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)
            ?: (uri.path?.lowercase() ?: "")
        return mime.startsWith("video/") ||
            mime.endsWith(".mp4") || mime.endsWith(".webm") || mime.endsWith(".mkv")
    }

    // ---------------------------------------------------------------- crops

    fun watchCropFile(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "watch_crops").apply { mkdirs() }
        return File(dir, "${md5(uri.toString())}.jpg")
    }

    fun saveWatchCrop(context: Context, sourceUri: Uri, croppedUri: Uri): Boolean {
        return try {
            val target = watchCropFile(context, sourceUri)
            context.contentResolver.openInputStream(croppedUri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watch crop for $sourceUri", e)
            false
        }
    }

    fun clearWatchCrop(context: Context, sourceUri: Uri) {
        watchCropFile(context, sourceUri).delete()
    }

    // ---------------------------------------------------------------- manifest / config

    /** Stable id per uri; sync always resends full content anyway. */
    fun itemId(context: Context, uri: Uri): String = md5(uri.toString())

    /**
     * Full sync: replaces the watch set. Pushes config + manifest (the watch
     * prunes everything not in it), then streams every file one by one so
     * the watch starts displaying as soon as the first one lands.
     */
    suspend fun syncAllToWatch(
        context: Context,
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): PushResult = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val node = nodes.firstOrNull() ?: return@withContext PushResult.NoWatchConnected

            pushConfig(context)

            // Manifest first: the watch drops items that left the set
            val map = try {
                JSONObject(prefs(context).getString(KEY_MANIFEST_MAP, "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val arr = JSONArray()
            uris.forEach { uri ->
                val id = itemId(context, uri)
                map.put(id, uri.toString())
                arr.put(JSONObject().put("id", id).put("t", if (isVideo(context, uri)) "v" else "i"))
            }
            prefs(context).edit().putString(KEY_MANIFEST_MAP, map.toString()).apply()

            val request = PutDataMapRequest.create(PATH_MANIFEST)
            request.dataMap.putString("items", arr.toString())
            request.dataMap.putLong("ts", System.currentTimeMillis())
            request.setUrgent()
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()

            // Stream files sequentially; first arrival can display immediately
            val channelClient = Wearable.getChannelClient(context)
            var sent = 0
            uris.forEachIndexed { i, uri ->
                val id = itemId(context, uri)
                val prepared = prepareMediaFile(context, id)
                if (prepared != null) {
                    val (file, type) = prepared
                    val ext = if (type == "v") "mp4" else "jpg"
                    try {
                        val channel = channelClient
                            .openChannel(node.id, "$PATH_MEDIA_PREFIX$id.$ext").await()
                        channelClient.sendFile(channel, Uri.fromFile(file)).await()
                        sent++
                        Log.i(TAG, "Sent $id.$ext (${file.length() / 1024}KB) [${i + 1}/${uris.size}]")
                    } catch (e: Exception) {
                        Log.e(TAG, "Send failed for $id", e)
                    }
                }
                onProgress(i + 1, uris.size)
            }
            PushResult.Success(sent)
        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed", e)
            PushResult.Error(e.message ?: "Unknown error")
        }
    }

    sealed class PushResult {
        data class Success(val items: Int) : PushResult()
        object NoWatchConnected : PushResult()
        data class Error(val message: String) : PushResult()
    }

    /** Pushes the item list (ids only, no media) to the watch. */
    suspend fun pushManifest(context: Context): PushResult = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return@withContext PushResult.NoWatchConnected

            val items = getWatchItems(context)
            // Accumulative id map: keep older ids resolvable so requests made
            // against a previous manifest (or interrupted transfers) still work
            val map = try {
                JSONObject(prefs(context).getString(KEY_MANIFEST_MAP, "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val arr = JSONArray()
            items.forEach { uri ->
                val id = itemId(context, uri)
                map.put(id, uri.toString())
                arr.put(JSONObject().put("id", id).put("t", if (isVideo(context, uri)) "v" else "i"))
            }
            // Bound the map: drop stale entries once it grows past 300 ids
            if (map.length() > 300) {
                val current = items.map { itemId(context, it) }.toSet()
                map.keys().asSequence().toList()
                    .filter { it !in current }
                    .take(map.length() - 300)
                    .forEach { map.remove(it) }
            }
            prefs(context).edit().putString(KEY_MANIFEST_MAP, map.toString()).apply()

            val request = PutDataMapRequest.create(PATH_MANIFEST)
            request.dataMap.putString("items", arr.toString())
            request.dataMap.putLong("ts", System.currentTimeMillis())
            request.setUrgent()
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
            Log.i(TAG, "Manifest pushed: ${items.size} items")
            PushResult.Success(items.size)
        } catch (e: Exception) {
            Log.e(TAG, "Manifest push failed", e)
            PushResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Pushes the watch settings to the watch. */
    suspend fun pushConfig(context: Context): PushResult = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return@withContext PushResult.NoWatchConnected

            val request = PutDataMapRequest.create(PATH_CONFIG)
            request.dataMap.putInt("interval", interval(context))
            request.dataMap.putBoolean("shuffle", shuffle(context))
            request.dataMap.putBoolean("showDate", showDate(context))
            request.dataMap.putBoolean("showComplications", showComplications(context))
            request.dataMap.putString("clockPosition", clockPosition(context))
            request.dataMap.putBoolean("ambientMedia", ambientMedia(context))
            request.dataMap.putLong("ts", System.currentTimeMillis())
            request.setUrgent()
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
            Log.i(TAG, "Config pushed")
            PushResult.Success(0)
        } catch (e: Exception) {
            Log.e(TAG, "Config push failed", e)
            PushResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun pushAll(context: Context): PushResult {
        pushConfig(context)
        return pushManifest(context)
    }

    // ---------------------------------------------------------------- streaming

    fun uriForId(context: Context, id: String): Uri? {
        val json = prefs(context).getString(KEY_MANIFEST_MAP, null) ?: return null
        return try {
            val map = JSONObject(json)
            if (map.has(id)) Uri.parse(map.getString(id)) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prepares a plain readable file for streaming to the watch.
     * Images are downscaled (watch crop wins); videos are copied out of
     * MediaStore into cache as-is.
     */
    fun prepareMediaFile(context: Context, id: String): Pair<File, String>? {
        val uri = uriForId(context, id) ?: return null
        val dir = File(context.cacheDir, "watch_stream").apply { mkdirs() }
        cleanupStreamCache(dir)
        return if (isVideo(context, uri)) {
            val out = File(dir, "$id.mp4")
            if (!out.isFile || out.length() == 0L) {
                val size = try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                } catch (e: Exception) {
                    0L
                }
                if (size > MAX_UNCOMPRESSED_VIDEO_BYTES) {
                    // Heavy source (e.g. dropped into the folder externally):
                    // re-encode once to watch size before sending
                    Log.i(TAG, "Transcoding heavy video (${size / 1024 / 1024}MB) for watch: $uri")
                    if (!WatchMediaCompressor.transcodeVideoBlocking(context, uri, out)) {
                        Log.w(TAG, "Transcode failed, sending original: $uri")
                    }
                }
                if (!out.isFile || out.length() == 0L) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        } ?: return null
                    } catch (e: Exception) {
                        Log.e(TAG, "Video prepare failed for $uri", e)
                        out.delete()
                        return null
                    }
                }
            }
            out to "v"
        } else {
            val out = File(dir, "$id.jpg")
            if (!out.isFile || out.length() == 0L) {
                val crop = watchCropFile(context, uri)
                val source = if (crop.isFile && crop.length() > 0) Uri.fromFile(crop) else uri
                val bmp = decodeImage(context, source) ?: return null
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bmp.recycle()
            }
            out to "i"
        }
    }

    private fun cleanupStreamCache(dir: File) {
        val cutoff = System.currentTimeMillis() - 6 * 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        return try {
            // Bounds-only pass: decodeStream returns null here by design
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
            var sample = 1
            while (boundsOpts.outWidth / (sample * 2) >= MAX_DIMENSION &&
                boundsOpts.outHeight / (sample * 2) >= MAX_DIMENSION
            ) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image decode failed: $uri", e)
            null
        }
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
