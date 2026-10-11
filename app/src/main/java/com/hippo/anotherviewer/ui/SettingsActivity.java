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

package com.hippo.anotherviewer.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.ui.fragment.SettingsHeaders;
import com.hippo.util.DrawableManager;

public final class SettingsActivity extends SiteActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final int REQUEST_CODE_FRAGMENT = 0;

    /** 当前是否为双栏布局（宽窗口：左侧分类列表 + 右侧详情栏）。 */
    private boolean mDualPane;
    private int mLastContentWidth = -1;

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
        setActionBarUpIndicator(DrawableManager.getVectorDrawable(this, R.drawable.v_arrow_left_dark_x24));
        // 先按窗口宽窄调整主栏宽度与详情栏可见性，再装填碎片
        applyPaneMode();
        // Duo 跨屏窗口的 configuration.screenWidthDp 是近似值（铰链避让等），
        // 以内容区真实像素宽为准再次对齐主栏宽度；拖拽跨屏是渐变过程，后续
        // 每次尺寸变化都会在这里重算，最终精确停在铰链处。
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
                int width = r - l;
                if (mDualPane && width > 0 && width != mLastContentWidth) {
                    applyPaneWidth(width);
                }
                mLastContentWidth = width;
            });
        }
        // 进程死亡恢复：若此前在窄屏（单栏）下打开过分类，该分类碎片会被恢复进主容器；
        // 双栏下主容器只应显示分类列表，清掉对应的返回栈并重新装填分类列表，让用户在左侧重新选择。
        if (savedInstanceState != null && isDualPane()) {
            Fragment primary = getSupportFragmentManager().findFragmentById(R.id.settings);
            if (primary != null && !(primary instanceof SettingsHeaders)
                    && getSupportFragmentManager().findFragmentById(R.id.settings_detail) == null) {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.settings, new SettingsHeaders())
                        .commit();
            }
        }
        if (savedInstanceState==null){
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings,new SettingsHeaders())
                    .commit();
        }
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

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 清单配置了 configChanges=screenSize，窗口宽窄切换走这里而不是重建 Activity
        if (isDualPane() != mDualPane) {
            applyPaneMode();
        } else if (mDualPane) {
            // 拖拽跨屏是渐变过程：布尔翻转那一刻取到的 screenWidthDp 是动画中间值，
            // 之后每次配置变化都要重算主栏宽度，最终对齐铰链
            applyPaneWidth();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference preference) {
        String fragment = preference.getFragment();
        if (fragment == null) return false;
        if (!isDualPane()) {
            // 单栏：返回 false 交给 androidx.preference 的默认回退，
            // 分类仍替换进主容器并入返回栈，行为与改造前一致
            return false;
        }
        try {
            // 与库的回退实现保持一致：通过 FragmentManager 的 FragmentFactory 实例化分类碎片
            // （FragmentFactory.instantiate 为实例方法，不抛受检异常，失败时抛 Fragment.InstantiationException）
            Fragment f = getSupportFragmentManager().getFragmentFactory()
                    .instantiate(getClassLoader(), fragment);
            if (preference.getExtras() != null) {
                f.setArguments(preference.getExtras());
            }
            setSettingsTitle(preference.getTitle());
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_detail, f)
                    .commit();
            // 详情碎片是不透明度不足的 RecyclerView，会透出底下的占位文字，选中分类后隐藏
            View placeholder = findViewById(R.id.settings_detail_placeholder);
            if (placeholder != null) {
                placeholder.setVisibility(View.GONE);
            }
            return true;
        } catch (Fragment.InstantiationException e) {
            return false;
        }
    }

    /** 宽窗口（跨双屏、平板、横屏）时使用双栏布局。 */
    public boolean isDualPane() {
        return getResources().getConfiguration().screenWidthDp >= 720;
    }

    /** 按当前窗口宽窄调整主栏宽度与详情栏可见性。 */
    private void applyPaneMode() {
        boolean dual = isDualPane();
        mDualPane = dual;

        applyPaneWidth();

        View detailWrap = findViewById(R.id.settings_detail_wrap);
        detailWrap.setVisibility(dual ? View.VISIBLE : View.GONE);

        if (dual) {
            // 进入双栏时尚未选中分类，恢复占位文字（选中后被隐藏）
            View placeholder = findViewById(R.id.settings_detail_placeholder);
            if (placeholder != null && getSupportFragmentManager()
                    .findFragmentById(R.id.settings_detail) == null) {
                placeholder.setVisibility(View.VISIBLE);
            }
        }

        if (!dual) {
            // 切回单栏：详情栏不再展示，移除其中的碎片，并把标题重置回「设置」
            Fragment detail = getSupportFragmentManager().findFragmentById(R.id.settings_detail);
            if (detail != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(detail)
                        .commit();
            }
            setSettingsTitle(R.string.settings);
        }
    }

    /** 主栏宽度：双栏时至少占窗口宽的一半（跨双屏正好到铰链），单栏占满整窗。 */
    private void applyPaneWidth() {
        View content = findViewById(android.R.id.content);
        int contentWidth = content != null ? content.getWidth() : 0;
        applyPaneWidth(contentWidth);
    }

    /** 双栏时主栏 = max(360dp, 内容区宽的一半)；单栏占满整窗。 */
    private void applyPaneWidth(int contentWidthPx) {
        View settings = findViewById(R.id.settings);
        ViewGroup.LayoutParams settingsLp = settings.getLayoutParams();
        if (mDualPane) {
            if (contentWidthPx <= 0) {
                // onCreate 阶段尚未布局（getWidth()=0），先按 configuration 估算，
                // 布局监听器拿到真实像素宽后会再校正
                float density = getResources().getDisplayMetrics().density;
                contentWidthPx = Math.round(
                        getResources().getConfiguration().screenWidthDp * density);
            }
            settingsLp.width = Math.max(dp(360), contentWidthPx / 2);
        } else {
            settingsLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        settings.setLayoutParams(settingsLp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
