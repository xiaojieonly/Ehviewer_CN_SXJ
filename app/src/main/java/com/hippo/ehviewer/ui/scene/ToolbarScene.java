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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.easyrecyclerview.FastScroller;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.widget.BottomNavHider;
import com.hippo.widget.SearchBarMover;

public abstract class ToolbarScene extends BaseScene {

    @Nullable
    private Toolbar mToolbar;
    @Nullable
    private View mAppBarContainer;

    private CharSequence mTempTitle;

    @Nullable
    public View onCreateView3(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    /**
     * 子类返回 true 时使用悬浮 app bar 布局(scene_toolbar_overlay):
     * app bar 叠于内容之上,子类可自行挂 SearchBarMover 随列表滚动抬升收起。
     * 默认 false 使用文档流布局(scene_toolbar),其余子类不受影响。
     */
    protected boolean useOverlayAppBar() {
        return false;
    }

    @Nullable
    @Override
    public final View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                useOverlayAppBar() ? R.layout.scene_toolbar_overlay : R.layout.scene_toolbar,
                container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        FrameLayout contentPanel = view.findViewById(R.id.content_panel);
        mAppBarContainer = view.findViewById(R.id.appbar_container);

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
        mAppBarContainer = null;
    }

    /**
     * 悬浮 app bar 容器(含 toolbar);非 overlay 布局时回退为 toolbar 本身
     */
    @Nullable
    protected View getAppBarContainer() {
        return mAppBarContainer != null ? mAppBarContainer : mToolbar;
    }

    /**
     * 悬浮 app bar 接线(overlay 布局场景在 onViewCreated 中调用):
     * 列表/FastScroller 顶部让出 app bar 高度(app bar 布局完成后才有高度,
     * clipToPadding=false,滚动时内容画进让位区直达状态栏下沿);
     * 滚动抬升 app bar,完全收起/重新可见时联动状态栏样式;
     * 列表滚动联动底部导航栏显隐(下滚隐藏/上滑显示,IDLE 吸附)。
     * 返回创建的 SearchBarMover,场景销毁时负责 cancelAnimation
     */
    @Nullable
    protected SearchBarMover attachOverlayAppBar(@NonNull SearchBarMover.Helper helper,
            @NonNull RecyclerView recyclerView, @Nullable FastScroller fastScroller,
            int listBasePaddingTop, int fastScrollerBasePaddingTop) {
        View appBar = getAppBarContainer();
        if (appBar == null) {
            return null;
        }
        appBar.post(() -> {
            int appBarHeight = appBar.getHeight();
            if (appBarHeight <= 0) {
                return;
            }
            recyclerView.setPadding(recyclerView.getPaddingLeft(),
                    listBasePaddingTop + appBarHeight,
                    recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
            if (fastScroller != null) {
                fastScroller.setPadding(fastScroller.getPaddingLeft(),
                        fastScrollerBasePaddingTop + appBarHeight,
                        fastScroller.getPaddingRight(), fastScroller.getPaddingBottom());
            }
        });
        SearchBarMover mover = new SearchBarMover(helper, appBar, recyclerView);
        mover.setOnBarVisibilityListener(fullyHidden -> {
            MainActivity activity = getActivity2();
            if (activity != null) {
                activity.updateStatusBarStyle();
            }
        });
        new BottomNavHider(getActivity2(), recyclerView);
        return mover;
    }

    /**
     * app bar 是否已完全收起(底边移出场景顶部);MainActivity 据此切换状态栏样式
     */
    public boolean isAppBarFullyHidden() {
        View appBar = getAppBarContainer();
        return appBar != null && appBar.getHeight() > 0
                && appBar.getBottom() + appBar.getTranslationY() <= 0;
    }

    /**
     * 顶部避让:文档流布局由舞台容器统一避让状态栏;
     * 悬浮 app bar 布局(下载/历史)顶到屏幕顶端,app bar 自行延伸进状态栏区域
     */
    @Override
    public boolean needFitStatusBar() {
        return !useOverlayAppBar();
    }

    /**
     * 悬浮 app bar 布局时,app bar 容器顶部留出状态栏占位,
     * 容器背景延伸进状态栏区域(状态栏颜色随顶栏)
     */
    @Override
    public void onApplyWindowInsets(int statusBarInset, int bottomOccupied) {
        super.onApplyWindowInsets(statusBarInset, bottomOccupied);
        if (useOverlayAppBar()) {
            View appBar = getAppBarContainer();
            if (appBar != null && appBar.getPaddingTop() != statusBarInset) {
                appBar.setPadding(appBar.getPaddingLeft(), statusBarInset,
                        appBar.getPaddingRight(), appBar.getPaddingBottom());
            }
        }
    }

    /**
     * 顶部由舞台容器统一避让;底部避让由各子场景的内容列表自行处理
     */
    @Override
    public boolean needFitNavigationBar() {
        return false;
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
