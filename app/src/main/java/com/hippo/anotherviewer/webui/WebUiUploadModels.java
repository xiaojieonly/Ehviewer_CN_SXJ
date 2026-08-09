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

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Wire DTOs for the WebUI server's download-upload (push) endpoints
 * (see {@code contracts/openapi.yaml}, {@code /api/v1/download/upload/*}).
 * Mirrors {@code DownloadUploadInitRequest} / {@code DownloadUploadInitResponse}
 * / {@code DownloadUploadCompleteRequest} on the server.
 */
public final class WebUiUploadModels {

    private WebUiUploadModels() {}

    /** PUT /api/v1/download/upload/{gid} body. */
    public static class UploadInitRequest {
        public String token;
        @Nullable
        public String title;
        @Nullable
        public String titleJpn;
        @Nullable
        public String thumb;
        public int category;
        @Nullable
        public String uploader;
        public float rating;
        @Nullable
        public String simpleTags;
        public int pages;
        public int label;
        public boolean force;
    }

    /** PUT /api/v1/download/upload/{gid} response; existingPages drive resume. */
    public static class InitResponse {
        public boolean success;
        @Nullable
        public String message;
        @Nullable
        public List<Integer> existingPages;
    }

    /** POST /api/v1/download/upload/{gid}/complete body. */
    public static class CompleteRequest {
        public int total;
        public int done;
    }
}
