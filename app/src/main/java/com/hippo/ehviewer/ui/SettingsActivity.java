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

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.fragment.app.Fragment;

import com.hippo.ehviewer.ui.inset.WindowInsetHelper;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.adaptive.AdaptiveWindowState;
import com.hippo.ehviewer.ui.fragment.SettingsHeaders;
import com.hippo.util.DrawableManager;

public final class SettingsActivity extends EhActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final int REQUEST_CODE_FRAGMENT = 0;

    @Nullable
    private FrameLayout headersContainer;
    @Nullable
    private View settingsDivider;
    @Nullable
    private FrameLayout detailContainer;
    private boolean dualPane;

    @Override
    protected int getThemeResId(int theme) {
      switch (theme) {
        case Settings.THEME_LIGHT:
        default:
          return R.style.AppTheme_Settings;
        case Settings.THEME_DARK:
          return R.style.AppTheme_Settings_Dark;
        case Settings.THEME_BLACK:
          return R.style.AppTheme_Settings_Black;
      }
    }

    private void setActionBarUpIndicator(Drawable drawable) {
        ActionBarDrawerToggle.Delegate delegate = getDrawerToggleDelegate();
        if (delegate != null) {
            delegate.setActionBarUpIndicator(drawable, 0);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setTitle(R.string.settings);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        dualPane = getAdaptiveWindowState().supportsDualPane();
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            WindowInsetHelper.applyTopSystemBarToPadding(toolbar);
        }
        headersContainer = findViewById(R.id.settings_headers_container);
        settingsDivider = findViewById(R.id.settings_divider);
        detailContainer = findViewById(R.id.settings_detail_container);
        setActionBarUpIndicator(DrawableManager.getVectorDrawable(this, R.drawable.v_arrow_left_dark_x24));
        updateLayoutForMode();
        if (savedInstanceState==null){
            if (dualPane) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.settings_headers_container, new SettingsHeaders())
                        .commit();
            } else {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.settings_detail_container, new SettingsHeaders())
                        .commit();
            }
        }
    }

    @Override
    protected void onAdaptiveWindowStateChanged(@NonNull AdaptiveWindowState state) {
        boolean newDualPane = state.supportsDualPane();
        if (dualPane != newDualPane) {
            recreate();
            return;
        }
        updateLayoutForMode();
    }

    private void updateLayoutForMode() {
        if (headersContainer == null || settingsDivider == null || detailContainer == null) {
            return;
        }
        headersContainer.setVisibility(dualPane ? View.VISIBLE : View.GONE);
        settingsDivider.setVisibility(dualPane ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        String fragmentName = pref.getFragment();
        if (fragmentName == null) {
            return false;
        }

        Fragment fragment = getSupportFragmentManager()
                .getFragmentFactory()
                .instantiate(getClassLoader(), fragmentName);
        fragment.setArguments(pref.getExtras());

        if (dualPane) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_detail_container, fragment)
                    .commit();
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_detail_container, fragment)
                    .addToBackStack(fragmentName)
                    .commit();
        }
        setSettingsTitle(pref.getTitle());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getOnBackPressedDispatcher().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FRAGMENT) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void setSettingsTitle(int res){
        if (getSupportActionBar()!=null){
            getSupportActionBar().setTitle(res);
            return;
        }
        setTitle(res);
    }

    public void setSettingsTitle(CharSequence res){
        if (getSupportActionBar()!=null){
            getSupportActionBar().setTitle(res);
            return;
        }
        setTitle(res);
    }


}
