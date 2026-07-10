package com.ojitos369.lumaloop.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val RATIO_OPTIONS = listOf("Original", "1:1", "9:16", "3:4", "16:9", "4:3")

/**
 * Watch video editor: trim (duration) + aspect-ratio center-crop, in the
 * spirit of the Galaxy Gallery editor. Exports a muted, watch-sized
 * (~480p) H.264 file via Media3 Transformer.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun WatchVideoEditorDialog(
    activity: ComponentActivity,
    uri: Uri,
    onDismiss: () -> Unit,
    onSaved: (Uri) -> Unit
) {
    val context = LocalContext.current

    var durationMs by remember { mutableStateOf(0L) }
    var trimRange by remember { mutableStateOf(0f..0f) }
    var ratio by remember { mutableStateOf("Original") }
    var sourceW by remember { mutableStateOf(0) }
    var sourceH by remember { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }

    val player = remember {
        ExoPlayer.Builder(activity).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && durationMs <= 0) {
                    durationMs = player.duration.coerceAtLeast(0L)
                    trimRange = 0f..(durationMs / 1000f)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            transformer?.cancel()
        }
    }

    // Source dimensions (rotation-corrected) for the "Original" ratio target
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { sourceW = h; sourceH = w } else { sourceW = w; sourceH = h }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    // Keep preview looping inside the trim range
    LaunchedEffect(trimRange, exporting) {
        while (!exporting) {
            val startMs = (trimRange.start * 1000).toLong()
            val endMs = (trimRange.endInclusive * 1000).toLong()
            if (endMs > startMs && (player.currentPosition > endMs || player.currentPosition < startMs - 500)) {
                player.seekTo(startMs)
            }
            delay(250)
        }
    }

    // Poll export progress
    LaunchedEffect(exporting) {
        val holder = ProgressHolder()
        while (exporting) {
            transformer?.getProgress(holder)
            exportProgress = holder.progress / 100f
            delay(200)
        }
    }

    fun targetSize(): Pair<Int, Int> = when (ratio) {
        "1:1" -> 480 to 480
        "9:16" -> 480 to 854
        "3:4" -> 480 to 640
        "16:9" -> 854 to 480
        "4:3" -> 640 to 480
        else -> {
            if (sourceW > 0 && sourceH > 0) {
                val scale = 480f / minOf(sourceW, sourceH)
                val w = if (scale < 1f) (sourceW * scale).toInt() else sourceW
                val h = if (scale < 1f) (sourceH * scale).toInt() else sourceH
                (w - w % 2) to (h - h % 2)
            } else 480 to 854
        }
    }

    fun startExport() {
        if (exporting) return
        exporting = true
        errorMessage = null
        player.pause()

        val outDir = File(activity.filesDir, "watch_media").apply { mkdirs() }
        val outFile = File(outDir, "wm_${System.currentTimeMillis()}_edit.mp4")

        val startMs = (trimRange.start * 1000).toLong()
        val endMs = (trimRange.endInclusive * 1000).toLong().coerceAtLeast(startMs + 500)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val (tw, th) = targetSize()
        val layout = if (ratio == "Original") Presentation.LAYOUT_SCALE_TO_FIT
                     else Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        val videoEffects = listOf<Effect>(Presentation.createForWidthAndHeight(tw, th, layout))

        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true) // watch face plays muted
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val t = Transformer.Builder(activity)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    exporting = false
                    onSaved(Uri.fromFile(outFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    exporting = false
                    outFile.delete()
                    errorMessage = "Export failed: ${exportException.errorCodeName}"
                }
            })
            .build()
        transformer = t
        t.start(edited, outFile.absolutePath)
    }

    Dialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Edit Video for Watch",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Trim range
                val start = trimRange.start
                val end = trimRange.endInclusive
                Text(
                    text = "Trim: ${formatTime(start)} – ${formatTime(end)}  (${formatTime(end - start)})",
                    style = MaterialTheme.typography.labelLarge
                )
                RangeSlider(
                    value = trimRange,
                    onValueChange = { range ->
                        trimRange = range
                        player.seekTo((range.start * 1000).toLong())
                    },
                    valueRange = 0f..(durationMs / 1000f).coerceAtLeast(1f),
                    enabled = !exporting && durationMs > 0
                )

                // Aspect ratio
                Text(text = "Ratio", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RATIO_OPTIONS) { option ->
                        FilterChip(
                            selected = ratio == option,
                            onClick = { if (!exporting) ratio = option },
                            label = { Text(option) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (exporting) {
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Exporting... ${(exportProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !exporting) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { startExport() }, enabled = !exporting && durationMs > 0) {
                        Text("Save for Watch")
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
