/*
 * Copyright 2025 EhViewer Contributors
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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.transfer.TransferActivity;

/**
 * 传输设置Fragment
 * 提供传输服务相关的设置选项
 */
public class TransferFragment extends BasePreferenceFragmentCompat
        implements Preference.OnPreferenceClickListener {

    private static final String KEY_START_TRANSFER_SERVICE = "start_transfer_service";
    private static final String KEY_OPEN_TRANSFER_MANAGER = "open_transfer_manager";
    private static final String KEY_TRANSFER_SERVICE_DESCRIPTION = "transfer_service_description";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.transfer_settings);

        Preference startService = findPreference(KEY_START_TRANSFER_SERVICE);
        Preference openManager = findPreference(KEY_OPEN_TRANSFER_MANAGER);

        if (startService != null) {
            startService.setOnPreferenceClickListener(this);
        }

        if (openManager != null) {
            openManager.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        
        if (KEY_START_TRANSFER_SERVICE.equals(key)) {
            startTransferService();
            return true;
        } else if (KEY_OPEN_TRANSFER_MANAGER.equals(key)) {
            openTransferManager();
            return true;
        }
        
        return false;
    }

    /**
     * 启动传输服务
     */
    private void startTransferService() {
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), TransferActivity.class);
            startActivity(intent);
        }
    }

    /**
     * 打开传输管理器
     */
    private void openTransferManager() {
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), TransferActivity.class);
            startActivity(intent);
        }
    }
}
