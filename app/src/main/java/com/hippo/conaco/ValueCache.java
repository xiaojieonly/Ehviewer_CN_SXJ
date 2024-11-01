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

package com.hippo.conaco;

import androidx.annotation.NonNull;

import com.hippo.beerbelly.BeerBelly;
import com.hippo.streampipe.InputStreamPipe;

import java.io.OutputStream;

class ValueCache<V> extends BeerBelly<V> {

    private final ValueHelper<V> mHelper;

    public ValueCache(BeerBellyParams params, ValueHelper<V> helper) {
        super(params);
        mHelper = helper;
    }

    @Override
    protected int sizeOf(String key, V value) {
        return mHelper.sizeOf(key, value);
    }

    @Override
    protected void memoryEntryAdded(V value) {
        mHelper.onAddToMemoryCache(value);
    }

    @Override
    protected void memoryEntryRemoved(boolean evicted, String key, V oldValue, V newValue) {
        if (oldValue != null) {
            mHelper.onRemoveFromMemoryCache(key, oldValue);
        }
    }

    @Override
    protected V read(@NonNull InputStreamPipe isPipe) {
        return mHelper.decode(isPipe);
    }

    @Override
    protected boolean write(OutputStream os, V value) {
        throw new UnsupportedOperationException("Not support write object");
    }
}
