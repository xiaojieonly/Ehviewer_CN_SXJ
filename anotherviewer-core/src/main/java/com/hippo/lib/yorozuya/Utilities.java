package com.hippo.lib.yorozuya;

public class Utilities {
    public static boolean contain(Object[] array, Object value) {
        if (array == null) return false;
        for (Object item : array) {
            if (item == null) {
                if (value == null) return true;
            } else if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
