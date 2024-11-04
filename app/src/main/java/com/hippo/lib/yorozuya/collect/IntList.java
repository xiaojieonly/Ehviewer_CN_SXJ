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

package com.hippo.lib.yorozuya.collect;

import android.os.Parcel;
import android.os.Parcelable;

public class IntList implements Parcelable {

    public static final Creator<IntList> CREATOR = new Creator<IntList>() {

        @Override
        public IntList createFromParcel(Parcel source) {
            return new IntList(source);
        }

        @Override
        public IntList[] newArray(int size) {
            return new IntList[size];
        }
    };
    private static final int MIN_CAPACITY_INCREMENT = 12;
    private int[] mArray;
    private int mSize = 0;

    public IntList() {
        mArray = CollectionUtils.EMPTY_INT_ARRAY;
    }

    public IntList(int capacity) {
        mArray = new int[capacity];
    }

    protected IntList(Parcel in) {
        this(in.readInt());
        mSize = in.readInt();
        int s = mSize;
        int[] a = mArray;
        for (int i = 0; i < s; i++) {
            a[i] = in.readInt();
        }
    }

    static void throwIndexOutOfBoundsException(int index, int size) {
        throw new IndexOutOfBoundsException("Invalid index " + index + ", size is " + size);
    }

    private static int newCapacity(int currentCapacity) {
        int increment = (currentCapacity < (MIN_CAPACITY_INCREMENT / 2) ?
                MIN_CAPACITY_INCREMENT : currentCapacity >> 1);
        return currentCapacity + increment;
    }

    public void add(int value) {
        int[] a = mArray;
        int s = mSize;
        if (s == a.length) {
            int[] newArray = new int[s +
                    (s < (MIN_CAPACITY_INCREMENT / 2) ?
                            MIN_CAPACITY_INCREMENT : s >> 1)];
            System.arraycopy(a, 0, newArray, 0, s);
            mArray = a = newArray;
        }
        a[s] = value;
        mSize = s + 1;
    }

    public void add(int location, int value) throws IndexOutOfBoundsException {
        int[] a = mArray;
        int s = mSize;
        if (location > s || location < 0) {
            throwIndexOutOfBoundsException(location, s);
        }

        if (s < a.length) {
            System.arraycopy(a, location, a, location + 1, s - location);
        } else {
            // assert s == a.length;
            int[] newArray = new int[newCapacity(s)];
            System.arraycopy(a, 0, newArray, 0, location);
            System.arraycopy(a, location, newArray, location + 1, s - location);
            mArray = a = newArray;
        }
        a[location] = value;
        mSize = s + 1;
    }

    public void clear() {
        mSize = 0;
    }

    public boolean contains(int value) {
        int[] a = mArray;
        int s = mSize;
        for (int i = 0; i < s; i++) {
            if (a[i] == value) {
                return true;
            }
        }
        return false;
    }

    public int get(int location) {
        if (location >= mSize) {
            throwIndexOutOfBoundsException(location, mSize);
        }
        return mArray[location];
    }

    public int indexOf(int value) {
        int[] a = mArray;
        int s = mSize;
        for (int i = 0; i < s; i++) {
            if (a[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public boolean isEmpty() {
        return mSize == 0;
    }

    public int removeAt(int location) {
        int[] a = mArray;
        int s = mSize;
        if (location >= s) {
            throwIndexOutOfBoundsException(location, s);
        }
        int result = a[location];
        System.arraycopy(a, location + 1, a, location, --s - location);
        mSize = s;
        return result;
    }

    public boolean remove(int value) {
        int[] a = mArray;
        int s = mSize;
        for (int i = 0; i < s; i++) {
            if (a[i] == value) {
                System.arraycopy(a, i + 1, a, i, --s - i);
                mSize = s;
                return true;
            }
        }
        return false;
    }

    public int set(int location, int value) {
        int[] a = mArray;
        if (location >= mSize) {
            throwIndexOutOfBoundsException(location, mSize);
        }
        int result = a[location];
        a[location] = value;
        return result;
    }

    public int size() {
        return mSize;
    }

    public int[] getInternalArray() {
        return mArray;
    }

    @Override
    public String toString() {
        int[] a = mArray;
        if (a == null) {
            return "null";
        }
        if (mSize == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(mSize * 6);
        sb.append('[');
        sb.append(a[0]);
        for (int i = 1; i < mSize; i++) {
            sb.append(", ");
            sb.append(a[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int s = mSize;
        int[] a = mArray;
        dest.writeInt(a.length);
        dest.writeInt(s);
        for (int i = 0; i < s; i++) {
            dest.writeInt(a[i]);
        }
    }
}
