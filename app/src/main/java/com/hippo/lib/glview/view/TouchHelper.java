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

package com.hippo.lib.glview.view;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import com.hippo.lib.yorozuya.LayoutUtils;
import com.hippo.lib.yorozuya.SimpleHandler;

public class TouchHelper {

    private final TouchOwner mOwner;

    private boolean mPrePressed = false;
    private boolean mHasPerformedLongPress = false;

    private CheckForLongPress mPendingCheckForLongPress = null;
    private CheckForTap mPendingCheckForTap = null;
    private PerformClick mPerformClick = null;
    private UnsetPressedState mUnsetPressedState = null;

    /**
     * Cache the touch slop from the context that created the view.
     */
    private static int sTouchSlop = 8;

    public static void initialize(Context context) {
        sTouchSlop = LayoutUtils.dp2pix(context, 8); // 8dp
    }

    public TouchHelper(@NonNull TouchOwner owner) {
        mOwner = owner;
    }

    public boolean onTouch(MotionEvent event) {
        TouchOwner owner = mOwner;
        final float x = event.getX();
        final float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE)
            owner.setHotspot(x, y);

        if (!owner.isEnabled()) {
            if (event.getAction() == MotionEvent.ACTION_UP && owner.isPressed()) {
                owner.setPressed(false);
            }
            // A disabled view that is clickable still consumes the touch
            // events, it just doesn't respond to them.
            return (owner.isClickable() || owner.isLongClickable());
        }

        if (owner.isClickable() || owner.isLongClickable()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    if (owner.isPressed() || mPrePressed) {
                        if (mPrePressed) {
                            // The button is being released before we actually
                            // showed it as pressed.  Make it show the pressed
                            // state now (before scheduling the click) to ensure
                            // the user sees it.
                            setPressed(true, x, y);
                        }

                        if (!mHasPerformedLongPress) {
                            // This is a tap, so remove the longpress check
                            removeLongPressCallback();

                            // Use a Runnable and post this rather than calling
                            // performClick directly. This lets other visual state
                            // of the view update before click actions start.
                            if (mPerformClick == null) {
                                mPerformClick = new PerformClick();
                            }
                            if (!SimpleHandler.getInstance().post(mPerformClick)) {
                                owner.performClick();
                            }
                        }

                        if (mUnsetPressedState == null) {
                            mUnsetPressedState = new UnsetPressedState();
                        }

                        if (mPrePressed) {
                            SimpleHandler.getInstance().postDelayed(mUnsetPressedState,
                                    ViewConfiguration.getPressedStateDuration());
                        } else if (!SimpleHandler.getInstance().post(mUnsetPressedState)) {
                            // If the post failed, unpress right now
                            mUnsetPressedState.run();
                        }

                        removeTapCallback();
                    }
                    break;

                case MotionEvent.ACTION_DOWN:
                    mHasPerformedLongPress = false;

                    mPrePressed = true;
                    if (mPendingCheckForTap == null) {
                        mPendingCheckForTap = new CheckForTap();
                    }
                    mPendingCheckForTap.x = event.getX();
                    mPendingCheckForTap.y = event.getY();
                    SimpleHandler.getInstance().postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                    break;

                case MotionEvent.ACTION_CANCEL:
                    owner.setPressed(false);
                    removeTapCallback();
                    removeLongPressCallback();
                    break;

                case MotionEvent.ACTION_MOVE:
                    owner.setHotspot(x, y);

                    // Be lenient about moving outside of buttons
                    if (!pointInView(x, y, sTouchSlop)) {
                        // Outside button
                        removeTapCallback();
                        if (owner.isPressed()) {
                            // Remove any future long press/tap checks
                            removeLongPressCallback();

                            owner.setPressed(false);
                        }
                    }
                    break;
            }

            return true;
        }

        return false;
    }

    private void setPressed(boolean pressed, float x, float y){
        mOwner.setPressed(pressed);
        if (pressed) {
            mOwner.setHotspot(x, y);
        }
    }

    /**
     * Utility method to determine whether the given point, in local coordinates,
     * is inside the view, where the area of the view is expanded by the slop factor.
     * This method is called while processing touch-move events to determine if the event
     * is still within the view.
     */
    public boolean pointInView(float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < (mOwner.getWidth() + slop) &&
                localY < (mOwner.getHeight() + slop);
    }

    /**
     * Remove the longpress detection timer.
     */
    private void removeLongPressCallback() {
        if (mPendingCheckForLongPress != null) {
            SimpleHandler.getInstance().removeCallbacks(mPendingCheckForLongPress);
        }
    }

    /**
     * Remove the tap detection timer.
     */
    private void removeTapCallback() {
        if (mPendingCheckForTap != null) {
            mPrePressed = false;
            SimpleHandler.getInstance().removeCallbacks(mPendingCheckForTap);
        }
    }

    private void checkForLongClick(int delayOffset) {
        if (mOwner.isLongClickable()) {
            mHasPerformedLongPress = false;

            if (mPendingCheckForLongPress == null) {
                mPendingCheckForLongPress = new CheckForLongPress();
            }
            SimpleHandler.getInstance().postDelayed(mPendingCheckForLongPress,
                    ViewConfiguration.getLongPressTimeout() - delayOffset);
        }
    }

    private final class CheckForLongPress implements Runnable {

        @Override
        public void run() {
            if (mOwner.isPressed()) {
                if (mOwner.performLongClick()) {
                    mHasPerformedLongPress = true;
                }
            }
        }
    }

    private final class CheckForTap implements Runnable {
        public float x;
        public float y;

        @Override
        public void run() {
            mPrePressed = false;
            setPressed(true, x, y);
            checkForLongClick(ViewConfiguration.getTapTimeout());
        }
    }

    private final class PerformClick implements Runnable {

        @Override
        public void run() {
            mOwner.performClick();
        }
    }

    private final class UnsetPressedState implements Runnable {

        @Override
        public void run() {
            mOwner.setPressed(false);
        }
    }
}
