/*
 * Copyright 2016 Hippo Seven
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

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class SingleDocumentFile extends UniFile {

    private final Context mContext;
    private final Uri mUri;

    SingleDocumentFile(UniFile parent, Context context, Uri uri) {
        super(parent);
        mContext = context.getApplicationContext();
        mUri = uri;
    }

    @Override
    public UniFile createFile(String displayName) {
        return null;
    }

    @Override
    public UniFile createDirectory(String displayName) {
        return null;
    }

    @Override
    @NonNull
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
        return isDirectory();
    }

    @Override
    public boolean ensureFile() {
        if (isFile()) {
            return true;
        } else {
            OutputStream os;
            try {
                os = openOutputStream();
            } catch (IOException e) {
                return false;
            }
            Utils.closeQuietly(os);
            return true;
        }
    }

    @Override
    public UniFile subFile(String displayName) {
        return null;
    }

    @Override
    public boolean delete() {
        return com.hippo.unifile.DocumentsContractApi19.delete(mContext, mUri);
    }

    @Override
    public boolean exists() {
        return com.hippo.unifile.DocumentsContractApi19.exists(mContext, mUri);
    }

    @Override
    public UniFile[] listFiles() {
        return null;
    }

    @Override
    public UniFile[] listFiles(FilenameFilter filter) {
        return null;
    }

    @Override
    public UniFile findFile(String displayName) {
        return null;
    }

    @Override
    public boolean renameTo(String displayName) {
        return false;
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
