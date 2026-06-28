package com.hippo.ehviewer;

public class Settings {
    public static final int SITE_E = 0;
    public static final int SITE_EX = 1;
    public static final int SITE_EP = 2;
    public static boolean getBoolean(String key, boolean defValue) { return defValue; }
    public static void putBoolean(String key, boolean value) {}
    public static int getInt(String key, int defValue) { return defValue; }
    public static void putInt(String key, int value) {}
    public static String getString(String key, String defValue) { return defValue; }
    public static void putString(String key, String value) {}
    public static int getGallerySite() { return SITE_E; }
    public static boolean getFixThumbUrl() { return false; }
    public static boolean getShowTagTranslations() { return false; }
    public static boolean getSaveParseErrorBody() { return false; }
    public static boolean getShowGalleryPages() { return true; }
    public static boolean isLogin() { return false; }
}
