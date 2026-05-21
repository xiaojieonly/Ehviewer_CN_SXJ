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
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.util.MiuiOptimizationHelper
import com.hippo.ehviewer.network.NetworkLogger
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

    // WakeLock 用于防止CPU被限制（针对后台下载优化）
    private var mWakeLock: PowerManager.WakeLock? = null
    
    // WifiLock 用于保持WiFi高性能模式（HyperOS/Android 14+ 后台WiFi会被降速）
    private var mWifiLock: WifiManager.WifiLock? = null
    
    // 网络回调用于监听网络状态（针对小米系统优化）
    private var mNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var mConnectivityManager: ConnectivityManager? = null
    
    // 定时刷新锁的 Runnable - 每5分钟重新获取锁，防止WakeLock超时（10分钟）后失效
    private val mLockRefreshRunnable = Runnable { refreshLocks() }
    private var mLockRefreshActive = false

    private var CHANNEL_ID: String? = null

    override fun onCreate() {
        super.onCreate()

        // 记录设备信息用于调试
        MiuiOptimizationHelper.logDeviceInfo()

        CHANNEL_ID = "$packageName.download"
        mNotifyManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // 根据设备类型动态调整通知优先级
        val notificationImportance = MiuiOptimizationHelper.getRecommendedNotificationImportance()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                getString(R.string.download_service),
                notificationImportance
            ).apply {
                // 针对高版本Android和小米/HyperOS系统的优化
                if (MiuiOptimizationHelper.needsAggressiveOptimization()) {
                    setShowBadge(true)
                    enableVibration(false) // 避免频繁振动
                    setSound(null, null) // 避免频繁提示音
                    description = "后台下载服务 - 请勿限制后台运行"
                    // HyperOS/Android 14+ 绕过省电限制
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setBypassDnd(true)
                    }
                }
            }
            mNotifyManager!!.createNotificationChannel(channel)
            
            Log.i(TAG, "Created notification channel with importance: $notificationImportance")
        }
        
        // 初始化 WakeLock（用于防止CPU被限制）
        initWakeLock()
        
        // 初始化 WifiLock（用于防止后台WiFi降速，HyperOS/Android 14+）
        initWifiLock()
        
        // 初始化网络监听（针对小米系统优化）
        initNetworkCallback()
        
        mDownloadManager = EhApplication.getDownloadManager(applicationContext)
        mDownloadManager!!.setDownloadListener(this)

        // 如果启动时已经有任务，立即提升为前台以防被系统回收
        if (mDownloadManager != null && mDownloadManager!!.hasActiveDownload()) {
            ensureDownloadingBuilder()
            mDownloadingDelay!!.startForeground()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 停止锁刷新
        stopLockRefresh()
        
        // 释放 WakeLock
        releaseWakeLock()
        
        // 释放 WifiLock
        releaseWifiLock()
        
        // 释放网络监听
        releaseNetworkCallback()

        mNotifyManager = null
        if (mDownloadManager != null) {
            // 只在没有活跃下载时才清除监听器
            // 否则 START_STICKY 重启后需要重新设置监听
            if (!mDownloadManager!!.hasActiveDownload()) {
                mDownloadManager!!.setDownloadListener(null)
                mDownloadManager = null
            }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务移除应用时，主动停止下载并关闭前台服务，避免服务残留。
        try {
            mDownloadManager?.stopAllDownload()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop downloads on task removed", e)
            NetworkLogger.logError("Failed to stop downloads on task removed", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopLockRefresh()
        releaseWakeLock()
        releaseWifiLock()
        releaseNetworkCallback()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (shouldStartForegroundImmediately(intent)) {
            ensureDownloadingBuilder()
            mDownloadingBuilder!!
                .setContentTitle(getString(R.string.download_service))
                .setContentText(getString(R.string.preparing_download))
                .setContentInfo(null)
                .setProgress(0, 0, true)
            mDownloadingDelay!!.startForeground()
        }

        try {
            if (intent != null) {
                handleIntent(intent)
            }
        } catch (_: NullPointerException) {
        }

        // 前台保活兜底：如果仍有任务且尚未在前台，确保前台通知存在
        // 同时重新注册 DownloadManager 监听（START_STICKY 重启后 onDestroy 可能清掉了）
        if (mDownloadManager == null) {
            mDownloadManager = EhApplication.getDownloadManager(applicationContext)
        }
        if (mDownloadManager != null && mDownloadManager!!.hasActiveDownload()) {
            // 确保监听器已注册（Service 重启后可能丢失）
            mDownloadManager!!.setDownloadListener(this)
            ensureDownloadingBuilder()
            mDownloadingDelay!!.startForeground()
        }
        return START_STICKY
    }

    private fun shouldStartForegroundImmediately(intent: Intent?): Boolean {
        return when (intent?.action) {
            ACTION_START,
            ACTION_START_RANGE,
            ACTION_START_ALL -> true
            else -> false
        }
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
        bundle.putString("action", "clear_download_service")
        val activityIntent = Intent(this, MainActivity::class.java)
        activityIntent.setAction(StageActivity.ACTION_START_SCENE)
        activityIntent.putExtra(StageActivity.KEY_SCENE_NAME, "com.hippo.ehviewer.ui.scene.download.DownloadsScene")
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
        
        // 获取 WakeLock 防止后台下载被限制
        acquireWakeLock()
        
        // 获取 WifiLock 防止后台WiFi降速
        acquireWifiLock()
        
        // 启动锁定时刷新（每5分钟刷新一次，防止超时失效）
        startLockRefresh()

        ensureDownloadingBuilder()

        val bundle = Bundle()
        bundle.putLong("gid", info.gid)
        val activityIntent = Intent(this, MainActivity::class.java)
        activityIntent.setAction(StageActivity.ACTION_START_SCENE)
        activityIntent.putExtra(StageActivity.KEY_SCENE_NAME, "com.hippo.ehviewer.ui.scene.download.DownloadsScene")
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
            // 停止锁刷新
            stopLockRefresh()
            // 释放 WakeLock
            releaseWakeLock()
            // 释放 WifiLock
            releaseWifiLock()
            stopSelf()
        }
    }
    
    /**
     * 初始化 WakeLock
     * 用于防止后台下载时CPU被限制，特别是针对Android 14+和HyperOS
     */
    @SuppressLint("WakelockTimeout")
    private fun initWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            mWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "EhViewer:DownloadWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
            
            Log.i(TAG, "WakeLock initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WakeLock", e)
        }
    }
    
    /**
     * 获取/刷新 WakeLock
     * 核心策略：先释放旧的再重新获取，确保连续保护
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (mWakeLock == null) return
            
            // 如果已持有，先释放再重新获取（刷新倒计时）
            if (mWakeLock!!.isHeld) {
                mWakeLock!!.release()
                Log.d(TAG, "WakeLock released for refresh")
            }
            
            if (MiuiOptimizationHelper.needsAggressiveOptimization() ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // 使用 acquire() 不加超时，由 refresh 机制保证不会永久持有
                mWakeLock!!.acquire()
                Log.i(TAG, "WakeLock acquired (no timeout, refreshed periodically)")
                NetworkLogger.logBackground("WakeLock acquired (aggressive mode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }
    
    /**
     * 释放 WakeLock
     */
    private fun releaseWakeLock() {
        try {
            if (mWakeLock != null && mWakeLock!!.isHeld) {
                mWakeLock!!.release()
                Log.i(TAG, "WakeLock released")
                NetworkLogger.logBackground("WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }
    
    /**
     * 初始化 WifiLock
     * HyperOS / MIUI 会在后台降低WiFi性能，WifiLock可保持WiFi高性能模式
     */
    @SuppressLint("WakelockTimeout")
    private fun initWifiLock() {
        // 所有 Android 10+ 设备都需要 WifiLock 防止后台WiFi降速
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                mWifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "EhViewer:DownloadWifiLock"
                ).apply {
                    setReferenceCounted(false)
                }
                Log.i(TAG, "WifiLock initialized (WIFI_MODE_FULL_HIGH_PERF)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WifiLock", e)
        }
    }
    
    /**
     * 获取/刷新 WifiLock
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWifiLock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        
        try {
            if (mWifiLock == null) {
                initWifiLock()  // 如果未初始化，尝试重新初始化
            }
            if (mWifiLock != null && !mWifiLock!!.isHeld) {
                mWifiLock!!.acquire()
                Log.i(TAG, "WifiLock acquired")
                NetworkLogger.logBackground("WifiLock acquired (WIFI_MODE_FULL_HIGH_PERF)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WifiLock", e)
        }
    }
    
    /**
     * 释放 WifiLock
     */
    private fun releaseWifiLock() {
        try {
            if (mWifiLock != null && mWifiLock!!.isHeld) {
                mWifiLock!!.release()
                Log.i(TAG, "WifiLock released")
                NetworkLogger.logBackground("WifiLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WifiLock", e)
        }
    }
    
    /**
     * 启动锁定时刷新
     * 每5分钟刷新一次 WakeLock 和 WifiLock，防止超时或系统回收
     */
    private fun startLockRefresh() {
        if (mLockRefreshActive) return
        mLockRefreshActive = true
        SimpleHandler.getInstance().postDelayed(mLockRefreshRunnable, 5 * 60 * 1000L)
        Log.i(TAG, "Lock refresh started (every 5 min)")
        NetworkLogger.logBackground("Lock refresh timer started (interval=5min)")
    }
    
    /**
     * 停止锁定时刷新
     */
    private fun stopLockRefresh() {
        mLockRefreshActive = false
        SimpleHandler.getInstance().removeCallbacks(mLockRefreshRunnable)
        Log.i(TAG, "Lock refresh stopped")
        if (NetworkLogger.enabled) {
            NetworkLogger.logBackground("Lock refresh timer stopped")
        }
    }
    
    /**
     * 刷新锁 - 由定时器调用
     */
    private fun refreshLocks() {
        if (!mLockRefreshActive) return
        
        try {
            // 检查是否还有活跃下载
            if (mDownloadManager == null || !mDownloadManager!!.hasActiveDownload()) {
                stopLockRefresh()
                releaseWakeLock()
                releaseWifiLock()
                return
            }
            
            // 刷新 WakeLock（释放旧的+重新获取）
            acquireWakeLock()
            // 刷新 WifiLock
            acquireWifiLock()
            
            Log.d(TAG, "Locks refreshed, next refresh in 5 min")
            NetworkLogger.logBackground("Locks refreshed (WakeLock+WifiLock), next in 5min")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh locks", e)
        }
        
        // 安排下一次刷新
        if (mLockRefreshActive) {
            SimpleHandler.getInstance().postDelayed(mLockRefreshRunnable, 5 * 60 * 1000L)
        }
    }
    
    /**
     * 初始化网络回调
     * 监听网络切换（WiFi↔移动数据、VPN 连接/断开），触发连接池刷新和 DNS 重建
     */
    private fun initNetworkCallback() {
        try {
            mConnectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

            mNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                /** 记录上次的网络对象，用于检测网络是否真的变化了 */
                private var lastNetwork: Network? = null

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.i(TAG, "Network available: $network")
                    NetworkLogger.logBackground("Network available: $network")

                    // 新的网络接口出现（包括 VPN），刷新连接池
                    if (lastNetwork != null && lastNetwork != network) {
                        onNetworkSwitched(network, "available")
                    }
                    lastNetwork = network
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.w(TAG, "Network lost: $network")
                    NetworkLogger.logBackground("Network lost: $network")

                    // 网络丢失时刷新连接，确保旧连接被清除
                    flushNetworkConnections("lost")
                    lastNetwork = null
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val isUnmetered = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                    )
                    val hasInternet = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                    Log.i(TAG, "Network caps changed: $network, unmetered=$isUnmetered, internet=$hasInternet")
                    NetworkLogger.logBackground(
                        "Network caps changed: $network, unmetered=$isUnmetered, internet=$hasInternet"
                    )

                    // 如果网络对象变了（例如 VPN 接管），刷新连接
                    if (lastNetwork != null && lastNetwork != network) {
                        onNetworkSwitched(network, "capabilities changed")
                    }
                    lastNetwork = network
                }

                private fun onNetworkSwitched(network: Network, reason: String) {
                    Log.i(TAG, "Network switched ($reason), flushing connections")
                    NetworkLogger.logBackground("Network switched ($reason) - flushing OkHttp pools + DNS cache")
                    flushNetworkConnections(reason)
                }
            }

            // 注册网络回调 - 不限制 transport 类型，所有网络变化都监听
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            mConnectivityManager?.registerNetworkCallback(networkRequest, mNetworkCallback!!)
            Log.i(TAG, "Network callback registered (all transports)")
            NetworkLogger.logBackground("Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize network callback", e)
        }
    }

    /**
     * 刷新所有网络连接
     * VPN 切换后调用，清除旧网络接口上的 TCP 连接和 DNS 缓存
     */
    private fun flushNetworkConnections(reason: String) {
        try {
            EhApplication.onNetworkChanged()

            // 重新绑定 WifiLock 到新网络（如果需要）
            if (mWifiLock?.isHeld == true) {
                mWifiLock!!.release()
                mWifiLock!!.acquire()
                Log.i(TAG, "WifiLock rebound after network change ($reason)")
                NetworkLogger.logBackground("WifiLock rebound after network change ($reason)")
            }

            // 通知 SpiderQueen 网络已切换，保活需要重新开始
            SpiderQueen.notifyNetworkChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush network connections", e)
            NetworkLogger.logError("Failed to flush network connections: $reason", e)
        }
    }

    /**
     * 释放网络回调
     */
    private fun releaseNetworkCallback() {
        try {
            if (mNetworkCallback != null && mConnectivityManager != null) {
                mConnectivityManager?.unregisterNetworkCallback(mNetworkCallback!!)
                mNetworkCallback = null
                mConnectivityManager = null
                Log.i(TAG, "Network callback unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            mService!!.startForeground(
                                mId,
                                mBuilder.build(),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            )
                        } else {
                            mService!!.startForeground(mId, mBuilder.build())
                        }
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mService!!.startForeground(
                            mId,
                            mBuilder.build(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        mService!!.startForeground(mId, mBuilder.build())
                    }
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

        private const val TAG = "DownloadService"
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

        private fun startServiceCompat(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldUseForegroundService(intent)) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DownloadService", e)
            }
        }

        private fun shouldUseForegroundService(intent: Intent): Boolean {
            return when (intent.action) {
                ACTION_START,
                ACTION_START_RANGE,
                ACTION_START_ALL -> true
                else -> false
            }
        }

        @JvmStatic
        fun ensureRunning(context: Context) {
            startServiceCompat(context, Intent(context, DownloadService::class.java))
        }

        @JvmStatic
        fun startDownload(context: Context, galleryInfo: GalleryInfo, label: String?) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(KEY_GALLERY_INFO, galleryInfo)
                .putExtra(KEY_LABEL, label)
            startServiceCompat(context, intent)
        }

        @JvmStatic
        fun startRangeDownload(context: Context, gidList: LongList) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START_RANGE)
                .putExtra(KEY_GID_LIST, gidList)
            startServiceCompat(context, intent)
        }

        @JvmStatic
        fun startAllDownloads(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START_ALL)
            startServiceCompat(context, intent)
        }

        @JvmStatic
        fun stopAllDownloads(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_STOP_ALL)
            startServiceCompat(context, intent)
        }

        fun clear() {
            sFailedCount = 0
            sFinishedCount = 0
            sDownloadedCount = 0
            sItemStateArray.clear()
            sItemTitleArray.clear()
        }
    }
}
