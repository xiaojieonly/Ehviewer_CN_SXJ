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

package com.hippo.ehviewer.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.hippo.ehviewer.BuildConfig;
import com.hippo.ehviewer.R;

public class SystemInfoActivity extends EhActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_info);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.settings_about_system_info);
        }

        TextView textView = findViewById(R.id.system_info_text);
        if (textView != null) {
            textView.setText(buildSystemInfo());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private String buildSystemInfo() {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.settings_about_version)).append(": ")
                .append(BuildConfig.VERSION_NAME).append('\n');
        builder.append(getString(R.string.system_info_build_time)).append(": ")
                .append(BuildConfig.BUILD_TIME).append('\n');
        builder.append(getString(R.string.system_info_android)).append(": ")
                .append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")").append('\n');
        builder.append(getString(R.string.system_info_security_patch)).append(": ")
                .append(Build.VERSION.SECURITY_PATCH == null ? getString(R.string.system_info_unknown) : Build.VERSION.SECURITY_PATCH)
                .append('\n');
        builder.append(getString(R.string.system_info_manufacturer)).append(": ")
                .append(Build.MANUFACTURER).append('\n');
        builder.append(getString(R.string.system_info_model)).append(": ")
                .append(Build.MODEL).append('\n');
        builder.append(getString(R.string.system_info_brand)).append(": ")
                .append(Build.BRAND).append('\n');
        builder.append(getString(R.string.system_info_product)).append(": ")
                .append(Build.PRODUCT).append('\n');
        builder.append(getString(R.string.system_info_hardware)).append(": ")
                .append(Build.HARDWARE).append('\n');
        builder.append(getString(R.string.system_info_board)).append(": ")
                .append(Build.BOARD).append('\n');
        builder.append(getString(R.string.system_info_device)).append(": ")
                .append(Build.DEVICE).append('\n');

        builder.append('\n');
        builder.append(getString(R.string.system_info_webview_package)).append('\n');
        PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
        if (webViewPackage != null) {
            builder.append("  ").append(getString(R.string.system_info_package)).append(": ")
                    .append(webViewPackage.packageName).append('\n');
            builder.append("  ").append(getString(R.string.system_info_version)).append(": ")
                    .append(webViewPackage.versionName)
                    .append(" (code ").append(webViewPackage.versionCode).append(")").append('\n');
        } else {
            builder.append("  ").append(getString(R.string.unavailable)).append('\n');
        }

        builder.append('\n');
        builder.append(getString(R.string.system_info_application_package)).append(": ")
                .append(getPackageName()).append('\n');
        try {
            PackageInfo appInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            builder.append(getString(R.string.system_info_application_version)).append(": ")
                    .append(appInfo.versionName)
                    .append(" (code ").append(appInfo.versionCode).append(")").append('\n');
        } catch (PackageManager.NameNotFoundException e) {
            builder.append(getString(R.string.system_info_application_version)).append(": ")
                    .append(getString(R.string.system_info_unknown)).append('\n');
        }

        return builder.toString();
    }
}
