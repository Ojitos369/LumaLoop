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
package com.ojitos369.lumaloop.preferences;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ojitos369.lumaloop.R;

public class SharedPreferencesManager {

    private static final String PREFERENCE_KEY_ORDERING = "ordering";
    private static final String PREFERENCE_KEY_LAST_UPDATE = "last_update";
    private static final String PREFERENCE_KEY_LAST_INDEX = "last_index";
    private static final String PREFERENCE_KEY_URI_LIST = "pick_images";
    private static final String PREFERENCE_KEY_SECONDS_BETWEEN = "seconds";
    private static final String PREFERENCE_KEY_TOO_WIDE_IMAGES_RULE = "too_wide_images_rule";
    private static final String PREFERENCE_KEY_ANTI_ALIAS = "anti_alias";
    private static final String PREFERENCE_KEY_ANTI_ALIAS_WHILE_SCROLLING = "anti_alias_scrolling";
    private static final String PREFERENCE_KEY_SWIPE = "swipe";
    private static final String PREFERENCE_KEY_MUTE_VIDEOS = "mute_videos";
    private static final String PREFERENCE_KEY_TRANSITION_DURATION = "transition_duration";
    private static final String PREFERENCE_KEY_TAG_MAP = "tag_map";
    private static final String PREFERENCE_KEY_ACTIVE_TAGS = "active_tags";
    private static final String PREFERENCE_KEY_TAG_FILTER_MODE = "tag_filter_mode";
    private static final String PREFERENCE_KEY_HIDDEN_TAGS = "hidden_tags";
    private static final String PREFERENCE_KEY_AUTO_TAG_ENABLED = "auto_tag_enabled";
    private static final String PREFERENCE_KEY_TAG_CATALOG = "tag_catalog";

    public enum TagFilterMode {
        AND("and"),
        OR("or"),
        XAND("xand"),
        XOR("xor");

        private final String value;
        TagFilterMode(String value) { this.value = value; }
        public String getValue() { return value; }
        public static TagFilterMode fromValue(String value) {
            for (TagFilterMode mode : values()) {
                if (mode.value.equals(value)) return mode;
            }
            return OR; // Default
        }
    }

    public enum Ordering {
        SELECTION(0),
        RANDOM(1);

        private int valueListIndex;

        private Ordering(int valueListIndex) {
            this.valueListIndex = valueListIndex;
        }

        public static Ordering forValue(String value, Resources r) {
            Ordering[] values = values();
            Ordering result = null;
            for (int i = 0; i < values.length && result == null; i++) {
                if (values[i].getValue(r).equals(value)) {
                    result = values[i];
                }
            }
            return result;
        }

        public String getDescription(Resources r) {
            return r.getStringArray(R.array.orderings)[valueListIndex];
        }

        public String getValue(Resources r) {
            return r.getStringArray(R.array.ordering_values)[valueListIndex];
        }
    }

    public enum TooWideImagesRule {
        SCROLL_FORWARD(0),
        SCROLL_BACKWARD(1),
        SCALE_DOWN(2),
        SCALE_UP(3);

        private int valueListIndex;

        private TooWideImagesRule(int valueListIndex) {
            this.valueListIndex = valueListIndex;
        }

        public String getDescription(Resources r) {
            return r.getStringArray(R.array.too_wide_images_rules)[valueListIndex];
        }

        public String getValue(Resources r) {
            return r.getStringArray(R.array.too_wide_images_rule_values)[valueListIndex];
        }

        public static TooWideImagesRule forValue(String value, Resources r) {
            TooWideImagesRule[] values = values();
            TooWideImagesRule result = null;
            for (int i = 0; i < values.length && result == null; i++) {
                if (values[i].getValue(r).equals(value)) {
                    result = values[i];
                }
            }
            return result;
        }
    }

    private SharedPreferences preferences;

