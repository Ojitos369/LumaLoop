package com.ojitos369.lumaloop.service

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.ojitos369.lumaloop.ui.utils.WatchRepo

/**
 * Streams individual media files to the watch on demand.
 *
 * The watch face sends a message on /lumaloop/fetch with the item id as
 * payload; this service prepares the file (downscaled image with its watch
 * crop applied, or the video as-is) and streams it back over a Channel at
 * /lumaloop/media/<id>.<ext>. The watch keeps only a small temporary cache.
 */
class WatchMediaProviderService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchRepo.PATH_FETCH) return
        val id = String(event.data)
        Log.i(TAG, "Fetch request for $id from ${event.sourceNodeId}")

        val prepared = WatchRepo.prepareMediaFile(this, id)
        if (prepared == null) {
            Log.w(TAG, "Unknown or unreadable item $id")
            return
        }
        val (file, type) = prepared
        val ext = if (type == "v") "mp4" else "jpg"

        try {
            val channelClient = Wearable.getChannelClient(this)
            val channel = Tasks.await(
                channelClient.openChannel(
                    event.sourceNodeId,
                    "${WatchRepo.PATH_MEDIA_PREFIX}$id.$ext"
                )
            )
            Tasks.await(channelClient.sendFile(channel, Uri.fromFile(file)))
            Log.i(TAG, "Streaming $id.$ext (${file.length() / 1024}KB) to watch")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream $id", e)
        }
    }

    companion object {
        private const val TAG = "WatchMediaProvider"
    }
}
