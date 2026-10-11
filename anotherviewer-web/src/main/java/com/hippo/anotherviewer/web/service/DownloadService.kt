package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteRequestBuilder
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.config.PrivacyMaskFilter
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository.TitleProjection
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.PageFileHashRepository
import com.hippo.anotherviewer.web.service.integrity.PageHashWriter
import com.hippo.anotherviewer.web.service.integrity.RepairLogService
import com.hippo.anotherviewer.web.service.integrity.RepairResult
import com.hippo.anotherviewer.web.service.integrity.ReverifyStats
import com.hippo.anotherviewer.web.service.integrity.VGate
import com.hippo.anotherviewer.web.service.integrity.VGateResult
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.io.File
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Download manager with bounded concurrency:
 *
 * - Gallery-level concurrency is capped by [SiteCoreConfigProperties.DownloadProperties.maxConcurrentGalleries]
 *   via a bounded thread pool; page-level concurrency per gallery is capped by
 *   [SiteCoreConfigProperties.DownloadProperties.maxConcurrentImages].
 * - Pause/cancel/delete use a cooperative stop flag (checked between pages and
 *   immediately before file writes) instead of `Thread.interrupt()`, which
 *   OkHttp ignores.
 * - Rows are only finalised after the worker has exited; the final save checks
 *   row existence so a finished worker can never resurrect a deleted row.
 * - A failed page-count fetch marks the task FAILED (state 4) instead of
 *   fabricating a fake 1-page completion.
 *
 * State semantics (Android `DownloadInfo.STATE_*`): 0=NONE/WAIT(paused),
 * 1=WAIT, 2=DOWNLOADING, 3=FINISHED, 4=FAILED.
 */
