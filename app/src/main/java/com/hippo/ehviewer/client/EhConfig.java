/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

import com.hippo.network.InetValidator;

/**
 * Some configurable stuff about EH. It ends up cookie.
 */
public class EhConfig implements Cloneable {

    /**
     * The Cookie key of uconfig
     */
    public static final String KEY_UCONFIG = "uconfig";

    /**
     * The key of load images through the Hentai@Home Network
     * @see #LOAD_FROM_HAH_YES
     * @see #LOAD_FROM_HAH_NO
     */
    private static final String KEY_LOAD_FROM_HAH = "uh";
    /**
     * The key of Image Size Settings
     * @see #IMAGE_SIZE_AUTO
     * @see #IMAGE_SIZE_780X
     * @see #IMAGE_SIZE_980X
     * @see #IMAGE_SIZE_1280X
     * @see #IMAGE_SIZE_1600X
     * @see #IMAGE_SIZE_2400X
     */
    private static final String KEY_IMAGE_SIZE = "xr";
    /**
     * The key of scale images width
     */
    private static final String KEY_SCALE_WIDTH = "rx";
    /**
     * The key of scale images height
     */
    private static final String KEY_SCALE_HEIGHT = "ry";
    /**
     * The key of Gallery Name Display
     * @see #GALLERY_TITLE_DEFAULT
     * @see #GALLERY_TITLE_JAPANESE
     */
    private static final String KEY_GALLERY_TITLE = "tl";
    /**
     * The key of the behavior for downloading archiver
     * @see #ARCHIVER_DOWNLOAD_MAMS
     * @see #ARCHIVER_DOWNLOAD_MAAS
     * @see #ARCHIVER_DOWNLOAD_AAMS
     * @see #ARCHIVER_DOWNLOAD_AAAS
     */
    private static final String KEY_ARCHIVER_DOWNLOAD = "ar";
    /**
     * The key of display mode would you like to use on the front and search pages
     * @see #LAYOUT_MODE_LIST
     * @see #LAYOUT_MODE_THUMB
     */
    private static final String KEY_LAYOUT_MODE = "dm";
    /**
     * The key for show popular
     * @see #POPULAR_YES
     * @see #POPULAR_NO
     */
    private static final String KEY_POPULAR = "prn";
    /**
     * The key of categories would you like to view as default on the front page
     */
    private static final String KEY_DEFAULT_CATEGORIES = "cats";
    /**
     * The key for favorites sort
     * @see #FAVORITES_SORT_GALLERY_UPDATE_TIME
     * @see #FAVORITES_SORT_FAVORITED_TIME
     */
    private static final String KEY_FAVORITES_SORT = "fs";
    /**
     * The key of exclude certain namespaces from a default tag search
     */
    private static final String KEY_EXCLUDED_NAMESPACES = "xns";
    /**
     * The key of hide galleries in certain languages from the gallery list and searches
     */
    private static final String KEY_EXCLUDED_LANGUAGES = "xl";
    /**
     * The key of how many results would you like per page for the index/search page and torrent search pages
     * @see #RESULT_COUNT_25
     * @see #RESULT_COUNT_50
     * @see #RESULT_COUNT_100
     * @see #RESULT_COUNT_200
     */
    private static final String KEY_RESULT_COUNT = "rc";
    /**
     * The key of mouse-over thumb
     * @see #MOUSE_OVER_YES
     * @see #MOUSE_OVER_NO
     */
    private static final String KEY_MOUSE_OVER = "lt";
    /**
     * The key of preview size
     * @see #PREVIEW_SIZE_NORMAL
     * @see #PREVIEW_SIZE_LARGE
     */
    private static final String KEY_PREVIEW_SIZE = "ts";
    /**
     * The key of preview row per page
     * @see #PREVIEW_ROW_4
     * @see #PREVIEW_ROW_10
     * @see #PREVIEW_ROW_20
     * @see #PREVIEW_ROW_40
     */
    private static final String KEY_PREVIEW_ROW = "tr";
    /**
     * The key of sort order for gallery comments
     * @see #COMMENTS_SORT_OLDEST_FIRST
     * @see #COMMENTS_SORT_RECENT_FIRST
     * @see #COMMENTS_SORT_HIGHEST_SCORE_FIRST
     */
    private static final String KEY_COMMENTS_SORT = "cs";
    /**
     * The key of show gallery comment votes
     * @see #COMMENTS_VOTES_POP
     * @see #COMMENTS_VOTES_ALWAYS
     */
    private static final String KEY_COMMENTS_VOTES = "sc";
    /**
     * The key of sort order for gallery tags
     * @see #TAGS_SORT_ALPHABETICAL
     * @see #TAGS_SORT_POWER
     */
    private static final String KEY_TAGS_SORT = "to";
    /**
     * The key of show gallery page numbers
     * @see #SHOW_GALLERY_INDEX_NO
     * @see #SHOW_GALLERY_INDEX_YES
     */
    private static final String KEY_SHOW_GALLERY_INDEX = "pn";
    /**
     * The key of the IP:Port of a proxy-enabled Hentai@Home Client
     * to load all images
     */
    private static final String KEY_HAH_CLIENT_IP_PORT = "hp";
    /**
     * The key of the passkey of a proxy-enabled Hentai@Home Client
     * to load all images
     */
    private static final String KEY_HAH_CLIENT_PASSKEY = "hk";
    /**
     * The key of enable Tag Flagging
     * @see #ENABLE_TAG_FLAGGING_NO
     * @see #ENABLE_TAG_FLAGGING_YES
     */
    private static final String KEY_ENABLE_TAG_FLAGGING = "tf";
    /**
     * The key of always display the original images instead of the resampled versions
     * @see #ALWAYS_ORIGINAL_NO
     * @see #ALWAYS_ORIGINAL_YES
     */
    private static final String KEY_ALWAYS_ORIGINAL = "oi";
    /**
     * The key of enable the multi-Page Viewer
     * @see #MULTI_PAGE_NO
     * @see #MULTI_PAGE_YES
     */
    private static final String KEY_MULTI_PAGE = "qb";
    /**
     * The key of multi-Page Viewer Display Style
     * @see #MULTI_PAGE_STYLE_C
     * @see #MULTI_PAGE_STYLE_N
     * @see #MULTI_PAGE_STYLE_Y
     */
    private static final String KEY_MULTI_PAGE_STYLE = "ms";
    /**
     * The key of multi-Page Viewer Thumbnail Pane
     * @see #MULTI_PAGE_THUMB_HIDE
     * @see #MULTI_PAGE_THUMB_SHOW
     */
    private static final String KEY_MULTI_PAGE_THUMB = "mt";

