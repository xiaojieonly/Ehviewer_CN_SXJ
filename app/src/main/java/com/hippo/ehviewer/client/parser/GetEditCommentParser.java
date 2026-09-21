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

import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.client.exception.ParseException;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class GetEditCommentParser {

    public static class Result {
        public long id;
        public String comment;
    }

    public static Result parse(String body) throws Exception {
        try {
            JSONObject jsonObject = new JSONObject(body);
            if (jsonObject.has("error")) {
                throw new EhException(jsonObject.getString("error"));
            }

            Result result = new Result();
            result.id = jsonObject.getLong("comment_id");

            Element textarea = Jsoup.parseBodyFragment(
                    jsonObject.getString("editable_comment"))
                    .selectFirst("textarea[name=commenttext_edit]");
            if (textarea == null) {
                throw new ParseException("Can't find editable comment", body);
            }
            result.comment = textarea.wholeText();
            return result;
        } catch (JSONException e) {
            throw new ParseException("Can't parse editable comment", body, e);
        }
    }
}
