/*
 * Slideshow Wallpaper: An Android live wallpaper displaying custom images and videos.
 * Copyright (C) 2022  Doubi88 <tobis_mail@yahoo.de>
 *
 * Slideshow Wallpaper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Slideshow Wallpaper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.ojitos369.lumaloop.utilities;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.image.ImageRenderer;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.graphics.SurfaceTexture;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ojitos369.lumaloop.preferences.SharedPreferencesManager;

public class CurrentMediaHandler {
    private static final String TAG = "CurrentMediaHandler";

    private int currentIndex;
    private MediaInfo currentMedia;

    private SharedPreferencesManager manager;
    private int width;
    private int height;
    private Context context;
    private android.view.SurfaceHolder surfaceHolder;
    private final Object lock = new Object();
    private Handler mainHandler;

    private boolean runnable;
    private boolean isPaused = false;

    private ArrayList<NextMediaListener> nextMediaListeners;

    private boolean isVideoPlaying = false;
    private ExoPlayer exoPlayer;
    private GLWallpaperRenderer glRenderer;
    private SurfaceTexture videoSurfaceTexture;
    private Surface videoSurface;
    private ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private Runnable imageTimerRunnable;

    public interface NextMediaListener {
        void nextMedia(MediaInfo media);
    }

    public CurrentMediaHandler(SharedPreferencesManager manager, int width, int height, Context context,
            android.view.SurfaceHolder surfaceHolder) {
        this.manager = manager;
        this.width = width;
        this.height = height;
        this.context = context;
        this.surfaceHolder = surfaceHolder;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.runnable = true;
        this.isPaused = false;
        nextMediaListeners = new ArrayList<>(1);
    }

    private void initializeExoPlayer() {
        if (exoPlayer == null) {
            // Configure LoadControl for optimized video playback with large files
            // Lower buffer sizes for faster startup, reduces memory usage
            androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            2500, // Min buffer: 2.5s for fast startup
                            10000, // Max buffer: 10s to limit memory usage
                            1500, // Buffer for playback: 1.5s
                            2000 // Buffer for re-buffer: 2s
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            // Build ExoPlayer with optimized settings
            exoPlayer = new ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build();

            // Set video scaling mode for better performance
            exoPlayer.setVideoScalingMode(android.media.MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

            Log.d(TAG, "ExoPlayer created with optimized buffer settings for large videos");

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "Media playback completed (video or image)");
                        forceNextMedia(context);
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        Log.d(TAG, "Buffering video...");
                    } else if (playbackState == Player.STATE_READY) {
                        Log.d(TAG, "Video ready to play");
                    }
                }

                @Override
                public void onTracksChanged(Tracks tracks) {
                    Log.d(TAG, "Tracks changed: " + tracks);
                }

                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error: " + error.getMessage(), error);
                    // Try to recover or skip to next media
                    forceNextMedia(context);
                }
            });
        }
    }

    private long transitionDurationMs = 1000;
    private android.animation.ValueAnimator transitionAnimator;

    private void updateTransitionDuration() {
        transitionDurationMs = manager.getTransitionDuration();
    }

    /**
     * Prepare media - Videos and Images use ExoPlayer.
     */
    private void prepareMedia(Uri uri, boolean isVideo) {
        Log.d(TAG, "prepareMedia: " + uri + " (isVideo=" + isVideo + ")");

        initializeExoPlayer();

        // Initialize GL renderer if needed
        if (glRenderer == null && surfaceHolder != null) {
            glRenderer = new GLWallpaperRenderer(context);
            glRenderer.setSurface(surfaceHolder);
        }

        try {
            if (isVideo) {
                // For video: Create fresh SurfaceTexture every time to avoid stale frames
                isVideoPlaying = true;
                if (transitionAnimator != null) {
                    transitionAnimator.cancel();
                }

                if (videoSurfaceTexture != null) {
                    videoSurfaceTexture.release();
                    videoSurfaceTexture = null;
                }
                if (videoSurface != null) {
                    videoSurface.release();
                    videoSurface = null;
                }

                // Create new SurfaceTexture for this video
                videoSurfaceTexture = new SurfaceTexture(glRenderer.getVideoTextureId());
                videoSurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> {
                    try {
                        surfaceTexture.updateTexImage();
                        if (glRenderer != null) {
                            glRenderer.drawVideo();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error rendering video frame", e);
                    }
                });
                videoSurface = new Surface(videoSurfaceTexture);

                exoPlayer.setVideoSurface(videoSurface);

                MediaItem mediaItem = MediaItem.fromUri(uri);
                if (manager.getMuteVideos()) {
                    exoPlayer.setVolume(0f);
                } else {
                    exoPlayer.setVolume(1f);
                }
                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();
                if (!isPaused) {
                    exoPlayer.play();
                }
                Log.d(TAG, "Video playback started");
            } else {
                // For images: 
                boolean wasVideoPlaying = isVideoPlaying;
                isVideoPlaying = false;
                exoPlayer.stop();
                exoPlayer.clearMediaItems();

                imageExecutor.execute(() -> {
                    try {
                        InputStream inputStream = context.getContentResolver().openInputStream(uri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (inputStream != null)
                            inputStream.close();

                        if (bitmap != null) {
                            mainHandler.post(() -> {
                                if (!isVideoPlaying && glRenderer != null) {
                                    if (wasVideoPlaying) {
                                        // Transition from video to image: just upload and draw
                                        glRenderer.uploadImage(bitmap);
                                        glRenderer.drawImage();
                                        scheduleNextMedia();
                                    } else {
                                        // Transition from image to image: Cross-fade
                                        startImageTransition(bitmap);
                                    }
                                }
                            });
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading image", e);
                        forceNextMedia(context);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preparing media: " + e.getMessage(), e);
            forceNextMedia(context);
        }
    }

    private void startImageTransition(Bitmap nextBitmap) {
        if (glRenderer == null) return;

        updateTransitionDuration();

        if (transitionAnimator != null) {
            transitionAnimator.cancel();
        }

        glRenderer.uploadNextImage(nextBitmap);

        transitionAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        transitionAnimator.setDuration(transitionDurationMs);
        transitionAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (glRenderer != null && !isVideoPlaying) {
                glRenderer.drawImageTransition(progress);
            }
        });
        transitionAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (glRenderer != null && !isVideoPlaying) {
                    glRenderer.swapTextures();
                    glRenderer.drawImage(); // Draw final state
                    scheduleNextMedia();
                }
            }
        });
        transitionAnimator.start();
    }

    private void scheduleNextMedia() {
        // Simulate playback duration for image
        long durationMs = getImageDurationMs();

        // Cancel previous timer if any
        if (imageTimerRunnable != null) {
            mainHandler.removeCallbacks(imageTimerRunnable);
        }

        imageTimerRunnable = () -> {
            if (!isVideoPlaying && !isPaused) {
                forceNextMedia(context);
            }
        };

        mainHandler.postDelayed(imageTimerRunnable, durationMs);
    }

    /**
     * Prepare video using ExoPlayer.
     */

    /**
     * Release ExoPlayer completely.
     */

    /**
     * Get image display duration in milliseconds from preferences.
     */
    private long getImageDurationMs() {
        int seconds = 5; // default
        try {
            seconds = manager.getSecondsBetweenImages();
        } catch (Exception e) {
            Log.e(TAG, "Error getting image duration, using default", e);
        }
        return seconds * 1000L;
    }

    public void updateSurface(SurfaceHolder holder) {
        this.surfaceHolder = holder;
        // GLRenderer initialization is deferred to prepareMedia or can be done here if
        // context is ready
        // But we need to ensure GL context is created on the right thread if we were
        // using a GLThread.
        // Here we are running on main thread/service thread, so it should be fine for
        // simple usage.
        // However, if we want to re-init renderer:
        if (glRenderer != null) {
            glRenderer.release();
            glRenderer = null;
        }
    }

    public void addNextMediaListener(NextMediaListener l) {
        this.nextMediaListeners.add(l);
    }

    public void removeNextMediaListener(NextMediaListener l) {
        this.nextMediaListeners.remove(l);
    }

    private void notifyNextMediaListeners(MediaInfo media) {
        for (NextMediaListener l : nextMediaListeners) {
            l.nextMedia(media);
        }
    }

    public MediaInfo getCurrentMedia() {
        return currentMedia;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setDimensions(int width, int height, Context context) {
        this.width = width;
        this.height = height;
        updateAfter(context, 0);
    }

    public void startTimer(Context context) {
        runnable = true;
        isPaused = false;
        startPlayback();
    }

    public void pause() {
        isPaused = true;
        if (imageTimerRunnable != null) {
            mainHandler.removeCallbacks(imageTimerRunnable);
        }
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
        }
    }

    public void resume(Context context) {
        isPaused = false;
        startPlayback();
    }

    private void startPlayback() {
        if (exoPlayer != null && !exoPlayer.isPlaying()) {
            exoPlayer.play();
        }
    }

    public void updateAfter(Context context, long delay) {
        // Deprecated: Timer logic removed.
        // This method is kept if needed for interface compatibility but should trigger
        // next media.
        if (runnable && !isPaused) {
            forceNextMedia(context);
        }
    }

    public void stop() {
        Log.d(TAG, "stop() called");
        runnable = false;
        if (imageTimerRunnable != null) {
            mainHandler.removeCallbacks(imageTimerRunnable);
        }
        if (exoPlayer != null) {
            try {
                Log.d(TAG, "Stopping and releasing ExoPlayer");
                exoPlayer.stop();
                exoPlayer.clearVideoSurface();
                exoPlayer.release();
                exoPlayer = null;
                Log.d(TAG, "ExoPlayer released");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping ExoPlayer", e);
                exoPlayer = null; // Force null even on error
            }
        }
        if (glRenderer != null) {
            glRenderer.release();
            glRenderer = null;
        }
        if (videoSurface != null) {
            videoSurface.release();
            videoSurface = null;
        }
        if (videoSurfaceTexture != null) {
            videoSurfaceTexture.release();
            videoSurfaceTexture = null;
        }
        currentMedia = null;
        isVideoPlaying = false;
    }

    public boolean isStarted() {
        return runnable;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isVideoPlaying() {
        return isVideoPlaying;
    }

    /**
     * Clear the surface by drawing black screen.
     * This is CRITICAL before video playback to disconnect Canvas from the Surface.
     * MediaCodec requires exclusive Surface access and will fail if Canvas is still
     * connected.
     */

    public void forceNextMedia(Context context) {
        synchronized (lock) {
            if (runnable) {
                // Cancel pending timer to prevent accumulation
                if (imageTimerRunnable != null) {
                    mainHandler.removeCallbacks(imageTimerRunnable);
                }
                // Use a Handler to avoid Thread issues if called from background
                mainHandler.post(() -> {
                    try {
                        loadNewMedia(context, Direction.NEXT, true);
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading next media", e);
                    }
                });
            }
        }
    }

    public void forcePreviousMedia(Context context) {
        synchronized (lock) {
            if (runnable) {
                // Cancel pending timer to prevent accumulation
                if (imageTimerRunnable != null) {
                    mainHandler.removeCallbacks(imageTimerRunnable);
                }
                // Use a Handler to avoid Thread issues if called from background
                mainHandler.post(() -> {
                    try {
                        loadNewMedia(context, Direction.PREVIOUS, true);
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading previous media", e);
                    }
                });
            }
        }
    }

    private enum Direction {
        NEXT, PREVIOUS
    }

    private boolean loadNewMedia(Context context, Direction direction, boolean isForced) throws IOException {
        synchronized (lock) {
            Uri uri = getNextUri(context, direction, isForced);
            boolean result = false;
            if (uri != null) {
                MediaInfo.MediaType type = MediaInfo.determineType(context, uri);
                currentMedia = MediaLoader.loadMedia(uri, context, width, height, type);

                if (currentMedia != null) {
                    boolean isVideo = currentMedia.isVideo();
                    Log.d(TAG, "Loading " + (isVideo ? "video" : "image") + ": " + uri);

                    notifyNextMediaListeners(currentMedia);

                    // Use unified prepareMedia for both types
                    mainHandler.post(() -> prepareMedia(uri, isVideo));

                    result = true;
                }
            }
            return result;
        }
    }

    private Uri getNextUri(Context context, Direction direction, boolean isForced) {
        Uri result = null;
        Resources resources = context.getResources();
        SharedPreferencesManager.Ordering ordering = manager.getCurrentOrdering(resources);
        int countUris = manager.getImageUrisCount();

        if (countUris > 0) {
            int nextIndex;
            if (ordering == SharedPreferencesManager.Ordering.RANDOM) {
                // For Random: pick a truly random index
                if (countUris > 1) {
                    int currentIndexVal = manager.getCurrentIndex();
                    java.util.Random random = new java.util.Random();
                    do {
                        nextIndex = random.nextInt(countUris);
                    } while (nextIndex == currentIndexVal);
                } else {
                    nextIndex = 0;
                }
            } else {
                // For Selection: pick next/previous sequentially
                nextIndex = manager.getCurrentIndex();
                if (direction == Direction.PREVIOUS) {
                    nextIndex--;
                    if (nextIndex < 0) {
                        nextIndex = countUris - 1;
                    }
                } else {
                    nextIndex++;
                    if (nextIndex >= countUris) {
                        nextIndex = 0;
                    }
                }
            }

            manager.setCurrentIndex(nextIndex);
            manager.setLastUpdate(System.currentTimeMillis());

            result = manager.getImageUri(nextIndex);
            currentIndex = nextIndex;
        }

        return result;
    }

    private int getDelaySeconds(Context context) {
        int seconds = 5;
        try {
            seconds = manager.getSecondsBetweenImages();
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid number", e);
        }
        return seconds;
    }
}