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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.local.LocalGalleryManager;
import com.hippo.ehviewer.ui.local.adapter.LocalGalleryCardAdapter;
import com.hippo.yorozuya.ViewUtils;

import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class LocalGalleryListFragment extends Fragment implements LocalGalleryCardAdapter.OnLocalGalleryClickListener, LocalGalleryManager.LocalGalleryListener {
    
    private static final String KEY_GALLERIES = "galleries";
    private static final String KEY_IS_RECYCLE_BIN = "is_recycle_bin";
    
    private RecyclerView mRecyclerView;
    private LocalGalleryCardAdapter mAdapter;
    private LocalGalleryManager mLocalGalleryManager;
    private FloatingActionButton mFabRefresh;
    private View mEmptyView;
    private TextView mEmptyText;
    
    private List<LocalGalleryInfo> mGalleries;
    private boolean mIsRecycleBin;
    
    private static final String TAG = "LocalGalleryList";
    
    public static LocalGalleryListFragment newInstance(List<LocalGalleryInfo> galleries, boolean isRecycleBin) {
        LocalGalleryListFragment fragment = new LocalGalleryListFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(KEY_GALLERIES, new ArrayList<>(galleries));
        args.putBoolean(KEY_IS_RECYCLE_BIN, isRecycleBin);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        
        Bundle args = getArguments();
        if (args != null) {
            mGalleries = args.getParcelableArrayList(KEY_GALLERIES);
            mIsRecycleBin = args.getBoolean(KEY_IS_RECYCLE_BIN);
        } else {
            mGalleries = new ArrayList<>();
            mIsRecycleBin = false;
        }
        
        mLocalGalleryManager = LocalGalleryManager.getInstance(requireContext());
        mLocalGalleryManager.addListener(this);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocalGalleryManager != null) {
            mLocalGalleryManager.removeListener(this);
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_local_gallery_list, container, false);
        
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mFabRefresh = view.findViewById(R.id.fab_refresh);
        mEmptyView = view.findViewById(R.id.empty_view);
        mEmptyText = view.findViewById(R.id.empty_text);
        
        initViews();
        
        return view;
    }
    
    private void initViews() {
        Log.d(TAG, "initViews called with " + mGalleries.size() + " galleries, isRecycleBin: " + mIsRecycleBin);
        mAdapter = new LocalGalleryCardAdapter(requireContext(), mGalleries, mIsRecycleBin, this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        // 设置悬浮按钮点击事件
        mFabRefresh.setOnClickListener(v -> {
            Log.d(TAG, "开始刷新本地画廊列表");
            Toast.makeText(requireContext(), R.string.local_gallery_scanning, Toast.LENGTH_SHORT).show();
            mLocalGalleryManager.scanLocalGalleries();
        });
        
        updateEmptyView();
    }
    
    private void updateEmptyView() {
        if (mGalleries.isEmpty()) {
            mRecyclerView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
            
            // 根据是否是回收站设置不同的空状态文本
            if (mIsRecycleBin) {
                mEmptyText.setText(R.string.recycle_bin_empty);
            } else {
                mEmptyText.setText(R.string.local_gallery_empty);
            }
        } else {
            mRecyclerView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }
    
    public void updateGalleries(List<LocalGalleryInfo> galleries) {
        Log.d(TAG, "updateGalleries called with " + galleries.size() + " galleries, isRecycleBin: " + mIsRecycleBin);
        mGalleries.clear();
        mGalleries.addAll(galleries);
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
            Log.d(TAG, "Adapter notified, item count: " + mAdapter.getItemCount());
        } else {
            Log.w(TAG, "Adapter is null!");
        }
        updateEmptyView();
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mIsRecycleBin) {
            inflater.inflate(R.menu.fragment_local_gallery_list_recycle, menu);
        } else {
            inflater.inflate(R.menu.fragment_local_gallery_list, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh) {
            Log.d(TAG, "开始刷新本地画廊列表（菜单）");
            Toast.makeText(requireContext(), R.string.local_gallery_scanning, Toast.LENGTH_SHORT).show();
            mLocalGalleryManager.scanLocalGalleries();
            return true;
        } else if (itemId == R.id.action_empty_recycle_bin) {
            showEmptyRecycleBinDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showEmptyRecycleBinDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.recycle_bin_empty_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mLocalGalleryManager.emptyRecycleBin();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    @Override
    public void onLocalGalleryClick(LocalGalleryInfo gallery) {
        // 点击卡片本体 - 进入浏览界面
        LocalGalleryViewerActivity.start(requireContext(), gallery);
    }
    
    @Override
    public void onThumbClick(LocalGalleryInfo gallery) {
        // 点击缩略图 - 进入详情页
        LocalGalleryDetailActivity.start(requireContext(), gallery);
    }
    
    @Override
    public void onLocalGalleryLongClick(LocalGalleryInfo gallery) {
        showGalleryDialog(gallery);
    }
    
    private void showGalleryDialog(LocalGalleryInfo gallery) {
        String[] items;
        if (mIsRecycleBin) {
            items = new String[]{
                    getString(R.string.recycle_bin_restore),
                    getString(R.string.recycle_bin_delete_permanently)
            };
        } else {
            items = new String[]{
                    getString(R.string.local_gallery_search_info),
                    getString(R.string.local_gallery_delete_confirm)
            };
        }
        
        new AlertDialog.Builder(requireContext())
                .setTitle(gallery.getDisplayTitle())
                .setItems(items, (dialog, which) -> {
                    if (mIsRecycleBin) {
                        if (which == 0) {
                            // 还原
                            showRestoreDialog(gallery);
                        } else if (which == 1) {
                            // 永久删除
                            showPermanentDeleteDialog(gallery);
                        }
                    } else {
                        if (which == 0) {
                            // 搜索信息
                            LocalGalleryDetailActivity.start(requireContext(), gallery);
                        } else if (which == 1) {
                            // 删除到回收站
                            showDeleteDialog(gallery);
                        }
                    }
                })
                .show();
    }
    
    private void showDeleteDialog(LocalGalleryInfo gallery) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.local_gallery_delete_confirm)
                .setMessage(getString(R.string.local_gallery_delete_confirm, gallery.getDisplayTitle()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mLocalGalleryManager.deleteGallery(gallery);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    private void showRestoreDialog(LocalGalleryInfo gallery) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.recycle_bin_restore_confirm)
                .setMessage(getString(R.string.recycle_bin_restore_confirm, gallery.getDisplayTitle()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mLocalGalleryManager.restoreGallery(gallery);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    private void showPermanentDeleteDialog(LocalGalleryInfo gallery) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.recycle_bin_delete_permanent_confirm)
                .setMessage(getString(R.string.recycle_bin_delete_permanent_confirm, gallery.getDisplayTitle()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mLocalGalleryManager.permanentlyDeleteGallery(gallery);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    @Override
    public void onScanStart() {
        Log.d(TAG, "扫描开始");
        // 可以在这里显示进度指示器
    }
    
    @Override
    public void onScanProgress(String current) {
        Log.d(TAG, "扫描进度: " + current);
        // 可以在这里更新进度显示
    }
    
    @Override
    public void onScanComplete(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
        Log.d(TAG, "扫描完成 - 本地画廊: " + localGalleries.size() + ", 回收站: " + recycleBinGalleries.size());
        
        // 更新当前标签页的数据
        if (!mIsRecycleBin) {
            updateGalleries(localGalleries);
            String message = getString(R.string.local_gallery_scan_complete, localGalleries.size());
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            updateGalleries(recycleBinGalleries);
            String message = "回收站扫描完成，找到 " + recycleBinGalleries.size() + " 个画廊";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onGalleryDeleted(LocalGalleryInfo gallery, boolean success) {
        Log.d(TAG, "画廊删除结果: " + gallery.getDisplayTitle() + ", 成功: " + success);
        if (success) {
            Toast.makeText(requireContext(), R.string.local_gallery_delete_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.local_gallery_delete_failed, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onGalleryRestored(LocalGalleryInfo gallery, boolean success) {
        Log.d(TAG, "画廊还原结果: " + gallery.getDisplayTitle() + ", 成功: " + success);
        String message = success ? "画廊已还原" : "还原失败";
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}