    /**
     * The Cookie key of lofi resolution
     */
    public static final String KEY_LOFI_RESOLUTION = "xres";

    /**
     * The Cookie key of show warning
     */
    public static final String KEY_CONTENT_WARNING = "nw";

    /**
     * load images through the Hentai@Home Network
     */
    public static final String LOAD_FROM_HAH_YES = "y";
    /**
     * do not load images through the Hentai@Home Network
     */
    public static final String LOAD_FROM_HAH_NO = "n";

    /**
     * Image Size Auto
     */
    public static final String IMAGE_SIZE_AUTO = "a";
    /**
     * Image Size 780x
     */
    public static final String IMAGE_SIZE_780X = "780";
    /**
     * Image Size 980x
     */
    public static final String IMAGE_SIZE_980X = "980";
    /**
     * Image Size 1280x
     */
    public static final String IMAGE_SIZE_1280X = "1280";
    /**
     * Image Size 1600x
     */
    public static final String IMAGE_SIZE_1600X = "1600";
    /**
     * Image Size 2400x
     */
    public static final String IMAGE_SIZE_2400X = "2400";

    /**
     * Default gallery title
     */
    private static final String GALLERY_TITLE_DEFAULT = "r";
    /**
     * Japanese gallery title
     */
    private static final String GALLERY_TITLE_JAPANESE = "j";

