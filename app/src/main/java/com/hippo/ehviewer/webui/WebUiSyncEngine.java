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

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.HistoryInfo;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Timestamp-incremental sync against the WebUI server for the two priority
 * entities: favorites (union merge) and history (last-write-wins). Implements
 * the push → pull → apply cycle from sync-conflict-rules.md §6, reusing the
 * existing GreenDAO storage via {@link EhDB}.
 *
 * <p>Deletion propagation: local removals are detected by diffing the current
 * local key sets against the keys pushed in the last successful sync (persisted
 * per server URL in SharedPreferences). Removed keys are pushed as
 * {@code deleted: true} tombstones — soft for favorites (the server keeps the
 * record alive so other devices can resurrect it, per union semantics) and hard
 * for history (the server deletes the record). Incoming server tombstones are
 * honored locally: favorites are removed without re-adding, history is hard
 * deleted. Re-adding a locally-deleted favorite drops it from the pending
 * deletions so it is pushed alive again instead of being re-deleted.
 *
 * <p>All methods are synchronous and must run off the main thread.
 */
public final class WebUiSyncEngine {

    private static final String PREFS = "webui_sync_state";
    private static final String SUFFIX_SNAPSHOT_FAVORITES = ".snapshot.favorites";
    private static final String SUFFIX_SNAPSHOT_HISTORY = ".snapshot.history";
    private static final String SUFFIX_PENDING_FAVORITES = ".pending.favorites";
    private static final String SUFFIX_PENDING_HISTORY = ".pending.history";
    private static final String SEPARATOR = ",";

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
        String serverKey = config.baseUrl();

        // Keys pushed by the last successful sync, plus deletions detected but
        // not yet delivered. Diffed against the current local keys to find keys
        // removed locally since the last push; keys re-added locally (present in
        // both the pending set and the local set) are dropped automatically.
        Set<Long> snapshotFavorites = loadKeySet(serverKey, SUFFIX_SNAPSHOT_FAVORITES);
        Set<Long> snapshotHistory = loadKeySet(serverKey, SUFFIX_SNAPSHOT_HISTORY);
        Set<Long> pendingFavorites = loadKeySet(serverKey, SUFFIX_PENDING_FAVORITES);
        Set<Long> pendingHistory = loadKeySet(serverKey, SUFFIX_PENDING_HISTORY);

        Set<Long> currentFavorites = collectFavoriteKeys();
        Set<Long> currentHistory = collectHistoryKeys();

        pendingFavorites = detectDeletions(snapshotFavorites, pendingFavorites, currentFavorites);
        pendingHistory = detectDeletions(snapshotHistory, pendingHistory, currentHistory);

        // 1. Push local state, including tombstones for pending deletions.
        WebUiSyncModels.PushRequest push = buildPush(deviceId, pendingFavorites, pendingHistory);
        result.pushedFavorites = push.entities.favorites.size();
        result.pushedHistory = push.entities.history.size();
        WebUiSyncModels.PushResponse pushResponse = WebUiApiClient.push(config, push);
        if (!pushResponse.success) {
            throw new IOException("Server rejected push");
        }

        // The push is the new baseline; the pending deletions were delivered.
        // The snapshot is finalized after the pull so keys removed locally by
        // incoming server tombstones are not re-emitted next cycle.
        snapshotFavorites = currentFavorites;
        snapshotHistory = currentHistory;

        // 2. Pull server changes since the high-water mark.
        WebUiSyncModels.PullResponse pull = WebUiApiClient.pull(config, since);

        // 3. Apply pulled changes locally.
        applyFavorites(pull.entities.favorites, result, snapshotFavorites);
        applyHistory(pull.entities.history, result, snapshotHistory);

        saveKeySet(serverKey, SUFFIX_SNAPSHOT_FAVORITES, snapshotFavorites);
        saveKeySet(serverKey, SUFFIX_SNAPSHOT_HISTORY, snapshotHistory);
        saveKeySet(serverKey, SUFFIX_PENDING_FAVORITES, Collections.emptySet());
        saveKeySet(serverKey, SUFFIX_PENDING_HISTORY, Collections.emptySet());

