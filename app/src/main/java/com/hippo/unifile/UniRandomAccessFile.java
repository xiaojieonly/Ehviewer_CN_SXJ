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

/**
 * The UniRandomAccessFile is designed to emulate RandomAccessFile interface for UniFile
 */
public interface UniRandomAccessFile {

    /**
     * Closes this UniRandomAccessFile and releases any resources associated with it.
     * A closed UniRandomAccessFile cannot perform any operations.
     *
     * @throws IOException
     */
    void close() throws IOException;

    /**
     * Returns the current offset in this file.
     *
     * @throws IOException
     */
    long getFilePointer() throws IOException;

    /**
     * Move current location pointer to the new offset.
     *
     * @param pos the offset position, measured in bytes from the beginning
     * @throws IOException
     */
    void seek(long pos) throws IOException;

    /**
     * Attempts to skip over n bytes of input discarding the skipped bytes.
     *
     * @param n the number of bytes to be skipped
     * @return the actual number of bytes skipped
     * @throws IOException
     */
    int skipBytes(int n) throws IOException;

    /**
     * Returns the length of this file.
     *
     * @throws IOException
     */
    long length() throws IOException;

    /**
     * Sets the length of this file.
     *
     * @param newLength the desired length of the file
     * @throws IOException
     */
    void setLength(long newLength) throws IOException;

    /**
     * Reads b.length bytes from this file into the byte array,
     * starting at the current file pointer.
     *
     * @param b the buffer into which the data is read
     * @return the total number of bytes read into the buffer, or
     * {@code -1} if there is no more data because the end of
     * the file has been reached.
     * @throws IOException if an I/O error occurs
     */
    int read(byte[] b) throws IOException;

    /**
     * Reads exactly len bytes from this file into the byte array,
     * starting at the current file pointer.
     *
     * @param b   the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the number of bytes to read
     * @return the total number of bytes read into the buffer, or
     * {@code -1} if there is no more data because the end of
     * the file has been reached.
     * @throws IOException
     */
    int read(byte[] b, int off, int len) throws IOException;

    /**
     * Writes b.length bytes from the specified byte array to this file,
     * starting at the current file pointer.
     *
     * @param b the data
     * @throws IOException
     */
    void write(byte[] b) throws IOException;

    /**
     * Writes len bytes from the specified byte array starting at offset off to this file.
     *
     * @param b   the data
     * @param off the start offset in the data
     * @param len the number of bytes to write
     * @throws IOException
     */
    void write(byte[] b, int off, int len) throws IOException;
}
