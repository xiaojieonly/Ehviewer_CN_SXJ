/*
 * Copyright 2015-2016 Hippo Seven
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

package com.hippo.conaco;

import androidx.annotation.Nullable;

import com.hippo.streampipe.InputStreamPipe;

import java.io.InputStream;

public interface DataContainer {

    /**
     * Returns the enabled status for this DataContainer.
     */
    boolean isEnabled();

    /**
     * Get 304 or something like that
     */
    void onUrlMoved(String requestUrl, String responseUrl);

    /**
     * Save the {@code InputStream}
     */
    boolean save(InputStream is, long length, @Nullable String mediaType, @Nullable ProgressNotifier notify);

    /**
     * Get {@code InputStreamPipe} for saved before
     */
    @Nullable
    InputStreamPipe get();

    /**
     * Remove saved stuff
     */
    void remove();
}
