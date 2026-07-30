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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.lib.yorozuya.ViewUtils;

public class GalleryGuideView extends ViewGroup implements View.OnClickListener {

    private static final float LEFT_COLUMN_END = 9.0f / 25.0f;
    private static final float RIGHT_COLUMN_START = 16.0f / 25.0f;
    private static final float SAVE_AREA_TOP = 4.0f / 5.0f;

    private int mBgColor;
    private Paint mPaint;
    private final float[] mPoints = new float[4 * 4];
    private int mStep;

    private TextView mLeftText;
    private TextView mRightText;
    private TextView mMenuText;
    private TextView mProgressText;
    private TextView mSaveText;
    private TextView mLongClickText;

    public GalleryGuideView(Context context) {
        super(context);
        init(context);
    }

    public GalleryGuideView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GalleryGuideView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mBgColor = AttrResources.getAttrColor(context, R.attr.guideBackgroundColor);
        mPaint = new Paint();
        mPaint.setColor(AttrResources.getAttrColor(context, R.attr.guideTitleColor));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(context.getResources().getDimension(R.dimen.gallery_guide_divider_width));
        setOnClickListener(this);
        setWillNotDraw(false);
        bind();
    }

    private void bind() {
        // Clear up
        removeAllViews();
        mLeftText = null;
        mRightText = null;
        mMenuText = null;
        mProgressText = null;
        mSaveText = null;
        mLongClickText = null;

        switch (mStep) {
            case 0:
                bind1();
                break;
            default:
            case 1:
                bind2();
                break;
        }
    }

    private void bind1() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.widget_gallery_guide_1, this);
        mLeftText = (TextView) getChildAt(0);
        mRightText = (TextView) getChildAt(1);
        mMenuText = (TextView) getChildAt(2);
        mProgressText = (TextView) getChildAt(3);
        mSaveText = (TextView) getChildAt(4);
    }

    private void bind2() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.widget_gallery_guide_2, this);
        mLongClickText = (TextView) getChildAt(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (MeasureSpec.EXACTLY != widthMode || MeasureSpec.EXACTLY != heightMode) {
            throw new IllegalStateException();
        }

        switch (mStep) {
            case 0:
                int leftDivider = (int) (LEFT_COLUMN_END * widthSize);
                int rightDivider = (int) (RIGHT_COLUMN_START * widthSize);
                int saveDivider = (int) (SAVE_AREA_TOP * heightSize);
                mLeftText.measure(MeasureSpec.makeMeasureSpec(leftDivider, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(saveDivider, MeasureSpec.EXACTLY));
                mRightText.measure(MeasureSpec.makeMeasureSpec(widthSize - rightDivider, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
                mMenuText.measure(MeasureSpec.makeMeasureSpec(rightDivider - leftDivider, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(heightSize / 2, MeasureSpec.EXACTLY));
                mProgressText.measure(MeasureSpec.makeMeasureSpec(rightDivider - leftDivider, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(heightSize / 2, MeasureSpec.EXACTLY));
                mSaveText.measure(MeasureSpec.makeMeasureSpec(leftDivider, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(heightSize - saveDivider, MeasureSpec.EXACTLY));
                break;
            default:
            case 1:
                mLongClickText.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
                break;
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        switch (mStep) {
            case 0:
                int leftDivider = (int) (LEFT_COLUMN_END * width);
                int rightDivider = (int) (RIGHT_COLUMN_START * width);
                int saveDivider = (int) (SAVE_AREA_TOP * height);
                mLeftText.layout(0, 0, leftDivider, saveDivider);
                mRightText.layout(rightDivider, 0, width, height);
                mMenuText.layout(leftDivider, 0, rightDivider, height / 2);
                mProgressText.layout(leftDivider, height / 2, rightDivider, height);
                mSaveText.layout(0, saveDivider, leftDivider, height);
                break;
            default:
            case 1:
                mLongClickText.layout(0, 0, width, height);
                break;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (0 == mStep) {
            mPoints[0] = (int) (LEFT_COLUMN_END * w);
            mPoints[1] = 0;
            mPoints[2] = (int) (LEFT_COLUMN_END * w);
            mPoints[3] = h;

            mPoints[4] = (int) (RIGHT_COLUMN_START * w);
            mPoints[5] = 0;
            mPoints[6] = (int) (RIGHT_COLUMN_START * w);
            mPoints[7] = h;

            mPoints[8] = (int) (LEFT_COLUMN_END * w);
            mPoints[9] = h / 2;
            mPoints[10] = (int) (RIGHT_COLUMN_START * w);
            mPoints[11] = h / 2;

            mPoints[12] = 0;
            mPoints[13] = (int) (SAVE_AREA_TOP * h);
            mPoints[14] = (int) (LEFT_COLUMN_END * w);
            mPoints[15] = (int) (SAVE_AREA_TOP * h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mBgColor);
        if (0 == mStep) {
            canvas.drawLines(mPoints, mPaint);
        }
    }

    @Override
    public void onClick(View v) {
        switch (mStep) {
            case 0:
                mStep++;
                bind();
                break;
            default:
            case 1:
                Settings.putGuideGallery(false);
                ViewUtils.removeFromParent(this);
                break;
        }
    }
}
