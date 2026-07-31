/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.ehviewer.ui.scene.gallery.list;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.parser.GalleryListParser;
import com.hippo.ehviewer.dao.QuickSearch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lazily merges several chronologically ordered bookmark searches. Each source keeps its own
 * pagination cursor. A source is advanced only when its unseen upper bound could affect the next
 * item in the merged stream.
 */
final class BookmarkSubscriptionCoordinator {

    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final int MAX_CONCURRENT_REQUESTS = 3;

    interface Listener {
        void onBookmarkSubscriptionBatch(int taskId, List<GalleryInfo> data,
                                         boolean hasMore, boolean refresh,
                                         boolean noSubscriptions, boolean partialFailure);

        void onBookmarkSubscriptionFailure(int taskId, Exception error);
    }

    private static final class Source {
        final int id;
        final ListUrlBuilder builder = new ListUrlBuilder();
        final ArrayList<GalleryInfo> buffer = new ArrayList<>();

        int bufferIndex;
        int pageIndex;
        int pages;
        int loadedPages;
        @Nullable String nextHref;
        @Nullable String boundaryPosted;
        long boundaryGid;
        boolean hasBoundary;
        boolean exhausted;
        boolean queued;
        boolean loading;
        @Nullable EhRequest request;

        Source(int id, QuickSearch quickSearch) {
            this.id = id;
            builder.set(quickSearch);
            builder.setPageIndex(0);
        }

        boolean hasVisibleItem() {
            return bufferIndex < buffer.size();
        }

        @Nullable GalleryInfo peek() {
            return hasVisibleItem() ? buffer.get(bufferIndex) : null;
        }

        @Nullable GalleryInfo take() {
            return hasVisibleItem() ? buffer.get(bufferIndex++) : null;
        }
    }

    private record PendingRequest(Source source, boolean initial) {
    }

    private final EhClient mClient;
    private final Listener mListener;
    private final ArrayList<Source> mSources = new ArrayList<>();
    private final ArrayDeque<PendingRequest> mRequestQueue = new ArrayDeque<>();
    private final Set<Long> mEmittedGids = new HashSet<>();
    private final ArrayList<GalleryInfo> mPendingBatch = new ArrayList<>();

    private int mGeneration;
    private int mTaskId;
    private int mActiveRequests;
    private int mInitialRequestsRemaining;
    private int mObservedBatchSize;
    private int mBatchSize = DEFAULT_BATCH_SIZE;
    private boolean mRefresh;
    private boolean mLoading;
    private boolean mHadFailure;
    @Nullable private Exception mFirstFailure;
    private String mSubscriptionFingerprint = "";

    BookmarkSubscriptionCoordinator(EhClient client, Listener listener) {
        mClient = client;
        mListener = listener;
    }

    static boolean isSupported(QuickSearch quickSearch) {
        if (quickSearch == null) {
            return false;
        }
        switch (quickSearch.mode) {
            case ListUrlBuilder.MODE_NORMAL:
            case ListUrlBuilder.MODE_UPLOADER:
            case ListUrlBuilder.MODE_TAG:
            case ListUrlBuilder.MODE_FILTER:
                return true;
            default:
                return false;
        }
    }

    static String getFingerprint(List<QuickSearch> quickSearches) {
        StringBuilder builder = new StringBuilder();
        for (QuickSearch quickSearch : quickSearches) {
            if (!quickSearch.subscribed || !isSupported(quickSearch)) {
                continue;
            }
            builder.append(quickSearch.id).append('|')
                    .append(quickSearch.mode).append('|')
                    .append(quickSearch.category).append('|')
                    .append(quickSearch.keyword).append('|')
                    .append(quickSearch.advanceSearch).append('|')
                    .append(quickSearch.minRating).append('|')
                    .append(quickSearch.pageFrom).append('|')
                    .append(quickSearch.pageTo).append(';');
        }
        return builder.toString();
    }

    boolean matchesSubscriptions(List<QuickSearch> quickSearches) {
        return mSubscriptionFingerprint.equals(getFingerprint(quickSearches));
    }

    boolean isLoading() {
        return mLoading;
    }

    void refresh(int taskId, List<QuickSearch> quickSearches) {
        cancel();
        mTaskId = taskId;
        mRefresh = true;
        mLoading = true;
        mHadFailure = false;
        mFirstFailure = null;
        mObservedBatchSize = 0;
        mPendingBatch.clear();
        mEmittedGids.clear();
        mSubscriptionFingerprint = getFingerprint(quickSearches);

        for (QuickSearch quickSearch : quickSearches) {
            if (!quickSearch.subscribed || !isSupported(quickSearch)
                    || containsEquivalentSource(quickSearch)) {
                continue;
            }
            mSources.add(new Source(mSources.size(), quickSearch));
        }

        if (mSources.isEmpty()) {
            mLoading = false;
            mListener.onBookmarkSubscriptionBatch(taskId, Collections.emptyList(),
                    false, true, true, false);
            return;
        }

        mInitialRequestsRemaining = mSources.size();
        for (Source source : mSources) {
            enqueue(source, true);
        }
        pumpRequests();
    }

