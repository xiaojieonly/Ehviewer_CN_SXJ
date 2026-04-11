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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.parser.GalleryListParser;
import com.hippo.ehviewer.UrlOpener;
import com.hippo.ehviewer.local.LocalGalleryManager;
import com.hippo.ehviewer.ui.local.adapter.LocalGalleryCardAdapter;
import com.hippo.widget.FabLabelHelper;
import com.hippo.yorozuya.ViewUtils;

import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.io.File;
import java.util.List;

public class LocalGalleryListFragment extends Fragment implements LocalGalleryCardAdapter.OnLocalGalleryClickListener, LocalGalleryManager.LocalGalleryListener {
    
    private static final String KEY_IS_RECYCLE_BIN = "is_recycle_bin";
    
    private RecyclerView mRecyclerView;
    private LocalGalleryCardAdapter mAdapter;
    private LocalGalleryManager mLocalGalleryManager;
    private FloatingActionButton mFabRefresh;
    private FloatingActionButton mFabEmptyRecycleBin;
    private FloatingActionButton mFabRefreshRecycleBin;
    private FloatingActionButton mFabRescanRecycleBin;
    private View mRecycleBinFabGroup;
    private View mEmptyView;
    private TextView mEmptyText;
    
    private List<LocalGalleryInfo> mGalleries;
    private boolean mIsRecycleBin;
    
    private static final String TAG = "LocalGalleryList";
    
    public static LocalGalleryListFragment newInstance(boolean isRecycleBin) {
        LocalGalleryListFragment fragment = new LocalGalleryListFragment();
        Bundle args = new Bundle();
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
            mIsRecycleBin = args.getBoolean(KEY_IS_RECYCLE_BIN);
        } else {
            mIsRecycleBin = false;
        }

        if (mGalleries == null) {
            mGalleries = new ArrayList<>();
        }
        
        mLocalGalleryManager = LocalGalleryManager.getInstance(requireContext());
        mLocalGalleryManager.addListener(this);
        List<LocalGalleryInfo> initial = mIsRecycleBin
                ? mLocalGalleryManager.getCachedRecycleBinGalleries()
                : mLocalGalleryManager.getCachedLocalGalleries();
        if (initial != null) {
            mGalleries.clear();
            mGalleries.addAll(initial);
        }
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
        mFabEmptyRecycleBin = view.findViewById(R.id.fab_empty_recycle_bin);
        mFabRefreshRecycleBin = view.findViewById(R.id.fab_refresh_recycle_bin);
        mFabRescanRecycleBin = view.findViewById(R.id.fab_rescan_recycle_bin);
        mRecycleBinFabGroup = view.findViewById(R.id.recycle_bin_fab_group);
        mEmptyView = view.findViewById(R.id.empty_view);
        mEmptyText = view.findViewById(R.id.empty_text);
        
        initViews();
        updateFabLabels();
        
