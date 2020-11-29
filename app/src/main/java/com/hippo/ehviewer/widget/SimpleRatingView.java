/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.hippo.ehviewer.R;
import com.hippo.util.DrawableManager;
import com.hippo.yorozuya.MathUtils;

/**
 * 5 stars, from 0 to 10
 */
public class SimpleRatingView extends View {

    private Drawable mStarDrawable;
    private Drawable mStarHalfDrawable;
    private Drawable mStarOutlineDrawable;
    private int mRatingSize;
    private int mRatingInterval;

    private float mRating;
    private int mRatingInt;

    public SimpleRatingView(Context context) {
        super(context);
        init(context);
    }

    public SimpleRatingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SimpleRatingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        Resources resources = context.getResources();
        mStarDrawable = DrawableManager.getVectorDrawable(context, R.drawable.v_star_x16);
        mStarHalfDrawable = DrawableManager.getVectorDrawable(context, R.drawable.v_star_half_x16);
        mStarOutlineDrawable = DrawableManager.getVectorDrawable(context, R.drawable.v_star_outline_x16);
        mRatingSize = resources.getDimensionPixelOffset(R.dimen.rating_size);
        mRatingInterval = resources.getDimensionPixelOffset(R.dimen.rating_interval);

        mStarDrawable.setBounds(0, 0, mRatingSize, mRatingSize);
        mStarHalfDrawable.setBounds(0, 0, mRatingSize, mRatingSize);
        mStarOutlineDrawable.setBounds(0, 0, mRatingSize, mRatingSize);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mRatingSize * 5 + mRatingInterval * 4, mRatingSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int ratingInt = mRatingInt;
        int step = mRatingSize + mRatingInterval;
        int numStar = ratingInt / 2;
        int numStarHalf = ratingInt % 2;
        int saved = canvas.save();
        while (numStar-- > 0) {
            mStarDrawable.draw(canvas);
            canvas.translate(step, 0);
        }
        if (numStarHalf == 1) {
            mStarHalfDrawable.draw(canvas);
            canvas.translate(step, 0);
        }
        int numOutline = 5 - numStar - numStarHalf;
        while (numOutline-- > 0) {
            mStarOutlineDrawable.draw(canvas);
            canvas.translate(step, 0);
        }
        canvas.restoreToCount(saved);
    }

    public void setRating(float rating) {
        if (mRating != rating) {
            mRating = rating;
            int ratingInt = MathUtils.clamp((int) Math.ceil(rating * 2), 0, 10);
            if (mRatingInt != ratingInt) {
                mRatingInt = ratingInt;
                invalidate();
            }
        }
    }

    public float getRating() {
        return mRating;
    }
}
