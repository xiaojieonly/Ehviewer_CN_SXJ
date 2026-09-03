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

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.client.SiteCookieStore;
import com.hippo.anotherviewer.client.SiteUrl;
import com.hippo.anotherviewer.client.SiteUtils;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

/**
 * Timestamp-incremental sync against the WebUI server for all eight entities:
 * favorites (union merge), history (last-write-wins), downloads (union merge +
 * status sync), bookmarks (last-write-wins, hard delete), filters (union merge,
 * soft delete), quick searches (union merge, soft delete), download labels
 * (union merge, soft delete) and the single EH login session ehSession
 * (last-write-wins by lastModified, same tier as preferences — ADR-0004;
 * a deleted tombstone propagates under every conflict strategy). Implements the
 * push → pull → apply cycle from sync-conflict-rules.md §6, reusing the
 * existing GreenDAO storage via SiteDB (behind the {@link WebUiSyncStore}
 * seam). The ehSession entity has no natural idempotency key (a singleton), so
 * its snapshot/pending sets hold one fixed key; its push ledger tracks a
 * content fingerprint — the session carries no trusted change timestamp, so
 * content identity stands in for lastModified (a pulled session is not echoed
 * back, any real change is).
 *
 * <p>Deletion propagation: local removals are detected by diffing the current
 * local key sets against the snapshot of the last successful sync — the local
 * key sets re-collected at its save point, so keys adopted from a pull or
 * added mid-cycle are included (persisted per server URL in SharedPreferences).
 * Detected pending deletions are written through to the store at detection
 * time, before the push (W5, R4-15 audit §2.1): a process death between
 * detection and the end-of-cycle save cannot lose them — the next cycle
 * re-delivers the tombstones from the durable pending sets even if the
 * persisted snapshot no longer covers the keys. Removed keys are pushed as
 * {@code deleted: true} tombstones — soft for favorites, downloads, filters,
 * quick searches and download labels (the server keeps the record alive so
 * other devices can resurrect it, per union semantics) and hard for history and
 * bookmarks (the server deletes the record). Incoming server tombstones are
 * honored locally without re-adding. Re-adding a locally-deleted key drops it
 * from the pending deletions so it is pushed alive again instead of being
 * re-deleted.
 *
 * <p>B9 incremental push: the push phase no longer re-sends every live record
 * each cycle (contract §6.1: "Client sends ALL entities modified since its
 * last successful sync"). Per server URL a push ledger maps entity key →
 * lastModified last delivered to (or adopted from) the server, persisted via
 * {@link WebUiSyncStore}. Live records are pushed only when their key is
 * missing from the ledger (new) or their effective lastModified differs from
 * the ledger entry (changed); records applied from a pull enter the ledger at
 * their local effective value so they are not echoed back. Favorites, history,
 * bookmarks and downloads are ledgered on their wire lastModified (add/view
 * time, DownloadManager stamp). Filters, quick searches and download labels
 * stay full-push: those small sets carry no trusted change timestamp, so a
 * ledger could silently miss content changes. A corrupt/unreadable ledger
 * loads as "no ledger" and the cycle falls back to a full push for that
 * entity (safe default), rewriting a clean ledger on success. When an incoming
 * soft tombstone is not honored (§3.8 priority guard), the surviving local
 * record's ledger entry is dropped so the next push re-sends it alive and
 * resurrects the record on the server — what full-push used to do implicitly.
 * The push schema is unchanged; only the sent set shrinks.
 *
 * <p>The push phase is chunked: downloads — the only realistically huge entity
 * — are split into batches of {@link #PUSH_BATCH_SIZE} records (live records
 * and tombstones alike); all other entities ride along on the first batch.
 * Every batch must be accepted for the sync to proceed.
 *
 * <p>All methods are synchronous and must run off the main thread.
 *
 * <p>The engine talks to two seams so the full cycle is JVM-testable:
 * {@link WebUiSyncStore} for data (production: {@link SiteDbWebUiSyncStore}
 * wrapping SiteDB + SharedPreferences; tests: an in-memory Map store) and
 * {@link WebUiSyncTransport} for the network (production:
 * {@link WebUiApiSyncTransport} wrapping WebUiApiClient; tests: an in-memory
 * fake server). The static {@link #sync} facade keeps the production call
 * sites unchanged; the instance entry point {@link #syncInternal} is what the
 * tests drive.
 */
public final class WebUiSyncEngine {

    private static final String SUFFIX_SNAPSHOT_FAVORITES = ".snapshot.favorites";
    private static final String SUFFIX_SNAPSHOT_HISTORY = ".snapshot.history";
    private static final String SUFFIX_PENDING_FAVORITES = ".pending.favorites";
    private static final String SUFFIX_PENDING_HISTORY = ".pending.history";
    private static final String SUFFIX_SNAPSHOT_DOWNLOADS = ".snapshot.downloads";
    private static final String SUFFIX_PENDING_DOWNLOADS = ".pending.downloads";
    private static final String SUFFIX_SNAPSHOT_BOOKMARKS = ".snapshot.bookmarks";
    private static final String SUFFIX_PENDING_BOOKMARKS = ".pending.bookmarks";
    private static final String SUFFIX_SNAPSHOT_FILTERS = ".snapshot.filters";
    private static final String SUFFIX_PENDING_FILTERS = ".pending.filters";
    private static final String SUFFIX_SNAPSHOT_QUICK_SEARCHES = ".snapshot.quickSearches";
    private static final String SUFFIX_PENDING_QUICK_SEARCHES = ".pending.quickSearches";
    private static final String SUFFIX_SNAPSHOT_DOWNLOAD_LABELS = ".snapshot.downloadLabels";
    private static final String SUFFIX_PENDING_DOWNLOAD_LABELS = ".pending.downloadLabels";
    // The ehSession entity is a singleton: snapshot/pending hold one fixed key,
    // and the push ledger tracks a content fingerprint (see isEhSessionChanged).
    private static final String SUFFIX_SNAPSHOT_EH_SESSION = ".snapshot.ehSession";
    private static final String SUFFIX_PENDING_EH_SESSION = ".pending.ehSession";
    private static final String SUFFIX_LEDGER_EH_SESSION = ".ledger.ehSession";
    /** Fixed key of the singleton ehSession entity in the key-set collections. */
    private static final String KEY_EH_SESSION = "session";
    // B9 push ledgers (key -> lastModified last delivered/adopted), only for
    // the entities with a trusted change timestamp; filters, quick searches
    // and download labels stay full-push (see buildPushRequests).
    private static final String SUFFIX_LEDGER_FAVORITES = ".ledger.favorites";
    private static final String SUFFIX_LEDGER_HISTORY = ".ledger.history";
    private static final String SUFFIX_LEDGER_BOOKMARKS = ".ledger.bookmarks";
    private static final String SUFFIX_LEDGER_DOWNLOADS = ".ledger.downloads";
    private static final String KEY_SEPARATOR = "|";
    private static final int PUSH_BATCH_SIZE = 500;

    /**
     * Conflict arbitration strategy (contract v2 §1.4 / ADR-0003 D1).
     * {@code lww} is the v1 fallback (legacy servers, unknown values).
     */
    public enum ConflictStrategy {
        DEVICE_PRIORITY, LWW, WEB_PRIORITY;

        static ConflictStrategy parse(String value) {
            if ("device_priority".equals(value)) return DEVICE_PRIORITY;
            if ("web_priority".equals(value)) return WEB_PRIORITY;
            return LWW;
        }

        /** Platform that wins cross-platform same-key conflicts; null for LWW. */
        String priorityPlatform() {
            if (this == DEVICE_PRIORITY) return "android";
            if (this == WEB_PRIORITY) return "web";
            return null;
        }
    }

    /** deviceId format {@code {platform}-{uuid}} (contract §7). */
    static String platformOf(String deviceId) {
        if (deviceId == null) return "";
        int sep = deviceId.indexOf('-');
        return sep > 0 ? deviceId.substring(0, sep) : deviceId;
    }

    /**
     * Supplies the device's SyncPolicy for the push phase (ADR-0003 D2: the
     * android push is authoritative). Production wires a WebUiSettings-backed
     * source; tests and the unwired default use the contract defaults.
     */
    public interface PolicySource {
        WebUiSyncModels.SyncPolicy policy();
    }

    private static volatile PolicySource sPolicySource = () -> new WebUiSyncModels.SyncPolicy();

