package com.hippo.ehviewer.client.wifi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WiFiDownloadMigrationHelper {

    public static final int FILE_CHUNK_SIZE = 256 * 1024;

    private WiFiDownloadMigrationHelper() {
    }

    public static final class FileMeta {
        public String name;
        public String md5;
        public long size;
    }

    public static final class DirManifest {
        public long gid;
        public String dirname;
        public List<FileMeta> files = new ArrayList<>();
        public int pageIndex;
        public int pageSize;
    }

    public static final class DirAck {
        public long gid;
        public String dirname;
        public List<String> filesNeeded = new ArrayList<>();
    }

    public static final class FileChunk {
        public long gid;
        public String name;
        public String fileMd5;
        public int chunkIndex;
        public int chunkTotal;
        public byte[] data;
        public boolean lastChunk;
    }

    public static final class GalleryDirEntry {
        public long gid;
        public String dirname;
        public UniFile dir;
    }

    @Nullable
    public static UniFile getDownloadRoot() {
        return Settings.getDownloadLocation();
    }

    @NonNull
    public static List<GalleryDirEntry> scanGalleryDirs() {
        List<GalleryDirEntry> result = new ArrayList<>();
        UniFile root = getDownloadRoot();
        if (root == null) {
            return result;
        }
        UniFile[] files = root.listFiles();
        if (files == null) {
            return result;
        }
        for (UniFile file : files) {
            if (file == null || !file.isDirectory()) {
                continue;
            }
            String dirname = file.getName();
            if (dirname == null) {
                continue;
            }
            long gid = parseGidFromDirname(dirname);
            if (gid < 0) {
                continue;
            }
            GalleryDirEntry entry = new GalleryDirEntry();
            entry.gid = gid;
            entry.dirname = dirname;
            entry.dir = file;
            result.add(entry);
        }
        return result;
    }

    public static long parseGidFromDirname(@NonNull String dirname) {
        int dash = dirname.indexOf('-');
        if (dash <= 0) {
            return -1;
        }
        try {
            return Long.parseLong(dirname.substring(0, dash));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @NonNull
    public static List<FileMeta> scanGalleryDir(@NonNull UniFile dir) {
        List<FileMeta> list = new ArrayList<>();
        collectFiles(dir, "", list);
        return list;
    }

    private static void collectFiles(@NonNull UniFile dir, @NonNull String prefix,
            @NonNull List<FileMeta> out) {
        UniFile[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (UniFile child : children) {
            if (child == null) {
                continue;
            }
            String name = child.getName();
            if (name == null) {
                continue;
            }
            String rel = prefix.isEmpty() ? name : prefix + "/" + name;
            if (child.isDirectory()) {
                if (SpiderQueen.SPIDER_INFO_BACKUP_DIR.equals(name)) {
                    continue;
                }
                collectFiles(child, rel, out);
            } else if (child.isFile()) {
                FileMeta meta = new FileMeta();
                meta.name = rel;
                meta.size = child.length();
                meta.md5 = computeMd5Hex(child);
                if (meta.md5 != null) {
                    out.add(meta);
                }
            }
        }
    }

    @Nullable
    public static UniFile resolveGalleryDir(long gid, @NonNull String dirname) {
        UniFile root = getDownloadRoot();
        if (root == null) {
            return null;
        }
        return root.subFile(dirname);
    }

    @Nullable
    public static UniFile ensureGalleryDir(long gid, @NonNull String dirname) {
        UniFile dir = resolveGalleryDir(gid, dirname);
        if (dir == null) {
            UniFile root = getDownloadRoot();
            if (root == null) {
                return null;
            }
            dir = root.subFile(dirname);
        }
        if (dir == null) {
            return null;
        }
        if (!dir.isDirectory()) {
            if (!dir.ensureDir()) {
                return null;
            }
            EhDB.putDownloadDirname(gid, dirname);
        }
        return dir;
    }

    @NonNull
    public static List<String> filterFilesNeeded(@NonNull UniFile galleryDir,
            @NonNull List<FileMeta> remoteFiles) {
        List<String> needed = new ArrayList<>();
        for (FileMeta meta : remoteFiles) {
            UniFile local = resolveFileInGallery(galleryDir, meta.name);
            if (local == null || !local.isFile()) {
                needed.add(meta.name);
                continue;
            }
            String localMd5 = computeMd5Hex(local);
            if (localMd5 == null || !localMd5.equalsIgnoreCase(meta.md5)) {
                needed.add(meta.name);
            }
        }
        return needed;
    }

    @Nullable
    public static UniFile resolveFileInGallery(@NonNull UniFile galleryDir, @NonNull String relPath) {
        if (!relPath.contains("/")) {
            return galleryDir.subFile(relPath);
        }
        String[] parts = relPath.split("/");
        UniFile current = galleryDir;
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            current = current.subFile(part);
        }
        return current;
    }

    public static boolean writeChunk(@NonNull UniFile galleryDir, @NonNull String relPath,
            @NonNull byte[] data, long offset) throws IOException {
        UniFile target = resolveFileForWrite(galleryDir, relPath);
        if (target == null) {
            return false;
        }
        OutputStream os = target.openOutputStream(offset != 0);
        if (os == null) {
            return false;
        }
        try {
            os.write(data);
            os.flush();
            return true;
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    @Nullable
    private static UniFile resolveFileForWrite(@NonNull UniFile galleryDir, @NonNull String relPath) {
        if (!relPath.contains("/")) {
            UniFile file = galleryDir.subFile(relPath);
            return file != null ? file : galleryDir.createFile(relPath);
        }
        int lastSlash = relPath.lastIndexOf('/');
        String parentPath = relPath.substring(0, lastSlash);
        String fileName = relPath.substring(lastSlash + 1);
        UniFile parent = resolveFileInGallery(galleryDir, parentPath);
        if (parent == null) {
            parent = galleryDir;
            for (String segment : parentPath.split("/")) {
                UniFile next = parent.subFile(segment);
                if (next == null) {
                    next = parent.createDirectory(segment);
                }
                if (next == null) {
                    return null;
                }
                if (!next.isDirectory() && !next.ensureDir()) {
                    return null;
                }
                parent = next;
            }
        } else if (!parent.isDirectory() && !parent.ensureDir()) {
            return null;
        }
        UniFile file = parent.subFile(fileName);
        if (file == null) {
            file = parent.createFile(fileName);
        }
        return file;
    }

    public static boolean verifyFileMd5(@NonNull UniFile file, @NonNull String expectedMd5) {
        String actual = computeMd5Hex(file);
        return actual != null && actual.equalsIgnoreCase(expectedMd5);
    }

    @Nullable
    public static String computeMd5Hex(@NonNull UniFile file) {
        InputStream is = null;
        try {
            is = file.openInputStream();
            if (is == null) {
                return null;
            }
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @NonNull
    private static String bytesToHex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    /** Per-file write state on the client during directory migration. */
    public static final class FileWriteState {
        private final Map<String, Long> offsets = new HashMap<>();

        public long getOffset(@NonNull String name) {
            Long v = offsets.get(name);
            return v != null ? v : 0L;
        }

        public void addWritten(@NonNull String name, int bytes) {
            offsets.put(name, getOffset(name) + bytes);
        }

        public void clear(@NonNull String name) {
            offsets.remove(name);
        }
    }
}
