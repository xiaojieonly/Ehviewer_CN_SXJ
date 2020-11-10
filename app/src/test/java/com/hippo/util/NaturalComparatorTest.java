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

public class NaturalComparatorTest {

  @Test
  public void testOrder() {
    assertOrder("1", "2");
    assertOrder("2", "11");
    assertOrder("2", "00011");

    assertOrder("a2", "a11");
    assertOrder("a2a", "a11a");
  }

  @Test
  public void testEquals() {
    assertEquals("2", "2");
    assertEquals("11", "11");
    assertEquals("a11", "a11");
    assertEquals("a2", "a2");
    assertEquals("a2a", "a2a");
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

  @Test
  public void testBigNumber() {
    assertOrder("1", "22222222222222222222222222222222222222222222222222222222222222222222222");
  }

  @Test
  public void testLeadingZero() {
    assertOrder("000000000000000001", "1");
    assertOrder("000000000000000001", "0001");
    assertOrder("00000000000000000", "0");
    assertOrder("00000000000000000", "000");
  }

  private void assertOrder(String s1, String s2) {
    NaturalComparator comparator = new NaturalComparator();
    assertTrue(comparator.compare(s1, s2) < 0);
    assertTrue(comparator.compare(s2, s1) > 0);
  }

  private void assertEquals(String s1, String s2) {
    NaturalComparator comparator = new NaturalComparator();
    Assert.assertEquals(0, comparator.compare(s1, s2));
    Assert.assertEquals(0, comparator.compare(s2, s1));
  }
}
