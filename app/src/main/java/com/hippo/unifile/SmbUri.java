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

package com.hippo.unifile;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SmbUri {

    public static final String SCHEME = "smb";
    public static final int DEFAULT_PORT = 445;

    private final String host;
    private final int port;
    private final String share;
    private final String path;

    private SmbUri(String host, int port, String share, String path) {
        this.host = host;
        this.port = port;
        this.share = share;
        this.path = path;
    }

    @NonNull
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @NonNull
    public String getShare() {
        return share;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    @NonNull
    public Uri toUri() {
        Uri.Builder builder = new Uri.Builder()
                .scheme(SCHEME)
                .encodedAuthority(authority())
                .appendEncodedPath(share);
        if (!path.isEmpty()) {
            for (String segment : path.split("/")) {
                if (!segment.isEmpty()) {
                    builder.appendEncodedPath(segment);
                }
            }
        }
        return builder.build();
    }

    @NonNull
    public String authority() {
        return port == DEFAULT_PORT ? host : host + ":" + port;
    }

    @NonNull
    public String displayPath() {
        return TextUtils.isEmpty(path) ? "" : "/" + path;
    }

    @NonNull
    public static SmbUri create(String host, int port, String share, @Nullable String path) {
        if (TextUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Host is empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port is invalid");
        }
        if (TextUtils.isEmpty(share)) {
            throw new IllegalArgumentException("Share is empty");
        }
        String normalizedPath = normalizePath(path);
        for (String segment : normalizedPath.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Path is invalid");
            }
        }
        return new SmbUri(host.trim(), port, share.trim(), normalizedPath);
    }

    @Nullable
    public static SmbUri parse(@Nullable Uri uri) {
        if (uri == null || !SCHEME.equals(uri.getScheme())) {
            return null;
        }
        String userInfo = uri.getUserInfo();
        if (!TextUtils.isEmpty(userInfo)) {
            return null;
        }
        String host = uri.getHost();
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        int port = uri.getPort();
        if (port < 0) {
            port = DEFAULT_PORT;
        }
        List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) {
            return null;
        }
        String share = segments.get(0);
        if (TextUtils.isEmpty(share)) {
            return null;
        }
        List<String> pathSegments = new ArrayList<>();
        for (int i = 1; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (!TextUtils.isEmpty(segment) && !"..".equals(segment)) {
                pathSegments.add(segment);
            }
        }
        try {
            return create(host, port, share, joinSegments(pathSegments));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static SmbUri parse(@Nullable String uri) {
        if (TextUtils.isEmpty(uri)) {
            return null;
        }
        return parse(Uri.parse(uri));
    }

    private static String normalizePath(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String trimmed = path.replace('\\', '/').trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String joinSegments(List<String> segments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0, n = segments.size(); i < n; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(segments.get(i));
        }
        return builder.toString();
    }
}
