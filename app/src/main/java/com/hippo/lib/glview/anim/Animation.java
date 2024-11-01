/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.hippo.lib.glview.anim;

import android.os.SystemClock;
import android.view.animation.Interpolator;

import com.hippo.yorozuya.MathUtils;

// Animation calculates a value according to the current input time.
//
// 1. First we need to use setDuration(int) to set the duration of the
//    animation. The duration is in milliseconds.
// 2. Then we should call start(). The actual start time is the first value
//    passed to calculate(long).
// 3. Each time we want to get an animation value, we call
//    calculate(long currentTimeMillis) to ask the Animation to calculate it.
//    The parameter passed to calculate(long) should be nonnegative.
// 4. Use get() to get that value.
//
// In step 3, onCalculate(float progress) is called so subclasses can calculate
// the value according to progress (progress is a value in [0,1]).
//
// Before onCalculate(float) is called, There is an optional interpolator which
// can change the progress value. The interpolator can be set by
// setInterpolator(Interpolator). If the interpolator is used, the value passed
// to onCalculate may be (for example, the overshoot effect).
//
// The isActive() method returns true after the animation start() is called and
// before calculate is passed a value which reaches the duration of the
// animation.
//
// The start() method can be called again to restart the Animation.
//
abstract public class Animation {
    private static final long ANIMATION_START = -1;
    private static final long NO_ANIMATION = -2;

    /**
     * When the animation reaches the end and <code>repeatCount</code> is INFINITE
     * or a positive value, the animation restarts from the beginning.
     */
    public static final int RESTART = 1;
    /**
     * When the animation reaches the end and <code>repeatCount</code> is INFINITE
     * or a positive value, the animation reverses direction on every iteration.
     */
    public static final int REVERSE = 2;
    /**
     * This value used used with the {@link #setRepeatCount(int)} property to repeat
     * the animation indefinitely.
     */
    public static final int INFINITE = -1;

    private long mStartTime = NO_ANIMATION;
    private long mDuration;
    private Interpolator mInterpolator;
    private int mRepeatCount;
    private int mRepeatMode;

    private int mRunnedCount;
    private long mLastFrameTime;

    public void setInterpolator(Interpolator interpolator) {
        mInterpolator = interpolator;
    }

    public void setDuration(long duration) {
        mDuration = duration;
    }

    public void setRepeatCount(int repeatCount) {
        mRepeatCount = repeatCount;
    }

    public void setRepeatMode(int repeatMode) {
        mRepeatMode = repeatMode;
    }

    public boolean isRunning() {
        return mStartTime > 0 || mStartTime == ANIMATION_START;
    }

    public long getLastFrameTime() {
        return mLastFrameTime;
    }

    public void start() {
        if (mStartTime == NO_ANIMATION) {
            mStartTime = ANIMATION_START;
            mRunnedCount = 0;
            mLastFrameTime = 0;
        }
    }

    public void startAt(long time) {
        start();
        setStartTime(time);
    }

    public void startNow() {
        start();
        setStartTime(SystemClock.uptimeMillis());
    }

    public void setStartTime(long time) {
        mStartTime = time;
    }

    public void cancel() {
        mStartTime = NO_ANIMATION;
    }

    public void reset() {
        mStartTime = ANIMATION_START;
        mRunnedCount = 0;
        mLastFrameTime = 0;
    }

    // TODO mRepeatMode
    public boolean calculate(long currentTimeMillis) {
        if (mStartTime == NO_ANIMATION) {
            return false;
        }
        if (mStartTime == ANIMATION_START) {
            mStartTime = currentTimeMillis;
        }
        mLastFrameTime = currentTimeMillis;

        long elapse = currentTimeMillis - mStartTime;
        float x = MathUtils.clamp(mDuration == 0.0f ? 1.0f : (float) elapse / mDuration, 0.0f, 1.0f); // Avoid NaN
        Interpolator i = mInterpolator;
        onCalculate(i != null ? i.getInterpolation(x) : x);

        // It is ok to call cancel() in onCalculate()
        if (mStartTime != NO_ANIMATION && elapse >= mDuration) {
            mRunnedCount++;
            if (mRunnedCount >= mRepeatCount && mRepeatCount != INFINITE) {
                onFinish();
                mStartTime = NO_ANIMATION;
            } else {
                mStartTime += elapse;
            }
        }

        return mStartTime != NO_ANIMATION;
    }

    abstract protected void onCalculate(float progress);

    protected void onFinish() {
    }
}
