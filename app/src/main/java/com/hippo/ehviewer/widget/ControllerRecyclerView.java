/*
 * Copyright 2026 antigone
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
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.hippo.easyrecyclerview.EasyRecyclerView;

/** Keeps native card navigation and handles a direction pressed at a list boundary. */
public class ControllerRecyclerView extends EasyRecyclerView {
    public interface OnBoundaryListener {
        boolean onBoundary(int direction);
    }

    @Nullable
    private OnBoundaryListener mOnBoundaryListener;
    @Nullable
    private View mLeftFocusView;
    private int mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int mRestoreFocusPosition = NO_POSITION;

    public ControllerRecyclerView(Context context) {
        super(context);
    }

    public ControllerRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ControllerRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnBoundaryListener(@Nullable OnBoundaryListener listener) {
        mOnBoundaryListener = listener;
    }

    public void setLeftFocusView(@Nullable View view) {
        mLeftFocusView = view;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (direction == FOCUS_LEFT && mLeftFocusView != null && mLeftFocusView.isShown()
                && mLeftFocusView.isFocusable() && findContainingItemView(focused) != null
                && FocusFinder.getInstance().findNextFocus(this, focused, direction) == null) {
            // Toolbar buttons can sit above, rather than geometrically left of, the first column.
            return mLeftFocusView;
        }
        return super.focusSearch(focused, direction);
    }

    public void restoreItemFocusAfterLayout(int position) {
        mRestoreFocusPosition = position;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mRestoreFocusPosition != NO_POSITION && !hasPendingAdapterUpdates()) {
            int position = mRestoreFocusPosition;
            mRestoreFocusPosition = NO_POSITION;
            if (hasWindowFocus() && !isInTouchMode()) {
                ViewHolder holder = findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    holder = findViewHolderForAdapterPosition(0);
                }
                if (holder != null) {
                    holder.itemView.requestFocus();
                }
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == mConsumedKeyCode && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            // Touch or a dialog may have redirected the previous key's release.
            mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        }
        if (mConsumedKeyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode == mConsumedKeyCode) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            }
            // A held direction must not start another request when loading finishes.
            return true;
        }

        if (mOnBoundaryListener != null && event.getAction() == KeyEvent.ACTION_DOWN
                && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                && !hasPendingAdapterUpdates()) {
            View focused = findFocus();
            View item = focused != null ? findContainingItemView(focused) : null;
            int direction = keyCode == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN;
            if (item != null && getChildAdapterPosition(item) != NO_POSITION
                    && isAtBoundary(direction)
                    && FocusFinder.getInstance().findNextFocus(this, focused, direction) == null
                    && mOnBoundaryListener.onBoundary(direction)) {
                mConsumedKeyCode = keyCode;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isAtBoundary(int direction) {
        if (!canScrollVertically(direction == View.FOCUS_UP ? -1 : 1)) {
            return true;
        }
        // Search-bar/FAB padding can still scroll after the terminal row is reached.
        Adapter<?> adapter = getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) {
            return false;
        }
        int position = direction == View.FOCUS_UP ? 0 : adapter.getItemCount() - 1;
        return findViewHolderForAdapterPosition(position) != null;
    }

    @Override
    protected void onDetachedFromWindow() {
        mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        mRestoreFocusPosition = NO_POSITION;
        super.onDetachedFromWindow();
    }
}
