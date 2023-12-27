/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.lib.glgallery;

import android.view.MotionEvent;

class DownUpDetector {
    public interface DownUpListener {
        void onDown(MotionEvent e);
        void onUp(MotionEvent e);
        void onPointerDown(MotionEvent e);
        void onPointerUp(MotionEvent e);
    }

    private boolean mStillDown;
    private final DownUpListener mListener;

    public DownUpDetector(DownUpListener listener) {
        mListener = listener;
    }

    private void setState(boolean down, MotionEvent e) {
        if (down == mStillDown) return;
        mStillDown = down;
        if (down) {
            mListener.onDown(e);
        } else {
            mListener.onUp(e);
        }
    }

    public void onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setState(true, ev);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mListener.onPointerDown(ev);
                break;
            case MotionEvent.ACTION_UP:
                mListener.onPointerUp(ev);
                setState(false, ev);
                break;
            case MotionEvent.ACTION_CANCEL:
                setState(false, ev);
                break;
        }
    }

    public boolean isDown() {
        return mStillDown;
    }
}
