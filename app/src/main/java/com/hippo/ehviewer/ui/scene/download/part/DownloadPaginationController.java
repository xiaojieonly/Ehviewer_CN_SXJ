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

package com.hippo.ehviewer.ui.scene.download.part;

import static com.hippo.ehviewer.spider.SpiderInfo.getSpiderInfo;
import static com.hippo.ehviewer.ui.scene.download.DownloadsScene.LOCAL_GALLERY_INFO_CHANGE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.sync.DownloadSpiderInfoExecutor;
import com.hippo.ehviewer.widget.MyEasyRecyclerView;
import com.sxj.paginationlib.PaginationIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 下载列表分页与阅读进度。
 */
public class DownloadPaginationController {

    public interface Host {
        @Nullable
        List<DownloadInfo> getList();

        @Nullable
        RecyclerView.Adapter getNotifyAdapter();

        @Nullable
        MyEasyRecyclerView getRecyclerView();
    }

    @NonNull
    private final Host mHost;

    private int indexPage = 1;
    private int pageSize = 1;
    private boolean canPagination = true;
    private final int paginationSize = 500;
    private final int[] perPageCountChoices = {50, 100, 200, 300, 500};

    private MyPageChangeListener myPageChangeListener;
    @Nullable
    private PaginationIndicator mPaginationIndicator;

    private final Map<Long, SpiderInfo> mSpiderInfoMap = new HashMap<>();

    private boolean doNotScroll = false;
    private boolean needInitPage = false;
    private boolean needInitPageSize = false;

    public DownloadPaginationController(@NonNull Host host) {
        mHost = host;
    }

    public int getIndexPage() {
        return indexPage;
    }

