
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.hippo.lib.glview.view;

import android.os.SystemClock;

//
// The animation time should ideally be the vsync time the frame will be
// displayed, but that is an unknown time in the future. So we use the system
// time just after eglSwapBuffers (when GLSurfaceView.onDrawFrame is called)
// as a approximation.
//
public class AnimationTime {
    private static volatile long sTime;

    // Sets current time as the animation time.
    public static void update() {
        sTime = SystemClock.uptimeMillis();
    }

    // Returns the animation time.
    public static long get() {
        return sTime;
    }

    public static long startTime() {
        sTime = SystemClock.uptimeMillis();
        return sTime;
    }
}