    public SharedPreferencesManager(@NonNull SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public static SharedPreferencesManager fromContext(android.content.Context context) {
        return new SharedPreferencesManager(
            context.getSharedPreferences(
                context.getPackageName() + "_preferences",
                android.content.Context.MODE_PRIVATE
            )
        );
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    public Ordering getCurrentOrdering(Resources r) {
        String value = preferences.getString(PREFERENCE_KEY_ORDERING, "selection");
        return Ordering.forValue(value, r);
    }

    public List<Uri> getImageUris() {
        return getImageUrisBase();
    }

    /**
     * @deprecated Use getImageUris() or getImageUris(Resources)
     */
    @Deprecated
    public List<Uri> getImageUris(@NonNull Ordering ordering) {
        return getImageUrisBase();
    }

    public List<Uri> getImageUris(Resources r) {
        List<Uri> result = getFilteredImageUris();
        if (getCurrentOrdering(r) == Ordering.RANDOM) {
            Collections.shuffle(result);
        }
        return result;
    }

    public int getImageUrisCount() {
        return getFilteredImageUris().size();
    }

    public Uri getImageUri(@NonNull int index) {
        List<Uri> uris = getFilteredImageUris();
        if (index < 0 || index >= uris.size()) return null;
        return uris.get(index);
    }

    /**
     * @deprecated Use getImageUri(int)
     */
    @Deprecated
    public Uri getImageUri(@NonNull int index, @NonNull Ordering ordering) {
        return getImageUri(index);
    }

    public boolean hasImageUri(@NonNull Uri uri) {
        return getImageUrisBase().contains(uri);
    }

    private String[] getUriList() {
        String list = preferences.getString(PREFERENCE_KEY_URI_LIST, null);
        if (list == null || list.equals("")) {
            return new String[0];
        } else {
            return list.split(";");
        }
    }

    public List<Uri> getImageUrisBase() {
        String[] uris = getUriList();
        ArrayList<Uri> result = new ArrayList<>(uris.length);
        for (String uri : uris) {
            result.add(Uri.parse(uri));
        }
        return result;
    }

    public boolean addUri(Uri uri) {
        List<Uri> list = getImageUrisBase();
        boolean result = false;
        if (!hasUriWithSameId(list, uri)) {
            result = list.add(uri);
            if (result) {
                saveUriList(list);
            }
        }
        return result;
    }

    public boolean hasUriWithSameId(List<Uri> list, Uri uri) {
        if (uri == null) return false;
        String id = uri.getLastPathSegment();
        if (id == null) return list.contains(uri);
        
        for (Uri existing : list) {
            if (id.equals(existing.getLastPathSegment())) {
                return true;
            }
        }
        return false;
    }

    public boolean addUris(List<Uri> newUris) {
        if (newUris == null || newUris.isEmpty())
            return false;

        List<Uri> list = getImageUrisBase();
        boolean changed = false;

        for (Uri uri : newUris) {
            if (!hasUriWithSameId(list, uri)) {
                list.add(uri);
                changed = true;
            }
        }

        if (changed) {
            saveUriList(list);
        }
        return changed;
    }

    public void replaceUri(Uri oldUri, Uri newUri) {
        List<Uri> list = getImageUrisBase();
        int index = list.indexOf(oldUri);
        if (index != -1) {
            list.set(index, newUri);
            saveUriList(list);
        } else {
            addUri(newUri);
        }
    }

    private synchronized void saveUriList(List<Uri> list) {
        StringBuilder build = new StringBuilder();
        for (Uri entry : list) {
            if (build.length() > 0) {
                build.append(";");
            }
            build.append(entry.toString());
        }
        preferences.edit().putString(PREFERENCE_KEY_URI_LIST, build.toString()).apply();
    }

    public void removeUri(Uri uri) {
        List<Uri> uris = getImageUrisBase();
        if (uris.remove(uri)) {
            saveUriList(uris);
        }
    }

    public void removeUris(List<Uri> toRemove) {
        if (toRemove == null || toRemove.isEmpty())
            return;

        List<Uri> uris = getImageUrisBase();
        boolean changed = uris.removeAll(toRemove);

        if (changed) {
            saveUriList(uris);
        }
    }

    public int getCurrentIndex() {
        int result = preferences.getInt(PREFERENCE_KEY_LAST_INDEX, 0);
        String[] uris = getUriList();
        while (result >= uris.length) {
            result -= uris.length;
        }
        return result;
    }

    public void setCurrentIndex(int index) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCE_KEY_LAST_INDEX, index);
        editor.apply();
    }

    public long getLastUpdate() {
        return preferences.getLong(PREFERENCE_KEY_LAST_UPDATE, 0);
    }

    public void setLastUpdate(long value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(PREFERENCE_KEY_LAST_UPDATE, value);
        editor.apply();
    }

