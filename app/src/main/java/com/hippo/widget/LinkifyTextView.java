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

package com.hippo.widget;

import android.content.Context;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.NonNull;

public class LinkifyTextView extends ObservedTextView {

    private ClickableSpan mCurrentSpan;

    public LinkifyTextView(Context context) {
        super(context);
    }

    public LinkifyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LinkifyTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClickableSpan getCurrentSpan() {
        return mCurrentSpan;
    }

    public void clearCurrentSpan() {
        mCurrentSpan = null;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // Let the parent or grandparent of TextView to handles click aciton.
        // Otherwise click effect like ripple will not work, and if touch area
        // do not contain a url, the TextView will still get MotionEvent.
        // onTouchEven must be called with MotionEvent.ACTION_DOWN for each touch
        // action on it, so we analyze touched url here.
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mCurrentSpan = null;

            if (getText() instanceof Spanned) {
                // Get this code from android.text.method.LinkMovementMethod.
                // Work fine !
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();

                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                if (null != layout) {
                    int line = layout.getLineForVertical(y);
                    int off = layout.getOffsetForHorizontal(line, x);

                    ClickableSpan[] spans = ((Spanned)getText()).getSpans(off, off, ClickableSpan.class);

                    if (spans.length > 0) {
                        mCurrentSpan = spans[0];
                    }
                }
            }
        }

        return super.onTouchEvent(event);
    }
}
