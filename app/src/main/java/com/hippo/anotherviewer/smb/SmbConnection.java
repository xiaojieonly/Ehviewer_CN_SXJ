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

package com.hippo.anotherviewer.smb;

import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SmbConnection {

    private static final EnumSet<AccessMask> READ_WRITE_ACCESS = EnumSet.of(AccessMask.GENERIC_ALL);
    private static final EnumSet<FileAttributes> NORMAL_ATTRIBUTES = EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL);
    private static final EnumSet<SMB2ShareAccess> SHARE_ACCESS = EnumSet.of(
            SMB2ShareAccess.FILE_SHARE_READ,
            SMB2ShareAccess.FILE_SHARE_WRITE,
            SMB2ShareAccess.FILE_SHARE_DELETE);

    private static final int MAX_POOL_SIZE = 4;

    private static final java.util.concurrent.ConcurrentHashMap<String, SmbConnection> sPool =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static String poolKey(SmbConfig config) {
        return config.getHost() + ":" + config.getPort() + "/" + config.getShare();
    }

    public static SmbConnection obtain(SmbConfig config) throws IOException {
        String key = poolKey(config);
        synchronized (SmbConnection.class) {
            SmbConnection conn = sPool.get(key);
            if (conn != null && conn.isHealthy()) {
                return conn;
            }
            if (conn != null) {
                sPool.remove(key);
                conn.close();
            }
            if (sPool.size() >= MAX_POOL_SIZE) {
                conn = new SmbConnection(config);
                conn.mPooled = false;
                try {
                    conn.open();
                } catch (IOException e) {
                    conn.close();
                    throw e;
                }
                return conn;
            }
            conn = new SmbConnection(config);
            conn.mPooled = true;
            try {
                conn.open();
            } catch (IOException e) {
                conn.close();
                throw e;
            }
            sPool.put(key, conn);
            return conn;
        }
    }

    private boolean isHealthy() {
        return mPersistentShare != null
                && mPersistentConnection != null
                && mPersistentConnection.isConnected();
    }

    private final SmbConfig config;
    private boolean mPooled;
    private volatile DiskShare mPersistentShare;
    private volatile Connection mPersistentConnection;
    private volatile SMBClient mPersistentClient;
    private volatile Connection mTransientConnection;
    private volatile SMBClient mTransientClient;

    public SmbConnection(SmbConfig config) {
        this.config = config;
    }

    public synchronized void open() throws IOException {
        if (mPersistentShare != null) return;
        mPersistentClient = createClient();
        mPersistentConnection = mPersistentClient.connect(config.getHost(), config.getPort());
        Session session = mPersistentConnection.authenticate(authenticationContext());
        mPersistentShare = (DiskShare) session.connectShare(config.getShare());
    }

    public synchronized void close() {
        if (mPersistentShare != null) {
            try { mPersistentShare.close(); } catch (Exception ignored) {}
            mPersistentShare = null;
        }
        if (mPersistentConnection != null) {
            closeQuietly(mPersistentConnection);
            mPersistentConnection = null;
        }
        if (mPersistentClient != null) {
            closeQuietly(mPersistentClient);
            mPersistentClient = null;
        }
        if (mTransientConnection != null) {
            closeQuietly(mTransientConnection);
            mTransientConnection = null;
        }
        if (mTransientClient != null) {
            closeQuietly(mTransientClient);
            mTransientClient = null;
        }
    }

    public synchronized void release() {
        if (!mPooled) {
            close();
        }
    }

    public synchronized DiskShare getShare() throws IOException {
        if (mPersistentShare != null) return mPersistentShare;
        return openShare();
    }

    public synchronized void releaseShare(DiskShare share) {
        if (share == mPersistentShare) return;
        try { share.close(); } catch (Exception ignored) {}
        if (mTransientConnection != null) {
            closeQuietly(mTransientConnection);
            mTransientConnection = null;
        }
        if (mTransientClient != null) {
            closeQuietly(mTransientClient);
            mTransientClient = null;
        }
    }

    public List<SmbEntry> list(String path) throws IOException {
        DiskShare share = getShare();
        try {
            String smbPath = normalizeSmbPath(path);
            List<FileIdBothDirectoryInformation> entries = share.list(smbPath);
            List<SmbEntry> results = new ArrayList<>(entries.size());
            for (FileIdBothDirectoryInformation entry : entries) {
                String name = entry.getFileName();
                if (!".".equals(name) && !"..".equals(name)) {
                    results.add(new SmbEntry(name, isDirectory(entry)));
                }
            }
            return results;
        } finally {
            releaseShare(share);
        }
    }

    public boolean exists(String path) throws IOException {
        DiskShare share = getShare();
        try {
            String smbPath = normalizeSmbPath(path);
            return TextUtils.isEmpty(smbPath) || share.fileExists(smbPath) || share.folderExists(smbPath);
        } finally {
            releaseShare(share);
        }
    }

    public boolean isDirectory(String path) throws IOException {
        if (TextUtils.isEmpty(normalizeSmbPath(path))) {
            return true;
        }
        DiskShare share = getShare();
        try {
            return share.folderExists(normalizeSmbPath(path));
        } finally {
            releaseShare(share);
        }
    }

    public boolean isFile(String path) throws IOException {
        if (TextUtils.isEmpty(normalizeSmbPath(path))) {
            return false;
        }
        DiskShare share = getShare();
        try {
            return share.fileExists(normalizeSmbPath(path));
        } finally {
            releaseShare(share);
        }
    }

    public boolean ensureDirectory(String path) throws IOException {
        DiskShare share = getShare();
        try {
            String smbPath = normalizeSmbPath(path);
            if (TextUtils.isEmpty(smbPath)) {
                return true;
            }
            String[] parts = smbPath.split("/");
            String current = "";
            for (String part : parts) {
                if (TextUtils.isEmpty(part) || ".".equals(part) || "..".equals(part)) {
                    continue;
                }
                current = TextUtils.isEmpty(current) ? part : current + "/" + part;
                if (!share.folderExists(current)) {
                    share.mkdir(current);
                }
            }
            return true;
        } finally {
            releaseShare(share);
        }
    }

    public boolean ensureFile(String path) throws IOException {
        DiskShare share = getShare();
        File file = null;
        try {
            file = openFile(share, path, SMB2CreateDisposition.FILE_OPEN_IF);
            return true;
        } finally {
            closeQuietly(file);
            releaseShare(share);
        }
    }

    public void writeFile(String path, java.io.InputStream source) throws IOException {
        writeFile(path, source, false, null);
    }

    public void writeFile(String path, java.io.InputStream source, @Nullable java.lang.Runnable onChunk) throws IOException {
        writeFile(path, source, false, onChunk);
    }

    public void writeFile(String path, java.io.InputStream source, boolean append) throws IOException {
        writeFile(path, source, append, null);
    }

    public void writeFile(String path, java.io.InputStream source, boolean append,
            @Nullable java.lang.Runnable onChunk) throws IOException {
        if (append) {
            try (OutputStream os = openOutputStream(path, true)) {
                copyToOutputStream(source, os, onChunk);
            }
            return;
        }

        DiskShare share = getShare();
        File file = null;
        try {
            file = openFile(share, path, SMB2CreateDisposition.FILE_OVERWRITE_IF);
            try (OutputStream os = file.getOutputStream(false)) {
                copyToOutputStream(source, os, onChunk);
            }
        } finally {
            closeQuietly(file);
            releaseShare(share);
        }
    }

    public OutputStream openOutputStream(String path, boolean append) throws IOException {
        DiskShare share = getShare();
        File file = null;
        try {
            file = openFile(share, path, SMB2CreateDisposition.FILE_OPEN_IF);
            OutputStream stream = file.getOutputStream(append);
            return new SmbOutputStream(stream, file, share, this);
        } catch (RuntimeException e) {
            closeQuietly(file);
            releaseShare(share);
            throw e;
        }
    }

    public InputStream openInputStream(String path) throws IOException {
        DiskShare share = getShare();
        File file = null;
        try {
            file = openFile(share, path, SMB2CreateDisposition.FILE_OPEN);
            InputStream stream = file.getInputStream();
            return new SmbInputStream(stream, file, share, this);
        } catch (RuntimeException e) {
            closeQuietly(file);
            releaseShare(share);
            throw e;
        }
    }

    public boolean delete(String path) throws IOException {
        DiskShare share = getShare();
        try {
            String smbPath = normalizeSmbPath(path);
            if (TextUtils.isEmpty(smbPath)) {
                return false;
            }
            if (share.folderExists(smbPath)) {
                for (SmbEntry entry : listEntries(share, smbPath)) {
                    delete(share, joinSmbPath(smbPath, entry.getName()));
                }
                share.rmdir(smbPath, true);
            } else if (share.fileExists(smbPath)) {
                share.rm(smbPath);
            } else {
                return false;
            }
            return true;
        } finally {
            releaseShare(share);
        }
    }

    public boolean rename(String path, String displayName) throws IOException {
        DiskShare share = getShare();
        File file = null;
        try {
            file = openFile(share, path, SMB2CreateDisposition.FILE_OPEN);
            String parent = parentPath(path);
            String target = joinSmbPath(parent, displayName);
            file.rename(target, false);
            return true;
        } finally {
            closeQuietly(file);
            releaseShare(share);
        }
    }

    public long length(String path) throws IOException {
        DiskShare share = getShare();
        try {
            String smbPath = normalizeSmbPath(path);
            if (TextUtils.isEmpty(smbPath)) {
                return -1L;
            }
            return share.getFileInformation(smbPath).getStandardInformation().getEndOfFile();
        } finally {
            releaseShare(share);
        }
    }

    public String mimeType(String path) {
        String name = name(normalizeSmbPath(path));
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            String extension = name.substring(lastDot + 1).toLowerCase();
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        return "application/octet-stream";
    }

    public List<String> listShareNames() throws IOException {
        return listFolders("");
    }

    public List<String> listFolders(String subPath) throws IOException {
        List<String> result = new ArrayList<>();
        DiskShare share = getShare();
        try {
            List<FileIdBothDirectoryInformation> entries = share.list(subPath);
            for (FileIdBothDirectoryInformation entry : entries) {
                String entryName = entry.getFileName();
                if (".".equals(entryName) || "..".equals(entryName)) {
                    continue;
                }
                try {
                    if ((entry.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0) {
                        result.add(entryName);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to list folders", e);
        } finally {
            releaseShare(share);
        }
        return result;
    }

    public void testConnection() throws IOException {
        SMBClient client = createClient();
        Connection connection = null;
        try {
            connection = client.connect(config.getHost(), config.getPort());
            connection.authenticate(authenticationContext());
        } catch (IOException e) {
            throw e;
        } finally {
            closeQuietly(connection);
            closeQuietly(client);
        }
    }

    private DiskShare openShare() throws IOException {
        SMBClient client = createClient();
        Connection connection = null;
        try {
            connection = client.connect(config.getHost(), config.getPort());
            Session session = connection.authenticate(authenticationContext());
            DiskShare share = (DiskShare) session.connectShare(config.getShare());
            mTransientClient = client;
            mTransientConnection = connection;
            return share;
        } catch (IOException | RuntimeException e) {
            closeQuietly(connection);
            closeQuietly(client);
            throw e;
        }
    }

    private static SMBClient createClient() {
        com.hierynomus.smbj.SmbConfig smbConfig = com.hierynomus.smbj.SmbConfig.builder()
                .withSoTimeout(10, TimeUnit.SECONDS)
                .withReadTimeout(30, TimeUnit.SECONDS)
                .withTimeout(30, TimeUnit.SECONDS)
                .build();
        return new SMBClient(smbConfig);
    }

    private AuthenticationContext authenticationContext() {
        if (config.getLoginMode() == SmbLoginMode.PASSWORD) {
            return new AuthenticationContext(config.getUsername(), config.getPassword().toCharArray(), "");
        }
        return AuthenticationContext.anonymous();
    }

    private File openFile(DiskShare share, String path, SMB2CreateDisposition disposition) {
        return share.openFile(normalizeSmbPath(path), READ_WRITE_ACCESS, NORMAL_ATTRIBUTES,
                SHARE_ACCESS, disposition, null);
    }

    private List<SmbEntry> listEntries(DiskShare share, String path) {
        try {
            List<FileIdBothDirectoryInformation> entries = share.list(TextUtils.isEmpty(path) ? "" : path);
            List<SmbEntry> results = new ArrayList<>(entries.size());
            for (FileIdBothDirectoryInformation entry : entries) {
                String name = entry.getFileName();
                if (!".".equals(name) && !"..".equals(name)) {
                    results.add(new SmbEntry(name, isDirectory(entry)));
                }
            }
            return results;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private void delete(DiskShare share, String path) throws IOException {
        if (share.folderExists(path)) {
            for (SmbEntry entry : listEntries(share, path)) {
                delete(share, joinSmbPath(path, entry.getName()));
            }
            share.rmdir(path, true);
        } else if (share.fileExists(path)) {
            share.rm(path);
        }
    }

    private boolean isDirectory(FileIdBothDirectoryInformation entry) {
        try {
            return (entry.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeSmbPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String parentPath(String path) {
        String normalized = normalizeSmbPath(path);
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(0, index) : "";
    }

    private static String name(String path) {
        String normalized = normalizeSmbPath(path);
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static String joinSmbPath(String parent, String child) {
        if (TextUtils.isEmpty(parent)) {
            return child;
        }
        return parent + "/" + child;
    }

    private static void copyToOutputStream(InputStream source, OutputStream os,
            @Nullable Runnable onChunk) throws IOException {
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = source.read(buf)) != -1) {
            os.write(buf, 0, n);
            if (onChunk != null) {
                onChunk.run();
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeQuietly(SMBClient client) {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(File file) {
        if (file != null) {
            file.close();
        }
    }

    private static final class SmbInputStream extends InputStream {
        private final InputStream delegate;
        private final File file;
        private final DiskShare share;
        private final SmbConnection connection;

        private SmbInputStream(InputStream delegate, File file, DiskShare share, SmbConnection connection) {
            this.delegate = delegate;
            this.file = file;
            this.share = share;
            this.connection = connection;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                try {
                    file.close();
                } finally {
                    connection.releaseShare(share);
                }
            }
        }
    }

    private static final class SmbOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final File file;
        private final DiskShare share;
        private final SmbConnection connection;

        private SmbOutputStream(OutputStream delegate, File file, DiskShare share, SmbConnection connection) {
            this.delegate = delegate;
            this.file = file;
            this.share = share;
            this.connection = connection;
        }

        @Override
        public void write(int oneByte) throws IOException {
            delegate.write(oneByte);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                try {
                    file.close();
                } finally {
                    connection.releaseShare(share);
                }
            }
        }
    }

    public static final class SmbEntry {
        private final String name;
        private final boolean directory;

        private SmbEntry(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }
    }
}
