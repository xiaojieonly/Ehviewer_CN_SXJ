/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.lib.yorozuya

import java.util.Arrays
import kotlin.math.min

/**
 * 字符串工具。空串与 null 的处理与原来的 Java 版本一致，供 Java 以静态方法调用。
 */
class StringUtils private constructor() {
    companion object {
        @JvmField
        val EMPTY_STRING_ARRAY = arrayOf<String>()

        /** 供二分查找裁剪空白，必须保持码点升序。 */
        @JvmField
        val WHITE_SPACE_ARRAY = charArrayOf(
            '\u0009', // TAB
            '\u0020', // SPACE
            '\u00A0', // NO-BREAK SPACE
            '\u3000', // IDEOGRAPHIC SPACE
        )

        private val ESCAPE_CHARACTER_LIST = arrayOf<String?>(
            "&amp;",
            "&lt;",
            "&gt;",
            "&quot;",
            "&#039;",
            "&times;",
            "&nbsp;",
        )

        private val UNESCAPE_CHARACTER_LIST = arrayOf<String?>(
            "&",
            "<",
            ">",
            "\"",
            "'",
            "×",
            " ",
        )

        /**
         * 把常见 XML 实体还原成字符。不能覆盖全部实体，null 原样返回。
         */
        @JvmStatic
        fun unescapeXml(str: String?): String? {
            return replaceEach(str, ESCAPE_CHARACTER_LIST, UNESCAPE_CHARACTER_LIST)
        }

        /**
         * 替换全部匹配。text、searchString 或 replacement 为 null，或 searchString 为空时，返回原 text。
         */
        @JvmStatic
        fun replace(text: String?, searchString: String?, replacement: String?): String? {
            return replace(text, searchString, replacement, -1)
        }

        /**
         * 最多替换 max 次。max 为 0 时不替换；为负数时不限制次数。空搜索串和 null 入参都原样返回 text。
         */
        @JvmStatic
        fun replace(text: String?, searchString: String?, replacement: String?, max: Int): String? {
            if (text.isNullOrEmpty() || searchString.isNullOrEmpty() || replacement == null || max == 0) {
                return text
            }
            var start = 0
            var end = text.indexOf(searchString, start)
            if (end < 0) {
                return text
            }
            val replLength = searchString.length
            var increase = replacement.length - replLength
            increase = if (increase < 0) 0 else increase
            increase *= if (max < 0) 16 else if (max > 64) 64 else max
            val buf = StringBuilder(text.length + increase)
            var remaining = max
            while (end >= 0) {
                buf.append(text.substring(start, end)).append(replacement)
                start = end + replLength
                if (--remaining == 0) {
                    break
                }
                end = text.indexOf(searchString, start)
            }
            buf.append(text.substring(start))
            return buf.toString()
        }

        /**
         * 按数组逐项替换，只扫一遍，替换结果不会再次参与后续搜索。
         * 两个数组长度必须相同；null 或空 text、空数组都原样返回 text。数组里的 null 项会跳过。
         */
        @JvmStatic
        fun replaceEach(
            text: String?,
            searchList: Array<String?>?,
            replacementList: Array<String?>?,
        ): String? {
            return replaceEach(text, searchList, replacementList, false, 0)
        }

        /**
         * 按数组替换。repeat 为 true 时用替换结果继续替换，直到没有匹配或发现循环。
         * 循环时抛出 IllegalStateException，两个数组长度不同时抛出 IllegalArgumentException。
         */
        private fun replaceEach(
            text: String?,
            searchList: Array<String?>?,
            replacementList: Array<String?>?,
            repeat: Boolean,
            timeToLive: Int,
        ): String? {
            if (text.isNullOrEmpty() || searchList.isNullOrEmpty() || replacementList.isNullOrEmpty()) {
                return text
            }
            if (timeToLive < 0) {
                throw IllegalStateException(
                    "Aborting to protect against StackOverflowError - " +
                        "output of one loop is the input of another"
                )
            }

            val searchLength = searchList.size
            val replacementLength = replacementList.size
            if (searchLength != replacementLength) {
                throw IllegalArgumentException(
                    "Search and Replace array lengths don't match: " +
                        searchLength +
                        " vs " +
                        replacementLength
                )
            }

            val noMoreMatchesForReplIndex = BooleanArray(searchLength)
            var textIndex = -1
            var replaceIndex = -1
            var tempIndex: Int

            for (i in 0 until searchLength) {
                if (noMoreMatchesForReplIndex[i] ||
                    searchList[i].isNullOrEmpty() ||
                    replacementList[i] == null
                ) {
                    continue
                }
                tempIndex = text.indexOf(searchList[i]!!)
                if (tempIndex == -1) {
                    noMoreMatchesForReplIndex[i] = true
                } else if (textIndex == -1 || tempIndex < textIndex) {
                    textIndex = tempIndex
                    replaceIndex = i
                }
            }
            if (textIndex == -1) {
                return text
            }

            var start = 0
            var increase = 0
            for (i in searchList.indices) {
                val searchItem = searchList[i] ?: continue
                val replacementItem = replacementList[i] ?: continue
                val greater = replacementItem.length - searchItem.length
                if (greater > 0) {
                    increase += 3 * greater
                }
            }
            increase = min(increase, text.length / 5)
            val buf = StringBuilder(text.length + increase)

            while (textIndex != -1) {
                for (i in start until textIndex) {
                    buf.append(text[i])
                }
                buf.append(replacementList[replaceIndex])
                start = textIndex + searchList[replaceIndex]!!.length

                textIndex = -1
                replaceIndex = -1
                for (i in 0 until searchLength) {
                    if (noMoreMatchesForReplIndex[i] ||
                        searchList[i].isNullOrEmpty() ||
                        replacementList[i] == null
                    ) {
                        continue
                    }
                    tempIndex = text.indexOf(searchList[i]!!, start)
                    if (tempIndex == -1) {
                        noMoreMatchesForReplIndex[i] = true
                    } else if (textIndex == -1 || tempIndex < textIndex) {
                        textIndex = tempIndex
                        replaceIndex = i
                    }
                }
            }
            for (i in start until text.length) {
                buf.append(text[i])
            }
            val result = buf.toString()
            if (!repeat) {
                return result
            }
            return replaceEach(result, searchList, replacementList, repeat, timeToLive - 1)
        }

        /**
         * 判断字符串是否以给定后缀之一结尾。后缀数组按传入顺序检查。
         */
        @JvmStatic
        fun endsWith(string: String, suffixs: Array<String>): Boolean {
            for (i in suffixs.indices) {
                if (string.endsWith(suffixs[i])) {
                    return true
                }
            }
            return false
        }

        /**
         * 按字符拆分，相邻分隔符合并成一个。null 返回 null，空串返回空数组。
         */
        @JvmStatic
        fun split(str: String?, separatorChar: Char): Array<String>? {
            return splitWorker(str, separatorChar, false)
        }

        /**
         * 执行拆分。preserveAllTokens 为 true 时，相邻分隔符会留下空串；为 false 时合并。
         */
        private fun splitWorker(
            str: String?,
            separatorChar: Char,
            preserveAllTokens: Boolean,
        ): Array<String>? {
            if (str == null) {
                return null
            }
            val len = str.length
            if (len == 0) {
                return EMPTY_STRING_ARRAY
            }
            val list = ArrayList<String>()
            var i = 0
            var start = 0
            var match = false
            var lastMatch = false
            while (i < len) {
                if (str[i] == separatorChar) {
                    if (match || preserveAllTokens) {
                        list.add(str.substring(start, i))
                        match = false
                        lastMatch = true
                    }
                    start = ++i
                    continue
                }
                lastMatch = false
                match = true
                i++
            }
            if (match || (preserveAllTokens && lastMatch)) {
                list.add(str.substring(start, i))
            }
            return list.toTypedArray()
        }

        /**
         * null 换成空串，其余原样返回。
         */
        @JvmStatic
        fun avoidNull(value: String?): String {
            return avoidNull(value, "")!!
        }

        /**
         * value 为 null 时返回 defaultValue，defaultValue 本身也可以是 null。
         */
        @JvmStatic
        fun avoidNull(value: String?, defaultValue: String?): String? {
            return value ?: defaultValue
        }

        /**
         * 判断是否全部由 0-9 组成。null、空串或含其他字符时返回 false。
         */
        @JvmStatic
        fun isAllDigit(str: String?): Boolean {
            if (str.isNullOrEmpty()) {
                return false
            }
            for (i in 0 until str.length) {
                val ch = str[i]
                if (ch < '0' || ch > '9') {
                    return false
                }
            }
            return true
        }

        /**
         * 返回字符串长度。null 当作 0。
         */
        @JvmStatic
        fun length(str: String?): Int {
            return if (str == null) 0 else str.length
        }

        /**
         * 统计字符出现次数。null 或空串返回 0。
         */
        @JvmStatic
        fun countMatches(str: CharSequence?, ch: Char): Int {
            if (str.isNullOrEmpty()) {
                return 0
            }
            var count = 0
            for (i in 0 until str.length) {
                if (ch == str[i]) {
                    count++
                }
            }
            return count
        }

        /**
         * 两边都是 null 或空串时视为相等；否则必须都非空且内容相同。
         * 空串和有内容的字符串不相等。
         */
        @JvmStatic
        fun equals(str1: String?, str2: String?): Boolean {
            return (str1.isNullOrEmpty() && str2.isNullOrEmpty()) ||
                (!str1.isNullOrEmpty() && !str2.isNullOrEmpty() && str1 == str2)
        }

        /**
         * 查找字符第 n 次出现的位置，n 从 0 开始。str 为 null 或 n 小于 0 时返回 -1。
         */
        @JvmStatic
        fun ordinalIndexOf(str: String?, c: Char, n: Int): Int {
            if (str == null || n < 0) {
                return -1
            }
            var pos = -1
            var remaining = n
            do {
                pos = str.indexOf(c, pos + 1)
            } while (remaining-- > 0 && pos != -1)
            return pos
        }

        /**
         * 去掉首尾空白。空白含制表符、普通空格、不换行空格和全角空格。null 返回 null。
         */
        @JvmStatic
        fun trim(str: String?): String? {
            return trim(str, WHITE_SPACE_ARRAY)
        }

        /**
         * 按给定字符集去掉首尾字符。excluded 必须按码点升序，供二分查找使用。null 文本返回 null。
         */
        @JvmStatic
        fun trim(str: String?, excluded: CharArray?): String? {
            if (str == null) {
                return null
            }
            var start = 0
            val last = str.length - 1
            var end = last
            while (start <= end && Arrays.binarySearch(excluded!!, str[start]) >= 0) {
                start++
            }
            while (end >= start && Arrays.binarySearch(excluded!!, str[end]) >= 0) {
                end--
            }
            if (start == 0 && end == last) {
                return str
            }
            return str.substring(start, end + 1)
        }

        /**
         * 删除字符串中出现在 removed 里的字符。str 为 null、空串，或 removed 为 null、空数组时原样返回。
         * 一旦进入删除逻辑就返回新字符串，即使一个字符都没删掉。
         */
        @JvmStatic
        fun remove(str: String?, removed: CharArray?): String? {
            if (str.isNullOrEmpty() || removed == null || removed.isEmpty()) {
                return str
            }
            val chars = str.toCharArray()
            var pos = 0
            for (i in chars.indices) {
                if (!Utilities.contain(removed, chars[i])) {
                    chars[pos++] = chars[i]
                }
            }
            return String(chars, 0, pos)
        }
    }
}
