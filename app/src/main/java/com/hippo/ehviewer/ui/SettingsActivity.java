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
import android.os.PersistableBundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.fragment.SettingsHeaders;
import com.hippo.util.DrawableManager;

public final class SettingsActivity extends ToolbarActivity {

    private static final int REQUEST_CODE_FRAGMENT = 0;



    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setNavigationIcon(DrawableManager.getVectorDrawable(this, R.drawable.v_arrow_left_dark_x24));
        setupTransparentNavigationBar();
        if (savedInstanceState==null){
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings,new SettingsHeaders())
                    .commit();
        }
    }

    /**
     * 让设置列表绘制到透明的系统导航栏之下，实现沉浸式效果。
     *
     * <p>{@code scene_toolbar} 根布局用 {@code fitsSystemWindows} 预留了底部导航栏高度的内边距，
     * 导致透明导航栏后只露出纯色的窗口背景。这里覆盖其 inset 处理：根布局只消费顶部/左右
     * （状态栏、横屏侧边栏），底部交给列表自行处理——列表加上等高的底部 padding 并关闭
     * clipToPadding，使内容可以滚动到导航栏之下。仅作用于设置页，不影响共享该布局的其它界面。
     */
    private void setupTransparentNavigationBar() {
        View contentPanel = findViewById(R.id.content_panel);
        if (contentPanel != null && contentPanel.getParent() instanceof View toolbarRoot) {
            ViewCompat.setOnApplyWindowInsetsListener(toolbarRoot, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, 0);
                return insets;
            });
        }

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                            @NonNull View v, @Nullable Bundle savedInstanceState) {
                        if (!(f instanceof PreferenceFragmentCompat)) {
                            return;
                        }
                        RecyclerView list = ((PreferenceFragmentCompat) f).getListView();
                        if (list == null) {
                            return;
                        }
                        list.setClipToPadding(false);
                        ViewCompat.setOnApplyWindowInsetsListener(list, (rv, insets) -> {
                            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                            rv.setPadding(rv.getPaddingLeft(), rv.getPaddingTop(),
                                    rv.getPaddingRight(), bottom);
                            return insets;
                        });
                        ViewCompat.requestApplyInsets(list);
                    }
                }, true);
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
