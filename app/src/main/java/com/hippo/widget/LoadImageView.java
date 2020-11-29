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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import com.hippo.conaco.Conaco;
import com.hippo.conaco.ConacoTask;
import com.hippo.conaco.DataContainer;
import com.hippo.conaco.Unikery;
import com.hippo.drawable.PreciselyClipDrawable;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.image.ImageBitmap;
import com.hippo.image.ImageDrawable;
import com.hippo.image.RecycledException;
import com.hippo.util.DrawableManager;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class LoadImageView extends FixedAspectImageView implements Unikery<ImageBitmap>,
        View.OnClickListener, View.OnLongClickListener, Animatable {

    private static final String TAG = LoadImageView.class.getSimpleName();

    @IntDef({RETRY_TYPE_NONE, RETRY_TYPE_CLICK, RETRY_TYPE_LONG_CLICK})
    @Retention(RetentionPolicy.SOURCE)
    private @interface RetryType {}

    public static final int RETRY_TYPE_NONE = 0;
    public static final int RETRY_TYPE_CLICK = 1;
    public static final int RETRY_TYPE_LONG_CLICK = 2;

    private int mTaskId = Unikery.INVALID_ID;

    private Conaco<ImageBitmap> mConaco;

    private String mKey;
    private String mUrl;
    private DataContainer mContainer;
    private boolean mUseNetwork;

    private int mOffsetX = Integer.MIN_VALUE;
    private int mOffsetY = Integer.MIN_VALUE;
    private int mClipWidth = Integer.MIN_VALUE;
    private int mClipHeight = Integer.MIN_VALUE;

    private int mRetryType;

    private boolean mFailed;

    public LoadImageView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public LoadImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public LoadImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle, 0);
    }

    @SuppressWarnings("WrongConstant")
    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LoadImageView, defStyleAttr, defStyleRes);
        setRetryType(a.getInt(R.styleable.LoadImageView_retryType, 0));
        a.recycle();

        if (!isInEditMode()) {
            mConaco = EhApplication.getConaco(context);
        }
    }

    @Override
    public void setTaskId(int id) {
        mTaskId = id;
    }

    @Override
    public int getTaskId() {
        return mTaskId;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mFailed) {
            onFailure();
        } else if (mTaskId == Unikery.INVALID_ID) /* if (!mConaco.isLoading(mTaskId)) TODO Update Conaco */ {
            load(mKey, mUrl, mContainer, mUseNetwork);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Cancel
        mConaco.cancel(this);
        // Clear drawable
        clearDrawable();
    }

    private ImageDrawable getImageDrawable() {
        Drawable drawable = getDrawable();
        if (drawable instanceof TransitionDrawable) {
            TransitionDrawable transitionDrawable = (TransitionDrawable) drawable;
            if (transitionDrawable.getNumberOfLayers() == 2) {
                drawable = transitionDrawable.getDrawable(1);
            }
        }
        if (drawable instanceof PreciselyClipDrawable) {
            drawable = ((PreciselyClipDrawable) drawable).getWrappedDrawable();
        }
        if (drawable instanceof ImageDrawable) {
            return (ImageDrawable) drawable;
        } else {
            return null;
        }
    }

    private void clearDrawable() {
        // Recycle ImageDrawable
        ImageDrawable imageDrawable = getImageDrawable();
        if (imageDrawable != null) {
            imageDrawable.recycle();
        }

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
        load(key, url, null, true);
    }

    public void load(String key, String url, boolean useNetwork) {
        load(key, url, null, useNetwork);
    }

    public void load(String key, String url, DataContainer container, boolean useNetwork) {
        if (url == null || (key == null && container == null)) {
            return;
        }

        mFailed = false;
        clearRetry();

        mKey = key;
        mUrl = url;
        mContainer = container;
        mUseNetwork = useNetwork;

        ConacoTask.Builder<ImageBitmap> builder = new ConacoTask.Builder<ImageBitmap>()
                .setUnikery(this)
                .setKey(key)
                .setUrl(url)
                .setDataContainer(container)
                .setUseNetwork(useNetwork);
        mConaco.load(builder);
    }

    public void load(Drawable drawable) {
        unload();
        onPreSetImageDrawable(drawable, true);
        setImageDrawable(drawable);
    }

    public void load(@DrawableRes int id) {
        unload();
        onPreSetImageResource(id, true);
        setImageResource(id);
    }

    public void unload() {
        mConaco.cancel(this);
        mKey = null;
        mUrl = null;
        mContainer = null;
        clearDrawable();
    }

    @Override
    public void onMiss(int source) {
        if (source == Conaco.SOURCE_MEMORY) {
            clearDrawable();
        }
    }

    @Override
    public void onRequest() {}

    @Override
    public void onProgress(long singleReceivedSize, long receivedSize, long totalSize) {}

    @Override
    public void onWait() {
        clearDrawable();
    }

    @Override
    public boolean onGetValue(@NonNull ImageBitmap value, int source) {
        Drawable drawable;
        try {
            drawable = new ImageDrawable(value);
        } catch (RecycledException e) {
            // The image might be recycled because it is removed from memory cache.
            Log.d(TAG, "The image is recycled", e);
            return false;
        }

        clearDrawable();

        if (Integer.MIN_VALUE != mOffsetX) {
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
        Drawable drawable = DrawableManager.getVectorDrawable(getContext(), R.drawable.image_failed);
        onPreSetImageDrawable(drawable, true);
        setImageDrawable(drawable);
        if (mRetryType == RETRY_TYPE_CLICK) {
            setOnClickListener(this);
        } else if (mRetryType == RETRY_TYPE_LONG_CLICK) {
            setOnLongClickListener(this);
        } else {
            // Can't retry, so release
            mKey = null;
            mUrl = null;
            mContainer = null;
        }
    }

    @Override
    public void onCancel() {
        mFailed = false;
    }

    @Override
    public void start() {
        ImageDrawable drawable = getImageDrawable();
        if (drawable != null) {
            drawable.start();
        }
    }

    @Override
    public void stop() {
        ImageDrawable drawable = getImageDrawable();
        if (drawable != null) {
            drawable.stop();
        }
    }

    @Override
    public boolean isRunning() {
        ImageDrawable drawable = getImageDrawable();
        if (drawable != null) {
            return drawable.isRunning();
        } else {
            return false;
        }
    }

    @Override
    public void onClick(@NonNull View v) {
        load(mKey, mUrl, mContainer, true);
    }

    @Override
    public boolean onLongClick(@NonNull View v) {
        load(mKey, mUrl, mContainer, true);
        return true;
    }

    public void onPreSetImageDrawable(Drawable drawable, boolean isTarget) { }

    public void onPreSetImageResource(int resId, boolean isTarget) { }
}
