package com.hippo.lib.yorozuya;

public class AssertUtils {
    public static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + ", got: " + actual);
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", got: " + actual);
        }
    }

    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) { super(msg); }
    }
}
