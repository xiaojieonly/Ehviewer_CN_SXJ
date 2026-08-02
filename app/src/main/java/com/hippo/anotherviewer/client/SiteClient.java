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

package com.hippo.anotherviewer.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.hippo.anotherviewer.Analytics;
import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.client.data.userTag.TagPushParam;
import com.hippo.anotherviewer.client.data.userTag.UserTag;
import com.hippo.anotherviewer.client.exception.CancelledException;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.lib.yorozuya.SimpleHandler;

import java.io.File;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public class SiteClient {

    public static final String TAG = SiteClient.class.getSimpleName();

    public static final int METHOD_SIGN_IN = 0;
    public static final int METHOD_GET_GALLERY_LIST = 1;
    public static final int METHOD_GET_GALLERY_DETAIL = 3;
    public static final int METHOD_GET_PREVIEW_SET = 4;
    public static final int METHOD_GET_RATE_GALLERY = 5;
    public static final int METHOD_GET_COMMENT_GALLERY = 6;
    public static final int METHOD_GET_GALLERY_TOKEN = 7;
    public static final int METHOD_GET_FAVORITES = 8;
    public static final int METHOD_ADD_FAVORITES = 9;
    public static final int METHOD_ADD_FAVORITES_RANGE = 10;
    public static final int METHOD_MODIFY_FAVORITES = 11;
    public static final int METHOD_GET_TORRENT_LIST = 12;
    public static final int METHOD_GET_TOP_LIST = 13;
    public static final int METHOD_GET_PROFILE = 14;
    public static final int METHOD_VOTE_COMMENT = 15;
    public static final int METHOD_IMAGE_SEARCH = 16;
    public static final int METHOD_ARCHIVE_LIST = 17;
    public static final int METHOD_ARCHIVER = 27;
    public static final int METHOD_DOWNLOAD_ARCHIVE = 18;
    public static final int METHOD_DOWNLOAD_ARCHIVER = 28;
    public static final int METHOD_ADD_TAG = 20;
    public static final int METHOD_EDIT_WATCHED = 21;
    public static final int METHOD_DELETE_WATCHED = 22;
    public static final int METHOD_GET_WATCHED = 23;
    public static final int METHOD_GET_NEWS = 24;
    public static final int METHOD_GET_HOME = 25;
    public static final int METHOD_RESET_LIMIT = 26;

    private final ThreadPoolExecutor mRequestThreadPool;
    private final OkHttpClient mOkHttpClient;
    private final OkHttpClient mImageOkHttpClient;

    public SiteClient(Context context) {
        mRequestThreadPool = IoThreadPoolExecutor.Companion.getInstance();
        mOkHttpClient = SiteApplication.getOkHttpClient(context);
        mImageOkHttpClient = SiteApplication.getImageOkHttpClient(context);
    }

    public void execute(SiteRequest request) {
        if (!request.isCancelled()) {
            Task task = new Task(request.getMethod(), request.getCallback(), request.getSiteConfig());
            task.executeOnExecutor(mRequestThreadPool, request.getArgs());
            request.task = task;
        } else {
            request.getCallback().onCancel();
        }
    }

    @SuppressLint("StaticFieldLeak")
    public class Task extends AsyncTask<Object, Void, Object> {

        private final int mMethod;
        private Callback mCallback;
        private SiteConfig mSiteConfig;

        private final AtomicReference<Call> mCall = new AtomicReference<>();
        private final AtomicBoolean mStop = new AtomicBoolean();

        public Task(int method, Callback callback, SiteConfig ehConfig) {
            mMethod = method;
            mCallback = callback;
            mSiteConfig = ehConfig;
        }

        // Called in Job thread
        public void setCall(Call call) throws CancelledException {
            if (mStop.get()) {
                // Stopped Job thread
                throw new CancelledException();
            } else {
                mCall.lazySet(call);
            }
        }

        public SiteConfig getSiteConfig() {
            return mSiteConfig;
        }

        public void stop() {
            if (!mStop.get()) {
                mStop.lazySet(true);

                if (mCallback != null) {
                    // TODO Avoid new runnable
                    final Callback finalCallback = mCallback;
                    SimpleHandler.getInstance().post(finalCallback::onCancel);
                }

                Status status = getStatus();
                if (status == Status.PENDING) {
                    cancel(false);
                } else if (status == Status.RUNNING) {
                    // It is running, cancel call if it is created
                    Call call = mCall.get();
                    if (call != null) {
                        call.cancel();
                    }
                }

                // Clear
                mCallback = null;
                mSiteConfig = null;
                mCall.lazySet(null);
            }
        }

        @Override
        protected Object doInBackground(Object... params) {
            try {
                Log.i(TAG, "doInBackground: "+mMethod);
                switch (mMethod) {
                    case METHOD_SIGN_IN:
                        return SiteEngine.signIn(this, mOkHttpClient, (String) params[0], (String) params[1]);
                    case METHOD_GET_GALLERY_LIST:
                        return SiteEngine.getGalleryList(this, mOkHttpClient, (String) params[0], (int) params[1]);
                    case METHOD_GET_GALLERY_DETAIL:
                        return SiteEngine.getGalleryDetail(this, mOkHttpClient, (String) params[0]);
                    case METHOD_GET_PREVIEW_SET:
                        return SiteEngine.getPreviewSet(this, mOkHttpClient, (String) params[0]);
                    case METHOD_GET_RATE_GALLERY:
                        return SiteEngine.rateGallery(this, mOkHttpClient, (Long) params[0], (String) params[1], (Long) params[2], (String) params[3], (Float) params[4]);
                    case METHOD_GET_COMMENT_GALLERY:
                        return SiteEngine.commentGallery(this, mOkHttpClient, (String) params[0], (String) params[1], (String) params[2]);
                    case METHOD_GET_GALLERY_TOKEN:
                        return SiteEngine.getGalleryToken(this, mOkHttpClient, (Long) params[0], (String) params[1], (Integer) params[2]);
                    case METHOD_GET_FAVORITES:
                        return SiteEngine.getFavorites(this, mOkHttpClient, (String) params[0], (Boolean) params[1]);
                    case METHOD_ADD_FAVORITES:
                        return SiteEngine.addFavorites(this, mOkHttpClient, (Long) params[0], (String) params[1], (Integer) params[2], (String) params[3]);
                    case METHOD_ADD_FAVORITES_RANGE:
                        return SiteEngine.addFavoritesRange(this, mOkHttpClient, (long[]) params[0], (String[]) params[1], (Integer) params[2]);
                    case METHOD_MODIFY_FAVORITES:
                        return SiteEngine.modifyFavorites(this, mOkHttpClient, (String) params[0], (long[]) params[1], (Integer) params[2], (Boolean) params[3]);
                    case METHOD_GET_TORRENT_LIST:
                        return SiteEngine.getTorrentList(this, mOkHttpClient, (String) params[0], (Long) params[1], (String) params[2]);
                    case METHOD_GET_TOP_LIST:
                        return SiteEngine.getTopList(this, mOkHttpClient, (String) params[0]);
                    case METHOD_GET_PROFILE:
                        return SiteEngine.getProfile(this, mOkHttpClient);
                    case METHOD_VOTE_COMMENT:
                        return SiteEngine.voteComment(this, mOkHttpClient, (Long) params[0], (String) params[1], (Long) params[2], (String) params[3], (Long) params[4], (Integer) params[5]);
                    case METHOD_IMAGE_SEARCH:
                        return SiteEngine.imageSearch(this, mImageOkHttpClient, (File) params[0], (Boolean) params[1], (Boolean) params[2], (Boolean) params[3]);
                    case METHOD_ARCHIVE_LIST:
                        return SiteEngine.getArchiveList(this, mOkHttpClient, (String) params[0], (Long) params[1], (String) params[2]);
                    case METHOD_ARCHIVER:
                        return SiteEngine.getArchiver(this, mOkHttpClient, (String) params[0], (Long) params[1], (String) params[2]);
                    case METHOD_DOWNLOAD_ARCHIVE:
                        return SiteEngine.downloadArchive(this, mOkHttpClient, (Long) params[0], (String) params[1], (String) params[2], (String) params[3]);
                    case METHOD_DOWNLOAD_ARCHIVER:
                        return SiteEngine.downloadArchiver(this, mOkHttpClient, (String) params[0], (String) params[1], (String) params[2], (String) params[3]);
                    case METHOD_ADD_TAG:
                        return SiteEngine.addTag(this, mOkHttpClient, (String) params[0], (TagPushParam) params[1]);
                    case METHOD_EDIT_WATCHED:
                    case METHOD_DELETE_WATCHED:
                        return SiteEngine.deleteWatchedTag(this, mOkHttpClient, (String) params[0], (UserTag) params[1]);
                    case METHOD_GET_WATCHED:
                        return SiteEngine.getWatchedList(this, mOkHttpClient, (String) params[0]);
                    case METHOD_GET_NEWS:
                        return SiteEngine.getSiteNews(this, mOkHttpClient);
                    case METHOD_GET_HOME:
                        return SiteEngine.getHomeDetail(this, mOkHttpClient);
                    case METHOD_RESET_LIMIT:
                        return SiteEngine.resetLimit(this, mOkHttpClient);
                    default:
                        return new IllegalStateException("Can't detect method " + mMethod);
                }
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                return e;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void onPostExecute(Object result) {
            if (mCallback != null) {
                //noinspection StatementWithEmptyBody
                if (!(result instanceof CancelledException)) {
                    if (result instanceof Throwable) {
                        mCallback.onFailure((Exception) result);
                        Analytics.recordException((Throwable) result);
                    } else {
                        mCallback.onSuccess(result);
                    }
                } else {
                    // onCancel is called in stop
                }
            }

            // Clear
            mCallback = null;
            mSiteConfig = null;
            mCall.lazySet(null);
        }
    }

    public interface Callback<E> {

        void onSuccess(E result);

        void onFailure(Exception e);

        void onCancel();
    }
}
