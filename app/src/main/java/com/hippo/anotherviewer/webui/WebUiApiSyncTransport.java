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

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Production {@link WebUiSyncTransport} delegating to the static
 * {@link WebUiApiClient}. Thin wrapper: the engine only sees the interface so
 * tests can substitute an in-memory server.
 */
public class WebUiApiSyncTransport implements WebUiSyncTransport {

    @NonNull
    @Override
    public WebUiSyncModels.PushResponse push(@NonNull WebUiConfig config,
            @NonNull WebUiSyncModels.PushRequest request) throws IOException {
        return WebUiApiClient.push(config, request);
    }

    @NonNull
    @Override
    public WebUiSyncModels.PullResponse pull(@NonNull WebUiConfig config, long since) throws IOException {
        return WebUiApiClient.pull(config, since);
    }
}
