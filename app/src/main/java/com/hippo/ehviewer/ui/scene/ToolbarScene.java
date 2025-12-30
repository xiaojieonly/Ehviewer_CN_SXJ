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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
            
            // 为根视图设置WindowInsets监听器,使Toolbar和内容区域能够适应透明的状态栏和导航栏
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                
                // 为Toolbar设置顶部padding以适应状态栏
                toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    systemBars.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
                );
                
                // 注意：不再给contentPanel设置底部padding，以便列表内容可以延伸到导航栏下方。
                // 子Scene（如QuickSearchScene）需要自己处理RecyclerView的底部padding。
                
                return insets;
            });
            
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
                mToolbar.setOnMenuItemClickListener(ToolbarScene.this::onMenuItemClick);
                onMenuCreated(mToolbar.getMenu());
            }
            mToolbar.setNavigationOnClickListener(this::onNavigationClick);
            mToolbar.setOnClickListener(this::onClickListener);
        }
    }

    public void onClickListener(View view) {

    }


    public int getMenuResId() {
        return 0;
    }

    public void onMenuCreated(Menu menu) {
    }

    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    public void onNavigationClick(View view) {
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
