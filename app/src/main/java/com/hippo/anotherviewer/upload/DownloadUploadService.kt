/*
 * Copyright 2026 Hippo Seven
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
package com.hippo.anotherviewer.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import com.hippo.anotherviewer.R
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.client.data.GalleryInfo
import com.hippo.anotherviewer.dao.DownloadInfo
import com.hippo.anotherviewer.spider.SpiderDen
import com.hippo.anotherviewer.spider.SpiderInfo
import com.hippo.anotherviewer.ui.MainActivity
import com.hippo.anotherviewer.webui.WebUiConfig
import com.hippo.anotherviewer.webui.WebUiSettings
import com.hippo.anotherviewer.webui.WebUiUploadClient
import com.hippo.anotherviewer.webui.WebUiUploadModels
import com.hippo.unifile.UniFile
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前台服务：把本地已下载漫画推送到 WebUI 服务器（task 3 push-of-local-downloads）。
 * 遍历全部 FINISHED 下载（或 gid 子集），逐本：
 *
 * 1. 读 `.anotherviewer`（[SpiderInfo.getSpiderInfo]）拿页数；
 * 2. [WebUiUploadClient.uploadInit]（force=false —— 服务器已存在的 gid 跳过，绝不覆盖）；
 * 3. 逐页 [SpiderDen.findImageFile] 读取 `%08d.<ext>` 并 [WebUiUploadClient.uploadPage]；
 * 4. [WebUiUploadClient.uploadComplete] 标记服务器行 FINISHED。
 *
 * 参照 [com.hippo.anotherviewer.smb.SmbUploadService]：单线程 executor + 前台通知
 * （进度/取消）+ WakeLock。WebUI 未配置时仅 Toast 提示后结束。
 */
class DownloadUploadService : Service() {

    private lateinit var mNotifyManager: NotificationManager
    private lateinit var mBuilder: NotificationCompat.Builder
    private var mExecutor: ExecutorService? = null
    private var mWakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var mCancelled = false