    /**
     * Manual Accept, Manual Start
     */
    public static final String ARCHIVER_DOWNLOAD_MAMS = "0";
    /**
     * >Manual Accept, Auto Start
     */
    public static final String ARCHIVER_DOWNLOAD_AAMS = "1";
    /**
     * Auto Accept, Manual Start
     */
    public static final String ARCHIVER_DOWNLOAD_MAAS = "2";
    /**
     * Auto Accept, Auto Start
     */
    public static final String ARCHIVER_DOWNLOAD_AAAS = "3";

    /**
     * List View on the front and search pages
     */
    public static final String LAYOUT_MODE_LIST = "l";
    /**
     * Thumbnail View on the front and search pages
     */
    public static final String LAYOUT_MODE_THUMB = "t";

    /**
     * Show popular
     */
    private static final String POPULAR_YES = "y";
    /**
     * Don't show popular
     */
    private static final String POPULAR_NO = "n";

    public static final int MISC = 0x1;
    public static final int DOUJINSHI = 0x2;
    public static final int MANGA = 0x4;
    public static final int ARTIST_CG = 0x8;
    public static final int GAME_CG = 0x10;
    public static final int IMAGE_SET = 0x20;
    public static final int COSPLAY = 0x40;
    public static final int ASIAN_PORN = 0x80;
    public static final int NON_H = 0x100;
    public static final int WESTERN = 0x200;
    public static final int ALL_CATEGORY = 0x3ff;

    public static final int NAMESPACES_RECLASS = 0x1;
    public static final int NAMESPACES_LANGUAGE = 0x2;
    public static final int NAMESPACES_PARODY = 0x4;
    public static final int NAMESPACES_CHARACTER = 0x8;
    public static final int NAMESPACES_GROUP = 0x10;
    public static final int NAMESPACES_ARTIST = 0x20;
    public static final int NAMESPACES_MALE = 0x40;
    public static final int NAMESPACES_FEMALE = 0x80;

    public static final String JAPANESE_ORIGINAL = "0";
    public static final String JAPANESE_TRANSLATED = "1024";
    public static final String JAPANESE_REWRITE = "2048";
    public static final String ENGLISH_ORIGINAL = "1";
    public static final String ENGLISH_TRANSLATED = "1025";
    public static final String ENGLISH_REWRITE = "2049";
    public static final String CHINESE_ORIGINAL = "10";
    public static final String CHINESE_TRANSLATED = "1034";
    public static final String CHINESE_REWRITE = "2058";
    public static final String DUTCH_ORIGINAL = "20";
    public static final String DUTCH_TRANSLATED = "1044";
    public static final String DUTCH_REWRITE = "2068";
    public static final String FRENCH_ORIGINAL = "30";
    public static final String FRENCH_TRANSLATED = "1054";
    public static final String FRENCH_REWRITE = "2078";
    public static final String GERMAN_ORIGINAL = "40";
    public static final String GERMAN_TRANSLATED = "1064";
    public static final String GERMAN_REWRITE = "2088";
    public static final String HUNGARIAN_ORIGINAL = "50";
    public static final String HUNGARIAN_TRANSLATED = "1074";
    public static final String HUNGARIAN_REWRITE = "2098";
    public static final String ITALIAN_ORIGINAL = "60";
    public static final String ITALIAN_TRANSLATED = "1084";
    public static final String ITALIAN_REWRITE = "2108";
    public static final String KOREAN_ORIGINAL = "70";
    public static final String KOREAN_TRANSLATED = "1094";
    public static final String KOREAN_REWRITE = "2118";
    public static final String POLISH_ORIGINAL = "80";
    public static final String POLISH_TRANSLATED = "1104";
    public static final String POLISH_REWRITE = "2128";
    public static final String PORTUGUESE_ORIGINAL = "90";
    public static final String PORTUGUESE_TRANSLATED = "1114";
    public static final String PORTUGUESE_REWRITE = "2138";
    public static final String RUSSIAN_ORIGINAL = "100";
    public static final String RUSSIAN_TRANSLATED = "1124";
    public static final String RUSSIAN_REWRITE = "2148";
    public static final String SPANISH_ORIGINAL = "110";
    public static final String SPANISH_TRANSLATED = "1134";
    public static final String SPANISH_REWRITE = "2158";
    public static final String THAI_ORIGINAL = "120";
    public static final String THAI_TRANSLATED = "1144";
    public static final String THAI_REWRITE = "2168";
    public static final String VIETNAMESE_ORIGINAL = "130";
    public static final String VIETNAMESE_TRANSLATED = "1154";
    public static final String VIETNAMESE_REWRITE = "2178";
    public static final String NA_ORIGINAL = "254";
    public static final String NA_TRANSLATED = "1278";
    public static final String NA_REWRITE = "2302";
    public static final String OTHER_ORIGINAL = "255";
    public static final String OTHER_TRANSLATED = "1279";
    public static final String OTHER_REWRITE = "2303";

