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

import com.hippo.ehviewer.client.data.ArchiverData;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveParser {

    private static final Pattern PATTERN_FORM = Pattern.compile("<form id=\"hathdl_form\" action=\"[^\"]*?or=([^=\"]*?)\" method=\"post\">");
    private static final Pattern PATTERN_ARCHIVE = Pattern.compile("<a href=\"[^\"]*\" onclick=\"return do_hathdl\\('([0-9]+|org)'\\)\">([^<]+)</a>");
    private static final Pattern PATTERN_ARCHIVER_DOWNLOAD_URL = Pattern.compile("href=\"(.*)\">Click Here To Start Downloading");

    @SuppressWarnings("unchecked")
    public static Pair<String, Pair<String, String>[]> parse(String body) {
        Matcher m = PATTERN_FORM.matcher(body);
        if (!m.find()) {
            return new Pair<String, Pair<String, String>[]>("", new Pair[0]);
        }
        String paramOr = m.group(1);
        List<Pair<String, String>> archiveList = new ArrayList<>();
        m = PATTERN_ARCHIVE.matcher(body);
        while (m.find()) {
            String res = ParserUtils.trim(m.group(1));
            String name = ParserUtils.trim(m.group(2));
            Pair<String, String> item = new Pair<>(res, name);
            archiveList.add(item);
        }
        return new Pair<String, Pair<String, String>[]>(paramOr, archiveList.toArray(new Pair[archiveList.size()]));
    }

    public static ArchiverData parseArchiver(String body) {
        ArchiverData data = new ArchiverData();
        Document document = Jsoup.parse(body);

        try {
            Element bodyElement = (Element) document.childNode(2).childNode(3).childNode(1);
            data.funds = bodyElement.child(2).text();

            Element original = bodyElement.child(3).child(0);
            data.originalCost = original.child(0).child(0).text();
            data.originalSize = original.child(2).child(0).text();
            data.originalUrl = original.child(1).attr("action");

            Element resample = bodyElement.child(3).child(1);
            data.resampleCost = resample.child(0).child(0).text();
            data.resampleSize = resample.child(2).child(0).text();
            data.resampleUrl = resample.child(1).attr("action");
        } catch (Exception ignore) {
        }
        return data;
    }

    public static String parseArchiverDownloadUrl(String body) {
        Matcher m = PATTERN_ARCHIVER_DOWNLOAD_URL.matcher(body);
        if (!m.find()){
            return null;
        }
        return m.group(1);
    }
}
