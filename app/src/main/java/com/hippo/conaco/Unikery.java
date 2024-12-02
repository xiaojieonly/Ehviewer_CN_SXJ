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

package com.hippo.conaco;

import androidx.annotation.NonNull;

public interface Unikery<V> {

    int INVALID_ID = -1;

    /**
     * Store the conaco task id
     *
     * @param id the conaco task id
     */
    void setTaskId(int id);

    /**
     * Get the conaco task id
     *
     * @return the conaco task id
     */
    int getTaskId();

    /**
     * On miss in the source
     */
    void onMiss(@Conaco.Source int source);

    /**
     * On start http request
     */
    void onRequest();

    /**
     * On http request progress
     */
    void onProgress(long singleReceivedSize, long receivedSize, long totalSize);

    /**
     * On start to wait because repeated key
     */
    void onWait();

    /**
     * On get the value
     *
     * @return {@code true} for the {@code Unikery} accepts the value
     *          and the task ends. {@code false} for the {@code Unikery}
     *          reject the value and the task goes on or failed
     */
    boolean onGetValue(@NonNull V value, @Conaco.Source int source);

    /**
     * On failed to get value
     */
    void onFailure();

    /**
     * On user cancel the conaco task
     */
    void onCancel();
}