    /**
     * Sort favorites by last gallery update time
     */
    private static final String FAVORITES_SORT_GALLERY_UPDATE_TIME = "p";
    /**
     * Sort favorites by favorited time
     */
    private static final String FAVORITES_SORT_FAVORITED_TIME = "f";

    /**
     * 25 results per page for the index/search page and torrent search pages
     */
    public static final String RESULT_COUNT_25 = "0";
    /**
     * 50 results per page for the index/search page and torrent search pages
     */
    public static final String RESULT_COUNT_50 = "1";
    /**
     * 100 results per page for the index/search page and torrent search pages
     */
    public static final String RESULT_COUNT_100 = "2";
    /**
     * 200 results per page for the index/search page and torrent search pages
     */
    public static final String RESULT_COUNT_200 = "3";

    /**
     * On mouse-over
     */
    public static final String MOUSE_OVER_YES = "m";
    /**
     * On page load
     */
    public static final String MOUSE_OVER_NO = "p";

    /**
     * Preview normal size
     */
    public static final String PREVIEW_SIZE_NORMAL = "m";
    /**
     * Preview large size
     */
    public static final String PREVIEW_SIZE_LARGE = "l";

    /**
     * 4 row preview per page
     */
    public static final String PREVIEW_ROW_4 = "2";
    /**
     * 10 row preview per page
     */
    public static final String PREVIEW_ROW_10 = "5";
    /**
     * 20 row preview per page
     */
    public static final String PREVIEW_ROW_20 = "10";
    /**
     * 40 row preview per page
     */
    public static final String PREVIEW_ROW_40 = "20";

    /**
     * Oldest comments first
     */
    public static final String COMMENTS_SORT_OLDEST_FIRST = "a";
    /**
     * Recent comments first
     */
    public static final String COMMENTS_SORT_RECENT_FIRST = "d";
    /**
     * By highest score
     */
    public static final String COMMENTS_SORT_HIGHEST_SCORE_FIRST = "s";

    /**
     * Show gallery comment votes On score hover or click
     */
    public static final String COMMENTS_VOTES_POP = "0";
    /**
     * Always show gallery comment votes
     */
    public static final String COMMENTS_VOTES_ALWAYS = "1";

    /**
     * Sort order for gallery tags alphabetically
     */
    public static final String TAGS_SORT_ALPHABETICAL = "a";
    /**
     * Sort order for gallery tags by tag power
     */
    public static final String TAGS_SORT_POWER = "p";

