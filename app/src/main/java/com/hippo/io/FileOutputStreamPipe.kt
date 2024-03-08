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
package com.hippo.io

import com.hippo.streampipe.OutputStreamPipe
import com.hippo.yorozuya.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class FileOutputStreamPipe(private val mFile: File) : OutputStreamPipe {
    private var mOs: OutputStream? = null
    override fun obtain() {
        // Empty
    }

    override fun release() {
        // Empty
    }

    @Throws(IOException::class)
    override fun open(): OutputStream {
        check(mOs == null) { "Please close it first" }
        mOs = FileOutputStream(mFile)
        return mOs as FileOutputStream
    }

    override fun close() {
        IOUtils.closeQuietly(mOs)
        mOs = null
    }
}