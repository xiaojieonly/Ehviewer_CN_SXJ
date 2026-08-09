/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer.webui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;

import org.junit.Test;

import java.util.Arrays;

/**
 * Round-trips the download-upload (push) wire DTOs through fastjson
 * (see {@code contracts/openapi.yaml}, {@code /api/v1/download/upload/*}).
 * Field names must survive serialization verbatim so the server-side Kotlin
 * data classes bind the same shape.
 */
public class WebUiUploadModelsTest {

    @Test
    public void testUploadInitRequestRoundTrip() {
        WebUiUploadModels.UploadInitRequest request = new WebUiUploadModels.UploadInitRequest();
        request.token = "tok123";
        request.title = "Title";
        request.titleJpn = "タイトル";
        request.thumb = "http://img.ehgt.org/t123.jpg";
        request.category = 4;
        request.uploader = "bob";
        request.rating = 4.5f;
        request.simpleTags = "female:shion, language:chinese";
        request.pages = 20;
        request.label = 0;
        request.force = false;

        String json = JSON.toJSONString(request);
        WebUiUploadModels.UploadInitRequest parsed =
                JSON.parseObject(json, WebUiUploadModels.UploadInitRequest.class);

        assertEquals("tok123", parsed.token);
        assertEquals("Title", parsed.title);
        assertEquals("タイトル", parsed.titleJpn);
        assertEquals("http://img.ehgt.org/t123.jpg", parsed.thumb);
        assertEquals(4, parsed.category);
        assertEquals("bob", parsed.uploader);
        assertEquals(4.5f, parsed.rating, 0.001f);
        assertEquals("female:shion, language:chinese", parsed.simpleTags);
        assertEquals(20, parsed.pages);
        assertEquals(0, parsed.label);
        assertFalse(parsed.force);
    }

    @Test
    public void testUploadInitRequestNullablesSerializeAsAbsent() {
        WebUiUploadModels.UploadInitRequest request = new WebUiUploadModels.UploadInitRequest();
        request.token = "tok123";

        String json = JSON.toJSONString(request);

        // Server-side defaults must kick in: no title/thumb keys in the wire JSON.
        assertFalse(json.contains("title"));
        assertFalse(json.contains("thumb"));
        assertFalse(json.contains("simpleTags"));
    }

    @Test
    public void testInitResponseRoundTrip() {
        WebUiUploadModels.InitResponse response = new WebUiUploadModels.InitResponse();
        response.success = true;
        response.message = "ok";
        response.existingPages = Arrays.asList(1, 2, 3);

        WebUiUploadModels.InitResponse parsed = JSON.parseObject(
                JSON.toJSONString(response), WebUiUploadModels.InitResponse.class);

        assertTrue(parsed.success);
        assertEquals("ok", parsed.message);
        assertEquals(Arrays.asList(1, 2, 3), parsed.existingPages);
    }

    @Test
    public void testInitResponseConflictDefaults() {
        // Non-force conflict: the server answers 400 + success=false.
        WebUiUploadModels.InitResponse parsed = JSON.parseObject(
                "{\"success\":false,\"message\":\"gid=123 已存在\",\"existingPages\":[5]}",
                WebUiUploadModels.InitResponse.class);

        assertFalse(parsed.success);
        assertEquals("gid=123 已存在", parsed.message);
        assertEquals(Arrays.asList(5), parsed.existingPages);
    }

    @Test
    public void testCompleteRequestRoundTrip() {
        WebUiUploadModels.CompleteRequest request = new WebUiUploadModels.CompleteRequest();
        request.total = 20;
        request.done = 20;

        WebUiUploadModels.CompleteRequest parsed = JSON.parseObject(
                JSON.toJSONString(request), WebUiUploadModels.CompleteRequest.class);

        assertEquals(20, parsed.total);
        assertEquals(20, parsed.done);
    }

    @Test
    public void testCompleteRequestDefaultsDoneToZero() {
        WebUiUploadModels.CompleteRequest parsed = JSON.parseObject(
                "{\"total\":20}", WebUiUploadModels.CompleteRequest.class);

        assertEquals(20, parsed.total);
        assertEquals(0, parsed.done);
    }
}
