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

package com.hippo.yorozuya;

public final class NumberUtils {
    private NumberUtils() {
    }

    /**
     * 0 for false, Non 0 for true
     *
     * @param integer the int
     * @return the boolean
     */
    public static boolean int2boolean(int integer) {
        return integer != 0;
    }

    /**
     * false for 0, true for 1
     *
     * @param bool the boolean
     * @return the int
     */
    public static int boolean2int(boolean bool) {
        return bool ? 1 : 0;
    }

    /**
     * Do not throw NumberFormatException, use default value
     *
     * @param str          the string to be parsed
     * @param defaultValue the value to return when get error
     * @return the value of the string
     */
    public static int parseIntSafely(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    /**
     * Do not throw NumberFormatException, use default value
     *
     * @param str          the string to be parsed
     * @param defaultValue the value to return when get error
     * @return the value of the string
     */
    public static long parseLongSafely(String str, long defaultValue) {
        try {
            return Long.parseLong(str);
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    /**
     * Do not throw NumberFormatException, use default value
     *
     * @param str          the string to be parsed
     * @param defaultValue the value to return when get error
     * @return the value of the string
     */
    public static float parseFloatSafely(String str, float defaultValue) {
        try {
            return Float.parseFloat(str);
        } catch (Throwable e) {
            return defaultValue;
        }
    }
}
