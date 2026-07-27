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

package com.hippo.ehviewer.smb;

import android.util.Log;

import androidx.annotation.Nullable;

import com.hippo.unifile.UniFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared "walk local directories -> upload to SMB" sync algorithm. Used by the
 * backup service, the upload service and the in-app sync task. Callers supply a
 * {@link Source} (the local files), a {@link Callback} (progress/cancellation)
 * and {@link Options}; the engine owns the connection, the traversal, the
 * skip-if-unchanged check and the streaming / RAM-buffered upload.
 */
public final class SmbSyncEngine {

    private static final String TAG = "SmbSyncEngine";
    private static final int READ_BUFFER_BYTES = 256 * 1024;

    private SmbSyncEngine() {}

    /** A local store to sync from. */
    public interface Source {
        List<Gallery> galleries() throws IOException;
    }

    /** A top-level gallery directory. */
    public interface Gallery {
        @Nullable
        String name();

        List<Entry> entries() throws IOException;
    }

    /** A gallery whose local backing can be deleted after a successful upload. */
    public interface Deletable {
        void deleteRecursive();
    }

    /** A file or subdirectory within a gallery. */
    public interface Entry {
        @Nullable
        String name();

        boolean isDirectory();

        long length();

        InputStream open() throws IOException;
    }

    /**
     * Progress and cancellation callback. Every method is invoked on the sync
     * (background) thread; implementations must marshal to the main thread
     * themselves before touching UI.
     */
    public interface Callback {
        void onScan(int total);

        void onGallery(int index, int total, String name);

        void onFile(int fileIndex, int fileTotal, String name);

        boolean isCancelled();

        /** Throughput in bytes/sec, reported about once per second. Default no-op. */
        default void onSpeed(long bytesPerSecond) {}
    }

    public static final class Options {
        public boolean deleteAfterUpload = false;
        public boolean aggressive = false;
        public long ramBufferSize = 0;
    }

    public static final class Result {
        public final int success;
        public final int fail;

        Result(int success, int fail) {
            this.success = success;
            this.fail = fail;
        }
    }

