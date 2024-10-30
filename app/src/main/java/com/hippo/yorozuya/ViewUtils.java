/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.yorozuya;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

public final class ViewUtils {
    public static final int MAX_SIZE = Integer.MAX_VALUE & ~(0x3 << 30);
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    private ViewUtils() {
    }

    /**
     * Get view center location in window
     *
     * @param view     the view to check
     * @param location an array of two integers in which to hold the coordinates
     */
    public static void getCenterInWindows(View view, int[] location) {
        getLocationInWindow(view, location);
        location[0] += view.getWidth() / 2;
        location[1] += view.getHeight() / 2;
    }

    /**
     * Get view location in window
     *
     * @param view     the view to check
     * @param location an array of two integers in which to hold the coordinates
     */
    public static void getLocationInWindow(View view, int[] location) {
        getLocationInAncestor(view, location, android.R.id.content);
    }

    /**
     * Get view center in ths ancestor
     *
     * @param view       the view to start with
     * @param location   the container of result
     * @param ancestorId the ancestor id
     */
    public static void getCenterInAncestor(View view, int[] location, int ancestorId) {
        getLocationInAncestor(view, location, ancestorId);
        location[0] += view.getWidth() / 2;
        location[1] += view.getHeight() / 2;
    }

    /**
     * Get view location in ths ancestor
     *
     * @param view       the view to start with
     * @param location   the container of result
     * @param ancestorId the ancestor id
     */
    public static void getLocationInAncestor(View view, int[] location, int ancestorId) {
        if (location == null || location.length < 2) {
            throw new IllegalArgumentException(
                    "location must be an array of two integers");
        }

        float[] position = new float[2];

        position[0] = view.getLeft();
        position[1] = view.getTop();

        ViewParent viewParent = view.getParent();
        while (viewParent instanceof View) {
            view = (View) viewParent;
            if (view.getId() == ancestorId) {
                break;
            }

            position[0] -= view.getScrollX();
            position[1] -= view.getScrollY();

            position[0] += view.getLeft();
            position[1] += view.getTop();

            viewParent = view.getParent();
        }

        location[0] = (int) (position[0] + 0.5f);
        location[1] = (int) (position[1] + 0.5f);
    }

    /**
     * Get view center in ths ancestor
     *
     * @param view     the view to start with
     * @param location the container of result
     * @param ancestor the ancestor
     */
    public static void getCenterInAncestor(View view, int[] location, View ancestor) {
        getLocationInAncestor(view, location, ancestor);
        location[0] += view.getWidth() / 2;
        location[1] += view.getHeight() / 2;
    }

    /**
     * Get view location in ths ancestor
     *
     * @param view     the view to start with
     * @param location the container of result
     * @param ancestor the ancestor
     */
    public static void getLocationInAncestor(View view, int[] location, View ancestor) {
        if (location == null || location.length < 2) {
            throw new IllegalArgumentException(
                    "location must be an array of two integers");
        }

        float[] position = new float[2];

        position[0] = view.getLeft();
        position[1] = view.getTop();

        ViewParent viewParent = view.getParent();
        while (viewParent instanceof View) {
            view = (View) viewParent;
            if (viewParent == ancestor) {
                break;
            }

            position[0] -= view.getScrollX();
            position[1] -= view.getScrollY();

            position[0] += view.getLeft();
            position[1] += view.getTop();

            viewParent = view.getParent();
        }

        location[0] = (int) (position[0] + 0.5f);
        location[1] = (int) (position[1] + 0.5f);
    }

    /**
     * Look for a ancestor view with the given id. If this view has the given
     * id, return this view.
     *
     * @param view the view to start with
     * @param id   The id to search for.
     * @return The view that has the given id in the hierarchy or null
     */
    public static View getAncestor(View view, int id) {
        if (view.getId() == id) {
            return view;
        }

        ViewParent viewParent = view.getParent();
        while (viewParent instanceof View) {
            view = (View) viewParent;
            if (view.getId() == id) {
                return view;
            }
            viewParent = view.getParent();
        }
        return null;
    }

