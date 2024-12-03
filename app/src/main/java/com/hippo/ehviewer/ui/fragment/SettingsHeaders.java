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
    private SettingsActivity activity;
    public SettingsHeaders() {
        super();
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
        if (activity==null){
            activity = (SettingsActivity)getActivity();
        }
        if (activity!=null){
            if (activity.getSupportActionBar()!=null){
                activity.getSupportActionBar().setTitle(preference.getTitle());
            }else {
                activity.setTitle(preference.getTitle());
            }
        }
        preference.setOnPreferenceChangeListener(this::onPreferenceChange);
        return super.onPreferenceTreeClick(preference);
    }

    private boolean onPreferenceChange(Preference preference, Object o) {
        return false;
    }

}
