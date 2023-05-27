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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
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
import com.hippo.ehviewer.R;
import com.hippo.image.ImageBitmap;
import com.hippo.image.ImageDrawable;
import com.hippo.image.RecycledException;
import com.hippo.util.DrawableManager;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AvatarImageView extends FixedAspectImageView implements Unikery<ImageBitmap>, View.OnClickListener, View.OnLongClickListener, Animatable {

    public static final int RETRY_TYPE_NONE = 0;

    public static final int RETRY_TYPE_CLICK = 1;

    public static final int RETRY_TYPE_LONG_CLICK = 2;

    private static final String TAG = AvatarImageView.class.getSimpleName();

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

    public boolean mFailed;

    private boolean mLoadFromDrawable;

    private Bitmap mSrcBitmap;

    /**
     * 圆角的弧度
     */
    private float mRadius;

    private boolean mIsCircle;

    public AvatarImageView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public AvatarImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public AvatarImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LoadImageView, defStyleAttr, 0);
        setRetryType(a.getInt(R.styleable.LoadImageView_retryType, 0));
        mRadius = a.getDimension(R.styleable.LoadImageView_image_radius, 0);
        mIsCircle = a.getBoolean(R.styleable.LoadImageView_image_circle, false);
        if (attrs != null) {
            int srcResource = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "src", 0);
            if (srcResource != 0)
                mSrcBitmap = BitmapFactory.decodeResource(getResources(), srcResource);
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Paint paintBorder = new Paint();
        paintBorder.setAntiAlias(true);
        a.recycle();
        setFocusable(false);
        if (!isInEditMode()) {
            mConaco = EhApplication.getConaco(context);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        Bitmap image = drawableToBitmap(getDrawable());
        if (image == null) {
            super.onDraw(canvas);
            return;
        }
        if (mIsCircle) {
            Bitmap reSizeImage = reSizeImageC(image, width, height);
            canvas.drawBitmap(createCircleImage(reSizeImage, width, height), getPaddingLeft(), getPaddingTop(), null);
        } else {
            Bitmap reSizeImage = reSizeImage(image, width, height);
            canvas.drawBitmap(createRoundImage(reSizeImage, width, height), getPaddingLeft(), getPaddingTop(), null);
        }
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
                /* if (!mConaco.isLoading(mTaskId)) TODO Update Conaco */
            } else if (mTaskId == Unikery.INVALID_ID) {
                load(mKey, mUrl, mContainer, mUseNetwork);
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
        mLoadFromDrawable = false;
        mFailed = false;
        clearRetry();
        mKey = key;
        mUrl = url;
        mContainer = container;
        mUseNetwork = useNetwork;
        //        ConacoTask.Builder<ImageBitmap> builder = new ConacoTask.Builder<ImageBitmap>()
        //                .setUnikery(this)
        //                .setKey(key)
        //                .setUrl(url)
        //                .setDataContainer(container)
        //                .setUseNetwork(useNetwork);
        ConacoTask.Builder<ImageBitmap> builder = new ConacoTask.Builder<>();
        builder.unikery = this;
        builder.key = key;
        builder.url = url;
        builder.dataContainer = container;
        builder.useNetwork = useNetwork;
        builder.okHttpClient = EhApplication.getOkHttpClient(getContext());
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
        mContainer = null;
        clearDrawable();
    }

    @Override
    public void onMiss(int source) {
        if (source == Conaco.SOURCE_MEMORY) {
            clearDrawable();
        }
    }

    //    @Override
    //    public void onRequest() {
    //    }
    @Override
    public void onProgress(long singleReceivedSize, long receivedSize, long totalSize) {
    }

    @Override
    public void onWait() {
        clearDrawable();
    }

    @Override
    public void onGetValue(@NonNull ImageBitmap value, int source) {
        Drawable drawable;
        try {
            drawable = new ImageDrawable(value);
        } catch (RecycledException e) {
            // The image might be recycled because it is removed from memory cache.
            Log.d(TAG, "The image is recycled", e);
            return;
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

    public void onPreSetImageDrawable(Drawable drawable, boolean isTarget) {
    }

    public void onPreSetImageResource(int resId, boolean isTarget) {
    }

    @IntDef({ RETRY_TYPE_NONE, RETRY_TYPE_CLICK, RETRY_TYPE_LONG_CLICK })
    @Retention(RetentionPolicy.SOURCE)
    private @interface RetryType {
    }

    /**
     * 画圆角
     *
     * @param source
     * @param width
     * @param height
     * @return
     */
    private Bitmap createRoundImage(Bitmap source, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        RectF rect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rect, mRadius, mRadius, paint);
        // 核心代码取两个图片的交集部分
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source, 0, 0, paint);
        return target;
    }

    /**
     * 画圆
     *
     * @param source
     * @param width
     * @param height
     * @return
     */
    private Bitmap createCircleImage(Bitmap source, int width, int height) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Bitmap target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        canvas.drawCircle(width >> 1, height >> 1, Math.min(width, height) >> 1, paint);
        // 核心代码取两个图片的交集部分
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source, (width - source.getWidth()) >> 1, (height - source.getHeight()) >> 1, paint);
        return target;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    /**
     * drawable转bitmap
     *
     * @param drawable
     * @return
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            if (mSrcBitmap != null) {
                return mSrcBitmap;
            } else {
                return null;
            }
        } else if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * 重设Bitmap的宽高
     *
     * @param bitmap
     * @param newWidth
     * @param newHeight
     * @return
     */
    private Bitmap reSizeImage(Bitmap bitmap, int newWidth, int newHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // 计算出缩放比
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // 矩阵缩放bitmap
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    /**
     * 重设Bitmap的宽高
     *
     * @param bitmap
     * @param newWidth
     * @param newHeight
     * @return
     */
    private Bitmap reSizeImageC(Bitmap bitmap, int newWidth, int newHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int x = (newWidth - width) / 2;
        int y = (newHeight - height) / 2;
        if (x > 0 && y > 0) {
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, null, true);
        }
        float scale;
        if (width > height) {
            // 按照宽度进行等比缩放
            scale = ((float) newWidth) / width;
        } else {
            // 按照高度进行等比缩放
            // 计算出缩放比
            scale = ((float) newHeight) / height;
        }
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }
}
