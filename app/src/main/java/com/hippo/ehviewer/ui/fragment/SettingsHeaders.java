package com.hippo.ehviewer.ui.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.SettingsActivity;

public class SettingsHeaders extends PreferenceFragmentCompat{
    private final SettingsActivity settingsActivity;
    public SettingsHeaders(SettingsActivity settingsActivity) {
        super();
        this.settingsActivity = settingsActivity;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.settings_headers, rootKey);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onNavigateToScreen(@NonNull PreferenceScreen preferenceScreen) {
        super.onNavigateToScreen(preferenceScreen);
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        if (settingsActivity.getSupportActionBar()!=null){
            settingsActivity.getSupportActionBar().setTitle(preference.getTitle());
        }else {
            settingsActivity.setTitle(preference.getTitle());
        }
        preference.setOnPreferenceChangeListener(this::onPreferenceChange);
        return super.onPreferenceTreeClick(preference);
    }

    private boolean onPreferenceChange(Preference preference, Object o) {
        return false;
    }

}