    override fun onCreate() {
        super.onCreate()
        mNotifyManager = getSystemService(NotificationManager::class.java)
        mExecutor = Executors.newSingleThreadExecutor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.webui_push_service_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.webui_push_service_desc)
            mNotifyManager.createNotificationChannel(channel)
        }

        mBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(getString(R.string.settings_download_webui_push_syncing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val mainIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mBuilder.setContentIntent(pi)

        val cancelIntent = Intent(this, DownloadUploadService::class.java)
        cancelIntent.action = ACTION_CANCEL
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mBuilder.addAction(
            android.R.drawable.ic_delete,
            getString(android.R.string.cancel), cancelPi
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_CANCEL == intent.action) {
            mCancelled = true
            stopSelf()
            return START_NOT_STICKY
        }

        if (sRunning.compareAndSet(false, true)) {
            mCancelled = false
            startForeground(NOTIFICATION_ID, mBuilder.build())
            acquireWakeLock()
            val gids = intent?.getLongArrayExtra(EXTRA_GIDS)
            mExecutor?.execute { doPush(gids) }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun doPush(gids: LongArray?) {
        val config = WebUiSettings(this).loadConfig()
        if (config == null) {
            Toast.makeText(
                this,
                R.string.settings_download_webui_push_not_configured,
                Toast.LENGTH_LONG
            ).show()
            finish(null)
            return
        }

        val downloadManager = SiteApplication.getDownloadManager(this)
        val targets = downloadManager.getAllDownloadInfoList().filter {
            it.state == DownloadInfo.STATE_FINISH && (gids == null || gids.contains(it.gid))
        }
        if (targets.isEmpty()) {
            finish(intArrayOf(0, 0, 0))
            return
        }

        var success = 0
        var skipped = 0
        var fail = 0
        for ((index, info) in targets.withIndex()) {
            if (mCancelled) break
            updateNotification(index + 1, targets.size, getString(R.string.settings_download_webui_push_syncing))
            when (pushOne(config, info)) {
                0 -> success++
                1 -> skipped++
                else -> fail++
            }
        }

        finish(intArrayOf(success, skipped, fail))
    }

    /** 单本推送：0 = 成功，1 = 跳过（服务器已存在该 gid），2 = 失败。 */
    private fun pushOne(config: WebUiConfig, info: GalleryInfo): Int {
        val spiderInfo = SpiderInfo.getSpiderInfo(info)
        if (spiderInfo == null || spiderInfo.gid != info.gid || spiderInfo.pages <= 0) {
            return 2
        }
        val dir = SpiderDen.getExistingGalleryDownloadDir(info) ?: return 2

        val request = WebUiUploadModels.UploadInitRequest().apply {
            token = spiderInfo.token
            title = info.title
            titleJpn = info.titleJpn
            thumb = info.thumb
            category = info.category
            uploader = info.uploader
            rating = info.rating
            simpleTags = info.simpleTags?.joinToString(", ")
            pages = spiderInfo.pages
            force = false
        }

        val initResponse = try {
            WebUiUploadClient.uploadInit(config, info.gid, request)
        } catch (e: IOException) {
            return 2
        }
        if (!initResponse.success) {
            // 服务器已拥有该 gid（非 force 冲突）：跳过，绝不覆盖服务器下载。
            return 1
        }

        try {
            for (page in 1..spiderInfo.pages) {
                if (mCancelled) return 2
                val file = SpiderDen.findImageFile(dir, page - 1)
                if (file == null || !WebUiUploadClient.uploadPage(config, info.gid, page, file)) {
                    return 2
                }
            }
            if (!WebUiUploadClient.uploadComplete(config, info.gid, spiderInfo.pages)) {
                return 2
            }
        } catch (e: IOException) {
            return 2
        }
        return 0
    }

    private fun updateNotification(current: Int, total: Int, text: String) {
        mBuilder.setProgress(total, current, false)
            .setContentText(String.format(Locale.US, "%s (%d/%d)", text, current, total))
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build())
    }

    private fun finish(result: IntArray?) {
        releaseWakeLock()
        sRunning.set(false)

        if (result != null) {
            mBuilder.setProgress(0, 0, false)
                .setContentText(
                    getString(
                        R.string.settings_download_webui_push_done,
                        result[0], result[1], result[2]
                    )
                )
                .setOngoing(false)
                .setAutoCancel(true)
            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build())
        } else {
            mNotifyManager.cancel(NOTIFICATION_ID)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anotherviewer:webui_push")
        mWakeLock?.acquire(30 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        if (mWakeLock != null && mWakeLock!!.isHeld) {
            mWakeLock!!.release()
            mWakeLock = null
        }
    }

    @Nullable
    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        mCancelled = true
        releaseWakeLock()
        mExecutor?.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "webui_push"
        private const val NOTIFICATION_ID = 0x574255
        /** 菜单入口（DownloadsScene）直接以该 action 启动服务。 */
        const val ACTION_PUSH_ALL = "com.hippo.anotherviewer.upload.PUSH_ALL"
        private const val ACTION_PUSH_GIDS = "com.hippo.anotherviewer.upload.PUSH_GIDS"
        private const val ACTION_CANCEL = "com.hippo.anotherviewer.upload.PUSH_CANCEL"
        private const val EXTRA_GIDS = "gids"

        private val sRunning = AtomicBoolean(false)

        fun isRunning(): Boolean = sRunning.get()

        /** 启动推送：gids 为空 → 全部 FINISHED 下载；否则只推指定 gid。 */
        fun start(context: Context, gids: LongArray?) {
            val intent = Intent(context, DownloadUploadService::class.java)
            if (gids != null && gids.isNotEmpty()) {
                intent.action = ACTION_PUSH_GIDS
                intent.putExtra(EXTRA_GIDS, gids)
            } else {
                intent.action = ACTION_PUSH_ALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, DownloadUploadService::class.java)
            intent.action = ACTION_CANCEL
            context.startService(intent)
        }
    }
}
