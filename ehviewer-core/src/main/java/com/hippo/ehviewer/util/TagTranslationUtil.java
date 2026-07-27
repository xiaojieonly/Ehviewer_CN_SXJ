package com.hippo.ehviewer.util;

public class TagTranslationUtil {
    public static String getTagCN(String[] parts, String ehTags) {
        if (parts == null || parts.length < 2) return "";
        return parts[1];
    }
}
