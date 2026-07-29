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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable connection settings for the EhViewer WebUI companion server.
 * Mirrors the role of {@link com.hippo.ehviewer.smb.SmbConfig} but for the
 * HTTP sync backend: protocol + host + port identify the server, and the
 * bearer token authenticates against {@code /api/v1/*}.
 */
public final class WebUiConfig {

    public static final int DEFAULT_PORT = 8080;
    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_HTTPS = "https";

    private final String protocol;
    private final String host;
    private final int port;
    private final String username;
    private final String token;

    public WebUiConfig(@Nullable String protocol, @NonNull String host, int port,
            @Nullable String username, @Nullable String token) {
        this.protocol = PROTOCOL_HTTPS.equalsIgnoreCase(protocol) ? PROTOCOL_HTTPS : PROTOCOL_HTTP;
        this.host = host.trim();
        this.port = port <= 0 ? DEFAULT_PORT : port;
        this.username = username == null ? "" : username.trim();
        this.token = token == null ? "" : token;
    }

    @NonNull
    public String getProtocol() {
        return protocol;
    }

    @NonNull
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getToken() {
        return token;
    }

    /** Base URL without trailing slash, e.g. {@code http://192.168.1.10:8080}. */
    @NonNull
    public String baseUrl() {
        return protocol + "://" + host + ":" + port;
    }

    /** Human-readable {@code host:port} for summaries. */
    @NonNull
    public String displayAddress() {
        return host + ":" + port;
    }
}
