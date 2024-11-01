/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

class TreeDocumentFile extends UniFile {

    private static final String TAG = TreeDocumentFile.class.getSimpleName();

    private final Context mContext;
    private Uri mUri;
    private String mFilename;

    TreeDocumentFile(UniFile parent, Context context, Uri uri) {
        super(parent);
        mContext = context.getApplicationContext();
        mUri = uri;
    }

    private TreeDocumentFile(UniFile parent, Context context, Uri uri, String filename) {
        super(parent);
        mContext = context.getApplicationContext();
        mUri = uri;
        mFilename = filename;
    }

    @Override
    public UniFile createFile(String displayName) {
        UniFile child = findFile(displayName);

        if (child != null) {
            if (child.isFile()) {
                return child;
            } else {
                Log.w(TAG, "Try to create file " + displayName + ", but it is not file");
                return null;
            }
        } else {
            int index = displayName.lastIndexOf('.');
            if (index > 0) {
                String name = displayName.substring(0, index);
                String extension = displayName.substring(index + 1);
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (!TextUtils.isEmpty(mimeType)) {
                    final Uri result = DocumentsContractApi21.createFile(mContext, mUri, mimeType, name);
                    return (result != null) ? new TreeDocumentFile(this, mContext, result, displayName) : null;
                }
            }

            // Not dot in displayName or dot is the first char or can't get MimeType
            final Uri result = DocumentsContractApi21.createFile(mContext, mUri, "application/octet-stream", displayName);
            return (result != null) ? new TreeDocumentFile(this, mContext, result, displayName) : null;
        }
    }

    @Override
    public UniFile createDirectory(String displayName) {
        UniFile child = findFile(displayName);

        if (child != null) {
            if (child.isDirectory()) {
                return child;
            } else {
                return null;
            }
        } else {
            final Uri result = DocumentsContractApi21.createDirectory(mContext, mUri, displayName);
            return (result != null) ? new TreeDocumentFile(this, mContext, result, displayName) : null;
        }
    }

    @NonNull
    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    public String getName() {
        return com.hippo.unifile.DocumentsContractApi19.getName(mContext, mUri);
    }

    @Override
    public String getType() {
        return com.hippo.unifile.DocumentsContractApi19.getType(mContext, mUri);
    }

    @Override
    public boolean isDirectory() {
        return com.hippo.unifile.DocumentsContractApi19.isDirectory(mContext, mUri);
    }

    @Override
    public boolean isFile() {
        return com.hippo.unifile.DocumentsContractApi19.isFile(mContext, mUri);
    }

    @Override
    public long lastModified() {
        return com.hippo.unifile.DocumentsContractApi19.lastModified(mContext, mUri);
    }

    @Override
    public long length() {
        return com.hippo.unifile.DocumentsContractApi19.length(mContext, mUri);
    }

    @Override
    public boolean canRead() {
        return com.hippo.unifile.DocumentsContractApi19.canRead(mContext, mUri);
    }

    @Override
    public boolean canWrite() {
        return com.hippo.unifile.DocumentsContractApi19.canWrite(mContext, mUri);
    }

    @Override
    public boolean ensureDir() {
        if (isDirectory()) {
            return true;
        } else if (isFile()) {
            return false;
        }

        UniFile parent = getParentFile();
        if (parent != null && parent.ensureDir() && mFilename != null) {
            return parent.createDirectory(mFilename) != null;
        } else {
            return false;
        }
    }

    @Override
    public boolean ensureFile() {
        if (isFile()) {
            return true;
        } else if (isDirectory()) {
            return false;
        }

        UniFile parent = getParentFile();
        if (parent != null && parent.ensureDir() && mFilename != null) {
            return parent.createFile(mFilename) != null;
        } else {
            return false;
        }
    }

    @Override
    public UniFile subFile(String displayName) {
        Uri childUri = DocumentsContractApi21.buildChildUri(mUri, displayName);
        return new TreeDocumentFile(this, mContext, childUri, displayName);
    }

    @Override
    public boolean delete() {
        return com.hippo.unifile.DocumentsContractApi19.delete(mContext, mUri);
    }

    @Override
    public boolean exists() {
        return com.hippo.unifile.DocumentsContractApi19.exists(mContext, mUri);
    }

    private String getFilenameForUri(Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            int index = path.lastIndexOf('/');
            if (index >= 0) {
                return path.substring(index + 1);
            }
        }
        return null;
    }

    @Override
    public UniFile[] listFiles() {
        final Uri[] result = DocumentsContractApi21.listFiles(mContext, mUri);
        final UniFile[] resultFiles = new UniFile[result.length];
        for (int i = 0, n = result.length; i < n; i++) {
            Uri uri = result[i];
            resultFiles[i] = new TreeDocumentFile(this, mContext, uri, getFilenameForUri(uri));
        }
        return resultFiles;
    }

    @Override
    public UniFile[] listFiles(FilenameFilter filter) {
        if (filter == null) {
            return listFiles();
        }

        final Uri[] result = DocumentsContractApi21.listFiles(mContext, mUri);
        final ArrayList<UniFile> results = new ArrayList<>();
        for (int i = 0, n = result.length; i < n; i++) {
            Uri uri = result[i];
            String name = getFilenameForUri(uri);
            if (filter.accept(this, name)) {
                results.add(new TreeDocumentFile(this, mContext, uri, name));
            }
        }
        return results.toArray(new UniFile[results.size()]);
    }

    @Override
    public UniFile findFile(String displayName) {
        Uri childUri = DocumentsContractApi21.buildChildUri(mUri, displayName);
        return com.hippo.unifile.DocumentsContractApi19.exists(mContext, childUri) ?
                new TreeDocumentFile(this, mContext, childUri, displayName) : null;
    }

    @Override
    public boolean renameTo(String displayName) {
        final Uri result = DocumentsContractApi21.renameTo(mContext, mUri, displayName);
        if (result != null) {
            mUri = result;
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public OutputStream openOutputStream() throws IOException {
        return Contracts.openOutputStream(mContext, mUri);
    }

    @NonNull
    @Override
    public OutputStream openOutputStream(boolean append) throws IOException {
        return Contracts.openOutputStream(mContext, mUri, append);
    }

    @NonNull
    @Override
    public InputStream openInputStream() throws IOException {
        return Contracts.openInputStream(mContext, mUri);
    }

    @NonNull
    @Override
    public UniRandomAccessFile createRandomAccessFile(String mode) throws IOException {
        // Check file
        if (!ensureFile()) {
            throw new IOException("Can't make sure it is file");
        }

        return FileDescriptorRandomAccessFile.create(mContext, mUri, mode);
    }
}
