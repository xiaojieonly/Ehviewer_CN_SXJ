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
package com.hippo.util

import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan
import java.nio.charset.Charset
import java.util.regex.Pattern

object TextUrl {
    @JvmField
    val UTF_8: Charset? = Charset.forName("UTF-8")

    private val URL_PATTERN: Pattern =
        Pattern.compile("(http|https)://[a-z0-9A-Z%-]+(\\.[a-z0-9A-Z%-]+)+(:\\d{1,5})?(/[a-zA-Z0-9-_~:#@!&',;=%/\\*\\.\\?\\+\\$\\[\\]\\(\\)]+)?/?")

    @JvmStatic
    fun handleTextUrl(content: CharSequence): CharSequence {
        val m = URL_PATTERN.matcher(content)

        var spannable: Spannable? = null
        while (m.find()) {
            // Ensure spannable
            if (spannable == null) {
                spannable = content as? Spannable ?: SpannableString(content)
            }

            val start = m.start()
            val end = m.end()

            val links = spannable.getSpans(start, end, URLSpan::class.java)
            if (links.size > 0) {
                // There has been URLSpan already, leave it alone
                continue
            }

            val urlSpan = URLSpan(m.group(0))
            spannable.setSpan(urlSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable ?: content
    }
}
