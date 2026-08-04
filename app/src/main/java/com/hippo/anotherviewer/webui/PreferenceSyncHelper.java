/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer.webui;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.hippo.anotherviewer.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Syncs user preferences between the app's SharedPreferences and the WebUI
 * server. Preferences travel as one JSON document shaped like the server's
 * {@code PreferenceResponse} (general / reader / privacy sections), wrapped in
 * the sync {@code preferences} entity of the push/pull payloads.
 *
 * <p>All methods are synchronous and must run off the main thread. Failures
 * surface as {@link IOException} and never touch local settings: missing or
 * malformed fields are skipped on import, and an unparseable document aborts
 * the whole import without writing anything. This helper never imports or
 * exports anything but the preference keys listed below.
 */
public final class PreferenceSyncHelper {

    private static final String PATH_PUSH = "/api/v1/sync/push";
    private static final String PATH_PULL = "/api/v1/sync/pull";

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    // JSON sections (PreferenceResponse)
    private static final String SECTION_GENERAL = "general";
    private static final String SECTION_READER = "reader";
    private static final String SECTION_PRIVACY = "privacy";

    // general fields
    private static final String JSON_THEME = "theme";
    private static final String JSON_THEME_AUTO_SWITCH = "themeAutoSwitch";
    private static final String JSON_LAUNCH_PAGE = "launchPage";
    private static final String JSON_LIST_MODE = "listMode";
    private static final String JSON_SHOW_READ_PROGRESS = "showReadProgress";
    private static final String JSON_DETAIL_SIZE = "detailSize";
    private static final String JSON_THUMB_SIZE = "thumbSize";
    private static final String JSON_HISTORY_INFO_SIZE = "historyInfoSize";
    private static final String JSON_SHOW_JPN_TITLE = "showJpnTitle";
    private static final String JSON_SHOW_GALLERY_PAGES = "showGalleryPages";
    private static final String JSON_SHOW_TAG_TRANSLATIONS = "showTagTranslations";
    private static final String JSON_SHOW_GALLERY_COMMENT = "showGalleryComment";
    private static final String JSON_SHOW_GALLERY_RATING = "showGalleryRating";
    private static final String JSON_SHOW_EH_EVENTS = "showSiteEvents";
    private static final String JSON_SHOW_EH_LIMITS = "showSiteLimits";

    // reader fields
    private static final String JSON_READING_DIRECTION = "readingDirection";
    private static final String JSON_PAGE_MODE = "pageMode";
    private static final String JSON_FIRST_PAGE_COVER = "firstPageCover";
    private static final String JSON_PAGE_SCALING = "pageScaling";
    private static final String JSON_START_POSITION = "startPosition";
    private static final String JSON_AUTO_PLAY_INTERVAL_SEC = "autoPlayIntervalSec";
    private static final String JSON_SHOW_PROGRESS = "showProgress";
    private static final String JSON_SHOW_PAGE_INTERVAL = "showPageInterval";
    private static final String JSON_FULLSCREEN = "fullscreen";
    private static final String JSON_BRIGHTNESS = "brightness";

    // privacy fields
    private static final String JSON_ENABLE_ANALYTICS = "enableAnalytics";

    // Preference keys mirroring Settings.java for keys that are private there
    private static final String KEY_LAUNCH_PAGE = "launch_page";
    private static final String KEY_SHOW_JPN_TITLE = "show_jpn_title";
    private static final String KEY_SHOW_GALLERY_PAGES = "show_gallery_pages";
    private static final String KEY_READING_DIRECTION = "reading_direction";
    private static final String KEY_READING_DUAL_PAGE = "reading_dual_page";
    private static final String KEY_READING_FIRST_PAGE_COVER = "reading_first_page_cover";
    private static final String KEY_PAGE_SCALING = "page_scaling";
    private static final String KEY_START_POSITION = "start_position";
    private static final String KEY_START_TRANSFER_TIME = "start_transfer_time";
    private static final String KEY_SHOW_PROGRESS = "gallery_show_progress";
    private static final String KEY_SHOW_PAGE_INTERVAL = "gallery_show_page_interval";
    private static final String KEY_READING_FULLSCREEN = "reading_fullscreen";
    private static final String KEY_CUSTOM_SCREEN_LIGHTNESS = "custom_screen_lightness";
    private static final String KEY_SCREEN_LIGHTNESS = "screen_lightness";

