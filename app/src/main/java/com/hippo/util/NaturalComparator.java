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

import java.util.Comparator;

/**
 * Implements natural sort order.
 */
public class NaturalComparator implements Comparator<String> {

  @Override
  public int compare(String o1, String o2) {
    if (o1 == null && o2 == null) {
      return 0;
    }
    if (o1 == null) {
      return -1;
    }
    if (o2 == null) {
      return 1;
    }

    int index1 = 0;
    int index2 = 0;
    while (true) {
      String data1 = nextSlice(o1, index1);
      String data2 = nextSlice(o2, index2);

      if (data1 == null && data2 == null) {
        return 0;
      }
      if (data1 == null) {
        return -1;
      }
      if (data2 == null) {
        return 1;
      }

      index1 += data1.length();
      index2 += data2.length();

      int result;
      if (isDigit(data1) && isDigit(data2)) {
        result = compareNumberString(data1, data2);
      } else {
        result = data1.compareToIgnoreCase(data2);
      }

      if (result != 0) {
        return result;
      }
    }
  }

  private static boolean isDigit(String str) {
    // Just check the first char
    char ch = str.charAt(0);
    return ch >= '0' && ch <= '9';
  }

  private static String nextSlice(String str, int index) {
    int length = str.length();
    if (index == length) {
      return null;
    }

    char ch = str.charAt(index);
    if (ch == '.' || ch == ' ') {
      return str.substring(index, index + 1);
    } else if (ch >= '0' && ch <= '9') {
      return str.substring(index, nextNumberBound(str, index + 1));
    } else {
      return str.substring(index, nextOtherBound(str, index + 1));
    }
  }

  private static int nextNumberBound(String str, int index) {
    for (int length = str.length(); index < length; index++) {
      char ch = str.charAt(index);
      if (ch < '0' || ch > '9') {
        break;
      }
    }
    return index;
  }

  private static int nextOtherBound(String str, int index) {
    for (int length = str.length(); index < length; index++) {
      char ch = str.charAt(index);
      if (ch == '.' || ch == ' ' || (ch >= '0' && ch <= '9')) {
        break;
      }
    }
    return index;
  }

  private static String removeLeadingZero(String s) {
    if (s.length() < 1) {
      return s;
    }

    // At least keep the last number
    for (int i = 0, n = s.length() - 1; i < n; i++) {
      if (s.charAt(i) != '0') {
        return s.substring(i);
      }
    }

    return s.substring(s.length() - 1);
  }

  private static int compareNumberString(String s1, String s2) {
    String p1 = removeLeadingZero(s1);
    String p2 = removeLeadingZero(s2);

    int l1 = p1.length();
    int l2 = p2.length();

    if (l1 > l2) {
      return 1;
    } else if (l1 < l2) {
      return -1;
    } else {
      for (int i = 0; i < l1; i++) {
        char c1 = p1.charAt(i);
        char c2 = p2.charAt(i);
        if (c1 > c2) {
          return 1;
        } else if (c1 < c2) {
          return -1;
        }
      }
    }

    return -Integer.compare(s1.length(), s2.length());
  }
}
