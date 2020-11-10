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

package com.hippo.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;
import com.hippo.ehviewer.R;
import com.hippo.util.ExceptionUtils;

public class ActivityPreference extends Preference {

    private static final String TAG = ActivityPreference.class.getSimpleName();

    private Class<?> mActivityClazz;

    public ActivityPreference(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public ActivityPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public ActivityPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ActivityPreference, defStyleAttr, defStyleRes);
        String clazzName = a.getString(R.styleable.ActivityPreference_activity);
        if (null != clazzName) {
            try {
                mActivityClazz = Class.forName(clazzName);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Can't find class " + clazzName, e);
            }
        }
        a.recycle();
    }

    @Override
    protected void onClick() {
        if (null == mActivityClazz) {
            return;
        }

        Context context = getContext();
        Intent intent = new Intent(context, mActivityClazz);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(context, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }
}
