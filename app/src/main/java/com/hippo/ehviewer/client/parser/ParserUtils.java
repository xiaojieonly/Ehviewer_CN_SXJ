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

package com.hippo.ehviewer.client.parser;

import com.hippo.yorozuya.NumberUtils;
import com.hippo.yorozuya.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ParserUtils {

    public static final DateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    public static synchronized String formatDate(long time) {
        return sDateFormat.format(new Date(time));
    }

    public static String trim(String str) {
        // Avoid null
        if (str == null) {
            str = "";
        }
        return StringUtils.unescapeXml(str).trim();
    }

    public static int parseInt(String str, int defValue) {
        return NumberUtils.parseIntSafely(trim(str).replace(",", ""), defValue);
    }

    public static long parseLong(String str, long defValue) {
        return NumberUtils.parseLongSafely(trim(str).replace(",", ""), defValue);
    }

    public static float parseFloat(String str, float defValue) {
        return NumberUtils.parseFloatSafely(trim(str).replace(",", ""), defValue);
    }
}
