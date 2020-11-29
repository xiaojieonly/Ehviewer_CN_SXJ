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

package com.hippo.widget;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class ObservedTextView extends AppCompatTextView {

    private OnWindowAttachListener mOnWindowAttachListener;

    public ObservedTextView(Context context) {
        super(context);
    }

    public ObservedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ObservedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mOnWindowAttachListener != null) {
            mOnWindowAttachListener.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mOnWindowAttachListener != null) {
            mOnWindowAttachListener.onDetachedFromWindow();
        }
    }

    public void setOnWindowAttachListener(OnWindowAttachListener onWindowAttachListener) {
        mOnWindowAttachListener = onWindowAttachListener;
    }

    public interface OnWindowAttachListener {
        void onAttachedToWindow();
        void onDetachedFromWindow();
    }
}
