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

import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;

import com.ojitos369.lumaloop.R;
import com.ojitos369.lumaloop.SlideshowWallpaperService;

public class WallpaperPreferencesFragment extends PreferenceFragmentCompat {
    public static int DEFAULT_SECONDS = 60;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.wallpaper_preferences);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Here getResources should never throw an IllegalStateException,
        // because onResume is only called, if an Activity is present.
        updateSummaries(getResources());
        getPreferenceManager().findPreference(getResources().getString(R.string.preference_preview_key)).setOnPreferenceClickListener(preference -> {
            // A click on a preference can only occur in a valid context
            Context ctx = getContext();
            if (ctx != null) {
                SharedPreferencesManager manager = new SharedPreferencesManager(getPreferenceManager().getSharedPreferences());
                if (manager.getImageUrisCount() == 0) {
                    new AlertDialog.Builder(ctx)
                            .setTitle(R.string.error_title)
                            .setMessage(R.string.no_images_selected_text)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setNeutralButton(android.R.string.ok, (DialogInterface dialogInterface, int i) -> {
                            })
                            .show();
                    return true;
                }

                Intent intent = new Intent(
                        WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(ctx, SlideshowWallpaperService.class));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    new AlertDialog.Builder(ctx)
                            .setTitle(R.string.error_title)
                            .setMessage(R.string.no_wallpaper_activity_text)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setNeutralButton(android.R.string.ok, (DialogInterface dialogInterface, int i) -> {
                            })
                            .show();
                }
                return true;
            } else {
                return false;
            }
        });
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this::updateSummary);
    }

    private void updateSummaries(Resources resources) {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        updateSummary(sharedPreferences, getResources().getString(R.string.preference_add_images_key));
        updateSummary(sharedPreferences, resources.getString(R.string.preference_interval_value_key));
        updateSummary(sharedPreferences, resources.getString(R.string.preference_interval_unit_key));
        updateSummary(sharedPreferences, resources.getString(R.string.preference_ordering_key));
        updateSummary(sharedPreferences, resources.getString(R.string.preference_too_wide_images_rule_key));
    }

    private <T> int getIndex(T[] values, T value) {
        int index = -1;
        for (int i = 0; (i < values.length) && (index < 0); i++) {
            if (values[i].equals(value)) {
                index = i;
            }
        }
        return index;
    }
    private void updateSummary(SharedPreferences sharedPreferences, String key) {
        Resources res = null;
        try {
            res = getResources();
        } catch (IllegalStateException e) {
            // There is no context currently -> We do not need to update the view
        }
        if (res != null) {
            if (key.equals(res.getString(R.string.preference_add_images_key))) {
                SharedPreferencesManager prefManager = new SharedPreferencesManager(sharedPreferences);
                int imagesCount = prefManager.getImageUris(SharedPreferencesManager.Ordering.SELECTION).size();

                int maxCount = 128;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    maxCount = 512;
                }
                findPreference(key).setSummary(res.getQuantityString(R.plurals.images_selected, imagesCount, imagesCount, maxCount));
            } else if (key.equals(res.getString(R.string.preference_interval_value_key)) || key.equals(res.getString(R.string.preference_interval_unit_key))) {
                String valueKey = res.getString(R.string.preference_interval_value_key);
                String unitKey = res.getString(R.string.preference_interval_unit_key);

                String intervalValueStr = sharedPreferences.getString(valueKey, "60");
                String unitValueStr = sharedPreferences.getString(unitKey, "1");

                findPreference(valueKey).setSummary(intervalValueStr);

                String[] unitValues = res.getStringArray(R.array.interval_unit_values);
                String[] unitEntries = res.getStringArray(R.array.interval_units);
                int unitIndex = getIndex(unitValues, unitValueStr);
                if (unitIndex != -1) {
                    findPreference(unitKey).setSummary(unitEntries[unitIndex]);
                }

                try {
                    int intervalValue = Integer.parseInt(intervalValueStr);
                    int unitValue = Integer.parseInt(unitValueStr);
                    int totalSeconds = intervalValue * unitValue;
                    sharedPreferences.edit().putString(res.getString(R.string.preference_seconds_key), String.valueOf(totalSeconds)).apply();
                } catch (NumberFormatException e) {
                    // Handle exception if parsing fails
                }

            } else if (key.equals(res.getString(R.string.preference_ordering_key))) {
                String[] orderings = res.getStringArray(R.array.orderings);
                String[] orderingValues = res.getStringArray(R.array.ordering_values);
                String currentValue = sharedPreferences.getString(key, SharedPreferencesManager.Ordering.SELECTION.getValue(res));
                int index = getIndex(orderingValues, currentValue);
                findPreference(key).setSummary(orderings[index]);
            } else if (key.equals(res.getString(R.string.preference_too_wide_images_rule_key))) {
                String[] displayRules = res.getStringArray(R.array.too_wide_images_rules);
                String[] displayRuleValues = res.getStringArray(R.array.too_wide_images_rule_values);
                String currentValue = sharedPreferences.getString(key, SharedPreferencesManager.TooWideImagesRule.SCALE_DOWN.getValue(res));
                int index = getIndex(displayRuleValues, currentValue);
                findPreference(key).setSummary(displayRules[index]);
            }
        }

    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this::updateSummary);
        super.onPause();
    }
}
