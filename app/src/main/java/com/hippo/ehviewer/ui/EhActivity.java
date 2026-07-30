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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.hippo.content.ContextLocalWrapper;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.Settings;
import java.util.Locale;

public abstract class EhActivity extends AppCompatActivity {

    @StyleRes
    protected abstract int getThemeResId(int theme);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

//        setTheme(getThemeResId(Settings.getTheme(context)));
        setTheme(getThemeResId(Settings.getTheme()));
        super.onCreate(savedInstanceState);

        if (isEdgeToEdgeEnabled()) {
            applyEdgeToEdge();
        }

        ((EhApplication) getApplication()).registerActivity(this);

        if (Analytics.isEnabled()) {
            FirebaseAnalytics.getInstance(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ((EhApplication) getApplication()).unregisterActivity(this);
    }

    /**
     * 是否需要沉浸式系统栏;windowFullscreen 等自行管理系统栏的页面可覆写关闭
     */
    protected boolean isEdgeToEdgeEnabled() {
        return true;
    }

    /**
     * 全应用沉浸式:内容延伸至系统栏区域,状态栏/导航栏透明并关闭系统自动对比度蒙层,
     * 图标明暗由各叶主题的 windowLightStatusBar/windowLightNavigationBar 控制
     */
    protected void applyEdgeToEdge() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
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
        Context context = newBase;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Settings.isThemeAutoSwitchAvailable()) {
            boolean is_dark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            if ((Settings.getTheme() == 0) == is_dark) {
                if (is_dark) {
                    Settings.putTheme(Settings.THEME_DARK);
                } else {
                    Settings.putTheme(Settings.THEME_LIGHT);
                }
                ((EhApplication) getApplication()).recreate();
            }
        }
    }
}
