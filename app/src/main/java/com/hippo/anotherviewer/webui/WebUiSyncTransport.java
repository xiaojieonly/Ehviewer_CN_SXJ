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
 * Network seam for {@link WebUiSyncEngine}: the two endpoints of the sync
 * cycle. {@link WebUiApiSyncTransport} is the production implementation that
 * delegates to the static {@link WebUiApiClient}; tests supply an in-memory
 * fake server so a full push → pull → apply cycle runs on the JVM.
 */
public interface WebUiSyncTransport {

    @NonNull
    WebUiSyncModels.PushResponse push(@NonNull WebUiConfig config,
            @NonNull WebUiSyncModels.PushRequest request) throws IOException;

    @NonNull
    WebUiSyncModels.PullResponse pull(@NonNull WebUiConfig config, long since) throws IOException;
}
