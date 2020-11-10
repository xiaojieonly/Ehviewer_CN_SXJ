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

package com.hippo.ehviewer.ui.scene;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import com.hippo.ehviewer.R;

public abstract class ToolbarScene extends BaseScene {

    @Nullable
    private Toolbar mToolbar;

    private CharSequence mTempTitle;

    @Nullable
    public View onCreateView3(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    @Nullable
    @Override
    public final View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_toolbar, container, false);
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        FrameLayout contentPanel = (FrameLayout) view.findViewById(R.id.content_panel);

        View contentView = onCreateView3(inflater, contentPanel, savedInstanceState);
        if (contentView == null) {
            return null;
        } else {
            mToolbar = toolbar;
            contentPanel.addView(contentView, 0);
            return view;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mToolbar = null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mToolbar != null) {
            if (mTempTitle != null) {
                mToolbar.setTitle(mTempTitle);
                mTempTitle = null;
            }

            int menuResId = getMenuResId();
            if (menuResId != 0) {
                mToolbar.inflateMenu(menuResId);
                mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return ToolbarScene.this.onMenuItemClick(item);
                    }
                });
                onMenuCreated(mToolbar.getMenu());
            }
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onNavigationClick();
                }
            });
        }
    }

    public int getMenuResId() {
        return 0;
    }

    public void onMenuCreated(Menu menu) {
    }

    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    public void onNavigationClick() {
    }

    public void setNavigationIcon(@DrawableRes int resId) {
        if (mToolbar != null) {
            mToolbar.setNavigationIcon(resId);
        }
    }

    public void setNavigationIcon(@Nullable Drawable icon) {
        if (mToolbar != null) {
            mToolbar.setNavigationIcon(icon);
        }
    }

    public void setTitle(@StringRes int resId) {
        setTitle(getString(resId));
    }

    public void setTitle(CharSequence title) {
        if (mToolbar != null) {
            mToolbar.setTitle(title);
        } else {
            mTempTitle = title;
        }
    }
}