    void loadMore(int taskId) {
        if (mLoading) {
            return;
        }
        mTaskId = taskId;
        mRefresh = false;
        mLoading = true;
        mHadFailure = false;
        mFirstFailure = null;
        mPendingBatch.clear();
        continueProducing();
    }

    void cancel() {
        mGeneration++;
        mLoading = false;
        mRefresh = false;
        mRequestQueue.clear();
        mActiveRequests = 0;
        mInitialRequestsRemaining = 0;
        for (Source source : mSources) {
            source.queued = false;
            source.loading = false;
            if (source.request != null) {
                EhRequest request = source.request;
                source.request = null;
                request.cancel();
            }
        }
        mSources.clear();
        mPendingBatch.clear();
        mEmittedGids.clear();
    }

    private boolean containsEquivalentSource(QuickSearch quickSearch) {
        for (Source source : mSources) {
            if (source.builder.equalsQuickSearch(quickSearch)) {
                return true;
            }
        }
        return false;
    }

    private void enqueue(Source source, boolean initial) {
        if (source.exhausted || source.queued || source.loading) {
            return;
        }
        source.queued = true;
        mRequestQueue.addLast(new PendingRequest(source, initial));
    }

    private void pumpRequests() {
        while (mActiveRequests < MAX_CONCURRENT_REQUESTS && !mRequestQueue.isEmpty()) {
            PendingRequest pending = mRequestQueue.removeFirst();
            Source source = pending.source();
            source.queued = false;
            source.loading = true;

            String url;
            if (pending.initial()) {
                source.pageIndex = 0;
                source.builder.setPageIndex(0);
                url = source.builder.build();
            } else if (!TextUtils.isEmpty(source.nextHref)) {
                url = source.nextHref;
            } else {
                source.pageIndex++;
                source.builder.setPageIndex(source.pageIndex);
                url = source.builder.build();
            }

            if (TextUtils.isEmpty(url)) {
                onPageFailure(mGeneration, source.id, pending.initial(),
                        new IllegalStateException("Bookmark subscription URL is empty"));
                continue;
            }

            int generation = mGeneration;
            EhRequest request = new EhRequest()
                    .setMethod(EhClient.METHOD_GET_GALLERY_LIST)
                    .setArgs(url, source.builder.getMode())
                    .setCallback(new EhClient.Callback<GalleryListParser.Result>() {
                        @Override
                        public void onSuccess(GalleryListParser.Result result) {
                            onPageSuccess(generation, source.id, pending.initial(), result);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            onPageFailure(generation, source.id, pending.initial(), e);
                        }

                        @Override
                        public void onCancel() {
                            // Explicit cancellation increments the generation first.
                        }
                    });
            source.request = request;
            mActiveRequests++;
            mClient.execute(request);
        }
    }

    private void onPageSuccess(int generation, int sourceId, boolean initial,
                               GalleryListParser.Result result) {
        if (generation != mGeneration || sourceId < 0 || sourceId >= mSources.size()) {
            return;
        }
        Source source = mSources.get(sourceId);
        finishRequest(source, initial);
        source.loadedPages++;

        if (initial) {
            mObservedBatchSize = Math.max(mObservedBatchSize, result.rawResultCount);
        }
        source.buffer.clear();
        source.bufferIndex = 0;
        source.buffer.addAll(result.galleryInfoList);
        source.buffer.sort((first, second) -> -compareGalleryOrder(first, second));

        if (result.rawResultCount > 0) {
            source.boundaryPosted = result.rawTailPosted;
            source.boundaryGid = result.rawTailGid;
            source.hasBoundary = true;
        }

        source.nextHref = result.nextHref;
        source.pages = result.pages;
        boolean hasHref = !TextUtils.isEmpty(result.nextHref);
        boolean hasIndexedPage = result.pages > 0 && source.pageIndex + 1 < result.pages;
        source.exhausted = !hasHref && !hasIndexedPage;

        afterRequestFinished();
    }

