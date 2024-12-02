/*
 * Copyright 2015-2016 Hippo Seven
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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.conaco.Conaco;
import com.hippo.conaco.ConacoTask;
import com.hippo.conaco.DataContainer;
import com.hippo.conaco.Unikery;
import com.hippo.drawable.PreciselyClipDrawable;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.ui.scene.download.DownloadsScene;
import com.hippo.lib.image.Image;
import com.hippo.lib.yorozuya.IntIdGenerator;
import com.hippo.util.DrawableManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class LoadImageView extends FixedAspectImageView implements Unikery<Image>,
        View.OnClickListener, View.OnLongClickListener, Animatable {

    public static final int RETRY_TYPE_NONE = 0;
    public static final int RETRY_TYPE_CLICK = 1;
    public static final int RETRY_TYPE_LONG_CLICK = 2;
    private static final String TAG = LoadImageView.class.getSimpleName();
    private int mTaskId = Unikery.INVALID_ID;
    private Conaco<Image> mConaco;
    private String mKey;
    private String mUrl;
    private boolean mUseNetwork;
    private int mOffsetX = Integer.MIN_VALUE;
    private int mOffsetY = Integer.MIN_VALUE;
    private int mClipWidth = Integer.MIN_VALUE;
    private int mClipHeight = Integer.MIN_VALUE;
    private int mRetryType;
    public boolean mFailed;
    private boolean mLoadFromDrawable;
    private boolean secondTry = false;
    @Nullable
    private DownloadInfo downloadInfo = null;

    private int mRequestId = IntIdGenerator.INVALID_ID;

    public LoadImageView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public LoadImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public LoadImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LoadImageView, defStyleAttr, 0);
        setRetryType(a.getInt(R.styleable.LoadImageView_retryType, 0));
        a.recycle();
        setFocusable(false);
        if (!isInEditMode()) {
            mConaco = EhApplication.getConaco(context);
        }
//        setScaleType(ScaleType.FIT_CENTER);
    }

    @Override
    public int getTaskId() {
        return mTaskId;
    }

    @Override
    public void setTaskId(int id) {
        mTaskId = id;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mLoadFromDrawable) {
            if (mFailed) {
                onFailure();
            } else if (mTaskId == Unikery.INVALID_ID) /* if (!mConaco.isLoading(mTaskId)) TODO Update Conaco */ {
                load(mKey, mUrl, mUseNetwork);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (!mLoadFromDrawable) {
            try {
                // Cancel
                mConaco.cancel(this);
            } catch (Exception e) {
                // Ignore
            }
            // Clear drawable
            clearDrawable();
        }
    }

    private Drawable getImageDrawable() {
        Drawable drawable = getDrawable();
        if (drawable instanceof TransitionDrawable transitionDrawable) {
            if (transitionDrawable.getNumberOfLayers() == 2) {
                drawable = transitionDrawable.getDrawable(1);
            }
        }
        if (drawable instanceof PreciselyClipDrawable) {
            drawable = ((PreciselyClipDrawable) drawable).getDrawable();
        }
        return drawable;
    }

    private void clearDrawable() {

        // Set drawable null
        setImageDrawable(null);
    }

    private void clearRetry() {
        if (mRetryType == RETRY_TYPE_CLICK) {
            setOnClickListener(null);
            setClickable(false);
        } else if (mRetryType == RETRY_TYPE_LONG_CLICK) {
            setOnLongClickListener(null);
            setLongClickable(false);
        }
    }

    public void setRetryType(@RetryType int retryType) {
        if (mRetryType != retryType) {
            int oldRetryType = mRetryType;
            mRetryType = retryType;

            if (mFailed) {
                if (oldRetryType == RETRY_TYPE_CLICK) {
                    setOnClickListener(null);
                    setClickable(false);
                } else if (oldRetryType == RETRY_TYPE_LONG_CLICK) {
                    setOnLongClickListener(null);
                    setLongClickable(false);
                }

                if (retryType == RETRY_TYPE_CLICK) {
                    setOnClickListener(this);
                } else if (retryType == RETRY_TYPE_LONG_CLICK) {
                    setOnLongClickListener(this);
                }
            }
        }
    }

    public void setClip(int offsetX, int offsetY, int clipWidth, int clipHeight) {
        mOffsetX = offsetX;
        mOffsetY = offsetY;
        mClipWidth = clipWidth;
        mClipHeight = clipHeight;
    }

    public void resetClip() {
        mOffsetX = Integer.MIN_VALUE;
        mOffsetY = Integer.MIN_VALUE;
        mClipWidth = Integer.MIN_VALUE;
        mClipHeight = Integer.MIN_VALUE;
    }

    public void load(String key, String url) {
        load(key, url, true);
    }

    public void load(String key, String url, boolean useNetwork, DownloadInfo downloadInfo) {
        this.downloadInfo = downloadInfo;
        load(key, url, useNetwork);
    }



    public void load(String key, String url, boolean useNetwork) {
        load(key,url,null,useNetwork);
    }

    public void load(String key, String url, DataContainer dataContainer, boolean useNetwork) {
        if (url == null || key == null) {
            return;
        }

        mLoadFromDrawable = false;
        mFailed = false;
        clearRetry();

        mKey = key;
        mUrl = url;
        mUseNetwork = useNetwork;

        ConacoTask.Builder<Image> builder = new ConacoTask.Builder<Image>()
                .setUnikery(this)
                .setKey(key)
                .setUrl(url)
                .setUseNetwork(useNetwork);
        if (dataContainer!=null){
            builder.setDataContainer(dataContainer);
        }

        mConaco.load(builder);
    }

    public void load(Drawable drawable) {
        unload();
        mLoadFromDrawable = true;
        onPreSetImageDrawable(drawable, true);
        setImageDrawable(drawable);
    }

    public void load(@DrawableRes int id) {
        unload();
        mLoadFromDrawable = true;
        onPreSetImageResource(id, true);
        setImageResource(id);
    }

    public void unload() {
        mConaco.cancel(this);
        mKey = null;
        mUrl = null;
        clearDrawable();
    }

    @Override
    public void onMiss(int source) {
        if (source == Conaco.SOURCE_MEMORY) {
            clearDrawable();
        }
    }

    @Override
    public void onRequest() {
    }

    @Override
    public void onProgress(long singleReceivedSize, long receivedSize, long totalSize) {
    }

    @Override
    public void onWait() {
        clearDrawable();
    }

    @Override
    public boolean onGetValue(@NonNull Image value, int source) {
        Drawable drawable;
        try {
            drawable = value.getDrawable();
        } catch (Exception e) {
            // The image might be recycled because it is removed from memory cache.
            Log.d(TAG, "The image is recycled", e);
            return false;
        }

        if (Integer.MIN_VALUE != mOffsetX) {
            Drawable.ConstantState state = value.getDrawable().getConstantState();
            if (state != null) {
                drawable = state.newDrawable();
            }
            drawable = new PreciselyClipDrawable(drawable, mOffsetX, mOffsetY, mClipWidth, mClipHeight);
        }

        onPreSetImageDrawable(drawable, true);
        if ((source == Conaco.SOURCE_DISK || source == Conaco.SOURCE_NETWORK) && isShown()) {
            Drawable[] layers = new Drawable[2];
            layers[0] = new ColorDrawable(Color.TRANSPARENT);
            layers[1] = drawable;
            TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
            setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(300);
        } else {
            setImageDrawable(drawable);
        }

        return true;
    }

    @Override
    public void onFailure() {
        mFailed = true;
        clearDrawable();
        Drawable drawable;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            drawable = DrawableManager.getVectorDrawable(getContext(), R.drawable.image_failed_new);
        } else {
            drawable = DrawableManager.getVectorDrawable(getContext(), R.drawable.image_failed);
        }

        onPreSetImageDrawable(drawable, true);
        setImageDrawable(drawable);
        if (mRetryType == RETRY_TYPE_CLICK) {
            setOnClickListener(this);
        } else if (mRetryType == RETRY_TYPE_LONG_CLICK) {
            setOnLongClickListener(this);
        } else {
            if (secondTry && downloadInfo != null) {
                Context context = this.getContext();
                if (EhApplication.getInstance().containGlobalStuff(mRequestId)) {
                    // request exist
                    return;
                }
                String detailUrl = EhUrl.getGalleryDetailUrl(downloadInfo.gid, downloadInfo.token);
                GalleryDetailCallback callback = new GalleryDetailCallback(context);
                mRequestId = ((EhApplication) context.getApplicationContext()).putGlobalStuff(callback);
                EhRequest request = new EhRequest()
                        .setMethod(EhClient.METHOD_GET_GALLERY_DETAIL)
                        .setArgs(detailUrl)
                        .setCallback(callback);
                EhApplication.getEhClient(context).execute(request);
            }
            // Can't retry, so release
            mKey = null;
            mUrl = null;
        }

    }

    @Override
    public void onCancel() {
        mFailed = false;
    }

    @Override
    public void start() {
        Drawable drawable = getImageDrawable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (drawable instanceof AnimatedImageDrawable animatedImageDrawable) {
                animatedImageDrawable.start();
            }
        }
    }

    @Override
    public void stop() {
        Drawable drawable = getImageDrawable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (drawable instanceof AnimatedImageDrawable animatedImageDrawable) {
                animatedImageDrawable.stop();
            }
        }
    }

    @Override
    public boolean isRunning() {
        Drawable drawable = getImageDrawable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (drawable instanceof AnimatedImageDrawable animatedImageDrawable) {
                return animatedImageDrawable.isRunning();
            }
        }
        return false;
    }

    @Override
    public void onClick(@NonNull View v) {
        load(mKey, mUrl, true);
    }

    @Override
    public boolean onLongClick(@NonNull View v) {
        load(mKey, mUrl, true);
        return true;
    }

    public void onPreSetImageDrawable(Drawable drawable, boolean isTarget) {
    }

    public void onPreSetImageResource(int resId, boolean isTarget) {
    }



    @IntDef({RETRY_TYPE_NONE, RETRY_TYPE_CLICK, RETRY_TYPE_LONG_CLICK})
    @Retention(RetentionPolicy.SOURCE)
    private @interface RetryType {
    }

    private class GalleryDetailCallback implements EhClient.Callback<GalleryDetail> {
        private final Context context;
        private final EhApplication ehApplication;

        private GalleryDetailCallback(Context context) {
            this.context = context;
            this.ehApplication = (EhApplication) context.getApplicationContext();
        }

        @Override
        public void onSuccess(GalleryDetail result) {
            ehApplication.removeGlobalStuff(this);
            secondTry = true;
            if (context == null||result == null || downloadInfo == null) {
                return;
            }
            // Put gallery detail to cache
            EhApplication.getGalleryDetailCache(context).put(result.gid, result);
            if (downloadInfo.gid == result.gid && !downloadInfo.thumb.equals(result.thumb)) {
                downloadInfo.updateInfo(result);
                EhDB.putDownloadInfo(downloadInfo);
                load(EhCacheKeyFactory.getThumbKey(downloadInfo.gid), downloadInfo.thumb, true);
            }
        }

        @Override
        public void onFailure(Exception e) {
            ehApplication.removeGlobalStuff(this);
        }

        @Override
        public void onCancel() {
            ehApplication.removeGlobalStuff(this);
        }
    }
}
