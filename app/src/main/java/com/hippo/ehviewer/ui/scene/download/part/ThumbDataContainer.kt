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
package com.hippo.ehviewer.ui.scene.download.part

import com.hippo.conaco.DataContainer
import com.hippo.conaco.ProgressNotifier
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.io.UniFileInputStreamPipe
import com.hippo.lib.yorozuya.IOUtils
import com.hippo.streampipe.InputStreamPipe
import com.hippo.unifile.UniFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 缩略图数据容器
 */
class ThumbDataContainer(private val mInfo: DownloadInfo) : DataContainer {
    private var mDirectory: UniFile? = null
    private var mFile: UniFile? = null

    private fun ensureDirectory(): UniFile? {
        if (mDirectory == null) {
            val dir = SpiderDen.getExistingGalleryDownloadDir(mInfo)
            if (dir != null && dir.isDirectory()) {
                mDirectory = dir
            }
        }
        return mDirectory
    }

    private fun findExistingFile(): UniFile? {
        val dir = ensureDirectory() ?: return null
        val file = dir.findFile(".thumb")
        if (file == null || !file.isFile) return null
        if (file.length() <= 0L) {
            file.delete()
            return null
        }
        mFile = file
        return file
    }

    override fun isEnabled(): Boolean {
        // Reading must never create .thumb. A missing file means Conaco should continue to the
        // network source; the directory is needed only if that response is later saved.
        return ensureDirectory() != null
    }

    override fun onUrlMoved(requestUrl: String?, responseUrl: String?) {
    }

    override fun save(
        `is`: InputStream,
        length: Long,
        mediaType: String?,
        notify: ProgressNotifier?
    ): Boolean {
        val dir = ensureDirectory() ?: return false
        val temporaryName = ".thumb.tmp-${System.nanoTime().toString(16)}"
        val temporary = dir.createFile(temporaryName) ?: return false

        var os: OutputStream? = null
        try {
            os = temporary.openOutputStream()
            IOUtils.copy(`is`, os)
            IOUtils.closeQuietly(os)
            os = null
            if (temporary.length() <= 0L) return false
            val old = dir.findFile(".thumb")
            if (old != null && !old.delete()) return false
            if (!temporary.renameTo(".thumb")) return false
            mFile = dir.findFile(".thumb")
            return mFile != null
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            IOUtils.closeQuietly(os)
            if (temporary.exists()) temporary.delete()
        }
    }

    override fun get(): InputStreamPipe? {
        val file = findExistingFile() ?: return null
        return UniFileInputStreamPipe(file)
    }

    override fun remove() {
        findExistingFile()?.delete()
        mFile = null
    }
}
