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
        builder.append("Build time: ").append(BuildConfig.BUILD_TIME).append('\n');
        builder.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")").append('\n');
        builder.append("Security patch: ")
                .append(Build.VERSION.SECURITY_PATCH == null ? "unknown" : Build.VERSION.SECURITY_PATCH)
                .append('\n');
        builder.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n');
        builder.append("Model: ").append(Build.MODEL).append('\n');
        builder.append("Brand: ").append(Build.BRAND).append('\n');
        builder.append("Product: ").append(Build.PRODUCT).append('\n');
        builder.append("Hardware: ").append(Build.HARDWARE).append('\n');
        builder.append("Board: ").append(Build.BOARD).append('\n');
        builder.append("Device: ").append(Build.DEVICE).append('\n');

        builder.append('\n');
        builder.append("WebView package:\n");
        PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
        if (webViewPackage != null) {
            builder.append("  Package: ").append(webViewPackage.packageName).append('\n');
            builder.append("  Version: ").append(webViewPackage.versionName)
                    .append(" (code ").append(webViewPackage.versionCode).append(")").append('\n');
        } else {
            builder.append("  unavailable\n");
        }

        builder.append('\n');
        builder.append("Application package: ").append(getPackageName()).append('\n');
        try {
            PackageInfo appInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            builder.append("Application version: ").append(appInfo.versionName)
                    .append(" (code ").append(appInfo.versionCode).append(")").append('\n');
        } catch (PackageManager.NameNotFoundException e) {
            builder.append("Application version: unknown\n");
        }

        return builder.toString();
    }
}
