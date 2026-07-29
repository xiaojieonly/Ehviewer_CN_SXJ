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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.HistoryInfo;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;

import java.io.IOException;
import java.util.List;

/**
 * Timestamp-incremental sync against the WebUI server for the two priority
 * entities: favorites (union merge) and history (last-write-wins). Implements
 * the push → pull → apply cycle from sync-conflict-rules.md §6, reusing the
 * existing GreenDAO storage via {@link EhDB}.
 *
 * <p>All methods are synchronous and must run off the main thread.
 */
public final class WebUiSyncEngine {

    private WebUiSyncEngine() {}

    public static final class Result {
        public int pushedFavorites;
        public int pushedHistory;
        public int pulledFavorites;
        public int pulledHistory;
        public long serverTimestamp;
    }

    /**
     * Runs a full sync cycle. {@code since} is the persisted high-water mark
     * (0 on first sync); the returned {@link Result#serverTimestamp} should be
     * persisted by the caller for the next run.
     */
    @NonNull
    public static Result sync(@NonNull WebUiConfig config, @NonNull String deviceId, long since) throws IOException {
        Result result = new Result();

        // 1. Push local state.
        WebUiSyncModels.PushRequest push = buildPush(deviceId);
        result.pushedFavorites = push.entities.favorites.size();
        result.pushedHistory = push.entities.history.size();
        WebUiSyncModels.PushResponse pushResponse = WebUiApiClient.push(config, push);
        if (!pushResponse.success) {
            throw new IOException("Server rejected push");
        }

        // 2. Pull server changes since the high-water mark.
        WebUiSyncModels.PullResponse pull = WebUiApiClient.pull(config, since);

        // 3. Apply pulled changes locally.
        applyFavorites(pull.entities.favorites, result);
        applyHistory(pull.entities.history, result);

        result.serverTimestamp = pull.serverTimestamp;
        return result;
    }

    private static WebUiSyncModels.PushRequest buildPush(String deviceId) {
        WebUiSyncModels.PushRequest request = new WebUiSyncModels.PushRequest();
        request.deviceId = deviceId;
        request.timestamp = System.currentTimeMillis();

        long now = System.currentTimeMillis();
        for (GalleryInfo gi : EhDB.getAllLocalFavorites()) {
            WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
            copyGalleryToDto(gi, fav);
            long time = gi instanceof LocalFavoriteInfo ? ((LocalFavoriteInfo) gi).time : now;
            fav.time = time;
            fav.lastModified = time;
            fav.deviceId = deviceId;
            fav.deleted = false;
            request.entities.favorites.add(fav);
        }

        for (HistoryInfo hi : EhDB.getAllHistoryForSync()) {
            WebUiSyncModels.SyncHistory hist = new WebUiSyncModels.SyncHistory();
            copyGalleryToDto(hi, hist);
            hist.mode = hi.mode;
            hist.time = hi.time;
            hist.lastModified = hi.time;
            hist.deviceId = deviceId;
            hist.deleted = false;
            request.entities.history.add(hist);
        }
        return request;
    }

    /** Favorites use union merge: never delete a remote entry, insert-if-absent. */
    private static void applyFavorites(List<WebUiSyncModels.SyncFavorite> favorites, Result result) {
        for (WebUiSyncModels.SyncFavorite fav : favorites) {
            if (fav.deleted) {
                continue; // union merge: a remote soft-delete must not remove a local favorite
            }
            if (EhDB.containLocalFavorites(fav.gid)) {
                continue;
            }
            LocalFavoriteInfo info = new LocalFavoriteInfo();
            copyDtoToGallery(fav, info);
            info.time = fav.time;
            EhDB.putLocalFavorite(info);
            result.pulledFavorites++;
        }
    }

    /** History uses last-write-wins with hard-delete. */
    private static void applyHistory(List<WebUiSyncModels.SyncHistory> history, Result result) {
        for (WebUiSyncModels.SyncHistory hist : history) {
            if (hist.deleted) {
                EhDB.removeHistoryByKey(hist.gid);
                result.pulledHistory++;
                continue;
            }
            HistoryInfo info = new HistoryInfo();
            copyDtoToGallery(hist, info);
            info.mode = hist.mode;
            info.time = hist.time;
            EhDB.applySyncedHistory(info);
            result.pulledHistory++;
        }
    }

    private static void copyGalleryToDto(GalleryInfo gi, WebUiSyncModels.GalleryBase dto) {
        dto.gid = gi.gid;
        dto.token = gi.token;
        dto.title = gi.title;
        dto.titleJpn = gi.titleJpn;
        dto.thumb = gi.thumb;
        dto.category = gi.category;
        dto.posted = gi.posted;
        dto.uploader = gi.uploader;
        dto.rating = gi.rating;
        dto.rated = gi.rated;
        dto.simpleLanguage = gi.simpleLanguage;
        dto.simpleTags = joinTags(gi.simpleTags);
        dto.thumbWidth = gi.thumbWidth;
        dto.thumbHeight = gi.thumbHeight;
        dto.spanSize = gi.spanSize;
        dto.spanIndex = gi.spanIndex;
        dto.spanGroupIndex = gi.spanGroupIndex;
        dto.favoriteSlot = gi.favoriteSlot;
        dto.favoriteName = gi.favoriteName;
        dto.pages = gi.pages;
    }

    private static void copyDtoToGallery(WebUiSyncModels.GalleryBase dto, GalleryInfo gi) {
        gi.gid = dto.gid;
        gi.token = dto.token;
        gi.title = dto.title;
        gi.titleJpn = dto.titleJpn;
        gi.thumb = dto.thumb;
        gi.category = dto.category;
        gi.posted = dto.posted;
        gi.uploader = dto.uploader;
        gi.rating = dto.rating;
        gi.rated = dto.rated;
        gi.simpleLanguage = dto.simpleLanguage;
        gi.simpleTags = splitTags(dto.simpleTags);
        gi.thumbWidth = dto.thumbWidth;
        gi.thumbHeight = dto.thumbHeight;
        gi.spanSize = dto.spanSize;
        gi.spanIndex = dto.spanIndex;
        gi.spanGroupIndex = dto.spanGroupIndex;
        gi.favoriteSlot = dto.favoriteSlot;
        gi.favoriteName = dto.favoriteName;
        gi.pages = dto.pages;
    }

    private static String joinTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return null;
        }
        return TextUtils.join(";", tags);
    }

    private static String[] splitTags(String tags) {
        if (TextUtils.isEmpty(tags)) {
            return null;
        }
        return tags.split(";");
    }
}
