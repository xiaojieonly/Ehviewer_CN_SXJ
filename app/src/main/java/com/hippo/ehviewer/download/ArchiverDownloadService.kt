package com.hippo.ehviewer.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hippo.ehviewer.R
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene
import com.hippo.lib.yorozuya.FileUtils
import com.hippo.scene.StageActivity
import com.hippo.util.ReadableTime

/**
 * 归档下载轻量前台服务，仅用于保活与通知进度。
 */
class ArchiverDownloadService : Service() {

    private var notificationManager: NotificationManager? = null
    private var channelId: String? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        channelId = packageName + ".archiver_download"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.archiver_download_service_label),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelfIfNeeded()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                val gid = intent.getLongExtra(EXTRA_GID, -1L)
                if (gid >= 0) {
                    ArchiverDownloader.getInstance(this)?.pause(gid)
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                val gid = intent.getLongExtra(EXTRA_GID, -1L)
                if (gid >= 0) {
                    ArchiverDownloader.getInstance(this)?.resume(gid)
                }
                return START_STICKY
            }
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val gid = intent.getLongExtra(EXTRA_GID, -1L)
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: ""
        val downloaded = intent.getLongExtra(EXTRA_DOWNLOADED, 0L)
        val total = intent.getLongExtra(EXTRA_TOTAL, 0L)
        val speed = intent.getLongExtra(EXTRA_SPEED, 0L)
        val remaining = intent.getLongExtra(EXTRA_REMAINING, -1L)
        val paused = intent.getBooleanExtra(EXTRA_PAUSED, false)
        val notification = buildNotification(
            title, gid, token, downloaded, total, speed, remaining, paused
        )
        if (paused) {
            showPausedNotification(notification)
            return START_NOT_STICKY
        }
        if (!foregroundStarted) {
            startForegroundCompat(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showPausedNotification(notification: Notification) {
        if (!foregroundStarted) {
            startForegroundCompat(NOTIFICATION_ID, notification)
            foregroundStarted = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        foregroundStarted = false
        notificationManager?.notify(NOTIFICATION_ID, notification)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        gid: Long,
        token: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        remaining: Long,
        paused: Boolean
    ): Notification {
        val contentTitle = title.ifEmpty {
            getString(R.string.archiver_download_service_label)
        }
        val builder = NotificationCompat.Builder(this, channelId!!)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(contentTitle)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
        if (gid >= 0) {
            val args = Bundle().apply {
                putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN)
                putLong(GalleryDetailScene.KEY_GID, gid)
                putString(GalleryDetailScene.KEY_TOKEN, token)
            }
            val activityIntent = Intent(this, MainActivity::class.java).apply {
                action = StageActivity.ACTION_START_SCENE
                putExtra(StageActivity.KEY_SCENE_NAME, GalleryDetailScene::class.java.name)
                putExtra(StageActivity.KEY_SCENE_ARGS, args)
            }
            val contentIntent = PendingIntent.getActivity(
                this,
                gid.toInt(),
                activityIntent,
                PENDING_INTENT_FLAGS
            )
            builder.setContentIntent(contentIntent)
            if (paused) {
                builder.addAction(
                    R.drawable.ic_play_x24,
                    getString(R.string.archiver_download_resume),
                    serviceActionIntent(ACTION_RESUME, gid, REQUEST_RESUME)
                )
            } else {
                builder.addAction(
                    R.drawable.ic_pause_x24,
                    getString(R.string.archiver_download_pause),
                    serviceActionIntent(ACTION_PAUSE, gid, REQUEST_PAUSE)
                )
            }
        }
        if (paused) {
            builder.setContentText(getString(R.string.archiver_download_paused))
        } else if (speed > 0 || downloaded > 0) {
            builder.setContentText(formatSpeedText(speed, remaining))
        }
        if (total > 0) {
            val progress = minOf(100L, downloaded * 100L / total).toInt()
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, !paused)
        }
        return builder.build()
    }

    private fun formatSpeedText(speed: Long, remaining: Long): String {
        val safeSpeed = if (speed < 0) 0L else speed
        val text = FileUtils.humanReadableByteCount(safeSpeed, false) + "/S"
        return if (remaining >= 0) {
            getString(
                R.string.download_speed_text_2,
                text,
                ReadableTime.getShortTimeInterval(remaining)
            )
        } else {
            getString(R.string.download_speed_text, text)
        }
    }

    private fun serviceActionIntent(action: String, gid: Long, requestCode: Int): PendingIntent {
        val intent = Intent(this, ArchiverDownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_GID, gid)
        }
        return PendingIntent.getService(this, requestCode, intent, PENDING_INTENT_FLAGS)
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    private fun stopSelfIfNeeded() {
        if (!foregroundStarted) {
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START = "archiver_download_start"
        const val ACTION_UPDATE = "archiver_download_update"
        const val ACTION_STOP = "archiver_download_stop"
        const val ACTION_PAUSE = "archiver_download_pause"
        const val ACTION_RESUME = "archiver_download_resume"

        const val EXTRA_TITLE = "title"
        const val EXTRA_GID = "gid"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_DOWNLOADED = "downloaded"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_PAUSED = "paused"

        private const val NOTIFICATION_ID = 0x41524348
        private const val REQUEST_PAUSE = 0x41524349
        private const val REQUEST_RESUME = 0x4152434A
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        @JvmStatic
        fun start(context: Context, title: String?, gid: Long, token: String?) {
            val intent = Intent(context, ArchiverDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title ?: "")
                putExtra(EXTRA_GID, gid)
                putExtra(EXTRA_TOKEN, token ?: "")
            }
            startServiceCompat(context, intent)
        }

        @JvmStatic
        fun updateProgress(
            context: Context,
            title: String?,
            gid: Long,
            token: String?,
            downloaded: Long,
            total: Long,
            speed: Long,
            remaining: Long,
            paused: Boolean
        ) {
            val intent = Intent(context, ArchiverDownloadService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title ?: "")
                putExtra(EXTRA_GID, gid)
                putExtra(EXTRA_TOKEN, token ?: "")
                putExtra(EXTRA_DOWNLOADED, downloaded)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_SPEED, speed)
                putExtra(EXTRA_REMAINING, remaining)
                putExtra(EXTRA_PAUSED, paused)
            }
            startServiceCompat(context, intent)
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, ArchiverDownloadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
