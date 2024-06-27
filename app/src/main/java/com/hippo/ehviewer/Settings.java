/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.ehviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.client.EhConfig;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.FavListUrlBuilder;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.ehviewer.ui.scene.gallery.list.GalleryListScene;
import com.hippo.lib.glgallery.GalleryView;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.NumberUtils;
import java.io.File;
import java.util.Locale;

public class Settings {

    private static final String TAG = Settings.class.getSimpleName();

    @SuppressLint("StaticFieldLeak")
    private static Context sContext;
    private static SharedPreferences sSettingsPre;
    private static EhConfig sEhConfig;

    public static void initialize(Context context) {
        sContext = context.getApplicationContext();
        sSettingsPre = PreferenceManager.getDefaultSharedPreferences(sContext);
        sEhConfig = loadEhConfig();
        fixDefaultValue();
    }

    private static void fixDefaultValue() {
        // Enable builtin hosts if the country is CN
        if (!sSettingsPre.contains(KEY_BUILT_IN_HOSTS)) {
            if ("CN".equals(Locale.getDefault().getCountry())) {
                putBuiltInHosts(true);
                putBuiltEXHosts(true);
            }
        }
        if (!sSettingsPre.contains(KEY_DOMAIN_FRONTING)) {
            if ("CN".equals(Locale.getDefault().getCountry())) {
                putDF(true);
            }
        }

    }

    private static EhConfig loadEhConfig() {
        EhConfig ehConfig= new EhConfig();
        ehConfig.imageSize = getImageResolution();
        ehConfig.excludedLanguages = getExcludedLanguages();
        ehConfig.defaultCategories = getDefaultCategories();
        ehConfig.excludedNamespaces = getExcludedTagNamespaces();
        ehConfig.setDirty();
        return ehConfig;
    }

    public static boolean getBoolean(String key, boolean defValue) {
        try {
            return sSettingsPre.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            Log.d(TAG, "Get ClassCastException when get " + key + " value", e);
            return defValue;
        }
    }

    public static void putBoolean(String key, boolean value) {
        sSettingsPre.edit().putBoolean(key, value).apply();
    }

    public static int getInt(String key, int defValue) {
        try {
            return sSettingsPre.getInt(key, defValue);
        } catch (ClassCastException e) {
            Log.d(TAG, "Get ClassCastException when get " + key + " value", e);
            return defValue;
        }
    }

    public static void putInt(String key, int value) {
        sSettingsPre.edit().putInt(key, value).apply();
    }

    public static long getLong(String key, long defValue) {
        try {
            return sSettingsPre.getLong(key, defValue);
        } catch (ClassCastException e) {
            Log.d(TAG, "Get ClassCastException when get " + key + " value", e);
            return defValue;
        }
    }

    public static void putLong(String key, long value) {
        sSettingsPre.edit().putLong(key, value).apply();
    }

    public static float getFloat(String key, float defValue) {
        try {
            return sSettingsPre.getFloat(key, defValue);
        } catch (ClassCastException e) {
            Log.d(TAG, "Get ClassCastException when get " + key + " value", e);
            return defValue;
        }
    }

    public static void putFloat(String key, float value) {
        sSettingsPre.edit().putFloat(key, value).apply();
    }

    public static String getString(String key, String defValue) {
        try {
            return sSettingsPre.getString(key, defValue);
        } catch (ClassCastException e) {
            Log.d(TAG, "Get ClassCastException when get " + key + " value", e);
            return defValue;
        }
    }

    public static void putString(String key, String value) {
        sSettingsPre.edit().putString(key, value).apply();
    }

    public static int getIntFromStr(String key, int defValue) {
        try {
            return NumberUtils.parseIntSafely(sSettingsPre.getString(key, Integer.toString(defValue)), defValue);
        } catch (ClassCastException e) {
            Log.d(TAG, "Get ClassCastException when get " + key + " value", e);
            return defValue;
        }
    }

    public static void putIntToStr(String key, int value) {
        sSettingsPre.edit().putString(key, Integer.toString(value)).apply();
    }

    private static final String KEY_VERSION_CODE = "version_code";
    private static final int DEFAULT_VERSION_CODE = 0;

    public static int getVersionCode() {
        return getInt(KEY_VERSION_CODE, DEFAULT_VERSION_CODE);
    }

    public static void putVersionCode(int value) {
        putInt(KEY_VERSION_CODE, value);
    }

    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String DEFAULT_DISPLAY_NAME = null;

    public static String getDisplayName() {
        return getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME);
    }

    public static void putDisplayName(String value) {
        putString(KEY_DISPLAY_NAME, value);
    }

    private static final String KEY_AVATAR = "avatar";
    private static final String DEFAULT_AVATAR = null;

    public static String getAvatar() {
        return getString(KEY_AVATAR, DEFAULT_AVATAR);
    }

    public static void putAvatar(String value) {
        putString(KEY_AVATAR, value);
    }

    private static final String KEY_SHOW_WARNING = "show_warning";
    private static final boolean DEFAULT_SHOW_WARNING = true;

    public static boolean getShowWarning() {
        return getBoolean(KEY_SHOW_WARNING, DEFAULT_SHOW_WARNING);
    }

    public static void putShowWarning(boolean value) {
        putBoolean(KEY_SHOW_WARNING, value);
    }

    private static final String KEY_REMOVE_IMAGE_FILES = "include_pic";
    private static final boolean DEFAULT_REMOVE_IMAGE_FILES = true;

    public static boolean getRemoveImageFiles() {
        return getBoolean(KEY_REMOVE_IMAGE_FILES, DEFAULT_REMOVE_IMAGE_FILES);
    }

    public static void putRemoveImageFiles(boolean value) {
        putBoolean(KEY_REMOVE_IMAGE_FILES, value);
    }

    public static EhConfig getEhConfig() {
        return sEhConfig;
    }

    private static final String KEY_NEED_SIGN_IN = "need_sign_in";
    private static final boolean DEFAULT_NEED_SIGN_IN = true;

    public static boolean getNeedSignIn() {
        return getBoolean(KEY_NEED_SIGN_IN, DEFAULT_NEED_SIGN_IN);
    }

    public static void putNeedSignIn(boolean value) {
        putBoolean(KEY_NEED_SIGN_IN, value);
    }

    private static final String KEY_SELECT_SITE = "select_site";
    private static final boolean DEFAULT_SELECT_SITE = true;

    public static boolean getSelectSite() {
        return getBoolean(KEY_SELECT_SITE, DEFAULT_SELECT_SITE);
    }

    public static void putSelectSite(boolean value) {
        putBoolean(KEY_SELECT_SITE, value);
    }

    private static final String KEY_QUICK_SEARCH_TIP = "quick_search_tip";
    private static final boolean DEFAULT_QUICK_SEARCH_TIP = true;

    public static boolean getQuickSearchTip() {
        return getBoolean(KEY_QUICK_SEARCH_TIP, DEFAULT_QUICK_SEARCH_TIP);
    }

    public static void putQuickSearchTip(boolean value) {
        putBoolean(KEY_QUICK_SEARCH_TIP, value);
    }

    /********************
     ****** Eh
     ********************/

    public static final String KEY_THEME = "theme";
    public static final String KEY_THEME_DARK = "theme";
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_BLACK = 2;
    private static final int DEFAULT_THEME = THEME_LIGHT;

