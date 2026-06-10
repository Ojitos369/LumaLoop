package com.ojitos369.lumaloop;

import android.app.WallpaperColors;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import com.ojitos369.lumaloop.preferences.SharedPreferencesManager;
import com.ojitos369.lumaloop.utilities.CurrentMediaHandler;
import com.ojitos369.lumaloop.utilities.MediaInfo;

/**
 * Clean SlideshowWallpaperService implementation.
 * This file has been replaced to remove corrupted/duplicated fragments.
 */
public class SlideshowWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new SlideshowWallpaperEngine();
    }

    private class SlideshowWallpaperEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String TAG = "SlideshowWallpaperEngine";
        private final Handler handler = new Handler(Looper.getMainLooper());
        private CurrentMediaHandler currentMediaHandler;
        private int width = 0;
        private int height = 0;
        private final SharedPreferencesManager manager;
        private final SharedPreferences sharedPrefs;
        private GestureDetector gestureDetector;
        private boolean surfaceReady = false;

        // Two-finger double-tap (toggles video sound)
        private static final long TWO_FINGER_TAP_TIMEOUT_MS = 250;
        private static final long TWO_FINGER_DOUBLE_TAP_WINDOW_MS = 400;
        private static final float TWO_FINGER_MOVE_SLOP_PX = 60f;
        private boolean twoFingerGesture = false;
        private boolean twoFingerMoved = false;
        private long twoFingerDownTime = 0;
        private long lastTwoFingerTapTime = 0;
        private float twoFingerStartX = 0f;
        private float twoFingerStartY = 0f;

        SlideshowWallpaperEngine() {
            // Use default SharedPreferences to match WallpaperPreferencesFragment
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            manager = new SharedPreferencesManager(sharedPrefs);
            initGestureDetector();
            // Register for preference changes
            sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        private void initGestureDetector() {
            gestureDetector = new GestureDetector(getApplicationContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                            if (manager.getSwipeToChange() && currentMediaHandler != null) {
                                if (e1.getX() - e2.getX() > 50 && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                                    currentMediaHandler.forceNextMedia(getApplicationContext());
                                    return true;
                                } else if (e2.getX() - e1.getX() > 50
                                        && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                                    currentMediaHandler.forcePreviousMedia(getApplicationContext());
                                    return true;
                                }
                            }
                            return super.onFling(e1, e2, velocityX, velocityY);
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            if (currentMediaHandler != null && currentMediaHandler.getCurrentMedia() != null
                                    && currentMediaHandler.getCurrentMedia().isVideo()) {
                                if (currentMediaHandler.isPaused())
                                    currentMediaHandler.resume(getApplicationContext());
                                else {
                                    try {
                                        currentMediaHandler.pause();
                                    } catch (Exception ex) {
                                        Log.e(TAG, "Error pausing video", ex);
                                    }
                                }
                                return true;
                            }
                            return super.onDoubleTap(e);
                        }
                    });
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);

            handleTwoFingerDoubleTap(event);

            if (gestureDetector != null) {
                if (event.getPointerCount() > 1) {
                    // Second finger landed: cancel the single-finger detector so a
                    // two-finger double tap never also fires onDoubleTap (play/pause)
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    gestureDetector.onTouchEvent(cancel);
                    cancel.recycle();
                } else {
                    gestureDetector.onTouchEvent(event);
                }
            }
        }

        private void handleTwoFingerDoubleTap(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    twoFingerGesture = false;
                    twoFingerMoved = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        twoFingerGesture = true;
                        twoFingerMoved = false;
                        twoFingerDownTime = event.getEventTime();
                        twoFingerStartX = (event.getX(0) + event.getX(1)) / 2f;
                        twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f;
                    } else {
                        // 3+ fingers: not our gesture
                        twoFingerGesture = false;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (twoFingerGesture && event.getPointerCount() >= 2) {
                        float cx = (event.getX(0) + event.getX(1)) / 2f;
                        float cy = (event.getY(0) + event.getY(1)) / 2f;
                        if (Math.hypot(cx - twoFingerStartX, cy - twoFingerStartY)
                                > TWO_FINGER_MOVE_SLOP_PX) {
                            twoFingerMoved = true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    if (twoFingerGesture && event.getPointerCount() == 2) {
                        long duration = event.getEventTime() - twoFingerDownTime;
                        if (!twoFingerMoved && duration < TWO_FINGER_TAP_TIMEOUT_MS) {
                            long now = event.getEventTime();
                            if (now - lastTwoFingerTapTime < TWO_FINGER_DOUBLE_TAP_WINDOW_MS) {
                                lastTwoFingerTapTime = 0;
                                toggleVideoSound();
                            } else {
                                lastTwoFingerTapTime = now;
                            }
                        }
                        twoFingerGesture = false;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    twoFingerGesture = false;
                    break;
            }
        }

        private void toggleVideoSound() {
            if (currentMediaHandler == null || currentMediaHandler.getCurrentMedia() == null
                    || !currentMediaHandler.getCurrentMedia().isVideo()) {
                return;
            }
            boolean newMuted = !manager.getMuteVideos();
            // Persist so the Settings switch stays in sync
            sharedPrefs.edit().putBoolean("mute_videos", newMuted).apply();
            currentMediaHandler.applyMute(newMuted);
            Log.d(TAG, "Two-finger double tap: mute=" + newMuted);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "onSurfaceCreated");
            surfaceReady = true;
            if (currentMediaHandler != null) {
                currentMediaHandler.updateSurface(holder);
            }
            if (isVisible() && currentMediaHandler != null) {
                currentMediaHandler.resume(getApplicationContext());
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "onSurfaceDestroyed");
            surfaceReady = false;
            if (currentMediaHandler != null)
                currentMediaHandler.stop();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;
            if (currentMediaHandler == null) {
                currentMediaHandler = new CurrentMediaHandler(manager, width, height, getApplicationContext(),
                        getSurfaceHolder());
                currentMediaHandler.addNextMediaListener(this::displayCurrentMedia);
                currentMediaHandler.updateAfter(getApplicationContext(), 0);
            } else {
                currentMediaHandler.updateSurface(holder);
                currentMediaHandler.setDimensions(width, height, getApplicationContext());
                displayCurrentMedia(currentMediaHandler.getCurrentMedia());
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d(TAG, "onVisibilityChanged: " + visible);
            if (currentMediaHandler != null) {
                if (visible && surfaceReady)
                    currentMediaHandler.resume(getApplicationContext());
                else
                    currentMediaHandler.pause();
            }
        }

        private void displayCurrentMedia(MediaInfo media) {
            // ExoPlayer handles all rendering, so this method is now just a placeholder
            // or can be removed if no other UI updates are needed.
            // Keeping it empty for now to satisfy the listener interface if needed.
        }

        @RequiresApi(api = Build.VERSION_CODES.O_MR1)
        @Override
        public WallpaperColors onComputeColors() {
            if (currentMediaHandler != null && currentMediaHandler.getCurrentMedia() != null) {
                Bitmap bitmap = currentMediaHandler.getCurrentMedia().getImage();
                if (bitmap != null) {
                    return WallpaperColors.fromBitmap(bitmap);
                }
            }
            return super.onComputeColors();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Log.d(TAG, "Preference changed: " + key);
            try {
                // Handle media list changes - force reload
                if ("pick_images".equals(key)) {
                    Log.d(TAG, "Media list changed, forcing reload");
                    if (currentMediaHandler != null && surfaceReady && isVisible()) {
                        handler.postDelayed(() -> {
                            try {
                                if (currentMediaHandler != null) {
                                    currentMediaHandler.forceNextMedia(getApplicationContext());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error forcing next media", e);
                            }
                        }, 300);
                    }
                }
                // Handle ordering or interval changes - advance to apply
                else if ("ordering".equals(key) || "seconds".equals(key) ||
                        "too_wide_images_rule".equals(key) || "active_tags".equals(key)) {
                    Log.d(TAG, "Settings or tags changed, forcing reload");
                    if (currentMediaHandler != null && surfaceReady && isVisible()) {
                        handler.postDelayed(() -> {
                            try {
                                if (currentMediaHandler != null) {
                                    currentMediaHandler.forceNextMedia(getApplicationContext());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error forcing next media", e);
                            }
                        }, 300);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling preference change", e);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            // Unregister listener
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
            if (currentMediaHandler != null)
                currentMediaHandler.stop();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                int xPixelOffset, int yPixelOffset) {
            // ExoPlayer handles all rendering - no scrolling offset needed
            // Images/videos are rendered by ExoPlayer which handles scaling automatically
        }
    }
}
