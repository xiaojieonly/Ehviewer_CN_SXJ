package com.hippo.anotherviewer;

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

    /** Runtime Gallery Site selection consumed by SiteUrl host picking. */
    private static volatile int sGallerySite = SITE_E;

    public static int getGallerySite() { return sGallerySite; }

    /** Switch the Gallery Site selection. Only SITE_E / SITE_EX are accepted. */
    public static void setGallerySite(int site) {
        if (site != SITE_E && site != SITE_EX) {
            throw new IllegalArgumentException("Invalid gallery site: " + site);
        }
        sGallerySite = site;
    }
    public static boolean getFixThumbUrl() { return false; }
    public static boolean getShowTagTranslations() { return false; }
    public static boolean getSaveParseErrorBody() { return false; }
    public static boolean getShowGalleryPages() { return true; }
    public static boolean isLogin() { return false; }
}