//    private static boolean overSet = false;

//    public static int getTheme(Context context) {
    public static int getTheme() {
//        if (getDarkModeStatus(context) && !overSet){
//            return THEME_BLACK;
//        }
        return getIntFromStr(KEY_THEME, DEFAULT_THEME);
    }

    public static void putTheme(int theme) {
//        if (!overSet){
//            overSet = true;
//            System.out.println("覆盖主题");
//        }
        putIntToStr(KEY_THEME, theme);
    }

    //检查当前系统是否已开启暗黑模式
    public static boolean getDarkModeStatus(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static final String KEY_APPLY_NAV_BAR_THEME_COLOR = "apply_nav_bar_theme_color";
    private static final boolean DEFAULT_APPLY_NAV_BAR_THEME_COLOR = true;

    public static boolean getApplyNavBarThemeColor() {
        return getBoolean(KEY_APPLY_NAV_BAR_THEME_COLOR, DEFAULT_APPLY_NAV_BAR_THEME_COLOR);
    }

    public static final String KEY_GALLERY_SITE = "gallery_site";
    private static final int DEFAULT_GALLERY_SITE = 0;

    public static int getGallerySite() {
        return getIntFromStr(KEY_GALLERY_SITE, DEFAULT_GALLERY_SITE);
    }

    public static void putGallerySite(int value) {
        putIntToStr(KEY_GALLERY_SITE, value);
    }

    private static final String KEY_LAUNCH_PAGE = "launch_page";
    private static final int DEFAULT_LAUNCH_PAGE = 0;

    public static String getLaunchPageGalleryListSceneAction() {
        int value = getIntFromStr(KEY_LAUNCH_PAGE, DEFAULT_LAUNCH_PAGE);
        switch (value) {
            default:
            case 0:
                return GalleryListScene.ACTION_HOMEPAGE;
            case 1:
                return GalleryListScene.ACTION_SUBSCRIPTION;
            case 2:
                return GalleryListScene.ACTION_WHATS_HOT;
        }
    }

    public static final String KEY_LIST_MODE = "list_mode";
    private static final int DEFAULT_LIST_MODE = 0;

    public static int getListMode() {
        return getIntFromStr(KEY_LIST_MODE, DEFAULT_LIST_MODE);
    }

    public static final String KEY_DETAIL_SIZE = "detail_size";
    private static final int DEFAULT_DETAIL_SIZE = 0;

    public static int getDetailSize() {
        return getIntFromStr(KEY_DETAIL_SIZE, DEFAULT_DETAIL_SIZE);
    }

    @DimenRes
    public static int getDetailSizeResId() {
        switch (getDetailSize()) {
            default:
            case 0:
                return R.dimen.gallery_list_column_width_long;
            case 1:
                return R.dimen.gallery_list_column_width_short;
        }
    }

    public static final String KEY_THUMB_SIZE = "thumb_size";
    private static final int DEFAULT_THUMB_SIZE = 1;

    public static int getThumbSize() {
        return getIntFromStr(KEY_THUMB_SIZE, DEFAULT_THUMB_SIZE);
    }

    @DimenRes
    public static int getThumbSizeResId() {
        switch (getThumbSize()) {
            case 0:
                return R.dimen.gallery_grid_column_width_large;
            default:
            case 1:
                return R.dimen.gallery_grid_column_width_middle;
            case 2:
                return R.dimen.gallery_grid_column_width_small;
        }
    }

    public static final String KEY_THUMB_RESOLUTION = "thumb_resolution";
    private static final int DEFAULT_THUMB_RESOLUTION = 0;

    public static int getThumbResolution() {
        return getIntFromStr(KEY_THUMB_RESOLUTION, DEFAULT_THUMB_RESOLUTION);
    }

    private static final String KEY_FIX_THUMB_URL = "fix_thumb_url";
    private static final boolean DEFAULT_FIX_THUMB_URL = false;

    public static boolean getFixThumbUrl() {
        return getBoolean(KEY_FIX_THUMB_URL, DEFAULT_FIX_THUMB_URL);
    }

    private static final String KEY_SHOW_JPN_TITLE = "show_jpn_title";
    private static final boolean DEFAULT_SHOW_JPN_TITLE = false;

    public static boolean getShowJpnTitle() {
        return getBoolean(KEY_SHOW_JPN_TITLE, DEFAULT_SHOW_JPN_TITLE);
    }

    private static final String KEY_SHOW_GALLERY_PAGES = "show_gallery_pages";
    private static final boolean DEFAULT_SHOW_GALLERY_PAGES = false;

    public static boolean getShowGalleryPages() {
        return getBoolean(KEY_SHOW_GALLERY_PAGES, DEFAULT_SHOW_GALLERY_PAGES);
    }

    public static final String KEY_SHOW_TAG_TRANSLATIONS = "show_tag_translations";
    private static final boolean DEFAULT_SHOW_TAG_TRANSLATIONS = true;

    /**
     * 是否展示翻译的标签
     * @return boolean
     */
    public static boolean getShowTagTranslations() {
        return getBoolean(KEY_SHOW_TAG_TRANSLATIONS, DEFAULT_SHOW_TAG_TRANSLATIONS);
    }

    public static final String KEY_DEFAULT_CATEGORIES = "default_categories";
    public static final int DEFAULT_DEFAULT_CATEGORIES = EhUtils.ALL_CATEGORY;

    public static int getDefaultCategories() {
        return getInt(KEY_DEFAULT_CATEGORIES, DEFAULT_DEFAULT_CATEGORIES);
    }

    public static void putDefaultCategories(int value) {
        sEhConfig.defaultCategories = value;
        sEhConfig.setDirty();
        putInt(KEY_DEFAULT_CATEGORIES, value);
    }

    public static final String KEY_EXCLUDED_TAG_NAMESPACES = "excluded_tag_namespaces";
    private static final int DEFAULT_EXCLUDED_TAG_NAMESPACES = 0;

    public static int getExcludedTagNamespaces() {
        return getInt(KEY_EXCLUDED_TAG_NAMESPACES, DEFAULT_EXCLUDED_TAG_NAMESPACES);
    }

    public static void putExcludedTagNamespaces(int value) {
        sEhConfig.excludedNamespaces = value;
        sEhConfig.setDirty();
        putInt(KEY_EXCLUDED_TAG_NAMESPACES, value);
    }

    public static final String KEY_EXCLUDED_LANGUAGES = "excluded_languages";
    private static final String DEFAULT_EXCLUDED_LANGUAGES = null;

    public static String getExcludedLanguages() {
        return getString(KEY_EXCLUDED_LANGUAGES, DEFAULT_EXCLUDED_LANGUAGES);
    }

    public static void putExcludedLanguages(String value) {
        sEhConfig.excludedLanguages = value;
        sEhConfig.setDirty();
        putString(KEY_EXCLUDED_LANGUAGES, value);
    }

    private static final String KEY_CELLULAR_NETWORK_WARNING = "cellular_network_warning";
    private static final boolean DEFAULT_CELLULAR_NETWORK_WARNING = false;

    public static boolean getCellularNetworkWarning() {
        return getBoolean(KEY_CELLULAR_NETWORK_WARNING, DEFAULT_CELLULAR_NETWORK_WARNING);
    }

    /********************
     ****** Read
     ********************/
    private static final String KEY_SCREEN_ROTATION = "screen_rotation";
    private static final int DEFAULT_SCREEN_ROTATION = 0;

    public static int getScreenRotation() {
        return getIntFromStr(KEY_SCREEN_ROTATION, DEFAULT_SCREEN_ROTATION);
    }

    public static void putScreenRotation(int value) {
        putIntToStr(KEY_SCREEN_ROTATION, value);
    }

    private static final String KEY_READING_DIRECTION = "reading_direction";
    private static final int DEFAULT_READING_DIRECTION = GalleryView.LAYOUT_RIGHT_TO_LEFT;

    @GalleryView.LayoutMode
    public static int getReadingDirection() {
        return GalleryView.sanitizeLayoutMode(getIntFromStr(KEY_READING_DIRECTION, DEFAULT_READING_DIRECTION));
    }

    public static void putReadingDirection(int value) {
        putIntToStr(KEY_READING_DIRECTION, value);
    }

    private static final String KEY_PAGE_SCALING = "page_scaling";
    private static final int DEFAULT_PAGE_SCALING = GalleryView.SCALE_FIT;

    @GalleryView.ScaleMode
    public static int getPageScaling() {
        return GalleryView.sanitizeScaleMode(getIntFromStr(KEY_PAGE_SCALING, DEFAULT_PAGE_SCALING));
    }

    public static void putPageScaling(int value) {
        putIntToStr(KEY_PAGE_SCALING, value);
    }

    private static final String KEY_START_POSITION = "start_position";
    private static final int DEFAULT_START_POSITION = GalleryView.START_POSITION_TOP_RIGHT;

    @GalleryView.StartPosition
    public static int getStartPosition() {
        return GalleryView.sanitizeStartPosition(getIntFromStr(KEY_START_POSITION, DEFAULT_START_POSITION));
    }

    public static void putStartPosition(int value) {
        putIntToStr(KEY_START_POSITION, value);
    }

    private static final String KEY_START_TRANSFER_TIME = "start_transfer_time";
    private static final int DEFAULT_START_TRANSFER_TIME = 2;

    public static int getStartTransferTime() {
        return getInt(KEY_START_TRANSFER_TIME, DEFAULT_START_TRANSFER_TIME);
    }

    public static void putStartTransferTime(int value) {
        putInt(KEY_START_TRANSFER_TIME, value);
    }

    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final boolean DEFAULT_KEEP_SCREEN_ON = false;

    public static boolean getKeepScreenOn() {
        return getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON);
    }

    public static void putKeepScreenOn(boolean value) {
        putBoolean(KEY_KEEP_SCREEN_ON, value);
    }

    private static final String KEY_SHOW_CLOCK = "gallery_show_clock";
    private static final boolean DEFAULT_SHOW_CLOCK = true;

    public static boolean getShowClock() {
        return getBoolean(KEY_SHOW_CLOCK, DEFAULT_SHOW_CLOCK);
    }

    public static void putShowClock(boolean value) {
        putBoolean(KEY_SHOW_CLOCK, value);
    }

    private static final String KEY_SHOW_PROGRESS = "gallery_show_progress";
    private static final boolean DEFAULT_SHOW_PROGRESS = true;

    public static boolean getShowProgress() {
        return getBoolean(KEY_SHOW_PROGRESS, DEFAULT_SHOW_PROGRESS);
    }

    public static void putShowProgress(boolean value) {
        putBoolean(KEY_SHOW_PROGRESS, value);
    }

    private static final String KEY_SHOW_BATTERY = "gallery_show_battery";
    private static final boolean DEFAULT_SHOW_BATTERY = true;

    public static boolean getShowBattery() {
        return getBoolean(KEY_SHOW_BATTERY, DEFAULT_SHOW_BATTERY);
    }

    public static void putShowBattery(boolean value) {
        putBoolean(KEY_SHOW_BATTERY, value);
    }

    private static final String KEY_SHOW_PAGE_INTERVAL = "gallery_show_page_interval";
    private static final boolean DEFAULT_SHOW_PAGE_INTERVAL = true;

    public static boolean getShowPageInterval() {
        return getBoolean(KEY_SHOW_PAGE_INTERVAL, DEFAULT_SHOW_PAGE_INTERVAL);
    }

    public static void putShowPageInterval(boolean value) {
        putBoolean(KEY_SHOW_PAGE_INTERVAL, value);
    }

    private static final String KEY_VOLUME_PAGE = "volume_page";
    private static final boolean DEFAULT_VOLUME_PAGE = false;

    public static boolean getVolumePage() {
        return getBoolean(KEY_VOLUME_PAGE, DEFAULT_VOLUME_PAGE);
    }

    public static void putVolumePage(boolean value) {
        putBoolean(KEY_VOLUME_PAGE, value);
    }


    private static final String KEY_REVERSE_VOLUME_PAGE = "reverse_volume_page";
    private static final boolean DEFAULT_REVERSE_VOLUME_PAGE = false;

    public static boolean getReverseVolumePage() {
        return getBoolean(KEY_REVERSE_VOLUME_PAGE, DEFAULT_REVERSE_VOLUME_PAGE);
    }

    public static void putReverseVolumePage(boolean value) {
        putBoolean(KEY_REVERSE_VOLUME_PAGE, value);
    }

    private static final String KEY_READING_FULLSCREEN = "reading_fullscreen";
    private static final boolean VALUE_READING_FULLSCREEN = true;

    public static boolean getReadingFullscreen() {
        return getBoolean(KEY_READING_FULLSCREEN, VALUE_READING_FULLSCREEN);
    }

    public static void putReadingFullscreen(boolean value) {
        putBoolean(KEY_READING_FULLSCREEN, value);
    }

    private static final String KEY_CUSTOM_SCREEN_LIGHTNESS = "custom_screen_lightness";
    private static final boolean DEFAULT_CUSTOM_SCREEN_LIGHTNESS = false;

    public static boolean getCustomScreenLightness() {
        return getBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, DEFAULT_CUSTOM_SCREEN_LIGHTNESS);
    }

    public static void putCustomScreenLightness(boolean value) {
        putBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, value);
    }

    private static final String KEY_SCREEN_LIGHTNESS = "screen_lightness";
    private static final int DEFAULT_SCREEN_LIGHTNESS = 50;

    public static int getScreenLightness() {
        return getInt(KEY_SCREEN_LIGHTNESS, DEFAULT_SCREEN_LIGHTNESS);
    }

    public static void putScreenLightness(int value) {
        putInt(KEY_SCREEN_LIGHTNESS, value);
    }

    /********************
     ****** Privacy and Security
     ********************/
    public static final String KEY_SEC_SECURITY = "enable_secure";
    public static final boolean VALUE_SEC_SECURITY = false;

    public static boolean getEnabledSecurity() {
        return getBoolean(KEY_SEC_SECURITY, VALUE_SEC_SECURITY);
    }
    public static void putEnabledSecurity(boolean value) {
        putBoolean(KEY_READING_FULLSCREEN, value);
    }

    /********************
     ****** Download
     ********************/
    public static final String KEY_DOWNLOAD_SAVE_SCHEME = "image_scheme";
    public static final String KEY_DOWNLOAD_SAVE_AUTHORITY = "image_authority";
    public static final String KEY_DOWNLOAD_SAVE_PATH = "image_path";
    public static final String KEY_DOWNLOAD_SAVE_QUERY = "image_query";
    public static final String KEY_DOWNLOAD_SAVE_FRAGMENT = "image_fragment";

    @Nullable
    public static UniFile getDownloadLocation() {
        UniFile dir = null;
        try {
            Uri.Builder builder = new Uri.Builder();
            builder.scheme(getString(KEY_DOWNLOAD_SAVE_SCHEME, null));
            builder.encodedAuthority(getString(KEY_DOWNLOAD_SAVE_AUTHORITY, null));
            builder.encodedPath(getString(KEY_DOWNLOAD_SAVE_PATH, null));
            builder.encodedQuery(getString(KEY_DOWNLOAD_SAVE_QUERY, null));
            builder.encodedFragment(getString(KEY_DOWNLOAD_SAVE_FRAGMENT, null));
            dir = UniFile.fromUri(sContext, builder.build());
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
        }
        return dir != null ? dir : UniFile.fromFile(AppConfig.getDefaultDownloadDir());
    }

    public static void putDownloadLocation(@NonNull UniFile location) {
        Uri uri = location.getUri();
        putString(KEY_DOWNLOAD_SAVE_SCHEME, uri.getScheme());
        putString(KEY_DOWNLOAD_SAVE_AUTHORITY, uri.getEncodedAuthority());
        putString(KEY_DOWNLOAD_SAVE_PATH, uri.getEncodedPath());
        putString(KEY_DOWNLOAD_SAVE_QUERY, uri.getEncodedQuery());
        putString(KEY_DOWNLOAD_SAVE_FRAGMENT, uri.getEncodedFragment());

        if (getMediaScan()) {
            CommonOperations.removeNoMediaFile(location);
        } else {
            CommonOperations.ensureNoMediaFile(location);
        }
    }

    public static final String KEY_MEDIA_SCAN = "media_scan";
    private static final boolean DEFAULT_MEDIA_SCAN = false;

    public static boolean getMediaScan() {
        return getBoolean(KEY_MEDIA_SCAN, DEFAULT_MEDIA_SCAN);
    }

    private static final String KEY_RECENT_DOWNLOAD_LABEL = "recent_download_label";
    private static final String DEFAULT_RECENT_DOWNLOAD_LABEL = null;

    public static String getRecentDownloadLabel() {
        return getString(KEY_RECENT_DOWNLOAD_LABEL, DEFAULT_RECENT_DOWNLOAD_LABEL);
    }

    public static void putRecentDownloadLabel(String value) {
        putString(KEY_RECENT_DOWNLOAD_LABEL, value);
    }

    private static final String KEY_HAS_DEFAULT_DOWNLOAD_LABEL = "has_default_download_label";
    private static final boolean DEFAULT_HAS_DOWNLOAD_LABEL = false;

    public static boolean getHasDefaultDownloadLabel() {
        return getBoolean(KEY_HAS_DEFAULT_DOWNLOAD_LABEL, DEFAULT_HAS_DOWNLOAD_LABEL);
    }

    public static void putHasDefaultDownloadLabel(boolean hasDefaultDownloadLabel) {
        putBoolean(KEY_HAS_DEFAULT_DOWNLOAD_LABEL, hasDefaultDownloadLabel);
    }

    private static final String KEY_DEFAULT_DOWNLOAD_LABEL = "default_download_label";
    private static final String DEFAULT_DOWNLOAD_LABEL = null;

    public static String getDefaultDownloadLabel() {
        return getString(KEY_DEFAULT_DOWNLOAD_LABEL, DEFAULT_DOWNLOAD_LABEL);
    }

    public static void putDefaultDownloadLabel(String value) {
        putString(KEY_DEFAULT_DOWNLOAD_LABEL, value);
    }

    private static final String KEY_MULTI_THREAD_DOWNLOAD = "download_thread";
    private static final int DEFAULT_MULTI_THREAD_DOWNLOAD = 3;

    public static int getMultiThreadDownload() {
        return getIntFromStr(KEY_MULTI_THREAD_DOWNLOAD, DEFAULT_MULTI_THREAD_DOWNLOAD);
    }

    public static void putMultiThreadDownload(int value) {
        putIntToStr(KEY_MULTI_THREAD_DOWNLOAD, value);
    }

    private static final String KEY_PRELOAD_IMAGE = "preload_image";
    private static final int DEFAULT_PRELOAD_IMAGE = 5;

    public static int getPreloadImage() {
        return getIntFromStr(KEY_PRELOAD_IMAGE, DEFAULT_PRELOAD_IMAGE);
    }

    public static void putPreloadImage(int value) {
        putIntToStr(KEY_PRELOAD_IMAGE, value);
    }

    public static final String KEY_IMAGE_RESOLUTION = "image_size";
    public static final String DEFAULT_IMAGE_RESOLUTION = EhConfig.IMAGE_SIZE_AUTO;

    public static String getImageResolution() {
        return getString(KEY_IMAGE_RESOLUTION, DEFAULT_IMAGE_RESOLUTION);
    }

    public static void putImageResolution(String value) {
        sEhConfig.imageSize = value;
        sEhConfig.setDirty();
        putString(KEY_IMAGE_RESOLUTION, value);
    }

    private static final String KEY_DOWNLOAD_ORIGIN_IMAGE = "download_origin_image";
    private static final boolean DEFAULT_DOWNLOAD_ORIGIN_IMAGE = false;

    public static boolean getDownloadOriginImage() {
        return getBoolean(KEY_DOWNLOAD_ORIGIN_IMAGE, DEFAULT_DOWNLOAD_ORIGIN_IMAGE);
    }

    public static void putDownloadOriginImage(boolean value) {
        putBoolean(KEY_DOWNLOAD_ORIGIN_IMAGE, value);
    }

    /********************
     ****** Favorites
     ********************/
    private static final String KEY_FAV_CAT_0 = "fav_cat_0";
    private static final String KEY_FAV_CAT_1 = "fav_cat_1";
    private static final String KEY_FAV_CAT_2 = "fav_cat_2";
    private static final String KEY_FAV_CAT_3 = "fav_cat_3";
    private static final String KEY_FAV_CAT_4 = "fav_cat_4";
    private static final String KEY_FAV_CAT_5 = "fav_cat_5";
    private static final String KEY_FAV_CAT_6 = "fav_cat_6";
    private static final String KEY_FAV_CAT_7 = "fav_cat_7";
    private static final String KEY_FAV_CAT_8 = "fav_cat_8";
    private static final String KEY_FAV_CAT_9 = "fav_cat_9";
    private static final String DEFAULT_FAV_CAT_0 = "Favorites 0";
    private static final String DEFAULT_FAV_CAT_1 = "Favorites 1";
    private static final String DEFAULT_FAV_CAT_2 = "Favorites 2";
    private static final String DEFAULT_FAV_CAT_3 = "Favorites 3";
    private static final String DEFAULT_FAV_CAT_4 = "Favorites 4";
    private static final String DEFAULT_FAV_CAT_5 = "Favorites 5";
    private static final String DEFAULT_FAV_CAT_6 = "Favorites 6";
    private static final String DEFAULT_FAV_CAT_7 = "Favorites 7";
    private static final String DEFAULT_FAV_CAT_8 = "Favorites 8";
    private static final String DEFAULT_FAV_CAT_9 = "Favorites 9";

    private static final String KEY_FAV_COUNT_0 = "fav_count_0";
    private static final String KEY_FAV_COUNT_1 = "fav_count_1";
    private static final String KEY_FAV_COUNT_2 = "fav_count_2";
    private static final String KEY_FAV_COUNT_3 = "fav_count_3";
    private static final String KEY_FAV_COUNT_4 = "fav_count_4";
    private static final String KEY_FAV_COUNT_5 = "fav_count_5";
    private static final String KEY_FAV_COUNT_6 = "fav_count_6";
    private static final String KEY_FAV_COUNT_7 = "fav_count_7";
    private static final String KEY_FAV_COUNT_8 = "fav_count_8";
    private static final String KEY_FAV_COUNT_9 = "fav_count_9";

    private static final String KEY_FAV_LOCAL = "fav_local";
    private static final String KEY_FAV_CLOUD = "fav_cloud";

    private static final int DEFAULT_FAV_COUNT = 0;

    public static String[] getFavCat() {
        String[] favCat = new String[10];
        favCat[0] = sSettingsPre.getString(KEY_FAV_CAT_0, DEFAULT_FAV_CAT_0);
        favCat[1] = sSettingsPre.getString(KEY_FAV_CAT_1, DEFAULT_FAV_CAT_1);
        favCat[2] = sSettingsPre.getString(KEY_FAV_CAT_2, DEFAULT_FAV_CAT_2);
        favCat[3] = sSettingsPre.getString(KEY_FAV_CAT_3, DEFAULT_FAV_CAT_3);
        favCat[4] = sSettingsPre.getString(KEY_FAV_CAT_4, DEFAULT_FAV_CAT_4);
        favCat[5] = sSettingsPre.getString(KEY_FAV_CAT_5, DEFAULT_FAV_CAT_5);
        favCat[6] = sSettingsPre.getString(KEY_FAV_CAT_6, DEFAULT_FAV_CAT_6);
        favCat[7] = sSettingsPre.getString(KEY_FAV_CAT_7, DEFAULT_FAV_CAT_7);
        favCat[8] = sSettingsPre.getString(KEY_FAV_CAT_8, DEFAULT_FAV_CAT_8);
        favCat[9] = sSettingsPre.getString(KEY_FAV_CAT_9, DEFAULT_FAV_CAT_9);
        return favCat;
    }

    public static void putFavCat(String[] value) {
        AssertUtils.assertEquals(10, value.length);
        sSettingsPre.edit()
                .putString(KEY_FAV_CAT_0, value[0])
                .putString(KEY_FAV_CAT_1, value[1])
                .putString(KEY_FAV_CAT_2, value[2])
                .putString(KEY_FAV_CAT_3, value[3])
                .putString(KEY_FAV_CAT_4, value[4])
                .putString(KEY_FAV_CAT_5, value[5])
                .putString(KEY_FAV_CAT_6, value[6])
                .putString(KEY_FAV_CAT_7, value[7])
                .putString(KEY_FAV_CAT_8, value[8])
                .putString(KEY_FAV_CAT_9, value[9])
                .apply();
    }

    public static int[] getFavCount() {
        int[] favCount = new int[10];
        favCount[0] = sSettingsPre.getInt(KEY_FAV_COUNT_0, DEFAULT_FAV_COUNT);
        favCount[1] = sSettingsPre.getInt(KEY_FAV_COUNT_1, DEFAULT_FAV_COUNT);
        favCount[2] = sSettingsPre.getInt(KEY_FAV_COUNT_2, DEFAULT_FAV_COUNT);
        favCount[3] = sSettingsPre.getInt(KEY_FAV_COUNT_3, DEFAULT_FAV_COUNT);
        favCount[4] = sSettingsPre.getInt(KEY_FAV_COUNT_4, DEFAULT_FAV_COUNT);
        favCount[5] = sSettingsPre.getInt(KEY_FAV_COUNT_5, DEFAULT_FAV_COUNT);
        favCount[6] = sSettingsPre.getInt(KEY_FAV_COUNT_6, DEFAULT_FAV_COUNT);
        favCount[7] = sSettingsPre.getInt(KEY_FAV_COUNT_7, DEFAULT_FAV_COUNT);
        favCount[8] = sSettingsPre.getInt(KEY_FAV_COUNT_8, DEFAULT_FAV_COUNT);
        favCount[9] = sSettingsPre.getInt(KEY_FAV_COUNT_9, DEFAULT_FAV_COUNT);
        return favCount;
    }

    public static void putFavCount(int[] count) {
        AssertUtils.assertEquals(10, count.length);
        sSettingsPre.edit()
                .putInt(KEY_FAV_COUNT_0, count[0])
                .putInt(KEY_FAV_COUNT_1, count[1])
                .putInt(KEY_FAV_COUNT_2, count[2])
                .putInt(KEY_FAV_COUNT_3, count[3])
                .putInt(KEY_FAV_COUNT_4, count[4])
                .putInt(KEY_FAV_COUNT_5, count[5])
                .putInt(KEY_FAV_COUNT_6, count[6])
                .putInt(KEY_FAV_COUNT_7, count[7])
                .putInt(KEY_FAV_COUNT_8, count[8])
                .putInt(KEY_FAV_COUNT_9, count[9])
                .apply();
    }

    public static int getFavLocalCount() {
        return sSettingsPre.getInt(KEY_FAV_LOCAL, DEFAULT_FAV_COUNT);
    }

    public static void putFavLocalCount(int count) {
        sSettingsPre.edit().putInt(KEY_FAV_LOCAL, count).apply();
    }

    public static int getFavCloudCount() {
        return sSettingsPre.getInt(KEY_FAV_CLOUD, DEFAULT_FAV_COUNT);
    }

    public static void putFavCloudCount(int count) {
        sSettingsPre.edit().putInt(KEY_FAV_CLOUD, count).apply();
    }

    private static final String KEY_RECENT_FAV_CAT = "recent_fav_cat";
    private static final int DEFAULT_RECENT_FAV_CAT = FavListUrlBuilder.FAV_CAT_ALL;

    public static int getRecentFavCat() {
        return getInt(KEY_RECENT_FAV_CAT, DEFAULT_RECENT_FAV_CAT);
    }

    public static void putRecentFavCat(int value) {
        putInt(KEY_RECENT_FAV_CAT, value);
    }

    // -1 for local, 0 - 9 for cloud favorite, other for no default fav slot
    private static final String KEY_DEFAULT_FAV_SLOT = "default_favorite_2";
    public static final int INVALID_DEFAULT_FAV_SLOT = -2;
    private static final int DEFAULT_DEFAULT_FAV_SLOT = INVALID_DEFAULT_FAV_SLOT;

    public static int getDefaultFavSlot() {
        return getInt(KEY_DEFAULT_FAV_SLOT, DEFAULT_DEFAULT_FAV_SLOT);
    }

    public static void putDefaultFavSlot(int value) {
        putInt(KEY_DEFAULT_FAV_SLOT, value);
    }

    /********************
     ****** Analytics
     ********************/
    private static final String KEY_ASK_ANALYTICS = "ask_analytics";
    private static final boolean DEFAULT_ASK_ANALYTICS = true;

    public static boolean getAskAnalytics() {
        return getBoolean(KEY_ASK_ANALYTICS, DEFAULT_ASK_ANALYTICS);
    }

    public static void putAskAnalytics(boolean value) {
        putBoolean(KEY_ASK_ANALYTICS, value);
    }

    public static final String KEY_ENABLE_ANALYTICS = "enable_analytics";
    private static final boolean DEFAULT_ENABLE_ANALYTICS = false;

    public static boolean getEnableAnalytics() {
        return getBoolean(KEY_ENABLE_ANALYTICS, DEFAULT_ENABLE_ANALYTICS);
    }

    public static void putEnableAnalytics(boolean value) {
        putBoolean(KEY_ENABLE_ANALYTICS, value);
    }

    private static final String KEY_USER_ID = "user_id";
    private static final String FILENAME_USER_ID = ".user_id";
    private static final int LENGTH_USER_ID = 32;

    public static String getUserID() {
        boolean writeXml = false;
        boolean writeFile = false;
        String userID = getString(KEY_USER_ID, null);
        File file = AppConfig.getFileInExternalAppDir(FILENAME_USER_ID);
        if (null == userID || !isValidUserID(userID)) {
            writeXml = true;
            // Get use ID from out sd card file
            userID = FileUtils.read(file);
            if (null == userID || !isValidUserID(userID)) {
                writeFile = true;
                userID = generateUserID();
            }
        } else {
            writeFile = true;
        }

        if (writeXml) {
            putString(KEY_USER_ID, userID);
        }
        if (writeFile) {
            FileUtils.write(file, userID);
        }

        return userID;
    }

    @NonNull
    private static String generateUserID() {
        int length = LENGTH_USER_ID;
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            if (MathUtils.random(0, ('9' - '0' + 1) + ('z' - 'a' + 1)) <= '9' - '0') {
                sb.append((char) MathUtils.random('0', '9' + 1));
            } else {
                sb.append((char) MathUtils.random('a', 'z' + 1));
            }
        }

        return sb.toString();
    }

    private static boolean isValidUserID(@Nullable String userID) {
        if (null == userID || LENGTH_USER_ID != userID.length()) {
            return false;
        }

        for (int i = 0; i < LENGTH_USER_ID; i++) {
            char ch = userID.charAt(i);
            if (!(ch >= '0' && ch <= '9') && !(ch >= 'a' && ch <= 'z')) {
                return false;
            }
        }

        return true;
    }

    /********************
     ****** Update
     ********************/
    private static final String KEY_BETA_UPDATE_CHANNEL = "beta_update_channel";
    private static final boolean DEFAULT_BETA_UPDATE_CHANNEL = EhApplication.BETA;
    private static final String KEY_SKIP_UPDATE_VERSION = "skip_update_version";
    private static final int DEFAULT_SKIP_UPDATE_VERSION = 0;

    public static boolean getBetaUpdateChannel() {
        return getBoolean(KEY_BETA_UPDATE_CHANNEL, DEFAULT_BETA_UPDATE_CHANNEL);
    }

    public static void putBetaUpdateChannel(boolean value) {
        putBoolean(KEY_BETA_UPDATE_CHANNEL, value);
    }

    public static int getSkipUpdateVersion() {
        return getInt(KEY_SKIP_UPDATE_VERSION, DEFAULT_SKIP_UPDATE_VERSION);
    }

    public static void putSkipUpdateVersion(int value) {
        putInt(KEY_SKIP_UPDATE_VERSION, value);
    }

    /********************
     ****** Advanced
     ********************/
    public static final String KEY_SAVE_PARSE_ERROR_BODY = "save_parse_error_body";
    private static final boolean DEFAULT_SAVE_PARSE_ERROR_BODY = EhApplication.BETA;

    public static boolean getSaveParseErrorBody() {
        return getBoolean(KEY_SAVE_PARSE_ERROR_BODY, DEFAULT_SAVE_PARSE_ERROR_BODY);
    }

    public static void putSaveParseErrorBody(boolean value) {
        putBoolean(KEY_SAVE_PARSE_ERROR_BODY, value);
    }

    private static final String KEY_SAVE_CRASH_LOG = "save_crash_log";
    private static final boolean DEFAULT_SAVE_CRASH_LOG = false;

    public static boolean getSaveCrashLog() {
        return getBoolean(KEY_SAVE_CRASH_LOG, DEFAULT_SAVE_CRASH_LOG);
    }

    public static final String KEY_SECURITY = "security";
    public static final String DEFAULT_SECURITY = "";

    public static String getSecurity() {
        return getString(KEY_SECURITY, DEFAULT_SECURITY);
    }

    public static void putSecurity(String value) {
        putString(KEY_SECURITY, value);
    }

    public static final String KEY_ENABLE_FINGERPRINT = "enable_fingerprint";

    public static boolean getEnableFingerprint() {
        return getBoolean(KEY_ENABLE_FINGERPRINT, false);
    }

    public static void putEnableFingerprint(boolean value) {
        putBoolean(KEY_ENABLE_FINGERPRINT, value);
    }

    public static final String KEY_READ_CACHE_SIZE = "read_cache_size";
    public static final int DEFAULT_READ_CACHE_SIZE = 160;

    public static int getReadCacheSize() {
        return getIntFromStr(KEY_READ_CACHE_SIZE, DEFAULT_READ_CACHE_SIZE);
    }

    public static final String KEY_BUILT_IN_HOSTS = "built_in_hosts";
    private static final boolean DEFAULT_BUILT_IN_HOSTS = true;

    public static boolean getBuiltInHosts() {
        return getBoolean(KEY_BUILT_IN_HOSTS, DEFAULT_BUILT_IN_HOSTS);
    }

    public static void putBuiltInHosts(boolean value) {
        putBoolean(KEY_BUILT_IN_HOSTS, value);
    }

    public static final String KEY_BUILT_EX_HOSTS = "built_ex_hosts";
    private static final boolean DEFAULT_BUILT_EX_HOSTS = true;

    public static boolean getBuiltEXHosts() {
        return getBoolean(KEY_BUILT_EX_HOSTS, DEFAULT_BUILT_EX_HOSTS);
    }

    public static void putBuiltEXHosts(boolean value) {
        putBoolean(KEY_BUILT_EX_HOSTS, value);
    }


    public static final String KEY_APP_LANGUAGE = "app_language";
    private static final String DEFAULT_APP_LANGUAGE = "system";

    //获取系统语言
    public static String getAppLanguage() {
        return getString(KEY_APP_LANGUAGE, DEFAULT_APP_LANGUAGE);
    }

    public static void putAppLanguage(String value) {
        putString(KEY_APP_LANGUAGE, value);
    }

    private static final String KEY_PROXY_TYPE = "proxy_type";
    private static final int DEFAULT_PROXY_TYPE = EhProxySelector.TYPE_SYSTEM;

    public static int getProxyType() {
        return getInt(KEY_PROXY_TYPE, DEFAULT_PROXY_TYPE);
    }

    public static void putProxyType(int value) {
        putInt(KEY_PROXY_TYPE, value);
    }

    private static final String KEY_PROXY_IP = "proxy_ip";
    private static final String DEFAULT_PROXY_IP = null;

    public static String getProxyIp() {
        return getString(KEY_PROXY_IP, DEFAULT_PROXY_IP);
    }

    public static void putProxyIp(String value) {
        putString(KEY_PROXY_IP, value);
    }

    private static final String KEY_PROXY_PORT = "proxy_port";
    private static final int DEFAULT_PROXY_PORT = -1;

    public static int getProxyPort() {
        return getInt(KEY_PROXY_PORT, DEFAULT_PROXY_PORT);
    }

    public static void putProxyPort(int value) {
        putInt(KEY_PROXY_PORT, value);
    }

    /********************
     ****** Guide
     ********************/
    private static final String KEY_GUIDE_QUICK_SEARCH = "guide_quick_search";
    private static final boolean DEFAULT_GUIDE_QUICK_SEARCH = true;

    public static boolean getGuideQuickSearch() {
        return getBoolean(KEY_GUIDE_QUICK_SEARCH, DEFAULT_GUIDE_QUICK_SEARCH);
    }

    public static void putGuideQuickSearch(boolean value) {
        putBoolean(KEY_GUIDE_QUICK_SEARCH, value);
    }

    private static final String KEY_GUIDE_COLLECTIONS = "guide_collections";
    private static final boolean DEFAULT_GUIDE_COLLECTIONS = true;

    public static boolean getGuideCollections() {
        return getBoolean(KEY_GUIDE_COLLECTIONS, DEFAULT_GUIDE_COLLECTIONS);
    }

    public static void putGuideCollections(boolean value) {
        putBoolean(KEY_GUIDE_COLLECTIONS, value);
    }

    private static final String KEY_GUIDE_DOWNLOAD_THUMB = "guide_download_thumb";
    private static final boolean DEFAULT_GUIDE_DOWNLOAD_THUMB = true;

    public static boolean getGuideDownloadThumb() {
        return getBoolean(KEY_GUIDE_DOWNLOAD_THUMB, DEFAULT_GUIDE_DOWNLOAD_THUMB);
    }

    public static void putGuideDownloadThumb(boolean value) {
        putBoolean(KEY_GUIDE_DOWNLOAD_THUMB, value);
    }

    private static final String KEY_GUIDE_DOWNLOAD_LABELS = "guide_download_labels";
    private static final boolean DEFAULT_GUIDE_DOWNLOAD_LABELS = true;

    public static boolean getGuideDownloadLabels() {
        return getBoolean(KEY_GUIDE_DOWNLOAD_LABELS, DEFAULT_GUIDE_DOWNLOAD_LABELS);
    }

    public static void puttGuideDownloadLabels(boolean value) {
        putBoolean(KEY_GUIDE_DOWNLOAD_LABELS, value);
    }

    private static final String KEY_GUIDE_GALLERY = "guide_gallery";
    private static final boolean DEFAULT_GUIDE_GALLERY = true;

    public static boolean getGuideGallery() {
        return getBoolean(KEY_GUIDE_GALLERY, DEFAULT_GUIDE_GALLERY);
    }

    public static void putGuideGallery(boolean value) {
        putBoolean(KEY_GUIDE_GALLERY, value);
    }

    private static final String KEY_CLIPBOARD_TEXT_HASH_CODE = "clipboard_text_hash_code";
    private static final int DEFAULT_CLIPBOARD_TEXT_HASH_CODE = 0;

    public static int getClipboardTextHashCode() {
        return getInt(KEY_CLIPBOARD_TEXT_HASH_CODE, DEFAULT_CLIPBOARD_TEXT_HASH_CODE);
    }

    public static void putClipboardTextHashCode(int value) {
        putInt(KEY_CLIPBOARD_TEXT_HASH_CODE, value);
    }


    public static final String KEY_DOH = "dns_over_https";
    private static final boolean DEFAULT_DOH = false;

    /**
     * dns开关
     * @return
     */
    public static boolean getDoH() {
        return getBoolean(KEY_DOH, DEFAULT_DOH);
    }

    public static void putDoH(boolean value) {
        putBoolean(KEY_DOH, value);
    }


    private static final boolean DEFAULT_FRONTING = true;
    public static final String KEY_DOMAIN_FRONTING = "domain_fronting";


    public static boolean getDF() {
        return getBoolean(KEY_DOMAIN_FRONTING, DEFAULT_FRONTING);
    }

    public static void putDF(boolean value) {
        putBoolean(KEY_DOMAIN_FRONTING, value);
    }


    private static final String KEY_DOWNLOAD_DELAY = "download_delay";
    private static final int DEFAULT_DOWNLOAD_DELAY = 0;

    public static int getDownloadDelay() {
        return getIntFromStr(KEY_DOWNLOAD_DELAY, DEFAULT_DOWNLOAD_DELAY);
    }
    public static void putDownloadDelay(int value) {
        putIntToStr(KEY_DOWNLOAD_DELAY, value);
    }

    private static final String KEY_IS_LOGIN = "is_login";

    private static boolean IS_LOGIN = false;

    public static boolean isLogin() {
        return getBoolean(KEY_IS_LOGIN, IS_LOGIN);
    }

    public static void setLoginState(boolean value) {
        putBoolean(KEY_IS_LOGIN, value);
    }


    public static final String KEY_SHOW_GALLERY_COMMENT = "show_gallery_comment";

    private static boolean IS_SHOW_GALLERY_COMMENT = true;

    public static boolean getShowGalleryComment() {
        return getBoolean(KEY_SHOW_GALLERY_COMMENT, IS_SHOW_GALLERY_COMMENT);
    }

    public static void setShowGalleryComment(boolean value) {
        putBoolean(KEY_SHOW_GALLERY_COMMENT, value);
    }

    public static final String KEY_CLOSE_AUTO_UPDATES = "close_auto_updates";

    private static boolean IS_CLOSE_AUTO_UPDATES = false;

    public static boolean getCloseAutoUpdate() {
        return getBoolean(KEY_CLOSE_AUTO_UPDATES, IS_CLOSE_AUTO_UPDATES);
    }

    public static void setKeyCloseAutoUpdates(boolean value) {
        putBoolean(KEY_CLOSE_AUTO_UPDATES, value);
    }

    public static final String KEY_SHOW_EH_EVENTS = "show_eh_events";

    private static boolean IS_SHOW_EH_EVENTS = true;

    public static boolean getShowEhEvents() {
        return getBoolean(KEY_SHOW_EH_EVENTS, IS_SHOW_EH_EVENTS) && isLogin();
    }

    public static void setKeyShowEhEvents(boolean value) {
        putBoolean(KEY_SHOW_EH_EVENTS, value);
    }

    public static final String KEY_SHOW_EH_LIMITS = "show_eh_limits";

    private static boolean IS_SHOW_EH_LIMITS = true;

    public static boolean getShowEhLimits() {
        return getBoolean(KEY_SHOW_EH_LIMITS, IS_SHOW_EH_LIMITS) && isLogin();
    }

    public static void setKeyShowEhLimits(boolean value) {
        putBoolean(KEY_SHOW_EH_LIMITS, value);
    }


    public static final String KEY_LOCK_COOKIE_IGNEOUS = "lock_cookie_igneous";

    private static boolean IS_LOCK_COOKIE_IGNEOUS = false;

    public static boolean getLockCookieIgneous() {
        return getBoolean(KEY_LOCK_COOKIE_IGNEOUS, IS_LOCK_COOKIE_IGNEOUS) ;
    }

    public static void setLockCookieIgneous(boolean value) {
        putBoolean(KEY_LOCK_COOKIE_IGNEOUS, value);
    }

    public static final String USER_BACKGROUND_IMAGE = "background_image_path";
    public static final String USER_AVATAR_IMAGE = "avatar_image_path";

    public static File getUserImageFile(String key){
        String path = getString(key,"");
        if (path.equals("")){
            return null;
        }
        File file = new File(path);
        if (file.exists()){
            return file;
        }else {
            return null;
        }
    }

    public static void saveFilePath(String key,String path){
        putString(key,path);
    }

    public static final String KEY_DOWNLOAD_ORDER_ASC = "download_order_asc";

    private static boolean IS_DOWNLOAD_ORDER_ASC = true;

    public static boolean getDownloadOrder() {
        return getBoolean(KEY_DOWNLOAD_ORDER_ASC, IS_DOWNLOAD_ORDER_ASC) ;
    }

    public static void setDownloadOrder(boolean value) {
        putBoolean(KEY_DOWNLOAD_ORDER_ASC, value);
    }

    public static final String KEY_DOWNLOAD_LIST_PAGINATION = "download_list_pagination";

    private static boolean IS_DOWNLOAD_LIST_PAGINATION = true;

    public static boolean getDownloadPagination() {
        return getBoolean(KEY_DOWNLOAD_LIST_PAGINATION, IS_DOWNLOAD_LIST_PAGINATION) ;
    }

    public static void setDownloadPagination(boolean value) {
        putBoolean(KEY_DOWNLOAD_LIST_PAGINATION, value);
    }

    public static final String KEY_SHOW_READ_PROGRESS = "show_read_progress";

    private static boolean IS_SHOW_READ_PROGRESS = true;

    public static boolean getShowReadProgress() {
        return getBoolean(KEY_SHOW_READ_PROGRESS, IS_SHOW_READ_PROGRESS) ;
    }

    public static void setShowReadProgress(boolean value) {
        putBoolean(KEY_SHOW_READ_PROGRESS, value);
    }

    public static final String KEY_HISTORY_INFO_SIZE = "history_info_size";

    public static int DEFAULT_HISTORY_INFO_SIZE = 100;

    public static int getHistoryInfoSize() {
        int size = getIntFromStr(KEY_HISTORY_INFO_SIZE, DEFAULT_HISTORY_INFO_SIZE);
        if (size<DEFAULT_HISTORY_INFO_SIZE){
            setHistoryInfoSize(DEFAULT_HISTORY_INFO_SIZE);
            return DEFAULT_HISTORY_INFO_SIZE;
        }
        return size;
    }

    public static void setHistoryInfoSize(int value) {
        putIntToStr(KEY_HISTORY_INFO_SIZE, value);
    }

    public static final String KEY_DOWNLOAD_TIMEOUT = "download_timeout";

    public static int DEFAULT_DOWNLOAD_TIMEOUT = 0;

    public static int getDownloadTimeout() {
        int size = getIntFromStr(KEY_DOWNLOAD_TIMEOUT, DEFAULT_DOWNLOAD_TIMEOUT);
        return Math.max(size, DEFAULT_DOWNLOAD_TIMEOUT);
    }

    public static void setDownloadTimeout(int value) {
        putIntToStr(KEY_DOWNLOAD_TIMEOUT, value);
    }

}