        return view;
    }
    
    private void initViews() {
        Log.d(TAG, "initViews called with " + mGalleries.size() + " galleries, isRecycleBin: " + mIsRecycleBin);
        mAdapter = new LocalGalleryCardAdapter(requireContext(), mGalleries, mIsRecycleBin, this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        // 设置悬浮按钮点击事件
        mFabRefresh.setOnClickListener(v -> {
            if (!mIsRecycleBin) {
                Log.d(TAG, "开始刷新本地画廊列表");
                Toast.makeText(requireContext(), R.string.local_gallery_scanning, Toast.LENGTH_SHORT).show();
                mLocalGalleryManager.scanLocalGalleries(true);
            }
        });

        mFabEmptyRecycleBin.setOnClickListener(v -> {
            if (mIsRecycleBin) {
                mLocalGalleryManager.emptyRecycleBin();
                Toast.makeText(requireContext(), R.string.recycle_bin_empty_success, Toast.LENGTH_SHORT).show();
            }
        });

        mFabRefreshRecycleBin.setOnClickListener(v -> {
            if (mIsRecycleBin) {
                mLocalGalleryManager.scanLocalGalleries(true);
                Toast.makeText(requireContext(), R.string.recycle_bin_action_refresh, Toast.LENGTH_SHORT).show();
            }
        });

        mFabRescanRecycleBin.setOnClickListener(v -> {
            if (mIsRecycleBin) {
                mLocalGalleryManager.scanLocalGalleries(true);
                Toast.makeText(requireContext(), R.string.recycle_bin_action_rescan, Toast.LENGTH_SHORT).show();
            }
        });

        if (mIsRecycleBin) {
            mFabRefresh.setVisibility(View.GONE);
            mRecycleBinFabGroup.setVisibility(View.VISIBLE);
        } else {
            mFabRefresh.setVisibility(View.VISIBLE);
            mRecycleBinFabGroup.setVisibility(View.GONE);
        }

        updateEmptyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateFabLabels();
    }

    private void updateFabLabels() {
        boolean show = Settings.getShowFabFunctionName();
        FabLabelHelper.updateFabLabel(mFabRefresh, show);
        FabLabelHelper.updateFabLabel(mFabEmptyRecycleBin, show);
        FabLabelHelper.updateFabLabel(mFabRefreshRecycleBin, show);
        FabLabelHelper.updateFabLabel(mFabRescanRecycleBin, show);
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
            mLocalGalleryManager.scanLocalGalleries(true);
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
            List<String> itemList = new ArrayList<>();
            itemList.add(getString(R.string.local_gallery_search_info));
            if (!TextUtils.isEmpty(getFolderGidPrefix(gallery))) {
                itemList.add(getString(R.string.local_gallery_open_online_detail));
            }
            itemList.add(getString(R.string.local_gallery_delete_confirm));
            items = itemList.toArray(new String[0]);
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
                        int actionIndex = 0;
                        if (which == actionIndex) {
                            openOnlineSearch(gallery);
                            return;
                        }
                        actionIndex++;
                        if (!TextUtils.isEmpty(getFolderGidPrefix(gallery))) {
                            if (which == actionIndex) {
                                openOnlineDetail(gallery);
                                return;
                            }
                            actionIndex++;
                        }
                        if (which == actionIndex) {
                            // 删除到回收站
                            showDeleteDialog(gallery);
                        }
                    }
                })
                .show();
    }

    private void openOnlineSearch(LocalGalleryInfo gallery) {
        if (gallery == null) {
            return;
        }
        String keyword = gallery.getDisplayTitle();
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(requireContext(), R.string.local_gallery_search_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        ListUrlBuilder builder = new ListUrlBuilder();
        builder.setMode(ListUrlBuilder.MODE_NORMAL);
        builder.setKeyword(keyword);
        UrlOpener.openUrl(requireContext(), builder.build(), true);
    }

    private void openOnlineDetail(LocalGalleryInfo gallery) {
        String gidPrefix = getFolderGidPrefix(gallery);
        if (TextUtils.isEmpty(gidPrefix)) {
            return;
        }
        long gid;
        try {
            gid = Long.parseLong(gidPrefix);
        } catch (NumberFormatException e) {
            return;
        }

        String token = gallery != null ? gallery.token : null;
        if (!TextUtils.isEmpty(token)) {
            String url = EhUrl.getGalleryDetailUrl(gid, token);
            UrlOpener.openUrl(requireContext(), url, true);
            return;
        }

        String keyword = gallery != null ? gallery.getDisplayTitle() : null;
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(requireContext(), R.string.local_gallery_search_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), R.string.local_gallery_searching, Toast.LENGTH_SHORT).show();
        ListUrlBuilder builder = new ListUrlBuilder();
        builder.setMode(ListUrlBuilder.MODE_NORMAL);
        builder.setKeyword(keyword);
        String url = builder.build();

        EhRequest request = new EhRequest();
        request.setMethod(EhClient.METHOD_GET_GALLERY_LIST);
        request.setArgs(url, builder.getMode());
        request.setCallback(new EhClient.Callback<GalleryListParser.Result>() {
            @Override
            public void onSuccess(GalleryListParser.Result result) {
                GalleryInfo match = findGalleryByGid(result, gid);
                if (match != null && !TextUtils.isEmpty(match.token)) {
                    String detailUrl = EhUrl.getGalleryDetailUrl(match.gid, match.token);
                    UrlOpener.openUrl(requireContext(), detailUrl, true);
                } else {
                    Toast.makeText(requireContext(), R.string.local_gallery_search_not_found, Toast.LENGTH_SHORT).show();
                    UrlOpener.openUrl(requireContext(), url, true);
                }
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(requireContext(), R.string.local_gallery_search_not_found, Toast.LENGTH_SHORT).show();
                UrlOpener.openUrl(requireContext(), url, true);
            }

            @Override
            public void onCancel() {
            }
        });
        EhApplication.getEhClient(requireContext()).execute(request);
    }

    private GalleryInfo findGalleryByGid(GalleryListParser.Result result, long gid) {
        if (result == null || result.galleryInfoList == null) {
            return null;
        }
        for (GalleryInfo info : result.galleryInfoList) {
            if (info != null && info.gid == gid) {
                return info;
            }
        }
        return null;
    }

    private String getFolderGidPrefix(LocalGalleryInfo gallery) {
        if (gallery == null || TextUtils.isEmpty(gallery.path)) {
            return null;
        }
        String name = new File(gallery.path).getName();
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        int index = 0;
        while (index < name.length() && Character.isDigit(name.charAt(index))) {
            index++;
        }
        if (index == 0) {
            return null;
        }
        return name.substring(0, index);
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
                    performPermanentDeleteWithProgress(gallery);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performPermanentDeleteWithProgress(LocalGalleryInfo gallery) {
        Context context = requireContext();
        if (context == null) {
            return;
        }

        ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);

        TextView progressText = new TextView(context);
        progressText.setText(getString(R.string.recycle_bin_delete_progress, 0, 0));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (context.getResources().getDisplayMetrics().density * 12);
        layout.setPadding(padding, padding, padding, padding);
        layout.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(progressText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog progressDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.recycle_bin_delete_permanent)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton(R.string.background_processing, (d, w) -> d.dismiss())
                .create();
        progressDialog.show();

        mLocalGalleryManager.permanentlyDeleteGallery(gallery, (current, total, detail) -> {
            if (total > 0) {
                int percent = current * 100 / total;
                progressBar.setProgress(percent);
                progressText.setText(getString(R.string.recycle_bin_delete_progress, current, total));
            }
            if (current >= total) {
                progressDialog.dismiss();
                Toast.makeText(context, R.string.recycle_bin_delete_permanent_success, Toast.LENGTH_SHORT).show();
            }
        });
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