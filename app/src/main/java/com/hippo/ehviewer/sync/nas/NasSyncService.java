package com.hippo.ehviewer.sync.nas;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.event.SomethingNeedRefresh;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;
import java.security.MessageDigest;

import org.greenrobot.eventbus.EventBus;

public final class NasSyncService extends Service {
    private static final String TAG = "NasSyncService";
    public static final String ACTION_MERGE = "com.hippo.ehviewer.nas.MERGE";
    public static final String ACTION_UPLOAD_ALL = "com.hippo.ehviewer.nas.UPLOAD_ALL";
    public static final String ACTION_UPLOAD_METADATA =
            "com.hippo.ehviewer.nas.UPLOAD_METADATA";
    public static final String ACTION_DOWNLOAD_ALL = "com.hippo.ehviewer.nas.DOWNLOAD_ALL";
    public static final String ACTION_POST_DOWNLOAD = "com.hippo.ehviewer.nas.POST_DOWNLOAD";
    public static final String ACTION_DOWNLOAD_GALLERY = "com.hippo.ehviewer.nas.DOWNLOAD_GALLERY";
    public static final String ACTION_DELETE_GALLERY =
            "com.hippo.ehviewer.nas.DELETE_GALLERY";
    public static final String ACTION_IMPORT_DATABASE =
            "com.hippo.ehviewer.nas.IMPORT_DATABASE";
    private static final String ACTION_CANCEL = "com.hippo.ehviewer.nas.CANCEL";
    private static final String ACTION_CANCEL_GALLERY =
            "com.hippo.ehviewer.nas.CANCEL_GALLERY";
    private static final String EXTRA_GID = "gid";
    private static final String CHANNEL_ID = "nas_sync";
    private static final int NOTIFICATION_ID = 0x4e4153;
    private static final String KEY_LAST_IMPORTED_DATABASE_HASH =
            "nas_last_imported_database_sha256";
    private static final int DATABASE_RESTORED = 1;
    private static final int DATABASE_UNCHANGED = 2;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger cancelGeneration = new AtomicInteger();
    private final AtomicInteger pendingJobs = new AtomicInteger();
    private final java.util.Set<Long> cancelledGalleries = ConcurrentHashMap.newKeySet();
    private volatile int activeCancelGeneration;
    private volatile long activeGid = -1L;
    private NotificationManager notificationManager;
    private long startedAt;
    private int lastCurrent;
    private long lastNotifyAt;

