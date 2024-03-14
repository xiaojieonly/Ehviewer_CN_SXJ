/*
 * Copyright (C) 2014-2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UrlBuilder {

    public String mRootUrl;
    public Map<String, Object> mQueryMap = new HashMap<>();

    public UrlBuilder(String rootUrl) {
        mRootUrl = rootUrl;
    }

    public void addQuery(String key, Object value) {
        mQueryMap.put(key, value);
    }

    public String build() {
        if (mQueryMap.size() == 0) {
            return mRootUrl;
        } else {
            StringBuilder sb = new StringBuilder(mRootUrl);
            sb.append("?");

            Iterator<String> iter = mQueryMap.keySet().iterator();
            if (iter.hasNext()) {
                String key = iter.next();
                Object value = mQueryMap.get(key);
                sb.append(key).append("=").append(value);
            }
            while (iter.hasNext()) {
                String key = iter.next();
                Object value = mQueryMap.get(key);
                sb.append("&").append(key).append("=").append(value);
            }
            return sb.toString();
        }
    }
}
