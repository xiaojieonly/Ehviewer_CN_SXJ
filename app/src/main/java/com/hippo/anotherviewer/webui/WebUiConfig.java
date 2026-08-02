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
import androidx.annotation.Nullable;

/**
 * Immutable connection settings for the AnotherViewer WebUI companion server.
 * Mirrors the role of {@link com.hippo.anotherviewer.smb.SmbConfig} but for the
 * HTTP sync backend: protocol + host + port identify the server, and the
 * bearer token authenticates against {@code /api/v1/*}.
 */
public final class WebUiConfig {

    public static final int DEFAULT_PORT = 8080;
    public static final int MAX_PORT = 65535;
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
        this.host = normalizeHost(host);
        this.port = port <= 0 ? DEFAULT_PORT : Math.min(port, MAX_PORT);
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

    /**
     * Validates user-supplied host/port values, returning an error message or
     * {@code null} when acceptable. Applies the same normalization as the
     * constructor, so values accepted here produce a usable {@link #baseUrl()}.
     * The sync fragment does not call this yet; wire it into the configure and
     * pair dialog save handlers to surface the message before connecting.
     */
    @Nullable
    public static String validate(@NonNull String host, int port) {
        if (normalizeHost(host).isEmpty()) {
            return "Host must not be empty";
        }
        if (port < 1 || port > MAX_PORT) {
            return "Port must be between 1 and " + MAX_PORT;
        }
        return null;
    }

    /**
     * Normalizes a raw host string: trims whitespace, strips a leading
     * {@code http://} or {@code https://} scheme, discards any pasted path
     * (and preceding slashes), and drops an embedded {@code :port} suffix so
     * the separate port field stays authoritative.
     */
    private static String normalizeHost(String raw) {
        if (raw == null) return "";
        String host = raw.trim();
        while (!host.isEmpty()) {
            if (startsWithIgnoreCase(host, "http://")) {
                host = host.substring(7).trim();
            } else if (startsWithIgnoreCase(host, "https://")) {
                host = host.substring(8).trim();
            } else {
                break;
            }
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash).trim();
        }
        if (!host.isEmpty() && !host.startsWith("[") && host.indexOf(':') >= 0) {
            int colon = host.lastIndexOf(':');
            if (isAllDigits(host.substring(colon + 1))) {
                host = host.substring(0, colon).trim();
            }
        }
        return host;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean isAllDigits(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    /** Human-readable {@code host:port} for summaries. */
    @NonNull
    public String displayAddress() {
        return host + ":" + port;
    }
}
