/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.network

import java.util.regex.Pattern

object InetValidator {
    private const val IPV4_REGEX = "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$"
    private val IPV4_PATTERN = Pattern.compile(IPV4_REGEX)
    @JvmStatic
    fun isValidInet4Address(inet4Address: String?): Boolean {
        if (null == inet4Address) {
            return false
        }
        val matcher = IPV4_PATTERN.matcher(inet4Address)
        if (!matcher.find()) {
            return false
        }

        // verify that address subgroups are legal
        for (i in 1..4) {
            val ipSegment = matcher.group(i)
            if (ipSegment == null || ipSegment.length == 0) {
                return false
            }
            var iIpSegment: Int
            iIpSegment = try {
                ipSegment.toInt()
            } catch (e: NumberFormatException) {
                return false
            }
            if (iIpSegment > 255) {
                return false
            }
            if (ipSegment.length > 1 && ipSegment.startsWith("0")) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun isValidInetPort(inetPort: Int): Boolean {
        return inetPort >= 0 && inetPort <= 65535
    }
}