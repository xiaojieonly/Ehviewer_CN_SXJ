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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hippo.app.CheckBoxDialogBuilder
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadService
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.ui.GalleryActivity
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.widget.MyEasyRecyclerView
import com.hippo.lib.yorozuya.collect.LongList
import com.hippo.unifile.UniFile
import com.hippo.util.IoThreadPoolExecutor.Companion.instance
import com.hippo.widget.FabLayout
import java.util.LinkedList
import androidx.core.util.size

/**
 * 下载页 FAB 多选操作及删除/移动对话框。
 */
class DownloadBatchActions(private val mHost: Host) {
    interface Host {
        val eHContext: Context?

        val activity2: MainActivity?

        val recyclerView: MyEasyRecyclerView?

        val list: MutableList<DownloadInfo>?

        val downloadManager: DownloadManager?

        fun positionInList(position: Int): Int

        val fabLayout: FabLayout?

        fun onClickPrimaryFab(view: FabLayout?, fab: FloatingActionButton?)

        fun launchGalleryActivity(intent: Intent?)

        val resources: Resources?

        fun getString(resId: Int): String?

        fun getString(resId: Int, vararg formatArgs: Any?): String?
    }

    fun onClickSecondaryFab(view: FabLayout?, fab: FloatingActionButton, position: Int) {
        val context: Context? = mHost.eHContext
        val activity: Activity? = mHost.activity2
        val recyclerView = mHost.recyclerView
        if (null == context || null == activity || null == recyclerView) {
            return
        }

        if (0 == position) {
            recyclerView.checkAll()
        } else {
            val list = mHost.list ?: return

            var gidList: LongList? = null
            var downloadInfoList: MutableList<DownloadInfo?>? = null
            val collectGid = position == 1 || position == 2 || position == 3 // Start, Stop, Delete
            val collectDownloadInfo = position == 3 || position == 4 // Delete or Move
            if (collectGid) {
                gidList = LongList()
            }
            if (collectDownloadInfo) {
                downloadInfoList = LinkedList<DownloadInfo?>()
            }

            val stateArray = recyclerView.getCheckedItemPositions()
            var i = 0
            val n = stateArray.size
            while (i < n) {
                if (stateArray.valueAt(i)) {
                    val info = list[mHost.positionInList(stateArray.keyAt(i))]
                    if (collectDownloadInfo) {
                        downloadInfoList!!.add(info)
                    }
                    if (collectGid) {
                        gidList!!.add(info.gid)
                    }
                }
                i++
            }

            when (position) {
                1 -> {
                    // Start
                    if (gidList!!.isEmpty) {
                        return
                    }
                    val intent = Intent(activity, DownloadService::class.java)
                    intent.setAction(DownloadService.ACTION_START_RANGE)
                    intent.putExtra(DownloadService.KEY_GID_LIST, gidList)
                    activity.startService(intent)
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode()
                }

                2 -> {
                    // Stop
                    if (gidList!!.isEmpty) {
                        return
                    }
                    val downloadManager =
                        mHost.downloadManager
                    downloadManager?.stopRangeDownload(gidList)
                    // Cancel check mode
                    recyclerView.outOfCustomChoiceMode()
                }

                3 -> {
                    // Delete
                    if (downloadInfoList!!.isEmpty()) {
                        return
                    }
                    val builder = CheckBoxDialogBuilder(
                        context,
                        mHost.getString(
                            R.string.download_remove_dialog_message_2,
                            gidList!!.size()
                        ),
                        mHost.getString(R.string.download_remove_dialog_check_text),
                        Settings.getRemoveImageFiles()
                    )
                    val helper = DeleteRangeDialogHelper(
                        downloadInfoList, gidList, builder, recyclerView, mHost.downloadManager
                    )
                    builder.setTitle(R.string.download_remove_dialog_title)
                        .setPositiveButton(android.R.string.ok, helper)
                        .show()
                }

                4 -> {
                    // Move
                    if (downloadInfoList!!.isEmpty()) {
                        return
                    }
                    val labelRawList = EhApplication.getDownloadManager(context).getLabelList()
                    val labelList: MutableList<String?> = ArrayList<String?>(labelRawList.size + 1)
                    labelList.add(mHost.getString(R.string.default_download_label_name))
                    var i = 0
                    val n = labelRawList.size
                    while (i < n) {
                        labelList.add(labelRawList.get(i)!!.getLabel())
                        i++
                    }
                    val labels = labelList.toTypedArray<String?>()

                    val helper = MoveDialogHelper(labels, downloadInfoList, recyclerView, mHost)

                    AlertDialog.Builder(context)
                        .setTitle(R.string.download_move_dialog_title)
                        .setItems(labels, helper)
                        .show()
                }

                5 -> {
                    if (mHost.list == null || mHost.list!!.isEmpty()) {
                        return
                    }
                    mHost.onClickPrimaryFab(mHost.fabLayout, null)
                    viewRandom()
                }

                6 -> setDragEnable(fab)
            }
        }
    }

