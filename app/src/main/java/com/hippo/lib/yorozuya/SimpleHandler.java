/*
 * Copyright (C) 2015 Hippo Seven
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

package com.hippo.lib.yorozuya;

import android.os.Handler;
import android.os.Looper;

public final class SimpleHandler extends Handler {

    private static Handler sInstance;

    private SimpleHandler(Looper mainLooper) {
        super(mainLooper);
    }

    public static Handler getInstance() {
        if (sInstance == null) {
            sInstance = new Handler(Looper.getMainLooper());
        }
        return sInstance;
    }
}