    /**
     * Look for a child view with the given id. If this view has the given
     * id, return this view.
     *
     * @param view the view to start with
     * @param id   the id to search for
     * @return the view that has the given id in the hierarchy or null
     */
    public static View getChild(View view, int id) {
        if (view.getId() == id) {
            return view;
        }

        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0, n = viewGroup.getChildCount(); i < n; i++) {
                View child = viewGroup.getChildAt(i);
                View result = getChild(child, id);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Returns a bitmap showing a screenshot of the view passed in.
     *
     * @param v The view to get screenshot
     * @return The screenshot
     */
    public static Bitmap getBitmapFromView(View v) {
        int width = v.getWidth();
        int height = v.getHeight();
        if (width == 0 && height == 0) {
            width = v.getMeasuredWidth();
            height = v.getMeasuredHeight();
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-v.getScrollX(), -v.getScrollY());
        v.draw(canvas);
        return bitmap;
    }

    /**
     * Remove view from its parent
     *
     * @param view the view to remove
     */
    public static void removeFromParent(View view) {
        ViewParent vp = view.getParent();
        if (vp instanceof ViewGroup)
            ((ViewGroup) vp).removeView(view);
    }

    /**
     * Method that removes the support for HardwareAcceleration from a
     * {@link View}.<br/>
     * <br/>
     * Check AOSP notice:<br/>
     *
     * <pre>
     * 'ComposeShader can only contain shaders of different types (a BitmapShader and a
     * LinearGradient for instance, but not two instances of BitmapShader)'. But, 'If your
     * application is affected by any of these missing features or limitations, you can turn
     * off hardware acceleration for just the affected portion of your application by calling
     * setLayerType(View.LAYER_TYPE_SOFTWARE, null).'
     * </pre>
     *
     * @param v The view
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void removeHardwareAccelerationSupport(View v) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            if (v.getLayerType() != View.LAYER_TYPE_SOFTWARE) {
                v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void addHardwareAccelerationSupport(View v) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            if (v.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }
    }

    public static void measureView(View v, int width, int height) {
        int widthMeasureSpec;
        int heightMeasureSpec;
        if (width == ViewGroup.LayoutParams.WRAP_CONTENT)
            widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0,
                    View.MeasureSpec.UNSPECIFIED);
        else
            widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(Math.max(width, 0),
                    View.MeasureSpec.EXACTLY);
        if (height == ViewGroup.LayoutParams.WRAP_CONTENT)
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0,
                    View.MeasureSpec.UNSPECIFIED);
        else
            heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(Math.max(height, 0),
                    View.MeasureSpec.EXACTLY);

        v.measure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Determine if the supplied view is under the given point in the
     * parent view's coordinate system.
     *
     * @param view Child view of the parent to hit test
     * @param x    X position to test in the parent's coordinate system
     * @param y    Y position to test in the parent's coordinate system
     * @param slop the slop out of the view, or negative for inside
     * @return true if the supplied view is under the given point, false otherwise
     */
    public static boolean isViewUnder(@Nullable View view, int x, int y, int slop) {
        if (view == null) {
            return false;
        } else {
            float translationX = 0.0f;
            float translationY = 0.0f;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                translationX = view.getTranslationX();
                translationY = view.getTranslationY();
            }
            return x >= view.getLeft() + translationX - slop &&
                    x < view.getRight() + translationX + slop &&
                    y >= view.getTop() + translationY - slop &&
                    y < view.getBottom() + translationY + slop;
        }
    }