        result.serverTimestamp = pull.serverTimestamp;
        return result;
    }

    private static WebUiSyncModels.PushRequest buildPush(String deviceId,
            Set<Long> pendingFavorites, Set<Long> pendingHistory) {
        WebUiSyncModels.PushRequest request = new WebUiSyncModels.PushRequest();
        request.deviceId = deviceId;
        long now = System.currentTimeMillis();
        request.timestamp = now;

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
        for (long gid : pendingFavorites) {
            // Soft tombstone: the server stores it only when no live record
            // exists (union merge, contract §3.1/§4.1).
            WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
            fav.gid = gid;
            fav.lastModified = now;
            fav.deviceId = deviceId;
            fav.deleted = true;
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
        for (long gid : pendingHistory) {
            // Hard-delete tombstone: the server removes the record entirely
            // (mergeHistory deletes on deleted=true, contract §3.2/§4.2).
            WebUiSyncModels.SyncHistory hist = new WebUiSyncModels.SyncHistory();
            hist.gid = gid;
            hist.lastModified = now;
            hist.deviceId = deviceId;
            hist.deleted = true;
            request.entities.history.add(hist);
        }
        return request;
    }

    /**
     * Favorites use union merge. Incoming tombstones remove the local copy
     * without re-adding; alive records are inserted when absent; existing
     * favorites get their metadata refreshed when the server copy is newer
     * (last-write-wins on lastModified, local add time is preserved).
     */
    private static void applyFavorites(List<WebUiSyncModels.SyncFavorite> favorites,
            Result result, Set<Long> snapshotFavorites) {
        for (WebUiSyncModels.SyncFavorite fav : favorites) {
            if (fav.deleted) {
                // Tombstone: this device has deleted the favorite (or another
                // device did) — honor it locally and never re-add it.
                EhDB.removeLocalFavorites(fav.gid);
                snapshotFavorites.remove(fav.gid);
                result.pulledFavorites++;
                continue;
            }
            LocalFavoriteInfo local = EhDB.loadLocalFavorite(fav.gid);
            if (local == null) {
                LocalFavoriteInfo info = new LocalFavoriteInfo();
                copyDtoToGallery(fav, info);
                info.time = fav.time;
                EhDB.putLocalFavorite(info);
                result.pulledFavorites++;
            } else if (fav.lastModified > local.time) {
                // Server copy is newer — refresh metadata (the local favorite's
                // push lastModified is its add time, so time is the comparison).
                copyDtoToGallery(fav, local);
                EhDB.updateLocalFavorite(local);
                result.pulledFavorites++;
            }
        }
    }

    /** History uses last-write-wins with hard-delete. */
    private static void applyHistory(List<WebUiSyncModels.SyncHistory> history,
            Result result, Set<Long> snapshotHistory) {
        for (WebUiSyncModels.SyncHistory hist : history) {
            if (hist.deleted) {
                EhDB.removeHistoryByKey(hist.gid);
                snapshotHistory.remove(hist.gid);
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

    /**
     * Keys pushed before (or already pending) that no longer exist locally are
     * deletions to propagate. Keys present locally again (re-added) are dropped
     * so the next push resurrects them instead of re-deleting them.
     */
    private static Set<Long> detectDeletions(Set<Long> snapshot, Set<Long> pending, Set<Long> current) {
        Set<Long> deletions = new LinkedHashSet<>(snapshot);
        deletions.addAll(pending);
        deletions.removeAll(current);
        return deletions;
    }

    private static Set<Long> collectFavoriteKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (GalleryInfo gi : EhDB.getAllLocalFavorites()) {
            keys.add(gi.gid);
        }
        return keys;
    }

    private static Set<Long> collectHistoryKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (HistoryInfo hi : EhDB.getAllHistoryForSync()) {
            keys.add(hi.gid);
        }
        return keys;
    }

    private static SharedPreferences prefs() {
        return EhApplication.getInstance().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<Long> loadKeySet(String serverKey, String suffix) {
        Set<Long> keys = new LinkedHashSet<>();
        String raw = prefs().getString(serverKey + suffix, "");
        if (TextUtils.isEmpty(raw)) {
            return keys;
        }
        for (String part : raw.split(SEPARATOR)) {
            try {
                keys.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
                // Skip malformed entries; the set is rebuilt on every save.
            }
        }
        return keys;
    }

    private static void saveKeySet(String serverKey, String suffix, Set<Long> keys) {
        StringBuilder sb = new StringBuilder();
        for (Long key : keys) {
            if (sb.length() > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(key);
        }
        prefs().edit().putString(serverKey + suffix, sb.toString()).apply();
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
