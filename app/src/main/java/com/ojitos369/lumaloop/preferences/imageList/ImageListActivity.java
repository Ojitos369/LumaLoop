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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.chip.ChipGroup;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.view.ViewGroup;
import android.graphics.Color;

import com.ojitos369.lumaloop.R;
import com.ojitos369.lumaloop.listeners.OnCropListener;
import com.ojitos369.lumaloop.listeners.OnSelectListener;
import com.ojitos369.lumaloop.preferences.SharedPreferencesManager;
import com.ojitos369.lumaloop.utilities.ImageInfo;

public class ImageListActivity extends AppCompatActivity implements OnCropListener {

    private SharedPreferencesManager manager;
    private static final int REQUEST_CODE_FILE = 1;
    private static final int REQUEST_CODE_VIDEO_EDIT = 2;

    private ImageListAdapter imageListAdapter;
    private ChipGroup filterChipGroup;
    private TextView selectionCountText;
    private View emptyState;
    private TextView emptyStateText;

    public enum MediaFilter {
        ALL, IMAGES_ONLY, VIDEOS_ONLY
    }

    private MediaFilter currentFilter = MediaFilter.ALL;

    private FloatingActionButton removeButton;
    private Uri originalUri;

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private ActivityResultLauncher<PickVisualMediaRequest> launcher = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog progressDialog;

