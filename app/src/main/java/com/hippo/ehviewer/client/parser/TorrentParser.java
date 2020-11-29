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

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TorrentParser {

    private static final Pattern PATTERN_TORRENT = Pattern.compile("<td colspan=\"5\"> &nbsp; <a href=\"([^\"]+)\"[^<]+>([^<]+)</a></td>");

    @SuppressWarnings("unchecked")
    public static Pair<String, String>[] parse(String body) {
        List<Pair<String, String>> torrentList = new ArrayList<>();
        Matcher m = PATTERN_TORRENT.matcher(body);
        while (m.find()) {
            // Remove ?p= to make torrent redistributable
            String url = ParserUtils.trim(m.group(1));
            int index = url.indexOf("?p=");
            if (index != -1) {
                url = url.substring(0, index);
            }
            String name = ParserUtils.trim(m.group(2));
            Pair<String, String> item = new Pair<>(url, name);
            torrentList.add(item);
        }
        return torrentList.toArray(new Pair[torrentList.size()]);
    }
}
