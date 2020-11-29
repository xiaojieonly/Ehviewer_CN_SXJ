/*
 * Copyright 2019 Hippo Seven
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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.hippo.ehviewer.R;
import com.hippo.widget.LoadImageView;

public class FixedThumb extends LoadImageView {

  private float minAspect;
  private float maxAspect;

  public FixedThumb(Context context) {
    super(context);
    init(context, null, 0, 0);
  }

  public FixedThumb(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs, 0, 0);
  }

  public FixedThumb(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs, defStyle, 0);
  }

  private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FixedThumb, defStyleAttr, defStyleRes);
    minAspect = a.getFloat(R.styleable.FixedThumb_minAspect, 0.0f);
    maxAspect = a.getFloat(R.styleable.FixedThumb_maxAspect, 0.0f);
    a.recycle();
  }

  public void setFix(float minAspect, float maxAspect) {
    this.minAspect = minAspect;
    this.maxAspect = maxAspect;
  }

  @Override
  public void onPreSetImageDrawable(Drawable drawable, boolean isTarget) {
    if (isTarget && drawable != null) {
      int width = drawable.getIntrinsicWidth();
      int height = drawable.getIntrinsicHeight();
      if (width > 0 && height > 0) {
        float aspect = (float) width / (float) height;
        if (aspect < maxAspect && aspect > minAspect) {
          setScaleType(ScaleType.CENTER_CROP);
          return;
        }
      }
    }

    setScaleType(ScaleType.FIT_CENTER);
  }

  @Override
  public void onPreSetImageResource(int resId, boolean isTarget) {
    setScaleType(ScaleType.FIT_CENTER);
  }
}
