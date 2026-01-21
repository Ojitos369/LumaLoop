package com.ojitos369.lumaloop.utilities;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.ojitos369.lumaloop.R;
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager;

public class CurrentImageHandler {

    private int currentIndex;
    private ImageInfo currentImage;

    private SharedPreferencesManager manager;
    private int width;
    private int height;

    private boolean runnable;

    private Timer currentTimer;

    private ArrayList<NextImageListener> nextImageListeners;

    public CurrentImageHandler(SharedPreferencesManager manager, int width, int height) {
        this.manager = manager;
        this.width = width;
        this.height = height;
        this.runnable = true;
        nextImageListeners = new ArrayList<>(1);
    }

    public void addNextImageListener(NextImageListener l) {
        this.nextImageListeners.add(l);
    }
    public void removeNextImageListener(NextImageListener l) {
        this.nextImageListeners.remove(l);
    }

    private void notifyNextImageListeners(ImageInfo i) {
        for (NextImageListener l : nextImageListeners) {
            l.nextImage(i);
        }
    }

    public ImageInfo getCurrentImage() {
        return currentImage;
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
        long update = getDelaySeconds(context);
        updateAfter(context, update);
    }

    public void updateAfter(Context context, long delay) {
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
        if (runnable) {
            currentTimer = new Timer("CurrentImageHandlerTimer");
            currentTimer.schedule(new Runner(context), delay < 0 ? 0 : delay * 1000);
        }
    }

    public void stop() {
        runnable = false;
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
    }

    public boolean isStarted() {
        return currentTimer != null && runnable;
    }

    public void forceNextImage(Context context) {
        if (runnable) {
            if (currentTimer != null) {
                currentTimer.cancel();
            }
            currentTimer = new Timer("CurrentImageHandlerTimer");
            currentTimer.schedule(new Runner(context, Direction.NEXT, true), 0);
        }
    }

    public void forcePreviousImage(Context context) {
        if (runnable) {
            if (currentTimer != null) {
                currentTimer.cancel();
            }
            currentTimer = new Timer("CurrentImageHandlerTimer");
            currentTimer.schedule(new Runner(context, Direction.PREVIOUS, true), 0);
        }
    }

    private enum Direction {
        NEXT, PREVIOUS
    }

    public class Runner extends TimerTask {
        private Context context;
        private Direction direction;
        private boolean isForced;

        public Runner(Context context) {
            this(context, Direction.NEXT, false);
        }

        public Runner(Context context, Direction direction, boolean isForced) {
            this.context = context;
            this.direction = direction;
            this.isForced = isForced;
        }

        @Override
        public void run() {
            try {
                boolean updated = loadNewImage(context, direction, isForced);
                if (updated) {
                    notifyNextImageListeners(currentImage);
                }
            } catch (IOException e) {
                Log.e(CurrentImageHandler.class.getSimpleName(), "Error loading image", e);
            }

            if (runnable) {
                startTimer(context);
            }
        }
    }

    private boolean loadNewImage(Context context, Direction direction, boolean isForced) throws IOException {
        Uri uri = getNextUri(context, direction, isForced);
        boolean result = false;
        if (uri != null) {
            if (currentImage == null || currentImage.getImage() == null || !uri.equals(currentImage.getUri())) {
                currentImage = ImageLoader.loadImage(uri, context, width, height, false);
                result = true;
            }
        }
        return result;
    }

    private Uri getNextUri(Context context, Direction direction, boolean isForced) {
        Uri result = null;
        Resources resources = context.getResources();
        SharedPreferencesManager.Ordering ordering = manager.getCurrentOrdering(resources);
        int countUris = manager.getImageUrisCount();

        if (countUris > 0) {
            int currentImageIndex = manager.getCurrentIndex();
            if (currentImageIndex >= countUris) {
                // If an image was deleted and therefore we are over the end of the list
                currentImageIndex = 0;
            }

            if (direction == Direction.PREVIOUS) {
                currentImageIndex--;
                if (currentImageIndex < 0) {
                    currentImageIndex = countUris - 1;
                }
            } else { // Direction.NEXT
                currentImageIndex++;
                if (currentImageIndex >= countUris) {
                    currentImageIndex = 0;
                }
            }

            manager.setCurrentIndex(currentImageIndex);
            manager.setLastUpdate(System.currentTimeMillis());

            result = manager.getImageUri(currentImageIndex, ordering);
            currentIndex = currentImageIndex;
        }

        return result;
    }

    private long calculateNextUpdateInSeconds(Context context) {
        long lastUpdate = manager.getLastUpdate();
        long result = 0;
        if (lastUpdate > 0) {
            int delaySeconds = getDelaySeconds(context);
            long current = System.currentTimeMillis();
            result = delaySeconds - ((current - lastUpdate) / 1000); // Difference between delay and elapsed time since last update in seconds
        }
        return result;
    }

    private int getDelaySeconds(Context context) {
        int seconds = 5;
        try {
            seconds = manager.getSecondsBetweenImages();
        } catch (NumberFormatException e) {
            Log.e(CurrentImageHandler.class.getSimpleName(), "Invalid number", e);
        }
        return seconds;
    }

}
