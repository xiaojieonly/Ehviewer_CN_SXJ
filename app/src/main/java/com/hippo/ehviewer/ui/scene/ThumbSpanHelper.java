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

package com.hippo.ehviewer.ui.scene;

import android.util.Log;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.Arrays;
import java.util.List;

// TODO GridLayoutManager do not work at all ! SpaceGroupIndex is useless for layout! I need to create one by myself
public class ThumbSpanHelper {

    private static final int MIN_ARRAY_LENGTH = 50;
    private static final int MAX_ROW_INTERVAL = 3;

    private final List<GalleryInfo> mData;

    // true for occupied, false for free
    private boolean[] mCells = new boolean[MIN_ARRAY_LENGTH];

    private int mNextIndex;
    private int mNextGroupIndex;
    private int mNearSpaceIndex;
    private int mNearSpaceGroupIndex;
    private int mAttachedCount;

    private int mSpanCount;

    private boolean mEnable;

    public ThumbSpanHelper(List<GalleryInfo> data) {
        mData = data;
    }

    public void setEnable(boolean enable) {
        if (enable == mEnable) {
            return;
        }
        mEnable = enable;

        if (!enable) {
            clear();
            mSpanCount = 0;
        } else {
            if (mSpanCount > 0) {
                rebuild();
            }
        }
    }

    public void setSpanCount(int spanCount) {
        if (!mEnable) {
            return;
        }

        if (spanCount == mSpanCount) {
            return;
        }
        mSpanCount = spanCount;
        if (spanCount > 0) {
            rebuild();
        }
    }

    public void notifyDataSetChanged() {
        if (!mEnable || mSpanCount <= 0) {
            return;
        }
        rebuild();
    }

    public void notifyItemRangeRemoved(int positionStart, int itemCount) {
        if (!mEnable || mSpanCount <= 0) {
            return;
        }
        rebuild();
    }

    protected void notifyItemRangeInserted(int positionStart, int itemCount) {
        if (!mEnable || mSpanCount <= 0) {
            return;
        }
        if (mAttachedCount == positionStart) {
            append();
        } else {
            rebuild();
        }
    }

    private void clear() {

        Log.d("TAG", "=======================clear=======================");

        if (mSpanCount > 0) {
            if (mNextGroupIndex > 0 || mNextIndex > 0) {
                Arrays.fill(mCells, 0, mNextGroupIndex * mSpanCount + mNextIndex, false);
            }
        } else {
            Arrays.fill(mCells, false);
        }
        mNextIndex = 0;
        mNextGroupIndex = 0;
        mNearSpaceIndex = 0;
        mNearSpaceGroupIndex = 0;
        mAttachedCount = 0;
    }

    private void rebuild() {
        clear();
        append();
    }

