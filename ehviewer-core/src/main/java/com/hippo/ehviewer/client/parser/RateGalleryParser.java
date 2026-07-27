/*
 * Copyright 2015 Hippo Seven
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

import com.hippo.ehviewer.client.exception.ParseException;

import org.json.JSONException;
import org.json.JSONObject;

public class RateGalleryParser {

    public static class Result {
        public float rating;
        public int ratingCount;
    }

    public static Result parse(String body) throws Exception {
        try {
            JSONObject jsonObject = new JSONObject(body);
            Result result = new Result();
            result.rating = (float)jsonObject.getDouble("rating_avg");
            result.ratingCount = jsonObject.getInt("rating_cnt");
            return result;
        } catch (JSONException e) {
            Exception exception = new ParseException("Can't parse rate gallery", body);
            exception.initCause(e);
            throw exception;
        }
    }
}
