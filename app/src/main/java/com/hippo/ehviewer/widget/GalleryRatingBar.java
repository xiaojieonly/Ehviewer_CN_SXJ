/*
 * Copyright 2014-2016 Hippo Seven
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
import android.util.AttributeSet;
import android.widget.RatingBar;
import androidx.appcompat.widget.AppCompatRatingBar;

public class GalleryRatingBar extends AppCompatRatingBar
        implements RatingBar.OnRatingBarChangeListener {

    private OnUserRateListener mListener;

    public GalleryRatingBar(Context context) {
        super(context);
        init();
    }

    public GalleryRatingBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GalleryRatingBar(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setOnRatingBarChangeListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mListener != null) {
            mListener.onUserRate(getRating());
        }
    }

    public void setOnUserRateListener(OnUserRateListener l) {
        mListener = l;
    }

    @Override
    public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
        if (rating <= 0.0f) {
            setRating(0.5f);
        }
    }

    public interface OnUserRateListener {
        void onUserRate(float rating);
    }
}
