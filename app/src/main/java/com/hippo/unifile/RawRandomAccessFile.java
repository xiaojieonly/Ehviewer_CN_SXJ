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

import java.io.IOException;
import java.io.RandomAccessFile;

class RawRandomAccessFile implements UniRandomAccessFile {

    private final RandomAccessFile mFile;

    RawRandomAccessFile(RandomAccessFile file) {
        mFile = file;
    }

    @Override
    public void close() throws IOException {
        mFile.close();
    }

    @Override
    public long getFilePointer() throws IOException {
        return mFile.getFilePointer();
    }

    @Override
    public void seek(long pos) throws IOException {
        mFile.seek(pos);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return mFile.skipBytes(n);
    }

    @Override
    public long length() throws IOException {
        return mFile.length();
    }

    @Override
    public void setLength(long newLength) throws IOException {
        mFile.setLength(newLength);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return mFile.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return mFile.read(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        mFile.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        mFile.write(b, off, len);
    }
}
