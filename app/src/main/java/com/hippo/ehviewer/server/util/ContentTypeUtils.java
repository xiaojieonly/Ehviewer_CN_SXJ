package com.hippo.ehviewer.server.util;

import androidx.annotation.NonNull;

import java.net.URLConnection;

public final class ContentTypeUtils {

    private ContentTypeUtils() {
    }

    @NonNull
    public static String fromName(@NonNull String name) {
        String type = URLConnection.guessContentTypeFromName(name);
        if (type == null || type.isEmpty()) {
            return "application/octet-stream";
        }
        return type;
    }

    public static boolean isImage(@NonNull String mimeType) {
        return mimeType.startsWith("image/");
    }
}