    fun setDragEnable(fab: FloatingActionButton) {
        DownloadAdapter.DRAG_ENABLE = !DownloadAdapter.DRAG_ENABLE
        Settings.setDragDownloadGallery(DownloadAdapter.DRAG_ENABLE)
        val context: Context = mHost.eHContext ?: return
        if (DownloadAdapter.DRAG_ENABLE) {
            fab.setImageDrawable(
                mHost.resources?.let {
                    ResourcesCompat.getDrawable(
                        it,
                        R.drawable.v_mobile_hand_left_x24,
                        context.theme
                    )
                }
            )
        } else {
            fab.setImageDrawable(
                mHost.resources?.let {
                    ResourcesCompat.getDrawable(
                        it,
                        R.drawable.v_mobile_hand_left_off_x24,
                        context.theme
                    )
                }
            )
        }
        //        mDragDropManager.cancelDrag(dragEnable);
    }

    fun viewRandom() {
        val list = mHost.list
        if (list == null) {
            return
        }
        val position = (Math.random() * list.size).toInt()
        if (position < 0 || position >= list.size) {
            return
        }
        val activity: Activity? = mHost.activity2
        if (null == activity || null == mHost.recyclerView) {
            return
        }

        val intent = Intent(activity, GalleryActivity::class.java)
        intent.setAction(GalleryActivity.ACTION_EH)
        intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, list.get(position))
        mHost.launchGalleryActivity(intent)
    }

    /**
     * 单条删除对话框。当前下载页未使用，保持原逻辑。
     */
    class DeleteDialogHelper(
        private val mGalleryInfo: GalleryInfo, private val mBuilder: CheckBoxDialogBuilder,
        private val mDownloadManager: DownloadManager?
    ) : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface?, which: Int) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return
            }

            // Delete
            mDownloadManager?.deleteDownload(mGalleryInfo.gid)

            // Delete image files
            val checked = mBuilder.isChecked
            Settings.putRemoveImageFiles(checked)
            if (checked) {
                val file = SpiderDen.getExistingGalleryDownloadDir(mGalleryInfo)
                EhDB.removeDownloadDirname(mGalleryInfo.gid)
                if (file != null) {
                    deleteFileAsync(file)
                } else {
                    deleteGalleryFilesAsync(mutableListOf<GalleryInfo?>(mGalleryInfo))
                }
            }
        }
    }

    class DeleteRangeDialogHelper(
        private val mDownloadInfoList: MutableList<DownloadInfo?>?,
        private val mGidList: LongList?, private val mBuilder: CheckBoxDialogBuilder,
        private val mRecyclerView: MyEasyRecyclerView?,
        private val mDownloadManager: DownloadManager?
    ) : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface?, which: Int) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return
            }

            // Cancel check mode
            mRecyclerView?.outOfCustomChoiceMode()

            // Delete
            mDownloadManager?.deleteRangeDownload(mGidList)

            // Delete image files
            val checked = mBuilder.isChecked
            Settings.putRemoveImageFiles(checked)
            if (checked) {
                deleteGalleryFilesAsync(mDownloadInfoList)
            }
        }
    }

    class MoveDialogHelper(
        private val mLabels: Array<String?>,
        private val mDownloadInfoList: MutableList<DownloadInfo?>?,
        private val mRecyclerView: MyEasyRecyclerView?,
        private val mHost: Host
    ) : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface?, which: Int) {
            // Cancel check mode
            val context: Context = mHost.eHContext ?: return
            mRecyclerView?.outOfCustomChoiceMode()

            val label: String?
            if (which == 0) {
                label = null
            } else {
                label = mLabels[which]
            }
            EhApplication.getDownloadManager(context).changeLabel(mDownloadInfoList, label)
        }
    }

    companion object {
        fun deleteFileAsync(vararg files: UniFile?) {
            DeleteFileTask().executeOnExecutor(instance, *files)
        }

        fun deleteGalleryFilesAsync(galleryInfoList: MutableList<out GalleryInfo?>?) {
            DeleteGalleryFilesTask().executeOnExecutor(instance, galleryInfoList)
        }
    }

    private class DeleteFileTask : AsyncTask<UniFile?, Void?, Void?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: UniFile?): Void? {
            for (file in params) {
                if (file != null) {
                    file.delete()
                }
            }
            return null
        }
    }

    private class DeleteGalleryFilesTask : AsyncTask<MutableList<out GalleryInfo?>?, Void?, Void?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: MutableList<out GalleryInfo?>?): Void? {
            for (info in params[0]!!) {
                val file = SpiderDen.getGalleryDownloadDir(info)
                EhDB.removeDownloadDirname(info!!.gid)
                if (file != null) {
                    file.delete()
                }
            }
            return null
        }
    }
}
