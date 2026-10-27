package com.hippo.ehviewer.server.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.unifile.UniFile;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class PathValidator {

    private PathValidator() {
    }

    @Nullable
    public static UniFile resolveRelativePath(@NonNull UniFile root, @Nullable String requestPath) {
        if (requestPath == null || requestPath.isEmpty() || "/".equals(requestPath)) {
            return root;
        }

        String path = normalizePath(requestPath);
        if (path.isEmpty()) {
            return root;
        }

        UniFile current = root;
        String[] segments = path.split("/");
        for (String rawSegment : segments) {
            if (rawSegment.isEmpty()) {
                continue;
            }
            String segment = decode(rawSegment);
            if (!isSafeSegment(segment)) {
                return null;
            }
            current = current.findFile(segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    @NonNull
    public static String encodePath(@NonNull String relativePath) {
        String[] segments = normalizePath(relativePath).split("/");
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(encode(segment));
        }
        return sb.toString();
    }

    @NonNull
    public static String normalizePath(@Nullable String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.replace('\\', '/').trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private static boolean isSafeSegment(@Nullable String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        if (".".equals(segment) || "..".equals(segment)) {
            return false;
        }
        return !(segment.contains("/") || segment.contains("\\"));
    }

    @NonNull
    private static String decode(@NonNull String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    @NonNull
    private static String encode(@NonNull String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
