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

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;

public abstract class ToolbarActivity extends EhActivity {

    @Override
    protected int getThemeResId(int theme) {
        switch (theme) {
            case Settings.THEME_LIGHT:
            default:
                return R.style.AppTheme_Toolbar;
            case Settings.THEME_DARK:
                return R.style.AppTheme_Toolbar_Dark;
            case Settings.THEME_BLACK:
                return R.style.AppTheme_Toolbar_Black;
        }
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(R.layout.activity_toolbar);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.content_panel), true);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(R.layout.activity_toolbar);
        ((ViewGroup) findViewById(R.id.content_panel)).addView(view);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(R.layout.activity_toolbar);
        ((ViewGroup) findViewById(R.id.content_panel)).addView(view, params);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    }

    public void setNavigationIcon(@DrawableRes int resId) {
        View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            ((Toolbar) toolbar).setNavigationIcon(resId);
        }
    }

    public void setNavigationIcon(@Nullable Drawable icon) {
        View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            ((Toolbar) toolbar).setNavigationIcon(icon);
        }
    }
}
