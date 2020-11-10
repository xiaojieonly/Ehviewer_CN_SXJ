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

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.hippo.android.resource.AttrResources;
import com.hippo.app.PrettyPreferenceActivity;
import com.hippo.content.ContextLocalWrapper;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import java.util.Locale;

public abstract class EhPreferenceActivity extends PrettyPreferenceActivity {

    @StyleRes
    protected abstract int getThemeResId(int theme);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(getThemeResId(Settings.getTheme()));

        super.onCreate(savedInstanceState);

        ((EhApplication) getApplication()).registerActivity(this);

        if (Analytics.isEnabled()) {
            FirebaseAnalytics.getInstance(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Settings.getApplyNavBarThemeColor()) {
            getWindow().setNavigationBarColor(AttrResources.getAttrColor(this, R.attr.colorPrimaryDark));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ((EhApplication) getApplication()).unregisterActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Settings.getEnabledSecurity()){
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = null;
        String language = Settings.getAppLanguage();
        if (language != null && !language.equals("system")) {
            String[] split = language.split("-");
            if (split.length == 1) {
                locale = new Locale(split[0]);
            } else if (split.length == 2) {
                locale = new Locale(split[0], split[1]);
            } else if (split.length == 3) {
                locale = new Locale(split[0], split[1], split[2]);
            }
        }

        if (locale == null) {
            locale = Resources.getSystem().getConfiguration().locale;
        }

        newBase = ContextLocalWrapper.wrap(newBase, locale);
        super.attachBaseContext(newBase);
    }
}
