package com.hippo.ehviewer.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class TextUtilTest {
    @Test
    public void testIsEmpty() {
        assertTrue(TextUtil.isEmpty(null));
        assertTrue(TextUtil.isEmpty(""));
        assertFalse(TextUtil.isEmpty("hello"));
    }

    @Test
    public void testEquals() {
        assertTrue(TextUtil.equals(null, null));
        assertTrue(TextUtil.equals("", ""));
        assertFalse(TextUtil.equals("a", "b"));
        assertFalse(TextUtil.equals(null, "a"));
    }

    @Test
    public void testIsBlank() {
        assertTrue(TextUtil.isBlank(null));
        assertTrue(TextUtil.isBlank(""));
        assertTrue(TextUtil.isBlank("   "));
        assertFalse(TextUtil.isBlank("hello"));
    }

    @Test
    public void testTrim() {
        assertEquals("", TextUtil.trim(null));
        assertEquals("", TextUtil.trim(""));
        assertEquals("hello", TextUtil.trim("  hello  "));
    }

    @Test
    public void testJoin() {
        assertEquals("a,b,c", TextUtil.join(",", "a", "b", "c"));
        assertEquals("a", TextUtil.join(",", "a"));
        assertEquals("", TextUtil.join(","));
    }

    @Test
    public void testIndexOf() {
        assertEquals(2, TextUtil.indexOf("hello", "llo"));
        assertEquals(-1, TextUtil.indexOf("hello", "xyz"));
        assertEquals(-1, TextUtil.indexOf(null, "xyz"));
    }
}
