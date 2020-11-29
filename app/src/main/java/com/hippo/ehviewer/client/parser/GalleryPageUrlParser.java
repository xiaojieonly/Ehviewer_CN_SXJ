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

import androidx.annotation.Nullable;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.yorozuya.NumberUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Like http://exhentai.org/s/91ea4b6d89/901103-12
 */
public final class GalleryPageUrlParser {

    private static final Pattern URL_STRICT_PATTERN = Pattern.compile(
            "https?://(?:" + EhUrl.DOMAIN_EX + "|" + EhUrl.DOMAIN_E + "|" + EhUrl.DOMAIN_LOFI + ")/s/([0-9a-f]{10})/(\\d+)-(\\d+)");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "([0-9a-f]{10})/(\\d+)-(\\d+)");

    @Nullable
    public static Result parse(String url) {
        return parse(url, true);
    }

    @Nullable
    public static Result parse(String url, boolean strict) {
        if (url == null) {
            return null;
        }

        Pattern pattern = strict ? URL_STRICT_PATTERN : URL_PATTERN;
        Matcher m = pattern.matcher(url);
        if (m.find()) {
            Result result = new Result();
            result.gid = NumberUtils.parseLongSafely(m.group(2), -1L);
            result.pToken = m.group(1);
            result.page = NumberUtils.parseIntSafely(m.group(3), 0) - 1;
            if (result.gid < 0 || result.page < 0) {
                return null;
            }
            return result;
        } else {
            return null;
        }
    }

    public static class Result {
        public long gid;
        public String pToken;
        public int page;
    }
}
