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

package com.hippo.ehviewer.client;

import android.net.Uri;

import com.hippo.okhttp.ChromeRequestBuilder;

import java.util.Iterator;
import java.util.Map;

public class EhRequestBuilder extends ChromeRequestBuilder {

    public EhRequestBuilder(String url) {
        this(url, null, null);
    }

    public EhRequestBuilder(String url, String referer) {
        this(url, referer, null);
    }

    public EhRequestBuilder(String url, String referer, String origin) {
        super(url);
        if (referer != null) {
            addHeader("Referer", referer);
        }
        if (origin != null) {
            addHeader("Origin", origin);
        }
    }

    public EhRequestBuilder(Map<String, String> headers,String url ) {
        super(url);
        for (Map.Entry<String, String> m : headers.entrySet()) {
            addHeader(m.getKey(), m.getValue());
        }
    }

}
