package com.ojitos369.lumaloop.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Compresses media on its way into the LumaLoopWatch album so files stay
 * light for the watch: images are downscaled JPEGs, videos are re-encoded
 * to ~480p H.264 with the audio removed (the watch face plays muted).
 * Falls back to a plain copy when compression fails.
 */
object WatchMediaCompressor {

    private const val TAG = "WatchMediaCompressor"
    private const val IMAGE_MAX_DIMENSION = 1080
    private const val IMAGE_JPEG_QUALITY = 85
    private const val VIDEO_SHORT_SIDE = 480

    suspend fun compressAndImport(context: Context, sourceUri: Uri, originalName: String?): Uri? {
        val mime = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
        return try {
            if (mime.startsWith("video/")) {
                val compressed = transcodeVideo(context, sourceUri)
                if (compressed != null) {
                    val uri = MediaStoreHelper.copyToPublicAlbum(
                        context, Uri.fromFile(compressed), originalName,
                        MediaStoreHelper.WATCH_ALBUM_NAME
                    )
                    compressed.delete()
                    uri
                } else {
                    Log.w(TAG, "Video transcode failed, importing original: $sourceUri")
                    MediaStoreHelper.copyToPublicAlbum(
                        context, sourceUri, originalName, MediaStoreHelper.WATCH_ALBUM_NAME
                    )
                }
            } else {
                val compressed = compressImage(context, sourceUri)
                if (compressed != null) {
                    val uri = MediaStoreHelper.copyToPublicAlbum(
                        context, Uri.fromFile(compressed), originalName,
                        MediaStoreHelper.WATCH_ALBUM_NAME
                    )
                    compressed.delete()
                    uri
                } else {
                    MediaStoreHelper.copyToPublicAlbum(
                        context, sourceUri, originalName, MediaStoreHelper.WATCH_ALBUM_NAME
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed for $sourceUri", e)
            null
        }
    }

    private suspend fun compressImage(context: Context, uri: Uri): File? =
        withContext(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

                var sample = 1
                while (boundsOpts.outWidth / (sample * 2) >= IMAGE_MAX_DIMENSION &&
                    boundsOpts.outHeight / (sample * 2) >= IMAGE_MAX_DIMENSION
                ) sample *= 2

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext null

                val out = File(context.cacheDir, "watch_import_${System.currentTimeMillis()}.jpg")
                out.outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, it)
                }
                bmp.recycle()
                out
            } catch (e: Exception) {
                Log.w(TAG, "Image compress failed: $uri", e)
                null
            }
        }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun transcodeVideo(context: Context, uri: Uri): File? {
        val (w, h) = videoSize(context, uri) ?: return null
        val scale = VIDEO_SHORT_SIDE.toFloat() / minOf(w, h)
        val (tw, th) = if (scale < 1f) {
            val sw = (w * scale).toInt().let { it - it % 2 }
            val sh = (h * scale).toInt().let { it - it % 2 }
            sw to sh
        } else {
            (w - w % 2) to (h - h % 2)
        }

        val out = File(context.cacheDir, "watch_import_${System.currentTimeMillis()}.mp4")
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        Presentation.createForWidthAndHeight(tw, th, Presentation.LAYOUT_SCALE_TO_FIT)
                    )
                )
            )
            .build()

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            cont.resume(out)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException
                        ) {
                            Log.w(TAG, "Transcode failed: ${exception.errorCodeName}")
                            out.delete()
                            cont.resume(null)
                        }
                    })
                    .build()
                transformer.start(edited, out.absolutePath)
                cont.invokeOnCancellation {
                    transformer.cancel()
                    out.delete()
                }
            }
        }
    }

    /**
     * Blocking variant for non-coroutine callers (Wearable listener threads).
     * Transformer must run on a Looper thread, so the work is posted to the
     * main thread and awaited here.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun transcodeVideoBlocking(context: Context, uri: Uri, out: File): Boolean {
        val size = videoSize(context, uri) ?: return false
        val (w, h) = size
        val scale = VIDEO_SHORT_SIDE.toFloat() / minOf(w, h)
        val (tw, th) = if (scale < 1f) {
            val sw = (w * scale).toInt().let { it - it % 2 }
            val sh = (h * scale).toInt().let { it - it % 2 }
            sw to sh
        } else {
            (w - w % 2) to (h - h % 2)
        }

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        Presentation.createForWidthAndHeight(tw, th, Presentation.LAYOUT_SCALE_TO_FIT)
                    )
                )
            )
            .build()

        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            ok = true
                            latch.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException
                        ) {
                            Log.w(TAG, "Blocking transcode failed: ${exception.errorCodeName}")
                            latch.countDown()
                        }
                    })
                    .build()
                transformer.start(edited, out.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Blocking transcode setup failed", e)
                latch.countDown()
            }
        }
        latch.await(5, java.util.concurrent.TimeUnit.MINUTES)
        if (!ok) out.delete()
        return ok
    }

    private fun videoSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) h to w else w to h
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
