package com.hippo.anotherviewer.client;

import com.hippo.anotherviewer.dao.QuickSearch;

public class SiteUtils {
    public static final int NONE = 0;
    public static final int UNKNOWN = -1;

    public static int getCategory(String category) { return 0; }
    public static String getCategory(int category) { return ""; }
    public static boolean contains(int category, int target) { return (category & target) == target; }
    public static String getDefaultGalleryDetailUrl(long gid, String token) { return ""; }

    public static String handleThumbUrlResolution(String url) {
        if (url == null) return null;
        return url.replace("ehgt.org", "ehgt.org");
    }
}