    /**
     * Show gallery page numbers
     */
    public static final String SHOW_GALLERY_INDEX_YES = "1";
    /**
     * Do not show gallery page numbers
     */
    public static final String SHOW_GALLERY_INDEX_NO = "0";

    /**
     * Enable Tag Flagging
     */
    public static final String ENABLE_TAG_FLAGGING_YES = "y";
    /**
     * Do not enable Tag Flagging
     */
    public static final String ENABLE_TAG_FLAGGING_NO = "n";

    /**
     * Always display the original images
     */
    public static final String ALWAYS_ORIGINAL_YES = "y";
    /**
     * Do not Always display the original images
     */
    public static final String ALWAYS_ORIGINAL_NO = "n";

    /**
     * Enable the Multi-Page Viewe
     */
    public static final String MULTI_PAGE_YES = "y";
    /**
     * Do not enable the Multi-Page Viewe
     */
    public static final String MULTI_PAGE_NO = "n";

    /**
     * Align left, only scale if image is larger than browser width
     */
    public static final String MULTI_PAGE_STYLE_N = "n";
    /**
     * Align center, only scale if image is larger than browser width
     */
    public static final String MULTI_PAGE_STYLE_C = "c";
    /**
     * Align center, Always scale images to fit browser width
     */
    public static final String MULTI_PAGE_STYLE_Y = "y";

    /**
     * Show Multi-Page Viewer Thumbnail Pane
     */
    public static final String MULTI_PAGE_THUMB_SHOW = "n";
    /**
     * Hide Multi-Page Viewer Thumbnail Pane
     */
    public static final String MULTI_PAGE_THUMB_HIDE = "y";

    /**
     * 460x for lofi resolution
     */
    public static final String LOFI_RESOLUTION_460X = "1";

    /**
     * 780X for lofi resolution
     */
    public static final String LOFI_RESOLUTION_780X = "2";

    /**
     * 980X for lofi resolution
     */
    public static final String LOFI_RESOLUTION_980X = "3";

    /**
     * show warning
     */
    public static final String CONTENT_WARNING_SHOW = "0";

    /**
     * not show warning
     */
    public static final String CONTENT_WARNING_NOT_SHOW = "1";

    /**
     * Load images through the Hentai@Home Network<br/>
     * key: {@link #KEY_LOAD_FROM_HAH}<br/>
     * value: {@link #LOAD_FROM_HAH_YES}, {@link #LOAD_FROM_HAH_NO}
     */
    public String loadFromHAH = LOAD_FROM_HAH_YES;

    /**
     * Image Size<br/>
     * key: {@link #KEY_IMAGE_SIZE}<br/>
     * value: {@link #IMAGE_SIZE_AUTO}, {@link #IMAGE_SIZE_780X}, {@link #IMAGE_SIZE_980X},
     *        {@link #IMAGE_SIZE_1280X}, {@link #IMAGE_SIZE_1600X}, {@link #IMAGE_SIZE_2400X}
     */
    public String imageSize = IMAGE_SIZE_AUTO;

    /**
     * Scale width<br/>
     * key: {@link #KEY_SCALE_WIDTH}<br/>
     * value: 0 for no limit
     */
    public int scaleWidth = 0;

    /**
     * Scale height<br/>
     * key: {@link #KEY_SCALE_HEIGHT}<br/>
     * value: 0 for no limit
     */
    public int scaleHeight = 0;

    /**
     * Gallery title<br/>
     * key: {@link #KEY_GALLERY_TITLE}<br/>
     * value: {@link #GALLERY_TITLE_DEFAULT}, {@link #GALLERY_TITLE_JAPANESE}
     */
    public String galleryTitle = GALLERY_TITLE_DEFAULT;

