/*
 * Copyright 2018 Hippo Seven
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

package com.hippo.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

public class DrawableWrapper extends Drawable implements Drawable.Callback {
  private Drawable mDrawable;

  public DrawableWrapper(Drawable drawable) {
    this.setWrappedDrawable(drawable);
  }

  public void draw(@NonNull Canvas canvas) {
    this.mDrawable.draw(canvas);
  }

  protected void onBoundsChange(Rect bounds) {
    this.mDrawable.setBounds(bounds);
  }

  public void setChangingConfigurations(int configs) {
    this.mDrawable.setChangingConfigurations(configs);
  }

  public int getChangingConfigurations() {
    return this.mDrawable.getChangingConfigurations();
  }

  public void setDither(boolean dither) {
    this.mDrawable.setDither(dither);
  }

  public void setFilterBitmap(boolean filter) {
    this.mDrawable.setFilterBitmap(filter);
  }

  public void setAlpha(int alpha) {
    this.mDrawable.setAlpha(alpha);
  }

  public void setColorFilter(ColorFilter cf) {
    this.mDrawable.setColorFilter(cf);
  }

  public boolean isStateful() {
    return this.mDrawable.isStateful();
  }

  public boolean setState(@NonNull int[] stateSet) {
    return this.mDrawable.setState(stateSet);
  }

  @NonNull
  public int[] getState() {
    return this.mDrawable.getState();
  }

  public void jumpToCurrentState() {
    this.mDrawable.jumpToCurrentState();
  }

  @NonNull
  public Drawable getCurrent() {
    return this.mDrawable.getCurrent();
  }

  public boolean setVisible(boolean visible, boolean restart) {
    return super.setVisible(visible, restart) || this.mDrawable.setVisible(visible, restart);
  }

  public int getOpacity() {
    return this.mDrawable.getOpacity();
  }

  public Region getTransparentRegion() {
    return this.mDrawable.getTransparentRegion();
  }

  public int getIntrinsicWidth() {
    return this.mDrawable.getIntrinsicWidth();
  }

  public int getIntrinsicHeight() {
    return this.mDrawable.getIntrinsicHeight();
  }

  public int getMinimumWidth() {
    return this.mDrawable.getMinimumWidth();
  }

  public int getMinimumHeight() {
    return this.mDrawable.getMinimumHeight();
  }

  public boolean getPadding(@NonNull Rect padding) {
    return this.mDrawable.getPadding(padding);
  }

  public void invalidateDrawable(@NonNull Drawable who) {
    this.invalidateSelf();
  }

  public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
    this.scheduleSelf(what, when);
  }

  public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
    this.unscheduleSelf(what);
  }

  protected boolean onLevelChange(int level) {
    return this.mDrawable.setLevel(level);
  }

  public void setAutoMirrored(boolean mirrored) {
    DrawableCompat.setAutoMirrored(this.mDrawable, mirrored);
  }

  public boolean isAutoMirrored() {
    return DrawableCompat.isAutoMirrored(this.mDrawable);
  }

  public void setTint(int tint) {
    DrawableCompat.setTint(this.mDrawable, tint);
  }

  public void setTintList(ColorStateList tint) {
    DrawableCompat.setTintList(this.mDrawable, tint);
  }

  public void setTintMode(@NonNull PorterDuff.Mode tintMode) {
    DrawableCompat.setTintMode(this.mDrawable, tintMode);
  }

  public void setHotspot(float x, float y) {
    DrawableCompat.setHotspot(this.mDrawable, x, y);
  }

  public void setHotspotBounds(int left, int top, int right, int bottom) {
    DrawableCompat.setHotspotBounds(this.mDrawable, left, top, right, bottom);
  }

  public Drawable getWrappedDrawable() {
    return this.mDrawable;
  }

  public void setWrappedDrawable(Drawable drawable) {
    if (this.mDrawable != null) {
      this.mDrawable.setCallback(null);
    }

    this.mDrawable = drawable;
    if (drawable != null) {
      drawable.setCallback(this);
    }
  }
}
