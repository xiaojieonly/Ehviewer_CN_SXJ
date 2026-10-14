package com.hippo.ehviewer.util;

import com.hippo.ehviewer.client.EhTagDatabase;

public final class TagSearchQueryHelper {

    private TagSearchQueryHelper() {
    }

    public static boolean shouldUseTagSearch(String keyword) {
        if (keyword == null) {
            return false;
        }

        String normalized = keyword.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        if (normalized.indexOf('"') >= 0 ||
                normalized.indexOf('$') >= 0 ||
                normalized.indexOf('|') >= 0 ||
                normalized.indexOf('~') >= 0) {
            return true;
        }

        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex <= 0) {
            return false;
        }

        String prefix = normalized.substring(0, namespaceIndex + 1);
        return EhTagDatabase.prefixToNamespace(prefix) != null;
    }

    public static String normalizeSpecifiedTagQuery(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim();
    }
}
