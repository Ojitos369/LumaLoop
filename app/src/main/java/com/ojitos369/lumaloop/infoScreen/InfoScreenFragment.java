package com.ojitos369.lumaloop.infoScreen;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.ojitos369.lumaloop.R;

public class InfoScreenFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.info_screen);
    }
}
