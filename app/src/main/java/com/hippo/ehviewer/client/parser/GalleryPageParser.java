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

package com.hippo.ehviewer.client.parser;

import android.text.TextUtils;
import com.hippo.ehviewer.client.exception.ParseException;
import com.hippo.yorozuya.StringUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GalleryPageParser {

    private static final Pattern PATTERN_IMAGE_URL = Pattern.compile("<img[^>]*src=\"([^\"]+)\" style");
    private static final Pattern PATTERN_SKIP_HATH_KEY = Pattern.compile("onclick=\"return nl\\('([^\\)]+)'\\)");
    private static final Pattern PATTERN_ORIGIN_IMAGE_URL = Pattern.compile("<a href=\"([^\"]+)fullimg([^\"]+)\">");
    // TODO Not sure about the size of show keys
    private static final Pattern PATTERN_SHOW_KEY = Pattern.compile("var showkey=\"([0-9a-z]+)\";");

    public static Result parse(String body) throws ParseException {
        Matcher m;
        Result result = new Result();
        m = PATTERN_IMAGE_URL.matcher(body);
        if (m.find()) {
            result.imageUrl = StringUtils.unescapeXml(StringUtils.trim(m.group(1)));
        }
        m = PATTERN_SKIP_HATH_KEY.matcher(body);
        if (m.find()) {
            result.skipHathKey = StringUtils.unescapeXml(StringUtils.trim(m.group(1)));
        }
        m = PATTERN_ORIGIN_IMAGE_URL.matcher(body);
        if (m.find()) {
            result.originImageUrl = StringUtils.unescapeXml(m.group(1)) + "fullimg" + StringUtils.unescapeXml(m.group(2));
        }
        m = PATTERN_SHOW_KEY.matcher(body);
        if (m.find()) {
            result.showKey = m.group(1);
        }

        if (!TextUtils.isEmpty(result.imageUrl) && !TextUtils.isEmpty(result.showKey)) {
            return result;
        } else {
            throw new ParseException("Parse image url and show error", body);
        }
    }

    public static class Result {
        public String imageUrl;
        public String skipHathKey;
        public String originImageUrl;
        public String showKey;
    }
}
