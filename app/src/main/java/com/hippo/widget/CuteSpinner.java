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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import androidx.appcompat.widget.AppCompatSpinner;
import com.hippo.ehviewer.R;

public class CuteSpinner extends AppCompatSpinner {

    public CuteSpinner(Context context) {
        super(context);
        init(context, null, androidx.appcompat.R.attr.spinnerStyle);
    }

    public CuteSpinner(Context context, int mode) {
        super(context, mode);
        init(context, null, androidx.appcompat.R.attr.spinnerStyle);
    }

    public CuteSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, androidx.appcompat.R.attr.spinnerStyle);
    }

    public CuteSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    public CuteSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);
        init(context, attrs, defStyleAttr);
    }

    public CuteSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode, Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, mode, popupTheme);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(attrs,
                androidx.appcompat.R.styleable.Spinner, defStyleAttr, 0);
        final CharSequence[] entries = a.getTextArray(R.styleable.Spinner_android_entries);
        if (entries != null) {
            final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(context,
                    R.layout.item_cute_spinner_item, entries);
            adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
            setAdapter(adapter);
        }
        a.recycle();
    }
}