    /**
     * The default behavior for downloading an archiver<br/>
     * key: {@link #KEY_ARCHIVER_DOWNLOAD}<br/>
     * value: {@link #ARCHIVER_DOWNLOAD_MAMS}, {@link #ARCHIVER_DOWNLOAD_AAMS},
     * {@link #ARCHIVER_DOWNLOAD_MAAS}, {@link #ARCHIVER_DOWNLOAD_AAAS}
     */
    public String archiverDownload = ARCHIVER_DOWNLOAD_MAMS;

    /**
     * Display mode used on the front and search pages<br/>
     * false for list, true for thumb<br/>
     * key: {@link #KEY_LAYOUT_MODE}<br/>
     * value: {@link #LAYOUT_MODE_LIST}, {@link #LAYOUT_MODE_THUMB}
     */
    public String layoutMode = LAYOUT_MODE_LIST;

    /**
     * Show popular or not<br/>
     * key: {@link #KEY_POPULAR}<br/>
     * value: {@link #POPULAR_YES}, {@link #POPULAR_NO}
     */
    public String popular = POPULAR_YES;

    /**
     * Default categories on the front page<br/>
     * key: {@link #KEY_DEFAULT_CATEGORIES}<br/>
     * value: the value of categories, for multiple use & operation,
     * 0 for none
     */
    public int defaultCategories = 0;

    /**
     * <br/>
     * key: {@link #KEY_FAVORITES_SORT}<br/>
     * value: {@link #FAVORITES_SORT_GALLERY_UPDATE_TIME}, {@link #FAVORITES_SORT_FAVORITED_TIME}
     */
    public String favoritesSort = FAVORITES_SORT_FAVORITED_TIME;

    /**
     * Certain namespaces excluded from a default tag search<br/>
     * key: {@link #KEY_EXCLUDED_NAMESPACES}<br/>
     * value: the value of namespaces, for multiple use & operation,
     * 0 for none
     */
    public int excludedNamespaces = 0;

    /**
     * Certain languages excluded from list and searches<br/>
     * key: {@link #KEY_EXCLUDED_LANGUAGES}<br/>
     * value: {@link #JAPANESE_TRANSLATED}, {@link #JAPANESE_REWRITE} ...
     * For multiple languages, use <code>x<code/> to combine them, like 1x1024x2048
     */
    public String excludedLanguages = "";

    /**
     * How many results would you like per page for the index/search page
     * and torrent search pages<br/>
     * key: {@link #KEY_RESULT_COUNT}<br/>
     * value: {@link #RESULT_COUNT_25}, {@link #RESULT_COUNT_50},
     * {@link #RESULT_COUNT_100}, {@link #RESULT_COUNT_200}<br/>
     * Require <code>Hath Perk:Paging Enlargement</code>
     */
    public String resultCount = RESULT_COUNT_25;

    /**
     * mouse-over thumb<br/>
     * key: {@link #KEY_MOUSE_OVER}<br/>
     * value: {@link #MOUSE_OVER_YES}, {@link #MOUSE_OVER_NO}
     */
    public String mouseOver = MOUSE_OVER_YES;

    /**
     * Default preview mode<br/>
     * key: {@link #KEY_PREVIEW_SIZE}<br/>
     * value: {@link #PREVIEW_SIZE_NORMAL}, {@link #PREVIEW_SIZE_LARGE}
     */
    public String previewSize = PREVIEW_SIZE_LARGE;

    /**
     * Preview row<br/>
     * key: {@link #KEY_PREVIEW_ROW}<br/>
     * value: {@link #PREVIEW_ROW_4}, {@link #PREVIEW_ROW_10},
     * {@link #PREVIEW_ROW_20}, {@link #PREVIEW_ROW_40}
     */
    public String previewRow = PREVIEW_ROW_4;

    /**
     * Sort order for gallery comments<br/>
     * key: {@link #KEY_COMMENTS_SORT}<br/>
     * value: {@link #COMMENTS_SORT_OLDEST_FIRST}, {@link #COMMENTS_SORT_RECENT_FIRST},
     * {@link #COMMENTS_SORT_HIGHEST_SCORE_FIRST}
     */
    public String commentSort = COMMENTS_SORT_OLDEST_FIRST;

