package com.ojitos369.lumaloop.wear

import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

/**
 * Receives from the phone:
 *  - /lumaloop/manifest (DataItem): ordered item list (ids + types), no media
 *  - /lumaloop/config (DataItem): watch face settings
 *  - /lumaloop/media/<id>.<ext> (Channel): a single streamed media file,
 *    stored in the temporary cache
 */
class ImageSyncService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            when (event.dataItem.uri.path) {
                PATH_MANIFEST -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val items = dataMap.getString("items", "[]")
                    ImageStore.setManifest(this, items)
                    ImageStore.bumpCacheVersion(this)
                    Log.i(TAG, "Manifest updated: ${ImageStore.getManifest(this).size} items")
                }
                PATH_CONFIG -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    ImageStore.setConfig(
                        this,
                        interval = dataMap.getInt("interval", 30),
                        shuffle = dataMap.getBoolean("shuffle", false),
                        showDate = dataMap.getBoolean("showDate", true),
                        showComplications = dataMap.getBoolean("showComplications", true),
                        clockPosition = dataMap.getString("clockPosition", "center") ?: "center",
                        ambientMedia = dataMap.getBoolean("ambientMedia", true)
                    )
                    Log.i(TAG, "Config updated")
                }
            }
        }
    }

    private fun requestMissingFiles() {
        try {
            val missing = ImageStore.getManifest(this)
                .filter { ImageStore.cachedFile(this, it) == null }
            if (missing.isEmpty()) return
            val nodes = com.google.android.gms.tasks.Tasks.await(
                Wearable.getNodeClient(this).connectedNodes
            )
            val node = nodes.firstOrNull() ?: return
            val messageClient = Wearable.getMessageClient(this)
            missing.forEach { entry ->
                com.google.android.gms.tasks.Tasks.await(
                    messageClient.sendMessage(node.id, PATH_FETCH, entry.id.toByteArray())
                )
            }
            Log.i(TAG, "Requested ${missing.size} missing files from phone")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request missing files", e)
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        if (!path.startsWith(PATH_MEDIA_PREFIX)) return
        val fileName = path.removePrefix(PATH_MEDIA_PREFIX)
        Log.i(TAG, "Receiving stream $fileName")
        val target = File(ImageStore.cacheDir(this), "$fileName.tmp")
        Wearable.getChannelClient(this)
            .receiveFile(channel, Uri.fromFile(target), false)
    }

    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appErrorCode: Int) {
        val path = channel.path
        if (!path.startsWith(PATH_MEDIA_PREFIX)) return
        val fileName = path.removePrefix(PATH_MEDIA_PREFIX)
        val tmp = File(ImageStore.cacheDir(this), "$fileName.tmp")
        if (closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL && tmp.isFile && tmp.length() > 0) {
            val id = fileName.substringBefore('.')
            if (ImageStore.getManifest(this).any { it.id == id }) {
                tmp.renameTo(File(ImageStore.cacheDir(this), fileName))
                ImageStore.bumpCacheVersion(this)
                Log.i(TAG, "Stream complete: $fileName (${File(ImageStore.cacheDir(this), fileName).length() / 1024}KB)")
            } else {
                // Leftover transfer from a previous set
                tmp.delete()
                Log.i(TAG, "Discarded stale stream $fileName")
            }
        } else {
            tmp.delete()
            Log.w(TAG, "Stream failed for $fileName (reason $closeReason)")
        }
        Wearable.getChannelClient(this).close(channel)
    }

    companion object {
        private const val TAG = "LumaLoopWearSync"
        const val PATH_MANIFEST = "/lumaloop/manifest"
        const val PATH_CONFIG = "/lumaloop/config"
        const val PATH_MEDIA_PREFIX = "/lumaloop/media/"
        const val PATH_FETCH = "/lumaloop/fetch"
    }
}
