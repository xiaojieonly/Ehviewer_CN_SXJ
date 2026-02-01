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

package com.hippo.ehviewer.ui.local;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.local.LocalGalleryManager;
import com.hippo.ehviewer.ui.EhActivity;
import com.hippo.ehviewer.ui.local.fragment.LocalGalleryDetailFragment;
import com.hippo.ehviewer.ui.local.fragment.LocalGalleryPreviewsFragment;
import com.hippo.ehviewer.ui.local.fragment.LocalGalleryCommentsFragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocalGalleryDetailActivity extends EhActivity implements LocalGalleryManager.LocalGalleryListener {
    
    private static final String KEY_GALLERY_INFO = "gallery_info";
    
    private LocalGalleryInfo mGalleryInfo;
    private LocalGalleryManager mLocalGalleryManager;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    
    public static void start(Context context, LocalGalleryInfo galleryInfo) {
        Intent intent = new Intent(context, LocalGalleryDetailActivity.class);
        intent.putExtra(KEY_GALLERY_INFO, galleryInfo);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_local_gallery_detail);
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mGalleryInfo != null ? mGalleryInfo.getDisplayTitle() : getString(R.string.local_gallery_title));
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        
        mLocalGalleryManager = LocalGalleryManager.getInstance(this);
        mLocalGalleryManager.addListener(this);
        
        // 获取画廊信息
        Intent intent = getIntent();
        if (intent != null) {
            mGalleryInfo = intent.getParcelableExtra(KEY_GALLERY_INFO);
        }
        
        if (mGalleryInfo == null) {
            finish();
            return;
        }
        
        initViews();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLocalGalleryManager != null) {
            mLocalGalleryManager.removeListener(this);
        }
    }
    
    private void initViews() {
        mViewPager = findViewById(R.id.viewpager);
        mTabLayout = findViewById(R.id.tab_layout);
        
        if (mViewPager != null && mTabLayout != null) {
            setupViewPager();
        } else {
            // 如果没有ViewPager和TabLayout，则显示简单信息
            showGalleryInfo();
        }
    }
    
    private void setupViewPager() {
        mViewPager.setAdapter(new DetailPagerAdapter(getSupportFragmentManager()));
        mTabLayout.setupWithViewPager(mViewPager);
    }
    
    private class DetailPagerAdapter extends FragmentStatePagerAdapter {
        
        public DetailPagerAdapter(androidx.fragment.app.FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }
        
        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return LocalGalleryDetailFragment.newInstance(mGalleryInfo);
                case 1:
                    return LocalGalleryPreviewsFragment.newInstance(mGalleryInfo);
                case 2:
                    return LocalGalleryCommentsFragment.newInstance(mGalleryInfo);
                default:
                    return LocalGalleryDetailFragment.newInstance(mGalleryInfo);
            }
        }
        
        @Override
        public int getCount() {
            return 3;
        }
        
        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "详情";
                case 1:
                    return "预览";
                case 2:
                    return "评论";
                default:
                    return "";
            }
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_local_gallery_detail, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_view) {
            // 查看画廊
            LocalGalleryViewerActivity.start(this, mGalleryInfo);
            return true;
        } else if (itemId == R.id.action_info) {
            // 显示信息
            showGalleryInfo();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showGalleryInfo() {
        // 创建信息对话框
        StringBuilder info = new StringBuilder();
        info.append("标题: ").append(mGalleryInfo.getDisplayTitle()).append("\n");
        info.append("路径: ").append(mGalleryInfo.path).append("\n");
        info.append("大小: ").append(formatFileSize(mGalleryInfo.size)).append("\n");
        info.append("图片数量: ").append(mGalleryInfo.pageCount).append("\n");
        
        if (mGalleryInfo.category != null) {
            info.append("分类: ").append(mGalleryInfo.category).append("\n");
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.local_gallery_info_title)
                .setMessage(info.toString())
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.local_gallery_search_info, (dialog, which) -> {
                    searchGalleryInfo();
                })
                .show();
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    private void searchGalleryInfo() {
        Toast.makeText(this, R.string.local_gallery_searching, Toast.LENGTH_SHORT).show();
        
        // 这里可以实现搜索画廊信息的功能
        // 例如根据文件夹名称搜索对应的在线画廊信息
        // 由于这是简化版本，暂时显示提示信息
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.local_gallery_search_info)
                .setMessage(R.string.local_gallery_search_not_found)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
    
    private void deleteGallery() {
        if (mGalleryInfo.type == LocalGalleryInfo.TYPE_RECYCLE_BIN) {
            // 回收站中的画廊永久删除
            new AlertDialog.Builder(this)
                    .setTitle(R.string.recycle_bin_delete_permanent_confirm)
                    .setMessage(getString(R.string.recycle_bin_delete_permanent_confirm, mGalleryInfo.getDisplayTitle()))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        mLocalGalleryManager.permanentlyDeleteGallery(mGalleryInfo);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            // 本地画廊移到回收站
            new AlertDialog.Builder(this)
                    .setTitle(R.string.local_gallery_delete_confirm)
                    .setMessage(getString(R.string.local_gallery_delete_confirm, mGalleryInfo.getDisplayTitle()))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        mLocalGalleryManager.deleteGallery(mGalleryInfo);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }
    
    private void viewGallery() {
        LocalGalleryViewerActivity.start(this, mGalleryInfo);
    }
    
    @Override
    public void onScanStart() {
        // 不需要处理
    }
    
    @Override
    public void onScanProgress(String current) {
        // 不需要处理
    }
    
    @Override
    public void onScanComplete(java.util.List<LocalGalleryInfo> localGalleries, java.util.List<LocalGalleryInfo> recycleBinGalleries) {
        // 不需要处理
    }
    
    @Override
    public void onGalleryDeleted(LocalGalleryInfo gallery, boolean success) {
        if (success && gallery.equals(mGalleryInfo)) {
            Toast.makeText(this, R.string.local_gallery_delete_success, Toast.LENGTH_SHORT).show();
            finish();
        } else if (!success) {
            Toast.makeText(this, R.string.local_gallery_delete_failed, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onGalleryRestored(LocalGalleryInfo gallery, boolean success) {
        if (success && gallery.equals(mGalleryInfo)) {
            Toast.makeText(this, R.string.recycle_bin_restore_success, Toast.LENGTH_SHORT).show();
            finish();
        } else if (!success) {
            Toast.makeText(this, R.string.recycle_bin_restore_failed, Toast.LENGTH_SHORT).show();
        }
    }
}