    private void append() {
        if (1 == mSpanCount) {
            for (int i = mAttachedCount, n = mData.size(); i < n; i++) {
                GalleryInfo gi = mData.get(i);
                gi.spanSize = 1;
                gi.spanGroupIndex = i;
                gi.spanIndex = 0;
            }
            mAttachedCount = mData.size();
        } else if (mSpanCount >= 2) {
            for (int i = mAttachedCount, n = mData.size(); i < n; i++) {
                GalleryInfo gi = mData.get(i);
                int spanSize = gi.thumbWidth <= gi.thumbHeight ? 1 : 2;
                gi.spanSize = spanSize;

                if (1 == spanSize) {
                    // Update near space
                    updateNearSpace();

                    Log.d("TAG", "Update mNearSpaceGroupIndex = " + mNearSpaceGroupIndex + ", mNearSpaceIndex = " + mNearSpaceIndex);

                    if (mNextIndex == mNearSpaceIndex && mNextGroupIndex == mNearSpaceGroupIndex) {
                        // No space, just append
                        gi.spanIndex = mNextIndex;
                        gi.spanGroupIndex = mNextGroupIndex;

                        // Update cell
                        int start = gi.spanGroupIndex * mSpanCount + gi.spanIndex;
                        fillCell(start, start + 1);

                        // Update field
                        mNextIndex++;
                        if (mSpanCount == mNextIndex) {
                            mNextIndex = 0;
                            mNextGroupIndex++;
                        }
                        mNearSpaceIndex = mNextIndex;
                        mNearSpaceGroupIndex = mNextGroupIndex;

                        Log.d("TAG", "type 0");
                        Log.d("TAG", "i = " + i + ", spanSize = " + spanSize + ", spanGroupIndex = " + gi.spanGroupIndex + ", spanIndex = " + gi.spanIndex);
                        Log.d("TAG", "mNextGroupIndex = " + mNextGroupIndex + ", mNextIndex = " + mNextIndex);
                        Log.d("TAG", "mNearSpaceGroupIndex = " + mNearSpaceGroupIndex + ", mNearSpaceIndex = " + mNearSpaceIndex);

                    } else {
                        // Found space
                        gi.spanIndex = mNearSpaceIndex;
                        gi.spanGroupIndex = mNearSpaceGroupIndex;

                        // Update cell
                        int start = gi.spanGroupIndex * mSpanCount + gi.spanIndex;
                        fillCell(start, start + 1);

                        // Find near space
                        findNearSpace(start + 1);

                        Log.d("TAG", "type 1");
                        Log.d("TAG", "i = " + i + ", spanSize = " + spanSize + ", spanGroupIndex = " + gi.spanGroupIndex + ", spanIndex = " + gi.spanIndex);
                        Log.d("TAG", "mNextGroupIndex = " + mNextGroupIndex + ", mNextIndex = " + mNextIndex);
                        Log.d("TAG", "mNearSpaceGroupIndex = " + mNearSpaceGroupIndex + ", mNearSpaceIndex = " + mNearSpaceIndex);
                    }
                } else {
                    boolean syncNear = mNextIndex == mNearSpaceIndex && mNextGroupIndex == mNearSpaceGroupIndex;
                    // false for old, true for new
                    boolean oldOrNew;
                    if (mSpanCount - mNextIndex >= 2) {
                        gi.spanIndex = mNextIndex;
                        gi.spanGroupIndex = mNextGroupIndex;
                        oldOrNew = true;
                    } else {
                        // Go to next row
                        gi.spanIndex = 0;
                        gi.spanGroupIndex = mNextGroupIndex + 1;
                        oldOrNew = false;
                    }

                    // Update cell
                    int start = gi.spanGroupIndex * mSpanCount + gi.spanIndex;
                    fillCell(start, start + 2);

                    // Update field
                    if (syncNear && !oldOrNew) {
                        mNearSpaceIndex = mNextIndex;
                        mNearSpaceGroupIndex = mNextGroupIndex;
                    }
                    mNextIndex = gi.spanIndex + 2;
                    mNextGroupIndex = gi.spanGroupIndex;
                    if (mSpanCount == mNextIndex) {
                        mNextIndex = 0;
                        mNextGroupIndex++;
                    }
                    if (syncNear && oldOrNew) {
                        mNearSpaceIndex = mNextIndex;
                        mNearSpaceGroupIndex = mNextGroupIndex;
                    }

                    Log.d("TAG", "type 2");
                    Log.d("TAG", "i = " + i + ", spanSize = " + spanSize + ", spanGroupIndex = " + gi.spanGroupIndex + ", spanIndex = " + gi.spanIndex);
                    Log.d("TAG", "mNextGroupIndex = " + mNextGroupIndex + ", mNextIndex = " + mNextIndex);
                    Log.d("TAG", "mNearSpaceGroupIndex = " + mNearSpaceGroupIndex + ", mNearSpaceIndex = " + mNearSpaceIndex);
                }
            }
            mAttachedCount = mData.size();
        }
    }

    private void updateNearSpace() {
        // Check is space is near enough
        if (mNextGroupIndex - mNearSpaceGroupIndex <= MAX_ROW_INTERVAL) {
            return;
        }

        // The space is too far, find a near one
        int start = Math.max(0, mNextGroupIndex - MAX_ROW_INTERVAL) * mSpanCount;
        findNearSpace(start);
    }

    private void findNearSpace(int start) {
        int end = mNextGroupIndex * mSpanCount + mNextIndex;
        boolean[] cells = mCells;
        for (int i = start; i < end; i++) {
            if (!cells[i]) {
                // Find space !
                mNearSpaceIndex = i % mSpanCount;
                mNearSpaceGroupIndex = i / mSpanCount;
                return;
            }
        }
        // Can't find space
        mNearSpaceIndex = mNextIndex;
        mNearSpaceGroupIndex = mNextGroupIndex;
    }

    private void fillCell(int start, int end) {
        boolean[] array = mCells;
        // Avoid IndexOutOfBoundsException
        if (end >= array.length) {
            boolean[] newArray = new boolean[end + (end < (MIN_ARRAY_LENGTH / 2) ? MIN_ARRAY_LENGTH : end >> 1)];
            System.arraycopy(array, 0, newArray, 0, array.length);
            mCells = array = newArray;
        }
        Arrays.fill(array, start, end, true);
    }
}
