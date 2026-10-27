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

import com.hippo.ehviewer.client.data.TorrentInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TorrentParser {

    private static final Pattern PATTERN_TORRENT_BLOCK = Pattern.compile("<form\\b[^>]*>.*?</form>", Pattern.DOTALL);
    private static final Pattern PATTERN_POSTED = Pattern.compile("<span[^>]*>\\s*Posted:\\s*</span>\\s*<span>([^<]+)</span>", Pattern.DOTALL);
    private static final Pattern PATTERN_TORRENT = Pattern.compile("<td colspan=\"5\">\\s*&nbsp;\\s*<a href=\"([^\"]+)\"[^<]*>([^<]+)</a></td>", Pattern.DOTALL);

    public static TorrentInfo[] parse(String body) {
        List<TorrentInfo> torrentList = new ArrayList<>();
        Matcher blockMatcher = PATTERN_TORRENT_BLOCK.matcher(body);
        while (blockMatcher.find()) {
            String block = blockMatcher.group();
            Matcher torrentMatcher = PATTERN_TORRENT.matcher(block);
            if (!torrentMatcher.find()) {
                continue;
            }
            // Remove ?p= to make torrent redistributable
            String url = ParserUtils.trim(torrentMatcher.group(1));
            int index = url.indexOf("?p=");
            if (index != -1) {
                url = url.substring(0, index);
            }
            String name = ParserUtils.trim(torrentMatcher.group(2));
            String posted = "";
            Matcher postedMatcher = PATTERN_POSTED.matcher(block);
            if (postedMatcher.find()) {
                posted = ParserUtils.trim(postedMatcher.group(1));
            }
            TorrentInfo item = new TorrentInfo(url, name, posted);
            torrentList.add(item);
        }
        return torrentList.toArray(new TorrentInfo[0]);
    }
}
