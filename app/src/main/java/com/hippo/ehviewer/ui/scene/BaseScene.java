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

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.fragment.app.FragmentActivity;
import com.hippo.drawerlayout.DrawerLayout;
import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.ui.MainActivity;
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

    public void updateAvatar() {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateProfile();
        }
    }

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

    public int getDrawerLockMode(int edgeGravity) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            return ((MainActivity) activity).getDrawerLockMode(edgeGravity);
        } else {
            return DrawerLayout.LOCK_MODE_UNLOCKED;
        }
    }

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

    public void toggleDrawer(int drawerGravity) {
        FragmentActivity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).toggleDrawer(drawerGravity);
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

    public boolean needShowLeftDrawer() {
        return true;
    }

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
    public final View onCreateView(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return onCreateView2(LayoutInflater.from(getContext2()), container, savedInstanceState);
    }

    @Nullable
    public View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update left drawer locked state
        if (needShowLeftDrawer()) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
        } else {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
        }

        // Update nav checked item
        setNavCheckedItem(getNavCheckedItem());

        // Hide soft ime
        AppHelper.hideSoftInput(getActivity());
    }

    public void createThemeContext(@StyleRes int style) {
        mThemeContext = new ContextThemeWrapper(getContext(), style);
    }

    public void destroyThemeContext() {
        mThemeContext = null;
    }

    @Nullable
    public Context getContext2() {
        return null != mThemeContext ? mThemeContext : super.getContext();
    }

    @Nullable
    public Resources getResources2() {
        Context context = getContext2();
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

    @Nullable
    public LayoutInflater getLayoutInflater2() {
        return LayoutInflater.from(getContext2());
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
            AppHelper.showSoftInput(activity, view);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Analytics.onSceneView(this);
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
}