    /**
     * Utility to return a default size. Uses the supplied size if the
     * MeasureSpec imposed no constraints. Will get suitable if allowed
     * by the MeasureSpec.
     *
     * @param size        Default size for this view
     * @param measureSpec Constraints imposed by the parent
     * @return The size this view should be.
     */
    public static int getSuitableSize(int size, int measureSpec) {
        int result = size;
        int specMode = View.MeasureSpec.getMode(measureSpec);
        int specSize = View.MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case View.MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case View.MeasureSpec.EXACTLY:
                result = specSize;
                break;
            case View.MeasureSpec.AT_MOST:
                return size == 0 ? specSize : Math.min(size, specSize);
        }
        return result;
    }

    /**
     * removeOnGlobalLayoutListener
     *
     * @param viewTreeObserver the ViewTreeObserver
     * @param l                the OnGlobalLayoutListener
     */
    @SuppressWarnings("deprecation")
    public static void removeOnGlobalLayoutListener(ViewTreeObserver viewTreeObserver,
                                                    ViewTreeObserver.OnGlobalLayoutListener l) {
        viewTreeObserver.removeOnGlobalLayoutListener(l);
    }

    /**
     * Get index in parent
     *
     * @param view The view
     * @return The index
     */
    public static int getIndexInParent(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup viewParent) {
            int count = viewParent.getChildCount();
            for (int i = 0; i < count; i++) {
                View v = viewParent.getChildAt(i);
                if (v == view) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Transform point from parent to child
     *
     * @param point  the point
     * @param parent the parent
     * @param child  the child
     */
    public static void transformPointToViewLocal(float[] point, View parent, View child) {
        point[0] += parent.getScrollX() - child.getLeft();
        point[1] += parent.getScrollY() - child.getTop();
    }

    public static void setEnabledRecursively(View view, boolean enabled) {
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0, n = viewGroup.getChildCount(); i < n; i++) {
                setEnabledRecursively(viewGroup.getChildAt(i), enabled);
            }
        }
        view.setEnabled(enabled);
    }

    public static void dumpViewHierarchy(View view, PrintWriter writer) {
        dumpViewHierarchy(view, writer, "");
    }

    private static void dumpViewHierarchy(View view, PrintWriter writer, String prefix) {
        writer.write(prefix);
        writer.write(view.getClass().getName());
        writer.write('\n');
        if (view instanceof ViewGroup viewGroup) {
            String newPrefix = prefix + "    ";
            for (int i = 0, count = viewGroup.getChildCount(); i < count; i++) {
                View child = viewGroup.getChildAt(i);
                dumpViewHierarchy(child, writer, newPrefix);
            }
        }
        writer.flush();
    }

    /**
     * Generate a value suitable for use in {@link View#setId(int)}.
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    public static int generateViewId() {
        for (; ; ) {
            final int result = sNextGeneratedId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }

    /**
     * Offset this view translationX
     */
    public static void translationXBy(View view, float offset) {
        view.setTranslationX(view.getTranslationX() + offset);
    }

    /**
     * Offset this view translationY
     */
    public static void translationYBy(View view, float offset) {
        view.setTranslationY(view.getTranslationY() + offset);
    }

    /**
     * The visual right position of this view, in pixels. This is equivalent to the
     * {@link View#setTranslationX(float) translationX} property plus the current
     * {@link View#getRight() right} property.
     *
     * @return The visual right position of this view, in pixels.
     */
    public static float getX2(View view) {
        return view.getRight() + view.getTranslationX();
    }

    /**
     * The visual bottom position of this view, in pixels. This is equivalent to the
     * {@link View#setTranslationY(float) translationY} property plus the current
     * {@link View#getBottom()} () bottom} property.
     *
     * @return The visual bottom position of this view, in pixels.
     */
    public static float getY2(View view) {
        return view.getBottom() + view.getTranslationY();
    }

    @NonNull
    public static View $$(Activity activity, @IdRes int id) {
        View result = activity.findViewById(id);
        if (null == result) {
            throw new NullPointerException("Can't find view with id: " + id);
        }
        return result;
    }

    @NonNull
    public static View $$(Dialog dialog, @IdRes int id) {
        View result = dialog.findViewById(id);
        if (null == result) {
            throw new NullPointerException("Can't find view with id: " + id);
        }
        return result;
    }

    @NonNull
    public static View $$(View view, @IdRes int id) {
        View result = view.findViewById(id);
        if (null == result) {
            throw new NullPointerException("Can't find view with id: " + id);
        }
        return result;
    }
}
