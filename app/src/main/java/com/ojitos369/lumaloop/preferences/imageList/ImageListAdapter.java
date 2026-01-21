/*
 * Slideshow Wallpaper: An Android live wallpaper displaying custom images.
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
package com.ojitos369.lumaloop.preferences.imageList;

import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.ojitos369.lumaloop.R;
import com.ojitos369.lumaloop.listeners.OnCropListener;
import com.ojitos369.lumaloop.listeners.OnSelectListener;
import com.ojitos369.lumaloop.utilities.AsyncTaskLoadImages;
import com.ojitos369.lumaloop.utilities.ImageInfo;
import com.ojitos369.lumaloop.utilities.ProgressListener;
import com.ojitos369.lumaloop.preferences.imageList.ImageListActivity.MediaFilter;

public class ImageListAdapter extends RecyclerView.Adapter<ImageInfoViewHolder> {

    private List<Uri> uris;
    private List<Uri> filteredUris;
    private MediaFilter currentFilter = MediaFilter.ALL;
    private List<OnSelectListener> listeners;
    private OnCropListener cropListener;
    private HashMap<Uri, AsyncTaskLoadImages> loading;
    private HashSet<ImageInfo> selectedImages;

    public ImageListAdapter(List<Uri> uris) {
        this.selectedImages = new HashSet<>();
        this.uris = new ArrayList<>(uris);
        this.filteredUris = new ArrayList<>(uris);
        listeners = new LinkedList<>();
        loading = new HashMap<>();
    }

    @NonNull
    @Override
    public ImageInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.image_list_entry, parent, false);
        ImageInfoViewHolder holder = new ImageInfoViewHolder(view);
        holder.setOnSelectListener(new OnSelectListener() {
            @Override
            public void onImageSelected(ImageInfo info) {
                select(info);
            }

            @Override
            public void onImagedDeselected(ImageInfo info) {
                deselect(info);
            }

            @Override
            public void onSelectionChanged(HashSet<ImageInfo> setInfo) {
            }
        });
        holder.setOnCropListener(this.cropListener);
        return holder;
    }

    public void addOnSelectListener(OnSelectListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void setOnCropListener(OnCropListener listener) {
        this.cropListener = listener;
    }

    private void notifyListeners() {
        for (OnSelectListener listener : listeners) {
            listener.onSelectionChanged(this.selectedImages);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ImageInfoViewHolder holder, int position) {
        final Uri uri = filteredUris.get(position);
        AsyncTaskLoadImages asyncTask = loading.get(uri);
        if (asyncTask == null) {
            asyncTask = new AsyncTaskLoadImages(holder.itemView.getContext(), holder.itemView.getWidth(),
                    holder.itemView.getResources().getDimensionPixelSize(R.dimen.image_preview_height));
            loading.put(uri, asyncTask);

            asyncTask.addProgressListener(new ProgressListener<Uri, BigDecimal, List<ImageInfo>>() {
                @Override
                public void onProgressChanged(AsyncTask<Uri, BigDecimal, List<ImageInfo>> task, BigDecimal current,
                        BigDecimal max) {

                }

                @Override
                public void onTaskFinished(AsyncTask<Uri, BigDecimal, List<ImageInfo>> task,
                        List<ImageInfo> imageInfos) {
                    loading.remove(uri);
                }

                @Override
                public void onTaskCancelled(AsyncTask<Uri, BigDecimal, List<ImageInfo>> task,
                        List<ImageInfo> imageInfos) {
                    loading.remove(uri);
                }
            });
            asyncTask.execute(uri);
        }
        asyncTask.addProgressListener(holder);
        holder.setUri(uri);

        // Restore selection state
        // We create a dummy ImageInfo because equals() only checks URI
        ImageInfo tempInfo = new ImageInfo(uri, null, 0, null);
        if (selectedImages.contains(tempInfo)) {
            holder.SelectImage();
        } else {
            holder.DeselectImage();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ImageInfoViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        AsyncTaskLoadImages task = loading.get(holder.getUri());
        if (task != null) {
            task.cancel(false);
            loading.remove(holder.getUri());
        }
    }

    @Override
    public int getItemCount() {
        return filteredUris.size();
    }

    public void delete(HashSet<ImageInfo> imageInfos) {
        for (ImageInfo imageInfo : imageInfos) {
            int index = uris.indexOf(imageInfo.getUri());
            uris.remove(index);
            notifyItemRemoved(index);
        }
        selectedImages.clear();
        notifyListeners();
    }

    private void select(ImageInfo info) {
        selectedImages.add(info);
        for (OnSelectListener listener : this.listeners) {
            listener.onSelectionChanged(this.selectedImages);
        }
    }

    private void deselect(ImageInfo info) {
        selectedImages.remove(info);
        for (OnSelectListener listener : this.listeners) {
            listener.onSelectionChanged(this.selectedImages);
        }
    }

    public HashSet<ImageInfo> getSelectedImages() {
        return this.selectedImages;
    }

    public int getVisibleItemCount() {
        return filteredUris.size();
    }

    public void setFilter(MediaFilter filter) {
        this.currentFilter = filter;
        applyFilter();
    }

    private void applyFilter() {
        filteredUris.clear();
        for (Uri uri : uris) {
            boolean isVideo = isVideoUri(uri);
            if (currentFilter == MediaFilter.ALL ||
                    (currentFilter == MediaFilter.IMAGES_ONLY && !isVideo) ||
                    (currentFilter == MediaFilter.VIDEOS_ONLY && isVideo)) {
                filteredUris.add(uri);
            }
        }
        notifyDataSetChanged();
    }

    private boolean isVideoUri(Uri uri) {
        try {
            // Simple heuristic: check if path contains video indicators
            String uriString = uri.toString();
            return uriString.contains("/video/") || uriString.contains("Movies");
        } catch (Exception e) {
            return false;
        }
    }

    public void selectAll() {
        for (Uri uri : filteredUris) {
            ImageInfo info = new ImageInfo(uri, null, 0, null);
            if (!selectedImages.contains(info)) {
                selectedImages.add(info);
            }
        }
        notifyDataSetChanged();
        notifyListeners();
    }

    public void deselectAll() {
        selectedImages.clear();
        notifyDataSetChanged();
        notifyListeners();
    }

    public void addUris(List<Uri> uris) {
        int oldSize = this.filteredUris.size();
        this.uris.addAll(uris);
        applyFilter();

        if (currentFilter == MediaFilter.ALL) {
            notifyItemRangeInserted(oldSize, uris.size());
        } else {
            notifyDataSetChanged();
        }
    }

    public void replaceUri(Uri oldUri, Uri newUri) {
        int uriIndex = uris.indexOf(oldUri);
        int filteredIndex = filteredUris.indexOf(oldUri);

        if (uriIndex != -1) {
            // Check if the old URI was selected
            ImageInfo oldInfo = new ImageInfo(oldUri, null, 0, null);
            if (selectedImages.contains(oldInfo)) {
                selectedImages.remove(oldInfo);
                selectedImages.add(new ImageInfo(newUri, null, 0, null));
                notifyListeners();
            }

            uris.set(uriIndex, newUri);

            if (filteredIndex != -1) {
                filteredUris.set(filteredIndex, newUri);
                notifyItemChanged(filteredIndex);
            } else {
                // The old URI was in uris but not in filtered list
                // Just update uris, no notification needed
            }
        }
    }
}