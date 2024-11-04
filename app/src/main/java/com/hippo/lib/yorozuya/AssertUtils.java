/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.yorozuya;

public final class AssertUtils {
    private AssertUtils() {
    }

    public static void assertTrue(boolean cond) {
        assertTrue("Condition has to be true.", cond);
    }

    public static void assertTrueEx(boolean cond) throws com.hippo.lib.yorozuya.AssertException {
        assertTrueEx("Condition has to be true.", cond);
    }

    public static void assertTrue(String message, boolean cond) {
        if (!cond) {
            throw new AssertError(message);
        }
    }

    public static void assertTrueEx(String message, boolean cond) throws com.hippo.lib.yorozuya.AssertException {
        if (!cond) {
            throw new com.hippo.lib.yorozuya.AssertException(message);
        }
    }

    public static void assertNull(Object object) {
        assertNull("Should be null", object);
    }

    public static void assertNullEx(Object object) throws com.hippo.lib.yorozuya.AssertException {
        assertNullEx("Should be null", object);
    }

    public static void assertNull(String message, Object object) {
        if (object != null) {
            throw new AssertError(message);
        }
    }

    public static void assertNullEx(String message, Object object) throws com.hippo.lib.yorozuya.AssertException {
        if (object != null) {
            throw new com.hippo.lib.yorozuya.AssertException(message);
        }
    }

    public static void assertNotNull(Object object) {
        assertNotNull("Should not be null", object);
    }

    public static void assertNotNullEx(Object object) throws com.hippo.lib.yorozuya.AssertException {
        assertNotNullEx("Should not be null", object);
    }

    public static void assertNotNull(String message, Object object) {
        if (object == null) {
            throw new AssertError(message);
        }
    }

    public static void assertNotNullEx(String message, Object object) throws com.hippo.lib.yorozuya.AssertException {
        if (object == null) {
            throw new com.hippo.lib.yorozuya.AssertException(message);
        }
    }

    public static void assertEquals(int expected, int actual) {
        assertEquals("Should be " + expected + ", but it is " + actual, expected, actual);
    }

    public static void assertEqualsEx(int expected, int actual) throws com.hippo.lib.yorozuya.AssertException {
        assertEqualsEx("Should be " + expected + ", but it is " + actual, expected, actual);
    }

    public static void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            throw new AssertError(message);
        }
    }

    public static void assertEqualsEx(String message, int expected, int actual) throws com.hippo.lib.yorozuya.AssertException {
        if (expected != actual) {
            throw new com.hippo.lib.yorozuya.AssertException(message);
        }
    }

    public static void assertInstanceOf(Object obj, Class clazz) {
        assertInstanceOf("The object should be instance of " + clazz.getName(), obj, clazz);
    }

    public static void assertInstanceOfEx(Object obj, Class clazz) throws com.hippo.lib.yorozuya.AssertException {
        assertInstanceOfEx("The object should be instance of " + clazz.getName(), obj, clazz);
    }

    public static void assertInstanceOf(String message, Object obj, Class clazz) {
        if (!clazz.isInstance(obj)) {
            throw new AssertError(message);
        }
    }

    public static void assertInstanceOfEx(String message, Object obj, Class clazz) throws com.hippo.lib.yorozuya.AssertException {
        if (!clazz.isInstance(obj)) {
            throw new AssertException(message);
        }
    }
}
