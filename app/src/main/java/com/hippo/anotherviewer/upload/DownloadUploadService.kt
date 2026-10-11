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
import android.util.Log
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import com.hippo.anotherviewer.R
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.client.PrivacyMask
import com.hippo.anotherviewer.client.data.GalleryInfo
import com.hippo.anotherviewer.dao.DownloadInfo
import com.hippo.anotherviewer.download.DownloadManager
import com.hippo.anotherviewer.spider.SpiderDen
import com.hippo.anotherviewer.spider.SpiderInfo
import com.hippo.anotherviewer.ui.MainActivity
import com.hippo.anotherviewer.webui.WebUiConfig
import com.hippo.anotherviewer.webui.WebUiSettings
import com.hippo.anotherviewer.webui.WebUiUploadClient
import com.hippo.anotherviewer.webui.WebUiUploadModels
import com.hippo.unifile.UniFile
import java.io.IOException
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前台服务：把本地已下载漫画推送到 WebUI 服务器（task 3 push-of-local-downloads）。
 * 遍历全部 FINISHED 下载，逐本：
 *
 * 1. 读 `.anotherviewer`（[SpiderInfo.getSpiderInfo]）拿页数；
 * 2. [WebUiUploadClient.uploadInit]（force=false；本设备此前推送过的 gid 若中途失败，
 *    读取 existingPages 后以 force=true 续传，服务器自己的下载仍跳过、绝不覆盖）；
 * 3. 逐页 [SpiderDen.findImageFile] 读取 `%08d.<ext>` 并 [WebUiUploadClient.uploadPage]；
 * 4. [WebUiUploadClient.uploadComplete] 标记服务器行 FINISHED。
 *
 * 参照 [com.hippo.anotherviewer.smb.SmbUploadService]：单线程 executor + 前台通知
 * （进度/取消）+ WakeLock。WebUI 未配置时仅 Toast 提示后结束。
 *
 * ## WP7：下载完成自动上传（精推 + 运行中追加合并）
 *
 * 除手动「推送全部」（[ACTION_PUSH_ALL]）外，新增 [ACTION_PUSH_GIDS] 精推入口：
 * 下载转 FINISH（DownloadManager 钩子）与周期对账（[UploadReconciler]）只把指定
 * gid 放进 [mPendingGids] 队列。worker 消费队列而不是拍快照：
 *
 * - 运行中再到的 Intent 只入队即返回，**绝不 stopSelf**——旧实现在 CAS 失败分支
 *   无条件 stopSelf，会误停正在跑的 worker；
 * - 队列排空后 [GRACE_MS] 锁内等待再查一次，闭合「worker 刚判空、入队还没落地」
 *   的竞态；
 * - 出队时经 `dm.getDownloadInfo(gid)` 复核存在且仍为 STATE_FINISH——入队后下载
 *   被删或重新下载回退的 gid 直接跳过；
 * - 外层 catch(Throwable) 兜底 finish：worker 任何异常都必须复位 sRunning，
 *   否则后续所有推送永久哑火（假死）。
 *
 * ## pushed_gids 断点续传判定与共存语义（A2）
 *
 * 本地集合（[PREFS_NAME]）只在「本设备曾推送过该 gid」时授权 force 续传。
 * 条目自 A2 起带时间戳（`"<gid>:<戳入时刻 ms>"`），仅 [PUSHED_GID_TTL_MS]
 * （7 天）内有效：过期条目、旧版无时间戳的裸 gid 条目、损坏条目一律按
 * 「未推送」处理——宁可走跳过分支也绝不 force 覆盖服务器行。这是有意的
 * 保守方向：TTL 过后最多损失一次断点续传机会（重新走 init→补页流程的
 * 跳过路径），不会误覆盖他人的下载行。
 *
 * 同一 gid 既被其他设备委托下载（经同步落成本机 FINISHED 行）、又在本机
 * 推送过时的共存行为：
 * - TTL 窗口内 force 续传会以本机内容刷新该服务器行；因 gid/token/页数
 *   相同、缺失页才补传，属收敛写而非破坏写；
 * - TTL 窗口外（或任何无法证明「服务器半成品源自本设备」的情形）一律
 *   跳过，满足类注释「绝不覆盖」承诺。
 */
class DownloadUploadService : Service() {

    private lateinit var mNotifyManager: NotificationManager
    private lateinit var mBuilder: NotificationCompat.Builder
    private var mExecutor: ExecutorService? = null
    private var mWakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var mCancelled = false

    /** WP7：待推送 gid 队列。onStartCommand（主线程）生产，worker 消费。 */
    private val mPendingGids = ConcurrentLinkedQueue<Long>()