    public static Result sync(SmbConfig config, Source source, Callback callback, Options options) {
        SmbConnection connection = new SmbConnection(config);
        int total = 0;
        int success = 0;
        int fail = 0;
        try {
            List<Gallery> galleries = source.galleries();
            total = galleries.size();
            callback.onScan(total);
            connection.open();
            String basePath = config.getPath();
            for (int i = 0; i < galleries.size(); i++) {
                if (callback.isCancelled()) break;
                Gallery gallery = galleries.get(i);
                String name = gallery.name();
                if (name == null || name.startsWith(".")) continue;
                callback.onGallery(i + 1, total, name);
                try {
                    syncGallery(connection, gallery, basePath, callback, options);
                    if (options.deleteAfterUpload && !callback.isCancelled()
                            && gallery instanceof Deletable) {
                        ((Deletable) gallery).deleteRecursive();
                    }
                    success++;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to sync gallery: " + name, e);
                    fail++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SMB sync failed", e);
            fail = total;
        } finally {
            connection.close();
        }
        return new Result(success, fail);
    }

    private static void syncGallery(SmbConnection connection, Gallery gallery, String basePath,
            Callback callback, Options options) throws IOException {
        String name = gallery.name();
        String galleryPath = basePath.isEmpty() ? name : basePath + "/" + name;
        connection.ensureDirectory(galleryPath);
        List<Entry> entries = gallery.entries();
        int fileTotal = entries.size();
        int fileIndex = 0;
        for (Entry entry : entries) {
            if (callback.isCancelled()) break;
            String entryName = entry.name();
            if (entryName == null) continue;
            if (entry.isDirectory()) {
                connection.ensureDirectory(galleryPath + "/" + entryName);
                continue;
            }
            String filePath = galleryPath + "/" + entryName;
            if (connection.exists(filePath) && connection.length(filePath) == entry.length()) {
                continue;
            }
            callback.onFile(++fileIndex, fileTotal, entryName);
            uploadFile(connection, entry, filePath, callback, options);
        }
    }

    private static void uploadFile(SmbConnection connection, Entry entry, String filePath,
            Callback callback, Options options) throws IOException {
        SpeedTracker tracker = new SpeedTracker();
        if (options.aggressive && options.ramBufferSize > 0) {
            try {
                uploadWithRamBuffer(connection, entry, filePath, options.ramBufferSize, callback, tracker);
            } catch (OutOfMemoryError e) {
                uploadWithStream(connection, entry, filePath, callback, tracker);
            }
        } else {
            uploadWithStream(connection, entry, filePath, callback, tracker);
        }
    }

    private static void uploadWithStream(SmbConnection connection, Entry entry, String filePath,
            Callback callback, SpeedTracker tracker) throws IOException {
        try (CountingInputStream cis = new CountingInputStream(entry.open())) {
            connection.writeFile(filePath, cis, () -> reportSpeed(callback, tracker, cis.getCount()));
        }
    }

    private static void uploadWithRamBuffer(SmbConnection connection, Entry entry, String filePath,
            long bufferSize, Callback callback, SpeedTracker tracker) throws IOException {
        int readBufferSize = (int) Math.max(8192L, Math.min(bufferSize, READ_BUFFER_BYTES));
        byte[] buffer = new byte[readBufferSize];
        ByteArrayOutputStream ramBuffer = new ByteArrayOutputStream(
                Math.min(readBufferSize * 4, 1024 * 1024));
        long flushThreshold = Math.max(readBufferSize, bufferSize);
        boolean append = false;
        long totalBytes = 0;
        try (InputStream in = entry.open()) {
            int bytesRead;
            while (!callback.isCancelled() && (bytesRead = in.read(buffer)) != -1) {
                ramBuffer.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                reportSpeed(callback, tracker, totalBytes);
                if (ramBuffer.size() >= flushThreshold) {
                    append = flushRamBuffer(connection, filePath, ramBuffer, append);
                    ramBuffer.reset();
                }
            }
            if (!callback.isCancelled() && ramBuffer.size() > 0) {
                flushRamBuffer(connection, filePath, ramBuffer, append);
            }
        }
    }

    private static boolean flushRamBuffer(SmbConnection connection, String filePath,
            ByteArrayOutputStream ramBuffer, boolean append) throws IOException {
        byte[] data = ramBuffer.toByteArray();
        try (InputStream in = new ByteArrayInputStream(data)) {
            connection.writeFile(filePath, in, append);
        }
        return true;
    }

    private static void reportSpeed(Callback callback, SpeedTracker tracker, long totalBytes) {
        long bps = tracker.bytesPerSecond(totalBytes);
        if (bps >= 0) {
            callback.onSpeed(bps);
        }
    }

    private static final class SpeedTracker {
        private long windowStart;
        private long windowBytes;

        long bytesPerSecond(long totalBytes) {
            long now = System.currentTimeMillis();
            if (windowStart == 0) {
                windowStart = now;
                windowBytes = totalBytes;
                return -1;
            }
            long elapsed = now - windowStart;
            if (elapsed >= 1000) {
                long bps = (totalBytes - windowBytes) * 1000 / elapsed;
                windowStart = now;
                windowBytes = totalBytes;
                return bps;
            }
            return -1;
        }
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private long count;

        CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        long getCount() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    public static Source uniFileSource(UniFile dir) {
        return new UniFileSource(dir);
    }

    public static Source fileSource(File dir) {
        return new LocalFileSource(dir);
    }

    private static final class UniFileSource implements Source {
        private final UniFile dir;

        UniFileSource(UniFile dir) {
            this.dir = dir;
        }

        @Override
        public List<Gallery> galleries() {
            UniFile[] dirs = dir.listFiles();
            List<Gallery> result = new ArrayList<>();
            if (dirs != null) {
                for (UniFile d : dirs) {
                    if (d != null && d.isDirectory()) {
                        result.add(new UniFileGallery(d));
                    }
                }
            }
            return result;
        }
    }

    private static final class UniFileGallery implements Gallery {
        private final UniFile dir;

        UniFileGallery(UniFile dir) {
            this.dir = dir;
        }

        @Override
        public String name() {
            return dir.getName();
        }

        @Override
        public List<Entry> entries() {
            UniFile[] files = dir.listFiles();
            List<Entry> result = new ArrayList<>();
            if (files != null) {
                for (UniFile f : files) {
                    if (f != null && f.getName() != null && !f.getName().startsWith(".")) {
                        result.add(new UniFileEntry(f));
                    }
                }
            }
            return result;
        }
    }

    private static final class UniFileEntry implements Entry {
        private final UniFile file;

        UniFileEntry(UniFile file) {
            this.file = file;
        }

        @Override
        public String name() {
            return file.getName();
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public long length() {
            return file.length();
        }

        @Override
        public InputStream open() throws IOException {
            return file.openInputStream();
        }
    }

    private static final class LocalFileSource implements Source {
        private final File dir;

        LocalFileSource(File dir) {
            this.dir = dir;
        }

        @Override
        public List<Gallery> galleries() {
            File[] dirs = dir.listFiles();
            List<Gallery> result = new ArrayList<>();
            if (dirs != null) {
                for (File d : dirs) {
                    if (d != null && d.isDirectory()) {
                        result.add(new LocalFileGallery(d));
                    }
                }
            }
            return result;
        }
    }

    private static final class LocalFileGallery implements Gallery, Deletable {
        private final File dir;

        LocalFileGallery(File dir) {
            this.dir = dir;
        }

        @Override
        public String name() {
            return dir.getName();
        }

        @Override
        public List<Entry> entries() {
            File[] files = dir.listFiles();
            List<Entry> result = new ArrayList<>();
            if (files != null) {
                for (File f : files) {
                    if (f != null) {
                        result.add(new LocalFileEntry(f));
                    }
                }
            }
            return result;
        }

        @Override
        public void deleteRecursive() {
            deleteFileTree(dir);
        }
    }

    private static final class LocalFileEntry implements Entry {
        private final File file;

        LocalFileEntry(File file) {
            this.file = file;
        }

        @Override
        public String name() {
            return file.getName();
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public long length() {
            return file.length();
        }

        @Override
        public InputStream open() throws IOException {
            return new BufferedInputStream(new FileInputStream(file), READ_BUFFER_BYTES);
        }
    }

    private static void deleteFileTree(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileTree(child);
                }
            }
        }
        fileOrDir.delete();
    }
}
