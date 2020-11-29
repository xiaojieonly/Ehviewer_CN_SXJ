/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.hippo.util;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

/**
 * Helper for controlling the visibility of the System UI across the various API levels. To use
 * this API, instantiate an instance of this class with the required level. The level specifies the
 * extent to which the System UI's visibility is changed when you call {@link #hide()}
 * or {@link #toggle()}.
 */
public final class SystemUiHelper {

    /**
     * In this level, the helper will toggle low profile mode.
     */
    public static final int LEVEL_LOW_PROFILE = 0;

    /**
     * In this level, the helper will toggle the visibility of the status bar.
     * If there is a navigation bar, it will toggle low profile mode.
     */
    public static final int LEVEL_HIDE_STATUS_BAR = 1;

    /**
     * In this level, the helper will toggle the visibility of the navigation bar
     * (if present and if possible) and status bar. In cases where the navigation
     * bar is present but cannot be hidden, it will toggle low profile mode.
     */
    public static final int LEVEL_LEAN_BACK = 2;

    /**
     * In this level, the helper will toggle the visibility of the navigation bar
     * (if present and if possible) and status bar, in an immersive mode. This means that the app
     * will continue to receive all touch events. The user can reveal the system bars with an
     * inward swipe along the region where the system bars normally appear.
     *
     * <p>The {@link #FLAG_IMMERSIVE_STICKY} flag can be used to control how the system bars are
     * displayed.
     */
    public static final int LEVEL_IMMERSIVE = 3;

    /**
     * When this flag is set, the
     * {@link android.view.WindowManager.LayoutParams#FLAG_LAYOUT_IN_SCREEN}
     * flag will be set on older devices, making the status bar "float" on top
     * of the activity layout. This is most useful when there are no controls at
     * the top of the activity layout.
     * <p>
     * This flag isn't used on newer devices because the <a
     * href="http://developer.android.com/design/patterns/actionbar.html">action
     * bar</a>, the most important structural element of an Android app, should
     * be visible and not obscured by the system UI.
     */
    public static final int FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES = 0x1;

    /**
     * Used with {@link #LEVEL_IMMERSIVE}. When this flag is set, an inward swipe in the system
     * bars areas will cause the system bars to temporarily appear in a semi-transparent state,
     * but no flags are cleared, and your system UI visibility change listeners are not triggered.
     * The bars automatically hide again after a short delay, or if the user interacts with the
     * middle of the screen.
     */
    public static final int FLAG_IMMERSIVE_STICKY = 0x2;

    private static final String LOG_TAG = SystemUiHelper.class.getSimpleName();

    private final SystemUiHelperImpl mImpl;

    private final Handler mHandler;
    private final Runnable mHideRunnable;

    /**
     * Construct a new SystemUiHelper.
     *
     * @param activity The Activity who's system UI should be changed
     * @param level The level of hiding. Should be either {@link #LEVEL_LOW_PROFILE},
     *              {@link #LEVEL_HIDE_STATUS_BAR}, {@link #LEVEL_LEAN_BACK} or
     *              {@link #LEVEL_IMMERSIVE}
     * @param flags Additional options. See {@link #FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES} and
     *              {@link #FLAG_IMMERSIVE_STICKY}
     */
    public SystemUiHelper(Activity activity, int level, int flags) {
        this(activity, level, flags, null);
    }