    public static void setPolicySource(PolicySource source) {
        sPolicySource = source != null ? source : () -> new WebUiSyncModels.SyncPolicy();
    }

    /**
     * Supplies the device's local EH login session and applies a pulled one
     * (ADR-0004). Production wires {@link SiteEhSessionSource}, which reads the
     * {@link SiteCookieStore} jar plus the display/avatar/gallery-site Settings;
     * tests substitute an in-memory source per device. A {@code null} result
     * from {@link #loadLocal()} means signed out / no session — the engine then
     * pushes a {@code deleted: true} tombstone when a session was previously
     * synced. {@link #applyRemote} writes cookies back to the jar and mirrors
     * the user-settings fields; a {@code deleted} session signs the device out.
     */
    public interface EhSessionSource {
        @Nullable
        WebUiSyncModels.SyncEhSession loadLocal();

        void applyRemote(@NonNull WebUiSyncModels.SyncEhSession session);
    }

    /**
     * Contract v2 §3.8: a soft-entity tombstone is honored locally unless the
     * deleting device is non-priority while this (live, retaining) device is
     * the priority platform — in that case the local copy survives and is
     * re-pushed alive next cycle so the server resurrects it. Tombstone
     * entities (history/bookmark) always propagate and bypass this guard.
     */
    static boolean honorSoftTombstone(ConflictStrategy strategy, String tombstoneDeviceId,
            String localDeviceId, boolean localAlive) {
        String priority = strategy.priorityPlatform();
        if (priority == null) return true;
        if (priority.equals(platformOf(tombstoneDeviceId))) return true;
        if (localAlive && priority.equals(platformOf(localDeviceId))) return false;
        return true;
    }

    /**
     * Contract v2 §3.8 double-alive arbitration for a pulled alive record that
     * collides with a locally-alive record. Cross-platform same-key conflicts
     * are won unconditionally by the priority platform under A/C — the winner
     * is applied even when its {@code lastModified} is older than the local
     * copy (timestamp does not arbitrate, §1.4). Same-platform conflicts and
     * the B strategy fall back to last-write-wins via {@code incomingNewer}.
     * Without this guard the client's plain LWW would keep a newer local copy
     * and diverge from the server's authoritative priority winner.
     */
    static boolean aliveRecordWins(ConflictStrategy strategy, String incomingDeviceId,
            String localDeviceId, boolean incomingNewer) {
        String priority = strategy.priorityPlatform();
        if (priority == null) return incomingNewer;
        String incomingPlatform = platformOf(incomingDeviceId);
        String localPlatform = platformOf(localDeviceId);
        if (incomingPlatform.equals(localPlatform)) return incomingNewer;
        return priority.equals(incomingPlatform);
    }

    private static volatile WebUiSyncEngine sInstance;

    private final WebUiSyncStore mStore;
    private final WebUiSyncTransport mTransport;
    private final EhSessionSource mEhSessionSource;

    private WebUiSyncEngine() {
        this(new SiteDbWebUiSyncStore(), new WebUiApiSyncTransport(), new SiteEhSessionSource());
    }

    /**
     * Test-facing constructor: supplies the data and network seams directly so
     * a sync cycle can run without Android or the real server.
     */
    WebUiSyncEngine(WebUiSyncStore store, WebUiSyncTransport transport) {
        this(store, transport, new SiteEhSessionSource());
    }

    /**
     * Test-facing constructor with the EH session seam supplied explicitly.
     * The default {@link SiteEhSessionSource} answers "no local session" when
     * no app instance exists, so the two-argument form stays JVM-safe.
     */
    WebUiSyncEngine(WebUiSyncStore store, WebUiSyncTransport transport,
            EhSessionSource ehSessionSource) {
        mStore = store;
        mTransport = transport;
        mEhSessionSource = ehSessionSource;
    }

    /**
     * Production {@link EhSessionSource}: the EH session lives in the
     * {@link SiteCookieStore} jar (all gallery-site cookies, not just the
     * identity pair) plus the display/avatar/gallery-site Settings. Cookie
     * values travel plaintext in the wire model — the server encrypts them at
     * rest (enc:v1: + security.key) and HTTPS protects them in transit.
     */
    private static final class SiteEhSessionSource implements EhSessionSource {

        /** Gallery-site hosts whose stored cookies form the session (contract). */
        private static final HttpUrl[] SITE_URLS = {
                HttpUrl.parse(SiteUrl.HOST_E),
                HttpUrl.parse(SiteUrl.HOST_EX),
                HttpUrl.parse("https://" + SiteUrl.DOMAIN_FORUMS + "/"),
                HttpUrl.parse("https://ehgt.org/"),
        };

        @Nullable
        @Override
        public WebUiSyncModels.SyncEhSession loadLocal() {
            SiteApplication app = SiteApplication.getInstance();
            if (app == null) {
                return null;
            }
            SiteCookieStore store = SiteApplication.getSiteCookieStore(app);
            Set<String> seen = new HashSet<>();
            List<WebUiSyncModels.SyncEhCookie> cookies = new ArrayList<>();
            for (HttpUrl url : SITE_URLS) {
                for (Cookie cookie : store.getCookies(url)) {
                    if (seen.add(cookie.name() + '|' + cookie.domain() + '|' + cookie.path())) {
                        cookies.add(toDto(cookie));
                    }
                }
            }
            if (cookies.isEmpty()) {
                return null;
            }
            WebUiSyncModels.SyncEhSession session = new WebUiSyncModels.SyncEhSession();
            session.cookies = cookies;
            session.displayName = Settings.getDisplayName();
            session.avatar = Settings.getAvatar();
            session.gallerySite = Settings.getGallerySite();
            return session;
        }

        @Override
        public void applyRemote(@NonNull WebUiSyncModels.SyncEhSession session) {
            SiteApplication app = SiteApplication.getInstance();
            if (app == null) {
                return;
            }
            SiteCookieStore store = SiteApplication.getSiteCookieStore(app);
            if (session.deleted) {
                // Tombstone: the session was logged out / cleared elsewhere.
                SiteUtils.signOut(app);
                return;
            }
            // The pulled session is the authoritative EH session state: replace
            // the jar contents, then mirror the user-settings fields.
            store.clear();
            for (WebUiSyncModels.SyncEhCookie dto : session.cookies) {
                try {
                    store.addCookie(toCookie(dto));
                } catch (IllegalArgumentException ignored) {
                    // Skip a cookie the local Cookie.Builder rejects.
                }
            }
            Settings.putDisplayName(session.displayName);
            Settings.putAvatar(session.avatar);
            if (session.gallerySite != null) {
                // Contract: absent/null gallerySite keeps the current selection.
                Settings.putGallerySite(session.gallerySite);
            }
        }

        private static WebUiSyncModels.SyncEhCookie toDto(Cookie cookie) {
            WebUiSyncModels.SyncEhCookie dto = new WebUiSyncModels.SyncEhCookie();
            dto.name = cookie.name();
            dto.value = cookie.value();
            dto.domain = cookie.domain();
            dto.path = cookie.path();
            dto.expiresAt = cookie.persistent() ? cookie.expiresAt() : 0L;
            dto.secure = cookie.secure();
            dto.httpOnly = cookie.httpOnly();
            dto.persistent = cookie.persistent();
            dto.hostOnly = cookie.hostOnly();
            return dto;
        }

        private static Cookie toCookie(WebUiSyncModels.SyncEhCookie dto) {
            Cookie.Builder builder = new Cookie.Builder();
            builder.name(dto.name);
            builder.value(dto.value);
            if (dto.hostOnly) {
                builder.hostOnlyDomain(dto.domain);
            } else {
                builder.domain(dto.domain);
            }
            builder.path(dto.path);
            if (dto.expiresAt > 0L) {
                builder.expiresAt(dto.expiresAt);
            }
            if (dto.secure) {
                builder.secure();
            }
            if (dto.httpOnly) {
                builder.httpOnly();
            }
            return builder.build();
        }
    }

    private static WebUiSyncEngine instance() {
        WebUiSyncEngine engine = sInstance;
        if (engine == null) {
            synchronized (WebUiSyncEngine.class) {
                engine = sInstance;
                if (engine == null) {
                    engine = new WebUiSyncEngine();
                    sInstance = engine;
                }
            }
        }
        return engine;
    }