@Service
class DownloadService(
    private val downloadRepository: DownloadInfoRepository,
    private val labelRepository: DownloadLabelRepository,
    private val config: SiteCoreConfigProperties,
    private val eventPublisher: ApplicationEventPublisher,
    private val imageCacheService: ImageCacheService,
    private val sessionManager: SiteSessionManager,
    private val galleryLookup: GalleryLookupService,
    private val serverConfigService: ServerConfigService,
    private val availability: EhAvailabilityService,
    private val downloadDirIndex: DownloadDirIndex,
    // S10: 批量取 history 行 page 填下载列表 readProgress（findByGidIn，避免 N+1）。
    private val historyRepository: com.hippo.anotherviewer.web.repository.HistoryInfoRepository,
    private val usernameProvider: com.hippo.anotherviewer.web.config.CurrentUsernameProvider,
    // 文件完整性 Wave 2（S3）：页文件落盘时过 V 门并建/刷新 SHA-256 基线
    // （origin=downloader）。默认 null 仅为既有直构测试（DownloadControllerTest）
    // 的源兼容保留——那些用例不触达下载写入路径；Spring 装配按类型注入真实仓库。
    private val pageFileHashRepository: PageFileHashRepository? = null,
    // 文件完整性 Wave 3（S7）：修复日志（遥测，只追加）。默认 null 仅为既有直构
    // 测试的源兼容保留；Spring 装配按类型注入。
    private val repairLogService: RepairLogService? = null,
    // P-S8 进度批量+节流：页级进度内存累计 + 1s 合并落库（批量件见
    // DownloadProgressPersister）。默认直构（绑定本仓库）仅为既有直构测试的源兼容
    // 保留——那些用例不触达后台调度（afterPropertiesSet 才启动）；Spring 装配按
    // 类型注入容器级单例。
    private val progressPersister: DownloadProgressPersister = DownloadProgressPersister(downloadRepository),
) : DisposableBean {
    private val logger = LoggerFactory.getLogger(DownloadService::class.java)

    private val mapper = jacksonObjectMapper()

    /** In-flight download tasks, keyed by download id. */
    private val tasks = ConcurrentHashMap<Long, DownloadTask>()

    /**
     * Bounded gallery worker pool — at most [maxConcurrentGalleries] galleries
     * run concurrently; the rest wait in the queue. core==max 不变式，容量
     * 可经 [applyGalleryConcurrency] 运行时调整。
     */
    private val workerPool: ThreadPoolExecutor = ThreadPoolExecutor(
        config.download.maxConcurrentGalleries,
        config.download.maxConcurrentGalleries,
        60, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )

    /** 当前画廊并发（core==max 不变式的观测口，测试与启动回喂对齐用）。 */
    internal val galleryConcurrency: Int get() = workerPool.corePoolSize

    /**
     * 运行时调整画廊并发（设置页 PUT 落盘后调用，无需重启即生效）。
     * 入参钳制到 DownloadSettings 允许的 1..20；与当前值相等时跳过。
     * ThreadPoolExecutor 要求 core<=max：扩容先 max 后 core，缩容先 core
     * 后 max，否则抛 IllegalArgumentException。
     */
    fun applyGalleryConcurrency(n: Int) {
        val target = n.coerceIn(MIN_GALLERY_CONCURRENCY, MAX_GALLERY_CONCURRENCY)
        val current = workerPool.corePoolSize
        if (target == current) return
        if (target > current) {
            workerPool.maximumPoolSize = target
            workerPool.corePoolSize = target
        } else {
            workerPool.corePoolSize = target
            workerPool.maximumPoolSize = target
        }
    }

    private val okHttpClient get() = sessionManager.okHttpClient

    /**
     * One in-flight download. [stopRequested] is the cooperative pause/delete
     * flag; [finished] is counted down when the worker thread has fully exited
     * so callers can join before mutating the row.
     */
    private class DownloadTask(
        val id: Long,
        val gid: Long,
        val token: String,
        val downloadDir: String,
        val label: Int,
        maxConcurrentImages: Int
    ) {
        val stopRequested = AtomicBoolean(false)
        val finished = CountDownLatch(1)
        val pageExecutor: ExecutorService = Executors.newFixedThreadPool(maxConcurrentImages)

        fun requestStop() {
            stopRequested.set(true)
        }

        /** Wait for the worker to exit; returns false on timeout. */
        fun awaitFinished(timeoutMs: Long): Boolean {
            pageExecutor.shutdown()
            return try {
                finished.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    // ── query ───────────────────────────────────────────────────

    fun listDownloads(
        labelId: Int? = null,
        offset: Int = 0,
        limit: Int = 100,
        sort: String = "time_desc",
        q: String? = null,
        regex: Boolean = false,
    ): DownloadListResponse {
        // A5 契约：offset 为行偏移、limit 为每页条数。PageRequest 以页码为参数，
        // 换算 pageIndex = offset / limit（前端按 limit 倍数递增，语义精确）。
        // 排序（sort 参数，未知值回落默认 time_desc=添加时间倒序=最新在前）：
        //   time_desc / time_asc / title_asc / title_desc
        // 过滤：q 非空时按标题/标题日文匹配；regex=true 时 q 按正则解释
        // （SQLite 无 REGEXP：SQL 层仅按 label 投影，正则匹配+排序在服务端内存完成）。
        // 分页契约：limit 钳制 [1,500]，负载留在服务器（2026-09-06 回滚
        // 098f1c04 的一次拉全量，恢复 df382ee7 跳页分页形态）。
        val size = limit.coerceIn(1, 500)
        val sortObj = sortOf(sort)
        val pageable = PageRequest.of(offset.coerceAtLeast(0) / size, size, sortObj)
        // A7-2（D12）：标签列表过滤墓碑（downloadLabel 是同步软删实体）。
        val labels = labelRepository.findAll().filter { !it.deleted }

        val labelFilter = labelId?.takeIf { it != 0 }
        val qFilter = q?.takeIf { it.isNotBlank() }

        // A7-2（D1）：列表与 total 一律仅存活行（墓碑行是同步删除的传播载体，
        // 不列进 REST 列表、不计入 total——内容/total 在有墓碑时变小是修复）。
        val (rows, totalCount) = when {
            qFilter != null && regex -> regexPage(labelFilter, qFilter, offset.coerceAtLeast(0), size, sortObj)
            qFilter != null -> downloadRepository.searchDownloads(labelFilter, escapeLike(qFilter), pageable).content to
                downloadRepository.countSearchDownloads(labelFilter, escapeLike(qFilter))
            labelFilter != null -> downloadRepository.findByLabelAndDeletedFalse(labelFilter, pageable).content to
                downloadRepository.countByLabelAndDeletedFalse(labelFilter)
            else -> downloadRepository.findAllByDeletedFalse(pageable).content to downloadRepository.countByDeletedFalse()
        }
        // S10: 对最终 rows（含 regexPage 路径）批量取历史行填 readProgress。
        val progressByGid = historyRepository.findByGidIn(rows.map { it.gid })
            .associateBy({ it.gid }) { it.page }
        return DownloadListResponse(
            downloads = rows.map { it.toItem(progressByGid[it.gid] ?: 0) },
            labels = labels.map { DownloadLabel(it.id, it.label, it.time) },
            total = totalCount.toInt()
        )
    }

    /**
     * 正则筛选页：SQL 层按 label 轻量投影 → 内存正则匹配（title/titleJpn）→
     * 按 sort 排序 → 行偏移分页 → 按页内 id 顺序回查完整实体。
     * @throws IllegalArgumentException 正则非法（控制器转 400）
     */
    private fun regexPage(
        label: Int?,
        pattern: String,
        offset: Int,
        size: Int,
        sort: Sort,
    ): Pair<List<DownloadInfoEntity>, Long> {
        val matched = regexMatched(label, pattern).sortedWith(comparatorOf(sort))
        val pageIds = matched.drop(offset).take(size).map { it.id }
        val entities = if (pageIds.isEmpty()) emptyList() else downloadRepository.findAllById(pageIds)
        val byId = entities.associateBy { it.id }
        return (pageIds.mapNotNull { byId[it] }) to matched.size.toLong()
    }

    /**
     * 正则匹配 id 集（批量 all 模式与正则分页共用）：label 投影 →
     * 内存正则匹配 title/titleJpn。
     * @throws IllegalArgumentException 正则非法（控制器转 400）
     */
    private fun regexMatchedIds(label: Int?, pattern: String): List<Long> =
        regexMatched(label, pattern).map { it.id }

    private fun regexMatched(label: Int?, pattern: String): List<TitleProjection> {
        val matcher = try {
            Regex(pattern)
        } catch (e: Exception) {
            throw IllegalArgumentException("正则表达式无效: ${e.message}")
        }
        return downloadRepository.findTitlesByLabel(label)
            .filter { proj ->
                val t = proj.title ?: ""
                val tj = proj.titleJpn ?: ""
                matcher.containsMatchIn(t) || matcher.containsMatchIn(tj)
            }
    }

    /** sort → 内存比较器（正则页排序，与 SQL 层 Sort 语义一致）。 */
    private fun comparatorOf(sort: Sort): Comparator<DownloadInfoRepository.TitleProjection> {
        val order = sort.getOrderFor("title") ?: sort.getOrderFor("time")
        val byTitle = order?.property == "title"
        val desc = order?.direction == Sort.Direction.DESC
        val cmp = if (byTitle) {
            Comparator.comparing<TitleProjection, String?> { it.title ?: "" }
        } else {
            Comparator.comparingLong<TitleProjection> { it.time }
        }
        return if (desc) cmp.reversed() else cmp
    }

    /** sort 参数 → Spring Data Sort；未知取值回落默认（time_desc）。 */
    private fun sortOf(sort: String): Sort = when (sort) {
        "time_asc" -> Sort.by(Sort.Direction.ASC, "time")
        "title_asc" -> Sort.by(Sort.Direction.ASC, "title")
        "title_desc" -> Sort.by(Sort.Direction.DESC, "title")
        else -> Sort.by(Sort.Direction.DESC, "time")
    }

    /** LIKE 通配符转义（与 repository 查询的 ESCAPE '\' 配对）。 */
    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun getDownloadInfo(id: Long): DownloadItem? {
        // A7-2（D4）：墓碑行不作为详情/活动下载返回。
        return downloadRepository.findById(id).orElse(null)?.takeUnless { it.deleted }?.toItem()
    }

    // ── lifecycle ───────────────────────────────────────────────

    /**
     * A7-2 复活语义（对齐 addFavorite F3 与 SyncService.mergeDownload 的 incoming live
     * 复活分支）：撞墓碑行不拒绝而是复活——download 是同步软删实体，列表已隐藏墓碑，
     * 物理删时代「删了可重加」的能力必须保留；否则 Web 永远无法重新下载该 gid。
     * 磁盘文件已随删除清掉：done 清零、state 回到 0（待开始），total 保留元数据。
     * 活行仍拒绝（幂等防重复添加）。
     */
    fun addDownload(request: DownloadAddRequest): Boolean {
        // A7-3（P1-1）：List 化防同 gid 多行炸单实体派生查询；firstOrNull 与原
        // findByGid 等价（单行模型下语义不变，多行脏数据不再毒化请求）。
        val existing = downloadRepository.findAllByGid(request.gid).firstOrNull()
        val now = System.currentTimeMillis()
        // 打码边界（2026-10-11 用户裁决）：打码开关只应影响**响应展示**，绝不让
        // 脱敏数据落库——打码期间客户端只持有 `#gid` 形态的序列号标题，与历史
        // 回写同口径拒收（置空；目录名随之回落纯 gid，不再产生 `gid-#gid` 目录）；
        // 真标题由下载管线解析详情页时回填（title 空/脱敏的行）。
        val safeTitle = request.title?.takeIf { !PrivacyMaskFilter.isMaskedTitle(it) }
        // 2026-08-30：目录命名对齐 Android——`{gid}-{title}`（人读可辨），
        // 标题缺席回落纯 gid（旧布局兼容）。
        val downloadPath = File(config.download.path, DownloadDirs.dirName(request.gid, safeTitle))
        downloadPath.mkdirs()

        val entity = when {
            existing == null -> DownloadInfoEntity().apply {
                gid = request.gid
                token = request.token
                title = safeTitle
                titleJpn = ""
                thumb = request.thumb
                category = 0
                state = 0
                total = 0
                done = 0
                label = request.label
                downloadDir = downloadPath.absolutePath
                time = now
                // A7-1 stamping：新行当场落属主与同步水位（请求线程才有 SecurityContext，
                // username 只在写入口落；worker 后续只 bump lastModified）。
                username = usernameProvider.currentUsername()
                lastModified = now
            }
            existing.deleted -> existing.apply {
                token = request.token
                title = safeTitle
                thumb = request.thumb
                label = request.label
                state = 0
                total = 0
                done = 0
                error = null
                downloadDir = downloadPath.absolutePath
                time = now
                deleted = false
                if (username == null) username = usernameProvider.currentUsername()
                lastModified = now
            }
            else -> return false
        }
        downloadRepository.save(entity)
        return true
    }

    fun startDownload(id: Long): Boolean {
        // Expired Gallery Site logins surface as a 401 before any download begins.
        sessionManager.requireValidSession()

        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        // 活动态（1 待跑/2 下载中）仅在**确有存活任务**时拒绝——服务重启/进程
        // 崩溃后 DB 里残留的活动态行没有任何任务（僵尸行），若按状态一律拒绝，
        // 「开始」与「全部下载」对它们全是空操作，行永远卡在 Wait/Downloading
        // （历史遗留「状态重建卡住」的一半根因）。已结束但尚未自 map 摘除的
        // 任务（finished 已计数）不阻止重启——它的终态写已完成，新任务按断点
        // 续传语义重扫磁盘即可。
        val live = tasks[id]
        if ((entity.state == 1 || entity.state == 2) && live != null && live.finished.count > 0L) {
            return false
        }

        // EH DOWN 熔断（docs/plan-2026-08-30-eh-circuit-breaker.md §3.2）：
        // 启动检查——DOWN 时直接置 FAILED（用户手动 start 可重试），绝不静默
        // 挂起在 pending/paused（pending 不自动重试会永远卡住）。
        if (availability.isBlocked()) {
            logger.warn("Download start blocked for gid={}: EH unavailable; marking FAILED", entity.gid)
            updateEntity(id) {
                it.state = 4
                it.error = "EH_UNAVAILABLE: EH 平台当前不可达"
            }
            return false
        }

        // Rows migrated from another host carry the old machine's absolute
        // paths; resolve against the current root and write the usable path
        // back so the row self-heals on every (re)start. 解析结果不存在但磁盘
        // 上有该 gid 的既有目录（`{gid}-{title}`：App 推送行 downloadDir 为空、
        // 旧机器路径失效）→ 采用既有目录：续传进原目录，而不是另建裸 `<gid>`
        // 目录把整套内容重复下载一遍。
        var downloadDir = DownloadDirs.resolve(config.download.path, entity.gid, entity.downloadDir)
        if (!downloadDir.isDirectory) {
            DownloadDirs.findExisting(config.download.path, entity.gid)?.let { downloadDir = it }
        }
        updateEntity(id) {
            it.state = 1
            it.error = null
            if (it.downloadDir != downloadDir.path) it.downloadDir = downloadDir.path
        }

        val task = DownloadTask(
            id = entity.id,
            gid = entity.gid,
            token = entity.token,
            downloadDir = downloadDir.path,
            label = entity.label,
            maxConcurrentImages = config.download.maxConcurrentImages.coerceAtLeast(1)
        )
        tasks[id] = task
        try {
            workerPool.execute { executeDownload(task) }
        } catch (e: RejectedExecutionException) {
            tasks.remove(id, task)
            updateEntity(id) {
                it.state = 4
                it.error = "Download queue rejected the task"
            }
            return false
        }
        return true
    }

    fun pauseDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        if (entity.state == 0) return false

        // Cooperative stop: the worker checks the flag between page downloads
        // and stops writing; the row transitions to state 0 (WAIT/paused).
        // P-S8 批量：暂停态写包进 flush 锁——在途批量 flush（加载的是暂停前状态）
        // 先完成，暂停写随后落地，行状态绝不被旧进度帧复活。
        // P2-2（二期 Wave 2 E2E）：锁内**先 flush 再写 state=0**——drain 守卫跳过
        // 暂停(0)行，若不先落库，pending 的最新 done 帧会滞留内存（行 done 停在
        // 旧值，暂停瞬间进度丢失）；先 flush（此刻行状态仍是 1/2，合法写 done）
        // 再写暂停态，行 done 反映暂停瞬间的真实进度，且该 id 再无 pending 残帧。
        tasks[id]?.requestStop()
        progressPersister.locked {
            progressPersister.flush(id)
            updateEntity(id) { it.state = 0 }
        }
        return true
    }

    fun cancelDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        val task = tasks[id]
        task?.requestStop()
        // Wait for the worker to exit before marking the row cancelled so a
        // late worker save cannot resurrect it as finished.
        task?.awaitFinished(90_000)
        // P-S8 批量：worker 已退出，先强制 flush 待写进度再写终态（取消的 done
        // 必须是最后上报值，flush 后该 id 再无批量写、终态写无竞争）。
        progressPersister.flush(id)
        if (downloadRepository.existsById(id)) {
            updateEntity(id) {
                it.state = 4
                it.error = "Cancelled"
            }
        }
        tasks.remove(id)
        downloadDirIndex.invalidate(entity.gid)
        return true
    }

    /**
     * A7-2：删除下载 = 磁盘文件照删（行为不变），DB 行墓碑化（deleted=true +
     * lastModified bump）——行保留让删除经增量 pull 传播给 App；物理删会让删除
     * 永不传播（§1.1），且 App 下次 push 同 gid 时 union-merge 静默重建。
     * cancelDownload 的终态写不复活墓碑（updateEntity 跳墓碑行已覆盖）。
     */
    fun deleteDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        val task = tasks[id]
        task?.requestStop()
        task?.awaitFinished(90_000)
        // Resolve through DownloadDirs so rows migrated from another host
        // delete their files at the current-root location instead of no-oping
        // on the old machine's path (which can never exist here).
        val dirFile = DownloadDirs.resolve(config.download.path, entity.gid, entity.downloadDir)
        if (dirFile.exists()) dirFile.deleteRecursively()
        // P-S8 批量：墓碑化前丢弃待写进度（行将删除，进度无意义；批量守卫本也
        // 跳过墓碑，这里只是把内存累计一并清掉）。
        progressPersister.discard(id)
        if (downloadRepository.existsById(id)) {
            entity.deleted = true
            entity.lastModified = System.currentTimeMillis()
            if (entity.username == null) entity.username = usernameProvider.currentUsername()
            downloadRepository.save(entity)
        }
        tasks.remove(id)
        downloadDirIndex.invalidate(entity.gid)
        return true
    }

    fun startAllDownloads() {
        val waiting = downloadRepository.findByStateAndDeletedFalse(0)
        waiting.forEach { startDownload(it.id) }
    }

    /**
     * 2026-08-30（用户裁决）：「全部下载」——无视现有状态（含已完成 3 / 失败 4 /
     * 暂停 0）全部重新开始；磁盘上有完整页面文件的行直接标记完成并跳过
     * （state=3, done=total, 零网络），缺失/损坏行走正常下载管线补下。
     *
     * 2026-10-11 增补（线上实例「状态重建卡住」的完整反演）：App 同步行不带
     * 页数（total=0），旧逻辑对 total<=0 一律判「未决」→ 全部行走下载管线
     * 重新排队——一次点击把 8700+ 行扔进 worker 队列逐个回源取页数，请求线程
     * 自身也要在 8700 行的磁盘扫描/建表/落库上磨几分钟（实测 394s、准备对
     * 8700 个画廊发起网络请求）。现在 total 未决的行以**磁盘为权威封存**：
     * 索引页数（内存查表，零磁盘 IO）优先，索引未覆盖再直接列目录；磁盘有
     * 内容的行落 total=done=磁盘页数、state=3（内容已在盘上就是「下载完成」
     * ——用户对「状态重建」的期待；缺页由阅读路径的按需回源兜底），磁盘无
     * 内容的行才真正走下载管线。App 恢复推送后其权威状态经 union-merge 覆盖
     * 本封存（封存只动 mutable 字段，不碰墓碑语义）。
     *
     * 另：写入带差分短路（状态与目标一致时零落库）——重复点击「全部下载」
     * 不再对 8800 行各写一次，也避开启动期 TOFU 回填的 SQLITE_BUSY_SNAPSHOT
     * 写争用放大。
     */
    fun restartAllDownloads(): Int {
        // 2026-08-31：点击「全部下载」刷新导目录索引——用户复制/上传缓存后无需
        // 重启，随后读取/服务路径即读到刚落盘的文件。
        // 2026-10-11：改为**后台异步**重建。原先的同步全量 walk（9327 目录/
        // 64 万文件，实测 9.5s 起、冷盘分钟级）在请求线程上执行，前端 await
        // 该 POST 且无任何忙态反馈——用户观感即「全部下载卡住」。本方法的磁盘
        // 校验直接列目录/查索引、不依赖这次调度，异步不影响其正确性；索引早
        // 一轮晚一轮只影响读取/服务路径的缓存新鲜度。
        // 索引刷新是只读感知；**不**为磁盘-only 目录建 DB 行（数据库是行数据
        // 的权威，只能由 App 推送/WebUI 添加/导入 .db 产生，磁盘文件夹不得
        // 反向写库——2026-08-31 用户裁决）。total 未决行的封存只更新既有行。
        downloadDirIndex.refreshAsync()
        // 磁盘目录映射一次建表（gid → 既有目录）：行 downloadDir 为空/失效时
        // 校验按映射找回 `{gid}-{title}` 目录——否则 292MB/1984 页已落盘的行
        // 永远校验不过、且重启被状态守卫拒绝，卡死在 Wait（2026-10-11 线上实例）。
        val diskDirs = DownloadDirs.listOurDirs(config.download.path)
        var restarted = 0
        var skippedVerified = 0
        var sealedFromDisk = 0
        // A7-2（D6）：「全部下载」遍历仅存活行，墓碑不参与重启/完成化。
        downloadRepository.findAllByDeletedFalseOrderById().forEach { entity ->
            val total = entity.total
            if (total > 0 &&
                isVerifiedOnDisk(entity.gid, entity.downloadDir, total, diskDirs[entity.gid])
            ) {
                // 差分短路：已是目标态的行零落库（8800 行全量写会与启动期
                // TOFU 回填争 SQLite 写锁，放大 BUSY_SNAPSHOT）。
                if (entity.state != 3 || entity.done != total || entity.error != null) {
                    updateEntity(entity.id) {
                        it.state = 3
                        it.done = total
                        it.error = null
                    }
                }
                skippedVerified++
                return@forEach
            }
            if (total <= 0) {
                // total 未决（App 同步行）：磁盘有内容即封存，零网络。
                val diskPages = diskPageCount(entity.gid, diskDirs[entity.gid])
                if (diskPages > 0) {
                    if (entity.state != 3 || entity.total != diskPages ||
                        entity.done != diskPages || entity.error != null
                    ) {
                        updateEntity(entity.id) {
                            it.state = 3
                            it.total = diskPages
                            it.done = diskPages
                            it.error = null
                        }
                    }
                    sealedFromDisk++
                    return@forEach
                }
            }
            // 磁盘无内容，或 total 已知但校验未过（真实缺页/损坏）：正常管线补下。
            if (startDownload(entity.id)) restarted++
        }
        logger.info(
            "restartAllDownloads: restarted={}, skippedVerified={}, sealedFromDisk={}",
            restarted, skippedVerified, sealedFromDisk
        )
        return restarted
    }

    /**
     * 磁盘校验：目录存在且 `%04d.*` 文件数 >= total（total<=0 视为未决，不判定完成）。
     *
     * [fallbackDir]：行自身路径解析不到目录时的磁盘既有目录（gid 映射/索引）——
     * App 推送行 downloadDir 为空、旧机器绝对路径失效等形态下，内容目录以
     * `{gid}-{title}` 命名在盘上，不回落就永远校验不过。
     */
    private fun isVerifiedOnDisk(
        gid: Long,
        storedDir: String?,
        total: Int,
        fallbackDir: File? = null,
    ): Boolean {
        if (total <= 0) return false
        val resolved = DownloadDirs.resolve(config.download.path, gid, storedDir)
        val dir = when {
            resolved.isDirectory -> resolved
            fallbackDir?.isDirectory == true -> fallbackDir
            else -> return false
        }
        return countPageFiles(dir) >= total
    }

    /**
     * 磁盘页数（封存口径）：索引页数（内存查表；索引每 30s 至多一次根指纹
     * 检查）优先，索引无条目时对该 gid 的磁盘目录直接列一次。
     */
    private fun diskPageCount(gid: Long, fallbackDir: File?): Int {
        val indexed = downloadDirIndex.pageCount(gid)
        if (indexed > 0) return indexed
        return fallbackDir?.let { countPageFiles(it) } ?: 0
    }

    /** 目录内 `%04d.*` 非空页文件计数（索引未覆盖时的直读口径，与 isVerifiedOnDisk 同式）。 */
    private fun countPageFiles(dir: File): Int {
        if (!dir.isDirectory) return 0
        return dir.listFiles { f -> f.isFile && f.name.matches(VERIFY_PAGE_NAME) }
            ?.count { it.length() > 0 } ?: 0
    }

    /**
     * 目录一次扫描 → 已有非空页号集合（任意白名单扩展名，与索引口径同源）。
     * 断点续传快路径专用：原子写的 temp（`.{name}.{uuid}.tmp`，点前缀）
     * 不匹配 [POOL_PAGE_NAME]，不会被误判为已有页。
     */
    private fun collectExistingPages(dir: File): Set<Int> =
        dir.listFiles()
            ?.mapNotNull { f ->
                if (!f.isFile || f.length() <= 0L) return@mapNotNull null
                POOL_PAGE_NAME.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull()
            }
            ?.toSet()
            .orEmpty()

    /**
     * 阅读命中存储池推送文件后的「完成化」（需求 1）：下载行存在、当前非完成态、
     * 且磁盘校验（%04d.* 文件数 == total）通过 → 置 3（已完成）。
     * 调用点：ImageProxyController.servePushedPage（阅读器读到推送文件即标记）。
     */
    fun completeIfVerified(gid: Long) {
        // A7-2（D9）：墓碑行不做磁盘校验「完成化」（复活表象）。
        // A7-3（P1-1）：查找 List 化（firstOrNull 等价，多行脏数据不再毒化请求）。
        val entity = downloadRepository.findAllByGid(gid).firstOrNull()?.takeUnless { it.deleted } ?: return
        if (entity.state == 3) return
        val total = entity.total
        if (total <= 0) return
        // 单行路径用索引做目录回落（索引启动即载入，逐目录扫描交给巡检/TTL 维护），
        // 与 restartAllDownloads 的 gid 映射同语义：downloadDir 为空/失效而内容
        // 目录以 `{gid}-{title}` 在盘上时不漏判完成化。
        if (isVerifiedOnDisk(gid, entity.downloadDir, total, downloadDirIndex.dirFor(gid))) {
            // P-S8 批量：完成化前强制 flush 对应任务的待写进度（完成化是终态写，
            // flush 后该 id 的内存累计清空，后续再无旧帧可写）。
            progressPersister.flush(entity.id)
            updateEntity(entity.id) {
                it.state = 3
                it.done = total
                it.error = null
            }
            downloadDirIndex.invalidate(gid)
            logger.info("Download gid={} marked complete after disk verification", gid)
        }
    }

    fun pauseAllDownloads() {
        // A7-2（D7）：仅存活行。
        val active = downloadRepository.findByStateAndDeletedFalse(1) + downloadRepository.findByStateAndDeletedFalse(2)
        active.forEach { pauseDownload(it.id) }
    }

    // ── 批量操作（Android 多选模式 Start/Stop/Delete/Move 的 WebUI 对等物）──
    // all=true 时忽略 ids，按 (label, q) 过滤条件在服务端解析全集（跨页全选）。

    /** 批量开始：返回成功开始的数量。 */
    fun startDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?, regex: Boolean): Int {
        var started = 0
        resolveBatchIds(ids, all, label, q, regex).forEach { if (startDownload(it)) started++ }
        return started
    }

    /** 批量停止（暂停）：返回成功暂停的数量。 */
    fun pauseDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?, regex: Boolean): Int {
        var paused = 0
        resolveBatchIds(ids, all, label, q, regex).forEach { if (pauseDownload(it)) paused++ }
        return paused
    }

    /** 批量删除：返回已删除的数量（含下载目录文件）。 */
    fun deleteDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?, regex: Boolean): Int {
        var removed = 0
        resolveBatchIds(ids, all, label, q, regex).forEach { if (deleteDownload(it)) removed++ }
        return removed
    }

    /** 批量移动标签：labelId=0 表示移回默认标签；返回成功更新的数量。 */
    fun moveDownloads(ids: List<Long>?, all: Boolean, label: Int?, q: String?, regex: Boolean, labelId: Int): Int {
        if (labelId != 0 && !labelRepository.existsById(labelId.toLong())) return 0
        var moved = 0
        resolveBatchIds(ids, all, label, q, regex).forEach { id ->
            updateEntity(id) { it.label = labelId }
            moved++
        }
        return moved
    }

    /**
     * 批量目标解析：all=true → 按 (label, q[, regex]) 过滤全集取 id
     * （LIKE 走 SQL 投影；regex 走内存匹配投影），否则直接用 ids。
     */
    private fun resolveBatchIds(ids: List<Long>?, all: Boolean, label: Int?, q: String?, regex: Boolean): List<Long> {
        if (!all) return ids.orEmpty()
        val labelFilter = label?.takeIf { it != 0 }
        val qFilter = q?.takeIf { it.isNotBlank() }
        return if (qFilter != null && regex) {
            regexMatchedIds(labelFilter, qFilter)
        } else {
            downloadRepository
                .findAllIdsBy(labelFilter, qFilter?.let(::escapeLike), PageRequest.of(0, MAX_BATCH_IDS))
                .content
        }
    }

    // ── labels ──────────────────────────────────────────────────

    fun createLabel(label: String): Boolean {
        val existing = labelRepository.findByLabel(label)
        // A7-2：同名墓碑标签复活（downloadLabel 按 label 名联合合并且 findByLabel
        // 是单实体查询——墓碑旁再插同名行会让 mergeDownloadLabel 命中两行直接抛
        // IncorrectResultSizeDataAccessException）；活行仍拒绝。
        if (existing != null && !existing.deleted) return false
        val now = System.currentTimeMillis()
        val entity = if (existing != null) {
            existing.apply {
                deleted = false
                lastModified = now
                if (username == null) username = usernameProvider.currentUsername()
            }
        } else {
            DownloadLabelEntity().apply {
                this.label = label
                time = now
                // A7-1 stamping：新行当场落属主与同步水位。
                username = usernameProvider.currentUsername()
                lastModified = now
            }
        }
        labelRepository.save(entity)
        return true
    }

    /** A7-2（D12）：删除标签 = 墓碑化（downloadLabel 是同步软删实体，物理删不传播且会被 push 重建）。 */
    fun deleteLabel(id: Long): Boolean {
        val row = labelRepository.findById(id).orElse(null) ?: return false
        if (row.deleted) return true
        row.deleted = true
        row.lastModified = System.currentTimeMillis()
        if (row.username == null) row.username = usernameProvider.currentUsername()
        labelRepository.save(row)
        return true
    }

    // ── filter slots（筛选槽位：命名正则预设，serverConfig KV 持久化）──

    /**
     * 读取筛选槽位：JSON 解析容错——未配置或内容损坏一律返回空数组，
     * 绝不因坏数据让 GET 500。
     */
    fun getFilterSlots(): List<FilterSlotDto> {
        val raw = serverConfigService.get(KEY_FILTER_SLOTS)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            mapper.readValue(raw, object : TypeReference<List<FilterSlotDto>>() {})
        } catch (e: Exception) {
            logger.warn("Corrupt filter slots config ignored: {}", e.message)
            emptyList()
        }
    }

    /**
     * 整体替换筛选槽位：逐槽位校验 → 序列化为 JSON → set 持久化（随备份导出）→
     * 返回规范化后的结果（name 去首尾空白）。
     * @throws IllegalArgumentException 任一校验失败（控制器转 400 VALIDATION_ERROR）
     */
    fun putFilterSlots(slots: List<FilterSlotDto>): List<FilterSlotDto> {
        if (slots.size > MAX_FILTER_SLOTS) {
            throw IllegalArgumentException("at most 20 filter slots")
        }
        slots.forEach { slot ->
            val name = slot.name.trim()
            if (name.isEmpty()) throw IllegalArgumentException("slot name must not be blank")
            if (name.length > MAX_SLOT_NAME_LENGTH) {
                throw IllegalArgumentException("slot name must be at most 32 characters")
            }
            if (slot.pattern.isBlank()) throw IllegalArgumentException("slot pattern must not be blank")
            if (slot.pattern.length > MAX_SLOT_PATTERN_LENGTH) {
                throw IllegalArgumentException("slot pattern must be at most 256 characters")
            }
            try {
                Pattern.compile(slot.pattern)
            } catch (e: PatternSyntaxException) {
                throw IllegalArgumentException("invalid regex: ${e.message}")
            }
        }
        val normalized = slots.map { it.copy(name = it.name.trim()) }
        serverConfigService.set(KEY_FILTER_SLOTS, mapper.writeValueAsString(normalized))
        return normalized
    }

    // ── stats ───────────────────────────────────────────────────

    fun getActiveDownloadCount(): Int = tasks.size

    fun getCompletedDownloadCount(): Long = downloadRepository.countByStateAndDeletedFalse(3)

    fun getFailedDownloadCount(): Long = downloadRepository.countByStateAndDeletedFalse(4)

    fun getActiveDownloads(): List<DownloadItem> {
        return tasks.keys.mapNotNull { id ->
            getDownloadInfo(id)
        }
    }

    // ── worker ──────────────────────────────────────────────────

    private fun executeDownload(task: DownloadTask) {
        try {
            runDownload(task)
        } catch (e: Exception) {
            logger.error("Download failed for gid=${task.gid}", e)
            // P-S8 批量：异常兜底终态写前强制 flush 待写进度。
            progressPersister.flush(task.id)
            updateEntity(task.id) {
                it.state = 4
                it.error = e.message ?: "Download failed"
            }
            publishProgress(task, 4, 0, 0)
        } finally {
            task.pageExecutor.shutdown()
            tasks.remove(task.id, task)
            // 任务终态（完成/失败/取消/暂停）后索引可能过期：失效强制下一次访问重扫。
            downloadDirIndex.invalidate(task.gid)
            task.finished.countDown()
        }
    }

    private fun runDownload(task: DownloadTask) {
        val downloadDir = File(task.downloadDir)
        downloadDir.mkdirs()

        val existingTotal = downloadRepository.findById(task.id)
            .map { it.total }.orElse(0)
        val totalPages = if (existingTotal > 0) existingTotal else fetchPageCount(task.gid, task.token)

        // 标题回填（打码边界，2026-10-11）：打码期客户端只送得到 `#gid` 形态的
        // 脱敏标题——add 入口已拒收，但历史存量行可能残留序列号/空标题；详情页
        // 与页数同源（10 分钟缓存，通常零额外请求）拿真标题回填。已有真标题的
        // 行不做任何改动（App 推送/正常添加的标题是权威）。
        val titleRow = downloadRepository.findById(task.id).orElse(null)
        if (titleRow != null &&
            (titleRow.title.isNullOrBlank() || PrivacyMaskFilter.isMaskedTitle(titleRow.title))
        ) {
            val detail = galleryLookup.bestEffortDetail(task.gid, task.token)
            val realTitle = detail?.title?.takeIf {
                it.isNotBlank() && !PrivacyMaskFilter.isMaskedTitle(it)
            }
            if (realTitle != null) {
                updateEntity(task.id) {
                    it.title = realTitle
                    if (it.titleJpn.isNullOrBlank()) it.titleJpn = detail.titleJpn ?: ""
                }
            }
        }

        if (totalPages == null || totalPages <= 0) {
            // A failed page-count fetch must NEVER masquerade as a 1-page
            // completed download — mark the task failed instead.
            updateEntity(task.id) {
                it.state = 4
                it.total = 0
                it.error = "Failed to fetch page count for gallery ${task.gid}"
            }
            publishProgress(task, 4, 0, 0)
            return
        }

        updateEntity(task.id) {
            it.state = 2
            it.total = totalPages
            it.done = 0
            it.error = null
        }
        publishProgress(task, 2, 0, totalPages)

        val done = AtomicInteger(0)
        // 断点续传判据：目录一次扫描 → 已有非空页号集合（任意扩展名）。
        // 旧快路径只认 `%08d.jpg` 名——App/站点下载的 `.webp` 页被误判缺失，
        // 「开始/全部下载」会把整册慢速全网重下，并在 .webp 旁写 .jpg 重复体
        // （2026-10-11 线上：1984 页的库已有 1932 个 .webp，done 长时间为 0）。
        val existingPages = collectExistingPages(downloadDir)
        val submissions = ArrayList<Future<*>>(totalPages)
        for (page in 1..totalPages) {
            if (task.stopRequested.get()) break
            submissions.add(task.pageExecutor.submit {
                downloadPage(task, downloadDir, page, done, totalPages, existingPages)
            })
        }

        task.pageExecutor.shutdown()
        try {
            if (!task.pageExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                task.pageExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            task.pageExecutor.shutdownNow()
        }

        // Final state decision — only after every page worker has exited.
        // P-S8 批量：三个终态分支都先 flush 待写进度再写终态行——页 worker 此刻
        // 已全部退出，flush 是该 id「此后再无批量写」的屏障，终态 updateEntity
        // 绝无并发 flush 竞争。
        if (task.stopRequested.get()) {
            progressPersister.flush(task.id)
            updateEntity(task.id) {
                it.state = 0
                it.done = done.get()
            }
            return
        }

        val completed = done.get()
        if (completed >= totalPages) {
            progressPersister.flush(task.id)
            updateEntity(task.id) {
                it.state = 3
                it.done = completed
                it.error = null
            }
            publishProgress(task, 3, completed, totalPages)
        } else {
            progressPersister.flush(task.id)
            updateEntity(task.id) {
                it.state = 4
                it.done = completed
                it.error = "Download incomplete: $completed of $totalPages pages completed"
            }
            publishProgress(task, 4, completed, totalPages)
        }
    }

    /**
     * Download a single page. The stop flag is checked before resolving the
     * image URL and immediately before writing the file, so a paused task
     * never writes additional files.
     *
     * [existingPages]：任务启动时一次性扫描的已有非空页号集合（任意扩展名）
     * ——断点续传快路径的 O(1) 判据。
     */
    private fun downloadPage(
        task: DownloadTask,
        downloadDir: File,
        page: Int,
        done: AtomicInteger,
        totalPages: Int,
        existingPages: Set<Int>,
    ) {
        try {
            if (task.stopRequested.get()) return

            if (page in existingPages) {
                val current = done.incrementAndGet()
                persistProgress(task, current)
                return
            }

            val fileName = "%08d.jpg".format(page)
            val file = File(downloadDir, fileName)

            val imageUrl = fetchImageUrl(task, page) ?: return
            if (task.stopRequested.get()) return

            val imageData = downloadImage(imageUrl) ?: return
            if (task.stopRequested.get()) return

            // S3 写入钩子（文件完整性 Wave 2）：落盘前过 V 门——拒收 = 该页下载
            // 失败（不落盘、不计 done、不建基线）；文件缺席让下一次重启/重跑按
            // 既有断点续传语义自然重试该页。
            if (!writePageWithBaseline(task.gid, page, file, imageData)) return
            val current = done.incrementAndGet()
            imageCacheService.cacheImage(imageUrl, imageData)
            persistProgress(task, current)
            publishProgress(task, 2, current, totalPages)
        } catch (e: Exception) {
            logger.warn("Page download failed for gid={} page={}: {}", task.gid, page, e.message)
        }
    }

    /**
     * S3 写入钩子：V 门校验 → 原子落盘（P1-1：temp + rename，见 [atomicWrite]）→
     * SHA-256 基线 upsert。
     *
     * - V 门拒收（contracts/integrity-vgate.md §2/§3）：不落盘、不建/不刷基线，
     *   返回 false——调用方按该页下载失败处理（不计 done，终态走既有 FAILED
     *   语义，下次运行按断点续传重试该页）。
     * - 落盘：原子写保证目标文件要么是旧完整内容、要么是新完整内容——写中途
     *   失败不留半截文件冒充已完成页（断点续传快路径 `file.exists() &&
     *   length() > 0` 因此不会被残渣误判）。写失败返回 false，调用方按该页
     *   失败处理。通过后对落盘字节计算 SHA-256 → [PageFileHashRepository]
     *   upsert（origin=[writeOrigin]，下载钩子恒为 downloader、修复覆写传 heal）。
     *   下载字节本已全量驻内存（[downloadImage] 返回 ByteArray），单遍
     *   `MessageDigest.digest(bytes)` 与流式聚合等价，无额外常驻。覆盖已有基线
     *   （重下/重传/修复同一页）= 刷新 hash/size/ext，并把 verdict/last_verified_at
     *   复位为未巡检（内容已变，旧巡检结论作废）。哈希落库失败不影响该页成功
     *   （基线 best-effort，可由巡检/TOFU 回填）。
     *
     * @param page 1-based（下载循环/文件名口径）；基线行按 0-based 口径落库（page-1）。
     * @return true = 已落盘并建基线；false = V 门拒收或写失败（未落盘/目标未动）。
     */
    internal fun writePageWithBaseline(
        gid: Long,
        page: Int,
        file: File,
        bytes: ByteArray,
        writeOrigin: String = ORIGIN_DOWNLOADER,
    ): Boolean {
        val gate = VGate.check(bytes)
        if (gate is VGateResult.Reject) {
            logger.warn(
                "V gate rejected page for gid={} page={}: reason={} format={}",
                gid, page, gate.reason, gate.format
            )
            return false
        }
        if (!atomicWrite(file, bytes)) {
            logger.warn("Atomic page write failed for gid={} page={}: target={}", gid, page, file.absolutePath)
            return false
        }
        upsertPageHash(gid, page - 1, file, bytes, writeOrigin)
        return true
    }

    /**
     * P1-1 原子写：先写同目录 temp 文件（隐藏前缀 + 随机后缀，绝不命中任何
     * 页文件名模式 `^\d{4,}\..*`），成功后 `Files.move(ATOMIC_MOVE)` 原子换名——
     * 半截内容只会出现在 temp 里，目标文件要么是旧完整内容、要么是新完整内容，
     * 绝不出现半截文件冒充已完成页。
     *
     * 失败路径（写入异常 / 换名异常）一律删除 temp，目标文件保持原样：本路径
     * 触碰目标的唯一方式是换名成功，因此修复覆写场景下旧的完整文件不受影响。
     * ATOMIC_MOVE 不被文件系统支持时回退同目录普通 move（同目录 move 即
     * rename，语义仍等价原子）。
     *
     * @return true = 目标已替换为本次内容；false = 失败（无 temp 残留、目标未动）。
     */
    internal fun atomicWrite(target: File, bytes: ByteArray): Boolean =
        atomicWrite(target) { out -> out.write(bytes) }

    /** [atomicWrite] 的注入缝：[writer] 负责把内容写进 temp 输出流（测试注入中途失败）。 */
    internal fun atomicWrite(target: File, writer: (OutputStream) -> Unit): Boolean {
        val dir = target.parentFile ?: return false
        val temp = File(dir, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            try {
                temp.outputStream().use(writer)
            } catch (e: Exception) {
                logger.warn("Atomic write failed while writing {}: {}", temp.absolutePath, e.toString())
                temp.delete()
                return false
            }
            try {
                Files.move(
                    temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } catch (e: Exception) {
            logger.warn(
                "Atomic write failed while moving {} -> {}: {}",
                temp.absolutePath, target.absolutePath, e.toString()
            )
            temp.delete()
            return false
        }
    }

    /**
     * SHA-256 基线 upsert（Wave 1 契约）：字段改写与「先取旧行再 save」的覆盖
     * 语义统一在 integrity 包共享实现 [PageHashWriter.upsert]（Wave 3 / S7 上取，
     * downloader 钩子与 heal 覆写共用同一条刷新路径）。任何异常只记日志不上抛：
     * 基线是 best-effort 附属产物，不得让已落盘的页被记为失败。
     */
    private fun upsertPageHash(gid: Long, page0: Int, file: File, bytes: ByteArray, writeOrigin: String) {
        val hashRepository = pageFileHashRepository
        if (hashRepository == null) {
            // 仅可能出现在未注入仓库的直构测试构造里；Spring 装配恒注入。
            logger.warn("PageFileHashRepository not wired; skipping baseline for gid={} page={}", gid, page0)
            return
        }
        PageHashWriter.upsert(hashRepository, gid, page0, file, bytes, writeOrigin)
    }

    // ── 文件完整性 Wave 3（S7）：单页强制重取与整本复验 ────────────────────

    /**
     * 单页强制重取（修复）：绕过「池内有文件就先 serve 池文件、下载器跳过已
     * 存在文件」的现状，从 EH 源重新拉取该页并覆写。
     *
     * 流程：解析该页源 URL（1-based [page]）→ **绕过 URL 缓存**直连源拉取 →
     * V 门 → 覆写池文件（保留既有文件名）→ 哈希基线刷新（origin=heal，
     * verdict/last_verified_at 复位）→ 清该页各层缓存（imageCache page-keyed
     * 内存/磁盘 + enhanced 派生 + downloadDirIndex）→ 重灌 URL 缓存为新鲜字节 →
     * 记修复日志（source=refresh）。
     *
     * 归因（仅 healed）：覆写字节哈希 == 旧基线 → local_corrupt（本地劣化）；
     * 不同 → source_changed（源已变）；无旧基线 → null（无法归因，处置相同）。
     * 失败（源不可得 / V 门拒收）→ status=failed，本地文件与基线不动。
     * 并发护栏（P2-2）：该 gid 有活跃下载任务时直接 failed（提示画廊下载中），
     * 绝不与下载管线并发覆写同一池文件。
     *
     * @param page API 口径 1-based 页号。
     */
    fun forceRefetchPage(gid: Long, page: Int): RepairResult =
        forceRefetchPageInternal(gid, page, RepairLogService.SOURCE_REFRESH)

    /**
     * 整本复验：逐页「读盘哈希 vs 基线」，只修坏页（按需深度校验，不拉源验证
     * 好页——「源变更」探测是 forceRefetchPage 归因的副产品）。
     *
     * 逐页范围 = **磁盘页文件 ∪ 基线行**（契约原义 "re-reads every page file on
     * disk"；P1-2：不再把行 total 的 1..total 并进来——部分下载的画廊只检查
     * 磁盘上实际存在的页，缺失的未下载页零拉源，不误报为坏页）。判定：
     * - 文件缺失 / 读不出 → 坏 → 修复；
     * - 基线非空且读盘哈希 == 基线 → 好（跳过，零网络）；
     * - 基线非空且哈希不符 → 坏 → 修复；
     * - 无基线（含 BackfillService 落的 struct_bad 空哈希行）→ 只做 V 门结构
     *   判定（零网络）：过 → 好跳过（基线补建归 BackfillService）；拒 → 坏 → 修复。
     *
     * 并发护栏（P2-2）：开始时检查一次该 gid 是否有活跃下载任务，有则整本拒绝
     * （零页检查，零网络）——修复覆写不得与下载管线并发写同一池文件。
     *
     * 每页修复走 [forceRefetchPage] 同款管线，日志 source=reverify。
     * [isCancelled] 每页检查；中断时返回已检查页的部分统计（interrupted=true）。
     * 建议画廊空闲时调用。
     */
    fun reverifyGallery(gid: Long, isCancelled: () -> Boolean = { false }): ReverifyStats {
        // P2-2：整本开始时检查一次（逐页修复在 forceRefetchPageInternal 里还有
        // 兜底拒绝，覆盖复验进行中才开始的下载任务）。
        if (hasActiveDownloadTask(gid)) {
            logger.warn(
                "Reverify refused for gid={}: a download task is active for this gallery", gid
            )
            return ReverifyStats(0, 0, 0, 0, interrupted = false)
        }
        val dir = downloadDirIndex.dirFor(gid)
        val baselines = pageFileHashRepository?.findByGid(gid).orEmpty().associateBy { it.page }

        val pages = sortedSetOf<Int>()
        dir?.listFiles()?.forEach { f ->
            val pageNo = POOL_PAGE_NAME.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull()
            if (pageNo != null && pageNo >= 1) pages += pageNo
        }
        pages += baselines.keys.map { it + 1 }.filter { it >= 1 }

        var ok = 0
        var bad = 0
        var refreshed = 0
        for (page in pages) {
            if (isCancelled()) {
                logger.info(
                    "Reverify interrupted for gid={}: ok={} bad={} refreshed={}", gid, ok, bad, refreshed
                )
                return ReverifyStats(ok + bad, ok, bad, refreshed, interrupted = true)
            }
            val bytes = dir?.let { findPoolFile(gid, it, page) }
                ?.takeIf { it.isFile }
                ?.let { f -> runCatching { f.readBytes() }.getOrNull() }
            val oldHash = baselines[page - 1]?.hash?.takeIf { it.isNotBlank() }
            val isGood = when {
                // 文件缺失 / 读不出 → 坏。
                bytes == null -> false
                // 无基线：只做 V 门结构判定（零网络）。
                oldHash == null -> VGate.check(bytes) is VGateResult.Accept
                // 哈希 vs 基线。
                else -> oldHash == PageHashWriter.sha256Hex(bytes)
            }
            if (isGood) {
                ok++
                continue
            }
            bad++
            if (forceRefetchPageInternal(gid, page, RepairLogService.SOURCE_REVERIFY).status ==
                RepairResult.STATUS_HEALED
            ) {
                refreshed++
            }
        }
        logger.info("Reverify done for gid={}: total={} ok={} bad={} refreshed={}", gid, ok + bad, ok, bad, refreshed)
        return ReverifyStats(ok + bad, ok, bad, refreshed, interrupted = false)
    }

    /**
     * P2-2 并发护栏：该 gid 是否存在活跃（运行中/排队中）下载任务。修复覆写
     * （forceRefetchPage / reverifyGallery 的逐页 heal）会与下载管线对同一池
     * 文件并发写——有活跃任务时拒绝修复动作（调用方拿到 failed，message 提示
     * 画廊下载中，稍后重试），比等待/加锁简单且安全。
     */
    internal fun hasActiveDownloadTask(gid: Long): Boolean = tasks.values.any { it.gid == gid }

    /** [forceRefetchPage] 的实现体；[source] 区分修复日志的来源（refresh|reverify）。 */
    private fun forceRefetchPageInternal(gid: Long, page: Int, source: String): RepairResult {
        // P2-2：修复动作开始前拒绝与活跃下载并发写（reverify 整本开始时另有
        // 一次性检查，这里兜底覆盖「复验进行中才开始的下载任务」）。
        if (hasActiveDownloadTask(gid)) {
            return repairFailed(
                gid, page, null, source,
                "gallery download in progress: repair refused (retry after the download finishes)",
            )
        }
        if (page < 1) {
            return repairFailed(gid, page, null, source, "page must be >= 1 (API page is 1-based)")
        }
        val row = downloadRepository.findAllByGid(gid).firstOrNull()?.takeUnless { it.deleted }
        val token = row?.token ?: galleryLookup.findToken(gid)
        if (token.isNullOrBlank()) {
            return repairFailed(gid, page, null, source, "no source token: gallery unknown to this server")
        }
        val oldHash = pageFileHashRepository
            ?.findByGidAndPage(gid, page - 1)?.hash?.takeIf { it.isNotBlank() }

        val imageUrl = resolvePageUrl(gid, token, page) ?: run {
            return repairFailed(gid, page, oldHash, source, "source URL unavailable (upstream error or EH blocked)")
        }
        val fetched = fetchImageFromSource(imageUrl)
        val bytes = fetched.bytes ?: run {
            return repairFailed(gid, page, oldHash, source, "source fetch failed: ${fetched.detail}")
        }
        // 失败消息带上 V 门拒因（writePageWithBaseline 内部的重检恒一致）。
        val gate = VGate.check(bytes)
        if (gate is VGateResult.Reject) {
            return repairFailed(gid, page, oldHash, source, "V gate rejected refetched page (${gate.reason.name})")
        }
        if (!writePageWithBaseline(gid, page, poolFile(gid, row, page), bytes, ORIGIN_HEAL)) {
            return repairFailed(gid, page, oldHash, source, "V gate rejected refetched page")
        }

        val newHash = PageHashWriter.sha256Hex(bytes)
        val attribution = when {
            oldHash == null -> null
            oldHash == newHash -> RepairResult.ATTR_LOCAL_CORRUPT
            else -> RepairResult.ATTR_SOURCE_CHANGED
        }

        // 清该页各层缓存（page-keyed 内存/磁盘 + enhanced 派生 + URL-keyed 旧字节），
        // 重灌 URL 条目为新鲜字节（与下载管线 cacheImage 行为一致），索引失效强制重扫。
        imageCacheService.evictPage(gid, page - 1, listOf(imageUrl))
        imageCacheService.cacheImage(imageUrl, bytes)
        downloadDirIndex.invalidate(gid)
        repairLogService?.record(gid, page - 1, attribution, oldHash, newHash, source)
        logger.info(
            "Repair healed gid={} page={} attribution={} oldHash={} newHash={} source={}",
            gid, page, attribution ?: "-", oldHash ?: "-", newHash, source
        )
        return RepairResult(RepairResult.STATUS_HEALED, attribution)
    }

    /** 失败路径：本地文件与基线不动，记失败日志行（attribution/new_hash 均空）。 */
    private fun repairFailed(
        gid: Long,
        page: Int,
        oldHash: String?,
        source: String,
        message: String,
    ): RepairResult {
        logger.warn("Repair failed gid={} page={} source={} oldHash={}: {}", gid, page, source, oldHash ?: "-", message)
        repairLogService?.record(gid, page - 1, null, oldHash, null, source)
        return RepairResult(RepairResult.STATUS_FAILED, null, message)
    }

    /**
     * 该页的池文件：目录经 [DownloadDirIndex]（缺目录时按下载行解析并创建）；
     * 文件选择统一走 [DownloadDirIndex.findPage]（P2-5：与阅读/索引路径同一份
     * pageFiles 映射，同 (gid,page) 多文件时取索引的确定胜者）；缺席时按
     * 下载器写入口径新建 `%08d.jpg`。
     */
    private fun poolFile(gid: Long, row: DownloadInfoEntity?, page: Int): File {
        val dir = downloadDirIndex.dirFor(gid)
            ?: DownloadDirs.resolve(config.download.path, gid, row?.downloadDir, row?.title).apply { mkdirs() }
        return findPoolFile(gid, dir, page) ?: File(dir, "%08d.jpg".format(page))
    }

    /**
     * 定位目录内页号 == [page] 的页文件（[page] 1-based 文件名口径）。
     *
     * P2-5 唯一选择：优先 [DownloadDirIndex.findPage]（0-based 入参）的索引胜者
     * ——基线回填、复验比对与修复覆写三处因此恒选中同一物理文件；索引返回的
     * 文件已消失（扫描后被动过）或索引缺席（直构测试 mock / 目录刚建）时，
     * 回落同一规则 [DownloadDirIndex.selectPageFile] 做确定性挑选，绝不依赖
     * listFiles 的顺序碰运气。
     */
    private fun findPoolFile(gid: Long, dir: File, page: Int): File? {
        downloadDirIndex.findPage(gid, page - 1)?.let { ref ->
            return File(dir, ref.fileName).takeIf { it.isFile }
        }
        val candidates = dir.listFiles()
            ?.filter { f ->
                f.isFile && POOL_PAGE_NAME.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull() == page
            }
            .orEmpty()
        return DownloadDirIndex.selectPageFile(candidates)
    }

    /**
     * 解析修复用源页 URL（[page] 1-based）：与 [fetchImageUrl] 同款 3 次退避重试
     * （Gallery Site 509 / 上游异常）。失败返回 null。
     */
    private fun resolvePageUrl(gid: Long, token: String, page: Int): String? {
        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            try {
                return galleryLookup.fetchImageUrl(gid, token, page)
            } catch (e: Exception) {
                logger.warn(
                    "Failed to resolve image URL for repair gid={} page={} (attempt {}): {}",
                    gid, page, attempt + 1, e.message
                )
            }
        }
        return null
    }

    /**
     * 修复拉取的字节结果：[bytes] 非 null = HTTP 成功；null 时 [detail] 说明原因
     * （HTTP 状态码或网络错误）。**绕过 URL 缓存**——修复必须拿源的新鲜字节，
     * 缓存里的可能正是待修复的损坏副本。
     */
    private class SourceFetch(val bytes: ByteArray?, val detail: String)

    /** 与 [downloadImage] 同款链路（共享会话 client、Referer、per-request 超时、509 退避），仅去缓存探针。 */
    private fun fetchImageFromSource(url: String): SourceFetch {
        // 与 ImageProxyController.siteReferer 同形：带尾斜杠的 origin 形态被
        // EH 图片宿主严格 Referer 校验接受，裸形态会 403。
        val referer = SiteUrl.getReferer() + "/"
        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) {
                return SourceFetch(null, "interrupted during backoff")
            }
            try {
                val request = SiteRequestBuilder(url, referer).build()
                val call = okHttpClient.newCall(request)
                call.timeout().timeout(config.download.downloadTimeout, TimeUnit.MILLISECONDS)
                call.execute().use { response ->
                    if (response.code == 509) continue
                    if (response.isSuccessful) {
                        return SourceFetch(response.body?.bytes(), "ok")
                    }
                    return SourceFetch(null, "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                return SourceFetch(null, e.message ?: e.javaClass.simpleName)
            }
        }
        return SourceFetch(null, "rate limited (509) after retries")
    }

    /**
     * P-S8 进度批量：页级进度只做内存累计（taskId → 最新 done），由
     * [DownloadProgressPersister] 每 1s 合并落库（每任务 findById+save 一次，
     * 守卫语义与旧逐页落库逐字一致：墓碑行跳过、暂停(0)/失败(4) 行跳过）。
     * 终态（完成/失败/取消/暂停收尾）、画廊完成化、删除、关停等路径在
     * [DownloadService] 各写盘点显式 flush/discard——进度绝不滞留内存跨终态。
     *
     * 断点续传/重启恢复语义不变：重启后从最近一次落库的 done 续跑，崩溃至多
     * 丢 1s 窗口的内存进度（受磁盘页文件断点续传兜底，无正确性影响）。
     */
    private fun persistProgress(task: DownloadTask, done: Int) {
        progressPersister.record(task.id, done)
    }

    /**
     * Idempotent entity update through a fresh load — guards the final save so
     * a finished worker cannot resurrect a deleted row.
     *
     * A7-1 worker 规则（§3.4）：墓碑行（deleted=true）一律跳过（含 cancelDownload
     * 的终态写——状态写不得复活墓碑）；worker 线程无 SecurityContext，只 bump
     * lastModified（state/error 是同步可见字段），绝不写 username。
     */
    private fun updateEntity(id: Long, transform: (DownloadInfoEntity) -> Unit) {
        try {
            downloadRepository.findById(id).ifPresent { e ->
                if (e.deleted) return@ifPresent
                transform(e)
                e.lastModified = System.currentTimeMillis()
                downloadRepository.save(e)
            }
        } catch (e: Exception) {
            logger.warn("Failed to update download row id={}", id, e)
        }
    }

    private fun publishProgress(task: DownloadTask, state: Int, done: Int, total: Int) {
        eventPublisher.publishEvent(DownloadProgress(
            gid = task.gid,
            state = state,
            downloaded = done,
            total = total,
            speed = 0,
            label = task.label
        ))
    }

    /**
     * Fetches the total page count via anotherviewer-core's GalleryDetailParser
     * (SiteEngine.getGalleryDetail). Returns null when the upstream call fails —
     * callers must NOT treat that as a 1-page gallery.
     */
    private fun fetchPageCount(gid: Long, token: String): Int? {
        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            val count = try {
                galleryLookup.fetchPageCount(gid, token)
            } catch (e: Exception) {
                logger.warn("Failed to fetch page count for gid=$gid (attempt ${attempt + 1})", e)
                null
            }
            if (count != null && count > 0) return count
        }
        return null
    }

    /**
     * Fetches the image URL for a 1-based gallery page via GalleryPageParser.
     * Backs off on Gallery Site 509 rate limiting.
     */
    private fun fetchImageUrl(task: DownloadTask, page: Int): String? {
        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            return try {
                galleryLookup.fetchImageUrl(task.gid, task.token, page)
            } catch (e: Exception) {
                logger.warn("Failed to fetch image URL for gid={} page={} (attempt ${attempt + 1})", task.gid, page, e)
                null
            } ?: continue
        }
        return null
    }

    /**
     * Downloads image bytes with the shared session client. Honors
     * [SiteCoreConfigProperties.DownloadProperties.downloadTimeout] per request
     * and backs off on 509 responses.
     */
    private fun downloadImage(url: String): ByteArray? {
        val cached = imageCacheService.getCachedImage(url)
        if (cached != null) return cached

        for (attempt in 0 until 3) {
            if (attempt > 0 && !sleepQuietly(config.download.downloadDelay.toLong())) return null
            try {
                val request = SiteRequestBuilder(url, SiteUrl.getReferer()).build()
                val call = okHttpClient.newCall(request)
                call.timeout().timeout(config.download.downloadTimeout, TimeUnit.MILLISECONDS)
                call.execute().use { response ->
                    if (response.code == 509) continue
                    if (response.isSuccessful) return response.body?.bytes()
                    logger.warn("Image download HTTP {} from {}", response.code, url)
                    return null
                }
            } catch (e: Exception) {
                logger.warn("Failed to download image from $url", e)
                return null
            }
        }
        return null
    }

    private fun sleepQuietly(ms: Long): Boolean {
        if (ms <= 0) return true
        return try {
            Thread.sleep(ms)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /** [readProgress] 由 [listDownloads] 经 history 行批量填入；单行读（getDownloadInfo）保持 null。 */
    private fun DownloadInfoEntity.toItem(readProgress: Int? = null) = DownloadItem(
        id = id,
        gid = gid,
        token = token,
        title = title,
        titleJpn = titleJpn,
        thumb = thumb,
        category = category,
        state = state,
        total = total,
        done = done,
        label = label,
        // The server's absolute download path is never exposed to clients.
        downloadDir = null,
        error = error,
        readProgress = readProgress,
        uploader = uploader,
        pages = pages
    )

    override fun destroy() {
        tasks.values.forEach { it.requestStop() }
        workerPool.shutdownNow()
        tasks.values.forEach { it.pageExecutor.shutdownNow() }
        // P-S8 批量：关停兜底——把全部在途内存进度立即落库（批量件自身 destroy
        // 亦会 flushAll，双保险幂等）。
        progressPersister.flushAll()
    }

    private companion object {
        /** 跨页全选/批量单次解析的全集上限（9000+ 级规模安全；超限取前 N 条）。 */
        const val MAX_BATCH_IDS = 100_000

        /** 页文件基线 origin：服务端自己下载（文件完整性 Wave 2 S3）。 */
        private const val ORIGIN_DOWNLOADER = "downloader"

        /** 页文件基线 origin：修复覆写（文件完整性 Wave 3 S7）。 */
        private const val ORIGIN_HEAL = "heal"

        /** 池页文件名：`{4,}位数字.{扩展}`，与 DownloadDirIndex/BackfillService 索引口径一致。 */
        private val POOL_PAGE_NAME = Regex("^(\\d{4,})\\.(jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)

        /** 校验/封存口径的页文件：`{4,}位数字.任意扩展`（比 POOL_PAGE_NAME 宽——旧下载的异扩展名页也算数）。 */
        private val VERIFY_PAGE_NAME = Regex("^\\d{4,}\\..+")

        /** 筛选槽位持久化键（serverConfig KV，随备份自动导出）。 */
        const val KEY_FILTER_SLOTS = "download.filterSlots"

        const val MAX_FILTER_SLOTS = 20
        const val MAX_SLOT_NAME_LENGTH = 32
        const val MAX_SLOT_PATTERN_LENGTH = 256

        // 画廊并发边界：与 DownloadSettings 的 maxConcurrentGalleries 校验一致。
        private const val MIN_GALLERY_CONCURRENCY = 1
        private const val MAX_GALLERY_CONCURRENCY = 20
    }
}
