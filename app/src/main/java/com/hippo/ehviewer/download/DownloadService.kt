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
package com.hippo.ehviewer.download

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.scene.download.DownloadsScene
import com.hippo.scene.StageActivity
import com.hippo.util.ReadableTime
import com.hippo.lib.yorozuya.FileUtils
import com.hippo.lib.yorozuya.SimpleHandler
import com.hippo.lib.yorozuya.collect.LongList
import com.hippo.lib.yorozuya.collect.SparseJBArray
import com.hippo.lib.yorozuya.collect.SparseJLArray

@SuppressLint("UnspecifiedImmutableFlag")
class DownloadService : Service(), DownloadManager.DownloadListener {
    private var mNotifyManager: NotificationManager? = null
    private var mDownloadManager: DownloadManager? = null
    private var mDownloadingBuilder: NotificationCompat.Builder? = null
    private var mDownloadedBuilder: NotificationCompat.Builder? = null
    private var m509dBuilder: NotificationCompat.Builder? = null
    private var mDownloadingDelay: NotificationDelay? = null
    private var mDownloadedDelay: NotificationDelay? = null
    private var m509Delay: NotificationDelay? = null


    private var CHANNEL_ID: String? = null

    override fun onCreate() {
        super.onCreate()

        CHANNEL_ID = "$packageName.download"
        mNotifyManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotifyManager!!.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, getString(R.string.download_service),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        mDownloadManager = EhApplication.getDownloadManager(applicationContext)
        mDownloadManager!!.setDownloadListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        mNotifyManager = null
        if (mDownloadManager != null) {
            mDownloadManager!!.setDownloadListener(null)
            mDownloadManager = null
        }
        mDownloadingBuilder = null
        mDownloadedBuilder = null
        m509dBuilder = null
        if (mDownloadingDelay != null) {
            mDownloadingDelay!!.release()
        }
        if (mDownloadedDelay != null) {
            mDownloadedDelay!!.release()
        }
        if (m509Delay != null) {
            m509Delay!!.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Handle the case where the intent is null
            return START_STICKY
        }
        handleIntent(intent)
        return START_STICKY
    }

    private fun handleIntent(intent: Intent?) {
        var action: String? = null
        if (intent != null) {
            action = intent.action
        }
        if (action == null) {
            checkStopSelf()
            return
        }
        when (action) {
            ACTION_CLEAR -> clear()
            ACTION_DELETE_RANGE -> {
                val gidList = intent!!.getParcelableExtra<LongList>(KEY_GID_LIST)
                if (gidList != null && mDownloadManager != null) {
                    mDownloadManager!!.deleteRangeDownload(gidList)
                }
            }

            ACTION_DELETE -> {
                val gid = intent!!.getLongExtra(KEY_GID, -1)
                if (gid != -1L && mDownloadManager != null) {
                    mDownloadManager!!.deleteDownload(gid)
                }
            }

            ACTION_STOP_ALL -> if (mDownloadManager != null) {
                mDownloadManager!!.stopAllDownload()
            }

            ACTION_STOP_RANGE -> {
                val gidListS = intent!!.getParcelableExtra<LongList>(KEY_GID_LIST)
                if (gidListS != null && mDownloadManager != null) {
                    mDownloadManager!!.stopRangeDownload(gidListS)
                }
            }

            ACTION_STOP_CURRENT -> if (mDownloadManager != null) {
                mDownloadManager!!.stopCurrentDownload()
            }

            ACTION_STOP -> {
                val gidS = intent!!.getLongExtra(KEY_GID, -1)
                if (gidS != -1L && mDownloadManager != null) {
                    mDownloadManager!!.stopDownload(gidS)
                }
            }

            ACTION_START_ALL -> if (mDownloadManager != null) {
                mDownloadManager!!.startAllDownload()
            }

            ACTION_START_RANGE -> {
                val gidListSR = intent!!.getParcelableExtra<LongList>(KEY_GID_LIST)
                if (gidListSR != null && mDownloadManager != null) {
                    mDownloadManager!!.startRangeDownload(gidListSR)
                }
            }

            ACTION_START -> {
                val gi = intent!!.getParcelableExtra<GalleryInfo>(KEY_GALLERY_INFO)
                val label = intent.getStringExtra(KEY_LABEL)
                if (gi != null && mDownloadManager != null) {
                    mDownloadManager!!.startDownload(gi, label)
                }
            }
        }
        checkStopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        throw IllegalStateException("No bindService")
    }

