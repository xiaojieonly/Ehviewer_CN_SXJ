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

package com.hippo.util;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUrl {

    public static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final Pattern URL_PATTERN = Pattern.compile("(http|https)://[a-z0-9A-Z%-]+(\\.[a-z0-9A-Z%-]+)+(:\\d{1,5})?(/[a-zA-Z0-9-_~:#@!&',;=%/\\*\\.\\?\\+\\$\\[\\]\\(\\)]+)?/?");

    public static CharSequence handleTextUrl(CharSequence content) {
        Matcher m = URL_PATTERN.matcher(content);

        Spannable spannable = null;
        while (m.find()) {
            // Ensure spannable
            if (spannable == null) {
                if (content instanceof Spannable) {
                    spannable = (Spannable) content;
                } else {
                    spannable = new SpannableString(content);
                }
            }

            int start = m.start();
            int end = m.end();

            URLSpan[] links = spannable.getSpans(start, end, URLSpan.class);
            if (links.length > 0) {
                // There has been URLSpan already, leave it alone
                continue;
            }

            URLSpan urlSpan = new URLSpan(m.group(0));
            spannable.setSpan(urlSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable == null ? content : spannable;
    }
}
