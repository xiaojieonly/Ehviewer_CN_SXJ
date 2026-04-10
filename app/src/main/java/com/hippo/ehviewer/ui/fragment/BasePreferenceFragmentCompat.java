package com.hippo.ehviewer.ui.fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.SettingsActivity;
import com.hippo.ehviewer.ui.inset.WindowInsetHelper;

public class BasePreferenceFragmentCompat extends PreferenceFragmentCompat {
    private SettingsActivity settingsActivity;
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView list = getListView();
        if (list != null) {
            list.setClipToPadding(false);
            WindowInsetHelper.applyBottomSystemBarToPadding(list);
        }
    }

    private void setBaseStyle(Preference preference) {
        preference.setIconSpaceReserved(false);
        if (preference instanceof PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                setBaseStyle(group.getPreference(i));
            }
        }
    }

    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        if (preferenceScreen != null)
            setBaseStyle(preferenceScreen);
        super.setPreferenceScreen(preferenceScreen);
    }

    @Override
    public void onDestroyView() {
        if (null==getActivity()){
            return;
        }
        SettingsActivity settingsActivity = (SettingsActivity) getActivity();
        settingsActivity.setSettingsTitle(R.string.settings);
        super.onDestroyView();

    }



}
