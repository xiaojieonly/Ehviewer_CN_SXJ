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

package com.hippo.ehviewer.webui;

import androidx.annotation.Nullable;

/**
 * Wire DTOs for the WebUI server's download management endpoints
 * (see {@code contracts/openapi.yaml}, {@code /api/v1/download/*}).
 * Mirrors {@code DownloadAddRequest} / {@code DownloadItem} on the server.
 */
public final class WebUiDownloadModels {

    private WebUiDownloadModels() {}

    /** POST /api/v1/download/add body. */
    public static class DownloadAddRequest {
        public long gid;
        public String token;
        @Nullable
        public String title;
        @Nullable
        public String thumb;
        public int label;
    }

    /** Item of GET /api/v1/download/list. Only fields the client consumes. */
    public static class DownloadListItem {
        public long id;
        public long gid;
        public int state;
        @Nullable
        public String title;
    }
}