    @Suppress("deprecation")
    private fun ensureDownloadingBuilder() {
        if (mDownloadingBuilder != null) {
            return
        }

        val stopAllIntent = Intent(this, DownloadService::class.java)
        stopAllIntent.setAction(ACTION_STOP_ALL)
        val piStopAll = PendingIntent.getService(this, 0, stopAllIntent, 0)

        mDownloadingBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID!!)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(resources.getColor(R.color.colorPrimary))
            .addAction(
                R.drawable.ic_pause_x24,
                getString(R.string.stat_download_action_stop_all),
                piStopAll
            )
            .setShowWhen(false)
            .setChannelId(CHANNEL_ID!!)

        mDownloadingDelay =
            NotificationDelay(this, mNotifyManager, mDownloadingBuilder!!, ID_DOWNLOADING)
    }

    private fun ensureDownloadedBuilder() {
        if (mDownloadedBuilder != null) {
            return
        }

        val clearIntent = Intent(this, DownloadService::class.java)
        clearIntent.setAction(ACTION_CLEAR)
        val piClear = PendingIntent.getService(this, 0, clearIntent, 0)

        val bundle = Bundle()
        bundle.putString(DownloadsScene.KEY_ACTION, DownloadsScene.ACTION_CLEAR_DOWNLOAD_SERVICE)
        val activityIntent = Intent(this, MainActivity::class.java)
        activityIntent.setAction(StageActivity.ACTION_START_SCENE)
        activityIntent.putExtra(StageActivity.KEY_SCENE_NAME, DownloadsScene::class.java.name)
        activityIntent.putExtra(StageActivity.KEY_SCENE_ARGS, bundle)
        val piActivity = PendingIntent.getActivity(
            this@DownloadService, 0,
            activityIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        mDownloadedBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID!!)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(getString(R.string.stat_download_done_title))
            .setDeleteIntent(piClear)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(piActivity)
            .setChannelId(CHANNEL_ID!!)

        mDownloadedDelay =
            NotificationDelay(this, mNotifyManager, mDownloadedBuilder!!, ID_DOWNLOADED)
    }

    private fun ensure509Builder() {
        if (m509dBuilder != null) {
            return
        }

        m509dBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID!!)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentText(getString(R.string.stat_509_alert_title))
            .setContentText(getString(R.string.stat_509_alert_text))
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setChannelId(CHANNEL_ID!!)

        m509Delay = NotificationDelay(this, mNotifyManager, m509dBuilder!!, ID_509)
    }

    override fun onGet509() {
        if (mNotifyManager == null) {
            return
        }

        ensure509Builder()
        m509dBuilder!!.setWhen(System.currentTimeMillis())
        m509Delay!!.show()
    }

    override fun onStart(info: DownloadInfo) {
        if (mNotifyManager == null) {
            return
        }

        ensureDownloadingBuilder()

        val bundle = Bundle()
        bundle.putLong(DownloadsScene.KEY_GID, info.gid)
        val activityIntent = Intent(this, MainActivity::class.java)
        activityIntent.setAction(StageActivity.ACTION_START_SCENE)
        activityIntent.putExtra(StageActivity.KEY_SCENE_NAME, DownloadsScene::class.java.name)
        activityIntent.putExtra(StageActivity.KEY_SCENE_ARGS, bundle)
        val piActivity = PendingIntent.getActivity(
            this@DownloadService, 0,
            activityIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        mDownloadingBuilder!!.setContentTitle(EhUtils.getSuitableTitle(info))
            .setContentText(null)
            .setContentInfo(null)
            .setProgress(0, 0, true)
            .setContentIntent(piActivity)

        mDownloadingDelay!!.startForeground()
    }

    private fun onUpdate(info: DownloadInfo) {
        if (mNotifyManager == null) {
            return
        }
        ensureDownloadingBuilder()

        var speed = info.speed
        if (speed < 0) {
            speed = 0
        }
        var text = FileUtils.humanReadableByteCount(speed, false) + "/S"
        val remaining = info.remaining
        text = if (remaining >= 0) {
            getString(
                R.string.download_speed_text_2,
                text,
                ReadableTime.getShortTimeInterval(remaining)
            )
        } else {
            getString(R.string.download_speed_text, text)
        }
        mDownloadingBuilder!!.setContentTitle(EhUtils.getSuitableTitle(info))
            .setContentText(text)
            .setContentInfo(if (info.total == -1 || info.finished == -1) null else info.finished.toString() + "/" + info.total)
            .setProgress(info.total, info.finished, false)

        mDownloadingDelay!!.startForeground()
    }

    override fun onDownload(info: DownloadInfo) {
        onUpdate(info)
    }

    override fun onGetPage(info: DownloadInfo) {
        onUpdate(info)
    }

    override fun onFinish(info: DownloadInfo) {
        if (mNotifyManager == null) {
            return
        }

        if (null != mDownloadingDelay) {
            mDownloadingDelay!!.cancel()
        }

        ensureDownloadedBuilder()

        val finish = info.state == DownloadInfo.STATE_FINISH
        val gid = info.gid
        val index = sItemStateArray.indexOfKey(gid)
        if (index < 0) { // Not contain
            sItemStateArray.put(gid, finish)
            sItemTitleArray.put(gid, EhUtils.getSuitableTitle(info))
            sDownloadedCount++
            if (finish) {
                sFinishedCount++
            } else {
                sFailedCount++
            }
        } else { // Contain
            val oldFinish = sItemStateArray.valueAt(index)
            sItemStateArray.put(gid, finish)
            sItemTitleArray.put(gid, EhUtils.getSuitableTitle(info))
            if (oldFinish && !finish) {
                sFinishedCount--
                sFailedCount++
            } else if (!oldFinish && finish) {
                sFinishedCount++
                sFailedCount--
            }
        }

        val text: String
        val needStyle: Boolean
        if (sFinishedCount != 0 && sFailedCount == 0) {
            if (sFinishedCount == 1) {
                if (sItemTitleArray.size() >= 1) {
                    text = getString(
                        R.string.stat_download_done_line_succeeded,
                        sItemTitleArray.valueAt(0)
                    )
                } else {
                    Log.d("TAG", "WTF, sItemTitleArray is null")
                    text = getString(R.string.error_unknown)
                }
                needStyle = false
            } else {
                text = getString(R.string.stat_download_done_text_succeeded, sFinishedCount)
                needStyle = true
            }
        } else if (sFinishedCount == 0 && sFailedCount != 0) {
            if (sFailedCount == 1) {
                if (sItemTitleArray.size() >= 1) {
                    text = getString(
                        R.string.stat_download_done_line_failed,
                        sItemTitleArray.valueAt(0)
                    )
                } else {
                    Log.d("TAG", "WTF, sItemTitleArray is null")
                    text = getString(R.string.error_unknown)
                }
                needStyle = false
            } else {
                text = getString(R.string.stat_download_done_text_failed, sFailedCount)
                needStyle = true
            }
        } else {
            text = getString(R.string.stat_download_done_text_mix, sFinishedCount, sFailedCount)
            needStyle = true
        }

        val style: NotificationCompat.InboxStyle?
        if (needStyle) {
            style = NotificationCompat.InboxStyle()
            style.setBigContentTitle(getString(R.string.stat_download_done_title))
            val stateArray = sItemStateArray
            val titleArray = sItemTitleArray
            var i = 0
            val n = stateArray.size()
            while (i < n) {
                val id = stateArray.keyAt(i)
                val fin = stateArray.valueAt(i)
                val title = titleArray[id]
                if (title == null) {
                    i++
                    continue
                }
                style.addLine(
                    getString(
                        if (fin) R.string.stat_download_done_line_succeeded else R.string.stat_download_done_line_failed,
                        title
                    )
                )
                i++
            }
        } else {
            style = null
        }

        mDownloadedBuilder!!.setContentText(text)
            .setStyle(style)
            .setWhen(System.currentTimeMillis())
            .setNumber(sDownloadedCount)

        mDownloadedDelay!!.show()

        checkStopSelf()
    }

    override fun onCancel(info: DownloadInfo) {
        if (mNotifyManager == null) {
            return
        }

        if (null != mDownloadingDelay) {
            mDownloadingDelay!!.cancel()
        }

        checkStopSelf()
    }

    private fun checkStopSelf() {
        if (mDownloadManager == null || mDownloadManager!!.isIdle) {
//            stopForeground(true);
            stopSelf()
        }
    }

    // TODO Include all notification in one delay
    // Avoid frequent notification
    private class NotificationDelay(
        private var mService: Service?, private val mNotifyManager: NotificationManager?,
        private val mBuilder: NotificationCompat.Builder, private val mId: Int
    ) : Runnable {
        @IntDef(OPS_NOTIFY, OPS_CANCEL, OPS_START_FOREGROUND)
        @Retention(AnnotationRetention.SOURCE)
        private annotation class Ops

        private var mLastTime: Long = 0
        private var mPosted = false

        // false for show, true for cancel
        @Ops
        private var mOps = 0

        fun release() {
            mService = null
        }

        fun show() {
            if (mPosted) {
                mOps = OPS_NOTIFY
            } else {
                val now = SystemClock.currentThreadTimeMillis()
                if (now - mLastTime > DELAY) {
                    // Wait long enough, do it now
                    mNotifyManager!!.notify(mId, mBuilder.build())
                } else {
                    // Too quick, post delay
                    mOps = OPS_NOTIFY
                    mPosted = true
                    SimpleHandler.getInstance().postDelayed(this, DELAY)
                }
                mLastTime = now
            }
        }

        fun cancel() {
            if (mPosted) {
                mOps = OPS_CANCEL
            } else {
                val now = SystemClock.currentThreadTimeMillis()
                if (now - mLastTime > DELAY) {
                    // Wait long enough, do it now
                    mNotifyManager!!.cancel(mId)
                } else {
                    // Too quick, post delay
                    mOps = OPS_CANCEL
                    mPosted = true
                    SimpleHandler.getInstance().postDelayed(this, DELAY)
                }
            }
        }

        fun startForeground() {
            if (mPosted) {
                mOps = OPS_START_FOREGROUND
            } else {
                val now = SystemClock.currentThreadTimeMillis()
                if (now - mLastTime > DELAY) {
                    // Wait long enough, do it now
                    if (mService != null) {
                        mService!!.startForeground(mId, mBuilder.build())
                    }
                } else {
                    // Too quick, post delay
                    mOps = OPS_START_FOREGROUND
                    mPosted = true
                    SimpleHandler.getInstance().postDelayed(this, DELAY)
                }
            }
        }

        override fun run() {
            mPosted = false
            when (mOps) {
                OPS_NOTIFY -> mNotifyManager!!.notify(mId, mBuilder.build())
                OPS_CANCEL -> mNotifyManager!!.cancel(mId)
                OPS_START_FOREGROUND -> if (mService != null) {
                    mService!!.startForeground(mId, mBuilder.build())
                }
            }
        }

        companion object {
            private const val OPS_NOTIFY = 0
            private const val OPS_CANCEL = 1
            private const val OPS_START_FOREGROUND = 2

            private const val DELAY: Long = 1000 // 1s
        }
    }

    companion object {
        const val ACTION_START: String = "start"
        const val ACTION_START_RANGE: String = "start_range"
        const val ACTION_START_ALL: String = "start_all"
        const val ACTION_STOP: String = "stop"
        const val ACTION_STOP_RANGE: String = "stop_range"
        const val ACTION_STOP_CURRENT: String = "stop_current"
        const val ACTION_STOP_ALL: String = "stop_all"
        const val ACTION_DELETE: String = "delete"
        const val ACTION_DELETE_RANGE: String = "delete_range"
        const val ACTION_CLEAR: String = "clear"

        const val KEY_GALLERY_INFO: String = "gallery_info"
        const val KEY_LABEL: String = "label"
        const val KEY_GID: String = "gid"
        const val KEY_GID_LIST: String = "gid_list"

        private const val ID_DOWNLOADING = 1
        private const val ID_DOWNLOADED = 2
        private const val ID_509 = 3

        private val sItemStateArray =
            SparseJBArray()
        private val sItemTitleArray =
            SparseJLArray<String>()

        private var sFailedCount = 0
        private var sFinishedCount = 0
        private var sDownloadedCount = 0

        fun clear() {
            sFailedCount = 0
            sFinishedCount = 0
            sDownloadedCount = 0
            sItemStateArray.clear()
            sItemTitleArray.clear()
        }
    }
}