    /**
     * 队列的去重镜像。ConcurrentLinkedQueue 本身不去重，而入队来源有多个
     * （完成钩子、对账、手动 PUSH_ALL 可能对同一 gid 重复触发），必须去重
     * 保证幂等。镜像访问一律持 [mQueueLock]。
     */
    private val mPendingMirror = HashSet<Long>()

    /** 保护 [mPendingMirror]；同时是 worker 队列判空后 GRACE 等待的监视器。 */
    private val mQueueLock = Object()

    /** 入队：去重后追加；已在排队的 gid 直接忽略（幂等）。 */
    private fun enqueueGids(gids: Collection<Long>) {
        if (gids.isEmpty()) {
            return
        }
        synchronized(mQueueLock) {
            for (gid in gids) {
                if (mPendingMirror.add(gid)) {
                    mPendingGids.add(gid)
                }
            }
            // worker 可能正处在队列判空后的 GRACE 等待里，叫醒它立即消化。
            mQueueLock.notifyAll()
        }
    }

    /** 出队：队列原子弹出 + 镜像移除。空返回 null。 */
    private fun pollNextGid(): Long? {
        val gid = mPendingGids.poll() ?: return null
        synchronized(mQueueLock) { mPendingMirror.remove(gid) }
        return gid
    }

    private fun pendingCount(): Int = mPendingGids.size

