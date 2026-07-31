package com.hippo.ehviewer.client;

public class EhConfig {
    public static final int NONE = 0;
    public static final int ALL_CATEGORY = 1023;
    // Bit values MUST match the Android app's EhConfig (app/.../client/EhConfig.java):
    // f_cats on the site is encoded from these bits, and WebUI data (sync,
    // favorites/history/download DTOs) is interpreted with the same mapping.
    public static final int MISC = 1;
    public static final int DOUJINSHI = 2;
    public static final int MANGA = 4;
    public static final int ARTIST_CG = 8;
    public static final int GAME_CG = 16;
    public static final int IMAGE_SET = 32;
    public static final int COSPLAY = 64;
    public static final int ASIAN_PORN = 128;
    public static final int NON_H = 256;
    public static final int WESTERN = 512;

    public static final int RATING_UNKNOW = 0;
    public static final int RATING_1 = 1;
    public static final int RATING_2 = 2;
    public static final int RATING_3 = 3;
    public static final int RATING_4 = 4;
    public static final int RATING_5 = 5;

    public int advanceSearch;
    public boolean searchGalleryName = true;
    public boolean searchTags = true;
    public boolean searchComments;
    public boolean searchExpunged;
    public int minRating;
    public int pages;
}