    public static final class Result {
        public int pushedFavorites;
        public int pushedHistory;
        public int pulledFavorites;
        public int pulledHistory;
        public int pushedDownloads, pulledDownloads, pushedBookmarks, pulledBookmarks;
        public int pushedFilters, pulledFilters, pushedQuickSearches, pulledQuickSearches;
        public int pushedDownloadLabels, pulledDownloadLabels;
        public int pushedEhSessions, pulledEhSessions;
        public long serverTimestamp;
    }

    /**
     * Runs a full sync cycle. {@code since} is the persisted high-water mark
     * (0 on first sync); the returned {@link Result#serverTimestamp} should be
     * persisted by the caller for the next run.
     */
    @NonNull
    public static Result sync(@NonNull WebUiConfig config, @NonNull String deviceId, long since) throws IOException {
        return instance().syncInternal(config, deviceId, since);
    }

    /**
     * Instance sync entry point: the static facade delegates here. Tests drive
     * this directly against an in-memory store/transport.
     */
    @NonNull
    Result syncInternal(@NonNull WebUiConfig config, @NonNull String deviceId, long since) throws IOException {
        Result result = new Result();
        String serverKey = config.baseUrl();

        // Keys pushed by the last successful sync, plus deletions detected but
        // not yet delivered. Diffed against the current local keys to find keys
        // removed locally since the last push; keys re-added locally (present in
        // both the pending set and the local set) are dropped automatically.
        Set<Long> snapshotFavorites = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_FAVORITES);
        Set<Long> snapshotHistory = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_HISTORY);
        Set<Long> snapshotDownloads = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOADS);
        Set<Long> snapshotBookmarks = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_BOOKMARKS);
        Set<String> snapshotFilters = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_FILTERS);
        Set<String> snapshotQuickSearches = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_QUICK_SEARCHES);
        Set<String> snapshotDownloadLabels = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOAD_LABELS);
        Set<String> snapshotEhSession = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_EH_SESSION);

        Set<Long> pendingFavorites = mStore.loadKeySet(serverKey, SUFFIX_PENDING_FAVORITES);
        Set<Long> pendingHistory = mStore.loadKeySet(serverKey, SUFFIX_PENDING_HISTORY);
        Set<Long> pendingDownloads = mStore.loadKeySet(serverKey, SUFFIX_PENDING_DOWNLOADS);
        Set<Long> pendingBookmarks = mStore.loadKeySet(serverKey, SUFFIX_PENDING_BOOKMARKS);
        // 持久化的待删/待推集合也可能混入 gid<=0 的损坏 key（早期版本写入），
        // 与 current 集合一样剔除，避免 push 携带空 token 记录被服务器 400 拒收。
        pendingFavorites.removeIf(k -> k <= 0L);
        pendingHistory.removeIf(k -> k <= 0L);
        pendingDownloads.removeIf(k -> k <= 0L);
        pendingBookmarks.removeIf(k -> k <= 0L);
        Set<String> pendingFilters = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_FILTERS);
        Set<String> pendingQuickSearches = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_QUICK_SEARCHES);
        Set<String> pendingDownloadLabels = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_DOWNLOAD_LABELS);
        Set<String> pendingEhSession = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_EH_SESSION);

        Set<Long> currentFavorites = collectFavoriteKeys();
        Set<Long> currentHistory = collectHistoryKeys();
        Set<Long> currentDownloads = collectDownloadKeys();
        Set<Long> currentBookmarks = collectBookmarkKeys();
        Set<String> currentFilters = collectFilterKeys();
        Set<String> currentQuickSearches = collectQuickSearchKeys();
        Set<String> currentDownloadLabels = collectDownloadLabelKeys();
        WebUiSyncModels.SyncEhSession localEhSession = mEhSessionSource.loadLocal();
        Set<String> currentEhSession = localEhSession != null
                ? Collections.singleton(KEY_EH_SESSION)
                : Collections.<String>emptySet();

        pendingFavorites = detectDeletions(snapshotFavorites, pendingFavorites, currentFavorites);
        pendingHistory = detectDeletions(snapshotHistory, pendingHistory, currentHistory);
        pendingDownloads = detectDeletions(snapshotDownloads, pendingDownloads, currentDownloads);
        pendingBookmarks = detectDeletions(snapshotBookmarks, pendingBookmarks, currentBookmarks);
        pendingFilters = detectDeletions(snapshotFilters, pendingFilters, currentFilters);
        pendingQuickSearches = detectDeletions(snapshotQuickSearches, pendingQuickSearches, currentQuickSearches);
        pendingDownloadLabels = detectDeletions(snapshotDownloadLabels, pendingDownloadLabels, currentDownloadLabels);
        pendingEhSession = detectDeletions(snapshotEhSession, pendingEhSession, currentEhSession);

        // W5 write-through (R4-15 audit §2.1 hardening): persist the detected
        // pending tombstones NOW — at detection time, before the push — not
        // only via the end-of-cycle save block. If the process dies anywhere
        // between detection and save (in particular the ack-after/save-before
        // window), the tombstones are already durable and the next cycle
        // re-delivers them from the pending sets alone, without depending on
        // the persisted snapshot still containing the keys. The
        // ack-before-clear main order is unchanged: pending is only CLEARED
        // in the save block after every push batch was acked, and the B9
        // ledgers are untouched here; a failed cycle simply leaves the
        // written-through tombstones in place for the retry.
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_FAVORITES, pendingFavorites);
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_HISTORY, pendingHistory);
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_DOWNLOADS, pendingDownloads);
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_BOOKMARKS, pendingBookmarks);
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_FILTERS, pendingFilters);
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_QUICK_SEARCHES, pendingQuickSearches);
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_DOWNLOAD_LABELS, pendingDownloadLabels);
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_EH_SESSION, pendingEhSession);

        // B9: the push ledgers (key -> lastModified last delivered/adopted).
        // A null (corrupt/unreadable) ledger normalizes to empty, which marks
        // every live record as new — the full-push fallback (safe default).
        Map<String, Long> ledgerFavorites = loadLedgerOrEmpty(serverKey, SUFFIX_LEDGER_FAVORITES);
        Map<String, Long> ledgerHistory = loadLedgerOrEmpty(serverKey, SUFFIX_LEDGER_HISTORY);
        Map<String, Long> ledgerBookmarks = loadLedgerOrEmpty(serverKey, SUFFIX_LEDGER_BOOKMARKS);
        Map<String, Long> ledgerDownloads = loadLedgerOrEmpty(serverKey, SUFFIX_LEDGER_DOWNLOADS);
        // ehSession: the ledger value is a content fingerprint (no trusted
        // change timestamp exists), so unchanged content is not re-pushed and a
        // pulled session is not echoed back.
        Map<String, Long> ledgerEhSession = loadLedgerOrEmpty(serverKey, SUFFIX_LEDGER_EH_SESSION);
        boolean pushLiveEhSession = localEhSession != null
                && isEhSessionChanged(ledgerEhSession, localEhSession);

        // 1. Push local state, including tombstones for pending deletions.
        // B9 incremental: only new/changed live records (per the ledgers) and
        // tombstones are sent; the small timestamp-less entities (filters,
        // quick searches, download labels) ride along in full. Downloads are
        // chunked so no request carries more than PUSH_BATCH_SIZE of them;
        // the push succeeds only if every batch is accepted, otherwise no
        // bookkeeping (ledgers, snapshot, pending) is saved and the next
        // cycle retries.
        long now = System.currentTimeMillis();
        List<GalleryInfo> favoritesToPush = selectChangedFavorites(ledgerFavorites, now);
        List<HistoryInfo> historyToPush = selectChangedHistory(ledgerHistory);
        List<BookmarkInfo> bookmarksToPush = selectChangedBookmarks(ledgerBookmarks);
        List<DownloadInfo> downloadsToPush = selectChangedDownloads(ledgerDownloads);

        List<WebUiSyncModels.PushRequest> requests = buildPushRequests(deviceId, now,
                favoritesToPush, historyToPush, bookmarksToPush, downloadsToPush,
                pendingFavorites, pendingHistory, pendingDownloads, pendingBookmarks,
                pendingFilters, pendingQuickSearches, pendingDownloadLabels,
                pendingEhSession, localEhSession, pushLiveEhSession);
        for (int i = 0; i < requests.size(); i++) {
            WebUiSyncModels.PushRequest push = requests.get(i);
            WebUiSyncModels.PushResponse pushResponse = mTransport.push(config, push);
            if (!pushResponse.success) {
                // A5: carry the failing batch position and its entity counts in
                // the message — a bare "Server rejected push" gives no clue
                // which chunk or how many records to look at.
                WebUiSyncModels.EntityCollection failed = push.entities;
                throw new IOException("Server rejected push batch " + (i + 1) + "/"
                        + requests.size()
                        + " (favorites=" + failed.favorites.size()
                        + ", history=" + failed.history.size()
                        + ", downloads=" + failed.downloads.size()
                        + ", bookmarks=" + failed.bookmarks.size()
                        + ", filters=" + failed.filters.size()
                        + ", quickSearches=" + failed.quickSearches.size()
                        + ", downloadLabels=" + failed.downloadLabels.size()
                        + ", ehSession=" + failed.ehSession.size() + ")");
            }
            WebUiSyncModels.EntityCollection entities = push.entities;
            result.pushedFavorites += entities.favorites.size();
            result.pushedHistory += entities.history.size();
            result.pushedDownloads += entities.downloads.size();
            result.pushedBookmarks += entities.bookmarks.size();
            result.pushedFilters += entities.filters.size();
            result.pushedQuickSearches += entities.quickSearches.size();
            result.pushedDownloadLabels += entities.downloadLabels.size();
            result.pushedEhSessions += entities.ehSession.size();
        }

        // The push is the new baseline; the pending deletions were delivered.
        // Advance the ledgers to what the server now holds from this device:
        // pushed live records at their effective lastModified, tombstoned
        // keys dropped (a later re-add pushes as new again).
        for (GalleryInfo gi : favoritesToPush) {
            ledgerFavorites.put(Long.toString(gi.gid), favoriteLastModified(gi, now));
        }
        for (long gid : pendingFavorites) {
            ledgerFavorites.remove(Long.toString(gid));
        }
        for (HistoryInfo hi : historyToPush) {
            ledgerHistory.put(Long.toString(hi.gid), hi.time);
        }
        for (long gid : pendingHistory) {
            ledgerHistory.remove(Long.toString(gid));
        }
        for (BookmarkInfo bi : bookmarksToPush) {
            ledgerBookmarks.put(Long.toString(bi.gid), bi.time);
        }
        for (long gid : pendingBookmarks) {
            ledgerBookmarks.remove(Long.toString(gid));
        }
        for (DownloadInfo info : downloadsToPush) {
            ledgerDownloads.put(Long.toString(info.gid), downloadLastModified(info));
        }
        for (long gid : pendingDownloads) {
            ledgerDownloads.remove(Long.toString(gid));
        }
        if (pushLiveEhSession) {
            ledgerEhSession.put(KEY_EH_SESSION, fingerprintEhSession(localEhSession));
        }
        if (pendingEhSession.contains(KEY_EH_SESSION)) {
            // The logged-out tombstone was delivered; a later re-login pushes
            // as new again.
            ledgerEhSession.remove(KEY_EH_SESSION);
        }

        // The snapshot is finalized at save time (re-collected from the local
        // store after apply), not here — see the save block below (R4-15 W1/W2).

        // 2. Pull server changes since the high-water mark.
        WebUiSyncModels.PullResponse pull = mTransport.pull(config, since);

        // Contract v2 §6.2: strategy rides on the pull; legacy servers (no
        // policy) fall back to lww without error (compat matrix §4.2).
        ConflictStrategy strategy = pull.policy != null
                ? ConflictStrategy.parse(pull.policy.conflictStrategy)
                : ConflictStrategy.LWW;

        // 3. Apply pulled changes locally. The ledgered entities also record
        // the applied records' effective local lastModified so the next push
        // does not echo them back to the server.
        applyFavorites(pull.entities.favorites, result, snapshotFavorites, ledgerFavorites, strategy, deviceId);
        applyHistory(pull.entities.history, result, snapshotHistory, ledgerHistory);
        applyDownloads(pull.entities.downloads, result, snapshotDownloads, ledgerDownloads, strategy, deviceId);
        applyBookmarks(pull.entities.bookmarks, result, snapshotBookmarks, ledgerBookmarks, strategy, deviceId);
        applyFilters(pull.entities.filters, result, snapshotFilters, strategy, deviceId);
        applyQuickSearches(pull.entities.quickSearches, result, snapshotQuickSearches, strategy, deviceId);
        applyDownloadLabels(pull.entities.downloadLabels, result, snapshotDownloadLabels, strategy, deviceId);
        // ehSession follows the server unconditionally (the server already
        // resolved the LWW merge); a pulled tombstone signs the device out.
        applyEhSession(pull.entities.ehSession, result, snapshotEhSession, ledgerEhSession);

        // R4-15 W1/W2: the snapshot is finalized HERE — re-collected from the
        // local store at save time, after apply — instead of reusing the
        // pre-pull current sets. The persisted snapshot must equal the local
        // truth at the moment it is saved:
        // - applyFavorites/applyHistory adopt pulled keys into the local store
        //   without ever adding them to the snapshot (the W1 gap): once the
        //   saved snapshot lacked such a key, a later local deletion of it
        //   never entered detectDeletions, so its tombstone was never
        //   generated and the server row stayed alive forever;
        // - records added or removed mid-cycle are captured too (W2: once
        //   this save lands, the ack-before-save kill window no longer leaves
        //   adopted/new keys out of the persisted baseline).
        // Keys removed by honored server tombstones are gone from the local
        // store and therefore naturally absent — they are not re-emitted next
        // cycle. The snapshot add/remove bookkeeping inside apply is retained
        // as harmless no-ops against this recollection.
        snapshotFavorites = collectFavoriteKeys();
        snapshotHistory = collectHistoryKeys();
        snapshotDownloads = collectDownloadKeys();
        snapshotBookmarks = collectBookmarkKeys();
        snapshotFilters = collectFilterKeys();
        snapshotQuickSearches = collectQuickSearchKeys();
        snapshotDownloadLabels = collectDownloadLabelKeys();
        snapshotEhSession = collectEhSessionKeys();

        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_FAVORITES, snapshotFavorites);
        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_HISTORY, snapshotHistory);
        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOADS, snapshotDownloads);
        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_BOOKMARKS, snapshotBookmarks);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_FILTERS, snapshotFilters);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_QUICK_SEARCHES, snapshotQuickSearches);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOAD_LABELS, snapshotDownloadLabels);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_EH_SESSION, snapshotEhSession);
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_FAVORITES, Collections.emptySet());
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_HISTORY, Collections.emptySet());
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_DOWNLOADS, Collections.emptySet());
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_BOOKMARKS, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_FILTERS, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_QUICK_SEARCHES, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_DOWNLOAD_LABELS, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_EH_SESSION, Collections.emptySet());
        mStore.savePushLedger(serverKey, SUFFIX_LEDGER_FAVORITES, ledgerFavorites);
        mStore.savePushLedger(serverKey, SUFFIX_LEDGER_HISTORY, ledgerHistory);
        mStore.savePushLedger(serverKey, SUFFIX_LEDGER_BOOKMARKS, ledgerBookmarks);
        mStore.savePushLedger(serverKey, SUFFIX_LEDGER_DOWNLOADS, ledgerDownloads);
        mStore.savePushLedger(serverKey, SUFFIX_LEDGER_EH_SESSION, ledgerEhSession);

        result.serverTimestamp = pull.serverTimestamp;
        return result;
    }

    /**
     * B9: loads a push ledger, normalizing {@code null} (corrupt/unreadable
     * persisted value) to an empty map — with no known entries every live
     * record counts as new, so the cycle degrades to a full push for that
     * entity (safe default) and rewrites a clean ledger on success.
     */
    private Map<String, Long> loadLedgerOrEmpty(String serverKey, String suffix) {
        Map<String, Long> ledger = mStore.loadPushLedger(serverKey, suffix);
        return ledger != null ? ledger : new LinkedHashMap<>();
    }

    // --- B9 incremental push selection ---
    // A live record is sent when its key is absent from the ledger (new) or
    // its effective push lastModified differs from the ledger entry
    // (changed). Everything else stays home. An empty ledger (first sync or
    // corrupt-ledger fallback) therefore selects every live record.

    private List<GalleryInfo> selectChangedFavorites(Map<String, Long> ledger, long now) {
        List<GalleryInfo> selected = new ArrayList<>();
        for (GalleryInfo gi : mStore.getAllLocalFavorites()) {
            if (gi.gid <= 0L) {
                continue;
            }
            if (isNewOrChanged(ledger, gi.gid, favoriteLastModified(gi, now))) {
                selected.add(gi);
            }
        }
        return selected;
    }

    private List<HistoryInfo> selectChangedHistory(Map<String, Long> ledger) {
        List<HistoryInfo> selected = new ArrayList<>();
        for (HistoryInfo hi : mStore.getAllHistoryForSync()) {
            if (hi.gid <= 0L) {
                continue;
            }
            if (isNewOrChanged(ledger, hi.gid, hi.time)) {
                selected.add(hi);
            }
        }
        return selected;
    }

    private List<BookmarkInfo> selectChangedBookmarks(Map<String, Long> ledger) {
        List<BookmarkInfo> selected = new ArrayList<>();
        for (BookmarkInfo bi : mStore.getAllBookmark()) {
            if (bi.gid <= 0L) {
                continue;
            }
            if (isNewOrChanged(ledger, bi.gid, bi.time)) {
                selected.add(bi);
            }
        }
        return selected;
    }

    private List<DownloadInfo> selectChangedDownloads(Map<String, Long> ledger) {
        List<DownloadInfo> selected = new ArrayList<>();
        for (DownloadInfo info : mStore.getAllDownloadInfo()) {
            if (info.gid <= 0L) {
                continue;
            }
            if (isNewOrChanged(ledger, info.gid, downloadLastModified(info))) {
                selected.add(info);
            }
        }
        return selected;
    }

    private static boolean isNewOrChanged(Map<String, Long> ledger, long key, long effectiveLastModified) {
        Long known = ledger.get(Long.toString(key));
        return known == null || known.longValue() != effectiveLastModified;
    }

    /**
     * Effective push lastModified of a favorite: its add time. Gallery rows
     * that are not {@link LocalFavoriteInfo} (never returned by the
     * production store) fall back to {@code now}, which also makes them
     * re-pushable every cycle.
     */
    private static long favoriteLastModified(GalleryInfo gi, long now) {
        return gi instanceof LocalFavoriteInfo ? ((LocalFavoriteInfo) gi).time : now;
    }

    /**
     * Effective push lastModified of a download. B2: DownloadManager stamps
     * {@code lastModified} on every local state change; pre-v8 rows with an
     * empty column fall back to the record's {@code time}. A3: rows written
     * by the apply phase (including a self-push echo) adopt the wire stamp
     * via {@link #copyDtoToDownload}, so a rewritten row keeps its true
     * recency instead of degrading to {@code time}.
     */
    private static long downloadLastModified(DownloadInfo info) {
        return info.lastModified > 0 ? info.lastModified : info.time;
    }

    private List<WebUiSyncModels.PushRequest> buildPushRequests(String deviceId, long now,
            List<GalleryInfo> favoritesToPush, List<HistoryInfo> historyToPush,
            List<BookmarkInfo> bookmarksToPush, List<DownloadInfo> downloadsToPush,
            Set<Long> pendingFavorites, Set<Long> pendingHistory,
            Set<Long> pendingDownloads, Set<Long> pendingBookmarks,
            Set<String> pendingFilters, Set<String> pendingQuickSearches,
            Set<String> pendingDownloadLabels,
            Set<String> pendingEhSession, WebUiSyncModels.SyncEhSession localEhSession,
            boolean pushLiveEhSession) {
        List<Long> downloadTombstones = new ArrayList<>(pendingDownloads);

        // Downloads are the only realistically huge entity; chunk both live
        // records and tombstones into batches of PUSH_BATCH_SIZE. All other
        // entities are small and ride along on the first batch. B9: the
        // ledgered live lists are already narrowed to new/changed records;
        // filters, quick searches and download labels stay FULL-push — local
        // Filter rows carry no timestamp at all and QuickSearch/DownloadLabel
        // content edits do not bump their wire `time`, so a lastModified
        // ledger could silently miss content changes there. Those sets are
        // small (dozens of rows), so full-push costs negligible bandwidth
        // next to the gallery-bearing entities.
        int liveBatches = (downloadsToPush.size() + PUSH_BATCH_SIZE - 1) / PUSH_BATCH_SIZE;
        int tombBatches = (downloadTombstones.size() + PUSH_BATCH_SIZE - 1) / PUSH_BATCH_SIZE;
        int batchCount = Math.max(1, Math.max(liveBatches, tombBatches));

        List<WebUiSyncModels.PushRequest> requests = new ArrayList<>(batchCount);
        for (int i = 0; i < batchCount; i++) {
            WebUiSyncModels.PushRequest request = new WebUiSyncModels.PushRequest();
            request.deviceId = deviceId;
            request.timestamp = now;
            // D2: the android push carries the device policy (authoritative).
            request.policy = sPolicySource.policy();

            if (i == 0) {
                fillFavorites(request.entities, deviceId, now, favoritesToPush, pendingFavorites);
                fillHistory(request.entities, deviceId, now, historyToPush, pendingHistory);
                fillBookmarks(request.entities, deviceId, now, bookmarksToPush, pendingBookmarks);
                fillFilters(request.entities, deviceId, now, pendingFilters);
                fillQuickSearches(request.entities, deviceId, now, pendingQuickSearches);
                fillDownloadLabels(request.entities, deviceId, now, pendingDownloadLabels);
                fillEhSession(request.entities, deviceId, now, localEhSession, pushLiveEhSession, pendingEhSession);
            }

            int from = i * PUSH_BATCH_SIZE;
            int to = Math.min(from + PUSH_BATCH_SIZE, downloadsToPush.size());
            for (int j = from; j < to; j++) {
                fillDownload(request.entities, deviceId, now, downloadsToPush.get(j));
            }
            int tFrom = i * PUSH_BATCH_SIZE;
            int tTo = Math.min(tFrom + PUSH_BATCH_SIZE, downloadTombstones.size());
            for (int j = tFrom; j < tTo; j++) {
                // Soft tombstone: the server stores it only when no live record
                // exists (union merge, contract §3.3).
                WebUiSyncModels.SyncDownload dto = new WebUiSyncModels.SyncDownload();
                dto.gid = downloadTombstones.get(j);
                dto.lastModified = now;
                dto.deviceId = deviceId;
                dto.deleted = true;
                request.entities.downloads.add(dto);
            }
            requests.add(request);
        }
        return requests;
    }

    private void fillFavorites(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, List<GalleryInfo> favoritesToPush,
            Set<Long> pendingFavorites) {
        for (GalleryInfo gi : favoritesToPush) {
            WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
            copyGalleryToDto(gi, fav);
            long time = favoriteLastModified(gi, now);
            fav.time = time;
            fav.lastModified = time;
            fav.deviceId = deviceId;
            fav.deleted = false;
            entities.favorites.add(fav);
        }
        for (long gid : pendingFavorites) {
            // Soft tombstone: the server stores it only when no live record
            // exists (union merge, contract §3.1/§4.1).
            WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
            fav.gid = gid;
            fav.lastModified = now;
            fav.deviceId = deviceId;
            fav.deleted = true;
            entities.favorites.add(fav);
        }
    }

    private void fillHistory(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, List<HistoryInfo> historyToPush,
            Set<Long> pendingHistory) {
        for (HistoryInfo hi : historyToPush) {
            WebUiSyncModels.SyncHistory hist = new WebUiSyncModels.SyncHistory();
            copyGalleryToDto(hi, hist);
            hist.mode = hi.mode;
            hist.page = hi.page;
            hist.time = hi.time;
            hist.lastModified = hi.time;
            hist.deviceId = deviceId;
            hist.deleted = false;
            entities.history.add(hist);
        }
        for (long gid : pendingHistory) {
            // Hard-delete tombstone: the server removes the record entirely
            // (mergeHistory deletes on deleted=true, contract §3.2/§4.2).
            WebUiSyncModels.SyncHistory hist = new WebUiSyncModels.SyncHistory();
            hist.gid = gid;
            hist.lastModified = now;
            hist.deviceId = deviceId;
            hist.deleted = true;
            entities.history.add(hist);
        }
    }

    private void fillDownload(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, DownloadInfo info) {
        WebUiSyncModels.SyncDownload dto = new WebUiSyncModels.SyncDownload();
        copyGalleryToDto(info, dto);
        dto.state = info.state;
        dto.legacy = info.legacy;
        dto.time = info.time;
        dto.label = info.label;
        dto.total = info.total;
        dto.finished = info.finished;
        dto.downloaded = info.downloaded;
        dto.lastModified = downloadLastModified(info);
        dto.deviceId = deviceId;
        dto.deleted = false;
        entities.downloads.add(dto);
    }

    private void fillBookmarks(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, List<BookmarkInfo> bookmarksToPush,
            Set<Long> pendingBookmarks) {
        for (BookmarkInfo bi : bookmarksToPush) {
            WebUiSyncModels.SyncBookmark dto = new WebUiSyncModels.SyncBookmark();
            copyGalleryToDto(bi, dto);
            dto.page = bi.page;
            dto.time = bi.time;
            dto.lastModified = bi.time;
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.bookmarks.add(dto);
        }
        for (long gid : pendingBookmarks) {
            // Hard-delete tombstone: the server removes the row entirely
            // (mergeBookmark deletes on deleted=true, contract §3.4/§4.4).
            WebUiSyncModels.SyncBookmark dto = new WebUiSyncModels.SyncBookmark();
            dto.gid = gid;
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.bookmarks.add(dto);
        }
    }

    private void fillFilters(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<String> pendingFilters) {
        for (Filter f : mStore.getAllFilter()) {
            WebUiSyncModels.SyncFilter dto = new WebUiSyncModels.SyncFilter();
            dto.mode = f.mode;
            dto.text = f.text;
            dto.enabled = f.enable != null && f.enable;
            // Local filters carry no timestamp; the push side is always newest.
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.filters.add(dto);
        }
        for (String key : pendingFilters) {
            // Composite key "mode|text" (filter text never contains '|').
            WebUiSyncModels.SyncFilter dto = new WebUiSyncModels.SyncFilter();
            int sep = key.indexOf(KEY_SEPARATOR);
            try {
                dto.mode = Integer.parseInt(key.substring(0, sep));
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                // Skip malformed tombstone keys; the set is rebuilt on save.
                continue;
            }
            dto.text = key.substring(sep + 1);
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.filters.add(dto);
        }
    }

    private void fillQuickSearches(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<String> pendingQuickSearches) {
        for (QuickSearch qs : mStore.getAllQuickSearch()) {
            WebUiSyncModels.SyncQuickSearch dto = new WebUiSyncModels.SyncQuickSearch();
            dto.name = qs.name;
            dto.mode = qs.mode;
            dto.category = qs.category;
            dto.keyword = qs.keyword;
            dto.advanceSearch = qs.advanceSearch;
            dto.minRating = qs.minRating;
            dto.pageFrom = qs.pageFrom;
            dto.pageTo = qs.pageTo;
            dto.time = qs.time;
            dto.lastModified = qs.time;
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.quickSearches.add(dto);
        }
        for (String name : pendingQuickSearches) {
            // Soft tombstone (union merge, contract §3.6).
            WebUiSyncModels.SyncQuickSearch dto = new WebUiSyncModels.SyncQuickSearch();
            dto.name = name;
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.quickSearches.add(dto);
        }
    }

    private void fillDownloadLabels(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<String> pendingDownloadLabels) {
        for (DownloadLabel dl : mStore.getAllDownloadLabelList()) {
            WebUiSyncModels.SyncDownloadLabel dto = new WebUiSyncModels.SyncDownloadLabel();
            dto.label = dl.getLabel();
            dto.time = dl.getTime();
            dto.lastModified = dl.getTime();
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.downloadLabels.add(dto);
        }
        for (String label : pendingDownloadLabels) {
            // Soft tombstone (union merge, contract §3.7).
            WebUiSyncModels.SyncDownloadLabel dto = new WebUiSyncModels.SyncDownloadLabel();
            dto.label = label;
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.downloadLabels.add(dto);
        }
    }

    /**
     * Singleton ehSession: a pending deletion pushes a {@code deleted: true}
     * tombstone; otherwise the live session rides along only when its content
     * changed since the last push (see {@link #isEhSessionChanged}).
     */
    private void fillEhSession(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, WebUiSyncModels.SyncEhSession localSession,
            boolean pushLive, Set<String> pendingEhSession) {
        if (pendingEhSession.contains(KEY_EH_SESSION)) {
            // Tombstone: the user logged out / cookies cleared on this device;
            // the deletion propagates under every conflict strategy.
            WebUiSyncModels.SyncEhSession tomb = new WebUiSyncModels.SyncEhSession();
            tomb.lastModified = now;
            tomb.deviceId = deviceId;
            tomb.deleted = true;
            entities.ehSession.add(tomb);
        } else if (pushLive && localSession != null) {
            localSession.lastModified = now;
            localSession.deviceId = deviceId;
            localSession.deleted = false;
            entities.ehSession.add(localSession);
        }
    }

    /**
     * Favorites use union merge. Incoming tombstones remove the local copy
     * without re-adding; alive records are inserted when absent; existing
     * favorites get their metadata refreshed when the server copy is newer
     * (last-write-wins on lastModified, local add time is preserved).
     */
    private void applyFavorites(List<WebUiSyncModels.SyncFavorite> favorites,
            Result result, Set<Long> snapshotFavorites, Map<String, Long> ledger,
            ConflictStrategy strategy, String localDeviceId) {
        for (WebUiSyncModels.SyncFavorite fav : favorites) {
            String ledgerKey = Long.toString(fav.gid);
            if (fav.deleted) {
                // §3.8: a non-priority deletion does not remove the priority
                // platform's live copy — skip and let the next push resurrect.
                if (!honorSoftTombstone(strategy, fav.deviceId, localDeviceId,
                        mStore.loadLocalFavorite(fav.gid) != null)) {
                    // B9: drop the ledger entry so the surviving live record
                    // is re-sent next cycle and resurrects the server record
                    // (full-push used to do this implicitly every cycle).
                    ledger.remove(ledgerKey);
                    continue;
                }
                // Tombstone: this device has deleted the favorite (or another
                // device did) — honor it locally and never re-add it.
                mStore.removeLocalFavorites(fav.gid);
                snapshotFavorites.remove(fav.gid);
                ledger.remove(ledgerKey);
                result.pulledFavorites++;
                continue;
            }
            LocalFavoriteInfo local = mStore.loadLocalFavorite(fav.gid);
            if (local == null) {
                LocalFavoriteInfo info = new LocalFavoriteInfo();
                copyDtoToGallery(fav, info);
                info.time = fav.time;
                mStore.putLocalFavorite(info);
                // Adopted from the server: ledger it at its local effective
                // value so the next push does not echo it back.
                ledger.put(ledgerKey, info.time);
                result.pulledFavorites++;
            } else if (aliveRecordWins(strategy, fav.deviceId, localDeviceId, fav.lastModified > local.time)) {
                // §3.8 double-alive: the priority platform's record wins
                // unconditionally under A/C; otherwise last-write-wins (the
                // local favorite's push lastModified is its add time, so time
                // is the LWW comparison).
                copyDtoToGallery(fav, local);
                mStore.updateLocalFavorite(local);
                // updateLocalFavorite preserves the original add time, which
                // is the effective push lastModified.
                ledger.put(ledgerKey, local.time);
                result.pulledFavorites++;
            }
        }
    }

    /** History uses last-write-wins with hard-delete. */
    private void applyHistory(List<WebUiSyncModels.SyncHistory> history,
            Result result, Set<Long> snapshotHistory, Map<String, Long> ledger) {
        for (WebUiSyncModels.SyncHistory hist : history) {
            if (hist.deleted) {
                mStore.removeHistoryByKey(hist.gid);
                snapshotHistory.remove(hist.gid);
                ledger.remove(Long.toString(hist.gid));
                result.pulledHistory++;
                continue;
            }
            HistoryInfo info = new HistoryInfo();
            copyDtoToGallery(hist, info);
            info.mode = hist.mode;
            info.page = hist.page;
            info.time = hist.time;
            mStore.applySyncedHistory(info);
            // Ledger the pulled view time. applySyncedHistory keeps a newer
            // local row when one exists; the entry then reads slightly low
            // and the next cycle pushes the newer local row once, converging
            // the ledger to the value actually delivered.
            ledger.put(Long.toString(hist.gid), info.time);
            result.pulledHistory++;
        }
    }

    /**
     * Downloads follow the server unconditionally: incoming tombstones remove
     * the local record, alive records overwrite every synced field via
     * {@code putDownloadInfo} (the server already resolved union/LWW conflicts).
     */
    private void applyDownloads(List<WebUiSyncModels.SyncDownload> downloads,
            Result result, Set<Long> snapshotDownloads, Map<String, Long> ledger,
            ConflictStrategy strategy, String localDeviceId) {
        Map<Long, DownloadInfo> locals = new HashMap<>();
        for (DownloadInfo info : mStore.getAllDownloadInfo()) {
            locals.put(info.gid, info);
        }
        for (WebUiSyncModels.SyncDownload dto : downloads) {
            String ledgerKey = Long.toString(dto.gid);
            if (dto.deleted) {
                if (!honorSoftTombstone(strategy, dto.deviceId, localDeviceId,
                        locals.containsKey(dto.gid))) {
                    // B9: drop the ledger entry so the surviving live record
                    // is re-sent next cycle and resurrects the server record.
                    ledger.remove(ledgerKey);
                    continue;
                }
                // Soft tombstone delivered: the server no longer tracks it.
                mStore.removeDownloadInfo(dto.gid);
                locals.remove(dto.gid);
                snapshotDownloads.remove(dto.gid);
                ledger.remove(ledgerKey);
                result.pulledDownloads++;
                continue;
            }
            DownloadInfo info = new DownloadInfo();
            copyDtoToDownload(dto, info);
            DownloadInfo existing = locals.get(dto.gid);
            if (existing != null) {
                // Keep the local archive URI (not part of the wire model).
                info.archiveUri = existing.archiveUri;
            }
            mStore.putDownloadInfo(info);
            locals.put(dto.gid, info);
            snapshotDownloads.add(dto.gid);
            // A3: copyDtoToDownload now carries the wire B2 stamp into
            // info.lastModified, so a self-push echo rewritten in the same
            // cycle keeps its DownloadManager stamp instead of being wiped to
            // 0. Ledger at the row's effective push value (= what this device
            // would send for it), which keeps the no-echo guarantee intact.
            ledger.put(ledgerKey, downloadLastModified(info));
            result.pulledDownloads++;
        }
    }

    /**
     * Bookmarks hard-delete (tombstones always propagate, §3.8) and arbitrate
     * double-alive by §3.8: priority platform wins unconditionally under A/C,
     * else last-write-wins on lastModified (local time).
     */
    private void applyBookmarks(List<WebUiSyncModels.SyncBookmark> bookmarks,
            Result result, Set<Long> snapshotBookmarks, Map<String, Long> ledger,
            ConflictStrategy strategy, String localDeviceId) {
        Map<Long, BookmarkInfo> locals = new HashMap<>();
        for (BookmarkInfo bi : mStore.getAllBookmark()) {
            locals.put(bi.gid, bi);
        }
        for (WebUiSyncModels.SyncBookmark dto : bookmarks) {
            String ledgerKey = Long.toString(dto.gid);
            if (dto.deleted) {
                // Hard-delete tombstone: clearing a reading position propagates.
                mStore.removeBookmarkByGid(dto.gid);
                snapshotBookmarks.remove(dto.gid);
                ledger.remove(ledgerKey);
                result.pulledBookmarks++;
                continue;
            }
            BookmarkInfo local = locals.get(dto.gid);
            if (local == null) {
                BookmarkInfo info = new BookmarkInfo();
                copyDtoToGallery(dto, info);
                info.page = dto.page;
                info.time = dto.time;
                mStore.putBookmark(info);
                locals.put(dto.gid, info);
                snapshotBookmarks.add(dto.gid);
                ledger.put(ledgerKey, info.time);
                result.pulledBookmarks++;
            } else if (aliveRecordWins(strategy, dto.deviceId, localDeviceId, dto.lastModified > local.time)) {
                // §3.8 double-alive: priority platform wins unconditionally
                // under A/C; otherwise last-write-wins on lastModified.
                copyDtoToGallery(dto, local);
                local.page = dto.page;
                local.time = dto.time;
                mStore.putBookmark(local);
                snapshotBookmarks.add(dto.gid);
                ledger.put(ledgerKey, local.time);
                result.pulledBookmarks++;
            }
            // Losing branch: the local row is untouched, so its existing
            // ledger entry (if any) remains valid and needs no update.
        }
    }

    /**
     * Filters follow the server unconditionally (the local model has no
     * timestamp, so no LWW comparison is possible): the enable flag is applied
     * as-is to the existing (mode, text) row, or the row is created.
     */
    private void applyFilters(List<WebUiSyncModels.SyncFilter> filters,
            Result result, Set<String> snapshotFilters,
            ConflictStrategy strategy, String localDeviceId) {
        for (WebUiSyncModels.SyncFilter dto : filters) {
            String key = filterKey(dto.mode, dto.text);
            Filter existing = mStore.findFilterByKey(dto.mode, dto.text);
            if (dto.deleted) {
                if (!honorSoftTombstone(strategy, dto.deviceId, localDeviceId, existing != null)) {
                    continue;
                }
                // Soft tombstone: remove the local row without re-adding it.
                mStore.deleteFilterByKey(dto.mode, dto.text);
                snapshotFilters.remove(key);
                result.pulledFilters++;
                continue;
            }
            if (existing == null) {
                Filter filter = new Filter();
                filter.mode = dto.mode;
                filter.text = dto.text;
                filter.enable = dto.enabled;
                mStore.addFilter(filter);
            } else {
                setFilterEnabled(existing, dto.enabled);
            }
            snapshotFilters.add(key);
            result.pulledFilters++;
        }
    }

    /**
     * Applies the server's {@code enabled} flag to an existing filter row.
     * {@code triggerFilter} toggles, so it is only used when the current value
     * is known and differs; a null enable (meaning disabled) is replaced by
     * re-inserting the row when the server enables it, since toggling null
     * would throw.
     */
    private void setFilterEnabled(Filter filter, boolean enabled) {
        if (filter.enable == null) {
            if (enabled) {
                mStore.deleteFilterByKey(filter.mode, filter.text);
                Filter replacement = new Filter();
                replacement.mode = filter.mode;
                replacement.text = filter.text;
                replacement.enable = true;
                mStore.addFilter(replacement);
            }
        } else if (filter.enable != enabled) {
            mStore.triggerFilter(filter);
        }
    }

    /**
     * Quick searches follow the server unconditionally, keyed by name: deleted
     * tombstones remove the local row, alive records are inserted or updated
     * (keeping the local row id).
     */
    private void applyQuickSearches(List<WebUiSyncModels.SyncQuickSearch> searches,
            Result result, Set<String> snapshotQuickSearches,
            ConflictStrategy strategy, String localDeviceId) {
        Map<String, QuickSearch> locals = new HashMap<>();
        for (QuickSearch qs : mStore.getAllQuickSearch()) {
            locals.put(qs.name, qs);
        }
        for (WebUiSyncModels.SyncQuickSearch dto : searches) {
            if (dto.deleted) {
                if (!honorSoftTombstone(strategy, dto.deviceId, localDeviceId,
                        locals.containsKey(dto.name))) {
                    continue;
                }
                QuickSearch existing = locals.remove(dto.name);
                if (existing != null) {
                    mStore.deleteQuickSearch(existing);
                }
                snapshotQuickSearches.remove(dto.name);
                result.pulledQuickSearches++;
                continue;
            }
            QuickSearch local = locals.get(dto.name);
            if (local == null) {
                QuickSearch qs = new QuickSearch();
                copyDtoToQuickSearch(dto, qs);
                mStore.insertQuickSearch(qs);
                locals.put(dto.name, qs);
            } else {
                copyDtoToQuickSearch(dto, local);
                mStore.updateQuickSearch(local);
            }
            snapshotQuickSearches.add(dto.name);
            result.pulledQuickSearches++;
        }
    }

    /**
     * Download labels follow the server unconditionally, keyed by label name:
     * deleted tombstones remove the local row, alive records are inserted with
     * the server's time or updated in place.
     */
    private void applyDownloadLabels(List<WebUiSyncModels.SyncDownloadLabel> labels,
            Result result, Set<String> snapshotDownloadLabels,
            ConflictStrategy strategy, String localDeviceId) {
        Map<String, DownloadLabel> locals = new HashMap<>();
        for (DownloadLabel dl : mStore.getAllDownloadLabelList()) {
            locals.put(dl.getLabel(), dl);
        }
        for (WebUiSyncModels.SyncDownloadLabel dto : labels) {
            if (dto.deleted) {
                if (!honorSoftTombstone(strategy, dto.deviceId, localDeviceId,
                        locals.containsKey(dto.label))) {
                    continue;
                }
                DownloadLabel existing = locals.remove(dto.label);
                if (existing != null) {
                    mStore.removeDownloadLabel(existing);
                }
                snapshotDownloadLabels.remove(dto.label);
                result.pulledDownloadLabels++;
                continue;
            }
            DownloadLabel local = locals.get(dto.label);
            if (local == null) {
                DownloadLabel dl = new DownloadLabel();
                dl.setLabel(dto.label);
                dl.setTime(dto.time);
                mStore.addDownloadLabel(dl);
                locals.put(dto.label, dl);
            } else {
                local.setLabel(dto.label);
                local.setTime(dto.time);
                mStore.updateDownloadLabel(local);
            }
            snapshotDownloadLabels.add(dto.label);
            result.pulledDownloadLabels++;
        }
    }

    /**
     * The singleton ehSession follows the server unconditionally (the server
     * already resolved the last-write-wins merge; same tier as preferences):
     * a live session replaces the local cookies/settings, a tombstone signs
     * the device out. The ledger is advanced to a fingerprint of the LOCAL
     * state after apply so the next push does not echo the session back.
     */
    private void applyEhSession(List<WebUiSyncModels.SyncEhSession> sessions,
            Result result, Set<String> snapshotEhSession, Map<String, Long> ledger) {
        for (WebUiSyncModels.SyncEhSession session : sessions) {
            mEhSessionSource.applyRemote(session);
            if (session.deleted) {
                snapshotEhSession.remove(KEY_EH_SESSION);
                ledger.remove(KEY_EH_SESSION);
            } else {
                snapshotEhSession.add(KEY_EH_SESSION);
                WebUiSyncModels.SyncEhSession local = mEhSessionSource.loadLocal();
                if (local != null) {
                    ledger.put(KEY_EH_SESSION, fingerprintEhSession(local));
                } else {
                    ledger.remove(KEY_EH_SESSION);
                }
            }
            result.pulledEhSessions++;
        }
    }

    /**
     * Keys pushed before (or already pending) that no longer exist locally are
     * deletions to propagate. Keys present locally again (re-added) are dropped
     * so the next push resurrects them instead of re-deleting them.
     */
    private <T> Set<T> detectDeletions(Set<T> snapshot, Set<T> pending, Set<T> current) {
        Set<T> deletions = new LinkedHashSet<>(snapshot);
        deletions.addAll(pending);
        deletions.removeAll(current);
        return deletions;
    }

    private Set<Long> collectFavoriteKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (GalleryInfo gi : mStore.getAllLocalFavorites()) {
            keys.add(gi.gid);
        }
        keys.removeIf(k -> k <= 0L);
        return keys;
    }

    private Set<Long> collectHistoryKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (HistoryInfo hi : mStore.getAllHistoryForSync()) {
            keys.add(hi.gid);
        }
        // 剔除 gid<=0 的本地损坏记录（如早期空 token 详情页写入的历史）：它们无法
        // 被服务器接受（token 为空会使 push 400 拒收），必须排除在同步之外。
        keys.removeIf(k -> k <= 0L);
        return keys;
    }

    private Set<Long> collectDownloadKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (DownloadInfo info : mStore.getAllDownloadInfo()) {
            keys.add(info.gid);
        }
        keys.removeIf(k -> k <= 0L);
        return keys;
    }

    private Set<Long> collectBookmarkKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (BookmarkInfo bi : mStore.getAllBookmark()) {
            keys.add(bi.gid);
        }
        keys.removeIf(k -> k <= 0L);
        return keys;
    }

    private Set<String> collectFilterKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Filter f : mStore.getAllFilter()) {
            keys.add(filterKey(f.mode, f.text));
        }
        return keys;
    }

    private Set<String> collectQuickSearchKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (QuickSearch qs : mStore.getAllQuickSearch()) {
            keys.add(qs.name);
        }
        return keys;
    }

    private Set<String> collectDownloadLabelKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (DownloadLabel dl : mStore.getAllDownloadLabelList()) {
            keys.add(dl.getLabel());
        }
        return keys;
    }

    /** The singleton ehSession is keyed by a fixed key when a session exists. */
    private Set<String> collectEhSessionKeys() {
        return mEhSessionSource.loadLocal() != null
                ? Collections.singleton(KEY_EH_SESSION)
                : Collections.<String>emptySet();
    }

    /**
     * The ehSession carries no trusted change timestamp, so the push ledger
     * tracks a content fingerprint instead of a lastModified: the live session
     * is re-pushed only when its content differs from what was last
     * delivered/adopted — unchanged content (including a session adopted from
     * a pull) is never echoed back.
     */
    private boolean isEhSessionChanged(Map<String, Long> ledger,
            WebUiSyncModels.SyncEhSession local) {
        Long known = ledger.get(KEY_EH_SESSION);
        return known == null || known.longValue() != fingerprintEhSession(local);
    }

    /**
     * Deterministic 64-bit fingerprint over the session content (cookies sorted
     * by name/domain/path, then the user-settings fields). Cookie order from
     * the cookie jar is not stable across cycles, so it is normalized here.
     */
    static long fingerprintEhSession(WebUiSyncModels.SyncEhSession session) {
        long h = 1469598103934665603L; // FNV-1a 64-bit offset basis
        List<WebUiSyncModels.SyncEhCookie> cookies =
                session != null && session.cookies != null ? session.cookies
                        : Collections.<WebUiSyncModels.SyncEhCookie>emptyList();
        List<WebUiSyncModels.SyncEhCookie> sorted = new ArrayList<>(cookies);
        sorted.sort(Comparator.comparing((WebUiSyncModels.SyncEhCookie c) -> c.name,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(c -> c.domain, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(c -> c.path, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (WebUiSyncModels.SyncEhCookie dto : sorted) {
            h = mixFingerprint(h, dto.name);
            h = mixFingerprint(h, dto.value);
            h = mixFingerprint(h, dto.domain);
            h = mixFingerprint(h, dto.path);
            h = mixFingerprint(h, Long.toString(dto.expiresAt));
            h = mixFingerprint(h, Boolean.toString(dto.secure));
            h = mixFingerprint(h, Boolean.toString(dto.httpOnly));
            h = mixFingerprint(h, Boolean.toString(dto.persistent));
            h = mixFingerprint(h, Boolean.toString(dto.hostOnly));
        }
        if (session != null) {
            h = mixFingerprint(h, session.displayName);
            h = mixFingerprint(h, session.avatar);
            h = mixFingerprint(h, session.gallerySite != null
                    ? Integer.toString(session.gallerySite) : null);
        }
        return h;
    }

    private static long mixFingerprint(long h, String s) {
        if (s == null) {
            return (h ^ 0xffL) * 1099511628211L; // FNV-1a prime
        }
        long result = h;
        for (int i = 0; i < s.length(); i++) {
            result = (result ^ s.charAt(i)) * 1099511628211L;
        }
        return result;
    }

    private String filterKey(int mode, String text) {
        return mode + KEY_SEPARATOR + (text == null ? "" : text);
    }

    private void copyGalleryToDto(GalleryInfo gi, WebUiSyncModels.GalleryBase dto) {
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

    private void copyDtoToGallery(WebUiSyncModels.GalleryBase dto, GalleryInfo gi) {
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

    private void copyDtoToDownload(WebUiSyncModels.SyncDownload dto, DownloadInfo info) {
        copyDtoToGallery(dto, info);
        info.state = dto.state;
        info.legacy = dto.legacy;
        info.time = dto.time;
        info.label = dto.label;
        info.total = dto.total;
        info.finished = dto.finished;
        info.downloaded = dto.downloaded;
        // A3: pass the wire B2 stamp through so applyDownloads (including a
        // same-cycle self-push echo) never rewrites a local row with a wiped
        // (0) stamp. A 0 wire value lands as 0 — identical to the previous
        // behaviour of leaving the fresh row unstamped.
        info.lastModified = dto.lastModified;
    }

    private void copyDtoToQuickSearch(WebUiSyncModels.SyncQuickSearch dto, QuickSearch qs) {
        qs.name = dto.name;
        qs.mode = dto.mode;
        qs.category = dto.category;
        qs.keyword = dto.keyword;
        qs.advanceSearch = dto.advanceSearch;
        qs.minRating = dto.minRating;
        qs.pageFrom = dto.pageFrom;
        qs.pageTo = dto.pageTo;
        qs.time = dto.time;
    }

    private String joinTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return null;
        }
        return TextUtils.join(";", tags);
    }

    private String[] splitTags(String tags) {
        if (TextUtils.isEmpty(tags)) {
            return null;
        }
        return tags.split(";");
    }
}
