package com.hippo.lib.yorozuya

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {

    @Test
    fun unescapeXmlReplacesKnownEntities() {
        assertEquals(
            "&<>\"'× ",
            StringUtils.unescapeXml("&amp;&lt;&gt;&quot;&#039;&times;&nbsp;")
        )
        assertNull(StringUtils.unescapeXml(null))
    }

    @Test
    fun trimRemovesListedWhitespaceAndKeepsNull() {
        assertEquals("a", StringUtils.trim("\t\u00A0 a\u3000 "))
        assertEquals("a b", StringUtils.trim(" a b "))
        assertNull(StringUtils.trim(null as String?))
    }

    @Test
    fun splitCollapsesAdjacentSeparators() {
        assertNull(StringUtils.split(null, '.'))
        assertArrayEquals(emptyArray<String>(), StringUtils.split("", '.'))
        assertArrayEquals(arrayOf("a", "b", "c"), StringUtils.split("a..b.c", '.'))
        assertArrayEquals(arrayOf("a:b:c"), StringUtils.split("a:b:c", '.'))
    }

    @Test
    fun equalsTreatsNullAndEmptyAsEqual() {
        assertTrue(StringUtils.equals(null, ""))
        assertTrue(StringUtils.equals(null, null))
        assertTrue(StringUtils.equals("a", "a"))
        assertFalse(StringUtils.equals("", "a"))
        assertFalse(StringUtils.equals("a", "b"))
    }

    @Test
    fun removeDropsListedCharacters() {
        assertEquals("abc", StringUtils.remove("a:b*c", charArrayOf(':', '*')))
        assertNull(StringUtils.remove(null, charArrayOf('a')))
        assertEquals("a:b", StringUtils.remove("a:b", null))
        assertEquals("ab", StringUtils.remove("ab", charArrayOf()))
    }

    @Test
    fun endsWithAndAvoidNullMatchCallSites() {
        assertTrue(StringUtils.endsWith("a.JPG", arrayOf(".png", ".JPG")))
        assertFalse(StringUtils.endsWith("a.txt", arrayOf(".png", ".jpg")))
        assertEquals("", StringUtils.avoidNull(null))
        assertEquals("a", StringUtils.avoidNull("a"))
        assertEquals("d", StringUtils.avoidNull(null, "d"))
    }
}
