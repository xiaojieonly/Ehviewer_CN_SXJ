/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.FastScroller;
import com.hippo.easyrecyclerview.HandlerDrawable;
import com.hippo.easyrecyclerview.LayoutManagerUtils;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.refreshlayout.RefreshLayout;
import com.hippo.util.DrawableManager;
import com.hippo.util.ExceptionUtils;
import com.hippo.view.ViewTransition;
import com.hippo.yorozuya.IntIdGenerator;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.collect.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ContentLayout extends FrameLayout {

    private ProgressView mProgressView;
    private TextView mTipView;
    private ViewGroup mContentView;

    private RefreshLayout mRefreshLayout;
    private EasyRecyclerView mRecyclerView;
    private FastScroller mFastScroller;

    private ContentHelper mContentHelper;

    private int mRecyclerViewOriginTop;
    private int mRecyclerViewOriginBottom;

    public ContentLayout(Context context) {
        super(context);
        init(context);
    }

    public ContentLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ContentLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_content_layout, this);

        mProgressView = (ProgressView) findViewById(R.id.progress);
        mTipView = (TextView) findViewById(R.id.tip);
        mContentView = (ViewGroup) findViewById(R.id.content_view);

        mRefreshLayout = (RefreshLayout) mContentView.findViewById(R.id.refresh_layout);
        mFastScroller = (FastScroller) mContentView.findViewById(R.id.fast_scroller);
        mRecyclerView = (EasyRecyclerView) mRefreshLayout.findViewById(R.id.recycler_view);

        mFastScroller.attachToRecyclerView(mRecyclerView);
        HandlerDrawable drawable = new HandlerDrawable();
        drawable.setColor(AttrResources.getAttrColor(context, R.attr.widgetColorThemeAccent));
        mFastScroller.setHandlerDrawable(drawable);

        mRefreshLayout.setHeaderColorSchemeResources(
                R.color.loading_indicator_red,
                R.color.loading_indicator_purple,
                R.color.loading_indicator_blue,
                R.color.loading_indicator_cyan,
                R.color.loading_indicator_green,
                R.color.loading_indicator_yellow);
        mRefreshLayout.setFooterColorSchemeResources(
                R.color.loading_indicator_red,
                R.color.loading_indicator_blue,
                R.color.loading_indicator_green,
                R.color.loading_indicator_orange);

        mRecyclerViewOriginTop = mRecyclerView.getPaddingTop();
        mRecyclerViewOriginBottom = mRecyclerView.getPaddingBottom();
    }

    @NonNull
    public EasyRecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    public FastScroller getFastScroller() {
        return mFastScroller;
    }

    public RefreshLayout getRefreshLayout() {
        return mRefreshLayout;
    }

    public void setHelper(ContentHelper helper) {
        mContentHelper = helper;
        helper.init(this);
    }

    public void showFastScroll() {
        if (!mFastScroller.isAttached()) {
            mFastScroller.attachToRecyclerView(mRecyclerView);
        }
    }

    public void hideFastScroll() {
        mFastScroller.detachedFromRecyclerView();
    }

    public void setFitPaddingTop(int fitPaddingTop) {
        // RecyclerView
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), mRecyclerViewOriginTop + fitPaddingTop, mRecyclerView.getPaddingRight(), mRecyclerView.getPaddingBottom());
        // RefreshLayout
        mRefreshLayout.setProgressViewOffset(false, fitPaddingTop, fitPaddingTop + LayoutUtils.dp2pix(getContext(), 32)); // TODO
    }

    public void setFitPaddingBottom(int fitPaddingBottom) {
        // RecyclerView
        mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(),
                mRecyclerView.getPaddingTop(), mRecyclerView.getPaddingRight(),
                mRecyclerViewOriginBottom + fitPaddingBottom);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        return mContentHelper.saveInstanceState(super.onSaveInstanceState());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(mContentHelper.restoreInstanceState(state));
    }

    public abstract static class ContentHelper<E extends Parcelable> implements ViewTransition.OnShowViewListener {

        private static final String TAG = ContentHelper.class.getSimpleName();

        private static final int CHECK_DUPLICATE_RANGE = 50;

        private static final String KEY_SUPER = "super";
        private static final String KEY_SHOWN_VIEW = "shown_view";
        private static final String KEY_TIP = "tip";
        private static final String KEY_DATA = "data";
        private static final String KEY_NEXT_ID = "next_id";
        private static final String KEY_PAGE_DIVIDER = "page_divider";
        private static final String KEY_START_PAGE = "start_page";
        private static final String KEY_END_PAGE = "end_page";
        private static final String KEY_PAGES = "pages";

        public static final int TYPE_REFRESH = 0;
        public static final int TYPE_PRE_PAGE = 1;
        public static final int TYPE_PRE_PAGE_KEEP_POS = 2;
        public static final int TYPE_NEXT_PAGE = 3;
        public static final int TYPE_NEXT_PAGE_KEEP_POS = 4;
        public static final int TYPE_SOMEWHERE = 5;
        public static final int TYPE_REFRESH_PAGE = 6;

        public static final int REFRESH_TYPE_HEADER = 0;
        public static final int REFRESH_TYPE_FOOTER = 1;
        public static final int REFRESH_TYPE_PROGRESS_VIEW = 2;

        private ProgressView mProgressView;
        private TextView mTipView;
        private ViewGroup mContentView;

        private RefreshLayout mRefreshLayout;
        private EasyRecyclerView mRecyclerView;

        private ViewTransition mViewTransition;

        /**
         * Store data
         */
        private ArrayList<E> mData = new ArrayList<>();

        /**
         * Generate task id
         */
        private final IntIdGenerator mIdGenerator = new IntIdGenerator();

        /**
         * Store the page divider index
         *
         * For example, the data contain page 3, page 4, page 5,
         * page 3 size is 7, page 4 size is 8, page 5 size is 9,
         * so <code>mPageDivider</code> contain 7, 15, 24.
         */
        private IntList mPageDivider = new IntList();

        /**
         * The first page in <code>mData</code>
         */
        private int mStartPage;

        /**
         * The last page + 1 in <code>mData</code>
         */
        private int mEndPage;

        /**
         * The available page count.
         */
        private int mPages;

        private int mNextPage;

        private int mCurrentTaskId;
        private int mCurrentTaskType;
        private int mCurrentTaskPage;

        private int mNextPageScrollSize;

        private String mEmptyString = "No hint";

        private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!mRefreshLayout.isRefreshing() && mRefreshLayout.isAlmostBottom() && mEndPage < mPages) {
                    // Get next page
                    mRefreshLayout.setFooterRefreshing(true);
                    mOnRefreshListener.onFooterRefresh();
                }
            }
        };

        private final RefreshLayout.OnRefreshListener mOnRefreshListener = new RefreshLayout.OnRefreshListener() {
            @Override
            public void onHeaderRefresh() {
                if (mStartPage > 0) {
                    mCurrentTaskId = mIdGenerator.nextId();
                    mCurrentTaskType = TYPE_PRE_PAGE_KEEP_POS;
                    mCurrentTaskPage = mStartPage - 1;
                    getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                } else {
                    doRefresh();
                }
            }

            @Override
            public void onFooterRefresh() {
                if (mEndPage < mPages) {
                    // Get next page
                    // Fill pages before NextPage with empty list
                    while (mNextPage > mEndPage && mEndPage < mPages) {
                        mCurrentTaskId = mIdGenerator.nextId();
                        mCurrentTaskType = TYPE_NEXT_PAGE_KEEP_POS;
                        mCurrentTaskPage = mEndPage;
                        onGetPageData(mCurrentTaskId, mPages, mNextPage, Collections.emptyList());
                    }
                    mCurrentTaskId = mIdGenerator.nextId();
                    mCurrentTaskType = TYPE_NEXT_PAGE_KEEP_POS;
                    mCurrentTaskPage = mEndPage;
                    getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                } else if (mEndPage == mPages) {
                    // Refresh last page
                    mCurrentTaskId = mIdGenerator.nextId();
                    mCurrentTaskType = TYPE_REFRESH_PAGE;
                    mCurrentTaskPage = mEndPage - 1;
                    getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                } else {
                    Log.e(TAG, "Try to footer refresh, but mEndPage = " + mEndPage + ", mPages = " + mPages);
                    mRefreshLayout.setFooterRefreshing(false);
                }
            }
        };

        private final LayoutManagerUtils.OnScrollToPositionListener mOnScrollToPositionListener =
                new LayoutManagerUtils.OnScrollToPositionListener() {
                    @Override
                    public void onScrollToPosition(int position) {
                        ContentHelper.this.onScrollToPosition(position);
                    }
                };

        private void init(ContentLayout contentLayout) {
            mNextPageScrollSize = LayoutUtils.dp2pix(contentLayout.getContext(), 48);

            mProgressView = contentLayout.mProgressView;
            mTipView = contentLayout.mTipView;
            mContentView = contentLayout.mContentView;

            mRefreshLayout = contentLayout.mRefreshLayout;
            mRecyclerView = contentLayout.mRecyclerView;

            Drawable drawable = DrawableManager.getVectorDrawable(getContext(), R.drawable.big_sad_pandroid);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            mTipView.setCompoundDrawables(null, drawable, null, null);

            mViewTransition = new ViewTransition(mContentView, mProgressView, mTipView);
            mViewTransition.setOnShowViewListener(this);

            mRecyclerView.addOnScrollListener(mOnScrollListener);
            mRefreshLayout.setOnRefreshListener(mOnRefreshListener);

            mTipView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    refresh();
                }
            });
        }

        /**
         * Call {@link #onGetPageData(int, int, int, List)} when get data
         *
         * @param taskId task id
         * @param page the page to get
         */
        protected abstract void getPageData(int taskId, int type, int page);

        protected abstract Context getContext();

        protected abstract void notifyDataSetChanged();

        protected abstract void notifyItemRangeRemoved(int positionStart, int itemCount);

        protected abstract void notifyItemRangeInserted(int positionStart, int itemCount);

        protected void onScrollToPosition(int postion) {}

        @Override
        public void onShowView(View hiddenView, View shownView) {}

        public int getShownViewIndex() {
            return mViewTransition.getShownViewIndex();
        }

        public void setRefreshLayoutEnable(boolean enable) {
            mRefreshLayout.setEnabled(enable);
        }

        public void setEnable(boolean enable) {
            mRefreshLayout.setEnabled(enable);
        }

        public void setEmptyString(String str) {
            mEmptyString = str;
        }

        public List<E> getData() {
            return mData;
        }

        /**
         * @throws IndexOutOfBoundsException
         *                if {@code location < 0 || location >= size()}
         */
        public E getDataAt(int location) {
            return mData.get(location);
        }

        @Nullable
        public E getDataAtEx(int location) {
            if (location >= 0 && location < mData.size()) {
                return mData.get(location);
            } else {
                return null;
            }
        }

        public int size() {
            return mData.size();
        }

        public boolean isCurrentTask(int taskId) {
            return mCurrentTaskId == taskId;
        }

        public int getPages() {
            return mPages;
        }

        public void addAt(int index, E data) {
            mData.add(index, data);
            onAddData(data);

            for (int i = 0, n = mPageDivider.size(); i < n; i++) {
                int divider = mPageDivider.get(i);
                if (index < divider) {
                    mPageDivider.set(i, divider + 1);
                }
            }

            notifyItemRangeInserted(index, 1);
        }

        public void removeAt(int index) {
            E data = mData.remove(index);
            onRemoveData(data);

            for (int i = 0, n = mPageDivider.size(); i < n; i++) {
                int divider = mPageDivider.get(i);
                if (index < divider) {
                    mPageDivider.set(i, divider - 1);
                }
            }

            notifyItemRangeRemoved(index, 1);
        }

        protected abstract boolean isDuplicate(E d1, E d2);

        private void removeDuplicateData(List<E> data, int start, int end) {
            start = Math.max(0, start);
            end = Math.min(mData.size(), end);
            for (Iterator<E> iterator = data.iterator(); iterator.hasNext();) {
                E d = iterator.next();
                for (int i = start; i < end; i++) {
                    if (isDuplicate(d, mData.get(i))) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        protected void onAddData(E data) { }

        protected void onAddData(List<E> data) { }

        protected void onRemoveData(E data) { }

        protected void onRemoveData(List<E> data) { }

        protected void onClearData() { }

        public void onGetPageData(int taskId, int pages, int nextPage, List<E> data) {
            if (mCurrentTaskId == taskId) {
                int dataSize;

                switch (mCurrentTaskType) {
                    case TYPE_REFRESH:
                        mStartPage = 0;
                        mEndPage = 1;
                        mPages = pages;
                        mNextPage = nextPage;
                        mPageDivider.clear();
                        mPageDivider.add(data.size());

                        if (data.isEmpty()) {
                            mData.clear();
                            onClearData();
                            notifyDataSetChanged();

                            if (true || mEndPage >= mPages) { // Not found
                                // Ui change, show empty string
                                mRefreshLayout.setHeaderRefreshing(false);
                                mRefreshLayout.setFooterRefreshing(false);
                                showEmptyString();
                            } else {
                                // Ui change, show progress bar
                                mRefreshLayout.setHeaderRefreshing(false);
                                mRefreshLayout.setFooterRefreshing(false);
                                showProgressBar();

                                // Get next page
                                mCurrentTaskId = mIdGenerator.nextId();
                                mCurrentTaskType = TYPE_NEXT_PAGE_KEEP_POS;
                                mCurrentTaskPage = mEndPage;
                                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                            }
                        } else {
                            mData.clear();
                            onClearData();
                            mData.addAll(data);
                            onAddData(data);
                            notifyDataSetChanged();

                            // Ui change, show content
                            mRefreshLayout.setHeaderRefreshing(false);
                            mRefreshLayout.setFooterRefreshing(false);
                            showContent();

                            // RecyclerView scroll
                            if (mRecyclerView.isAttachedToWindow()) {
                                mRecyclerView.stopScroll();
                                LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), 0, 0);
                                onScrollToPosition(0);
                            }
                        }
                        break;
                    case TYPE_PRE_PAGE:
                    case TYPE_PRE_PAGE_KEEP_POS:
                        removeDuplicateData(data, 0, CHECK_DUPLICATE_RANGE);
                        dataSize = data.size();
                        for (int i = 0, n = mPageDivider.size(); i < n; i++) {
                            mPageDivider.set(i, mPageDivider.get(i) + dataSize);
                        }
                        mPageDivider.add(0, dataSize);
                        mStartPage--;
                        mPages = Math.max(mEndPage, pages);
                        // assert mStartPage >= 0

                        if (data.isEmpty()) {
                            if (true || mStartPage <= 0) { // OK, that's all
                                if (mData.isEmpty()) {
                                    // Ui change, show empty string
                                    mRefreshLayout.setHeaderRefreshing(false);
                                    mRefreshLayout.setFooterRefreshing(false);
                                    showEmptyString();
                                } else {
                                    // Ui change, show content
                                    mRefreshLayout.setHeaderRefreshing(false);
                                    mRefreshLayout.setFooterRefreshing(false);
                                    showContent();

                                    if (mCurrentTaskType == TYPE_PRE_PAGE && mRecyclerView.isAttachedToWindow()) {
                                        // RecyclerView scroll, to top
                                        mRecyclerView.stopScroll();
                                        LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), 0, 0);
                                        onScrollToPosition(0);
                                    }
                                }
                            } else {
                                // Keep UI

                                // Get previous
                                mCurrentTaskId = mIdGenerator.nextId();
                                // Keep mCurrentTaskType
                                mCurrentTaskPage = mStartPage - 1;
                                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                            }
                        } else {
                            mData.addAll(0, data);
                            onAddData(data);
                            notifyItemRangeInserted(0, data.size());

                            // Ui change, show content
                            mRefreshLayout.setHeaderRefreshing(false);
                            mRefreshLayout.setFooterRefreshing(false);
                            showContent();

                            if (mRecyclerView.isAttachedToWindow()) {
                                // RecyclerView scroll
                                if (mCurrentTaskType == TYPE_PRE_PAGE_KEEP_POS) {
                                    mRecyclerView.stopScroll();
                                    LayoutManagerUtils.scrollToPositionProperly(mRecyclerView.getLayoutManager(), getContext(),
                                            dataSize - 1, mOnScrollToPositionListener);
                                } else {
                                    mRecyclerView.stopScroll();
                                    LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), 0, 0);
                                    onScrollToPosition(0);
                                }
                            }
                        }
                        break;
                    case TYPE_NEXT_PAGE:
                    case TYPE_NEXT_PAGE_KEEP_POS:
                        removeDuplicateData(data, mData.size() - CHECK_DUPLICATE_RANGE, mData.size());
                        dataSize = data.size();
                        int oldDataSize = mData.size();
                        mPageDivider.add(oldDataSize + dataSize);
                        mEndPage++;
                        mNextPage = nextPage;
                        mPages = Math.max(mEndPage, pages);

                        if (data.isEmpty()) {
                            if (true || mEndPage >= mPages) { // OK, that's all
                                if (mData.isEmpty()) {
                                    // Ui change, show empty string
                                    mRefreshLayout.setHeaderRefreshing(false);
                                    mRefreshLayout.setFooterRefreshing(false);
                                    showEmptyString();
                                } else {
                                    // Ui change, show content
                                    mRefreshLayout.setHeaderRefreshing(false);
                                    mRefreshLayout.setFooterRefreshing(false);
                                    showContent();

                                    if (mCurrentTaskType == TYPE_NEXT_PAGE && mRecyclerView.isAttachedToWindow()) {
                                        // RecyclerView scroll
                                        mRecyclerView.stopScroll();
                                        LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), oldDataSize, 0);
                                        onScrollToPosition(oldDataSize);
                                    }
                                }
                            } else {
                                // Keep UI

                                // Get next page
                                mCurrentTaskId = mIdGenerator.nextId();
                                // Keep mCurrentTaskType
                                mCurrentTaskPage = mEndPage;
                                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                            }
                        } else {
                            mData.addAll(data);
                            onAddData(data);
                            notifyItemRangeInserted(oldDataSize, dataSize);

                            // Ui change, show content
                            mRefreshLayout.setHeaderRefreshing(false);
                            mRefreshLayout.setFooterRefreshing(false);
                            showContent();

                            if (mRecyclerView.isAttachedToWindow()) {
                                if (mCurrentTaskType == TYPE_NEXT_PAGE_KEEP_POS) {
                                    mRecyclerView.stopScroll();
                                    mRecyclerView.smoothScrollBy(0, mNextPageScrollSize);
                                } else {
                                    mRecyclerView.stopScroll();
                                    LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), oldDataSize, 0);
                                    onScrollToPosition(oldDataSize);
                                }
                            }
                        }
                        break;
                    case TYPE_SOMEWHERE:
                        mStartPage = mCurrentTaskPage;
                        mEndPage = mCurrentTaskPage + 1;
                        mNextPage = nextPage;
                        mPages = pages;
                        mPageDivider.clear();
                        mPageDivider.add(data.size());

                        if (data.isEmpty()) {
                            mData.clear();
                            onClearData();
                            notifyDataSetChanged();

                            if (true || mEndPage >= mPages) { // Not found
                                // Ui change, show empty string
                                mRefreshLayout.setHeaderRefreshing(false);
                                mRefreshLayout.setFooterRefreshing(false);
                                showEmptyString();
                            } else {
                                // Ui change, show progress bar
                                mRefreshLayout.setHeaderRefreshing(false);
                                mRefreshLayout.setFooterRefreshing(false);
                                showProgressBar();

                                // Get next page
                                mCurrentTaskId = mIdGenerator.nextId();
                                mCurrentTaskType = TYPE_NEXT_PAGE_KEEP_POS;
                                mCurrentTaskPage = mEndPage;
                                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
                            }
                        } else {
                            mData.clear();
                            onClearData();
                            mData.addAll(data);
                            onAddData(data);
                            notifyDataSetChanged();

                            // Ui change, show content
                            mRefreshLayout.setHeaderRefreshing(false);
                            mRefreshLayout.setFooterRefreshing(false);
                            showContent();

                            if (mRecyclerView.isAttachedToWindow()) {
                                // RecyclerView scroll
                                mRecyclerView.stopScroll();
                                LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), 0, 0);
                                onScrollToPosition(0);
                            }
                        }
                        break;
                    case TYPE_REFRESH_PAGE:
                        if (mCurrentTaskPage < mStartPage || mCurrentTaskPage >= mEndPage) {
                            Log.e(TAG, "TYPE_REFRESH_PAGE, but mCurrentTaskPage = " + mCurrentTaskPage +
                                    ", mStartPage = " + mStartPage + ", mEndPage = " + mEndPage);
                            break;
                        }

                        if (mCurrentTaskPage == mEndPage - 1) {
                            mNextPage = nextPage;
                        }

                        mPages = Math.max(mEndPage, pages);

                        int oldIndexStart = mCurrentTaskPage == mStartPage ? 0 : mPageDivider.get(mCurrentTaskPage - mStartPage - 1);
                        int oldIndexEnd = mPageDivider.get(mCurrentTaskPage - mStartPage);
                        List<E> toRemove = mData.subList(oldIndexStart, oldIndexEnd);
                        onRemoveData(toRemove);
                        toRemove.clear();
                        removeDuplicateData(data, oldIndexStart - CHECK_DUPLICATE_RANGE, oldIndexStart + CHECK_DUPLICATE_RANGE);
                        int newIndexStart = oldIndexStart;
                        int newIndexEnd = newIndexStart + data.size();
                        mData.addAll(oldIndexStart, data);
                        onAddData(data);
                        notifyDataSetChanged();

                        for (int i = mCurrentTaskPage - mStartPage, n = mPageDivider.size(); i < n; i++) {
                            mPageDivider.set(i, mPageDivider.get(i) - oldIndexEnd + newIndexEnd);
                        }

                        if (mData.isEmpty()) {
                            // Ui change, show empty string
                            mRefreshLayout.setHeaderRefreshing(false);
                            mRefreshLayout.setFooterRefreshing(false);
                            showEmptyString();
                        } else {
                            // Ui change, show content
                            mRefreshLayout.setHeaderRefreshing(false);
                            mRefreshLayout.setFooterRefreshing(false);
                            showContent();

                            // RecyclerView scroll
                            if (newIndexEnd > oldIndexEnd && newIndexEnd > 0 && mRecyclerView.isAttachedToWindow()) {
                                mRecyclerView.stopScroll();
                                LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), newIndexEnd - 1, 0);
                                onScrollToPosition(newIndexEnd - 1);
                            }
                        }
                        break;
                }
            }
        }

        public void onGetException(int taskId, Exception e) {
            if (mCurrentTaskId == taskId) {
                mRefreshLayout.setHeaderRefreshing(false);
                mRefreshLayout.setFooterRefreshing(false);

                String readableError;
                if (e != null) {
                    e.printStackTrace();
                    readableError = ExceptionUtils.getReadableString(e);
                } else {
                    readableError = getContext().getString(R.string.error_unknown);
                }

                if (mViewTransition.getShownViewIndex() == 0) {
                    Toast.makeText(getContext(), readableError, Toast.LENGTH_SHORT).show();
                } else {
                    showText(readableError);
                }
            }
        }

        public void showContent() {
            mViewTransition.showView(0);
        }

        private boolean isContentShowing() {
            return mViewTransition.getShownViewIndex() == 0;
        }

        public void showProgressBar() {
            showProgressBar(true);
        }

        public void showProgressBar(boolean animation) {
            mViewTransition.showView(1, animation);
        }

        public void showText(CharSequence text) {
            mTipView.setText(text);
            mViewTransition.showView(2);
        }

        public void showEmptyString() {
            showText(mEmptyString);
        }

        /**
         * Be carefull
         */
        public void doGetData(int type, int page, int refreshType) {
            switch (refreshType) {
                default:
                case REFRESH_TYPE_HEADER:
                    showContent();
                    mRefreshLayout.setFooterRefreshing(false);
                    mRefreshLayout.setHeaderRefreshing(true);
                    break;
                case REFRESH_TYPE_FOOTER:
                    showContent();
                    mRefreshLayout.setHeaderRefreshing(false);
                    mRefreshLayout.setFooterRefreshing(true);
                    break;
                case REFRESH_TYPE_PROGRESS_VIEW:
                    showProgressBar();
                    mRefreshLayout.setHeaderRefreshing(false);
                    mRefreshLayout.setFooterRefreshing(false);
                    break;
            }

            mCurrentTaskId = mIdGenerator.nextId();
            mCurrentTaskType = type;
            mCurrentTaskPage = page;
            getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
        }

        private void doRefresh() {
            mCurrentTaskId = mIdGenerator.nextId();
            mCurrentTaskType = TYPE_REFRESH;
            mCurrentTaskPage = 0;
            getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
        }

        /**
         * Lisk {@link #refresh()}, but no animation when show progress bar
         */
        public void firstRefresh() {
            showProgressBar(false);
            doRefresh();
        }

        /**
         * Show progress bar first, than do refresh
         */
        public void refresh() {
            showProgressBar();
            doRefresh();
        }

        private void cancelCurrentTask() {
            mCurrentTaskId = mIdGenerator.nextId();
            mRefreshLayout.setHeaderRefreshing(false);
            mRefreshLayout.setFooterRefreshing(false);
        }

        private int getPageStart(int page) {
            if (mStartPage == page) {
                return 0;
            } else {
                return mPageDivider.get(page - mStartPage - 1);
            }
        }

        private int getPageEnd(int page) {
            return mPageDivider.get(page - mStartPage);
        }

        private int getPageForPosition(int position) {
            if (position < 0) {
                return -1;
            }

            IntList pageDivider = mPageDivider;
            for (int i = 0, n = pageDivider.size(); i < n; i++) {
                if (position < pageDivider.get(i)) {
                    return i + mStartPage;
                }
            }

            return -1;
        }

        public int getPageForTop() {
            return getPageForPosition(LayoutManagerUtils.getFirstVisibleItemPosition(mRecyclerView.getLayoutManager()));
        }

        public int getPageForBottom() {
            return getPageForPosition(LayoutManagerUtils.getLastVisibleItemPosition(mRecyclerView.getLayoutManager()));
        }

        public boolean canGoTo() {
            return isContentShowing();
        }

        /**
         * Check range first!
         *
         * @param page the target page
         * @throws IndexOutOfBoundsException
         */
        public void goTo(int page) throws IndexOutOfBoundsException {
            if (page < 0 || page >= mPages) {
                throw new IndexOutOfBoundsException("Page count is " + mPages + ", page is " + page);
            } else if (page >= mStartPage && page < mEndPage) {
                cancelCurrentTask();

                int position = getPageStart(page);
                mRecyclerView.stopScroll();
                LayoutManagerUtils.scrollToPositionWithOffset(mRecyclerView.getLayoutManager(), position, 0);
                onScrollToPosition(position);
            } else if (page == mStartPage - 1) {
                mRefreshLayout.setFooterRefreshing(false);
                mRefreshLayout.setHeaderRefreshing(true);

                mCurrentTaskId = mIdGenerator.nextId();
                mCurrentTaskType = TYPE_PRE_PAGE;
                mCurrentTaskPage = page;
                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
            } else if (page == mEndPage) {
                mRefreshLayout.setHeaderRefreshing(false);
                mRefreshLayout.setFooterRefreshing(true);

                mCurrentTaskId = mIdGenerator.nextId();
                mCurrentTaskType = TYPE_NEXT_PAGE;
                mCurrentTaskPage = page;
                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
            } else {
                mRefreshLayout.setFooterRefreshing(false);
                mRefreshLayout.setHeaderRefreshing(true);

                mCurrentTaskId = mIdGenerator.nextId();
                mCurrentTaskType = TYPE_SOMEWHERE;
                mCurrentTaskPage = page;
                getPageData(mCurrentTaskId, mCurrentTaskType, mCurrentTaskPage);
            }
        }

        private int mSavedDataId = IntIdGenerator.INVALID_ID;

        protected Parcelable saveInstanceState(Parcelable superState) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(KEY_SUPER, superState);
            int shownView = mViewTransition.getShownViewIndex();
            bundle.putInt(KEY_SHOWN_VIEW, shownView);
            bundle.putString(KEY_TIP, mTipView.getText().toString());

            // TODO It's a bad design
            EhApplication app = (EhApplication) getContext().getApplicationContext();
            if (mSavedDataId != IntIdGenerator.INVALID_ID) {
                app.removeGlobalStuff(mSavedDataId);
                mSavedDataId = IntIdGenerator.INVALID_ID;
            }
            mSavedDataId = app.putGlobalStuff(mData);
            bundle.putInt(KEY_DATA, mSavedDataId);

            bundle.putInt(KEY_NEXT_ID, mIdGenerator.nextId());
            bundle.putParcelable(KEY_PAGE_DIVIDER, mPageDivider);
            bundle.putInt(KEY_START_PAGE, mStartPage);
            bundle.putInt(KEY_END_PAGE, mEndPage);
            bundle.putInt(KEY_PAGES, mPages);
            return bundle;
        }

        protected Parcelable restoreInstanceState(Parcelable state) {
            if (state instanceof Bundle) {
                Bundle bundle = (Bundle) state;
                mViewTransition.showView(bundle.getInt(KEY_SHOWN_VIEW), false);
                mTipView.setText(bundle.getString(KEY_TIP));

                mSavedDataId = bundle.getInt(KEY_DATA);
                ArrayList<E> newData = null;
                EhApplication app = (EhApplication) getContext().getApplicationContext();
                if (mSavedDataId != IntIdGenerator.INVALID_ID) {
                    newData = (ArrayList<E>) app.removeGlobalStuff(mSavedDataId);
                    mSavedDataId = IntIdGenerator.INVALID_ID;
                    if (newData != null) {
                        mData = newData;
                    }
                }

                mIdGenerator.setNextId(bundle.getInt(KEY_NEXT_ID));
                mPageDivider = bundle.getParcelable(KEY_PAGE_DIVIDER);
                mStartPage = bundle.getInt(KEY_START_PAGE);
                mEndPage = bundle.getInt(KEY_END_PAGE);
                mPages = bundle.getInt(KEY_PAGES);

                notifyDataSetChanged();

                if (newData == null) {
                    mPageDivider.clear();
                    mStartPage = 0;
                    mEndPage = 0;
                    mPages = 0;
                    firstRefresh();
                }

                return bundle.getParcelable(KEY_SUPER);
            } else {
                return state;
            }
        }
    }
}