    public void setIndexPage(int indexPage) {
        this.indexPage = indexPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isCanPagination() {
        return canPagination;
    }

    public void setCanPagination(boolean canPagination) {
        this.canPagination = canPagination;
    }

    public int getPaginationSize() {
        return paginationSize;
    }

    public int[] getPerPageCountChoices() {
        return perPageCountChoices;
    }

    public Map<Long, SpiderInfo> getSpiderInfoMap() {
        return mSpiderInfoMap;
    }

    public boolean isNeedInitPage() {
        return needInitPage;
    }

    public void setNeedInitPage(boolean needInitPage) {
        this.needInitPage = needInitPage;
    }

    @Nullable
    public PaginationIndicator getPaginationIndicator() {
        return mPaginationIndicator;
    }

    public void setPaginationIndicator(@Nullable PaginationIndicator paginationIndicator) {
        mPaginationIndicator = paginationIndicator;
    }

    public void bindPageChangeListener(@Nullable RecyclerView.Adapter adapter,
                                       @Nullable MyEasyRecyclerView recyclerView) {
        myPageChangeListener = new MyPageChangeListener(indexPage, pageSize, needInitPage, doNotScroll, adapter, recyclerView);
        myPageChangeListener.setPageChangeCallback(new MyPageChangeListener.PageChangeCallback() {
            @Override
            public void onPageChanged(int newIndexPage) {
                indexPage = newIndexPage;
                queryUnreadSpiderInfo();
            }

            @Override
            public void onPageSizeChanged(int newPageSize) {
                pageSize = newPageSize;
                queryUnreadSpiderInfo();
            }
        });
    }

    public int positionInList(int position) {
        List<DownloadInfo> list = mHost.getList();
        if (list != null && list.size() > paginationSize && canPagination) {
            return position + pageSize * (indexPage - 1);
        }
        return position;
    }

    public int listIndexInPage(int position) {
        List<DownloadInfo> list = mHost.getList();
        if (list != null && list.size() > paginationSize && canPagination) {
            return position % pageSize;
        }
        return position;
    }

    public void updatePaginationIndicator() {
        List<DownloadInfo> list = mHost.getList();
        if (mPaginationIndicator == null || list == null) {
            return;
        }
        if (list.size() < paginationSize || !canPagination) {
            mPaginationIndicator.setVisibility(View.GONE);
            return;
        }
        mPaginationIndicator.setVisibility(View.VISIBLE);
        needInitPageSize = true;
        mPaginationIndicator.initPaginationIndicator(pageSize, perPageCountChoices, list.size(), indexPage);
//        mPaginationIndicator.setTotalCount();
        mPaginationIndicator.setListener(myPageChangeListener);

        // 同步分页监听器的状态
        if (myPageChangeListener != null) {
            myPageChangeListener.setIndexPage(indexPage);
            myPageChangeListener.setPageSize(pageSize);
            myPageChangeListener.setNeedInitPage(needInitPage);
            myPageChangeListener.setDoNotScroll(doNotScroll);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateReadProcess(ActivityResult result) {
        if (result.getResultCode() == LOCAL_GALLERY_INFO_CHANGE) {
            Intent data = result.getData();
            if (data != null) {
                GalleryInfo info = data.getParcelableExtra("info");

                // Check if this is an imported archive - skip SpiderInfo processing
                boolean isImportedArchive = false;
                if (info instanceof DownloadInfo downloadInfo) {
                    isImportedArchive = downloadInfo.archiveUri != null &&
                            downloadInfo.archiveUri.startsWith("content://");
                }

                if (!isImportedArchive && info != null) {
                    // Only process SpiderInfo for regular downloads, not imported archives
                    mSpiderInfoMap.remove(info.gid);
                    SpiderInfo spiderInfo = getSpiderInfo(info);
                    if (spiderInfo != null) {
                        mSpiderInfoMap.put(info.gid, spiderInfo);
                    }
                    trimSpiderInfoMapToCurrentPage();
                }

//                mSpiderInfoMap.remove(info.gid);
//                SpiderInfo spiderInfo = getSpiderInfo(info);
                int position = -1;
                List<DownloadInfo> list = mHost.getList();
                RecyclerView.Adapter adapter = mHost.getNotifyAdapter();
                if (list == null || adapter == null || info == null) {
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).gid == info.gid) {
                        position = listIndexInPage(i);
                        break;
                    }
                }
                if (position != -1) {
                    adapter.notifyItemChanged(position);
                } else {
                    adapter.notifyDataSetChanged();
                }

            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void resetReadingProgressInUi() {
        for (SpiderInfo spiderInfo : mSpiderInfoMap.values()) {
            if (spiderInfo != null) {
                spiderInfo.startPage = 0;
            }
        }
        RecyclerView.Adapter adapter = mHost.getNotifyAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @NonNull
    public List<DownloadInfo> getCurrentPageList() {
        List<DownloadInfo> list = mHost.getList();
        if (list == null) {
            return Collections.emptyList();
        }
        if (list.size() > paginationSize && canPagination) {
            int from = pageSize * (indexPage - 1);
            if (from < 0) {
                from = 0;
            }
            if (from >= list.size()) {
                return Collections.emptyList();
            }
            int to = Math.min(from + pageSize, list.size());
            return list.subList(from, to);
        }
        return list;
    }

    public void trimSpiderInfoMapToCurrentPage() {
        List<DownloadInfo> pageList = getCurrentPageList();
        Set<Long> keep = new HashSet<>(pageList.size());
        for (DownloadInfo info : pageList) {
            keep.add(info.gid);
        }
        mSpiderInfoMap.keySet().retainAll(keep);
    }

    public void queryUnreadSpiderInfo() {
        List<DownloadInfo> list = mHost.getList();
        if (list == null) {
            return;
        }
        trimSpiderInfoMapToCurrentPage();
        List<DownloadInfo> pageList = getCurrentPageList();
        List<DownloadInfo> requestList = new ArrayList<>();
        for (int i = 0; i < pageList.size(); i++) {
            DownloadInfo info = pageList.get(i);
            if (!mSpiderInfoMap.containsKey(info.gid) || mSpiderInfoMap.get(info.gid) == null) {
                requestList.add(info);
            }
        }
        if (requestList.isEmpty()) {
            return;
        }
        fetchSpiderInfo(requestList);
    }

    public void fetchSpiderInfo(List<DownloadInfo> infos) {
        DownloadSpiderInfoExecutor executor = new DownloadSpiderInfoExecutor(infos, this::spiderInfoResultCallBack);
        executor.execute();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void spiderInfoResultCallBack(Map<Long, SpiderInfo> resultMap) {
        mSpiderInfoMap.putAll(resultMap);
        trimSpiderInfoMapToCurrentPage();
        RecyclerView.Adapter adapter = mHost.getNotifyAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void initPage(int position) {
        List<DownloadInfo> list = mHost.getList();
        if (list != null && list.size() > paginationSize && canPagination) {
            indexPage = position / pageSize + 1;
        }
        doNotScroll = true;
        if (mPaginationIndicator != null) {
            mPaginationIndicator.skip2Pos(indexPage);
        }
        MyEasyRecyclerView recyclerView = mHost.getRecyclerView();
        recyclerView.scrollToPosition(listIndexInPage(position));
    }

    public int getPageSizePos(int pageSize) {
        int index = 0;
        for (int i = 0; i < perPageCountChoices.length; i++) {
            if (pageSize == perPageCountChoices[i]) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * Called from {@link DownloadsScene#onDestroyView()}.
     * The controller outlives the view, so the old indicator/listener must be
     * released here. Otherwise {@code getPaginationIndicator() != null} stays
     * true on the next view creation and {@link #needInitPage} gets stuck,
     * which breaks page switching after returning from the detail scene.
     */
    public void onDestroyView() {
        mPaginationIndicator = null;
        myPageChangeListener = null;
    }
}
