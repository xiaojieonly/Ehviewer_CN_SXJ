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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.fragment.app.FragmentActivity;

import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.widget.MainContentLayout;
import com.hippo.scene.SceneFragment;
import com.hippo.util.AppHelper;

public abstract class BaseScene extends SceneFragment {

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static final String KEY_DRAWER_VIEW_STATE =
            "com.hippo.ehviewer.ui.scene.BaseScene:DRAWER_VIEW_STATE";

    private Context mThemeContext;

    @Nullable
    private View drawerView;
    @Nullable
    private SparseArray<Parcelable> drawerViewState;

    private final MainContentLayout.OnInsetsChangedListener mInsetsChangedListener =
            (top, bottomOccupied) -> onApplyWindowInsets(top, bottomOccupied);

    /** 记录场景根 view 的原始 padding,沉浸式 padding 在此基础上叠加 */
    @Nullable
    private View mPaddingBaseView;
    private int mBasePaddingBottom;

    public void addAboveSnackView(View view) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).addAboveSnackView(view);
        }
    }

    public void removeAboveSnackView(View view) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).removeAboveSnackView(view);
        }
    }

    public void setDrawerLockMode(int lockMode, int edgeGravity) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setDrawerLockMode(lockMode, edgeGravity);
        }
    }

//    public int getDrawerLockMode(int edgeGravity) {
//        FragmentActivity activity = getActivity();
//        if (activity instanceof MainActivity) {
//            return ((MainActivity) activity).getDrawerLockMode(edgeGravity);
//        } else {
//            return DrawerLayout.LOCK_MODE_UNLOCKED;
//        }
//    }

    public void openDrawer(int drawerGravity) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).openDrawer(drawerGravity);
        }
    }

    public void closeDrawer(int drawerGravity) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).closeDrawer(drawerGravity);
        }
    }

    public void setDrawerGestureBlocker(DrawerLayout.GestureBlocker gestureBlocker) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setDrawerGestureBlocker(gestureBlocker);
        }
    }

    public boolean isDrawersVisible() {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            return ((MainActivity) activity).isDrawersVisible();
        } else {
            return false;
        }
    }

    /**
     * @param resId 0 for clear
     */
    public void setNavCheckedItem(@IdRes int resId) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setNavCheckedItem(resId);
        }
    }

    public void showTip(CharSequence message, int length) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showTip(message, length);
        }
    }

    public void showTip(@StringRes int id, int length) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showTip(id, length);
        }
    }

    /**
     * 是否需要显示底部导航栏;仅最外层 tab 场景(首页/收藏/历史/下载/更多)返回 true,
     * 其余子页面(详情/评论/预览等)与流程场景(登录引导、安全锁等)一律隐藏
     */
    public boolean needShowBottomNav() {
        return false;
    }

    /**
     * 状态栏区域 scrim 颜色;返回 null 表示不着色(透明,露出窗口底色)。
     * 顶部有固定彩色区域的场景(如画廊详情页 header)可覆写返回与顶部同色,
     * MainActivity.updateStatusBarStyle 据此同步状态栏颜色
     */
    @Nullable
    public Integer getStatusBarScrimColor() {
        return null;
    }

    public void setBottomNavVisible(boolean visible) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setBottomNavVisible(visible);
        }
    }

    /*---------------
     Window insets(沉浸式)
     ---------------*/

    /**
     * 场景内容底部需要避让的高度:系统导航栏 inset + 底部导航栏(可见时)占位
     */
    public int getBottomOccupiedHeight() {
        MainActivity activity = getActivity2();
        return activity != null ? activity.getBottomOccupiedHeight() : 0;
    }

    /**
     * 是否由框架自动给场景根 view 叠加底部避让 padding;自行处理的场景返回 false
     */
    public boolean needFitNavigationBar() {
        return true;
    }

    /**
     * 是否由舞台容器统一避让状态栏(场景内容从状态栏下方开始);
     * 返回 false 的场景顶到屏幕顶端,自行按状态栏 inset 处理顶部避让,
     * 列表内容可沉浸式滚入状态栏区域(悬浮 app bar 场景:下载/历史;首页画廊列表)
     */
    public boolean needFitStatusBar() {
        return true;
    }

    /**
     * 窗口 inset 变化回调(onViewCreated 时会立即回调一次)。
     * 顶部由舞台容器统一避让(内容从状态栏下方默认区域开始),此处默认实现
     * 只按 needFitNavigationBar 给根 view 叠加底部 padding,
     * 根 view 背景不受 padding 影响,仍全幅绘制。
     */
    public void onApplyWindowInsets(int statusBarInset, int bottomOccupied) {
        View view = getView();
        if (view == null) {
            return;
        }
        if (view != mPaddingBaseView) {
            mPaddingBaseView = view;
            mBasePaddingBottom = view.getPaddingBottom();
        }
        int bottom = needFitNavigationBar() ? mBasePaddingBottom + bottomOccupied : mBasePaddingBottom;
        if (view.getPaddingBottom() != bottom) {
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom);
        }
    }

    /**
     * 底部让位工具:按基准值重算 view 的底部 padding(base + bottomOccupied),幂等。
     * 供自行处理底部避让的列表场景在 onApplyWindowInsets 中调用
     */
    protected static void applyBottomOccupiedPadding(@Nullable View view,
                                                     int basePaddingBottom, int bottomOccupied) {
        if (view == null) {
            return;
        }
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), basePaddingBottom + bottomOccupied);
    }

    /**
     * 返回底部导航栏选中的 tab id,0 表示不改变选中
     */
    public int getNavCheckedItem() {
        return 0;
    }

    public final View createDrawerView(LayoutInflater inflater,
                                       @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        drawerView = onCreateDrawerView(inflater, container, savedInstanceState);

        if (drawerView != null) {
            SparseArray<Parcelable> saved = drawerViewState;
            if (saved == null && savedInstanceState != null) {
                saved = savedInstanceState.getSparseParcelableArray(KEY_DRAWER_VIEW_STATE);
            }
            if (saved != null) {
                drawerView.restoreHierarchyState(saved);
            }
        }

        return drawerView;
    }

    public View onCreateDrawerView(LayoutInflater inflater,
                                   @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    public final void destroyDrawerView() {
        if (drawerView != null) {
            drawerViewState = new SparseArray<>();
            drawerView.saveHierarchyState(drawerViewState);
        }

        onDestroyDrawerView();

        drawerView = null;
    }

    public void onDestroyDrawerView() {
    }

    @Nullable
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater,
                                   @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return onCreateView2(LayoutInflater.from(getEHContext()), container, savedInstanceState);
    }

    @Nullable
    public View onCreateView2(LayoutInflater inflater,
                              @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 右侧筛选抽屉的锁定状态由各场景自行管理

        // 按场景显隐底部导航栏
        setBottomNavVisible(needShowBottomNav());

        // 按场景决定是否由舞台避让状态栏(悬浮 app bar 场景顶到屏幕顶端)
        MainActivity mainActivity = getActivity2();
        if (mainActivity != null) {
            mainActivity.setStageFitsStatusBar(needFitStatusBar());
        }

        // 沉浸式:注册窗口 inset 监听并立即应用一次
        // (必须在显隐底部导航之后,底部让位高度才是当前场景的)
        if (mainActivity != null) {
            mainActivity.addOnInsetsChangedListener(mInsetsChangedListener);
            onApplyWindowInsets(mainActivity.getWindowInsetTop(),
                    mainActivity.getBottomOccupiedHeight());
        }

        // Update nav checked item
        setNavCheckedItem(getNavCheckedItem());

        // 首帧布局后重算状态栏样式:事务提交是异步的,场景切换触发
        // MainActivity.updateStatusBarStyle 时新场景尚未创建(getTopScene 为空),
        // 可能留下过期的状态栏颜色/图标样式
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                MainActivity activity = getActivity2();
                if (activity != null) {
                    activity.updateStatusBarStyle();
                }
            }
        });

        // Hide soft ime
        AppHelper.hideSoftInput(requireActivity());
    }

    public void createThemeContext(@StyleRes int style) {
        mThemeContext = new ContextThemeWrapper(getContext(), style);
    }

    public void destroyThemeContext() {
        mThemeContext = null;
    }

    @Nullable
    public Context getEHContext() {
        return null != mThemeContext ? mThemeContext : super.getContext();
    }

    @Nullable
    public Resources getResources2() {
        Context context = getEHContext();
        if (null != context) {
            return context.getResources();
        } else {
            return null;
        }
    }

    @Nullable
    public MainActivity getActivity2() {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            return (MainActivity) activity;
        } else {
            return null;
        }
    }

    @NonNull
    public LayoutInflater getLayoutInflater2() {
        Context context = getEHContext();
        if (context == null) {
            context = getContext();
        }
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        if (layoutInflater == null) {
            layoutInflater = getLayoutInflater();
        }
        return layoutInflater;
    }

    public void hideSoftInput() {
        FragmentActivity activity = getActivity();
        if (null != activity) {
            AppHelper.hideSoftInput(activity);
        }
    }

    public void showSoftInput(@Nullable View view) {
        FragmentActivity activity = getActivity();
        if (null != activity && null != view) {
            AppHelper.showSoftInput(activity, view,true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Analytics.onSceneView(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MainActivity mainActivity = getActivity2();
        if (mainActivity != null) {
            mainActivity.removeOnInsetsChangedListener(mInsetsChangedListener);
        }
        mPaddingBaseView = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (drawerView != null) {
            drawerViewState = new SparseArray<>();
            drawerView.saveHierarchyState(drawerViewState);
            outState.putSparseParcelableArray(KEY_DRAWER_VIEW_STATE, drawerViewState);
        }
    }

    public void setTagList(UserTagList result){}

//    public boolean onGenericMotion(View view, MotionEvent motionEvent) {
//        if (motionEvent.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
//            if (motionEvent.getAction() == MotionEvent.ACTION_SCROLL) {
//                float scrollX = motionEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);
//                float scrollY = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
//                int x = view.getScrollX();
//                int y = view.getScrollY();
//                int toX = x + Math.round(scrollX);
//                int toY = y + Math.round(scrollY);
//                if (toX == view.getWidth()) {
//                    toX = view.getWidth();
//                }
//                if (toY == view.getHeight()) {
//                    toY = view.getHeight();
//                }
//                view.scrollTo(toX, toY);
//                Log.d("MA", "Mouse scrolled " + scrollX + ", " + scrollY);
//                return true;
//            }
//        }
//        return false;
//    }
}
