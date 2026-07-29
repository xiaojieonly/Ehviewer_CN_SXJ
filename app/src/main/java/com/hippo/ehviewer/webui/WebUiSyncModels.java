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

package com.hippo.ehviewer.webui;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire models for the WebUI sync protocol, matching {@code contracts/sync-schemas.json}
 * and the server's {@code SyncDto.kt}. Serialized with fastjson (the app's existing
 * JSON library). Field names mirror the schema exactly so no annotations are needed.
 *
 * <p>The gallery-bearing entities share {@link GalleryBase}; the JSON wire shape is
 * flat, which inherited public fields produce correctly.
 */
public final class WebUiSyncModels {

    private WebUiSyncModels() {}

    /** GalleryInfoBase + SyncMetadata (sync-schemas.json galleryInfoBase + syncMetadata). */
    public abstract static class GalleryBase {
        public long gid;
        public String token;
        public String title;
        public String titleJpn;
        public String thumb;
        public int category;
        public String posted;
        public String uploader;
        public float rating;
        public boolean rated;
        public String simpleLanguage;
        /** Semicolon-separated tag list (schema: simpleTags is a string). */
        public String simpleTags;
        public int thumbWidth;
        public int thumbHeight;
        public int spanSize;
        public int spanIndex;
        public int spanGroupIndex;
        public int favoriteSlot = -2;
        public String favoriteName;
        public int pages;
        // SyncMetadata
        public long lastModified;
        public String deviceId = "";
        public boolean deleted;
    }

    public static class SyncFavorite extends GalleryBase {
        public long time;
    }

    public static class SyncHistory extends GalleryBase {
        public int mode;
        public long time;
    }

    public static class SyncDownload extends GalleryBase {
        public int state;
        public int legacy;
        public long time;
        public String label;
        public int total;
        public int finished;
        public int downloaded;
    }

    public static class SyncBookmark extends GalleryBase {
        public int page;
        public long time;
    }

    public static class SyncFilter {
        public int mode;
        public String text;
        public boolean enabled = true;
        public long lastModified;
        public String deviceId = "";
        public boolean deleted;
    }

    public static class SyncQuickSearch {
        public String name;
        public int mode;
        public int category;
        public String keyword;
        public int advanceSearch;
        public int minRating;
        public int pageFrom;
        public int pageTo;
        public long time;
        public long lastModified;
        public String deviceId = "";
        public boolean deleted;
    }

    /** syncEntityCollection — all entity arrays grouped by type. */
    public static class EntityCollection {
        public List<SyncFavorite> favorites = new ArrayList<>();
        public List<SyncHistory> history = new ArrayList<>();
        public List<SyncDownload> downloads = new ArrayList<>();
        public List<SyncBookmark> bookmarks = new ArrayList<>();
        public List<SyncFilter> filters = new ArrayList<>();
        public List<SyncQuickSearch> quickSearches = new ArrayList<>();
    }

    /** POST /api/v1/sync/push body. */
    public static class PushRequest {
        public EntityCollection entities = new EntityCollection();
        public String deviceId = "";
        public long timestamp;
    }

    public static class PushResponse {
        public boolean success;
        public long serverTimestamp;
        public int conflicts;
    }

    /** GET /api/v1/sync/pull response. */
    public static class PullResponse {
        public EntityCollection entities = new EntityCollection();
        public long serverTimestamp;
    }

    /** GET /api/v1/sync/status response. */
    public static class StatusResponse {
        public long lastSyncTimestamp;
        public List<ConnectedDevice> connectedDevices = new ArrayList<>();
        public EntityCounts entityCounts;
    }

    public static class ConnectedDevice {
        public String deviceId;
        public String deviceName;
        public String platform;
        public long lastSeen;
    }

    public static class EntityCounts {
        public long favorites;
        public long history;
        public long downloads;
        public long bookmarks;
        public long filters;
        public long quickSearches;
    }

    /** POST /api/v1/auth/login body. */
    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class AuthResponse {
        public boolean success;
        public String message;
        public String token;
        public String username;
    }
}
