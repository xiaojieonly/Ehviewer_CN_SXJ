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
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.ObjectUtils;

public class GalleryHeader extends ViewGroup {

  private DisplayCutout displayCutout;

  private View battery;
  private View progress;
  private View clock;

  private Rect batteryRect = new Rect();
  private Rect progressRect = new Rect();
  private Rect clockRect = new Rect();

  private int[] location = new int[2];

  private int lastX = 0;
  private int lastY = 0;

  public GalleryHeader(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  public void setDisplayCutout(@Nullable DisplayCutout displayCutout) {
    if (!ObjectUtils.equal(this.displayCutout, displayCutout)) {
      this.displayCutout = displayCutout;
      requestLayout();
    }
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    battery = findViewById(R.id.battery);
    progress = findViewById(R.id.progress);
    clock = findViewById(R.id.clock);
  }

  private void measureChild(Rect rect, View view, int width, int paddingLeft, int paddingRight) {
    int left;
    MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
    if (view == battery) {
      left = paddingLeft + lp.leftMargin;
    } else if (view == progress) {
      left = paddingLeft + (width - paddingLeft - paddingRight) / 2 - view.getMeasuredWidth() / 2;
    } else {
      left = width - paddingRight - lp.rightMargin - view.getMeasuredWidth();
    }
    rect.set(left, lp.topMargin, left + view.getMeasuredWidth(), lp.topMargin + view.getMeasuredHeight());
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  private boolean offsetVertically(Rect rect, View view, int width) {
    int offset = 0;

    measureChild(rect, view, width, 0, 0);
    rect.offset(lastX, lastY);

    for (Rect notch : displayCutout.getBoundingRects()) {
      if (Rect.intersects(notch, rect)) {
        offset = Math.max(offset, notch.bottom - lastY);
      }
    }

    if (offset != 0) {
      rect.offset(-lastX, -lastY);
      rect.offset(0, offset);
      return true;
    } else {
      return false;
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  private int getOffsetLeft(Rect rect, View view, int width) {
    int offset = 0;

    measureChild(rect, view, width, 0, 0);
    rect.offset(lastX, lastY);

    for (Rect notch : displayCutout.getBoundingRects()) {
      if (Rect.intersects(notch, rect)) {
        offset = Math.max(offset, notch.right - lastX);
      }
    }

    return offset;
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  private int getOffsetRight(Rect rect, View view, int width) {
    int offset = 0;

    measureChild(rect, view, width, 0, 0);
    rect.offset(lastX, lastY);

    for (Rect notch : displayCutout.getBoundingRects()) {
      if (Rect.intersects(notch, rect)) {
        offset = Math.max(offset, lastX + width - notch.left);
      }
    }

    return offset;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
      throw new IllegalStateException();
    }
    int width = MeasureSpec.getSize(widthMeasureSpec);

    int height = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      measureChild(child, widthMeasureSpec, heightMeasureSpec);
      MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
      height = Math.max(height, child.getMeasuredHeight() + lp.topMargin);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && displayCutout != null) {
      // Check progress covered
      if (offsetVertically(progressRect, progress, width)) {
        offsetVertically(batteryRect, battery, width);
        offsetVertically(clockRect, clock, width);
        height = Math.max(progressRect.bottom, Math.max(batteryRect.bottom, clockRect.bottom));
      } else {
        // Clamp left and right
        int paddingLeft = getOffsetLeft(batteryRect, battery, width);
        int paddingRight = getOffsetRight(clockRect, clock, width);
        measureChild(batteryRect, battery, width, paddingLeft, paddingRight);
        measureChild(progressRect, progress, width, paddingLeft, paddingRight);
        measureChild(clockRect, clock, width, paddingLeft, paddingRight);
      }
    } else {
      measureChild(batteryRect, battery, width, 0, 0);
      measureChild(progressRect, progress, width, 0, 0);
      measureChild(clockRect, clock, width, 0, 0);
    }

    setMeasuredDimension(width, height);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    battery.layout(batteryRect.left, batteryRect.top, batteryRect.right, batteryRect.bottom);
    progress.layout(progressRect.left, progressRect.top, progressRect.right, progressRect.bottom);
    clock.layout(clockRect.left, clockRect.top, clockRect.right, clockRect.bottom);

    getLocationOnScreen(location);
    if (lastX != location[0] || lastY != location[1]) {
      lastX = location[0];
      lastY = location[1];
      requestLayout();
    }
  }

  @Override
  public MarginLayoutParams generateLayoutParams(AttributeSet attrs) {
    return new MarginLayoutParams(getContext(), attrs);
  }

  @Override
  protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
    return p instanceof MarginLayoutParams;
  }

  @Override
  protected MarginLayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
    if (lp instanceof MarginLayoutParams) {
      return new MarginLayoutParams((MarginLayoutParams) lp);
    }
    return new MarginLayoutParams(lp);
  }
}
