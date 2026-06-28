package com.hippo.ehviewer.client;

public class EhConfig {
    public static final int NONE = 0;
    public static final int ALL_CATEGORY = 1023;
    public static final int DOUJINSHI = 1;
    public static final int MANGA = 2;
    public static final int ARTIST_CG = 4;
    public static final int GAME_CG = 8;
    public static final int WESTERN = 16;
    public static final int NON_H = 32;
    public static final int IMAGE_SET = 64;
    public static final int COSPLAY = 128;
    public static final int ASIAN_PORN = 256;
    public static final int MISC = 512;

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