    // Defaults mirroring Settings.java
    private static final int DEFAULT_LAUNCH_PAGE = 0;
    private static final int DEFAULT_LIST_MODE = 0;
    private static final int DEFAULT_DETAIL_SIZE = 0;
    private static final int DEFAULT_THUMB_SIZE = 1;
    private static final int DEFAULT_HISTORY_INFO_SIZE = 100;
    private static final int DEFAULT_READING_DIRECTION = 1; // GalleryView.LAYOUT_RIGHT_TO_LEFT
    private static final int DEFAULT_PAGE_SCALING = 3; // GalleryView.SCALE_FIT
    private static final int DEFAULT_START_POSITION = 1; // GalleryView.START_POSITION_TOP_RIGHT
    private static final int DEFAULT_START_TRANSFER_TIME = 2;
    private static final int DEFAULT_SCREEN_LIGHTNESS = 50;
    private static final int MAX_SCREEN_LIGHTNESS = 200;

    private static volatile OkHttpClient sClient;

    private PreferenceSyncHelper() {}

    private static OkHttpClient client() {
        OkHttpClient client = sClient;
        if (client == null) {
            synchronized (PreferenceSyncHelper.class) {
                client = sClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            .build();
                    sClient = client;
                }
            }
        }
        return client;
    }

    // ---------------------------------------------------------------------------------------------
    // Export / import
    // ---------------------------------------------------------------------------------------------

    /**
     * Serializes the synced preference keys into the {@code PreferenceResponse}
     * JSON document, e.g. {@code {"general": {...}, "reader": {...}, "privacy": {...}}}.
     */
    @NonNull
    public static String exportPreferences(Context context) {
        JSONObject root = new JSONObject();
        try {
            root.put(SECTION_GENERAL, buildGeneral());
            root.put(SECTION_READER, buildReader());
            root.put(SECTION_PRIVACY, buildPrivacy());
        } catch (JSONException e) {
            // Keys are all literals; only thrown on an invalid key
        }
        return root.toString();
    }

    /**
     * Applies the {@code PreferenceResponse} document to the local settings.
     * Missing sections/fields are skipped; an unparseable document leaves the
     * local settings untouched.
     */
    public static void importPreferences(Context context, String json) {
        if (TextUtils.isEmpty(json)) {
            return;
        }
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            return;
        }
        JSONObject general = root.optJSONObject(SECTION_GENERAL);
        if (general != null) {
            importGeneral(general);
        }
        JSONObject reader = root.optJSONObject(SECTION_READER);
        if (reader != null) {
            importReader(reader);
        }
        JSONObject privacy = root.optJSONObject(SECTION_PRIVACY);
        if (privacy != null) {
            importPrivacy(privacy);
        }
    }

    private static JSONObject buildGeneral() throws JSONException {
        JSONObject general = new JSONObject();
        general.put(JSON_THEME, themeToJson(Settings.getIntFromStr(Settings.KEY_THEME, Settings.THEME_LIGHT)));
        general.put(JSON_THEME_AUTO_SWITCH, Settings.getBoolean(Settings.KEY_THEME_AUTO_SWITCH, false));
        general.put(JSON_LAUNCH_PAGE, launchPageToJson(Settings.getIntFromStr(KEY_LAUNCH_PAGE, DEFAULT_LAUNCH_PAGE)));
        general.put(JSON_LIST_MODE, listModeToJson(Settings.getIntFromStr(Settings.KEY_LIST_MODE, DEFAULT_LIST_MODE)));
        general.put(JSON_SHOW_READ_PROGRESS, Settings.getBoolean(Settings.KEY_SHOW_READ_PROGRESS, true));
        general.put(JSON_DETAIL_SIZE, detailSizeToJson(Settings.getIntFromStr(Settings.KEY_DETAIL_SIZE, DEFAULT_DETAIL_SIZE)));
        general.put(JSON_THUMB_SIZE, thumbSizeToJson(Settings.getIntFromStr(Settings.KEY_THUMB_SIZE, DEFAULT_THUMB_SIZE)));
        general.put(JSON_HISTORY_INFO_SIZE, Settings.getIntFromStr(Settings.KEY_HISTORY_INFO_SIZE, DEFAULT_HISTORY_INFO_SIZE));
        general.put(JSON_SHOW_JPN_TITLE, Settings.getBoolean(KEY_SHOW_JPN_TITLE, false));
        general.put(JSON_SHOW_GALLERY_PAGES, Settings.getBoolean(KEY_SHOW_GALLERY_PAGES, false));
        general.put(JSON_SHOW_TAG_TRANSLATIONS, Settings.getBoolean(Settings.KEY_SHOW_TAG_TRANSLATIONS, true));
        general.put(JSON_SHOW_GALLERY_COMMENT, Settings.getBoolean(Settings.KEY_SHOW_GALLERY_COMMENT, true));
        general.put(JSON_SHOW_GALLERY_RATING, Settings.getBoolean(Settings.KEY_SHOW_GALLERY_RATING, true));
        general.put(JSON_SHOW_EH_EVENTS, Settings.getBoolean(Settings.KEY_SHOW_EH_EVENTS, true));
        general.put(JSON_SHOW_EH_LIMITS, Settings.getBoolean(Settings.KEY_SHOW_EH_LIMITS, true));
        return general;
    }

    private static JSONObject buildReader() throws JSONException {
        JSONObject reader = new JSONObject();
        reader.put(JSON_READING_DIRECTION, readingDirectionToJson(Settings.getIntFromStr(KEY_READING_DIRECTION, DEFAULT_READING_DIRECTION)));
        reader.put(JSON_PAGE_MODE, Settings.getBoolean(KEY_READING_DUAL_PAGE, true) ? "dual" : "single");
        reader.put(JSON_FIRST_PAGE_COVER, Settings.getBoolean(KEY_READING_FIRST_PAGE_COVER, true));
        reader.put(JSON_PAGE_SCALING, pageScalingToJson(Settings.getIntFromStr(KEY_PAGE_SCALING, DEFAULT_PAGE_SCALING)));
        reader.put(JSON_START_POSITION, startPositionToJson(Settings.getIntFromStr(KEY_START_POSITION, DEFAULT_START_POSITION)));
        reader.put(JSON_AUTO_PLAY_INTERVAL_SEC, Settings.getInt(KEY_START_TRANSFER_TIME, DEFAULT_START_TRANSFER_TIME));
        reader.put(JSON_SHOW_PROGRESS, Settings.getBoolean(KEY_SHOW_PROGRESS, true));
        reader.put(JSON_SHOW_PAGE_INTERVAL, Settings.getBoolean(KEY_SHOW_PAGE_INTERVAL, true));
        reader.put(JSON_FULLSCREEN, Settings.getBoolean(KEY_READING_FULLSCREEN, true));
        boolean customLightness = Settings.getBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, false);
        reader.put(JSON_BRIGHTNESS, customLightness ? Settings.getInt(KEY_SCREEN_LIGHTNESS, DEFAULT_SCREEN_LIGHTNESS) : 0);
        return reader;
    }

    private static JSONObject buildPrivacy() throws JSONException {
        JSONObject privacy = new JSONObject();
        privacy.put(JSON_ENABLE_ANALYTICS, Settings.getBoolean(Settings.KEY_ENABLE_ANALYTICS, false));
        return privacy;
    }

    private static void importGeneral(JSONObject general) {
        int theme = themeFromJson(optStringIfPresent(general, JSON_THEME));
        if (theme >= 0) {
            Settings.putIntToStr(Settings.KEY_THEME, theme);
        }
        putBooleanIfBoolean(general, JSON_THEME_AUTO_SWITCH, Settings.KEY_THEME_AUTO_SWITCH);
        int launchPage = launchPageFromJson(optStringIfPresent(general, JSON_LAUNCH_PAGE));
        if (launchPage >= 0) {
            Settings.putIntToStr(KEY_LAUNCH_PAGE, launchPage);
        }
        int listMode = listModeFromJson(optStringIfPresent(general, JSON_LIST_MODE));
        if (listMode >= 0) {
            Settings.putIntToStr(Settings.KEY_LIST_MODE, listMode);
        }
        putBooleanIfBoolean(general, JSON_SHOW_READ_PROGRESS, Settings.KEY_SHOW_READ_PROGRESS);
        int detailSize = detailSizeFromJson(optStringIfPresent(general, JSON_DETAIL_SIZE));
        if (detailSize >= 0) {
            Settings.putIntToStr(Settings.KEY_DETAIL_SIZE, detailSize);
        }
        int thumbSize = thumbSizeFromJson(optStringIfPresent(general, JSON_THUMB_SIZE));
        if (thumbSize >= 0) {
            Settings.putIntToStr(Settings.KEY_THUMB_SIZE, thumbSize);
        }
        int historySize = optIntIfPresent(general, JSON_HISTORY_INFO_SIZE);
        if (historySize >= 0) {
            Settings.putIntToStr(Settings.KEY_HISTORY_INFO_SIZE, historySize);
        }
        putBooleanIfBoolean(general, JSON_SHOW_JPN_TITLE, KEY_SHOW_JPN_TITLE);
        putBooleanIfBoolean(general, JSON_SHOW_GALLERY_PAGES, KEY_SHOW_GALLERY_PAGES);
        putBooleanIfBoolean(general, JSON_SHOW_TAG_TRANSLATIONS, Settings.KEY_SHOW_TAG_TRANSLATIONS);
        putBooleanIfBoolean(general, JSON_SHOW_GALLERY_COMMENT, Settings.KEY_SHOW_GALLERY_COMMENT);
        putBooleanIfBoolean(general, JSON_SHOW_GALLERY_RATING, Settings.KEY_SHOW_GALLERY_RATING);
        putBooleanIfBoolean(general, JSON_SHOW_EH_EVENTS, Settings.KEY_SHOW_EH_EVENTS);
        putBooleanIfBoolean(general, JSON_SHOW_EH_LIMITS, Settings.KEY_SHOW_EH_LIMITS);
    }

    private static void importReader(JSONObject reader) {
        int direction = readingDirectionFromJson(optStringIfPresent(reader, JSON_READING_DIRECTION));
        if (direction >= 0) {
            Settings.putIntToStr(KEY_READING_DIRECTION, direction);
        }
        String pageMode = optStringIfPresent(reader, JSON_PAGE_MODE);
        if ("dual".equals(pageMode) || "single".equals(pageMode)) {
            Settings.putBoolean(KEY_READING_DUAL_PAGE, "dual".equals(pageMode));
        }
        putBooleanIfBoolean(reader, JSON_FIRST_PAGE_COVER, KEY_READING_FIRST_PAGE_COVER);
        int scaling = pageScalingFromJson(optStringIfPresent(reader, JSON_PAGE_SCALING));
        if (scaling >= 0) {
            Settings.putIntToStr(KEY_PAGE_SCALING, scaling);
        }
        int startPosition = startPositionFromJson(optStringIfPresent(reader, JSON_START_POSITION));
        if (startPosition >= 0) {
            Settings.putIntToStr(KEY_START_POSITION, startPosition);
        }
        int interval = optIntIfPresent(reader, JSON_AUTO_PLAY_INTERVAL_SEC);
        if (interval >= 0) {
            Settings.putInt(KEY_START_TRANSFER_TIME, interval);
        }
        putBooleanIfBoolean(reader, JSON_SHOW_PROGRESS, KEY_SHOW_PROGRESS);
        putBooleanIfBoolean(reader, JSON_SHOW_PAGE_INTERVAL, KEY_SHOW_PAGE_INTERVAL);
        putBooleanIfBoolean(reader, JSON_FULLSCREEN, KEY_READING_FULLSCREEN);
        int brightness = optIntIfPresent(reader, JSON_BRIGHTNESS);
        if (brightness > 0) {
            Settings.putBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, true);
            Settings.putInt(KEY_SCREEN_LIGHTNESS, Math.min(brightness, MAX_SCREEN_LIGHTNESS));
        } else if (brightness == 0) {
            Settings.putBoolean(KEY_CUSTOM_SCREEN_LIGHTNESS, false);
        }
    }

    private static void importPrivacy(JSONObject privacy) {
        putBooleanIfBoolean(privacy, JSON_ENABLE_ANALYTICS, Settings.KEY_ENABLE_ANALYTICS);
    }

    // ---------------------------------------------------------------------------------------------
    // Value mapping
    // ---------------------------------------------------------------------------------------------

    private static String themeToJson(int theme) {
        switch (theme) {
            case Settings.THEME_DARK:
                return "dark";
            case Settings.THEME_BLACK:
                return "black";
            default:
                return "light";
        }
    }

    private static int themeFromJson(String theme) {
        switch (theme) {
            case "light":
                return Settings.THEME_LIGHT;
            case "dark":
                return Settings.THEME_DARK;
            case "black":
                return Settings.THEME_BLACK;
            default:
                return -1;
        }
    }

    private static String launchPageToJson(int page) {
        switch (page) {
            case 1:
                return "subscription";
            case 2:
                return "whats_hot";
            default:
                return "homepage";
        }
    }

    private static int launchPageFromJson(String page) {
        switch (page) {
            case "homepage":
                return 0;
            case "subscription":
                return 1;
            case "whats_hot":
                return 2;
            default:
                return -1;
        }
    }

    private static String listModeToJson(int mode) {
        switch (mode) {
            case 1:
                return "thumb";
            default:
                return "detail";
        }
    }

    private static int listModeFromJson(String mode) {
        switch (mode) {
            case "detail":
                return 0;
            case "thumb":
                return 1;
            default:
                return -1;
        }
    }

    private static String detailSizeToJson(int size) {
        switch (size) {
            case 1:
                return "short";
            default:
                return "long";
        }
    }

    private static int detailSizeFromJson(String size) {
        switch (size) {
            case "long":
                return 0;
            case "short":
                return 1;
            default:
                return -1;
        }
    }

    private static String thumbSizeToJson(int size) {
        switch (size) {
            case 0:
                return "large";
            case 2:
                return "small";
            default:
                return "middle";
        }
    }

    private static int thumbSizeFromJson(String size) {
        switch (size) {
            case "large":
                return 0;
            case "middle":
                return 1;
            case "small":
                return 2;
            default:
                return -1;
        }
    }

    private static String readingDirectionToJson(int direction) {
        switch (direction) {
            case 0:
                return "ltr";
            case 2:
                return "vertical";
            default:
                return "rtl";
        }
    }

    private static int readingDirectionFromJson(String direction) {
        switch (direction) {
            case "ltr":
                return 0;
            case "rtl":
                return 1;
            case "vertical":
                return 2;
            default:
                return -1;
        }
    }

    private static String pageScalingToJson(int scaling) {
        switch (scaling) {
            case 0:
                return "actual";
            case 1:
                return "width";
            case 2:
                return "height";
            case 4:
                return "fixed";
            default:
                return "fit";
        }
    }

    private static int pageScalingFromJson(String scaling) {
        switch (scaling) {
            case "actual":
                return 0;
            case "width":
                return 1;
            case "height":
                return 2;
            case "fit":
                return 3;
            case "fixed":
                return 4;
            default:
                return -1;
        }
    }

    private static String startPositionToJson(int position) {
        switch (position) {
            case 0:
                return "top_left";
            case 2:
                return "bottom_left";
            case 3:
                return "bottom_right";
            case 4:
                return "center";
            default:
                return "top_right";
        }
    }

    private static int startPositionFromJson(String position) {
        switch (position) {
            case "top_left":
                return 0;
            case "top_right":
                return 1;
            case "bottom_left":
                return 2;
            case "bottom_right":
                return 3;
            case "center":
                return 4;
            default:
                return -1;
        }
    }

    /** Returns the string value, or empty when the field is absent. */
    private static String optStringIfPresent(JSONObject section, String key) {
        return section.has(key) ? section.optString(key, "") : "";
    }

    /** Returns a non-negative int, or {@code -1} when the field is absent or not a number. */
    private static int optIntIfPresent(JSONObject section, String key) {
        Object value = section.opt(key);
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static void putBooleanIfBoolean(JSONObject section, String jsonKey, String prefsKey) {
        Object value = section.opt(jsonKey);
        if (value instanceof Boolean) {
            Settings.putBoolean(prefsKey, (Boolean) value);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sync transport
    // ---------------------------------------------------------------------------------------------

    /**
     * Uploads the local preferences to the server. The body follows the sync
     * push contract with {@code entities.preferences} carrying the exported
     * document. Throws {@link IOException} on transport or server errors.
     *
     * <p>Note on {@code lastModified}: the server's preferences merge is
     * currently always-overwrite — {@code SyncService.push} delegates to
     * {@code UserPreferenceService.replace}, which stores the incoming document
     * unconditionally and stamps its own server {@code updatedAt} wall clock
     * without comparing the client's timestamp. The real value sent below is
     * therefore informational for now, but keeps the push metadata schema
     * compliant (syncMetadata requires {@code lastModified}) and stays
     * meaningful if the server ever starts comparing it.
     */
    public static void pushToServer(@NonNull WebUiConfig config, @NonNull Context context, @NonNull String deviceId)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            JSONObject entity = new JSONObject();
            entity.put("preferences", exportPreferences(context));
            // No per-key timestamps exist in Settings, so the wall clock at push
            // time is the best available "last modified" signal for this device.
            entity.put("lastModified", System.currentTimeMillis());
            entity.put("deviceId", deviceId);
            JSONObject entities = new JSONObject();
            entities.put("preferences", entity);
            body.put("entities", entities);
            body.put("deviceId", deviceId);
            body.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new IOException("Build push request failed", e);
        }
        String json = postJson(config.baseUrl() + PATH_PUSH, config.getToken(), body.toString());
        JSONObject root = parseResponse(json);
        if (root == null || !root.optBoolean("success", false)) {
            throw new IOException("Server rejected push");
        }
    }

    /**
     * Fetches the server preferences and applies them locally. A missing or
     * empty preferences entity is a no-op. Throws {@link IOException} on
     * transport or server errors.
     *
     * <p>The {@code since} query value is the persisted sync high-water mark
     * (0 on first sync). The server always returns the full preferences entity
     * regardless of {@code since} — only the other entity lists (favorites,
     * history, …) are filtered by it — so the document is never truncated;
     * passing the real mark just avoids re-listing those entities on every
     * cycle. The high-water mark is only read here, never advanced: it is owned
     * by the main sync task ({@code WebUiSyncFragment.SyncTask} persists the
     * server timestamp via {@code WebUiSettings#setLastSyncTimestamp}), and
     * advancing it from an interleaved preferences pull could skip favorites or
     * history that the main cycle has not pulled yet.
     */
    public static void pullFromServer(@NonNull WebUiConfig config, @NonNull Context context, @NonNull String deviceId)
            throws IOException {
        long since = new WebUiSettings(context).lastSyncTimestamp();
        String json = get(config.baseUrl() + PATH_PULL + "?since=" + since, config.getToken());
        JSONObject root = parseResponse(json);
        if (root == null) {
            throw new IOException("Empty pull response");
        }
        JSONObject entities = root.optJSONObject("entities");
        JSONObject entity = entities != null ? entities.optJSONObject("preferences") : null;
        if (entity == null) {
            return;
        }
        String preferences = entity.optString("preferences");
        if (TextUtils.isEmpty(preferences)) {
            return;
        }
        importPreferences(context, preferences);
    }

    private static JSONObject parseResponse(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String get(String url, String token) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        addAuth(builder, token);
        return execute(builder.build());
    }

    private static String postJson(String url, String token, String json) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON_MEDIA));
        addAuth(builder, token);
        return execute(builder.build());
    }

    private static void addAuth(Request.Builder builder, String token) {
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private static String execute(Request request) throws IOException {
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return text;
        }
    }
}
