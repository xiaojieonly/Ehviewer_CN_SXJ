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

package com.hippo.network;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InetValidator {

    private static final String IPV4_REGEX =
            "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$";

    private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);

    public static boolean isValidInet4Address(String inet4Address) {
        if (null == inet4Address) {
            return false;
        }

        Matcher matcher = IPV4_PATTERN.matcher(inet4Address);
        if (!matcher.find()) {
            return false;
        }

        // verify that address subgroups are legal
        for (int i = 1; i <= 4; i++) {
            String ipSegment = matcher.group(i);
            if (ipSegment == null || ipSegment.length() == 0) {
                return false;
            }
            int iIpSegment;
            try {
                iIpSegment = Integer.parseInt(ipSegment);
            } catch(NumberFormatException e) {
                return false;
            }
            if (iIpSegment > 255) {
                return false;
            }
            if (ipSegment.length() > 1 && ipSegment.startsWith("0")) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidInetPort(int inetPort) {
        return inetPort >= 0 && inetPort <= 65535;
    }

    private InetValidator() {}
}
