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

/*
 * Created by Hippo on 8/15/2016.
 */

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

// The OutputStream from Context.getContentResolver().openOutputStream()
// and FileProvider uri may throw Exception when write. The Exception looks like:
// java.io.IOException: write failed: EBADF (Bad file descriptor)
// But UriOutputStream can avoid it on my Nexus 5 cm13.
// TODO need more test
class UriOutputStream extends FileOutputStream {

    private final ParcelFileDescriptor mPfd;

    private UriOutputStream(ParcelFileDescriptor pfd, FileDescriptor fd) {
        super(fd);
        mPfd = pfd;
    }

    @NonNull
    static OutputStream create(Context context, Uri uri, String mode) throws IOException {
        ParcelFileDescriptor pfd;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, mode);
        } catch (Throwable e) {
            Utils.throwIfFatal(e);
            throw new IOException("Can't get ParcelFileDescriptor", e);
        }
        if (pfd == null) {
            throw new IOException("Can't get ParcelFileDescriptor");
        }
        FileDescriptor fd = pfd.getFileDescriptor();
        if (fd == null) {
            throw new IOException("Can't get FileDescriptor");
        }

        return new UriOutputStream(pfd, fd);
    }

    @Override
    public void close() throws IOException {
        mPfd.close();
        super.close();
    }
}
