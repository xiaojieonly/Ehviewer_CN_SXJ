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

package com.hippo.unifile;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.smb.SmbConfig;
import com.hippo.ehviewer.smb.SmbConnection;
import com.hippo.ehviewer.smb.SmbConnection.SmbEntry;
import com.hippo.ehviewer.smb.SmbSettings;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class SmbUniFile extends UniFile {

    private static final String TAG = SmbUniFile.class.getSimpleName();

    private final Context mContext;
    private final SmbUri mSmbUri;
    private final String mFilename;

    SmbUniFile(UniFile parent, Context context, SmbUri smbUri) {
        super(parent);
        mContext = context.getApplicationContext();
        mSmbUri = smbUri;
        mFilename = name(smbUri.getPath());
    }

    private SmbUniFile(UniFile parent, Context context, SmbUri smbUri, String filename) {
        super(parent);
        mContext = context.getApplicationContext();
        mSmbUri = smbUri;
        mFilename = filename;
    }

    @Override
    public UniFile createFile(String displayName) {
        if (!isSafeName(displayName)) {
            return null;
        }
        try {
            SmbConnection connection = connection();
            connection.openOutputStream(joinPath(displayName), false).close();
            return subFile(displayName);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to create SMB file " + displayName, e);
            return null;
        }
    }

    @Override
    public UniFile createDirectory(String displayName) {
        if (!isSafeName(displayName)) {
            return null;
        }
        try {
            SmbConnection connection = connection();
            connection.ensureDirectory(joinPath(displayName));
            return subFile(displayName);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to create SMB directory " + displayName, e);
            return null;
        }
    }

    @NonNull
    @Override
    public Uri getUri() {
        return mSmbUri.toUri();
    }

    @Nullable
    @Override
    public String getName() {
        return mFilename;
    }

    @Nullable
    @Override
    public String getType() {
        try {
            return connection().mimeType(mSmbUri.getPath());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean isDirectory() {
        try {
            return connection().isDirectory(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean isFile() {
        try {
            return connection().isFile(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public long lastModified() {
        return -1L;
    }

    @Override
    public long length() {
        try {
            return connection().length(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            return -1L;
        }
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public boolean ensureDir() {
        try {
            return connection().ensureDirectory(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to ensure SMB directory " + mSmbUri.getPath(), e);
            return false;
        }
    }

    @Override
    public boolean ensureFile() {
        try {
            return connection().ensureFile(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to ensure SMB file " + mSmbUri.getPath(), e);
            return false;
        }
    }

    @Override
    public UniFile subFile(String displayName) {
        if (!isSafeName(displayName)) {
            return null;
        }
        SmbUri uri = SmbUri.create(mSmbUri.getHost(), mSmbUri.getPort(), mSmbUri.getShare(), joinPath(displayName));
        return new SmbUniFile(this, mContext, uri, displayName);
    }

    @Override
    public boolean delete() {
        try {
            return connection().delete(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to delete SMB path " + mSmbUri.getPath(), e);
            return false;
        }
    }

    @Override
    public boolean exists() {
        try {
            return connection().exists(mSmbUri.getPath());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public UniFile[] listFiles() {
        try {
            List<SmbEntry> entries = connection().list(mSmbUri.getPath());
            UniFile[] results = new UniFile[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                SmbEntry entry = entries.get(i);
                SmbUri child = SmbUri.create(mSmbUri.getHost(), mSmbUri.getPort(), mSmbUri.getShare(), joinPath(entry.getName()));
                results[i] = new SmbUniFile(this, mContext, child, entry.getName());
            }
            return results;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to list SMB directory " + mSmbUri.getPath(), e);
            return null;
        }
    }

    @Nullable
    @Override
    public UniFile[] listFiles(FilenameFilter filter) {
        if (filter == null) {
            return listFiles();
        }
        UniFile[] files = listFiles();
        if (files == null) {
            return null;
        }
        List<UniFile> results = new ArrayList<>();
        for (UniFile file : files) {
            String name = file.getName();
            if (name != null && filter.accept(this, name)) {
                results.add(file);
            }
        }
        return results.toArray(new UniFile[results.size()]);
    }

    @Nullable
    @Override
    public UniFile findFile(String displayName) {
        if (!isSafeName(displayName)) {
            return null;
        }
        try {
            return connection().exists(joinPath(displayName)) ? subFile(displayName) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean renameTo(String displayName) {
        if (!isSafeName(displayName)) {
            return false;
        }
        try {
            return connection().rename(mSmbUri.getPath(), displayName);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to rename SMB path " + mSmbUri.getPath(), e);
            return false;
        }
    }

    @NonNull
    @Override
    public OutputStream openOutputStream() throws IOException {
        return connection().openOutputStream(mSmbUri.getPath(), false);
    }

    @NonNull
    @Override
    public OutputStream openOutputStream(boolean append) throws IOException {
        return connection().openOutputStream(mSmbUri.getPath(), append);
    }

    @NonNull
    @Override
    public InputStream openInputStream() throws IOException {
        return connection().openInputStream(mSmbUri.getPath());
    }

    @NonNull
    @Override
    public UniRandomAccessFile createRandomAccessFile(String mode) throws IOException {
        throw new FileNotFoundException("Random access is not supported for SMB");
    }

    private SmbConnection connection() {
        SmbConfig config = new SmbSettings(mContext).loadConfig();
        if (config == null
                || !mSmbUri.getHost().equals(config.getHost())
                || mSmbUri.getPort() != config.getPort()
                || !mSmbUri.getShare().equals(config.getShare())) {
            throw new IllegalStateException("SMB credentials are not configured for " + mSmbUri);
        }
        try {
            return SmbConnection.obtain(config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to SMB", e);
        }
    }

    private String joinPath(String child) {
        String parent = mSmbUri.getPath();
        if (TextUtils.isEmpty(parent)) {
            return child;
        }
        return parent + "/" + child;
    }

    private static boolean isSafeName(String name) {
        return !TextUtils.isEmpty(name) && !"/".equals(name) && !"\\".equals(name)
                && !name.contains("/") && !name.contains("\\") && !".".equals(name) && !"..".equals(name);
    }

    private static String name(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }
}
