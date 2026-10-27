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
package com.hippo.ehviewer.preference

import android.app.Activity
import com.hippo.app.ProgressDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.parser.ComicInfoParser
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.unifile.UniFile
import com.hippo.util.ExceptionUtils.throwIfFatal
import com.hippo.util.IoThreadPoolExecutor
import okhttp3.OkHttpClient
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class RestoreDownloadPreference : Preference {
    private var mTask: RestoreTask? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onClick() {
        super.onClick()
        if (mTask == null) {
            mTask = RestoreTask(context).also { it.start() }
        }
    }

    override fun onDetached() {
        mTask?.cancel()
        mTask = null
        super.onDetached()
    }

    private inner class RestoreTask(private val mContext: Context) {
        private val mApplication: EhApplication =
            mContext.applicationContext as EhApplication
        private val mManager: DownloadManager =
            EhApplication.getDownloadManager(mApplication)
        private val mHttpClient: OkHttpClient =
            EhApplication.getOkHttpClient(mApplication)
        private val mHandler = Handler(Looper.getMainLooper())
        private val mCancelled = AtomicBoolean(false)
        private var mProgressDialog: ProgressDialog? = null

        fun start() {
            onPreExecute()
            IoThreadPoolExecutor.instance.execute {
                val result = doInBackground()
                mHandler.post {
                    if (mCancelled.get()) {
                        onCancelled()
                    } else {
                        onPostExecute(result)
                    }
                }
            }
        }

        fun cancel() {
            mCancelled.set(true)
        }

        private fun onPreExecute() {
            mProgressDialog = ProgressDialog(mContext)
            mProgressDialog!!.setTitle(R.string.settings_download_restore_download_items)
            mProgressDialog!!.setIndeterminate(false)
            mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            mProgressDialog!!.setCancelable(false)
            mProgressDialog!!.show()
        }

        /**
         * 从下载子目录取出 gid 和 token。
         * 先读文本 .ehviewer，失败再读同一文件的 CBOR，仍失败则读 ComicInfo.xml。
         * 目录无效、三种来源都没有 gid/token，或该画廊已在下载列表中时返回 null。
         */
        fun getRestoreItem(file: UniFile?): RestoreItem? {
            if (null == file || !file.isDirectory()) {
                return null
            }
            var gid = -1L
            var token: String? = null
            val siFile = file.findFile(SpiderQueen.SPIDER_INFO_FILENAME)
            if (siFile != null) {
                val spiderInfo = SpiderInfo.readHeader(siFile) ?: SpiderInfo.readCborHeader(siFile)
                if (spiderInfo != null && spiderInfo.gid >= 0 && !spiderInfo.token.isNullOrEmpty()) {
                    gid = spiderInfo.gid
                    token = spiderInfo.token
                }
            }
            if (gid < 0L || token.isNullOrEmpty()) {
                val comicInfo = readComicInfo(file)
                if (comicInfo != null && comicInfo.gid >= 0 && !comicInfo.token.isNullOrEmpty()) {
                    gid = comicInfo.gid
                    token = comicInfo.token
                }
            }
            if (gid < 0L || token.isNullOrEmpty()) {
                return null
            }
            if (mManager.containDownloadInfo(gid)) {
                return null
            }
            val restoreItem = RestoreItem()
            restoreItem.gid = gid
            restoreItem.token = token
            restoreItem.dirname = file.getName()
            return restoreItem
        }

        /**
         * 读取目录中的 ComicInfo.xml，并从 Web 地址解析 gid 和 token。
         * 文件不存在、超过大小上限或地址无法解析时返回 null。
         */
        private fun readComicInfo(dir: UniFile): GalleryDetailUrlParser.Result? {
            val comicFile = dir.findFile(ComicInfoParser.FILENAME) ?: return null
            return try {
                comicFile.openInputStream().use { ComicInfoParser.parse(it) }
            } catch (e: Throwable) {
                throwIfFatal(e)
                null
            }
        }

        private fun doInBackground(): Any? {
            if (mCancelled.get()) {
                return null
            }
            val dir = Settings.getDownloadLocation() ?: return null

            val restoreItemList: MutableList<RestoreItem?> = ArrayList()

            val files = dir.listFiles() ?: return null

            val total = files.size
            publishProgress(0, total)

            for (i in 0..<total) {
                if (mCancelled.get()) {
                    return null
                }
                val file = files[i]
                val restoreItem = getRestoreItem(file)
                if (restoreItem != null) {
                    restoreItemList.add(restoreItem)
                }
                publishProgress(i + 1, total)
            }

            if (restoreItemList.isEmpty()) {
                return Collections.EMPTY_LIST
            }

            publishProgress(-1, -1)

            val galleryInfoList = ArrayList<GalleryInfo?>(restoreItemList)
            EhEngine.fillGalleryListByApiBestEffort(
                null,
                mHttpClient,
                galleryInfoList,
                EhUrl.getReferer()
            )
            return galleryInfoList
        }

        private fun publishProgress(progress: Int, max: Int) {
            mHandler.post { onProgressUpdate(progress, max) }
        }

        val isContextValid: Boolean
            get() {
                if (mContext is Activity) {
                    val activity = mContext
                    return !activity.isFinishing() && !activity.isDestroyed()
                }
                return true
            }

        fun dismissProgressDialog() {
            if (mProgressDialog == null) {
                return
            }
            if (this.isContextValid) {
                try {
                    if (mProgressDialog!!.isShowing) {
                        mProgressDialog!!.dismiss()
                    }
                } catch (e: IllegalArgumentException) {
                    throwIfFatal(e)
                }
            }
            mProgressDialog = null
        }

        private fun onProgressUpdate(progress: Int, max: Int) {
            if (mProgressDialog != null && this.isContextValid) {
                if (progress == -1 && max == -1) {
                    mProgressDialog!!.setIndeterminate(true)
                    mProgressDialog!!.setMessage(mApplication.getString(R.string.settings_download_restore_download_items_get_gallery_info))
                } else {
                    mProgressDialog!!.setIndeterminate(false)
                    mProgressDialog!!.setMax(max)
                    mProgressDialog!!.setProgress(progress)
                }
            }
        }

        private fun onCancelled() {
            mTask = null
            dismissProgressDialog()
        }

        private fun onPostExecute(o: Any?) {
            mTask = null
            dismissProgressDialog()
            if (o !is MutableList<*>) {
                Toast.makeText(
                    mApplication,
                    R.string.settings_download_restore_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val list: MutableList<RestoreItem> = o as MutableList<RestoreItem>
                if (list.isEmpty()) {
                    Toast.makeText(
                        mApplication,
                        R.string.settings_download_restore_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    var count = 0
                    var i = 0
                    val n = list.size
                    while (i < n) {
                        val item = list.get(i)
                        if (item.title.isNullOrEmpty()) {
                            item.title = item.dirname?.takeIf { it.isNotEmpty() }
                                ?: item.gid.toString()
                        }
                        mManager.addDownload(item, null)
                        EhDB.putDownloadDirname(item.gid, item.dirname)
                        count++
                        i++
                    }
                    Toast.makeText(
                        mApplication,
                        mApplication.getString(
                            R.string.settings_download_restore_successfully,
                            count
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    if (this.isContextValid) {
                        (mContext as Activity).setResult(Activity.RESULT_OK)
                    }
                }
            }
        }
    }

    private class RestoreItem : GalleryInfo {
        var dirname: String? = null

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeString(this.dirname)
        }

        constructor()

        protected constructor(`in`: Parcel) : super(`in`) {
            this.dirname = `in`.readString()
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<RestoreItem?> =
                object : Parcelable.Creator<RestoreItem?> {
                    override fun createFromParcel(source: Parcel): RestoreItem {
                        return RestoreItem(source)
                    }

                    override fun newArray(size: Int): Array<RestoreItem?> {
                        return arrayOfNulls<RestoreItem>(size)
                    }
                }
        }
    }
}
