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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

public class MediaInfo {
    private static final String TAG = "MediaInfo";

    public enum MediaType {
        IMAGE,
        VIDEO
    }

    private Uri uri;
    private String name;
    private int size;
    private Bitmap image;
    private MediaType type;

    public MediaInfo(Uri uri, String name, int size, Bitmap image, MediaType type) {
        this.uri = uri;
        this.name = name;
        this.size = size;
        this.image = image;
        this.type = type;
    }

    public Uri getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public Bitmap getImage() {
        return image;
    }

    public void setImage(Bitmap image) {
        this.image = image;
    }

    public MediaType getType() {
        return type;
    }

    public void release() {
        // Nothing to release here anymore for the player
    }

    public boolean isVideo() {
        return type == MediaType.VIDEO;
    }

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = false;
        if ((obj != null) && (obj instanceof MediaInfo)) {
            result = ((obj == this) || (((MediaInfo) obj).getUri().equals(getUri())));
        }
        return result;
    }

    @Override
    public int hashCode() {
        return getUri().hashCode();
    }

    public static MediaType determineType(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        String mimeType = cr.getType(uri);
        if (mimeType != null) {
            if (mimeType.startsWith("video/")) {
                return MediaType.VIDEO;
            } else if (mimeType.startsWith("image/")) {
                return MediaType.IMAGE;
            }
        }
        // Default to image if can't determine
        return MediaType.IMAGE;
    }
}