    public ImageListActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            this.launcher = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(),
                    this::imagePickerCallback);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            originalUri = savedInstanceState.getParcelable("originalUri");
            Log.d("CROP_DEBUG",
                    "onCreate: Restored originalUri: " + (originalUri != null ? originalUri.toString() : "null"));
        }
        setContentView(R.layout.image_list);

        this.removeButton = findViewById(R.id.delete_button);
        this.filterChipGroup = findViewById(R.id.filter_chip_group);
        this.selectionCountText = findViewById(R.id.selection_count_text);
        this.emptyState = findViewById(R.id.empty_state);
        this.emptyStateText = findViewById(R.id.empty_state_text);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        RecyclerView recyclerView = findViewById(R.id.image_list);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);
        manager = new SharedPreferencesManager(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE));

        List<Uri> uris = manager.getImageUris(SharedPreferencesManager.Ordering.SELECTION);
        uris = syncImageUris(uris);

        imageListAdapter = new ImageListAdapter(uris);
        imageListAdapter.setOnCropListener(this);
        imageListAdapter.addOnSelectListener(new OnSelectListener() {
            @Override
            public void onImageSelected(ImageInfo info) {

            }

            @Override
            public void onImagedDeselected(ImageInfo info) {

            }

            @Override
            public void onSelectionChanged(HashSet<ImageInfo> setInfo) {
                Log.d(ImageListActivity.class.getSimpleName(), setInfo.size() + " image(s) selected");
                updateSelectionUI(setInfo.size());
            }
        });
        recyclerView.setAdapter(imageListAdapter);

        // Setup filter chips
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            int checkedId = checkedIds.get(0);
            MediaFilter filter = MediaFilter.ALL;
            if (checkedId == R.id.chip_images) {
                filter = MediaFilter.IMAGES_ONLY;
            } else if (checkedId == R.id.chip_videos) {
                filter = MediaFilter.VIDEOS_ONLY;
            }
            currentFilter = filter; // Update currentFilter
            imageListAdapter.setFilter(filter);
            updateEmptyState();
        });

        updateEmptyState();

        // Handle incoming share intent
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            handleSharedImages(intent);
        }

        findViewById(R.id.add_button).setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && launcher != null) {
                launcher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build());
            } else {
                Intent intentFile = new Intent();
                intentFile.setType("*/*");
                intentFile.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "image/*", "video/*" });
                intentFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    intentFile.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                }
                intentFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    intentFile.setAction(Intent.ACTION_OPEN_DOCUMENT);
                } else {
                    intentFile.setAction(Intent.ACTION_GET_CONTENT);
                }
                startActivityForResult(intentFile, REQUEST_CODE_FILE);
            }
        });

        this.removeButton.setOnClickListener(view -> {
            HashSet<ImageInfo> selectedImages = imageListAdapter.getSelectedImages();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.remove_confirmation_title));
            builder.setMessage(getResources().getQuantityString(R.plurals.remove_confirmation_message,
                    selectedImages.size(), selectedImages.size()));
            builder.setPositiveButton(getString(R.string.positive_action_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (ImageInfo imageInfo : selectedImages) {
                        Uri uri = imageInfo.getUri();
                        manager.removeUri(uri);

                        if ("content".equals(uri.getScheme())) {
                            try {
                                // Check if it's a video or image to use the correct deletion method
                                String mimeType = getContentResolver().getType(uri);
                                if (mimeType != null && mimeType.startsWith("video/")) {
                                    getContentResolver().delete(uri, null, null);
                                } else {
                                    getContentResolver().delete(uri, null, null);
                                }
                            } catch (SecurityException e) {
                                Log.e(ImageListActivity.class.getSimpleName(), "Failed to delete media from MediaStore",
                                        e);
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && imageInfo.getSize() > 0
                                && !manager.hasImageUri(uri)) {
                            // Try to release permission, but don't crash if it fails
                            try {
                                getContentResolver().releasePersistableUriPermission(uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                Log.e(ImageListActivity.class.getSimpleName(),
                                        "Failed to release permission for URI: " + uri, e);
                            }
                        }
                    }
                    imageListAdapter.delete(selectedImages);
                }
            });

            builder.setNegativeButton(getString(R.string.cancel_action_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            // Create and display the AlertDialog
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (originalUri != null) {
            outState.putParcelable("originalUri", originalUri);
            Log.d("CROP_DEBUG", "onSaveInstanceState: Saving originalUri: " + originalUri.toString());
        }
    }

    private void handleSharedImages(Intent intent) {
        ArrayList<Uri> imageUris = new ArrayList<>();
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action) && intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
            imageUris.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)
                && intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) != null) {
            imageUris.addAll(intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM));
        }

        List<Uri> urisToAdd = new ArrayList<>();
        for (Uri uri : imageUris) {
            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
                Uri newUri = copySharedImageToMediaStore(uri);
                if (newUri != null && manager.addUri(newUri)) {
                    urisToAdd.add(newUri);
                }
            }
        }

        if (!urisToAdd.isEmpty()) {
            imageListAdapter.addUris(urisToAdd);
        }
    }

    private Uri copySharedImageToMediaStore(Uri inputUri) {
        ContentResolver resolver = getContentResolver();
        String mimeType = resolver.getType(inputUri);
        String fileExtension = mimeType != null && mimeType.startsWith("video/") ? ".mp4" : ".jpg";
        String fileName = "sharedMedia" + System.currentTimeMillis() + fileExtension;

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mimeType != null && mimeType.startsWith("video/")) {
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/SlideshowWallpaper");
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SlideshowWallpaper");
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);
        } else {
            if (mimeType != null && mimeType.startsWith("video/")) {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else {
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
        }

        Uri mediaUri = resolver.insert(collection, contentValues);

        if (mediaUri != null) {
            try (OutputStream os = resolver.openOutputStream(mediaUri);
                    InputStream is = resolver.openInputStream(inputUri)) {
                if (os != null && is != null) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
            } catch (IOException | SecurityException e) {
                Log.e(ImageListActivity.class.getSimpleName(), "Failed to copy shared media to MediaStore", e);
                resolver.delete(mediaUri, null, null);
                return null;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(mediaUri, contentValues, null, null);
            }
        }

        return mediaUri;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.image_list_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem selectAllItem = menu.findItem(R.id.action_select_all);
        MenuItem deselectAllItem = menu.findItem(R.id.action_deselect_all);

        int selectedCount = imageListAdapter.getSelectedImages().size();
        int visibleCount = imageListAdapter.getVisibleItemCount();

        if (selectedCount == 0) {
            selectAllItem.setVisible(visibleCount > 0);
            deselectAllItem.setVisible(false);
        } else if (selectedCount == visibleCount && visibleCount > 0) {
            selectAllItem.setVisible(false);
            deselectAllItem.setVisible(true);
        } else {
            selectAllItem.setVisible(true);
            deselectAllItem.setVisible(true);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_select_all) {
            imageListAdapter.selectAll();
            invalidateOptionsMenu();
            return true;
        } else if (item.getItemId() == R.id.action_deselect_all) {
            imageListAdapter.deselectAll();
            invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateSelectionUI(int selectedCount) {
        if (selectedCount > 0) {
            removeButton.setVisibility(View.VISIBLE);
            selectionCountText.setVisibility(View.VISIBLE);
            selectionCountText.setText(getResources().getQuantityString(
                    R.plurals.items_selected, selectedCount, selectedCount));
        } else {
            removeButton.setVisibility(View.GONE);
            selectionCountText.setVisibility(View.GONE);
        }
        invalidateOptionsMenu();
    }

    private void updateEmptyState() {
        int visibleCount = imageListAdapter.getVisibleItemCount();
        if (visibleCount == 0) {
            emptyState.setVisibility(View.VISIBLE);
            // Update message based on filter
            int checkedId = filterChipGroup.getCheckedChipId();
            if (checkedId == R.id.chip_images) {
                emptyStateText.setText(getString(R.string.no_media_filtered,
                        getString(R.string.media_type_image)));
            } else if (checkedId == R.id.chip_videos) {
                emptyStateText.setText(getString(R.string.no_media_filtered,
                        getString(R.string.media_type_video)));
            } else {
                emptyStateText.setText(R.string.no_media_message);
            }
        } else {
            emptyState.setVisibility(View.GONE);
        }
    }

    private void imagePickerCallback(List<Uri> uris) {
        if (uris != null && !uris.isEmpty()) {
            processSelectedUris(uris);
        }
    }

    private void processSelectedUris(List<Uri> uris) {
        showProgressDialog();
        executor.execute(() -> {
            List<Uri> urisToAdd = new ArrayList<>();
            for (Uri uri : uris) {
                Uri newUri = copySharedImageToMediaStore(uri);
                if (newUri != null && manager.addUri(newUri)) {
                    urisToAdd.add(newUri);
                }
            }
            mainHandler.post(() -> {
                hideProgressDialog();
                if (!urisToAdd.isEmpty()) {
                    imageListAdapter.addUris(urisToAdd);
                }
            });
        });
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(40, 40, 40, 40);
            layout.setGravity(Gravity.CENTER_VERTICAL);

            ProgressBar progressBar = new ProgressBar(this);
            progressBar.setIndeterminate(true);

            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText(R.string.processing_images);
            tv.setPadding(40, 0, 0, 0);
            tv.setTextColor(Color.BLACK);
            tv.setTextSize(16);

            layout.addView(progressBar);
            layout.addView(tv);

            builder.setView(layout);
            progressDialog = builder.create();
        }
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UCrop.REQUEST_CROP) {
            Log.d("CROP_DEBUG", "onActivityResult: Crop request finished with result code: " + resultCode);
            if (resultCode == RESULT_OK) {
                final Uri resultUri = UCrop.getOutput(data);
                Log.d("CROP_DEBUG",
                        "onActivityResult: Crop successful. Original URI: "
                                + (originalUri != null ? originalUri.toString() : "null") + ", Result URI: "
                                + (resultUri != null ? resultUri.toString() : "null"));
                if (originalUri != null && resultUri != null) {
                    Uri newUri = saveImageToMediaStore(resultUri);
                    if (newUri != null) {
                        // Replace the original URI with the new cropped URI
                        manager.removeUri(originalUri);
                        manager.addUri(newUri);

                        // Delete the original image
                        try {
                            if ("content".equals(originalUri.getScheme())) {
                                getContentResolver().delete(originalUri, null, null);
                            } else if ("file".equals(originalUri.getScheme())) {
                                new File(originalUri.getPath()).delete();
                            }
                        } catch (Exception e) {
                            Log.e("DELETE_DEBUG", "Failed to delete original image: " + originalUri, e);
                        }

                        // Update the adapter
                        imageListAdapter.replaceUri(originalUri, newUri);

                        // Release permission for the original URI if it's no longer needed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !manager.hasImageUri(originalUri)) {
                            try {
                                getContentResolver().releasePersistableUriPermission(originalUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                Log.d("CROP_DEBUG", "onActivityResult: Successfully released permission for "
                                        + originalUri.toString());
                            } catch (SecurityException e) {
                                Log.e("CROP_DEBUG",
                                        "onActivityResult: Failed to release permission for " + originalUri.toString(),
                                        e);
                            }
                        }
                    }
                }
                originalUri = null;
            } else if (resultCode == UCrop.RESULT_ERROR) {
                final Throwable cropError = UCrop.getError(data);
                Log.e("CROP_DEBUG", "onActivityResult: Crop error", cropError);
            } else {
                Log.d("CROP_DEBUG", "onActivityResult: Crop was cancelled.");
            }
        }

        List<Uri> uris = new LinkedList<>();
        if (requestCode == REQUEST_CODE_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                ArrayList<Uri> resultUris = new ArrayList<>();
                if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        resultUris.add(clipData.getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    resultUris.add(data.getData());
                }
                processSelectedUris(resultUris);
            }
        }
    }

    private Uri saveVideoToMediaStore(Uri inputUri) {
        ContentResolver resolver = getContentResolver();
        String fileName = "editedVideo" + System.currentTimeMillis() + ".mp4";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SlideshowWallpaper");
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri videoUri = resolver.insert(collection, contentValues);

        if (videoUri != null) {
            try (OutputStream os = resolver.openOutputStream(videoUri);
                    InputStream is = resolver.openInputStream(inputUri)) {
                if (os != null && is != null) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
            } catch (IOException e) {
                Log.e(ImageListActivity.class.getSimpleName(), "Failed to save video to MediaStore", e);
                resolver.delete(videoUri, null, null);
                return null;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
                resolver.update(videoUri, contentValues, null, null);
            }
        }

        return videoUri;
    }

    private Uri saveImageToMediaStore(Uri inputUri) {
        ContentResolver resolver = getContentResolver();
        String fileName = "croppedImage" + System.currentTimeMillis() + ".jpg";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SlideshowWallpaper");
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri imageUri = resolver.insert(collection, contentValues);

        if (imageUri != null) {
            try (OutputStream os = resolver.openOutputStream(imageUri);
                    InputStream is = resolver.openInputStream(inputUri)) {
                if (os != null && is != null) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
            } catch (IOException e) {
                Log.e(ImageListActivity.class.getSimpleName(), "Failed to save image to MediaStore", e);
                // If saving fails, delete the incomplete entry
                resolver.delete(imageUri, null, null);
                return null;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(imageUri, contentValues, null, null);
            }
        }

        return imageUri;
    }

    @Override
    public void onCrop(Uri uri) {
        Log.d("CROP_DEBUG", "onCrop: Cropping image with uri: " + uri.toString());
        this.originalUri = uri;

        UCrop.Options options = new UCrop.Options();

        int height, width;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
            android.graphics.Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars());
            width = windowMetrics.getBounds().width() - insets.left - insets.right;
            height = windowMetrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            height = displayMetrics.heightPixels;
            width = displayMetrics.widthPixels;
        }

        options.setAspectRatioOptions(0, new com.yalantis.ucrop.model.AspectRatio("auto", width, height),
                new com.yalantis.ucrop.model.AspectRatio("9:16", 9, 16),
                new com.yalantis.ucrop.model.AspectRatio("16:9", 16, 9),
                new com.yalantis.ucrop.model.AspectRatio("4:3", 4, 3),
                new com.yalantis.ucrop.model.AspectRatio("3:4", 3, 4),
                new com.yalantis.ucrop.model.AspectRatio("1:1", 1, 1));

        options.setToolbarColor(ContextCompat.getColor(this, R.color.primaryColor));
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.primaryDarkColor));
        options.setActiveControlsWidgetColor(ContextCompat.getColor(this, R.color.primaryColor));

        String destinationFileName = "croppedImage" + System.currentTimeMillis() + ".jpg";
        File destinationFile = new File(getCacheDir(), destinationFileName);
        Uri destinationUri = Uri.fromFile(destinationFile);

        UCrop uCrop = UCrop.of(uri, destinationUri)
                .withMaxResultSize(1920, 1920)
                .withOptions(options);

        Intent intent = uCrop.getIntent(this);
        startActivityForResult(intent, UCrop.REQUEST_CROP);
    }

    private List<Uri> syncImageUris(List<Uri> uris) {
        List<Uri> validUris = new ArrayList<>();
        for (Uri uri : uris) {
            boolean exists = false;
            if ("content".equals(uri.getScheme())) {
                try {
                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        exists = is != null;
                    }
                } catch (Exception e) {
                    exists = false;
                }
            } else if ("file".equals(uri.getScheme())) {
                File file = new File(uri.getPath());
                exists = file.exists();
            } else {
                exists = true;
            }

            if (exists) {
                validUris.add(uri);
            } else {
                manager.removeUri(uri);
            }
        }
        return validUris;
    }
}