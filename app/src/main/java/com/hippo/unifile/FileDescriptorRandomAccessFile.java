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
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

class FileDescriptorRandomAccessFile implements UniRandomAccessFile {

    public static final int FLUSH_NONE = 0;
    public static final int FLUSH_FSYNC = 1;
    public static final int FLUSH_FDATASYNC = 2;
    private final ParcelFileDescriptor pfd;
    private final FileDescriptor fd;
    private final int flushAfterWrite;
    private final Object closeLock = new Object();
    private volatile boolean closed = false;

    private FileDescriptorRandomAccessFile(ParcelFileDescriptor pfd, FileDescriptor fd, String mode) {
        this.pfd = pfd;
        this.fd = fd;

        switch (mode) {
            case "rws":
                flushAfterWrite = FLUSH_FSYNC;
                break;
            case "rwd":
                flushAfterWrite = FLUSH_FDATASYNC;
                break;
            default:
                flushAfterWrite = FLUSH_NONE;
                break;
        }
    }

    @NonNull
    static FileDescriptorRandomAccessFile create(Context context, Uri uri, String mode) throws IOException {
        // Get FileDescriptor
        ParcelFileDescriptor pfd;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, mode);
        } catch (Throwable e) {
            com.hippo.unifile.Utils.throwIfFatal(e);
            throw new IOException("Can't get ParcelFileDescriptor", e);
        }
        if (pfd == null) {
            throw new IOException("Can't get ParcelFileDescriptor");
        }
        FileDescriptor fd;
        try {
            fd = pfd.getFileDescriptor();
        } catch (SecurityException e) {
            pfd.close();
            throw new IOException("Can't get FileDescriptor", e);
        }
        if (fd == null) {
            pfd.close();
            throw new IOException("Can't get FileDescriptor");
        }

        return new FileDescriptorRandomAccessFile(pfd, fd, mode);
    }

    private void maybeSync() {
        if (flushAfterWrite == FLUSH_FSYNC) {
            try {
                fd.sync();
            } catch (IOException e) {
                // Ignored
            }
        } else if (flushAfterWrite == FLUSH_FDATASYNC) {
            try {
                Os.fdatasync(fd);
            } catch (ErrnoException e) {
                // Ignored
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }

        pfd.close();

        try {
            Os.close(fd);
        } catch (ErrnoException e) {
            throw new IOException("An ErrnoException occurs", e);
        }
    }

    @Override
    public long getFilePointer() throws IOException {
        try {
            return Os.lseek(fd, 0L, OsConstants.SEEK_CUR);
        } catch (ErrnoException e) {
            throw new IOException("An ErrnoException occurs", e);
        }
    }

    @Override
    public void seek(long pos) throws IOException {
        if (pos < 0) {
            throw new IOException("offset < 0: " + pos);
        } else {
            try {
                Os.lseek(fd, pos, OsConstants.SEEK_SET);
            } catch (ErrnoException e) {
                throw new IOException("An ErrnoException occurs", e);
            }
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        long pos;
        long len;
        long newpos;

        if (n <= 0) {
            return 0;
        }
        pos = getFilePointer();
        len = length();
        newpos = pos + n;
        if (newpos > len) {
            newpos = len;
        }
        seek(newpos);

        /* return the actual number of bytes skipped */
        return (int) (newpos - pos);
    }

    @Override
    public long length() throws IOException {
        try {
            return Os.fstat(fd).st_size;
        } catch (ErrnoException e) {
            throw new IOException("An ErrnoException occurs", e);
        }
    }

    @Override
    public void setLength(long newLength) throws IOException {
        if (newLength < 0) {
            throw new IllegalArgumentException("newLength < 0");
        }
        try {
            Os.ftruncate(fd, newLength);
        } catch (ErrnoException e) {
            throw new IOException("An ErrnoException occurs", e);
        }

        long filePointer = getFilePointer();
        if (filePointer > newLength) {
            seek(newLength);
        }

        maybeSync();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            return Os.read(fd, b, off, len);
        } catch (ErrnoException e) {
            throw new IOException("An ErrnoException occurs", e);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            Os.write(fd, b, off, len);
        } catch (ErrnoException e) {
            throw new IOException("An ErrnoException occurs", e);
        }
        maybeSync();
    }

    @IntDef({FLUSH_NONE, FLUSH_FSYNC, FLUSH_FDATASYNC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlushMode {
    }
}
