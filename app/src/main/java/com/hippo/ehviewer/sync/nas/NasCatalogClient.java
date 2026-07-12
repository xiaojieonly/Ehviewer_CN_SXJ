package com.hippo.ehviewer.sync.nas;

import android.content.Context;

import androidx.annotation.NonNull;

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
import com.hippo.unifile.UniFile;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.spider.SpiderDen;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class NasCatalogClient {
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int MAX_DEPTH = 16;
    private static final long TIMEOUT_SECONDS = 30L;
    private static final long MAX_THUMBNAIL_BYTES = 16L * 1024L * 1024L;

    public interface ProgressListener {
        void onProgress(int current, int total, @NonNull String path);
    }

    public interface CancellationToken {
        boolean isCancelled();
    }

    public interface ThumbnailListener {
        void onThumbnail(@NonNull NasCatalogEntry entry, File file);
    }

    @NonNull private final NasSyncConfig config;

    public NasCatalogClient(@NonNull NasSyncConfig config) {
        this.config = config;
    }

    public int downloadGallery(@NonNull UniFile localRoot, @NonNull NasCatalogEntry entry,
                               ProgressListener listener,
                               @NonNull CancellationToken cancellationToken) throws Exception {
        if (!localRoot.isDirectory() || !localRoot.canRead() || !localRoot.canWrite()) {
            throw new IOException("Download directory is not readable and writable");
        }
        SmbConfig smbConfig = createSmbConfig();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            try (Session session = connection.authenticate(authentication());
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String sourceRoot = joinRemote(config.remoteDirectory,
                        entry.remoteDirectory.replace('/', '\\'));
                if (!share.folderExists(sourceRoot)) {
                    throw new IOException("NAS gallery directory not found: " + entry.directoryName);
                }
                List<RemoteFile> files = new ArrayList<>();
                collectRemoteFiles(share, sourceRoot, "", files, 0, listener,
                        cancellationToken);
                UniFile targetRoot = localRoot.findFile(entry.directoryName);
                if (targetRoot == null) targetRoot = localRoot.createDirectory(entry.directoryName);
                if (targetRoot == null || !targetRoot.isDirectory()) {
                    throw new IOException("Unable to create local gallery directory");
                }
                int current = 0;
                for (RemoteFile remoteFile : files) {
                    checkCancelled(cancellationToken);
                    if (listener != null) {
                        listener.onProgress(current, files.size(), remoteFile.relativePath);
                    }
                    downloadFile(share, remoteFile.remotePath, targetRoot,
                            remoteFile.relativePath);
                    current++;
                }
                if (listener != null) listener.onProgress(current, files.size(), entry.title);
                return files.size();
            }
        } finally {
            config.clearPassword();
        }
    }

    /** Permanently deletes exactly one catalog gallery directory from NAS. */
    public void deleteGallery(@NonNull NasCatalogEntry entry) throws Exception {
        String relative = entry.remoteDirectory.replace('\\', '/');
        if (!NasDatabaseStore.isSafeRelativePath(relative)
                || !relative.toLowerCase(Locale.ROOT).startsWith("download/")
                || relative.substring("download/".length()).contains("/")) {
            throw new IOException("Unsafe NAS gallery directory: " + relative);
        }
        SmbConfig smbConfig = createSmbConfig();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            try (Session session = connection.authenticate(authentication());
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String target = joinRemote(config.remoteDirectory,
                        relative.replace('/', '\\'));
                if (share.folderExists(target)) share.rmdir(target, true);
            }
        } finally {
            config.clearPassword();
        }
    }

    @NonNull
    public UniFile prepareMetadata(@NonNull UniFile localRoot,
                                   @NonNull NasCatalogEntry entry) throws Exception {
        UniFile targetRoot = ensureLocalGalleryRoot(localRoot, entry.directoryName);
        UniFile existing = targetRoot.findFile(".ehviewer");
        if (existing != null && existing.isFile() && existing.length() > 0) {
            config.clearPassword();
            return existing;
        }
        SmbConfig smbConfig = createSmbConfig();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            try (Session session = connection.authenticate(authentication());
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String remote = joinRemote(config.remoteDirectory,
                        entry.remoteDirectory.replace('/', '\\') + "\\.ehviewer");
                downloadFile(share, remote, targetRoot, ".ehviewer");
                UniFile metadata = targetRoot.findFile(".ehviewer");
                if (metadata == null) throw new IOException("Unable to prepare NAS metadata");
                return metadata;
            }
        } finally {
            config.clearPassword();
        }
    }

    public boolean downloadPage(@NonNull UniFile localRoot,
                                @NonNull NasCatalogEntry entry, int index) throws Exception {
        UniFile targetRoot = ensureLocalGalleryRoot(localRoot, entry.directoryName);
        if (SpiderDen.findImageFile(targetRoot, index) != null) {
            config.clearPassword();
            return true;
        }
        String basename = String.format(Locale.US, "%08d", index + 1);
        SmbConfig smbConfig = createSmbConfig();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            try (Session session = connection.authenticate(authentication());
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String remoteRoot = joinRemote(config.remoteDirectory,
                        entry.remoteDirectory.replace('/', '\\'));
                for (String extension : GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS) {
                    String filename = basename + extension;
                    String remote = joinRemote(remoteRoot, filename);
                    if (share.fileExists(remote)) {
                        downloadFile(share, remote, targetRoot, filename);
                        return true;
                    }
                }
                return false;
            }
        } finally {
            config.clearPassword();
        }
    }

    public void fetchThumbnailBatch(@NonNull Context context,
                                    @NonNull List<NasCatalogEntry> entries,
                                    @NonNull ThumbnailListener listener) throws Exception {
        if (entries.isEmpty()) return;
        File cacheDirectory = thumbnailCacheDirectory(context);
        SmbConfig smbConfig = createSmbConfig();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            try (Session session = connection.authenticate(authentication());
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                for (NasCatalogEntry entry : entries) {
                    File cached = thumbnailFile(cacheDirectory, entry.gid);
                    if (!cached.isFile() && !entry.remoteThumbnail.isEmpty()) {
                        String remotePath = joinRemote(config.remoteDirectory,
                                entry.remoteThumbnail.replace('/', '\\'));
                        try {
                            writeThumbnail(openRemoteInput(share, remotePath), cached);
                        } catch (IOException ignored) {
                            continue;
                        }
                    }
                    if (cached.isFile()) listener.onThumbnail(entry, cached);
                }
            }
        } finally {
            config.clearPassword();
        }
    }

    @NonNull
    public static File getCachedThumbnail(@NonNull Context context, long gid) {
        return thumbnailFile(thumbnailCacheDirectory(context), gid);
    }

    private void collectRemoteFiles(DiskShare share, String remoteDirectory, String relative,
                                    List<RemoteFile> files, int depth,
                                    ProgressListener listener,
                                    CancellationToken cancellationToken) throws IOException {
        checkCancelled(cancellationToken);
        if (depth > MAX_DEPTH) throw new IOException("NAS gallery nesting is too deep");
        for (FileIdBothDirectoryInformation info : share.list(remoteDirectory)) {
            checkCancelled(cancellationToken);
            String name = info.getFileName();
            if (".".equals(name) || "..".equals(name)) continue;
            if (name.contains(".phone-tmp-")) continue;
            String childRelative = relative.isEmpty() ? name : relative + "/" + name;
            String remotePath = joinRemote(remoteDirectory, name);
            boolean directory = (info.getFileAttributes()
                    & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
            if (directory) {
                boolean reparsePoint = (info.getFileAttributes()
                        & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0;
                if (!reparsePoint) collectRemoteFiles(share, remotePath, childRelative,
                        files, depth + 1, listener, cancellationToken);
            } else {
                files.add(new RemoteFile(remotePath, childRelative));
                if (listener != null) listener.onProgress(files.size(), -1, childRelative);
            }
        }
    }

    private static void downloadFile(DiskShare share, String source, UniFile targetRoot,
                                     String relative) throws IOException {
        String[] parts = relative.split("/");
        UniFile directory = targetRoot;
        for (int i = 0; i < parts.length - 1; i++) {
            UniFile child = directory.findFile(parts[i]);
            if (child == null) child = directory.createDirectory(parts[i]);
            if (child == null || !child.isDirectory()) {
                throw new IOException("Unable to create local directory: " + parts[i]);
            }
            directory = child;
        }
        UniFile target = directory.findFile(parts[parts.length - 1]);
        if (target == null) target = directory.createFile(parts[parts.length - 1]);
        if (target == null || !target.isFile()) {
            throw new IOException("Unable to create local file: " + relative);
        }
        try (InputStream input = openRemoteInput(share, source);
             OutputStream output = target.openOutputStream()) {
            copy(input, output, Long.MAX_VALUE);
        } catch (IOException error) {
            target.delete();
            throw error;
        }
    }

    @NonNull
    private static UniFile ensureLocalGalleryRoot(UniFile localRoot, String directoryName)
            throws IOException {
        if (!localRoot.isDirectory() || !localRoot.canRead() || !localRoot.canWrite()) {
            throw new IOException("Download directory is not readable and writable");
        }
        UniFile targetRoot = localRoot.findFile(directoryName);
        if (targetRoot == null) targetRoot = localRoot.createDirectory(directoryName);
        if (targetRoot == null || !targetRoot.isDirectory()) {
            throw new IOException("Unable to create local gallery directory");
        }
        return targetRoot;
    }

    private AuthenticationContext authentication() {
        return config.username.isEmpty() ? AuthenticationContext.guest()
                : new AuthenticationContext(config.username, config.password, config.domain);
    }

    private static SmbConfig createSmbConfig() {
        return SmbConfig.builder().withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
    }

    private static InputStream openRemoteInput(DiskShare share, String path) {
        com.hierynomus.smbj.share.File file = share.openFile(path,
                EnumSet.of(AccessMask.GENERIC_READ), EnumSet.noneOf(FileAttributes.class),
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
        return new RemoteInputStream(file.getInputStream(), file);
    }

    private static void writeThumbnail(InputStream input, File target) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (InputStream source = new BufferedInputStream(input);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            copy(source, output, MAX_THUMBNAIL_BYTES);
        } catch (IOException error) {
            temporary.delete();
            throw error;
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Unable to replace thumbnail cache");
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to store thumbnail cache");
        }
    }

    private static void copy(InputStream input, OutputStream output, long maximum)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) throw new IOException("Remote file exceeds size limit");
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static void checkCancelled(CancellationToken cancellationToken)
            throws InterruptedIOException {
        if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("NAS operation cancelled");
        }
    }

    private static boolean isSafeRelativePath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.indexOf('\0') >= 0) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private static String joinRemote(String parent, String child) {
        if (parent == null || parent.isEmpty()) return child;
        if (child == null || child.isEmpty()) return parent;
        return parent + "\\" + child;
    }

    private static File thumbnailCacheDirectory(Context context) {
        File directory = new File(context.getCacheDir(), "nas-thumbnails-v1");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    private static File thumbnailFile(File directory, long gid) {
        return new File(directory, Long.toString(gid) + ".thumb");
    }

    private static final class RemoteFile {
        final String remotePath;
        final String relativePath;

        RemoteFile(String remotePath, String relativePath) {
            this.remotePath = remotePath;
            this.relativePath = relativePath;
        }
    }

    private static final class RemoteInputStream extends InputStream {
        private final InputStream input;
        private final com.hierynomus.smbj.share.File file;
        private boolean closed;

        RemoteInputStream(InputStream input, com.hierynomus.smbj.share.File file) {
            this.input = input;
            this.file = file;
        }

        @Override public int read() throws IOException { return input.read(); }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            return input.read(buffer, offset, length);
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
