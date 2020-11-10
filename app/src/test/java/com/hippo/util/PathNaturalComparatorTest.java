/*
 * Copyright 2018 Hippo Seven
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

package com.hippo.util;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class PathNaturalComparatorTest {

  @Test
  public void testOrder() {
    assertOrder("ab/d", "abcd");
    assertOrder("ab\\d", "abcd");

    assertOrder("ab/d2", "ab/d11");
  }

  @Test
  public void testEquals() {
    assertEquals("/abc", "abc");
    assertEquals("/abc", "\\abc");
    assertEquals("abc", "\\abc");
    assertEquals("abc///", "abc");
  }

  @Test
  public void testEmpty() {
    assertOrder("", "1");
    assertEquals("", "");
  }

  @Test
  public void testNull() {
    assertEquals(null, null);
    assertOrder(null, "1");
  }

  private void assertOrder(String s1, String s2) {
    PathNaturalComparator comparator = new PathNaturalComparator();
    assertTrue(comparator.compare(s1, s2) < 0);
    assertTrue(comparator.compare(s2, s1) > 0);
  }

  private void assertEquals(String s1, String s2) {
    PathNaturalComparator comparator = new PathNaturalComparator();
    Assert.assertEquals(0, comparator.compare(s1, s2));
    Assert.assertEquals(0, comparator.compare(s2, s1));
  }
}