    /**
     * Show gallery comment votes mode<br/>
     * key: {@link #KEY_COMMENTS_VOTES}<br/>
     * value: {@link #COMMENTS_VOTES_POP}, {@link #COMMENTS_VOTES_ALWAYS}
     */
    public String commentVotes = COMMENTS_VOTES_POP;


    /**
     * Sort order for gallery tags<br/>
     * key: {@link #KEY_TAGS_SORT}<br/>
     * value: {@link #TAGS_SORT_ALPHABETICAL}, {@link #TAGS_SORT_POWER}
     */
    public String tagSort = TAGS_SORT_ALPHABETICAL;

    /**
     * Show gallery page numbers<br/>
     * key: {@link #KEY_SHOW_GALLERY_INDEX}<br/>
     * value: {@link #SHOW_GALLERY_INDEX_YES}, {@link #SHOW_GALLERY_INDEX_NO}
     */
    public String showGalleryIndex = SHOW_GALLERY_INDEX_YES;

    /**
     * The IP of a proxy-enabled Hentai@Home Client
     * to load all images<br/>
     * key: {@link #KEY_HAH_CLIENT_IP_PORT}<br/>
     */
    public String hahClientIp = "";

    /**
     * The PORT of a proxy-enabled Hentai@Home Client
     * to load all images<br/>
     * key: {@link #KEY_HAH_CLIENT_IP_PORT}<br/>
     */
    public int hahClientPort = -1;

    /**
     * The passkey of a proxy-enabled Hentai@Home Client
     * to load all images<br/>
     * key: {@link #KEY_HAH_CLIENT_PASSKEY}<br/>
     */
    public String hahClientPasskey = "";

    /**
     * Enable tag flagging
     * key: {@link #KEY_ENABLE_TAG_FLAGGING}<br/>
     * value: {@link #ENABLE_TAG_FLAGGING_YES}, {@link #ENABLE_TAG_FLAGGING_NO}<br/>
     * <code>Bronze Star</code> or <code>Hath Perk: Tag Flagging</code> Required
     */
    public String enableTagFlagging = ENABLE_TAG_FLAGGING_NO;

    /**
     * Always display the original images instead of the resampled versions<br/>
     * key: {@link #KEY_ALWAYS_ORIGINAL}<br/>
     * value: {@link #ALWAYS_ORIGINAL_YES}, {@link #ALWAYS_ORIGINAL_NO}<br/>
     * <code>Silver Star</code> or <code>Hath Perk: Source Nexus</code> Required
     */
    public String alwaysOriginal = ALWAYS_ORIGINAL_NO;

    /**
     * Enable the multi-Page Viewer<br/>
     * key: {@link #KEY_MULTI_PAGE}<br/>
     * value: {@link #MULTI_PAGE_YES}, {@link #MULTI_PAGE_NO}<br/>
     * <code>Gold Star</code> or <code>Hath Perk: Multi-Page Viewer</code> Required
     */
    public String multiPage = MULTI_PAGE_NO;

    /**
     * Multi-Page Viewer Display Style<br/>
     * key: {@link #KEY_MULTI_PAGE_STYLE}<br/>
     * value: {@link #MULTI_PAGE_STYLE_N}, {@link #MULTI_PAGE_STYLE_C},
     * {@link #MULTI_PAGE_STYLE_Y}<br/>
     * <code>Gold Star</code> or <code>Hath Perk: Multi-Page Viewer</code> Required
     */
    public String multiPageStyle = MULTI_PAGE_STYLE_N;

    /**
     * Multi-Page Viewer Thumbnail Pane<br/>
     * key: {@link #KEY_MULTI_PAGE_THUMB}<br/>
     * value: {@link #MULTI_PAGE_THUMB_SHOW}, {@link #MULTI_PAGE_THUMB_HIDE}<br/>
     * <code>Gold Star</code> or <code>Hath Perk: Multi-Page Viewer</code> Required
     */
    public String multiPageThumb = MULTI_PAGE_THUMB_SHOW;

