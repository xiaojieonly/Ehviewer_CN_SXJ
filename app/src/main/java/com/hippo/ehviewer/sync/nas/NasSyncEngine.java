package com.hippo.ehviewer.sync.nas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.utils.SmbFiles;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class NasSyncEngine {
    private static final long SAME_TIME_TOLERANCE_MS = 2_000L;
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int MAX_DIRECTORY_DEPTH = 64;
    private static final long SMB_TIMEOUT_SECONDS = 30L;
    private static final String DOWNLOAD_DIRECTORY = "download";
    public static final String DATABASE_BACKUP_FILENAME = ".ehviewer-nas-data.db";
    private static final int MAX_VALIDATION_PROBLEMS = 12;

    public interface ProgressListener {
        void onProgress(int current, int total, @NonNull String path);
    }

    public interface CancellationToken {
        boolean isCancelled();
    }

    public enum Direction {
        UPLOAD_TO_NAS,
        UPLOAD_DATABASE_TO_NAS,
        DOWNLOAD_TO_PHONE
    }

    public static final class ValidationResult {
        public int galleries;
        public int files;
        public int matched;
        public int missing;
        public int sizeMismatch;
        public int missingMetadata;
        public int missingThumbnails;
        public boolean databaseBackup;
        @NonNull public final List<String> problems = new ArrayList<>();

        public boolean isRestorable() {
            return missing == 0 && sizeMismatch == 0 && missingMetadata == 0
                    && missingThumbnails == 0 && databaseBackup;
        }

        private void addProblem(@NonNull String problem) {
            if (problems.size() < MAX_VALIDATION_PROBLEMS) problems.add(problem);
        }
    }

    @NonNull private final UniFile localRoot;
    @NonNull private final NasSyncConfig config;

    public NasSyncEngine(@NonNull UniFile localRoot, @NonNull NasSyncConfig config) {
        this.localRoot = localRoot;
        this.config = config;
    }

    @NonNull
    public NasSyncResult sync(@NonNull List<String> galleryDirectoryOrder,
                              @NonNull List<NasDatabaseStore.ManifestEntry> remoteManifest,
                              ProgressListener listener,
                              @NonNull CancellationToken cancellationToken) throws Exception {
        if (!localRoot.isDirectory() || !localRoot.canRead() || !localRoot.canWrite()) {
            throw new IOException("Download directory is not readable and writable");
        }
        NasSyncResult result = new NasSyncResult();
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                ensureRemoteDirectory(share, remoteDownloadRoot());
                Map<String, LocalEntry> localFiles = new HashMap<>();
                Map<String, RemoteEntry> remoteFiles = remoteFilesFromManifest(remoteManifest);
                int[] scanCount = {0};
                collectLocal(localRoot, "", localFiles, 0, scanCount, listener,
                        cancellationToken);
                if (remoteFiles.isEmpty()) {
                    for (String directory : galleryDirectoryOrder) {
                        checkCancelled(cancellationToken);
                        if (!isSafeDirectoryName(directory)) continue;
                        String remoteDirectory = joinRemote(remoteDownloadRoot(), directory);
                        if (!share.folderExists(remoteDirectory)) continue;
                        collectRemote(share, remoteDirectory, directory, remoteFiles, 0,
                                new HashSet<>(), scanCount, listener, cancellationToken);
                    }
                }
                Set<String> all = new HashSet<>(localFiles.keySet());
                all.addAll(remoteFiles.keySet());
                List<String> paths = new ArrayList<>(all);
                Collections.sort(paths);
                int current = 0;
                for (String path : paths) {
                    checkCancelled(cancellationToken);
                    if (listener != null) listener.onProgress(current, paths.size(), path);
                    LocalEntry local = localFiles.get(path);
                    RemoteEntry remote = remoteFiles.get(path);
                    if (local == null) {
                        download(share, remote.path, path);
                        result.downloaded++;
                    } else if (remote == null) {
                        upload(share, local.file, remotePath(path));
                        result.uploaded++;
                    } else if (isSame(share, local, remote)) {
                        result.skipped++;
                    } else if (isLocalNewer(local, remote)) {
                        upload(share, local.file, remote.path);
                        result.uploaded++;
                    } else if (isRemoteNewer(local, remote)) {
                        download(share, remote.path, path);
                        result.downloaded++;
                    } else {
                        String conflict = conflictPath(path);
                        upload(share, local.file, remotePath(conflict));
                        download(share, remote.path, path);
                        result.uploaded++;
                        result.downloaded++;
                        result.conflicts++;
                    }
                    current++;
                    if (listener != null) listener.onProgress(current, paths.size(), path);
                }
            }
        } finally {
            config.clearPassword();
        }
        return result;
    }

    @NonNull
    public NasSyncResult syncAll(@NonNull Direction direction,
                                 @NonNull List<String> galleryDirectoryOrder,
                                 @NonNull List<NasDatabaseStore.ManifestEntry> remoteManifest,
                                 ProgressListener listener,
                                 @NonNull CancellationToken cancellationToken)
            throws Exception {
        if (!localRoot.isDirectory() || !localRoot.canRead() || !localRoot.canWrite()) {
            throw new IOException("Download directory is not readable and writable");
        }
        NasSyncResult result = new NasSyncResult();
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                ensureRemoteDirectory(share, remoteDownloadRoot());
                if (direction == Direction.UPLOAD_TO_NAS) {
                    Map<String, LocalEntry> localFiles = new HashMap<>();
                    collectLocal(localRoot, "", localFiles, 0, new int[]{0}, listener,
                            cancellationToken);
                    Map<String, RemoteEntry> remoteFiles = remoteFilesFromManifest(remoteManifest);
                    boolean bootstrappedManifest = false;
                    if (remoteFiles.isEmpty()) {
                        // First run without a PC-generated manifest: scan NAS once here, then
                        // persist the discovered manifest so later runs avoid this enumeration.
                        int[] scanCount = {0};
                        for (String directory : galleryDirectoryOrder) {
                            checkCancelled(cancellationToken);
                            if (!isSafeDirectoryName(directory)) continue;
                            String remoteDirectory = joinRemote(remoteDownloadRoot(), directory);
                            if (!share.folderExists(remoteDirectory)) continue;
                            collectRemote(share, remoteDirectory, directory, remoteFiles, 0,
                                    new HashSet<>(), scanCount, listener, cancellationToken);
                        }
                        bootstrappedManifest = true;
                    }
                    List<String> paths = new ArrayList<>();
                    for (String path : localFiles.keySet()) {
                        paths.add(path);
                    }
                    Map<String, Integer> order = new HashMap<>();
                    for (int i = 0; i < galleryDirectoryOrder.size(); i++) {
                        order.put(galleryDirectoryOrder.get(i).toLowerCase(Locale.ROOT), i);
                    }
                    Collections.sort(paths, (left, right) -> {
                        String leftDirectory = topDirectory(left).toLowerCase(Locale.ROOT);
                        String rightDirectory = topDirectory(right).toLowerCase(Locale.ROOT);
                        int leftOrder = orderOf(order, leftDirectory);
                        int rightOrder = orderOf(order, rightDirectory);
                        int comparison = Integer.compare(leftOrder, rightOrder);
                        if (comparison != 0) return comparison;
                        return left.compareToIgnoreCase(right);
                    });
                    int total = paths.size();
                    int current = 0;
                    for (String path : paths) {
                        checkCancelled(cancellationToken);
                        if (listener != null) listener.onProgress(current, total, path);
                        LocalEntry local = localFiles.get(path);
                        RemoteEntry declaredRemote = remoteFiles.get(path);
                        String destination = declaredRemote != null
                                ? declaredRemote.path : remotePath(path);
                        // A full DB manifest is authoritative for immutable gallery pages. This
                        // avoids one SMB exists/stat round trip for every unchanged image.
                        boolean manifestMatch = declaredRemote != null
                                && canSkipFromManifest(path, local.size, declaredRemote.size);
                        RemoteEntry currentRemote = null;
                        if (!manifestMatch && share.fileExists(destination)) {
                            currentRemote = remoteEntry(share, destination);
                        }
                        if (manifestMatch || (currentRemote != null
                                && canSkipTransfer(share, path, local, currentRemote))) {
                            result.skipped++;
                        } else {
                            upload(share, local.file, destination);
                            result.uploaded++;
                        }
                        current++;
                        if (listener != null) listener.onProgress(current, total, path);
                    }
                    if (!remoteManifest.isEmpty() || bootstrappedManifest) {
                        List<NasDatabaseStore.ManifestEntry> base = !remoteManifest.isEmpty()
                                ? remoteManifest
                                : manifestFromRemote(remoteFiles, galleryDirectoryOrder);
                        result.manifest.addAll(mergeManifest(base, localFiles,
                                null, galleryDirectoryOrder));
                    }
                } else if (direction == Direction.DOWNLOAD_TO_PHONE) {
                    Map<String, RemoteEntry> remoteFiles = remoteFilesFromManifest(remoteManifest);
                    Map<String, LocalEntry> localFiles = new HashMap<>();
                    collectLocal(localRoot, "", localFiles, 0, new int[]{0}, listener,
                            cancellationToken);
                    if (!remoteFiles.isEmpty()) {
                        downloadRemoteFiles(share, remoteFiles, localFiles, result, listener,
                                cancellationToken);
                    } else {
                        // A file manifest is optional. Enumerate and transfer one registered
                        // gallery at a time so metadata import is instant and full restore starts
                        // making progress without a blocking whole-NAS scan.
                        Map<String, RemoteEntry> discovered = new LinkedHashMap<>();
                        for (String directory : galleryDirectoryOrder) {
                            checkCancelled(cancellationToken);
                            if (!isSafeDirectoryName(directory)) {
                                throw new IOException("Unsafe gallery directory in database: " +
                                        directory);
                            }
                            String remoteDirectory = joinRemote(remoteDownloadRoot(), directory);
                            if (!share.folderExists(remoteDirectory)) continue;
                            Map<String, RemoteEntry> galleryFiles = new LinkedHashMap<>();
                            collectRemote(share, remoteDirectory, directory, galleryFiles, 0,
                                    new HashSet<>(), new int[]{0}, listener, cancellationToken);
                            discovered.putAll(galleryFiles);
                            downloadRemoteFiles(share, galleryFiles, localFiles, result, listener,
                                    cancellationToken);
                        }
                        result.manifest.addAll(manifestFromRemote(discovered,
                                galleryDirectoryOrder));
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported sync direction: " + direction);
                }
            }
        } finally {
            config.clearPassword();
        }
        return result;
    }

    private void downloadRemoteFiles(@NonNull DiskShare share,
                                     @NonNull Map<String, RemoteEntry> remoteFiles,
                                     @NonNull Map<String, LocalEntry> localFiles,
                                     @NonNull NasSyncResult result,
                                     ProgressListener listener,
                                     @NonNull CancellationToken cancellationToken)
            throws Exception {
        List<String> paths = new ArrayList<>(remoteFiles.keySet());
        int current = 0;
        for (String path : paths) {
            checkCancelled(cancellationToken);
            if (listener != null) listener.onProgress(current, paths.size(), path);
            RemoteEntry remote = remoteFiles.get(path);
            LocalEntry local = localFiles.get(path);
            boolean manifestMatch = local != null
                    && canSkipFromManifest(path, local.size, remote.size);
            RemoteEntry currentRemote = null;
            if (local != null && !manifestMatch) currentRemote = remoteEntry(share, remote.path);
            if (manifestMatch || (local != null
                    && canSkipTransfer(share, path, local, currentRemote))) {
                result.skipped++;
            } else {
                download(share, remote.path, path);
                result.downloaded++;
            }
            current++;
            if (listener != null) listener.onProgress(current, paths.size(), path);
        }
    }

    /** Uploads one completed gallery without enumerating the rest of the download directory. */
    @NonNull
    public NasSyncResult uploadGallery(@NonNull UniFile galleryDirectory,
                                       @NonNull String directoryName,
                                       @NonNull List<NasDatabaseStore.ManifestEntry> remoteManifest,
                                       @NonNull List<String> galleryDirectoryOrder,
                                       ProgressListener listener,
                                       @NonNull CancellationToken cancellationToken)
            throws Exception {
        if (!galleryDirectory.isDirectory() || !galleryDirectory.canRead()) {
            throw new IOException("Gallery directory is not readable: " + directoryName);
        }
        NasSyncResult result = new NasSyncResult();
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String remoteRoot = joinRemote(remoteDownloadRoot(), directoryName);
                ensureRemoteDirectory(share, remoteRoot);
                Map<String, LocalEntry> files = new HashMap<>();
                collectLocal(galleryDirectory, "", files, 0, new int[]{0}, listener,
                        cancellationToken);
                List<String> paths = new ArrayList<>(files.keySet());
                Collections.sort(paths);
                Map<String, RemoteEntry> declaredFiles = remoteFilesFromManifest(remoteManifest);
                int current = 0;
                for (String path : paths) {
                    checkCancelled(cancellationToken);
                    if (listener != null) listener.onProgress(current, paths.size(), path);
                    LocalEntry local = files.get(path);
                    String fullPath = directoryName + "/" + path;
                    String destination = joinRemote(remoteRoot, path.replace('/', '\\'));
                    RemoteEntry declared = declaredFiles.get(fullPath);
                    boolean manifestMatch = declared != null
                            && canSkipFromManifest(path, local.size, declared.size);
                    RemoteEntry currentRemote = null;
                    if (!manifestMatch && share.fileExists(destination)) {
                        currentRemote = remoteEntry(share, destination);
                    }
                    if (manifestMatch || (currentRemote != null
                            && canSkipTransfer(share, path, local, currentRemote))) {
                        result.skipped++;
                    } else {
                        upload(share, local.file, destination);
                        result.uploaded++;
                    }
                    current++;
                    if (listener != null) listener.onProgress(current, paths.size(), path);
                }
                if (!remoteManifest.isEmpty()) {
                    result.manifest.addAll(mergeManifest(remoteManifest, files,
                            directoryName, galleryDirectoryOrder));
                }
            }
        } finally {
            config.clearPassword();
        }
        return result;
    }

    public void uploadAuxiliaryFile(@NonNull UniFile source, @NonNull String filename)
            throws Exception {
        auxiliaryFileTransaction(source, filename, null, true);
    }

    /** Uploads only if the remote file is still the revision previously downloaded. */
    public void uploadAuxiliaryFileIfRevision(@NonNull UniFile source,
                                              @NonNull String filename,
                                              @NonNull String expectedRemoteHash)
            throws Exception {
        auxiliaryFileTransaction(source, filename, expectedRemoteHash, true);
    }

    /** Checks a revision under the same SMB lock used by guarded uploads. */
    public void verifyAuxiliaryRevision(@NonNull String filename,
                                        @NonNull String expectedRemoteHash)
            throws Exception {
        auxiliaryFileTransaction(null, filename, expectedRemoteHash, false);
    }

    public static final class RemoteRevisionChangedException extends IOException {
        RemoteRevisionChangedException() {
            super("Remote auxiliary file revision changed");
        }
    }

    private void auxiliaryFileTransaction(@Nullable UniFile source,
                                          @NonNull String filename,
                                          @Nullable String expectedRemoteHash,
                                          boolean uploadFile) throws Exception {
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                ensureRemoteDirectory(share, config.remoteDirectory);
                String destination = joinRemote(config.remoteDirectory, filename);
                String lockPath = destination + ".sync-lock";
                com.hierynomus.smbj.share.File lock = null;
                boolean ownsLock = false;
                try {
                    if (expectedRemoteHash != null) {
                        // FILE_CREATE is atomic on SMB. DELETE_ON_CLOSE prevents a crashed client
                        // from leaving a permanent lock once its SMB session is gone.
                        lock = share.openFile(lockPath,
                                EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_HIDDEN),
                                EnumSet.noneOf(SMB2ShareAccess.class),
                                SMB2CreateDisposition.FILE_CREATE,
                                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE,
                                        SMB2CreateOptions.FILE_DELETE_ON_CLOSE));
                        ownsLock = true;
                        String currentHash = share.fileExists(destination)
                                ? hex(hash(openRemoteInput(share, destination))) : "";
                        if (!expectedRemoteHash.equalsIgnoreCase(currentHash)) {
                            throw new RemoteRevisionChangedException();
                        }
                    }
                    if (!uploadFile) return;
                    if (source == null) throw new IOException("Missing auxiliary upload source");
                    if (share.fileExists(destination)) {
                        RemoteEntry remote = remoteEntry(share, destination);
                        LocalEntry local = new LocalEntry(source, source.length(),
                                source.lastModified());
                        if (remote != null && local.size == remote.size
                                && hasSameContent(share, local, remote)) return;
                    }
                    upload(share, source, destination);
                    RemoteEntry uploaded = remoteEntry(share, destination);
                    LocalEntry local = new LocalEntry(source, source.length(),
                            source.lastModified());
                    if (uploaded == null || uploaded.size != local.size
                            || !hasSameContent(share, local, uploaded)) {
                        throw new IOException("NAS auxiliary file verification failed: " +
                                filename);
                    }
                } finally {
                    if (lock != null) lock.close();
                    // Some SMB servers ignore DELETE_ON_CLOSE; remove the owned lock as fallback.
                    try {
                        if (ownsLock && share.fileExists(lockPath)) {
                            share.rm(lockPath);
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        } finally {
            config.clearPassword();
        }
    }

    public boolean downloadAuxiliaryFile(@NonNull String filename,
                                         @NonNull File destination) throws Exception {
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String remote = joinRemote(config.remoteDirectory, filename);
                if (!share.fileExists(remote)) return false;
                try (InputStream input = openRemoteInput(share, remote);
                     OutputStream output = new FileOutputStream(destination)) {
                    copy(input, output);
                }
                return destination.isFile() && destination.length() > 0L;
            }
        } finally {
            config.clearPassword();
        }
    }

    /**
     * Verifies the restore contract after an upload. Large immutable images are checked by path
     * and size against the database manifest; mutable metadata was content-checked while
     * uploading. A successful result means a full download has every file needed to rebuild the
     * download list, offline thumbnails and per-gallery spider metadata.
     */
    @NonNull
    public ValidationResult validateRemoteBackup(@NonNull List<String> galleryDirectories,
                                                 @NonNull List<NasDatabaseStore.ManifestEntry> manifest,
                                                 ProgressListener listener,
                                                 @NonNull CancellationToken cancellationToken)
            throws Exception {
        ValidationResult result = new ValidationResult();
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(SMB_TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                Map<String, RemoteEntry> remoteFiles = remoteFilesFromManifest(manifest);
                Map<String, RemoteEntry> remoteByLower = new HashMap<>();
                for (Map.Entry<String, RemoteEntry> item : remoteFiles.entrySet()) {
                    remoteByLower.put(item.getKey().toLowerCase(Locale.ROOT), item.getValue());
                }

                if (!manifest.isEmpty()) {
                    Map<String, LocalEntry> localFiles = new HashMap<>();
                    collectLocal(localRoot, "", localFiles, 0, new int[]{0}, listener,
                            cancellationToken);
                    result.files = localFiles.size();
                    int current = 0;
                    for (Map.Entry<String, LocalEntry> item : localFiles.entrySet()) {
                        checkCancelled(cancellationToken);
                        String path = item.getKey();
                        if (listener != null) {
                            listener.onProgress(current, localFiles.size(), "Verify: " + path);
                        }
                        RemoteEntry remote = remoteByLower.get(path.toLowerCase(Locale.ROOT));
                        if (remote == null) {
                            result.missing++;
                            result.addProblem("Missing: " + path);
                        } else if (remote.size != item.getValue().size) {
                            result.sizeMismatch++;
                            result.addProblem("Size mismatch: " + path);
                        } else {
                            result.matched++;
                        }
                        current++;
                    }
                }

                Set<String> uniqueDirectories = new HashSet<>();
                for (String directory : galleryDirectories) {
                    if (directory == null || directory.isEmpty()
                            || !uniqueDirectories.add(directory.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    result.galleries++;
                    if (!manifest.isEmpty()) {
                        String prefix = directory.toLowerCase(Locale.ROOT) + "/";
                        RemoteEntry metadata = remoteByLower.get(prefix + ".ehviewer");
                        if (metadata == null || metadata.size <= 0L) {
                            result.missingMetadata++;
                            result.addProblem("Missing metadata: " + directory);
                        }
                        RemoteEntry thumbnail = remoteByLower.get(prefix + ".thumb");
                        if (thumbnail == null || thumbnail.size <= 0L) {
                            result.missingThumbnails++;
                            result.addProblem("Missing thumbnail: " + directory);
                        }
                    }
                }
                String databasePath = joinRemote(config.remoteDirectory,
                        DATABASE_BACKUP_FILENAME);
                result.databaseBackup = share.fileExists(databasePath)
                        && remoteEntry(share, databasePath).size > 0L;
                if (!result.databaseBackup) {
                    result.addProblem("Missing: " + DATABASE_BACKUP_FILENAME);
                }
            }
        } finally {
            config.clearPassword();
        }
        return result;
    }

    private static String topDirectory(String path) {
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }

    private static boolean isSafeDirectoryName(String directory) {
        return directory != null && directory.indexOf('/') < 0 && directory.indexOf('\\') < 0
                && NasDatabaseStore.isSafeRelativePath(directory);
    }

    private static int orderOf(@NonNull Map<String, Integer> order, @NonNull String directory) {
        Integer value = order.get(directory);
        return value != null ? value : Integer.MAX_VALUE;
    }

    static boolean isMutableMetadataPath(@NonNull String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return ".ehviewer".equalsIgnoreCase(name) || ".thumb".equalsIgnoreCase(name)
                || DATABASE_BACKUP_FILENAME.equalsIgnoreCase(name);
    }

    static boolean canSkipFromManifest(@NonNull String path, long localSize,
                                       long manifestSize) {
        return localSize == manifestSize && !isMutableMetadataPath(path);
    }

    private boolean canSkipTransfer(@NonNull DiskShare share, @NonNull String path,
                                    @NonNull LocalEntry local,
                                    @NonNull RemoteEntry remote) throws Exception {
        if (local.size != remote.size) return false;
        // Gallery images are immutable in normal EhViewer use. Matching path and size lets a
        // reinstall avoid hashing or retransmitting thousands of large files. Small mutable
        // metadata is content-checked so labels, progress and thumbnails are never skipped merely
        // because their byte count happens to match.
        return !isMutableMetadataPath(path) || hasSameContent(share, local, remote);
    }

    private static RemoteEntry remoteEntry(@NonNull DiskShare share, @NonNull String path) {
        com.hierynomus.msdtyp.FileTime modified = share.getFileInformation(path)
                .getBasicInformation().getLastWriteTime();
        long size = share.getFileInformation(path).getStandardInformation().getEndOfFile();
        return new RemoteEntry(path, size, modified.toEpochMillis());
    }

    private static boolean hasSameContent(@NonNull DiskShare share, @NonNull LocalEntry local,
                                          @NonNull RemoteEntry remote) throws Exception {
        return Arrays.equals(hash(local.file.openInputStream()),
                hash(openRemoteInput(share, remote.path)));
    }

    @NonNull
    private Map<String, RemoteEntry> remoteFilesFromManifest(
            @NonNull List<NasDatabaseStore.ManifestEntry> manifest) throws IOException {
        Map<String, RemoteEntry> files = new LinkedHashMap<>();
        for (NasDatabaseStore.ManifestEntry entry : manifest) {
            if (!NasDatabaseStore.isSafeRelativePath(entry.path)) {
                throw new IOException("Unsafe NAS database manifest path: " + entry.path);
            }
            files.put(entry.path, new RemoteEntry(remotePath(entry.path), entry.size,
                    entry.modified));
        }
        return files;
    }

    /** Merges scanned local metadata into an already-complete NAS manifest. */
    static List<NasDatabaseStore.ManifestEntry> mergeManifest(
            @NonNull List<NasDatabaseStore.ManifestEntry> base,
            @NonNull Map<String, LocalEntry> localFiles,
            @Nullable String directoryPrefix,
            @NonNull List<String> galleryDirectoryOrder) {
        Map<String, NasDatabaseStore.ManifestEntry> merged = new LinkedHashMap<>();
        String prefix = directoryPrefix == null ? null : directoryPrefix + "/";
        for (NasDatabaseStore.ManifestEntry entry : base) {
            if (prefix == null || !entry.path.regionMatches(true, 0, prefix, 0,
                    prefix.length())) {
                merged.put(entry.path, entry);
            }
        }
        for (Map.Entry<String, LocalEntry> item : localFiles.entrySet()) {
            String path = prefix == null ? item.getKey() : prefix + item.getKey();
            LocalEntry local = item.getValue();
            merged.put(path, new NasDatabaseStore.ManifestEntry(path, local.size,
                    local.modified, 0));
        }
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < galleryDirectoryOrder.size(); i++) {
            order.put(galleryDirectoryOrder.get(i).toLowerCase(Locale.ROOT), i);
        }
        List<String> paths = new ArrayList<>(merged.keySet());
        Collections.sort(paths, (left, right) -> {
            int comparison = Integer.compare(orderOf(order,
                    topDirectory(left).toLowerCase(Locale.ROOT)), orderOf(order,
                    topDirectory(right).toLowerCase(Locale.ROOT)));
            return comparison != 0 ? comparison : left.compareToIgnoreCase(right);
        });
        List<NasDatabaseStore.ManifestEntry> result = new ArrayList<>(paths.size());
        for (int i = 0; i < paths.size(); i++) {
            NasDatabaseStore.ManifestEntry entry = merged.get(paths.get(i));
            result.add(new NasDatabaseStore.ManifestEntry(entry.path, entry.size,
                    entry.modified, i));
        }
        return result;
    }

    @NonNull
    private static List<NasDatabaseStore.ManifestEntry> manifestFromRemote(
            @NonNull Map<String, RemoteEntry> remoteFiles,
            @NonNull List<String> galleryDirectoryOrder) {
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < galleryDirectoryOrder.size(); i++) {
            order.put(galleryDirectoryOrder.get(i).toLowerCase(Locale.ROOT), i);
        }
        List<String> paths = new ArrayList<>(remoteFiles.keySet());
        Collections.sort(paths, (left, right) -> {
            int comparison = Integer.compare(orderOf(order,
                    topDirectory(left).toLowerCase(Locale.ROOT)), orderOf(order,
                    topDirectory(right).toLowerCase(Locale.ROOT)));
            return comparison != 0 ? comparison : left.compareToIgnoreCase(right);
        });
        List<NasDatabaseStore.ManifestEntry> result = new ArrayList<>(paths.size());
        for (int i = 0; i < paths.size(); i++) {
            RemoteEntry remote = remoteFiles.get(paths.get(i));
            result.add(new NasDatabaseStore.ManifestEntry(paths.get(i), remote.size,
                    remote.modified, i));
        }
        return result;
    }

    private boolean isSame(DiskShare share, LocalEntry local, RemoteEntry remote) throws Exception {
        if (local.size != remote.size) return false;
        if (local.modified > 0 && remote.modified > 0
                && Math.abs(local.modified - remote.modified) <= SAME_TIME_TOLERANCE_MS) {
            return true;
        }
        return Arrays.equals(hash(local.file.openInputStream()), hash(openRemoteInput(share, remote.path)));
    }

    private static boolean isLocalNewer(LocalEntry local, RemoteEntry remote) {
        return local.modified > 0 && remote.modified > 0
                && local.modified - remote.modified > SAME_TIME_TOLERANCE_MS;
    }

    private static boolean isRemoteNewer(LocalEntry local, RemoteEntry remote) {
        return local.modified > 0 && remote.modified > 0
                && remote.modified - local.modified > SAME_TIME_TOLERANCE_MS;
    }

    private void collectLocal(UniFile directory, String relative,
                              Map<String, LocalEntry> files, int depth, int[] scanCount,
                              ProgressListener listener, CancellationToken cancellationToken)
            throws IOException {
        checkCancelled(cancellationToken);
        if (depth > MAX_DIRECTORY_DEPTH) {
            throw new IOException("Local directory nesting is too deep: " + relative);
        }
        UniFile[] children = directory.listFiles();
        if (children == null) throw new IOException("Unable to list local directory: " + relative);
        for (UniFile child : children) {
            checkCancelled(cancellationToken);
            String name = child.getName();
            if (name == null || name.isEmpty()) continue;
            if (relative.isEmpty() && child.isFile()
                    && name.toLowerCase(Locale.ROOT).startsWith(".ehviewer-nas-")) continue;
            if (isTemporaryName(name)) continue;
            String path = relative.isEmpty() ? name : relative + "/" + name;
            scanCount[0]++;
            if (listener != null) listener.onProgress(scanCount[0], -1, "Local: " + path);
            if (child.isDirectory()) {
                collectLocal(child, path, files, depth + 1, scanCount, listener,
                        cancellationToken);
            } else if (child.isFile()) {
                files.put(path, new LocalEntry(child, child.length(), child.lastModified()));
            }
        }
    }

    private void collectRemote(DiskShare share, String remoteDirectory, String relative,
                               Map<String, RemoteEntry> files, int depth,
                               Set<String> visitedDirectories, int[] scanCount,
                               ProgressListener listener, CancellationToken cancellationToken)
            throws IOException {
        checkCancelled(cancellationToken);
        if (depth > MAX_DIRECTORY_DEPTH) {
            throw new IOException("NAS directory nesting is too deep: " + relative);
        }
        String visitKey = remoteDirectory.toLowerCase(Locale.ROOT);
        if (!visitedDirectories.add(visitKey)) return;
        for (FileIdBothDirectoryInformation info : share.list(remoteDirectory)) {
            checkCancelled(cancellationToken);
            String name = info.getFileName();
            if (".".equals(name) || "..".equals(name)) continue;
            if (relative.isEmpty()
                    && name.toLowerCase(Locale.ROOT).startsWith(".ehviewer-nas-")) continue;
            if (isTemporaryName(name)) continue;
            String path = relative.isEmpty() ? name : relative + "/" + name;
            String remotePath = joinRemote(remoteDirectory, name);
            scanCount[0]++;
            if (listener != null) listener.onProgress(scanCount[0], -1, "NAS: " + path);
            boolean isDirectory = (info.getFileAttributes()
                    & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
            if (isDirectory) {
                boolean isReparsePoint = (info.getFileAttributes()
                        & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0;
                if (!isReparsePoint) {
                    collectRemote(share, remotePath, path, files, depth + 1,
                            visitedDirectories, scanCount, listener, cancellationToken);
                }
            } else {
                files.put(path, new RemoteEntry(remotePath, info.getEndOfFile(),
                        info.getLastWriteTime().toEpochMillis()));
            }
        }
    }

    private static boolean isTemporaryName(String name) {
        return name.contains(".phone-tmp-");
    }

    private void upload(DiskShare share, UniFile source, String destination) throws IOException {
        ensureRemoteDirectory(share, parentRemote(destination));
        String temporary = destination + ".phone-tmp-" + Long.toHexString(System.nanoTime());
        try (InputStream input = source.openInputStream();
             com.hierynomus.smbj.share.File target = share.openFile(temporary,
                     EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                     EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL,
                     SMB2CreateDisposition.FILE_CREATE,
                     EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE))) {
            try (OutputStream output = target.getOutputStream()) {
                copy(input, output);
            }
            target.rename(destination, true);
        } catch (RuntimeException | IOException error) {
            try {
                if (share.fileExists(temporary)) share.rm(temporary);
            } catch (RuntimeException ignored) {
            }
            throw error;
        }
    }

    private void download(DiskShare share, String source, String destination) throws IOException {
        UniFile target = getOrCreateLocalFile(destination);
        try (InputStream input = openRemoteInput(share, source);
             OutputStream output = target.openOutputStream()) {
            copy(input, output);
        }
    }

    private static InputStream openRemoteInput(DiskShare share, String path) {
        com.hierynomus.smbj.share.File file = share.openFile(path,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.noneOf(FileAttributes.class), SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
        InputStream input = file.getInputStream();
        return new EntryInputStream(input, file);
    }

    private UniFile getOrCreateLocalFile(String relative) throws IOException {
        String[] parts = relative.split("/");
        UniFile directory = localRoot;
        for (int i = 0; i < parts.length - 1; i++) {
            UniFile next = directory.findFile(parts[i]);
            if (next == null) next = directory.createDirectory(parts[i]);
            if (next == null || !next.isDirectory()) {
                throw new IOException("Unable to create local directory: " + parts[i]);
            }
            directory = next;
        }
        String filename = parts[parts.length - 1];
        UniFile file = directory.findFile(filename);
        if (file == null) file = directory.createFile(filename);
        if (file == null || !file.isFile()) {
            throw new IOException("Unable to create local file: " + relative);
        }
        return file;
    }

    private String remotePath(String relative) {
        return joinRemote(remoteDownloadRoot(), relative.replace('/', '\\'));
    }

    private String remoteDownloadRoot() {
        return joinRemote(config.remoteDirectory, DOWNLOAD_DIRECTORY);
    }

    private static void ensureRemoteDirectory(DiskShare share, String path) {
        if (path == null || path.isEmpty() || share.folderExists(path)) return;
        new SmbFiles().mkdirs(share, path);
    }

    private static String parentRemote(String path) {
        int slash = path.lastIndexOf('\\');
        return slash > 0 ? path.substring(0, slash) : "";
    }

    private static String joinRemote(String parent, String child) {
        if (parent == null || parent.isEmpty()) return child;
        if (child == null || child.isEmpty()) return parent;
        return parent + "\\" + child;
    }

    private static String conflictPath(String path) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash) return path + ".phone-conflict-" + stamp;
        return path.substring(0, dot) + ".phone-conflict-" + stamp + path.substring(dot);
    }

    private static byte[] hash(InputStream input) throws Exception {
        try (InputStream stream = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = stream.read(buffer)) != -1) digest.update(buffer, 0, read);
            return digest.digest();
        }
    }

    @NonNull
    private static String hex(@NonNull byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.US, "%02x", item & 0xff));
        return value.toString();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        output.flush();
    }

    private static void checkCancelled(CancellationToken cancellationToken)
            throws InterruptedIOException {
        if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("NAS sync cancelled");
        }
    }

    private static final class LocalEntry {
        final UniFile file;
        final long size;
        final long modified;

        LocalEntry(UniFile file, long size, long modified) {
            this.file = file;
            this.size = size;
            this.modified = modified;
        }
    }

    private static final class RemoteEntry {
        final String path;
        final long size;
        final long modified;

        RemoteEntry(String path, long size, long modified) {
            this.path = path;
            this.size = size;
            this.modified = modified;
        }
    }

    private static final class EntryInputStream extends InputStream {
        private final InputStream input;
        private final com.hierynomus.smbj.share.File file;
        private boolean closed;

        EntryInputStream(InputStream input, com.hierynomus.smbj.share.File file) {
            this.input = input;
            this.file = file;
        }

        @Override public int read() throws IOException { return input.read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            return input.read(b, off, len);
        }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                input.close();
            } finally {
                file.closeSilently();
            }
        }
    }
}
