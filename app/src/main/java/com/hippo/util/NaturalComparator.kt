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
package com.hippo.util

/**
 * Implements natural sort order.
 */
class NaturalComparator : Comparator<String?> {
    override fun compare(o1: String?, o2: String?): Int {
        if (o1 == null && o2 == null) {
            return 0
        }
        if (o1 == null) {
            return -1
        }
        if (o2 == null) {
            return 1
        }

        var index1 = 0
        var index2 = 0
        while (true) {
            val data1: String? = nextSlice(o1, index1)
            val data2: String? = nextSlice(o2, index2)

            if (data1 == null && data2 == null) {
                return 0
            }
            if (data1 == null) {
                return -1
            }
            if (data2 == null) {
                return 1
            }

            index1 += data1.length
            index2 += data2.length

            val result: Int
            if (isDigit(data1) && isDigit(data2)) {
                result = compareNumberString(data1, data2)
            } else {
                result = data1.compareTo(data2, ignoreCase = true)
            }

            if (result != 0) {
                return result
            }
        }
    }

    companion object {
        private fun isDigit(str: String): Boolean {
            // Just check the first char
            val ch = str[0]
            return ch in '0'..'9'
        }

        private fun nextSlice(str: String, index: Int): String? {
            val length = str.length
            if (index == length) {
                return null
            }

            val ch = str[index]
            return when (ch) {
                '.', ' ' -> {
                    str.substring(index, index + 1)
                }
                in '0'..'9' -> {
                    str.substring(index, nextNumberBound(str, index + 1))
                }
                else -> {
                    str.substring(index, nextOtherBound(str, index + 1))
                }
            }
        }

        private fun nextNumberBound(str: String, index: Int): Int {
            var index = index
            val length = str.length
            while (index < length) {
                val ch = str[index]
                if (ch !in '0'..'9') {
                    break
                }
                index++
            }
            return index
        }

        private fun nextOtherBound(str: String, index: Int): Int {
            var index = index
            val length = str.length
            while (index < length) {
                val ch = str[index]
                if (ch == '.' || ch == ' ' || (ch in '0'..'9')) {
                    break
                }
                index++
            }
            return index
        }

        private fun removeLeadingZero(s: String): String {
            if (s.isEmpty()) {
                return s
            }

            // At least keep the last number
            var i = 0
            val n = s.length - 1
            while (i < n) {
                if (s[i] != '0') {
                    return s.substring(i)
                }
                i++
            }

            return s.substring(s.length - 1)
        }

        private fun compareNumberString(s1: String, s2: String): Int {
            val p1: String = removeLeadingZero(s1)
            val p2: String = removeLeadingZero(s2)

            val l1 = p1.length
            val l2 = p2.length

            if (l1 > l2) {
                return 1
            } else if (l1 < l2) {
                return -1
            } else {
                for (i in 0..<l1) {
                    val c1 = p1[i]
                    val c2 = p2[i]
                    if (c1 > c2) {
                        return 1
                    } else if (c1 < c2) {
                        return -1
                    }
                }
            }

            return -s1.length.compareTo(s2.length)
        }
    }
}
