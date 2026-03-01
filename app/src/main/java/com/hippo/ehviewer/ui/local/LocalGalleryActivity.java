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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.local.LocalGalleryManager;
import com.hippo.ehviewer.ui.EhActivity;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class LocalGalleryActivity extends EhActivity implements LocalGalleryManager.LocalGalleryListener {
    
    private static final String KEY_GALLERY_INFO = "gallery_info";
    private static final int REQUEST_CODE_VIEW = 0;
    
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private LocalGalleryPagerAdapter mAdapter;
    private LocalGalleryManager mLocalGalleryManager;
    
    private List<LocalGalleryInfo> mLocalGalleries;
    private List<LocalGalleryInfo> mRecycleBinGalleries;

    private ProgressDialog mScanProgressDialog;
    private boolean mIsScanDialogShown = false;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, LocalGalleryActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_local_gallery);
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.local_gallery_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        
        mLocalGalleryManager = LocalGalleryManager.getInstance(this);
        mLocalGalleryManager.addListener(this);
        
        mLocalGalleries = new ArrayList<>();
        mRecycleBinGalleries = new ArrayList<>();
        
        initViews();
        
        // Do not auto-scan on entry; user can refresh manually.
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLocalGalleryManager != null) {
            mLocalGalleryManager.removeListener(this);
        }
        if (mScanProgressDialog != null) {
            mScanProgressDialog.dismiss();
            mScanProgressDialog = null;
        }
    }
    
    private void initViews() {
        mViewPager = findViewById(R.id.viewpager);
        mTabLayout = findViewById(R.id.tab_layout);
        
        mAdapter = new LocalGalleryPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mAdapter);
        mTabLayout.setupWithViewPager(mViewPager);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_local_gallery, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_refresh) {
            mLocalGalleryManager.scanLocalGalleries(true);
            return true;
        } else if (itemId == R.id.action_empty_recycle_bin) {
            showEmptyRecycleBinDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showEmptyRecycleBinDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.recycle_bin_empty_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mLocalGalleryManager.emptyRecycleBin();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    @Override
    public void onScanStart() {
        if (isFinishing()) {
            return;
        }
        if (mScanProgressDialog == null) {
            mScanProgressDialog = new ProgressDialog(this);
            mScanProgressDialog.setTitle(R.string.local_gallery_title);
            mScanProgressDialog.setMessage(getString(R.string.local_gallery_scanning));
            mScanProgressDialog.setIndeterminate(true);
            mScanProgressDialog.setCancelable(false);
            mScanProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "后台运行", (dialog, which) -> {
                mIsScanDialogShown = false;
                dialog.dismiss();
            });
        } else {
            mScanProgressDialog.setMessage(getString(R.string.local_gallery_scanning));
        }
        mIsScanDialogShown = true;
        mScanProgressDialog.show();
    }
    
    @Override
    public void onScanProgress(String current) {
        if (mScanProgressDialog != null && mIsScanDialogShown) {
            if (current != null && !current.isEmpty()) {
                mScanProgressDialog.setMessage(getString(R.string.local_gallery_scanning) + "\n" + current);
            } else {
                mScanProgressDialog.setMessage(getString(R.string.local_gallery_scanning));
            }
        }
    }
    
    @Override
    public void onScanComplete(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
        if (mScanProgressDialog != null && mIsScanDialogShown) {
            mScanProgressDialog.dismiss();
            mIsScanDialogShown = false;
        }
        mLocalGalleries.clear();
        mLocalGalleries.addAll(localGalleries);
        
        mRecycleBinGalleries.clear();
        mRecycleBinGalleries.addAll(recycleBinGalleries);
        
        // 直接更新 Fragment 数据，而不是重新创建
        if (mAdapter != null) {
            mAdapter.updateFragments();
        }
    }
    
    @Override
    public void onGalleryDeleted(LocalGalleryInfo gallery, boolean success) {
        if (success) {
            mLocalGalleries.remove(gallery);
            mAdapter.notifyDataSetChanged();
        }
    }
    
    @Override
    public void onGalleryRestored(LocalGalleryInfo gallery, boolean success) {
        if (success) {
            mRecycleBinGalleries.remove(gallery);
            mAdapter.notifyDataSetChanged();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VIEW && resultCode == RESULT_OK) {
            // 刷新数据
            mLocalGalleryManager.scanLocalGalleries(true);
        }
    }
    
    private class LocalGalleryPagerAdapter extends FragmentPagerAdapter {
        
        private static final int TAB_LOCAL = 0;
        private static final int TAB_RECYCLE_BIN = 1;
        private static final int TAB_COUNT = 2;
        
        private LocalGalleryListFragment mLocalFragment;
        private LocalGalleryListFragment mRecycleBinFragment;
        
        public LocalGalleryPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }
        
        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_LOCAL:
                    if (mLocalFragment == null) {
                        mLocalFragment = LocalGalleryListFragment.newInstance(false);
                    }
                    return mLocalFragment;
                case TAB_RECYCLE_BIN:
                    if (mRecycleBinFragment == null) {
                        mRecycleBinFragment = LocalGalleryListFragment.newInstance(true);
                    }
                    return mRecycleBinFragment;
                default:
                    throw new IllegalArgumentException("Invalid position: " + position);
            }
        }
        
        @Override
        public int getItemPosition(@NonNull Object object) {
            // 让 Fragment 能够重新创建
            return POSITION_NONE;
        }
        
        public void updateFragments() {
            Log.d("LocalGalleryActivity", "updateFragments called - local: " + mLocalGalleries.size() + ", recycle: " + mRecycleBinGalleries.size());
            if (mLocalFragment != null) {
                mLocalFragment.updateGalleries(mLocalGalleries);
                Log.d("LocalGalleryActivity", "Updated local fragment");
            } else {
                Log.w("LocalGalleryActivity", "Local fragment is null");
            }
            if (mRecycleBinFragment != null) {
                mRecycleBinFragment.updateGalleries(mRecycleBinGalleries);
                Log.d("LocalGalleryActivity", "Updated recycle fragment");
            } else {
                Log.w("LocalGalleryActivity", "Recycle fragment is null");
            }
        }
        
        @Override
        public int getCount() {
            return TAB_COUNT;
        }
        
        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case TAB_LOCAL:
                    return getString(R.string.local_gallery_title);
                case TAB_RECYCLE_BIN:
                    return getString(R.string.recycle_bin_title);
                default:
                    return null;
            }
        }
    }
}