/*
 * Copyright 2025 Hippo Seven
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

package com.hippo.ehviewer.preference;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import androidx.preference.Preference;
import com.hippo.ehviewer.ui.DatabaseViewerActivity;

/**
 * 数据库查看Preference
 */
public class DatabaseViewerPreference extends Preference {

    public DatabaseViewerPreference(Context context) {
        super(context);
    }

    public DatabaseViewerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DatabaseViewerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        super.onClick();
        Context context = getContext();
        Intent intent = new Intent(context, DatabaseViewerActivity.class);
        context.startActivity(intent);
    }
}
