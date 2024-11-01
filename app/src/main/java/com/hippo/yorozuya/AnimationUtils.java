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

package com.hippo.yorozuya;

import android.view.animation.Interpolator;

import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

public final class AnimationUtils {
    public static final Interpolator FAST_SLOW_INTERPOLATOR = new LinearOutSlowInInterpolator();
    public static final Interpolator SLOW_FAST_INTERPOLATOR = new FastOutLinearInInterpolator();
    public static final Interpolator SLOW_FAST_SLOW_INTERPOLATOR = new FastOutSlowInInterpolator();

    private AnimationUtils() {
    }
}
