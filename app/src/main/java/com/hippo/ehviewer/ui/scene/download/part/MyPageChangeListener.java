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

import android.annotation.SuppressLint;
import androidx.recyclerview.widget.RecyclerView;
import com.sxj.paginationlib.PaginationIndicator;

/**
 * 分页指示器的页面变化监听器
 */
public class MyPageChangeListener implements PaginationIndicator.OnChangedListener {

    private int indexPage = 1;
    private int pageSize = 1;
    private boolean needInitPage = false;
    private boolean doNotScroll = false;
    private RecyclerView.Adapter<?> mAdapter;
    private RecyclerView mRecyclerView;
    private PageChangeCallback mPageChangeCallback;
    
    // 添加防抖机制
    private long lastClickTime = 0;
    private static final long CLICK_DEBOUNCE_DELAY = 300; // 300ms防抖延迟

    public MyPageChangeListener() {
    }

    public MyPageChangeListener(int indexPage, int pageSize, boolean needInitPage, 
                               boolean doNotScroll, RecyclerView.Adapter<?> adapter, 
                               RecyclerView recyclerView) {
        this.indexPage = indexPage;
        this.pageSize = pageSize;
        this.needInitPage = needInitPage;
        this.doNotScroll = doNotScroll;
        this.mAdapter = adapter;
        this.mRecyclerView = recyclerView;
    }
    
    public void setPageChangeCallback(PageChangeCallback callback) {
        this.mPageChangeCallback = callback;
    }

    @Override
    public void onPageSelectedChanged(int currentPagePos, int lastPagePos, int totalPageCount, int total) {
        // 防抖检查
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_DELAY) {
            return;
        }
        lastClickTime = currentTime;
        
        // 如果是初始化阶段且当前页就是目标页，重置标志
        if (needInitPage && indexPage == currentPagePos) {
            needInitPage = false;
            return;
        }
        
        // 如果需要初始化且当前页不是目标页，跳过本次处理
        if (needInitPage) {
            return;
        }
        
        // 如果页码没有变化，不需要处理
        if (indexPage == currentPagePos) {
            return;
        }
        
        indexPage = currentPagePos;
        
        // 通过回调更新主类的状态
        if (mPageChangeCallback != null) {
            mPageChangeCallback.onPageChanged(indexPage);
        }
        
        notifyAdapter();
    }

    @Override
    public void onPerPageCountChanged(int perPageCount) {
        // 防抖检查
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_DEBOUNCE_DELAY) {
            return;
        }
        lastClickTime = currentTime;
        
        // 如果页面大小没有变化，不需要处理
        if (pageSize == perPageCount) {
            return;
        }
        
        pageSize = perPageCount;
        
        // 通过回调更新主类的状态
        if (mPageChangeCallback != null) {
            mPageChangeCallback.onPageSizeChanged(pageSize);
        }
        
        notifyAdapter();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void notifyAdapter() {
        // 确保在主线程中执行
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            if (mAdapter != null) {
                try {
                    mAdapter.notifyDataSetChanged();
                } catch (Exception e) {
                    android.util.Log.e("MyPageChangeListener", "Error notifying adapter: " + e.getMessage());
                }
            }
            if (mRecyclerView != null) {
                try {
                    if (doNotScroll) {
                        doNotScroll = false;
                        return;
                    }
                    // 使用smoothScrollToPosition提供更好的用户体验
                    mRecyclerView.post(() -> {
                        try {
                            mRecyclerView.scrollToPosition(0);
                        } catch (Exception e) {
                            android.util.Log.e("MyPageChangeListener", "Error scrolling: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("MyPageChangeListener", "Error in scroll logic: " + e.getMessage());
                }
            }
        });
    }

    // Getter and Setter methods
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

    public boolean isNeedInitPage() {
        return needInitPage;
    }

    public void setNeedInitPage(boolean needInitPage) {
        this.needInitPage = needInitPage;
    }

    public boolean isDoNotScroll() {
        return doNotScroll;
    }

    public void setDoNotScroll(boolean doNotScroll) {
        this.doNotScroll = doNotScroll;
    }

    public RecyclerView.Adapter<?> getAdapter() {
        return mAdapter;
    }

    public void setAdapter(RecyclerView.Adapter<?> adapter) {
        this.mAdapter = adapter;
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.mRecyclerView = recyclerView;
    }
    
    public PageChangeCallback getPageChangeCallback() {
        return mPageChangeCallback;
    }

    /**
     * 分页变化回调接口
     */
    public interface PageChangeCallback {
        void onPageChanged(int newIndexPage);
        void onPageSizeChanged(int newPageSize);
    }
}
