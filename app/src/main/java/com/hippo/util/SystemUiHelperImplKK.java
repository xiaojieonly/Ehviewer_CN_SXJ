/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.hippo.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.view.View;

@TargetApi(Build.VERSION_CODES.KITKAT)
class SystemUiHelperImplKK extends SystemUiHelperImplJB {

    SystemUiHelperImplKK(Activity activity, int level, int flags,
            SystemUiHelper.OnVisibilityChangeListener onVisibilityChangeListener) {
        super(activity, level, flags, onVisibilityChangeListener);
    }

    @Override
    protected int createHideFlags() {
        int flag = super.createHideFlags();

        if (mLevel == SystemUiHelper.LEVEL_IMMERSIVE) {
            // If the client requested immersive mode, and we're on Android 4.4
            // or later, add relevant flags. Applying HIDE_NAVIGATION without
            // IMMERSIVE prevents the activity from accepting all touch events,
            // so we only do this on Android 4.4 and later (where IMMERSIVE is
            // present).
            flag |= ((mFlags & SystemUiHelper.FLAG_IMMERSIVE_STICKY) != 0)
                            ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            : View.SYSTEM_UI_FLAG_IMMERSIVE;
        }

        return flag;
    }

}
