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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public class MediaLoader {
    private static final String TAG = "MediaLoader";

    public static MediaInfo loadMedia(Uri uri, Context context, int targetWidth, int targetHeight,
            MediaInfo.MediaType type) throws IOException {
        String fileName = uri.getLastPathSegment();
        if (fileName == null) fileName = "Unknown";
        Bitmap bitmap = null;

        if (type == MediaInfo.MediaType.IMAGE) {
            bitmap = loadBitmap(uri, context, targetWidth, targetHeight);
        } else if (type == MediaInfo.MediaType.VIDEO) {
            // For videos, we'll get a thumbnail
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(context, uri);
                bitmap = retriever.getFrameAtTime();
                if (bitmap != null) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    retriever.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return new MediaInfo(uri, fileName, 0, bitmap, type);
    }

    private static Bitmap loadBitmap(Uri uri, Context context, int targetWidth, int targetHeight) throws IOException {
        Bitmap result = null;
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, options);
                is.close();

                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
                options.inJustDecodeBounds = false;

                is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                    if (bitmap != null) {
                        result = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                        if (result != bitmap) {
                            bitmap.recycle();
                        }
                    }
                }
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return result;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}