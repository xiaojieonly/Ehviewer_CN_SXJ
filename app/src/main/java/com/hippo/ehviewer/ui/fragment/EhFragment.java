/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.fragment;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;

public class EhFragment extends BasePreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.eh_settings);

        Preference theme = findPreference(Settings.KEY_THEME);
        Preference themeAutoSwitch = findPreference(Settings.KEY_THEME_AUTO_SWITCH);
        Preference applyNavBarThemeColor = findPreference(Settings.KEY_APPLY_NAV_BAR_THEME_COLOR);
        Preference gallerySite = findPreference(Settings.KEY_GALLERY_SITE);
        Preference listMode = findPreference(Settings.KEY_LIST_MODE);
        Preference detailSize = findPreference(Settings.KEY_DETAIL_SIZE);
        Preference thumbSize = findPreference(Settings.KEY_THUMB_SIZE);
        Preference historyInfoSize = findPreference(Settings.KEY_HISTORY_INFO_SIZE);
        Preference showTagTranslations = findPreference(Settings.KEY_SHOW_TAG_TRANSLATIONS);
        Preference showGalleryComment = findPreference(Settings.KEY_SHOW_GALLERY_COMMENT);
        Preference tagTranslationsSource = findPreference("tag_translations_source");

        // System theme display
        Preference systemTheme = findPreference("system_theme");
        if (systemTheme != null) {
            systemTheme.setSummary(getSystemThemeSummary());
        }

        theme.setOnPreferenceChangeListener(this);
        themeAutoSwitch.setOnPreferenceChangeListener(this);
        applyNavBarThemeColor.setOnPreferenceChangeListener(this);
        gallerySite.setOnPreferenceChangeListener(this);
        listMode.setOnPreferenceChangeListener(this);
        detailSize.setOnPreferenceChangeListener(this);
        thumbSize.setOnPreferenceChangeListener(this);
        historyInfoSize.setOnPreferenceChangeListener(this);
        showTagTranslations.setOnPreferenceChangeListener(this);
        showGalleryComment.setOnPreferenceChangeListener(this);

        if (!EhTagDatabase.isPossible(getActivity())) {
            getPreferenceScreen().removePreference(showTagTranslations);
            getPreferenceScreen().removePreference(tagTranslationsSource);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (Settings.KEY_THEME.equals(key)) {
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        } else if (Settings.KEY_APPLY_NAV_BAR_THEME_COLOR.equals(key)) {
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        } else if (Settings.KEY_GALLERY_SITE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_LIST_MODE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_DETAIL_SIZE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_THUMB_SIZE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_SHOW_TAG_TRANSLATIONS.equals(key)) {
            if (Boolean.TRUE.equals(newValue)) {
                EhTagDatabase.update(getActivity());
            }
            return true;
        } else if (Settings.KEY_HISTORY_INFO_SIZE.equals(key)) {
            try{
                int num = Integer.parseInt(newValue.toString());
                if (num<Settings.DEFAULT_HISTORY_INFO_SIZE){
                    num = Settings.DEFAULT_HISTORY_INFO_SIZE;
                }
                EhDB.MAX_HISTORY_COUNT = num;
            }catch (NumberFormatException e){
                EhDB.MAX_HISTORY_COUNT = Settings.DEFAULT_HISTORY_INFO_SIZE;
            }
            return true;
        } else if (Settings.KEY_SHOW_GALLERY_COMMENT.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_THEME_AUTO_SWITCH.equals(key) && Boolean.TRUE.equals(newValue)) {
            if (Settings.getDarkModeStatus(getContext())) {
                Settings.putTheme(Settings.THEME_DARK);
            } else {
                Settings.putTheme(Settings.THEME_LIGHT);
            }
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        }
        return true;
    }

    private String getSystemThemeSummary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int uiMode = getContext().getResources().getConfiguration().uiMode;
            int nightMode = uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            switch (nightMode) {
                case android.content.res.Configuration.UI_MODE_NIGHT_YES:
                    return "深色";
                case android.content.res.Configuration.UI_MODE_NIGHT_NO:
                    return "浅色";
                case android.content.res.Configuration.UI_MODE_NIGHT_UNDEFINED:
                default:
                    return "不可用";
            }
        } else {
            return "不可用";
        }
    }
}
