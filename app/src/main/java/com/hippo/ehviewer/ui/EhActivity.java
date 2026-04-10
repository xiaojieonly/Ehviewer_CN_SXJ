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
import android.util.TypedValue;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.AttrRes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.hippo.content.ContextLocalWrapper;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.adaptive.AdaptiveWindowState;
import com.hippo.ehviewer.ui.adaptive.AdaptiveWindowStateController;

import java.util.Locale;

public abstract class EhActivity extends AppCompatActivity {

    @Nullable
    private AdaptiveWindowStateController adaptiveWindowStateController;
    @NonNull
    private AdaptiveWindowState adaptiveWindowState = AdaptiveWindowState.DEFAULT;

    @StyleRes
    protected abstract int getThemeResId(int theme);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

//        setTheme(getThemeResId(Settings.getTheme(context)));
        setTheme(getThemeResId(Settings.getTheme()));
        super.onCreate(savedInstanceState);
        applyEdgeToEdgeWindowPolicy();
        adaptiveWindowStateController = new AdaptiveWindowStateController(this, state -> {
            adaptiveWindowState = state;
            onAdaptiveWindowStateChanged(state);
        });
        adaptiveWindowState = adaptiveWindowStateController.computeInitialState();

        ((EhApplication) getApplication()).registerActivity(this);

        if (Analytics.isEnabled()) {
            FirebaseAnalytics.getInstance(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (adaptiveWindowStateController != null) {
            adaptiveWindowStateController.stop();
        }
        super.onDestroy();

        ((EhApplication) getApplication()).unregisterActivity(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adaptiveWindowStateController != null) {
            adaptiveWindowStateController.start();
        }
    }

    @Override
    protected void onStop() {
        if (adaptiveWindowStateController != null) {
            adaptiveWindowStateController.stop();
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyEdgeToEdgeWindowPolicy();
        if(Settings.getEnabledSecurity()){
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    protected boolean shouldUseEdgeToEdge() {
        return true;
    }

    protected boolean shouldUseLightStatusBarIcons() {
        return resolveLightSystemBarAppearance();
    }

    protected boolean shouldUseLightNavigationBarIcons() {
        return resolveLightSystemBarAppearance();
    }

    protected int getStatusBarColor() {
        return Color.TRANSPARENT;
    }

    protected int getNavigationBarColor() {
        return Color.TRANSPARENT;
    }

    protected boolean shouldApplyCutoutShortEdges() {
        return false;
    }

    protected void onAdaptiveWindowStateChanged(@NonNull AdaptiveWindowState state) {
    }

    @NonNull
    protected AdaptiveWindowState getAdaptiveWindowState() {
        return adaptiveWindowState;
    }

    protected final void applyEdgeToEdgeWindowPolicy() {
        if (!shouldUseEdgeToEdge()) {
            return;
        }
        final Window window = getWindow();
        // Clear translucent flags inherited from values-v19 — they add a grey scrim
        // that overrides statusBarColor and conflicts with edge-to-edge.
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(getStatusBarColor());
        window.setNavigationBarColor(getNavigationBarColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && shouldApplyCutoutShortEdges()) {
            window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(shouldUseLightStatusBarIcons());
            controller.setAppearanceLightNavigationBars(shouldUseLightNavigationBarIcons());
        }
    }

    protected boolean resolveLightSystemBarAppearance() {
        final int theme = Settings.getTheme();
        if (theme == Settings.THEME_DARK || theme == Settings.THEME_BLACK) {
            return false;
        }
        final TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(R.attr.isLightSystemBarAppearance, typedValue, true)) {
            return typedValue.data != 0;
        }
        final int fallbackColor = resolveColorAttribute(android.R.attr.windowBackground, Color.WHITE);
        return isColorLight(fallbackColor);
    }

    protected int resolveColorAttribute(@AttrRes int attrRes, int fallback) {
        final TypedValue typedValue = new TypedValue();
        if (!getTheme().resolveAttribute(attrRes, typedValue, true)) {
            return fallback;
        }
        if (typedValue.resourceId != 0) {
            return getResources().getColor(typedValue.resourceId, getTheme());
        }
        return typedValue.data;
    }

    private boolean isColorLight(int color) {
        final double luminance = (0.299 * Color.red(color))
                + (0.587 * Color.green(color))
                + (0.114 * Color.blue(color));
        return luminance >= 186;
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
                return;
            }
        }
        applyEdgeToEdgeWindowPolicy();
        if (adaptiveWindowStateController != null) {
            adaptiveWindowStateController.refresh();
        }
    }
}