    public int getSecondsBetweenImages() throws NumberFormatException {
        String secondsString = preferences.getString(PREFERENCE_KEY_SECONDS_BETWEEN, "15");
        int result = Integer.parseInt(secondsString);

        return result;
    }

    public void setSecondsBetweenImages(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCE_KEY_SECONDS_BETWEEN, String.valueOf(value));
        editor.apply();
    }

    public TooWideImagesRule getTooWideImagesRule(Resources r) {
        String value = preferences.getString(PREFERENCE_KEY_TOO_WIDE_IMAGES_RULE,
                TooWideImagesRule.SCALE_DOWN.getValue(r));
        return TooWideImagesRule.forValue(value, r);
    }

    public boolean getAntiAlias() {
        return preferences.getBoolean(PREFERENCE_KEY_ANTI_ALIAS, true);
    }

    public void setAntiAlias(boolean value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFERENCE_KEY_ANTI_ALIAS, value);
        editor.apply();
    }

    public boolean getAntiAliasWhileScrolling() {
        return preferences.getBoolean(PREFERENCE_KEY_ANTI_ALIAS_WHILE_SCROLLING, true);
    }

    public void setAntiAliasWhileScrolling(boolean value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFERENCE_KEY_ANTI_ALIAS_WHILE_SCROLLING, value);
        editor.apply();
    }

    public boolean getSwipeToChange() {
        return preferences.getBoolean(PREFERENCE_KEY_SWIPE, true);
    }

    public int getTransitionDuration() {
        String durationString = preferences.getString(PREFERENCE_KEY_TRANSITION_DURATION, "1000");
        try {
            return Integer.parseInt(durationString);
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    public void setTransitionDuration(int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREFERENCE_KEY_TRANSITION_DURATION, String.valueOf(value));
        editor.apply();
    }

    public List<String> getTags(@NonNull Uri uri) {
        String key = "tags_" + uri.toString();
        java.util.Set<String> tags = preferences.getStringSet(key, null);
        return tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void addTag(@NonNull Uri uri, String tag) {
        String key = "tags_" + uri.toString();
        java.util.Set<String> tags = preferences.getStringSet(key, new java.util.HashSet<>());
        java.util.Set<String> newTags = new java.util.HashSet<>(tags);
        newTags.add(tag);
        preferences.edit().putStringSet(key, newTags).apply();
        
        // Ensure tag is in catalog
        addTagToCatalog(tag);
    }

    public void removeTag(@NonNull Uri uri, String tag) {
        String key = "tags_" + uri.toString();
        java.util.Set<String> tags = preferences.getStringSet(key, null);
        if (tags != null) {
            java.util.Set<String> newTags = new java.util.HashSet<>(tags);
            if (newTags.remove(tag)) {
                if (newTags.isEmpty()) {
                    preferences.edit().remove(key).apply();
                } else {
                    preferences.edit().putStringSet(key, newTags).apply();
                }
            }
        }
    }

    public java.util.Set<String> getAllTags() {
        return getMasterTagList();
    }

    public java.util.Set<String> getMasterTagList() {
        java.util.Set<String> catalog = preferences.getStringSet(PREFERENCE_KEY_TAG_CATALOG, new java.util.HashSet<>());
        java.util.Set<String> result = new java.util.HashSet<>(catalog);
        result.add("Images");
        result.add("Videos");
        return result;
    }

    public void addTagToCatalog(String tag) {
        if ("Images".equals(tag) || "Videos".equals(tag)) return;
        java.util.Set<String> catalog = preferences.getStringSet(PREFERENCE_KEY_TAG_CATALOG, new java.util.HashSet<>());
        if (!catalog.contains(tag)) {
            java.util.Set<String> newCatalog = new java.util.HashSet<>(catalog);
            newCatalog.add(tag);
            preferences.edit().putStringSet(PREFERENCE_KEY_TAG_CATALOG, newCatalog).apply();
        }
    }

    public void removeTagFromCatalog(String tag) {
        // Remove from master list
        java.util.Set<String> catalog = preferences.getStringSet(PREFERENCE_KEY_TAG_CATALOG, new java.util.HashSet<>());
        if (catalog.contains(tag)) {
            java.util.Set<String> newCatalog = new java.util.HashSet<>(catalog);
            newCatalog.remove(tag);
            preferences.edit().putStringSet(PREFERENCE_KEY_TAG_CATALOG, newCatalog).apply();
        }

        // Remove from all elements
        List<Uri> allUris = getImageUrisBase();
        for (Uri uri : allUris) {
            removeTag(uri, tag);
        }
        
        // Remove from active filters
        java.util.Set<String> active = getActiveTags();
        if (active.contains(tag)) {
            java.util.Set<String> newActive = new java.util.HashSet<>(active);
            newActive.remove(tag);
            setActiveTags(newActive);
        }

        // Remove from hidden filters
        java.util.Set<String> hidden = getHiddenTags();
        if (hidden.contains(tag)) {
            java.util.Set<String> newHidden = new java.util.HashSet<>(hidden);
            newHidden.remove(tag);
            setHiddenTags(newHidden);
        }
    }

    public void renameTag(String oldName, String newName) {
        if (oldName.equals(newName)) return;
        
        // Update catalog
        java.util.Set<String> catalog = preferences.getStringSet(PREFERENCE_KEY_TAG_CATALOG, new java.util.HashSet<>());
        java.util.Set<String> newCatalog = new java.util.HashSet<>(catalog);
        if (newCatalog.remove(oldName)) {
            newCatalog.add(newName);
            preferences.edit().putStringSet(PREFERENCE_KEY_TAG_CATALOG, newCatalog).apply();
        }

        // Update all elements
        List<Uri> allUris = getImageUrisBase();
        for (Uri uri : allUris) {
            java.util.List<String> tags = getTags(uri);
            if (tags.contains(oldName)) {
                removeTag(uri, oldName);
                addTag(uri, newName);
            }
        }

        // Update active filters
        java.util.Set<String> active = getActiveTags();
        if (active.contains(oldName)) {
            java.util.Set<String> newActive = new java.util.HashSet<>(active);
            newActive.remove(oldName);
            newActive.add(newName);
            setActiveTags(newActive);
        }

        // Update hidden filters
        java.util.Set<String> hidden = getHiddenTags();
        if (hidden.contains(oldName)) {
            java.util.Set<String> newHidden = new java.util.HashSet<>(hidden);
            newHidden.remove(oldName);
            newHidden.add(newName);
            setHiddenTags(newHidden);
        }
    }

    public java.util.Set<String> getActiveTags() {
        return preferences.getStringSet(PREFERENCE_KEY_ACTIVE_TAGS, new java.util.HashSet<>());
    }

    public void setActiveTags(java.util.Set<String> tags) {
        preferences.edit().putStringSet(PREFERENCE_KEY_ACTIVE_TAGS, tags).apply();
    }

    public TagFilterMode getTagFilterMode() {
        String value = preferences.getString(PREFERENCE_KEY_TAG_FILTER_MODE, "or");
        return TagFilterMode.fromValue(value);
    }

    public void setTagFilterMode(TagFilterMode mode) {
        preferences.edit().putString(PREFERENCE_KEY_TAG_FILTER_MODE, mode.getValue()).apply();
    }

    public java.util.Set<String> getHiddenTags() {
        return preferences.getStringSet(PREFERENCE_KEY_HIDDEN_TAGS, new java.util.HashSet<>());
    }

    public void setHiddenTags(java.util.Set<String> tags) {
        preferences.edit().putStringSet(PREFERENCE_KEY_HIDDEN_TAGS, tags).apply();
    }

    public boolean isAutoTagEnabled() {
        return preferences.getBoolean(PREFERENCE_KEY_AUTO_TAG_ENABLED, false);
    }

    public void setAutoTagEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREFERENCE_KEY_AUTO_TAG_ENABLED, enabled).apply();
    }

    public List<Uri> getFilteredImageUris() {
        List<Uri> allUris = getImageUrisBase();
        java.util.Set<String> activeTags = getActiveTags();
        java.util.Set<String> hiddenTags = getHiddenTags();
        TagFilterMode mode = getTagFilterMode();
        
        List<Uri> filtered = new ArrayList<>();
        for (Uri uri : allUris) {
            List<String> uriTags = getTags(uri);
            
            // Check hidden tags first - if it has ANY hidden tag, skip it entirely
            boolean isHidden = false;
            for (String hTag : hiddenTags) {
                if (uriTags.contains(hTag)) {
                    isHidden = true;
                    break;
                }
            }
            if (isHidden) continue;

            if (activeTags == null || activeTags.isEmpty()) {
                filtered.add(uri);
                continue;
            }

            boolean match = false;
            switch (mode) {
                case AND:
                    match = new java.util.HashSet<>(uriTags).containsAll(activeTags);
                    break;
                case OR:
                    for (String tag : activeTags) {
                        if (uriTags.contains(tag)) {
                            match = true;
                            break;
                        }
                    }
                    break;
                case XAND:
                    match = !new java.util.HashSet<>(uriTags).containsAll(activeTags);
                    break;
                case XOR:
                    int count = 0;
                    for (String tag : activeTags) {
                        if (uriTags.contains(tag)) count++;
                    }
                    match = (count == 1);
                    break;
            }
            if (match) {
                filtered.add(uri);
            }
        }
        return filtered;
    }

    public boolean getMuteVideos() {
        return preferences.getBoolean(PREFERENCE_KEY_MUTE_VIDEOS, true);
    }

    public TagExportData exportTagData(android.content.Context context) {
        TagExportData data = new TagExportData();
        data.catalog = getMasterTagList();
        data.activeTags = getActiveTags();
        data.hiddenTags = getHiddenTags();
        data.tagFilterMode = getTagFilterMode().getValue();
        data.autoTagEnabled = isAutoTagEnabled();

        data.mappings = new java.util.HashMap<>();
        List<Uri> allUris = getImageUrisBase();
        for (Uri uri : allUris) {
            List<String> tags = getTags(uri);
            if (!tags.isEmpty()) {
                String name = getDisplayName(context, uri);
                if (name != null) {
                    data.mappings.put(name, tags);
                }
            }
        }
        return data;
    }

    public void importTagData(android.content.Context context, TagExportData data) {
        if (data == null) return;

        // Import Catalog
        if (data.catalog != null) {
            for (String tag : data.catalog) {
                addTagToCatalog(tag);
            }
        }

        // Build map Name -> List<Uri> for current files
        java.util.Map<String, List<Uri>> nameMap = new java.util.HashMap<>();
        for (Uri uri : getImageUrisBase()) {
            String name = getDisplayName(context, uri);
            if (name != null) {
                if (!nameMap.containsKey(name)) {
                    nameMap.put(name, new ArrayList<>());
                }
                nameMap.get(name).add(uri);
            }
        }

        // Import Mappings
        if (data.mappings != null) {
            for (java.util.Map.Entry<String, List<String>> entry : data.mappings.entrySet()) {
                String key = entry.getKey();
                List<Uri> targets = nameMap.get(key);

                if (targets == null) {
                    // Try exact URI match
                    for (Uri currentUri : getImageUrisBase()) {
                        if (currentUri.toString().equals(key)) {
                            targets = java.util.Collections.singletonList(currentUri);
                            break;
                        }
                    }
                }

                if (targets == null && key.contains("://")) {
                    // Try to extract filename from URI (fallback for old backups)
                    String nameFromUri = null;
                    try {
                        android.net.Uri oldUri = android.net.Uri.parse(key);
                        nameFromUri = oldUri.getLastPathSegment();

                        // Try to decode if it contains encoded parts (common in content URIs)
                        if (key.contains("%")) {
                            String decoded = android.net.Uri.decode(key);
                            int lastSlash = decoded.lastIndexOf('/');
                            if (lastSlash != -1) {
                                nameFromUri = decoded.substring(lastSlash + 1);
                            }
                        }
                    } catch (Exception e) { /* Ignore */ }

                    if (nameFromUri != null) {
                        targets = nameMap.get(nameFromUri);
                    }
                }

                if (targets != null) {
                    for (Uri uri : targets) {
                        for (String tag : entry.getValue()) {
                            addTag(uri, tag);
                        }
                    }
                }
            }
        }

        // Import Settings
        if (data.activeTags != null) setActiveTags(data.activeTags);
        if (data.hiddenTags != null) setHiddenTags(data.hiddenTags);
        if (data.tagFilterMode != null) setTagFilterMode(TagFilterMode.fromValue(data.tagFilterMode));
        setAutoTagEnabled(data.autoTagEnabled);
    }

    private String getDisplayName(android.content.Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, 
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
}
