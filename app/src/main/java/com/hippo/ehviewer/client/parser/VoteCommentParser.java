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

import org.json.JSONException;
import org.json.JSONObject;

public class VoteCommentParser {

    public static class Result {
        public long id;
        public int score;
        public int vote;
        public int expectVote;
    }

    // {"comment_id":1253922,"comment_score":-19,"comment_vote":0}
    public static Result parse(String body, int vote) throws JSONException {
        Result result = new Result();
        JSONObject jo = new JSONObject(body);
        result.id = jo.getLong("comment_id");
        result.score = jo.getInt("comment_score");
        result.vote = jo.getInt("comment_vote");
        result.expectVote = vote;
        return result;
    }
}