    public static void start(Context context, String action) {
        Intent intent = new Intent(context, NasSyncService.class).setAction(action);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void onDownloadFinished(Context context, DownloadInfo info) {
        if (info.state != DownloadInfo.STATE_FINISH || !NasConfigStore.isEnabled(context)
                || !NasConfigStore.isConfigured()
                || NasConfigStore.DOWNLOAD_PHONE.equals(NasConfigStore.getDownloadBehavior())) {
            return;
        }
        Intent intent = new Intent(context, NasSyncService.class)
                .setAction(ACTION_POST_DOWNLOAD).putExtra(EXTRA_GID, info.gid);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void downloadGallery(Context context, long gid) {
        Intent intent = new Intent(context, NasSyncService.class)
                .setAction(ACTION_DOWNLOAD_GALLERY).putExtra(EXTRA_GID, gid);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void cancelGallery(Context context, long gid) {
        Intent intent = new Intent(context, NasSyncService.class)
                .setAction(ACTION_CANCEL_GALLERY).putExtra(EXTRA_GID, gid);
        // This action is invoked from the visible download screen. It only marks an active or
        // queued gallery job as cancelled and must not create a new foreground notification.
        context.startService(intent);
    }

    public static void deleteGallery(Context context, long gid) {
        Intent intent = new Intent(context, NasSyncService.class)
                .setAction(ACTION_DELETE_GALLERY).putExtra(EXTRA_GID, gid);
        ContextCompat.startForegroundService(context, intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.settings_nas), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelGeneration.incrementAndGet();
            if (pendingJobs.get() == 0) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL_GALLERY.equals(intent.getAction())) {
            long gid = intent.getLongExtra(EXTRA_GID, -1L);
            if (gid >= 0L) cancelledGalleries.add(gid);
            if (pendingJobs.get() == 0) stopSelf(startId);
            return START_NOT_STICKY;
        }
        int jobCancelGeneration = cancelGeneration.get();
        pendingJobs.incrementAndGet();
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.nas_sync_starting),
                0, -1, false));
        final String action = intent.getAction();
        final long gid = intent.getLongExtra(EXTRA_GID, -1L);
        executor.execute(() -> runJob(action, gid, jobCancelGeneration));
        return START_NOT_STICKY;
    }

    private void runJob(String action, long gid, int jobCancelGeneration) {
        activeCancelGeneration = jobCancelGeneration;
        activeGid = ACTION_DOWNLOAD_GALLERY.equals(action) ? gid : -1L;
        startedAt = SystemClock.elapsedRealtime();
        lastCurrent = 0;
        lastNotifyAt = 0L;
        notificationManager.notify(NOTIFICATION_ID, buildNotification(
                getString(R.string.nas_sync_starting), 0, -1, false));
        String completion;
        try {
            if (isCancelled()) throw new InterruptedIOException("NAS sync cancelled");
            if (!NasConfigStore.isEnabled(this) || !NasConfigStore.isConfigured()) {
                throw new IOException("NAS is disabled or not configured");
            }
            UniFile root = Settings.getDownloadLocation();
            if (root == null) throw new IOException("Download directory is not configured");
            if (ACTION_POST_DOWNLOAD.equals(action)) {
                completion = postDownload(root, gid);
            } else if (ACTION_DOWNLOAD_GALLERY.equals(action)) {
                completion = downloadGallery(root, gid);
            } else if (ACTION_DELETE_GALLERY.equals(action)) {
                completion = deleteGallery(root, gid);
            } else if (ACTION_IMPORT_DATABASE.equals(action)) {
                completion = importDatabase(root);
            } else {
                completion = runGeneralSync(root, action);
            }
            EventBus.getDefault().post(SomethingNeedRefresh.downloadInfoNeedRefresh());
            Log.i(TAG, completion);
            notifyFinished(completion, false);
        } catch (Exception error) {
            if (ACTION_DELETE_GALLERY.equals(action) && gid >= 0L) {
                // A failed remote deletion must become visible again so the user can inspect the
                // failure and retry. This also covers a directory deleted before a DB conflict.
                NasCatalogStore.showLocally(this, gid);
                EventBus.getDefault().post(SomethingNeedRefresh.downloadInfoNeedRefresh());
            }
            String detail = error.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = error.getClass().getSimpleName();
            String failure = isCancelled() ? getString(R.string.nas_sync_cancelled)
                    : getString(R.string.nas_sync_failed_kept_local, detail);
            Log.e(TAG, failure, error);
            notifyFinished(failure, true);
        } finally {
            if (ACTION_DOWNLOAD_GALLERY.equals(action)) cancelledGalleries.remove(gid);
            activeGid = -1L;
            if (pendingJobs.decrementAndGet() == 0) {
                stopForeground(false);
                stopSelf();
            }
        }
    }

    private String runGeneralSync(UniFile root, String action) throws Exception {
        NasSyncResult result;
        String suffix = "";
        if (ACTION_UPLOAD_ALL.equals(action) || ACTION_UPLOAD_METADATA.equals(action)) {
            boolean databaseOnly = ACTION_UPLOAD_METADATA.equals(action);
            File remoteDatabase = downloadDatabaseBackup(root, false);
            try {
                ensureRemoteDatabaseCurrent(remoteDatabase);
                NasDatabaseStore.Snapshot previous = remoteDatabase != null
                        ? NasDatabaseStore.read(remoteDatabase) : null;
                if (previous != null) {
                    NasCatalogStore.save(this, previous.generatedAt, previous.entries);
                }
                NasCatalogStore.mergeLocalDownloads(this, new ArrayList<>(EhApplication
                        .getDownloadManager(this).getAllDownloadInfoList()));
                List<String> order = galleryOrder();
                if (databaseOnly) {
                    result = new NasSyncResult();
                } else {
                    result = new NasSyncEngine(root, NasConfigStore.load(this)).syncAll(
                            NasSyncEngine.Direction.UPLOAD_TO_NAS, order,
                            previous != null ? previous.files
                                    : java.util.Collections.emptyList(),
                            this::onProgress, this::isCancelled);
                }
                boolean manifestBootstrapped = !databaseOnly
                        && (previous == null || previous.files.isEmpty())
                        && !result.manifest.isEmpty();
                DatabaseUpload uploaded = uploadDatabaseBackup(root, remoteDatabase,
                        databaseOnly ? null : result.manifest,
                        java.util.Collections.emptySet());
                if (uploaded.changed) result.uploaded++; else result.skipped++;
                if (databaseOnly) {
                    // Metadata-only sync deliberately avoids walking every gallery directory.
                    // A database backup is useful for restoring metadata, but it must not be
                    // reported as a complete/restorable image backup.
                    suffix = " · " + getString(R.string.nas_database_only_finished);
                } else {
                    NasSyncEngine.ValidationResult validation = requireRestorableBackup(root,
                            uploaded.snapshot);
                    suffix = " · " + getString(R.string.nas_restore_validated,
                            validation.galleries, validation.files);
                    if (manifestBootstrapped) {
                        suffix += " · " + getString(R.string.nas_manifest_built);
                    }
                }
            } finally {
                if (remoteDatabase != null) remoteDatabase.delete();
            }
        } else if (ACTION_DOWNLOAD_ALL.equals(action)) {
            File remoteDatabase = downloadDatabaseBackup(root, true);
            try {
                NasDatabaseStore.Snapshot snapshot = NasDatabaseStore.read(remoteDatabase);
                NasCatalogStore.save(this, snapshot.generatedAt, snapshot.entries);
                List<String> order = new ArrayList<>();
                for (NasCatalogEntry entry : snapshot.entries) order.add(entry.directoryName);
                result = new NasSyncEngine(root, NasConfigStore.load(this)).syncAll(
                        NasSyncEngine.Direction.DOWNLOAD_TO_PHONE, order, snapshot.files,
                        this::onProgress, this::isCancelled);
                if (snapshot.files.isEmpty() && !result.manifest.isEmpty()) {
                    uploadDiscoveredManifest(root, remoteDatabase, result.manifest, order);
                    result.uploaded++;
                }
                boolean manifestBootstrapped = snapshot.files.isEmpty()
                        && !result.manifest.isEmpty();
                int databaseRestore = restoreDatabaseBackup(remoteDatabase);
                materializeDownloadedCatalog(root);
                suffix = " · " + getString(databaseRestore == DATABASE_RESTORED
                        ? R.string.nas_database_restored
                        : R.string.nas_database_unchanged);
                if (manifestBootstrapped) {
                    suffix += " · " + getString(R.string.nas_manifest_built);
                }
            } finally {
                remoteDatabase.delete();
            }
        } else {
            File remoteDatabase = downloadDatabaseBackup(root, true);
            try {
                ensureRemoteDatabaseCurrent(remoteDatabase);
                NasDatabaseStore.Snapshot previous = NasDatabaseStore.read(remoteDatabase);
                NasCatalogStore.save(this, previous.generatedAt, previous.entries);
                result = new NasSyncEngine(root, NasConfigStore.load(this)).sync(galleryOrder(),
                        previous.files, this::onProgress, this::isCancelled);
                NasCatalogStore.mergeLocalDownloads(this, new ArrayList<>(EhApplication
                        .getDownloadManager(this).getAllDownloadInfoList()));
                DatabaseUpload uploaded = uploadDatabaseBackup(root, remoteDatabase);
                if (uploaded.changed) result.uploaded++; else result.skipped++;
                NasSyncEngine.ValidationResult validation = inspectRemoteBackup(root,
                        uploaded.snapshot);
                suffix = " · " + (validation.isRestorable()
                        ? getString(R.string.nas_restore_validated, validation.galleries,
                                validation.files)
                        : validationFailure(validation));
            } finally {
                remoteDatabase.delete();
            }
        }
        return getString(R.string.nas_sync_finished_summary, result.uploaded, result.downloaded,
                result.skipped) + suffix;
    }

    private String postDownload(UniFile root, long gid) throws Exception {
        DownloadInfo info = EhApplication.getDownloadManager(this).getDownloadInfo(gid);
        if (info == null || info.state != DownloadInfo.STATE_FINISH) {
            throw new IOException("Completed download not found");
        }
        String directoryName = EhDB.getDownloadDirname(gid);
        if (directoryName == null || directoryName.isEmpty()) {
            throw new IOException("Download directory mapping not found");
        }
        UniFile gallery = root.findFile(directoryName);
        if (gallery == null || !gallery.isDirectory()) throw new IOException("Gallery missing");
        File remoteDatabase = downloadDatabaseBackup(root, false);
        NasDatabaseStore.Snapshot previous = null;
        boolean databaseChanged = false;
        try {
            // Check the database revision before uploading gallery files. A stale local device
            // must pull the other device's changes instead of silently overwriting them.
            ensureRemoteDatabaseCurrent(remoteDatabase);
            if (remoteDatabase != null) {
                previous = NasDatabaseStore.read(remoteDatabase);
                NasCatalogStore.save(this, previous.generatedAt, previous.entries);
                NasCatalogStore.mergeLocalDownloads(this, new ArrayList<>(EhApplication
                        .getDownloadManager(this).getAllDownloadInfoList()));
            }
            NasSyncResult result = new NasSyncEngine(root, NasConfigStore.load(this))
                    .uploadGallery(gallery, directoryName,
                            previous != null ? previous.files
                                    : java.util.Collections.emptyList(),
                            galleryOrder(), this::onProgress,
                            this::isCancelled);
            NasCatalogStore.upsertFromDownload(this, info, directoryName);
            boolean remoteOnly = NasConfigStore.DOWNLOAD_NAS_ONLY.equals(
                    NasConfigStore.getDownloadBehavior());
            NasCatalogEntry entry = NasCatalogStore.find(this, gid);
            DownloadInfo nasOnly = remoteOnly && entry != null
                    ? NasCatalogStore.asNasOnlyDownload(entry) : null;
            if (nasOnly != null) {
                EhDB.putDownloadInfo(nasOnly);
                databaseChanged = true;
            }
            uploadDatabaseBackup(root, remoteDatabase, result.manifest,
                    java.util.Collections.emptySet());
            if (nasOnly != null) {
                if (!gallery.delete()) throw new IOException("Uploaded, but local cleanup failed");
                NasCatalogStore.markRemoteOnly(this, gid);
                new Handler(Looper.getMainLooper()).post(() ->
                        EhApplication.getDownloadManager(this).replaceInfo(nasOnly, info));
                databaseChanged = false;
                return getString(R.string.nas_post_download_moved, result.uploaded);
            }
            return getString(R.string.nas_post_download_copied, result.uploaded);
        } catch (Exception error) {
            if (databaseChanged) EhDB.putDownloadInfo(info);
            throw error;
        } finally {
            if (remoteDatabase != null) remoteDatabase.delete();
        }
    }

    private String downloadGallery(UniFile root, long gid) throws Exception {
        NasCatalogEntry entry = NasCatalogStore.find(this, gid);
        if (entry == null) throw new IOException("NAS gallery is not in the catalog");
        int count = new NasCatalogClient(NasConfigStore.load(this)).downloadGallery(root, entry,
                this::onProgress, this::isCancelled);
        UniFile directory = root.findFile(entry.directoryName);
        if (directory == null) throw new IOException("Downloaded gallery directory missing");
        UniFile metadata = directory.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
        SpiderInfo spiderInfo = metadata != null ? SpiderInfo.read(metadata) : null;
        if (spiderInfo == null || spiderInfo.gid != gid) {
            throw new IOException("Invalid gallery metadata");
        }
        DownloadInfo info = NasCatalogStore.asNasOnlyDownload(entry);
        info.token = spiderInfo.token;
        info.total = spiderInfo.pages;
        info.finished = spiderInfo.pages;
        info.downloaded = spiderInfo.pages;
        if (info.time <= 0L) info.time = System.currentTimeMillis();
        info.state = DownloadInfo.STATE_FINISH;
        EhDB.putDownloadDirname(gid, entry.directoryName);
        EhDB.putDownloadInfo(info);
        NasCatalogStore.markFullyCached(this, java.util.Collections.singletonList(entry));
        NasCatalogStore.showLocally(this, gid);
        new Handler(Looper.getMainLooper()).post(() -> EhApplication.getDownloadManager(this)
                .mergeNasDownloadedInfo(java.util.Collections.singletonList(info)));
        return getString(R.string.nas_gallery_background_finished, count);
    }

    private String deleteGallery(UniFile root, long gid) throws Exception {
        NasCatalogEntry entry = NasCatalogStore.find(this, gid);
        if (entry == null) throw new IOException("NAS gallery is not in the catalog");
        File remoteDatabase = downloadDatabaseBackup(root, true);
        try {
            // Refuse destructive work from a stale device before touching the gallery directory.
            ensureRemoteDatabaseCurrent(remoteDatabase);
            new NasCatalogClient(NasConfigStore.load(this)).deleteGallery(entry);
            EhDB.removeDownloadInfo(gid);
            EhDB.removeDownloadDirname(gid);
            NasCatalogStore.remove(this, gid);
            uploadDatabaseBackup(root, remoteDatabase,
                    null, java.util.Collections.singleton(gid));
            File cached = NasCatalogClient.getCachedThumbnail(this, gid);
            if (cached.isFile()) cached.delete();
            return getString(R.string.nas_gallery_remote_deleted, entry.title);
        } finally {
            remoteDatabase.delete();
        }
    }

    private String importDatabase(UniFile root) throws Exception {
        File remoteDatabase = downloadDatabaseBackup(root, true);
        try {
            NasDatabaseStore.Snapshot snapshot = NasDatabaseStore.read(remoteDatabase);
            NasCatalogStore.save(this, snapshot.generatedAt, snapshot.entries);
            int restored = restoreDatabaseBackup(remoteDatabase);
            return getString(R.string.nas_database_sync_finished, snapshot.entries.size()) +
                    " · " + getString(restored == DATABASE_RESTORED
                    ? R.string.nas_database_restored : R.string.nas_database_unchanged);
        } finally {
            remoteDatabase.delete();
        }
    }

    private static final class DatabaseUpload {
        @NonNull final NasDatabaseStore.Snapshot snapshot;
        final boolean changed;

        DatabaseUpload(@NonNull NasDatabaseStore.Snapshot snapshot, boolean changed) {
            this.snapshot = snapshot;
            this.changed = changed;
        }
    }

    @NonNull
    private DatabaseUpload uploadDatabaseBackup(UniFile root,
                                                @Nullable File remoteDatabase)
            throws Exception {
        return uploadDatabaseBackup(root, remoteDatabase, null,
                java.util.Collections.emptySet());
    }

    @NonNull
    private DatabaseUpload uploadDatabaseBackup(UniFile root,
                                                @Nullable File remoteDatabase,
                                                @Nullable List<NasDatabaseStore.ManifestEntry>
                                                        manifestOverride,
                                                @NonNull java.util.Set<Long> excludedGids)
            throws Exception {
        File temporary = new File(getCacheDir(), NasSyncEngine.DATABASE_BACKUP_FILENAME);
        try {
            String expectedRemoteHash = ensureRemoteDatabaseCurrent(remoteDatabase);
            boolean exported = EhDB.exportDB(this, temporary,
                    (current, total) -> {
                        if (isCancelled()) throw new CancellationException("NAS sync cancelled");
                        onProgress(current, total, getString(R.string.backup_copying));
                    });
            if (!exported) throw new IOException("Unable to export metadata database");
            NasDatabaseStore.mergeRemoteMetadata(remoteDatabase, temporary);
            NasDatabaseStore.removeDownloadEntries(temporary, excludedGids);
            List<String> order = galleryOrder();
            List<NasDatabaseStore.ManifestEntry> manifest;
            if (manifestOverride != null) {
                manifest = manifestOverride;
            } else if (remoteDatabase != null) {
                manifest = NasDatabaseStore.read(remoteDatabase).files;
            } else {
                manifest = java.util.Collections.emptyList();
            }
            // Keep only galleries still present in the canonical catalog. This also removes all
            // manifest rows for an explicitly deleted remote gallery.
            java.util.Set<String> allowed = new java.util.HashSet<>();
            for (String directory : order) allowed.add(directory.toLowerCase(Locale.ROOT));
            List<NasDatabaseStore.ManifestEntry> filtered = new ArrayList<>();
            for (NasDatabaseStore.ManifestEntry entry : manifest) {
                String path = entry.path;
                int slash = path.indexOf('/');
                String directory = slash >= 0 ? path.substring(0, slash) : path;
                if (allowed.contains(directory.toLowerCase(Locale.ROOT))) filtered.add(entry);
            }
            NasDatabaseStore.writeManifest(temporary, filtered, order);
            NasDatabaseStore.Snapshot snapshot = NasDatabaseStore.read(temporary);
            if (remoteDatabase != null
                    && NasDatabaseStore.hasSameCanonicalContent(remoteDatabase, temporary)) {
                try {
                    new NasSyncEngine(root, NasConfigStore.load(this))
                            .verifyAuxiliaryRevision(NasSyncEngine.DATABASE_BACKUP_FILENAME,
                                    expectedRemoteHash);
                } catch (NasSyncEngine.RemoteRevisionChangedException error) {
                    throw remoteDatabaseChanged();
                }
                Settings.putString(KEY_LAST_IMPORTED_DATABASE_HASH, expectedRemoteHash);
                return new DatabaseUpload(NasDatabaseStore.read(remoteDatabase), false);
            }
            String uploadedHash = sha256(temporary);
            try {
                new NasSyncEngine(root, NasConfigStore.load(this)).uploadAuxiliaryFileIfRevision(
                        UniFile.fromFile(temporary), NasSyncEngine.DATABASE_BACKUP_FILENAME,
                        expectedRemoteHash);
            } catch (NasSyncEngine.RemoteRevisionChangedException error) {
                throw remoteDatabaseChanged();
            }
            Settings.putString(KEY_LAST_IMPORTED_DATABASE_HASH, uploadedHash);
            return new DatabaseUpload(snapshot, true);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private int restoreDatabaseBackup(@NonNull File database) throws Exception {
        if (!database.isFile() || database.length() <= 0L) {
            throw new IOException(getString(R.string.nas_database_required));
        }
        String databaseHash = sha256(database);
        if (databaseHash.equals(Settings.getString(KEY_LAST_IMPORTED_DATABASE_HASH, ""))) {
            return DATABASE_UNCHANGED;
        }
        Handler progressHandler = new Handler(Looper.getMainLooper(), message -> {
            Bundle data = message.getData();
            int progress = data.getInt(
                    com.hippo.ehviewer.ui.fragment.AdvancedFragment.LOADING_PROGRESS, -1);
            if (progress >= 0) {
                onProgress(progress, 100, getString(R.string.nas_database_restoring));
            }
            return true;
        });
        String error = EhDB.importDB(this, database, progressHandler);
        if (error != null) throw new IOException(error);
        Settings.putString(KEY_LAST_IMPORTED_DATABASE_HASH, databaseHash);
        return DATABASE_RESTORED;
    }

    /** Persists the first mobile-discovered full manifest using the revision just downloaded. */
    private void uploadDiscoveredManifest(
            @NonNull UniFile root, @NonNull File remoteDatabase,
            @NonNull List<NasDatabaseStore.ManifestEntry> manifest,
            @NonNull List<String> order) throws Exception {
        String expectedRemoteHash = sha256(remoteDatabase);
        NasDatabaseStore.writeManifest(remoteDatabase, manifest, order);
        try {
            new NasSyncEngine(root, NasConfigStore.load(this)).uploadAuxiliaryFileIfRevision(
                    UniFile.fromFile(remoteDatabase), NasSyncEngine.DATABASE_BACKUP_FILENAME,
                    expectedRemoteHash);
        } catch (NasSyncEngine.RemoteRevisionChangedException error) {
            throw remoteDatabaseChanged();
        }
        // Do not update KEY_LAST_IMPORTED_DATABASE_HASH here. restoreDatabaseBackup() must still
        // import this newly augmented database before recording its hash.
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder encoded = new StringBuilder(64);
        for (byte value : digest.digest()) {
            encoded.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return encoded.toString();
    }

    /** Returns the NAS revision this device is allowed to replace, or an empty hash if absent. */
    @NonNull
    private String ensureRemoteDatabaseCurrent(@Nullable File remoteDatabase) throws Exception {
        String remoteHash = remoteDatabase != null ? sha256(remoteDatabase) : "";
        String baseHash = Settings.getString(KEY_LAST_IMPORTED_DATABASE_HASH, "");
        if (NasDatabaseStore.isRemoteRevisionConflict(baseHash, remoteHash)) {
            throw remoteDatabaseChanged();
        }
        return remoteHash;
    }

    @NonNull
    private IOException remoteDatabaseChanged() {
        return new IOException(getString(R.string.nas_database_remote_changed));
    }

    private File downloadDatabaseBackup(UniFile root, boolean required) throws Exception {
        File destination = new File(getCacheDir(), "nas-remote-" +
                Long.toHexString(System.nanoTime()) + ".db");
        boolean downloaded = new NasSyncEngine(root, NasConfigStore.load(this))
                .downloadAuxiliaryFile(NasSyncEngine.DATABASE_BACKUP_FILENAME, destination);
        if (!downloaded) {
            destination.delete();
            if (required) throw new IOException(getString(R.string.nas_database_required));
            return null;
        }
        return destination;
    }

    private NasSyncEngine.ValidationResult inspectRemoteBackup(
            UniFile root, NasDatabaseStore.Snapshot snapshot) throws Exception {
        return new NasSyncEngine(root,
                NasConfigStore.load(this)).validateRemoteBackup(galleryOrder(), snapshot.files,
                this::onProgress, this::isCancelled);
    }

    private NasSyncEngine.ValidationResult requireRestorableBackup(
            UniFile root, NasDatabaseStore.Snapshot snapshot) throws Exception {
        NasSyncEngine.ValidationResult validation = inspectRemoteBackup(root, snapshot);
        if (!validation.isRestorable()) {
            String detail = validationFailure(validation);
            if (!validation.problems.isEmpty()) detail += " · " + validation.problems.get(0);
            throw new IOException(detail);
        }
        return validation;
    }

    private String validationFailure(NasSyncEngine.ValidationResult validation) {
        String detail = getString(R.string.nas_restore_validation_failed,
                validation.missing, validation.sizeMismatch, validation.missingMetadata,
                validation.missingThumbnails);
        if (!validation.databaseBackup) {
            detail += " · " + NasSyncEngine.DATABASE_BACKUP_FILENAME;
        }
        return detail;
    }

    private List<String> galleryOrder() {
        List<String> order = new ArrayList<>();
        java.util.Set<String> known = new java.util.HashSet<>();
        for (NasCatalogEntry entry : NasCatalogStore.load(this)) {
            order.add(entry.directoryName);
            known.add(entry.directoryName.toLowerCase(Locale.ROOT));
        }
        List<DownloadInfo> local = new ArrayList<>(EhApplication.getDownloadManager(this)
                .getAllDownloadInfoList());
        java.util.Collections.reverse(local); // DB/UI list is newest-first; NAS creation is oldest-first.
        for (DownloadInfo info : local) {
            String directory = EhDB.getDownloadDirname(info.gid);
            if (directory != null && known.add(directory.toLowerCase(Locale.ROOT))) {
                order.add(directory);
            }
        }
        return order;
    }

    private void materializeDownloadedCatalog(UniFile root) throws IOException {
        List<NasCatalogEntry> entries = NasCatalogStore.load(this);
        List<DownloadInfo> restored = new ArrayList<>(entries.size());
        List<NasCatalogEntry> restoredEntries = new ArrayList<>(entries.size());
        java.util.Map<Long, Integer> existingStates = new java.util.HashMap<>();
        for (DownloadInfo existing : new ArrayList<>(EhApplication.getDownloadManager(this)
                .getAllDownloadInfoList())) {
            existingStates.put(existing.gid, existing.state);
        }
        long fallbackBase = Math.max(1L, System.currentTimeMillis() - entries.size());
        for (int i = 0; i < entries.size(); i++) {
            NasCatalogEntry entry = entries.get(i);
            Integer existingState = existingStates.get(entry.gid);
            if (existingState != null && existingState != DownloadInfo.STATE_NAS_ONLY) continue;
            UniFile directory = root.findFile(entry.directoryName);
            if (directory == null || !directory.isDirectory()) continue;
            UniFile metadata = directory.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
            SpiderInfo spiderInfo = metadata != null ? SpiderInfo.read(metadata) : null;
            if (spiderInfo == null || spiderInfo.gid != entry.gid) continue;
            DownloadInfo info = NasCatalogStore.asNasOnlyDownload(entry);
            info.token = spiderInfo.token;
            info.total = spiderInfo.pages;
            info.finished = spiderInfo.pages;
            info.downloaded = spiderInfo.pages;
            info.state = DownloadInfo.STATE_FINISH;
            if (info.time <= 0L) info.time = fallbackBase + i;
            EhDB.putDownloadDirname(info.gid, entry.directoryName);
            EhDB.putDownloadInfo(info);
            restored.add(info);
            restoredEntries.add(entry);
        }
        if (!restored.isEmpty()) {
            NasCatalogStore.markFullyCached(this, restoredEntries);
            new Handler(Looper.getMainLooper()).post(() -> EhApplication
                    .getDownloadManager(this).mergeNasDownloadedInfo(restored));
        }
    }

    private void onProgress(int current, int total, String path) {
        if (current == lastCurrent && current != 0) return;
        long now = SystemClock.elapsedRealtime();
        if (total > 0 && current < total && now - lastNotifyAt < 250L) return;
        lastCurrent = current;
        lastNotifyAt = now;
        notificationManager.notify(NOTIFICATION_ID,
                buildNotification(path, current, total, false));
    }

    private android.app.Notification buildNotification(String text, int current, int total,
                                                        boolean finished) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancelIntent = new Intent(this, NasSyncService.class).setAction(ACTION_CANCEL);
        PendingIntent cancel = PendingIntent.getService(this, 1, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(finished ? R.string.nas_sync_notification_finished
                        : R.string.nas_sync_notification_running))
                .setContentText(text).setContentIntent(content).setOnlyAlertOnce(true)
                .setOngoing(!finished).setAutoCancel(finished);
        if (!finished) {
            if (total > 0) {
                long elapsed = Math.max(1L, SystemClock.elapsedRealtime() - startedAt);
                long remaining = current > 0 ? elapsed * Math.max(0, total - current) / current : -1;
                String info = current + "/" + total;
                if (remaining >= 0) info += " · " + formatEta(remaining);
                builder.setSubText(info).setProgress(total, Math.min(current, total), false);
            } else {
                builder.setProgress(0, 0, true);
            }
            builder.addAction(0, getString(android.R.string.cancel), cancel);
        }
        return builder.build();
    }

    private void notifyFinished(String text, boolean failed) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, 0, 0, true));
    }

    private static String formatEta(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "ETA %02d:%02d", seconds / 60, seconds % 60);
    }

    private boolean isCancelled() {
        return activeCancelGeneration != cancelGeneration.get()
                || (activeGid >= 0L && cancelledGalleries.contains(activeGid))
                || Thread.currentThread().isInterrupted();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        cancelGeneration.incrementAndGet();
        cancelledGalleries.clear();
        executor.shutdownNow();
        super.onDestroy();
    }
}
