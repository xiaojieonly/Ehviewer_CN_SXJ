package com.hippo.util;

public class MutableBoolean {
    public boolean value;

    public MutableBoolean(boolean value) {
        this.value = value;
    }

    public boolean booleanValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}
