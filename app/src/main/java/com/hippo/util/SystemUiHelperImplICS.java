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

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class SystemUiHelperImplICS extends SystemUiHelperImplHC {

    SystemUiHelperImplICS(Activity activity, int level, int flags,
            SystemUiHelper.OnVisibilityChangeListener onVisibilityChangeListener) {
        super(activity, level, flags, onVisibilityChangeListener);
    }

    @Override
    protected int createShowFlags() {
        return View.SYSTEM_UI_FLAG_VISIBLE;
    }

    @Override
    protected int createTestFlags() {
        if (mLevel >= SystemUiHelper.LEVEL_LEAN_BACK) {
            // Intentionally override test flags.
            return View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        return View.SYSTEM_UI_FLAG_LOW_PROFILE;
    }

    @Override
    protected int createHideFlags() {
        int flag = View.SYSTEM_UI_FLAG_LOW_PROFILE;

        if (mLevel >= SystemUiHelper.LEVEL_LEAN_BACK) {
            flag |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        return flag;
    }
}