    private fun isPushed(gid: Long): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return isPushedEntryFresh(
            prefs.getStringSet(KEY_PUSHED_GIDS, null), gid, System.currentTimeMillis()
        )
    }

    private fun markPushed(gid: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // 写入时顺带剪枝：清掉过期/损坏条目，集合不随历史无限增长。
        val pushed = HashSet(prunePushedEntries(prefs.getStringSet(KEY_PUSHED_GIDS, null), now))
        if (pushed.add(encodePushedEntry(gid, now))) {
            prefs.edit().putStringSet(KEY_PUSHED_GIDS, pushed).apply()
        }
    }

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

        // 先入队再判定是否起 worker：即使 worker 已在跑，新 gid 也已进入
        // 队列，运行中的循环靠 GRACE 重查/下一轮 poll 消化，绝不丢活。
        when (intent?.action) {
            ACTION_PUSH_GIDS -> enqueueGids(gidsFromIntent(intent))
            // ACTION_PUSH_ALL 与 null action：全量扫描内存 FINISH 行
            else -> enqueueGids(scanFinishedGids(SiteApplication.getDownloadManager(this)))
        }

        if (sRunning.compareAndSet(false, true)) {
            mCancelled = false
            startForeground(NOTIFICATION_ID, mBuilder.build())
            acquireWakeLock()
            mExecutor?.execute { runPushLoop() }
        }
        // CAS 失败（已在运行）：入队即返回。旧实现此处无条件 stopSelf()，
        // 会把正在跑的 worker 拦腰停掉——彻底删除；收尾由 worker 自己负责。
        return START_NOT_STICKY
    }

    /** extras（ArrayList&lt;Long&gt;，Serializable 传输）还原 gid 列表；缺损坏一律按空表。 */
    private fun gidsFromIntent(intent: Intent): List<Long> {
        @Suppress("DEPRECATION")
        val raw = intent.getSerializableExtra(EXTRA_GIDS) as? List<*> ?: return emptyList()
        return raw.filterIsInstance<Long>()
    }

    /** 生产包装器：取应用级 DownloadManager（Robolectric 无 manifest 沙箱下会
     *  CCE，测试用可注入重载直接 new DownloadManager(context)）。 */
    private fun runPushLoop() {
        runPushLoop(SiteApplication.getDownloadManager(this))
    }

    private fun runPushLoop(dm: DownloadManager) {
        var result: IntArray? = null
        try {
            val config = WebUiSettings(this).loadConfig()
            if (config == null) {
                // 未配置只对手动 PUSH_ALL 有感知意义（自动钩子已被 isConfigured
                // 短路到不了这里），保留原 Toast 反馈；result=null → 通知移除。
                Toast.makeText(
                    this,
                    R.string.settings_download_webui_push_not_configured,
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            var success = 0
            var skipped = 0
            var fail = 0
            var processed = 0
            while (!mCancelled) {
                var gid = pollNextGid()
                if (gid == null) {
                    // 队列空：锁内等一小段 GRACE 再查一次，闭合「本 worker 刚
                    // 起来/CAS 失败路径刚入队、还没来得及 notify」的竞态。
                    synchronized(mQueueLock) {
                        if (mPendingGids.isEmpty()) {
                            mQueueLock.wait(GRACE_MS)
                        }
                    }
                    gid = pollNextGid() ?: break
                }
                val info = dm.getDownloadInfo(gid)
                if (info == null || info.state != DownloadInfo.STATE_FINISH) {
                    // 入队后被删除，或重新下载回退到 WAIT/DOWNLOAD：跳过，
                    // 绝不推非 FINISH 行。
                    continue
                }
                processed++
                // 按剩余队列数更新通知：total = 已处理 + 尚未消费的排队数，
                // 运行中追加会让 total 动态增长。
                updateNotification(
                    processed, processed + pendingCount(),
                    getString(R.string.settings_download_webui_push_syncing)
                )
                when (pushOne(config, info)) {
                    0 -> success++
                    1 -> skipped++
                    else -> fail++
                }
            }
            result = intArrayOf(success, skipped, fail)
        } catch (e: InterruptedException) {
            // onDestroy 的 shutdownNow 打断 GRACE 等待：直接走 finally 复位
            // sRunning，不上报半截结果（result 保持 null，通知整体移除）。
        } catch (t: Throwable) {
            // 兜底：worker 任何异常都必须落到 finish，sRunning 卡死意味着
            // 之后所有自动/手动推送静默失效（比崩溃更糟的假死）。
            Log.e(TAG, "runPushLoop crashed", t)
        } finally {
            finish(result)
        }
    }

    /** 单本推送：0 = 成功，1 = 跳过（服务器已存在该 gid），2 = 失败。 */
    private fun pushOne(config: WebUiConfig, info: GalleryInfo): Int {
        val spiderInfo = SpiderInfo.getSpiderInfo(info)
        if (spiderInfo == null || spiderInfo.gid != info.gid || spiderInfo.pages <= 0) {
            return 2
        }
        val dir = SpiderDen.getExistingGalleryDownloadDir(info) ?: return 2

        // 写回保护：打码期间落库的 "#<gid>" 标题不上送（服务端也拒绝该形态标题）
        val maskedTitle = PrivacyMask.isMaskedTitle(info.title)
        val request = WebUiUploadModels.UploadInitRequest().apply {
            token = spiderInfo.token
            title = if (maskedTitle) null else info.title
            titleJpn = if (maskedTitle) null else info.titleJpn
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

        val existingPages: List<Int>
        when {
            initResponse.success -> {
                // 服务器新建该 gid 行：立即标记「本设备已推送」，此后中断可续传。
                markPushed(info.gid)
                existingPages = emptyList()
            }
            isPushed(info.gid) -> {
                // 本设备之前的推送中断（服务器行 state=2）：force 续传。服务器保留
                // 既有页文件，只补传缺失页。
                val resumed = try {
                    WebUiUploadClient.uploadInit(config, info.gid, request.apply { force = true })
                } catch (e: IOException) {
                    return 2
                }
                if (!resumed.success) return 2
                existingPages = resumed.existingPages ?: emptyList()
            }
            else -> {
                // 服务器已拥有该 gid（本设备未推送过）：跳过，绝不覆盖服务器下载。
                return 1
            }
        }

        try {
            for (page in UploadResume.missingPages(spiderInfo.pages, existingPages)) {
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
        private const val TAG = "DownloadUploadService"
        private const val CHANNEL_ID = "webui_push"
        private const val NOTIFICATION_ID = 0x574255
        /** 菜单入口（DownloadsScene）直接以该 action 启动服务。 */
        const val ACTION_PUSH_ALL = "com.hippo.anotherviewer.upload.PUSH_ALL"
        /**
         * WP7 精推 action：extras 只带指定 gid（下载完成钩子/周期对账用），
         * 避免全量扫描。消费端对每个 gid 复核 FINISH 态。
         */
        const val ACTION_PUSH_GIDS = "com.hippo.anotherviewer.upload.PUSH_GIDS"
        /** ArrayList&lt;Long&gt;，经 Serializable 传输（gid 列表规模小，无需 Parcel）。 */
        const val EXTRA_GIDS = "gids"
        private const val ACTION_CANCEL = "com.hippo.anotherviewer.upload.PUSH_CANCEL"
        /** 本设备已推送过的 gid 持久化集合（断点续传判定用）。 */
        private const val PREFS_NAME = "webui_pushed_gids"
        private const val KEY_PUSHED_GIDS = "pushed_gids"

        /** 队列判空后的 GRACE 重查窗口：闭合运行中追加（CAS 失败入队路径）的竞态。 */
        private const val GRACE_MS = 3000L

        /**
         * A2：pushed 条目的存活时长。超过即视为「未推送」，force 续传降级为
         * 保守跳过——本地集合不再无限期授权覆盖服务器下载行。
         */
        const val PUSHED_GID_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** 条目编码 `"<gid>:<戳入时刻 ms>"`；gid 纯数字，冒号无歧义。 */
        @JvmStatic
        fun encodePushedEntry(gid: Long, at: Long): String = "$gid:$at"

        /**
         * [gid] 是否存在未过期条目。旧版裸 gid（无 `:`）与时间戳损坏的条目
         * 一律按过期处理（保守：跳过而非 force）。同一 gid 多条目时任一鲜活
         * 即算推送过。
         */
        @JvmStatic
        @JvmOverloads
        fun isPushedEntryFresh(entries: Set<String>?, gid: Long, now: Long,
                               ttlMs: Long = PUSHED_GID_TTL_MS): Boolean {
            if (entries == null) return false
            val prefix = "$gid:"
            for (entry in entries) {
                if (!entry.startsWith(prefix)) continue
                val at = entry.substring(prefix.length).toLongOrNull() ?: continue
                if (now - at < ttlMs) return true
            }
            return false
        }

        /** 剪掉过期与损坏条目，只留可解析且未过期的；null/空集返回空集。 */
        @JvmStatic
        @JvmOverloads
        fun prunePushedEntries(entries: Set<String>?, now: Long,
                               ttlMs: Long = PUSHED_GID_TTL_MS): Set<String> {
            if (entries.isNullOrEmpty()) return emptySet()
            return entries.filterTo(LinkedHashSet()) { entry ->
                val at = entry.substringAfter(':', "").toLongOrNull()
                at != null && now - at < ttlMs
            }
        }

        /**
         * WP7：解析 pushed 集合中全部 TTL 内鲜活的 gid，单遍 O(n)。供对账方
         * （UploadReconciler）求「未推送差集」。损坏/裸 gid/过期条目按未推送
         * 处理，与 [isPushedEntryFresh] 同一保守语义。
         */
        @JvmStatic
        fun freshPushedGids(context: Context): Set<Long> {
            val entries = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_PUSHED_GIDS, null) ?: return emptySet()
            val now = System.currentTimeMillis()
            val fresh = LinkedHashSet<Long>()
            for (entry in entries) {
                val sep = entry.indexOf(':')
                if (sep <= 0) continue
                val gid = entry.substring(0, sep).toLongOrNull() ?: continue
                val at = entry.substring(sep + 1).toLongOrNull() ?: continue
                if (now - at < PUSHED_GID_TTL_MS) {
                    fresh.add(gid)
                }
            }
            return fresh
        }

        /** 内存态全部 FINISH 行的 gid，保持遍历顺序（手动 PUSH_ALL 与对账共用）。 */
        @JvmStatic
        fun scanFinishedGids(dm: DownloadManager): List<Long> {
            val result = ArrayList<Long>()
            for (info in dm.getAllDownloadInfoList()) {
                if (info.state == DownloadInfo.STATE_FINISH) {
                    result.add(info.gid)
                }
            }
            return result
        }

        /**
         * WP7 精推入口：只把指定 gid 交给服务。空表是常见路径（对账无缺漏、
         * 钩子调用方）直接返回不发 Intent；WebUI 未配置时静默返回不 Toast——
         * 该入口被下载完成钩子高频调用，不能像手动 PUSH_ALL 那样打扰（手动
         * 入口的未配置反馈由 runPushLoop 的 loadConfig==null 分支保留）。
         *
         * 用 startForegroundService（O+，同 DownloadsScene 手动入口）：服务会
         * 走 startForeground；CAS 失败路径上服务本就在前台，满足 FGS 要求，
         * 且后台场景（同步导入触发钩子）不受 startService 的后台限制。
         */
        @JvmStatic
        fun pushGids(context: Context, gids: Collection<Long>) {
            if (gids.isEmpty()) {
                return
            }
            if (!WebUiSettings(context).isConfigured()) {
                return
            }
            val intent = Intent(context, DownloadUploadService::class.java)
            intent.action = ACTION_PUSH_GIDS
            intent.putExtra(EXTRA_GIDS, ArrayList<Long>(gids))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private val sRunning = AtomicBoolean(false)

        @JvmStatic
        fun isRunning(): Boolean = sRunning.get()

        fun cancel(context: Context) {
            val intent = Intent(context, DownloadUploadService::class.java)
            intent.action = ACTION_CANCEL
            context.startService(intent)
        }
    }
}
