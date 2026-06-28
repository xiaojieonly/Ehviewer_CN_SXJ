package com.hippo.ehviewer.client;

public class EhCacheKeyFactory {
    public static String getPreviewKey(long gid, int index) { return gid + "_" + index; }
    public static String getImageKey(long gid, int index) { return gid + "_img_" + index; }
    public static String getLargePreviewKey(long gid, int index) { return gid + "_lp_" + index; }
}
