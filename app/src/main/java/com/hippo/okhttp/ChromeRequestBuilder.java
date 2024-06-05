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

package com.hippo.okhttp;

import com.hippo.ehviewer.Settings;

import okhttp3.Request;

public class ChromeRequestBuilder extends Request.Builder {

    private static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36";

    private static final String CHROME_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;" +
                    "q=0.8,application/signed-exchange;v=b3;q=0.9";

    private static final String CHROME_ACCEPT_LANGUAGE =
            "en-US,en;q=0.9";
//    private static final String SOURCE_APP = "Ehviewer";

    private static final String CHROME_SEC_FETCH_DEST = "empty";
    private static final String CHROME_SEC_FETCH_MODE = "cors";
    private static final String CHROME_SEC_FETCH_SITE = "same-site";
    private static final String CHROME_SEC_CH_UA = "\"Chromium\";v=\"118\", \"Google Chrome\";v=\"118\", \"Not=A?Brand\";v=\"99\"";
    private static final String CHROME_SEC_CH_UA_MOBILE = "?0";
    private static final String CHROME_SEC_CH_UA_PLATFORM = "Windows";


    public ChromeRequestBuilder(String url) {
        String host = url.split("/")[2];
/*
Sec-Fetch-Dest: empty
Sec-Fetch-Mode: cors
Sec-Fetch-Site: same-site
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36
sec-ch-ua: "Chromium";v="118", "Google Chrome";v="118", "Not=A?Brand";v="99"
sec-ch-ua-mobile: ?0
sec-ch-ua-platform: "Windows"
 */
        // domain fronting
        url(url);
        addHeader("Host", host);
        addHeader("User-Agent", CHROME_USER_AGENT);
        addHeader("Accept", CHROME_ACCEPT);
        addHeader("Accept-Language", CHROME_ACCEPT_LANGUAGE);
//        addHeader("Source-App", SOURCE_APP);
//        addHeader("Sec-Fetch-Dest", CHROME_SEC_FETCH_DEST);
//        addHeader("Sec-Fetch-Mode", CHROME_SEC_FETCH_MODE);
//        addHeader("Sec-Fetch-Site", CHROME_SEC_FETCH_SITE);
//        addHeader("sec-ch-ua", CHROME_SEC_CH_UA);
//        addHeader("sec-ch-ua-mobile", CHROME_SEC_CH_UA_MOBILE);
//        addHeader("sec-ch-ua-platform", CHROME_SEC_CH_UA_PLATFORM);
    }
}
