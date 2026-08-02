package com.hippo.lib.yorozuya.collect;

public class LongList {

    private static final int MIN_CAPACITY_INCREMENT = 12;
    private long[] mArray;
    private int mSize = 0;

    public LongList() {
        mArray = new long[0];
    }

    public LongList(int capacity) {
        mArray = new long[capacity];
    }

    static void throwIndexOutOfBoundsException(int index, int size) {
        throw new IndexOutOfBoundsException("Invalid index " + index + ", size is " + size);
    }

    private static int newCapacity(int currentCapacity) {
        int increment = (currentCapacity < (MIN_CAPACITY_INCREMENT / 2) ?
                MIN_CAPACITY_INCREMENT : currentCapacity >> 1);
        return currentCapacity + increment;
    }

    public void add(long value) {
        long[] a = mArray;
        int s = mSize;
        if (s == a.length) {
            long[] newArray = new long[s +
                    (s < (MIN_CAPACITY_INCREMENT / 2) ?
                            MIN_CAPACITY_INCREMENT : s >> 1)];
            System.arraycopy(a, 0, newArray, 0, s);
            mArray = a = newArray;
        }
        a[s] = value;
        mSize = s + 1;
    }

    public void add(int location, long value) throws IndexOutOfBoundsException {
        long[] a = mArray;
        int s = mSize;
        if (location > s || location < 0) {
            throwIndexOutOfBoundsException(location, s);
        }

        if (s < a.length) {
            System.arraycopy(a, location, a, location + 1, s - location);
        } else {
            long[] newArray = new long[newCapacity(s)];
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

    public boolean contains(long value) {
        long[] a = mArray;
        int s = mSize;
        for (int i = 0; i < s; i++) {
            if (a[i] == value) {
                return true;
            }
        }
        return false;
    }

    public long get(int location) {
        if (location >= mSize) {
            throwIndexOutOfBoundsException(location, mSize);
        }
        return mArray[location];
    }

    public int indexOf(long value) {
        long[] a = mArray;
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

    public long removeAt(int location) {
        long[] a = mArray;
        int s = mSize;
        if (location >= s) {
            throwIndexOutOfBoundsException(location, s);
        }
        long result = a[location];
        System.arraycopy(a, location + 1, a, location, --s - location);
        mSize = s;
        return result;
    }

    public boolean remove(long value) {
        long[] a = mArray;
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

    public long set(int location, long value) {
        long[] a = mArray;
        if (location >= mSize) {
            throwIndexOutOfBoundsException(location, mSize);
        }
        long result = a[location];
        a[location] = value;
        return result;
    }

    public int size() {
        return mSize;
    }

    public long[] getInternalArray() {
        return mArray;
    }

    @Override
    public String toString() {
        long[] a = mArray;
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
}
