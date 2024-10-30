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

public class Pool<T> {

    private final T[] mArray;
    private final int mMaxSize;
    private int mSize;

    @SuppressWarnings("unchecked")
    public Pool(int size) {
        if (size <= 0) {
            throw new IllegalStateException("Pool size must > 0, it is " + size);
        }
        mArray = (T[]) new Object[size];
        mMaxSize = size;
        mSize = 0;
    }

    public void push(T t) {
        if (t != null && mSize < mMaxSize) {
            mArray[mSize++] = t;
        }
    }

    public T pop() {
        if (mSize > 0) {
            T t = mArray[--mSize];
            mArray[mSize] = null;
            return t;
        } else {
            return null;
        }
    }
}