    private void onPageFailure(int generation, int sourceId, boolean initial, Exception error) {
        if (generation != mGeneration || sourceId < 0 || sourceId >= mSources.size()) {
            return;
        }
        Source source = mSources.get(sourceId);
        finishRequest(source, initial);
        source.exhausted = true;
        mHadFailure = true;
        if (mFirstFailure == null) {
            mFirstFailure = error;
        }
        afterRequestFinished();
    }

    private void finishRequest(Source source, boolean initial) {
        source.loading = false;
        source.request = null;
        mActiveRequests = Math.max(0, mActiveRequests - 1);
        if (initial) {
            mInitialRequestsRemaining = Math.max(0, mInitialRequestsRemaining - 1);
        }
    }

    private void afterRequestFinished() {
        pumpRequests();
        if (mInitialRequestsRemaining == 0) {
            if (mRefresh) {
                mBatchSize = mObservedBatchSize > 0
                        ? mObservedBatchSize : DEFAULT_BATCH_SIZE;
            }
            continueProducing();
        }
    }

    private void continueProducing() {
        if (!mLoading || mInitialRequestsRemaining != 0) {
            return;
        }

        while (mPendingBatch.size() < mBatchSize) {
            Source visibleSource = findNewestVisibleSource();
            Source blockingSource = findBlockingUnknownSource(visibleSource);
            if (blockingSource != null) {
                if (!blockingSource.loading && !blockingSource.queued) {
                    enqueue(blockingSource, false);
                    pumpRequests();
                }
                return;
            }

            if (visibleSource == null) {
                finishBatch();
                return;
            }

            GalleryInfo galleryInfo = visibleSource.take();
            if (galleryInfo != null && mEmittedGids.add(galleryInfo.gid)) {
                mPendingBatch.add(galleryInfo);
            }
        }

        finishBatch();
    }

    @Nullable
    private Source findNewestVisibleSource() {
        Source best = null;
        for (Source source : mSources) {
            GalleryInfo candidate = source.peek();
            if (candidate != null && (best == null
                    || compareGalleryOrder(candidate, best.peek()) > 0)) {
                best = source;
            }
        }
        return best;
    }

    @Nullable
    private Source findBlockingUnknownSource(@Nullable Source visibleSource) {
        Source bestUnknown = null;
        for (Source source : mSources) {
            if (source.hasVisibleItem() || source.exhausted) {
                continue;
            }
            if (bestUnknown == null || compareUpperBounds(source, bestUnknown) > 0) {
                bestUnknown = source;
            }
        }
        if (bestUnknown == null || visibleSource == null || !bestUnknown.hasBoundary) {
            return bestUnknown;
        }

        GalleryInfo visible = visibleSource.peek();
        return visible != null && compareOrder(bestUnknown.boundaryPosted,
                bestUnknown.boundaryGid, visible.posted, visible.gid) > 0
                ? bestUnknown : null;
    }

    private static int compareUpperBounds(Source first, Source second) {
        if (!first.hasBoundary) {
            return second.hasBoundary ? 1 : 0;
        }
        if (!second.hasBoundary) {
            return -1;
        }
        return compareOrder(first.boundaryPosted, first.boundaryGid,
                second.boundaryPosted, second.boundaryGid);
    }

    private static int compareGalleryOrder(@Nullable GalleryInfo first,
                                           @Nullable GalleryInfo second) {
        if (first == null) {
            return second == null ? 0 : -1;
        }
        if (second == null) {
            return 1;
        }
        return compareOrder(first.posted, first.gid, second.posted, second.gid);
    }

    private static int compareOrder(@Nullable String firstPosted, long firstGid,
                                    @Nullable String secondPosted, long secondGid) {
        String first = firstPosted != null ? firstPosted : "";
        String second = secondPosted != null ? secondPosted : "";
        int dateComparison = first.compareTo(second);
        return dateComparison != 0 ? dateComparison : Long.compare(firstGid, secondGid);
    }

    private boolean hasMore() {
        for (Source source : mSources) {
            if (source.hasVisibleItem() || !source.exhausted) {
                return true;
            }
        }
        return false;
    }

    private void finishBatch() {
        boolean hasMore = hasMore();
        if (mPendingBatch.isEmpty() && mFirstFailure != null && !hasMore) {
            Exception error = mFirstFailure;
            mLoading = false;
            mListener.onBookmarkSubscriptionFailure(mTaskId, error);
            return;
        }

        ArrayList<GalleryInfo> data = new ArrayList<>(mPendingBatch);
        boolean refresh = mRefresh;
        boolean partialFailure = mHadFailure;
        mPendingBatch.clear();
        mLoading = false;
        mRefresh = false;
        mListener.onBookmarkSubscriptionBatch(mTaskId, data, hasMore,
                refresh, false, partialFailure);
    }
}