    /**
     * Construct a new SystemUiHelper.
     *
     * @param activity The Activity who's system UI should be changed
     * @param level The level of hiding. Should be either {@link #LEVEL_LOW_PROFILE},
     *              {@link #LEVEL_HIDE_STATUS_BAR}, {@link #LEVEL_LEAN_BACK} or
     *              {@link #LEVEL_IMMERSIVE}
     * @param flags Additional options. See {@link #FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES} and
     *              {@link #FLAG_IMMERSIVE_STICKY}
     * @param listener A listener which is called when the system visibility is changed
     */
    public SystemUiHelper(Activity activity, int level, int flags,
            OnVisibilityChangeListener listener) {

        mHandler = new Handler(Looper.getMainLooper());
        mHideRunnable = new HideRunnable();

        // Create impl
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mImpl = new SystemUiHelperImplKK(activity, level, flags, listener);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mImpl = new SystemUiHelperImplJB(activity, level, flags, listener);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mImpl = new SystemUiHelperImplICS(activity, level, flags, listener);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mImpl = new SystemUiHelperImplHC(activity, level, flags, listener);
        } else {
            mImpl = new SystemUiHelperImplBase(activity, level, flags, listener);
        }
    }

    /**
     * @return true if the system UI is currently showing. What this means depends on the mode this
     *         {@link SystemUiHelper} was instantiated with.
     */
    public boolean isShowing() {
        return mImpl.isShowing();
    }

    /**
     * Show the system UI. What this means depends on the mode this {@link SystemUiHelper} was
     * instantiated with.
     *
     * <p>Any currently queued delayed hide requests will be removed.
     */
    public void show() {
        // Ensure that any currently queued hide calls are removed
        removeQueuedRunnables();

        mImpl.show();
    }

    /**
     * Hide the system UI. What this means depends on the mode this {@link SystemUiHelper} was
     * instantiated with.
     *
     * <p>Any currently queued delayed hide requests will be removed.
     */
    public void hide() {
        // Ensure that any currently queued hide calls are removed
        removeQueuedRunnables();

        mImpl.hide();
    }

    /**
     * Request that the system UI is hidden after a delay.
     *
     * <p>Any currently queued delayed hide requests will be removed.
     *
     * @param delayMillis The delay (in milliseconds) until the Runnable
     *        will be executed.
     */
    public void delayHide(long delayMillis) {
        // Ensure that any currently queued hide calls are removed
        removeQueuedRunnables();

        mHandler.postDelayed(mHideRunnable, delayMillis);
    }

    /**
     * Toggle whether the system UI is displayed.
     */
    public void toggle() {
        if (mImpl.isShowing()) {
            mImpl.hide();
        } else {
            mImpl.show();
        }
    }

    private void removeQueuedRunnables() {
        // Ensure that any currently queued hide calls are removed
        mHandler.removeCallbacks(mHideRunnable);
    }

    /**
     * A callback interface used to listen for system UI visibility changes.
     */
    public interface OnVisibilityChangeListener {
        /**
         * Called when the system UI visibility has changed.
         *
         * @param visible True if the system UI is visible.
         */
        public void onVisibilityChange(boolean visible);
    }

    static abstract class SystemUiHelperImpl {

        final Activity mActivity;
        final int mLevel;
        final int mFlags;
        final OnVisibilityChangeListener mOnVisibilityChangeListener;

        boolean mIsShowing = true;

        SystemUiHelperImpl(Activity activity, int level, int flags,
                OnVisibilityChangeListener onVisibilityChangeListener) {
            mActivity = activity;
            mLevel = level;
            mFlags = flags;
            mOnVisibilityChangeListener = onVisibilityChangeListener;
        }

        abstract void show();
        abstract void hide();

        boolean isShowing() {
            return mIsShowing;
        }

        void setIsShowing(boolean isShowing) {
            mIsShowing = isShowing;

            if (mOnVisibilityChangeListener != null) {
                mOnVisibilityChangeListener.onVisibilityChange(mIsShowing);
            }
        }
    }

    /**
     * Base implementation. Used on API level 10 and below.
     */
    static class SystemUiHelperImplBase extends SystemUiHelperImpl {

        SystemUiHelperImplBase(Activity activity, int level, int flags,
                OnVisibilityChangeListener onVisibilityChangeListener) {
            super(activity, level, flags, onVisibilityChangeListener);

            if ((mFlags & SystemUiHelper.FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES) != 0) {
                mActivity.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }

        @Override
        void show() {
            if (mLevel > SystemUiHelper.LEVEL_LOW_PROFILE) {
                mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setIsShowing(true);
            }
        }

        @Override
        void hide() {
            if (mLevel > SystemUiHelper.LEVEL_LOW_PROFILE) {
                mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setIsShowing(false);
            }
        }
    }

    private class HideRunnable implements Runnable {
        @Override
        public void run() {
            hide();
        }
    }

}
