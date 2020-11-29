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
import android.util.Log;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.exception.ParseException;
import com.hippo.util.ExceptionUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ProfileParser {

    private static final String TAG = ProfileParser.class.getSimpleName();

    public static Result parse(String body) throws ParseException {
        try {
            Result result = new Result();
            Document d = Jsoup.parse(body);
            Element profilename = d.getElementById("profilename");
            result.displayName = profilename.child(0).text();
            try {
                result.avatar = profilename.nextElementSibling().nextElementSibling().child(0).attr("src");
                if (TextUtils.isEmpty(result.avatar)) {
                    result.avatar = null;
                } else if (!result.avatar.startsWith("http")) {
                    result.avatar = EhUrl.URL_FORUMS + result.avatar;
                }
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                Log.i(TAG, "No avatar");
            }
            return result;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throw new ParseException("Parse forums error", body);
        }
    }

    public static class Result {
        public String displayName;
        public String avatar;
    }
}