    /**
     * Lofi resolution
     * key: {@link #KEY_LOFI_RESOLUTION}<br/>
     * value: {@link #LOFI_RESOLUTION_460X}, {@link #LOFI_RESOLUTION_780X},
     * {@link #LOFI_RESOLUTION_980X}
     */
    public String lofiResolution = LOFI_RESOLUTION_980X;

    /**
     * Show content warning
     * key: {@link #KEY_CONTENT_WARNING}<br/>
     * value: {@link #CONTENT_WARNING_SHOW}, {@link #CONTENT_WARNING_NOT_SHOW}
     */
    public String contentWarning = CONTENT_WARNING_NOT_SHOW;

    private String mUconfig;

    private boolean mDirty = true;

    @Override
    public EhConfig clone() {
        try {
            return (EhConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void setDirty() {
        mDirty = true;
    }

    private void updateUconfig() {
        String hahClientIpPort = (InetValidator.isValidInet4Address(hahClientIp) &&
                InetValidator.isValidInetPort(hahClientPort)) ?
                hahClientIp + "%3A" + hahClientPort : "";
        String hahClientPasskey = null == this.hahClientPasskey ? "" : this.hahClientPasskey;
        mUconfig = KEY_LOAD_FROM_HAH + "_" + loadFromHAH + "-" +
                KEY_IMAGE_SIZE + "_" + imageSize + "-" +
                KEY_SCALE_WIDTH + "_" + scaleWidth + "-" +
                KEY_SCALE_HEIGHT + "_" + scaleHeight + "-" +
                KEY_GALLERY_TITLE + "_" + galleryTitle + "-" +
                KEY_ARCHIVER_DOWNLOAD + "_" + archiverDownload + "-" +
                KEY_LAYOUT_MODE + "_" + layoutMode + "-" +
                KEY_POPULAR + "_" + popular + "-" +
                KEY_DEFAULT_CATEGORIES + "_" + defaultCategories + "-" +
                KEY_FAVORITES_SORT + "_" + favoritesSort + "-" +
                KEY_EXCLUDED_NAMESPACES + "_" + excludedNamespaces + "-" +
                KEY_EXCLUDED_LANGUAGES + "_" + excludedLanguages + "-" +
                KEY_RESULT_COUNT + "_" + resultCount + "-" +
                KEY_MOUSE_OVER + "_" + mouseOver + "-" +
                KEY_PREVIEW_SIZE + "_" + previewSize + "-" +
                KEY_PREVIEW_ROW + "_" + previewRow + "-" +
                KEY_COMMENTS_SORT + "_" + commentSort + "-" +
                KEY_COMMENTS_VOTES + "_" + commentVotes + "-" +
                KEY_TAGS_SORT + "_" + tagSort + "-" +
                KEY_SHOW_GALLERY_INDEX + "_" + showGalleryIndex + "-" +
                KEY_HAH_CLIENT_IP_PORT + "_" + hahClientIpPort + "-" +
                KEY_HAH_CLIENT_PASSKEY + "_" + hahClientPasskey + "-" +
                KEY_ENABLE_TAG_FLAGGING + "_" + enableTagFlagging + "-" +
                KEY_ALWAYS_ORIGINAL + "_" + alwaysOriginal + "-" +
                KEY_MULTI_PAGE + "_" + multiPage + "-" +
                KEY_MULTI_PAGE_STYLE + "_" + multiPageStyle + "-" +
                KEY_MULTI_PAGE_THUMB + "_" + multiPageThumb;
    }

    public synchronized String uconfig() {
        if (mDirty) {
            mDirty = false;
            updateUconfig();
        }

        return mUconfig;
    }
}
