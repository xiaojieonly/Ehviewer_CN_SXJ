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
    private var mFile: UniFile? = null

    private fun ensureFile() {
        if (mFile == null) {
            val dir = SpiderDen.getGalleryDownloadDir(mInfo)
            if (dir != null && dir.isDirectory()) {
                mFile = dir.createFile(".thumb")
            }
        }
    }

    override fun isEnabled(): Boolean {
        ensureFile()
        return mFile != null
    }

    override fun onUrlMoved(requestUrl: String?, responseUrl: String?) {
    }

    override fun save(
        `is`: InputStream,
        length: Long,
        mediaType: String?,
        notify: ProgressNotifier?
    ): Boolean {
        ensureFile()
        if (mFile == null) {
            return false
        }

        var os: OutputStream? = null
        try {
            os = mFile!!.openOutputStream()
            IOUtils.copy(`is`, os)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            IOUtils.closeQuietly(os)
        }
    }

    override fun get(): InputStreamPipe? {
        ensureFile()
        if (mFile != null) {
            return UniFileInputStreamPipe(mFile)
        } else {
            return null
        }
    }

    override fun remove() {
        if (mFile != null) {
            mFile!!.delete()
        }
    }
}
