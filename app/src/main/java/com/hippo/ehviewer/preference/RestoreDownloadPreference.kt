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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.lib.yorozuya.IOUtils
import com.hippo.unifile.UniFile
import com.hippo.util.ExceptionUtils.throwIfFatal
import com.hippo.util.IoThreadPoolExecutor
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.InputStream
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

        fun getRestoreItem(file: UniFile?): RestoreItem? {
            if (null == file || !file.isDirectory()) {
                return null
            }
            val siFile = file.findFile(SpiderQueen.SPIDER_INFO_FILENAME) ?: return null

            var `is`: InputStream? = null
            try {
                `is` = siFile.openInputStream()
                val spiderInfo = SpiderInfo.read(`is`) ?: return null
                val gid = spiderInfo.gid
                if (mManager.containDownloadInfo(gid)) {
                    return null
                }
                val token = spiderInfo.token
                val restoreItem = RestoreItem()
                restoreItem.gid = gid
                restoreItem.token = token
                restoreItem.dirname = file.getName()
                return restoreItem
            } catch (e: IOException) {
                FirebaseCrashlytics.getInstance().recordException(e)
                return null
            } finally {
                IOUtils.closeQuietly(`is`)
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

            return try {
                EhEngine.fillGalleryListByApi(
                    null,
                    mHttpClient,
                    ArrayList<GalleryInfo?>(restoreItemList),
                    EhUrl.getReferer()
                )
            } catch (e: Throwable) {
                throwIfFatal(e)
                e.printStackTrace()
                null
            }
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
                        // Avoid failed gallery info
                        if (null != item.title) {
                            // Put to download
                            mManager.addDownload(item, null)
                            // Put download dir to DB
                            EhDB.putDownloadDirname(item.gid, item.dirname)
                            count++
                        }
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
