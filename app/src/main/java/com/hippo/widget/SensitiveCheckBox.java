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
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatCheckBox;

public class SensitiveCheckBox extends AppCompatCheckBox {

    private boolean mFromUser;

    private OnCheckedChangeListener mOnCheckedChangeListener;

    public SensitiveCheckBox(Context context) {
        super(context);
    }

    public SensitiveCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SensitiveCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean performClick() {
        boolean result;

        mFromUser = true;
        result = super.performClick();
        mFromUser = false;

        return result;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mOnCheckedChangeListener = listener;
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);

        if (mOnCheckedChangeListener != null) {
            mOnCheckedChangeListener.onCheckedChanged(this, checked, mFromUser);
        }
    }

    /**
     * Interface definition for a callback to be invoked when the checked state
     * of a compound button changed.
     */
    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a compound button has changed.
         *
         * @param view The sensitive check box view whose state has changed.
         * @param isChecked  The new checked state of buttonView.
         * @param fromUser True if the rating change was initiated by a user's
         *            touch gesture.
         */
        void onCheckedChanged(SensitiveCheckBox view, boolean isChecked, boolean fromUser);
    }
}
