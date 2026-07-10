package com.ojitos369.lumaloop.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * LumaLoop watch face, streaming edition.
 *
 * The phone pushes a manifest (item ids); media files are fetched one by
 * one over the Data Layer as playback approaches them and kept only in a
 * small temporary cache. Config (interval, order, clock position, date,
 * complications, ambient behavior) also comes from the phone.
 */
class LumaLoopWatchFaceService : WatchFaceService() {

    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager {
        val factory = CanvasComplicationFactory { watchState, listener ->
            CanvasComplicationDrawable(ComplicationDrawable(this), watchState, listener)
        }
        val supported = listOf(
            ComplicationType.SHORT_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SMALL_IMAGE
        )
        val left = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            LEFT_COMPLICATION_ID,
            factory,
            supported,
            DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_STEP_COUNT,
                ComplicationType.SHORT_TEXT
            ),
            ComplicationSlotBounds(RectF(0.17f, 0.70f, 0.43f, 0.93f))
        ).build()
        val right = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            RIGHT_COMPLICATION_ID,
            factory,
            supported,
            DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_WATCH_BATTERY,
                ComplicationType.SHORT_TEXT
            ),
            ComplicationSlotBounds(RectF(0.57f, 0.70f, 0.83f, 0.93f))
        ).build()
        return ComplicationSlotsManager(listOf(left, right), currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = SlideshowRenderer(
            applicationContext,
            surfaceHolder,
            watchState,
            currentUserStyleRepository,
            complicationSlotsManager
        )
        var lastTapMs = 0L
        return WatchFace(WatchFaceType.DIGITAL, renderer)
            .setTapListener(object : WatchFace.TapListener {
                override fun onTapEvent(
                    tapType: Int,
                    tapEvent: androidx.wear.watchface.TapEvent,
                    complicationSlot: ComplicationSlot?
                ) {
                    if (tapType == androidx.wear.watchface.TapType.UP && complicationSlot == null) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapMs <= DOUBLE_TAP_MS) {
                            lastTapMs = 0L
                            renderer.skipToNext()
                        } else {
                            lastTapMs = now
                        }
                    }
                }
            })
    }

    companion object {
        const val LEFT_COMPLICATION_ID = 100
        const val RIGHT_COMPLICATION_ID = 101
        private const val DOUBLE_TAP_MS = 400L
    }
}

class SlideshowRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val complicationSlotsManager: ComplicationSlotsManager
) : Renderer.GlesRenderer2<SlideshowRenderer.SlideshowSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    VIDEO_FRAME_MS
) {

    class SlideshowSharedAssets : SharedAssets {
        override fun onDestroy() {}
    }

    override suspend fun createSharedAssets(): SlideshowSharedAssets = SlideshowSharedAssets()

    private enum class Mode { NONE, IMAGE, VIDEO }

    // Playlist / streaming state
    private var playlist: List<ImageStore.Entry> = emptyList()
    private var loadedManifestVersion = -1L
    private var lastCacheVersion = -1L
    private var loadedConfigVersion = -1L
    private var config = ImageStore.Config()
    private var index = 0
    private var mode = Mode.NONE
    private var currentId: String? = null
    private var itemStartMs = 0L
    private var pendingShow = false
    private val requestedAt = HashMap<String, Long>()

    // Video state
    private var player: MediaPlayer? = null
    private var playerSurface: Surface? = null
    private var videoReady = false
    private var videoW = 0
    private var videoH = 0
    @Volatile private var frameAvailable = false
    private val stMatrix = FloatArray(16)

    // Image state
    private var imgW = 0
    private var imgH = 0
    private var imageLoaded = false

    // GL state
    private var glReady = false
    private var viewW = 0
    private var viewH = 0
    private var prog2d = 0
    private var progOes = 0
    private var imageTex = 0
    private var overlayTex = 0
    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private lateinit var posBuf: FloatBuffer
    private lateinit var texBufFlipped: FloatBuffer
    private lateinit var texBufNormal: FloatBuffer
    private val identityMatrix = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }

    // Overlay (clock + complications)
    private var overlayBitmap: Bitmap? = null
    private var lastOverlayKey = ""
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val ambientTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    init {
        scope.launch {
            watchState.isVisible.collect { visible ->
                if (visible == false) pauseVideo() else invalidate()
            }
        }
    }

    override suspend fun onUiThreadGlSurfaceCreated(width: Int, height: Int) {
        viewW = width
        viewH = height

        posBuf = floatBuffer(-1f, 1f, -1f, -1f, 1f, 1f, 1f, -1f)
        texBufFlipped = floatBuffer(0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f)
        texBufNormal = floatBuffer(0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)

        prog2d = buildProgram(VERTEX_SHADER, FRAGMENT_2D)
        progOes = buildProgram(VERTEX_SHADER, FRAGMENT_OES)

        imageTex = genTexture(GLES20.GL_TEXTURE_2D)
        overlayTex = genTexture(GLES20.GL_TEXTURE_2D)
        oesTex = genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

        surfaceTexture?.release()
        surfaceTexture = SurfaceTexture(oesTex).apply {
            setOnFrameAvailableListener({
                frameAvailable = true
                invalidate()
            }, mainHandler)
        }

        glReady = true
        imageLoaded = false
        videoReady = false
        mode = Mode.NONE
        lastOverlayKey = ""
        releasePlayer()
    }

    override fun render(zonedDateTime: ZonedDateTime, sharedAssets: SlideshowSharedAssets) {
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!glReady) return

        val now = System.currentTimeMillis()
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT

        refreshStateIfNeeded()

        if (isAmbient) {
            pauseVideo()
        } else {
            if (mode == Mode.NONE && playlist.isNotEmpty()) pendingShow = true
            if (pendingShow) {
                pendingShow = false
                showItem(now)
            }
            if (mode == Mode.IMAGE && playlist.size > 1 &&
                now - itemStartMs >= config.intervalSeconds * 1000L
            ) {
                advance(now)
            }
            if (mode == Mode.VIDEO && videoReady) resumeVideo()
        }

        if (mode == Mode.VIDEO && frameAvailable) {
            frameAvailable = false
            surfaceTexture?.let {
                it.updateTexImage()
                it.getTransformMatrix(stMatrix)
            }
        }

        val showMedia = !isAmbient || config.ambientMedia
        val brightness = if (isAmbient) 0.35f else 1f
        if (showMedia) {
            when (mode) {
                Mode.IMAGE -> if (imageLoaded) {
                    drawQuad(prog2d, imageTex, GLES20.GL_TEXTURE_2D, imgW, imgH, identityMatrix, brightness, texBufFlipped)
                }
                Mode.VIDEO -> if (videoReady) {
                    drawQuad(progOes, oesTex, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoW, videoH, stMatrix, brightness, texBufNormal)
                }
                Mode.NONE -> {}
            }
        }

        drawOverlay(zonedDateTime, isAmbient)
    }

    override fun renderHighlightLayer(zonedDateTime: ZonedDateTime, sharedAssets: SlideshowSharedAssets) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    // ---------------------------------------------------------------- streaming playlist

    private fun refreshStateIfNeeded() {
        val configVersion = ImageStore.configVersion(context)
        if (configVersion != loadedConfigVersion) {
            config = ImageStore.getConfig(context)
            loadedConfigVersion = configVersion
            lastOverlayKey = ""
        }

        val manifestVersion = ImageStore.manifestVersion(context)
        if (manifestVersion != loadedManifestVersion) {
            var entries = ImageStore.getManifest(context)
            if (config.shuffle && entries.size > 1) {
                entries = entries.shuffled(Random(manifestVersion))
            }
            playlist = entries
            loadedManifestVersion = manifestVersion
            index = 0
            mode = Mode.NONE
            currentId = null
            imageLoaded = false
            videoReady = false
            releasePlayer()
            requestedAt.clear()
            pendingShow = playlist.isNotEmpty()
        }

        val cacheVersion = ImageStore.cacheVersion(context)
        if (cacheVersion != lastCacheVersion) {
            lastCacheVersion = cacheVersion
            // A file may have just arrived for the item we are waiting on
            if (mode == Mode.NONE && playlist.isNotEmpty()) pendingShow = true
        }
    }

    private fun advance(now: Long) {
        if (playlist.isEmpty()) return
        index = (index + 1) % playlist.size
        showItem(now)
    }

    /** Double-tap gesture: jump to the next item. */
    fun skipToNext() {
        if (playlist.size <= 1) return
        index = (index + 1) % playlist.size
        pendingShow = true
        invalidate()
    }

    /** Must run inside render() so the GL context is current. */
    private fun showItem(now: Long) {
        if (playlist.isEmpty()) return
        if (index >= playlist.size) index = 0

        // Find the first cached entry starting at index; request the missing
        // ones we skip over so they stream in for the next rounds.
        var chosen = -1
        for (offset in 0 until playlist.size) {
            val i = (index + offset) % playlist.size
            if (ImageStore.cachedFile(context, playlist[i]) != null) {
                chosen = i
                break
            }
            requestFetch(playlist[i])
            if (offset >= 2) break // don't spam requests for the whole list
        }
        if (chosen == -1) {
            mode = Mode.NONE
            currentId = null
            lastOverlayKey = ""
            return
        }
        index = chosen
        val entry = playlist[index]
        val file = ImageStore.cachedFile(context, entry) ?: return
        currentId = entry.id
        itemStartMs = now
        releasePlayer()
        videoReady = false

        if (entry.isVideo) {
            mode = Mode.VIDEO
            interactiveDrawModeUpdateDelayMillis = VIDEO_FRAME_MS
            startVideo(file)
        } else {
            mode = Mode.IMAGE
            interactiveDrawModeUpdateDelayMillis = IMAGE_FRAME_MS
            val bmp = decodeScaled(file, viewW, viewH)
            if (bmp != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTex)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                imgW = bmp.width
                imgH = bmp.height
                imageLoaded = true
                bmp.recycle()
            } else {
                imageLoaded = false
                file.delete()
            }
        }

        prefetchNext()
    }

    /**
     * Fallback: the full set is normally pulled right after a manifest
     * update (see ImageSyncService), so this only re-requests holes.
     * Files stay on the watch until the manifest drops them.
     */
    private fun prefetchNext() {
        if (playlist.isEmpty()) return
        for (offset in 1..2) {
            val entry = playlist[(index + offset) % playlist.size]
            if (ImageStore.cachedFile(context, entry) == null) {
                requestFetch(entry)
            }
        }
    }

    private fun requestFetch(entry: ImageStore.Entry) {
        val now = System.currentTimeMillis()
        val last = requestedAt[entry.id] ?: 0L
        if (now - last < FETCH_RETRY_MS) return
        requestedAt[entry.id] = now
        scope.launch(Dispatchers.IO) {
            try {
                val nodes = com.google.android.gms.tasks.Tasks.await(
                    Wearable.getNodeClient(context).connectedNodes
                )
                nodes.firstOrNull()?.let { node ->
                    com.google.android.gms.tasks.Tasks.await(
                        Wearable.getMessageClient(context)
                            .sendMessage(node.id, PATH_FETCH, entry.id.toByteArray())
                    )
                    Log.i(TAG, "Requested ${entry.id} from phone")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch request failed for ${entry.id}", e)
            }
        }
    }

    // ---------------------------------------------------------------- video

    private fun startVideo(file: File) {
        val st = surfaceTexture ?: return
        try {
            val mp = MediaPlayer()
            player = mp
            playerSurface = Surface(st).also { mp.setSurface(it) }
            mp.setDataSource(file.absolutePath)
            mp.isLooping = playlist.size == 1
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener {
                videoW = it.videoWidth
                videoH = it.videoHeight
                videoReady = true
                it.start()
                Log.i(TAG, "Video started ${file.name} ${videoW}x$videoH playing=${it.isPlaying}")
                invalidate()
            }
            mp.setOnCompletionListener {
                if (playlist.size > 1) {
                    index = (index + 1) % playlist.size
                    pendingShow = true
                    invalidate()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Video error $what/$extra on ${file.name}")
                file.delete()
                if (playlist.size > 1) {
                    index = (index + 1) % playlist.size
                    pendingShow = true
                    invalidate()
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video ${file.name}", e)
            releasePlayer()
        }
    }

    private fun pauseVideo() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    private fun resumeVideo() {
        runCatching { player?.takeIf { !it.isPlaying }?.start() }
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
        runCatching { playerSurface?.release() }
        playerSurface = null
        frameAvailable = false
    }

    private fun decodeScaled(file: File, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var sample = 1
            while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- overlay

    private fun drawOverlay(zonedDateTime: ZonedDateTime, ambient: Boolean) {
        // Seconds shown while interactive; ambient updates once per minute
        val key = "${zonedDateTime.toEpochSecond() / (if (ambient) 60 else 1)}|$ambient|" +
            "${playlist.isEmpty()}|${mode == Mode.NONE}|$loadedConfigVersion"
        if (key != lastOverlayKey) {
            lastOverlayKey = key
            uploadOverlayBitmap(zonedDateTime, ambient)
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        drawQuad(prog2d, overlayTex, GLES20.GL_TEXTURE_2D, viewW, viewH, identityMatrix, 1f, texBufFlipped)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun uploadOverlayBitmap(zonedDateTime: ZonedDateTime, ambient: Boolean) {
        val w = viewW.coerceAtLeast(1)
        val h = viewH.coerceAtLeast(1)
        val bmp = overlayBitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { overlayBitmap = it }
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)

        val timePaint = Paint().apply {
            isAntiAlias = true
            color = if (ambient) Color.LTGRAY else Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = if (ambient) h * 0.20f else h * 0.16f
            if (!ambient) setShadowLayer(8f, 0f, 2f, Color.argb(200, 0, 0, 0))
        }
        val datePaint = Paint().apply {
            isAntiAlias = true
            color = if (ambient) Color.GRAY else Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = h * 0.055f
            if (!ambient) setShadowLayer(6f, 0f, 2f, Color.argb(200, 0, 0, 0))
        }

        val cx = w / 2f
        val centerY = when (config.clockPosition) {
            "top" -> h * 0.28f
            "bottom" -> h * 0.62f
            else -> h / 2f
        }
        val timeY = centerY + timePaint.textSize / 3f
        val formatter = if (ambient) ambientTimeFormatter else timeFormatter
        canvas.drawText(zonedDateTime.format(formatter), cx, timeY, timePaint)
        if (config.showDate) {
            canvas.drawText(
                zonedDateTime.format(dateFormatter),
                cx,
                timeY + datePaint.textSize * 1.6f,
                datePaint
            )
        }

        val hintPaint = Paint(datePaint).apply { textSize = h * 0.045f }
        if (playlist.isEmpty()) {
            canvas.drawText("Set up media in the phone app", cx, h * 0.80f, hintPaint)
        } else if (mode == Mode.NONE) {
            canvas.drawText("Loading from phone...", cx, h * 0.80f, hintPaint)
        }

        if (config.showComplications) {
            complicationSlotsManager.complicationSlots.forEach { (_, slot) ->
                if (slot.enabled) {
                    try {
                        slot.render(canvas, zonedDateTime, renderParameters)
                    } catch (e: Exception) {
                        Log.w(TAG, "Complication render failed", e)
                    }
                }
            }
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    }

    // ---------------------------------------------------------------- GL utils

    private fun drawQuad(
        program: Int,
        texture: Int,
        target: Int,
        mediaW: Int,
        mediaH: Int,
        texMatrix: FloatArray,
        brightness: Float,
        texBuf: FloatBuffer
    ) {
        if (mediaW <= 0 || mediaH <= 0) return
        val scale = maxOf(viewW.toFloat() / mediaW, viewH.toFloat() / mediaH)
        val sx = mediaW * scale / viewW
        val sy = mediaH * scale / viewH

        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aTex = GLES20.glGetAttribLocation(program, "aTex")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        posBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, posBuf)
        texBuf.position(0)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)

        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uScale"), sx, sy)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexM"), 1, false, texMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBright"), brightness)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(target, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    private fun floatBuffer(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(values)
            .apply { position(0) }

    private fun genTexture(target: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(target, ids[0])
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Program link failed: ${GLES20.glGetProgramInfoLog(program)}")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    override fun onDestroy() {
        scope.cancel()
        releasePlayer()
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LumaLoopWatchFace"
        private const val VIDEO_FRAME_MS = 33L
        private const val IMAGE_FRAME_MS = 1000L
        private const val FETCH_RETRY_MS = 45_000L
        private const val PATH_FETCH = "/lumaloop/fetch"

        private const val VERTEX_SHADER = """
            attribute vec2 aPos;
            attribute vec2 aTex;
            uniform vec2 uScale;
            uniform mat4 uTexM;
            varying vec2 vTex;
            void main() {
                gl_Position = vec4(aPos * uScale, 0.0, 1.0);
                vTex = (uTexM * vec4(aTex, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_2D = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            uniform float uBright;
            void main() {
                vec4 c = texture2D(uTex, vTex);
                gl_FragColor = vec4(c.rgb * uBright, c.a);
            }
        """

        private const val FRAGMENT_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES uTex;
            uniform float uBright;
            void main() {
                vec4 c = texture2D(uTex, vTex);
                gl_FragColor = vec4(c.rgb * uBright, 1.0);
            }
        """
    }
}
