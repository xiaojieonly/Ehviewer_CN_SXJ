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

import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.yorozuya.Utilities;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

public final class GalleryListUrlParser {

    private static final String[] VALID_HOSTS = {EhUrl.DOMAIN_EX, EhUrl.DOMAIN_E, EhUrl.DOMAIN_LOFI};

    private static final String PATH_NORMAL = "/";
    private static final String PATH_UPLOADER = "/uploader/";
    private static final String PATH_TAG = "/tag/";

    public static ListUrlBuilder parse(String urlStr) {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            return null;
        }

        if (!Utilities.contain(VALID_HOSTS, url.getHost())) {
            return null;
        }

        String path = url.getPath();
        if (path == null) {
            return null;
        }
        if (PATH_NORMAL.equals(path) || path.length() == 0) {
            ListUrlBuilder builder = new ListUrlBuilder();
            builder.setQuery(url.getQuery());
            return builder;
        } else if (path.startsWith(PATH_UPLOADER)) {
            return parseUploader(path);
        } else if (path.startsWith(PATH_TAG)) {
            return parseTag(path);
        } else if (path.startsWith("/")) {
            int category;
            try {
                category = Integer.parseInt(path.substring(1));
            } catch (NumberFormatException e) {
                return null;
            }
            ListUrlBuilder builder = new ListUrlBuilder();
            builder.setQuery(url.getQuery());
            builder.setCategory(category);
            return builder;
        } else {
            return null;
        }
    }

    // TODO get page
    private static ListUrlBuilder parseUploader(String path) {
        String uploader;
        int prefixLength = PATH_UPLOADER.length();
        int index = path.indexOf('/', prefixLength);

        if (index < 0) {
            uploader = path.substring(prefixLength);
        } else {
            uploader = path.substring(prefixLength, index);
        }

        try {
            uploader = URLDecoder.decode(uploader, "utf-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }

        if (TextUtils.isEmpty(uploader)) {
            return null;
        }

        ListUrlBuilder builder = new ListUrlBuilder();
        builder.setMode(ListUrlBuilder.MODE_UPLOADER);
        builder.setKeyword(uploader);
        return builder;
    }

    // TODO get page
    private static ListUrlBuilder parseTag(String path) {
        String tag;
        int prefixLength = PATH_TAG.length();
        int index = path.indexOf('/', prefixLength);


        if (index < 0) {
            tag = path.substring(prefixLength);
        } else {
            tag = path.substring(prefixLength, index);
        }

        try {
            tag = URLDecoder.decode(tag, "utf-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }

        if (TextUtils.isEmpty(tag)) {
            return null;
        }

        ListUrlBuilder builder = new ListUrlBuilder();
        builder.setMode(ListUrlBuilder.MODE_TAG);
        builder.setKeyword(tag);
        return builder;
    }
}
