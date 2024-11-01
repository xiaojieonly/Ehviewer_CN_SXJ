/*
 * Copyright 2015 Hippo Seven
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

class MediaFile extends UniFile {

    private final Context mContext;
    private final Uri mUri;

    MediaFile(Context context, Uri uri) {
        super(null);

        mContext = context.getApplicationContext();
        mUri = uri;
    }

    static boolean isMediaUri(Context context, Uri uri) {
        return null != com.hippo.unifile.MediaContract.getName(context, uri);
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
        return com.hippo.unifile.MediaContract.getName(mContext, mUri);
    }

    @Override
    public String getType() {
        return com.hippo.unifile.MediaContract.getType(mContext, mUri);
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isFile() {
        InputStream is;
        try {
            is = openInputStream();
        } catch (IOException e) {
            return false;
        }
        Utils.closeQuietly(is);
        return true;
    }

    @Override
    public long lastModified() {
        return com.hippo.unifile.MediaContract.lastModified(mContext, mUri);
    }

    @Override
    public long length() {
        return com.hippo.unifile.MediaContract.length(mContext, mUri);
    }

    @Override
    public boolean canRead() {
        return isFile();
    }

    @Override
    public boolean canWrite() {
        OutputStream os;
        try {
            os = openOutputStream(true);
        } catch (IOException e) {
            return false;
        }
        Utils.closeQuietly(os);
        return true;
    }

    @Override
    public boolean ensureDir() {
        return false;
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
        return false;
    }

    @Override
    public boolean exists() {
        return isFile();
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
        return UriOutputStream.create(mContext, mUri, "w");
    }

    @NonNull
    @Override
    public OutputStream openOutputStream(boolean append) throws IOException {
        return UriOutputStream.create(mContext, mUri, append ? "wa" : "w");
    }

    @NonNull
    @Override
    public InputStream openInputStream() throws IOException {
        return Contracts.openInputStream(mContext, mUri);
    }

    @NonNull
    @Override
    public UniRandomAccessFile createRandomAccessFile(String mode) throws IOException {
        return FileDescriptorRandomAccessFile.create(mContext, mUri, mode);
    }
}
