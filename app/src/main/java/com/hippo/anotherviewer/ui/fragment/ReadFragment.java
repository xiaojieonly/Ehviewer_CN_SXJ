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

package com.hippo.anotherviewer.ui.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.ui.pane.DuoSupport;

public class ReadFragment extends BasePreferenceFragmentCompat {

    /**
     * 设置->阅读界面
     * xml/read_settings.xml
     * @param savedInstanceState
     */
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.read_settings);
        // Duo 真机：横屏双页由折叠姿态自动决定，不给用户选择，
        // 隐藏相关偏好项，避免出现无效设置。
        if (DuoSupport.isDuoHardware()) {
            String[] hiddenKeys = {"reading_dual_page"};
            for (String key : hiddenKeys) {
                Preference preference = findPreference(key);
                if (preference != null) {
                    preference.setVisible(false);
                }
            }
        }
    }
}
