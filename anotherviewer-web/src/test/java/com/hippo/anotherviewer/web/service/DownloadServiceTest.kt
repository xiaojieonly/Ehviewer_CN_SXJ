package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.eq
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.DownloadAddRequest
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.PageFileHashEntity
import com.hippo.anotherviewer.web.entity.PageFileHashId
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.PageFileHashRepository
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
/**
 * Pins the G4 metrics fixes and the EH-DOWN download-start semantics
 * (plan-2026-08-30 §3.2/§3.3): state counts via COUNT SQL (countByState,
 * no entity materialisation) and immediate FAILED instead of a silent
 * pending task while EH is DOWN.
 *
 * 另含文件完整性 Wave 2（S3）下载写入钩子测试：V 门拒收不落盘不建基线
 * （截断页 → FAILED 计入重试）、合法页落盘 + SHA-256 基线（origin=downloader，
 * 0-based 页号）、覆盖写刷新基线。fixtures 取 Wave 0 classpath /integrity/。
 */
class DownloadServiceTest {

    /** addDownload 会在 download.path 下建目录——用临时目录隔离，不污染模块 CWD。 */
    @TempDir
    lateinit var tempDir: java.io.File

    private lateinit var downloadRepository: DownloadInfoRepository
    private lateinit var labelRepository: DownloadLabelRepository
    private lateinit var availability: EhAvailabilityService
    private lateinit var downloadDirIndex: DownloadDirIndex
    private lateinit var historyRepository: com.hippo.anotherviewer.web.repository.HistoryInfoRepository
    private lateinit var pageFileHashRepository: PageFileHashRepository
    private lateinit var galleryLookup: GalleryLookupService
    private lateinit var sessionManager: SiteSessionManager
    private lateinit var hashStore: ConcurrentHashMap<PageFileHashId, PageFileHashEntity>
    private lateinit var service: DownloadService

    @BeforeEach
    fun setUp() {
        downloadRepository = mock(DownloadInfoRepository::class.java)
        labelRepository = mock(DownloadLabelRepository::class.java)
        availability = mock(EhAvailabilityService::class.java)
        downloadDirIndex = mock(DownloadDirIndex::class.java)
        historyRepository = mock(com.hippo.anotherviewer.web.repository.HistoryInfoRepository::class.java)
        hashStore = ConcurrentHashMap()
        pageFileHashRepository = mock(PageFileHashRepository::class.java).apply {
            `when`(findByGidAndPage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer { inv ->
                    hashStore[PageFileHashId(inv.getArgument<Long>(0), inv.getArgument<Int>(1))]
                }
            `when`(save(any(PageFileHashEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<PageFileHashEntity>(0)
                hashStore[PageFileHashId(e.gid, e.page)] = e
                e
            }
        }
        galleryLookup = mock(GalleryLookupService::class.java)
        sessionManager = mock(SiteSessionManager::class.java)
        service = DownloadService(
            downloadRepository,
            labelRepository,
            SiteCoreConfigProperties().apply {
                download.path = java.io.File(tempDir, "downloads").absolutePath
            },
            mock(ApplicationEventPublisher::class.java),
            mock(ImageCacheService::class.java),
            sessionManager,
            galleryLookup,
            mock(ServerConfigService::class.java),
            availability,
            downloadDirIndex,
            historyRepository,
            stubProvider("test-user"),
            pageFileHashRepository,
        )
    }

    /** Wave 0 classpath fixture（src/test/resources/integrity/，口径见 MANIFEST.sha256）。 */
    private fun fixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/integrity/$name")!!.readBytes()

    /** 轮询等待异步 worker 落到终态（下载任务跑在 workerPool/pageExecutor 上）。 */
    private fun awaitUntil(timeoutMs: Long = 15_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    /** MockWebServer 供给一页字节（downloadImage 走真实 OkHttp 客户端）。 */
    private fun startPageServer(bytes: ByteArray): MockWebServer =
        MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
        }

    /** A7-1: 纯 Mockito 单测不碰 SecurityContext——注入固定用户的 Provider stub。 */
    private fun stubProvider(name: String): com.hippo.anotherviewer.web.config.CurrentUsernameProvider =
        mock(com.hippo.anotherviewer.web.config.CurrentUsernameProvider::class.java)
            .apply { `when`(currentUsername()).thenReturn(name) }

    // ── W1: 下载列表 uploader/pages 映射（GalleryInfoBase 列，WebUI 下载页展示） ──

    @Test
    fun `listDownloads maps uploader and pages from the entity`() {
        val full = DownloadInfoEntity().apply {
            id = 1L; gid = 42L; token = "tok"; title = "T"
            uploader = "someone"; pages = 42
        }
        val bare = DownloadInfoEntity().apply {
            id = 2L; gid = 43L; token = "tok2"; title = "T2"
        }
        `when`(downloadRepository.findAllByDeletedFalse(any(org.springframework.data.domain.Pageable::class.java)))
            .thenReturn(org.springframework.data.domain.PageImpl(listOf(full, bare)))
        `when`(downloadRepository.countByDeletedFalse()).thenReturn(2L)
        `when`(historyRepository.findByGidIn(any<Collection<Long>>())).thenReturn(emptyList())

        val response = service.listDownloads()

        val byGid = response.downloads.associateBy { it.gid }
        assertEquals("someone", byGid[42L]!!.uploader)
        assertEquals(42, byGid[42L]!!.pages)
        // 空态：实体列缺省 → uploader=null、pages=0。
        assertNull(byGid[43L]!!.uploader)
        assertEquals(0, byGid[43L]!!.pages)
    }

    @Test
    fun `getDownloadInfo maps uploader and pages on the single-row path`() {
        val row = DownloadInfoEntity().apply {
            id = 5L; gid = 77L; token = "tok"; title = "T"
            uploader = "uploader-77"; pages = 9
        }
        `when`(downloadRepository.findById(5L)).thenReturn(Optional.of(row))

        val item = service.getDownloadInfo(5L)!!

        assertEquals("uploader-77", item.uploader)
        assertEquals(9, item.pages)
    }

    @Test
    fun `state counts use countByStateAndDeletedFalse instead of loading entities`() {
        // A7-2（D8）: stats 计数不计墓碑（COUNT SQL，不加载实体，语义同前）。
        `when`(downloadRepository.countByStateAndDeletedFalse(3)).thenReturn(9171L)
        `when`(downloadRepository.countByStateAndDeletedFalse(4)).thenReturn(3L)

        assertEquals(9171L, service.getCompletedDownloadCount())
        assertEquals(3L, service.getFailedDownloadCount())

        verify(downloadRepository).countByStateAndDeletedFalse(3)
        verify(downloadRepository).countByStateAndDeletedFalse(4)
        verify(downloadRepository, never()).findByStateAndDeletedFalse(3)
        verify(downloadRepository, never()).findByStateAndDeletedFalse(4)
    }

    @Test
    fun `listDownloads fills readProgress with one batched history query (S10)`() {
        val row = DownloadInfoEntity().apply {
            id = 1L; gid = 42L; token = "tok42"; title = "T"; total = 10; done = 5
        }
        val rowNoHistory = DownloadInfoEntity().apply {
            id = 2L; gid = 43L; token = "tok43"; title = "T2"
        }
        `when`(downloadRepository.findAllByDeletedFalse(any(org.springframework.data.domain.Pageable::class.java)))
            .thenReturn(org.springframework.data.domain.PageImpl(listOf(row, rowNoHistory)))
        `when`(historyRepository.findByGidIn(listOf(42L, 43L))).thenReturn(
            listOf(com.hippo.anotherviewer.web.entity.HistoryInfoEntity().apply {
                gid = 42L; token = "tok42"; page = 7
            })
        )

        val response = service.listDownloads()

        val byGid = response.downloads.associateBy { it.gid }
        assertEquals(7, byGid[42L]!!.readProgress)
        // 无历史行 → 0（未读），与其他列表端点语义一致。
        assertEquals(0, byGid[43L]!!.readProgress)
        verify(historyRepository).findByGidIn(listOf(42L, 43L))
    }

    @Test
    fun `list limit above 500 is clamped back to 500`() {
        // 分页契约：limit 钳制 [1,500]（2026-09-06 回滚 098f1c04 的 100_000
        // 放宽）——size 以 500 进 PageRequest，返回行数同步受钳。
        val rows = (1..500L).map {
            DownloadInfoEntity().apply { id = it; gid = it; token = "t$it"; title = "T$it" }
        }
        `when`(
            downloadRepository.findAllByDeletedFalse(any(org.springframework.data.domain.Pageable::class.java))
        ).thenReturn(org.springframework.data.domain.PageImpl(rows))
        `when`(
            historyRepository.findByGidIn(any<Collection<Long>>())
        ).thenReturn(emptyList())
        `when`(downloadRepository.countByDeletedFalse()).thenReturn(500L)

        val response = service.listDownloads(limit = 100_000)

        assertEquals(500, response.downloads.size)
        assertEquals(500, response.total)
        // Pageable 拿到的 size 被钳到 500，而非透传 100_000。
        org.mockito.Mockito.verify(downloadRepository).findAllByDeletedFalse(
            com.hippo.anotherviewer.web.argThatK<org.springframework.data.domain.Pageable> { it.pageSize == 500 }
        )
    }

    @Test
    fun `restartAll skips disk-verified rows and restarts the rest`() {
        `when`(availability.isBlocked()).thenReturn(false)
        // DownloadDirs.resolve：相对 downloadDir 原样采用 → CWD 下建测试目录。
        val dir = java.io.File("dl-test-42").apply { mkdirs() }
        try {
            java.io.File(dir, "0001.jpg").writeBytes(ByteArray(10))
            java.io.File(dir, "0002.jpg").writeBytes(ByteArray(10))
            java.io.File(dir, "0003.jpg").writeBytes(ByteArray(10))
            val verified = DownloadInfoEntity().apply {
                id = 1L; gid = 42L; token = "tok-v"; total = 3; done = 3
                state = 3; downloadDir = "dl-test-42" // 相对路径 → resolve 原样返回
            }
            `when`(downloadRepository.findAllByDeletedFalseOrderById()).thenReturn(listOf(verified))
            `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(verified))
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

            val restarted = service.restartAllDownloads()

            assertEquals(0, restarted) // verified 行跳过；无其他行可重下
            // 差分短路（2026-10-11）：已是目标态的行零落库——重复点击不再对
            // 8800 行各写一次，避开启动期回填的 SQLite 写争用。
            verify(downloadRepository, never()).save(verified)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `restartAll verifies blank-downloadDir rows via gid-title disk dir (2026-10-11 stuck-row regression)`() {
        // 线上实例：gid=3618832 行 downloadDir 为空、state=1、total=1984/done=1984，
        // 磁盘内容在 `{gid}-{title}` 目录（292MB 全在）——旧校验只找裸 <gid>
        // 目录 → 永远不通过；旧守卫对 state=1 一律拒绝重启 → 行卡死 Wait。
        `when`(availability.isBlocked()).thenReturn(false)
        val downloadsRoot = java.io.File(tempDir, "downloads").apply { mkdirs() }
        val dir = java.io.File(downloadsRoot, "4246396-Some Title").apply { mkdirs() }
        java.io.File(dir, "00000001.webp").writeBytes(ByteArray(10))
        java.io.File(dir, "00000002.webp").writeBytes(ByteArray(10))
        java.io.File(dir, "00000003.webp").writeBytes(ByteArray(10))
        val stuck = DownloadInfoEntity().apply {
            id = 9021L; gid = 4246396L; token = ""; total = 3; done = 3
            state = 1; downloadDir = "" // App 推送行：无路径记录
        }
        `when`(downloadRepository.findAllByDeletedFalseOrderById()).thenReturn(listOf(stuck))
        `when`(downloadRepository.findById(9021L)).thenReturn(Optional.of(stuck))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        val restarted = service.restartAllDownloads()

        assertEquals(0, restarted)
        verify(downloadRepository).save(
            org.mockito.ArgumentMatchers.argThat<DownloadInfoEntity> { it.id == 9021L && it.state == 3 && it.done == 3 }
        )
        // 索引刷新改为后台异步：请求线程绝不再吃同步全量 walk。
        verify(downloadDirIndex).refreshAsync()
        verify(downloadDirIndex, never()).refresh()
    }

    @Test
    fun `restartAll seals total-unknown rows from disk without network (App-synced rows)`() {
        // App 同步行不带页数（total=0）：磁盘有 N 个页文件 → 封存 total=done=N、
        // state=3（磁盘为权威，零网络）。线上 8732 行即此形态——旧的「total 未决
        // →重下」把它们全部扔进下载队列（394s 请求 + 回源风暴）。
        `when`(availability.isBlocked()).thenReturn(false)
        val downloadsRoot = java.io.File(tempDir, "downloads").apply { mkdirs() }
        val dir = java.io.File(downloadsRoot, "12345-Titled").apply { mkdirs() }
        repeat(4) { i -> java.io.File(dir, "%04d.jpg".format(i + 1)).writeBytes(ByteArray(10)) }
        val row = DownloadInfoEntity().apply {
            id = 21L; gid = 12345L; token = "t"; total = 0; done = 0; state = 1
            downloadDir = ""
        }
        `when`(downloadRepository.findAllByDeletedFalseOrderById()).thenReturn(listOf(row))
        `when`(downloadRepository.findById(21L)).thenReturn(Optional.of(row))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        val restarted = service.restartAllDownloads()

        assertEquals(0, restarted)
        verify(downloadRepository).save(
            org.mockito.ArgumentMatchers.argThat<DownloadInfoEntity> {
                it.id == 21L && it.state == 3 && it.total == 4 && it.done == 4
            }
        )
    }

    @Test
    fun `restartAll seals via index page count without touching disk`() {
        // 索引页数优先（内存查表；每 30s 至多一次根指纹检查）：索引已覆盖的行
        // 不再列目录——8800 行规模下逐行列目录会把重建拖成分钟级。
        `when`(availability.isBlocked()).thenReturn(false)
        `when`(downloadDirIndex.pageCount(222L)).thenReturn(7)
        val row = DownloadInfoEntity().apply {
            id = 31L; gid = 222L; token = "t"; total = 0; done = 0; state = 0
            // 磁盘上并不存在（索引是唯一证据来源 → 证明走了索引快路径）
            downloadDir = java.io.File(tempDir, "downloads/222-NotOnDisk").absolutePath
        }
        `when`(downloadRepository.findAllByDeletedFalseOrderById()).thenReturn(listOf(row))
        `when`(downloadRepository.findById(31L)).thenReturn(Optional.of(row))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        val restarted = service.restartAllDownloads()

        assertEquals(0, restarted)
        verify(downloadRepository).save(
            org.mockito.ArgumentMatchers.argThat<DownloadInfoEntity> {
                it.id == 31L && it.state == 3 && it.total == 7 && it.done == 7
            }
        )
    }

    @Test
    fun `startDownload restarts a zombie row in activity state without a live task`() {
        // 服务重启后 DB 残留 state=1/2 而没有任何任务（僵尸行）：旧守卫按状态
        // 一律拒绝 → 「开始」「全部下载」全是空操作。用 EH 熔断作探针——必须
        // 越过分区守卫走到熔断标记 FAILED，才证明僵尸行已可被重新启动。
        `when`(availability.isBlocked()).thenReturn(true)
        val zombie = DownloadInfoEntity().apply {
            id = 7L; gid = 555L; token = "t"; state = 2; total = 10
        }
        `when`(downloadRepository.findById(7L)).thenReturn(Optional.of(zombie))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        val started = service.startDownload(7L)

        assertEquals(false, started) // EH DOWN → 不跑；但已越过分区守卫
        verify(downloadRepository).save(
            org.mockito.ArgumentMatchers.argThat<DownloadInfoEntity> { it.id == 7L && it.state == 4 }
        )
    }

    @Test
    fun `restartAll restarts rows whose disk content is short of a known total`() {
        // total 已知但磁盘缺页（真实缺失行）：不走封存，必须重新排队下载。
        // 用 EH 熔断作探针：必须进入 startDownload 才会写 FAILED——证明这是
        // 下载管线路径而非封存路径（封存会写 state=3）。
        `when`(availability.isBlocked()).thenReturn(true)
        val downloadsRoot = java.io.File(tempDir, "downloads").apply { mkdirs() }
        val dir = java.io.File(downloadsRoot, "777-Short").apply { mkdirs() }
        java.io.File(dir, "0001.jpg").writeBytes(ByteArray(10))
        val short = DownloadInfoEntity().apply {
            id = 11L; gid = 777L; token = "t"; total = 10; done = 1; state = 1
            downloadDir = dir.absolutePath
        }
        `when`(downloadRepository.findAllByDeletedFalseOrderById()).thenReturn(listOf(short))
        `when`(downloadRepository.findById(11L)).thenReturn(Optional.of(short))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        val restarted = service.restartAllDownloads()

        assertEquals(0, restarted) // 熔断拦截（探针口径）
        verify(downloadRepository).save(
            org.mockito.ArgumentMatchers.argThat<DownloadInfoEntity> { it.id == 11L && it.state == 4 }
        )
    }

    @Test
    fun `completeIfVerified only upgrades when disk holds full pages`() {
        val dir = java.io.File("dl-test-99").apply { mkdirs() }
        try {
            java.io.File(dir, "0001.jpg").writeBytes(ByteArray(10))
            java.io.File(dir, "0002.jpg").writeBytes(ByteArray(10))
            java.io.File(dir, "0003.jpg").writeBytes(ByteArray(10))
            java.io.File(dir, "0004.jpg").writeBytes(ByteArray(10))
            java.io.File(dir, "0005.jpg").writeBytes(ByteArray(10))

            val entity = DownloadInfoEntity().apply {
                id = 7L; gid = 99L; token = "tok"; total = 5; done = 2; state = 0
                downloadDir = "dl-test-99"
            }
            `when`(downloadRepository.findAllByGid(99L)).thenReturn(listOf(entity))
            `when`(downloadRepository.findById(7L)).thenReturn(Optional.of(entity))
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

            service.completeIfVerified(99L)

            assertEquals(3, entity.state)
            assertEquals(5, entity.done)
            verify(downloadRepository).save(entity)
            verify(downloadDirIndex).invalidate(99L)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `startDownload marks the task FAILED with EH_UNAVAILABLE error while blocked`() {
        `when`(availability.isBlocked()).thenReturn(true)
        val entity = DownloadInfoEntity().apply {
            id = 1
            gid = 42L
            token = "tok"
            state = 0
        }
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(entity))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        assertFalse(service.startDownload(1L))
        assertEquals(4, entity.state, "blocked start must FAILED immediately")
        assertEquals("EH_UNAVAILABLE: EH 平台当前不可达", entity.error)
        verify(downloadRepository).save(entity)
    }

    @Test
    fun `startDownload checks availability even for resumable paused tasks`() {
        // DOWN 重新开始：paused(0) 任务不被静默改回 1（无 worker、保持可重试的 FAILED 语义）。
        `when`(availability.isBlocked()).thenReturn(true)
        val entity = DownloadInfoEntity().apply {
            id = 1
            gid = 42L
            token = "tok"
            state = 0
        }
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(entity))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        service.startDownload(1L)

        assertEquals(4, entity.state)
    }

    // ── A7-1: 统一 stamping + worker 线程规则（§3.4） ──

    @Test
    fun `addDownload stamps username and lastModified on the new row`() {
        `when`(downloadRepository.findAllByGid(77L)).thenReturn(emptyList())

        assertTrue(service.addDownload(DownloadAddRequest(gid = 77L, token = "a1b2c3d4e5", title = "T", thumb = null)))

        verify(downloadRepository).save(
            argThatK {
                it.gid == 77L && it.username == "test-user" && it.lastModified > 0 && it.time > 0
            }
        )
    }

    @Test
    fun `addDownload rejects masked serial titles and dirs (privacy boundary)`() {
        // 打码开关只应影响响应展示，绝不让脱敏序列号落库：打码期间前端只持有
        // `#gid` 标题——add 拒收（置空），目录名随之回落纯 gid（不再产生
        // `gid-#gid` 目录，线上 9022/9023 即此产物）；真标题由下载管线回填。
        `when`(downloadRepository.findAllByGid(77L)).thenReturn(emptyList())

        assertTrue(service.addDownload(DownloadAddRequest(gid = 77L, token = "a1b2c3d4e5", title = "#77", thumb = null)))

        verify(downloadRepository).save(
            argThatK {
                it.gid == 77L && it.title == null &&
                    !it.downloadDir!!.contains("#") && it.downloadDir!!.endsWith("77")
            }
        )
    }

    @Test
    fun `download pipeline backfills real title for masked-title rows (privacy boundary)`() {
        // 打码期入库的存量行（title=#gid）：详情页与页数同源（10 分钟缓存）拿
        // 真标题回填——打码关闭后列表展示真标题，不再残留序列号。
        val server = startPageServer(fixture("valid.jpg"))
        try {
            val row = DownloadInfoEntity().apply {
                id = 1L; gid = 42L; token = "tok"; title = "#42"; titleJpn = ""
                state = 0
                downloadDir = java.io.File(java.io.File(tempDir, "downloads"), "42").absolutePath
            }
            `when`(availability.isBlocked()).thenReturn(false)
            `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(row))
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }
            `when`(galleryLookup.fetchPageCount(42L, "tok")).thenReturn(1)
            `when`(galleryLookup.fetchImageUrl(42L, "tok", 1)).thenReturn(server.url("/1.jpg").toString())
            `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())
            `when`(galleryLookup.bestEffortDetail(42L, "tok")).thenReturn(
                com.hippo.anotherviewer.client.data.GalleryDetail().apply {
                    title = "Real Title"
                    titleJpn = "リアルタイトル"
                }
            )

            assertTrue(service.startDownload(1L))
            awaitUntil { row.state == 3 }

            assertEquals("Real Title", row.title)
            assertEquals("リアルタイトル", row.titleJpn)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `download pipeline never overwrites an existing real title`() {
        // 已有真标题的行（App 推送/正常添加）是权威：管线不得触碰，也不发起
        // 详情请求——防止回填逻辑越界改写标题。
        val dir = java.io.File(java.io.File(tempDir, "downloads"), "42")
        dir.mkdirs()
        java.io.File(dir, "00000001.webp").writeBytes(ByteArray(8) { 1 })
        val row = DownloadInfoEntity().apply {
            id = 1L; gid = 42L; token = "tok"; title = "Real Existing Title"; titleJpn = "既存"
            state = 0; total = 1
            downloadDir = dir.absolutePath
        }
        `when`(availability.isBlocked()).thenReturn(false)
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(row))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        assertTrue(service.startDownload(1L))
        awaitUntil { row.state == 3 }

        assertEquals("Real Existing Title", row.title)
        verify(galleryLookup, never()).bestEffortDetail(42L, "tok")
    }

    @Test
    fun `state writes bump lastModified but never write username`() {
        // startDownload 的 EH-DOWN 状态写（等价 updateEntity 路径）：state 变更是同步
        // 可见字段 → bump lastModified；worker/请求线程规则：username 不写。
        val entity = DownloadInfoEntity().apply {
            id = 1
            gid = 42L
            token = "tok"
            state = 0
            lastModified = 3L
        }
        `when`(availability.isBlocked()).thenReturn(true)
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(entity))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        assertFalse(service.startDownload(1L))

        assertEquals(4, entity.state)
        assertTrue(entity.lastModified > 3L)
        assertNull(entity.username)
    }

    @Test
    fun `updateEntity skips tombstoned rows so state writes cannot resurrect them`() {
        // deleteDownload 后 cancelDownload 的终态写不得复活墓碑（deleted=true 行直接跳过）。
        val tombstone = DownloadInfoEntity().apply {
            id = 2
            gid = 43L
            token = "tok"
            state = 2
            deleted = true
        }
        `when`(downloadRepository.findById(2L)).thenReturn(Optional.of(tombstone))

        assertTrue(service.cancelDownload(2L))

        assertEquals(2, tombstone.state) // 状态写被跳过
        verify(downloadRepository, never()).save(any(DownloadInfoEntity::class.java))
    }

    @Test
    fun `pauseDownload flushes the pending progress frame before writing the paused state (P2-2)`() {
        // 修复前：requestStop 后锁内直接 state=0，pending 的 done 帧滞留（drain 守卫
        // 跳过暂停行，帧永远落不了库）——行 done 停在旧值。修复后：锁内先 flush
        // （行状态仍是 2，合法写 done）再写暂停态，行 done 反映暂停瞬间真实进度。
        val persister = DownloadProgressPersister(
            downloadRepository,
            scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor().apply { shutdownNow() },
        )
        val serviceWithPersister = DownloadService(
            downloadRepository,
            labelRepository,
            SiteCoreConfigProperties().apply {
                download.path = java.io.File(tempDir, "downloads").absolutePath
            },
            mock(ApplicationEventPublisher::class.java),
            mock(ImageCacheService::class.java),
            sessionManager,
            galleryLookup,
            mock(ServerConfigService::class.java),
            availability,
            downloadDirIndex,
            historyRepository,
            stubProvider("test-user"),
            pageFileHashRepository,
            progressPersister = persister,
        )
        val entity = DownloadInfoEntity().apply {
            id = 1L; gid = 42L; token = "tok"; state = 2; total = 10; done = 2
        }
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(entity))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        persister.record(1L, 7) // 页 worker 上报的最新进度（尚未落库）
        assertTrue(serviceWithPersister.pauseDownload(1L))

        assertEquals(0, entity.state, "暂停态必须落地")
        assertEquals(7, entity.done, "pending 的最新 done 必须在暂停写之前落库（P2-2）")

        // 幂等收尾：此后任何 flush 都不会再有该 id 的残帧可写。
        persister.flushAll()
        assertEquals(7, entity.done)
        assertEquals(0, entity.state)
    }

    // ── A7-2: 软删 / 复活 / 过滤 ──

    @Test
    fun `deleteDownload deletes files but keeps a tombstoned row`() {
        val dir = java.io.File("dl-test-del-7").apply { mkdirs() }
        try {
            java.io.File(dir, "0001.jpg").writeBytes(ByteArray(10))
            val row = DownloadInfoEntity().apply {
                id = 7L; gid = 71L; token = "tok"; state = 3
                downloadDir = "dl-test-del-7"; lastModified = 3L
            }
            `when`(downloadRepository.findById(7L)).thenReturn(Optional.of(row))
            `when`(downloadRepository.existsById(7L)).thenReturn(true)
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

            assertTrue(service.deleteDownload(7L))

            // 磁盘文件照删（行为不变）。
            assertFalse(dir.exists())
            // DB 行墓碑化：行保留、deleted=true、水位 bump（不再物理删）。
            assertTrue(row.deleted)
            assertTrue(row.lastModified > 3L)
            verify(downloadRepository, never()).deleteById(7L)
        } finally {
            java.io.File("dl-test-del-7").deleteRecursively()
        }
    }

    @Test
    fun `addDownload resurrects a tombstoned row instead of rejecting`() {
        val tombstone = DownloadInfoEntity().apply {
            id = 9L; gid = 91L; token = "old"; title = "Old"; state = 3; done = 7
            deleted = true
        }
        `when`(downloadRepository.findAllByGid(91L)).thenReturn(listOf(tombstone))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        assertTrue(service.addDownload(DownloadAddRequest(gid = 91L, token = "new", title = "New", thumb = null)))

        assertTrue(tombstone.deleted.not())
        assertEquals("new", tombstone.token)
        assertEquals(0, tombstone.state)
        assertEquals(0, tombstone.done)
        assertTrue(tombstone.lastModified > 0)
        verify(downloadRepository, never()).deleteById(9L)
    }

    @Test
    fun `getDownloadInfo returns null for a tombstoned row`() {
        val tombstone = DownloadInfoEntity().apply { id = 3L; gid = 44L; token = "tok"; deleted = true }
        `when`(downloadRepository.findById(3L)).thenReturn(Optional.of(tombstone))

        assertNull(service.getDownloadInfo(3L))
    }

    @Test
    fun `listDownloads queries live rows only across label and default paths`() {
        // A7-2（D1）: 默认路径与 label 路径都走 deleted=false 查询（total 同步）。
        val live = DownloadInfoEntity().apply { id = 1L; gid = 42L; token = "tok"; title = "T" }
        `when`(downloadRepository.findAllByDeletedFalse(any(org.springframework.data.domain.Pageable::class.java)))
            .thenReturn(org.springframework.data.domain.PageImpl(listOf(live)))
        `when`(downloadRepository.countByDeletedFalse()).thenReturn(1L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())
        `when`(historyRepository.findByGidIn(any<Collection<Long>>())).thenReturn(emptyList())

        val response = service.listDownloads()

        assertEquals(1, response.total)
        verify(downloadRepository, never()).findAll(any(org.springframework.data.domain.Pageable::class.java))
        verify(downloadRepository, never()).count()

        `when`(downloadRepository.findByLabelAndDeletedFalse(eq(5), any(org.springframework.data.domain.Pageable::class.java)))
            .thenReturn(org.springframework.data.domain.PageImpl(listOf(live)))
        `when`(downloadRepository.countByLabelAndDeletedFalse(5)).thenReturn(1L)

        assertEquals(1, service.listDownloads(labelId = 5).total)
    }

    @Test
    fun `startAll restartAll pauseAll skip tombstones`() {
        // A7-2（D5/D6/D7）: 生命周期批量遍历用 deleted=false 查询。
        val waiting = DownloadInfoEntity().apply { id = 1L; gid = 42L; token = "tok"; state = 0 }
        `when`(downloadRepository.findByStateAndDeletedFalse(0)).thenReturn(listOf(waiting))
        `when`(downloadRepository.findByStateAndDeletedFalse(1)).thenReturn(emptyList())
        `when`(downloadRepository.findByStateAndDeletedFalse(2)).thenReturn(emptyList())
        `when`(downloadRepository.findAllByDeletedFalseOrderById()).thenReturn(emptyList())
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(waiting.copyLike()))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }
        // EH 熔断短路 startDownload：本用例只钉生命周期查询口径——若放行会派生
        // 异步 worker 在 @TempDir 里跑 runDownload，清理与 worker 写目录竞态
        // （全量并行下偶发 "Failed to delete temp directory"）。
        `when`(availability.isBlocked()).thenReturn(true)

        service.startAllDownloads()
        service.pauseAllDownloads()
        service.restartAllDownloads()

        verify(downloadRepository).findByStateAndDeletedFalse(0)
        verify(downloadRepository).findByStateAndDeletedFalse(1)
        verify(downloadRepository).findByStateAndDeletedFalse(2)
        verify(downloadRepository).findAllByDeletedFalseOrderById()
        verify(downloadRepository, never()).findAll()
    }

    // ── F8: 画廊并发运行时生效 ──

    @Test
    fun `applyGalleryConcurrency resizes the worker pool at runtime`() {
        // 构造期取 yml 默认 3（core==max 不变式）。
        assertEquals(3, service.galleryConcurrency)

        service.applyGalleryConcurrency(8)
        assertEquals(8, service.galleryConcurrency)

        // 与当前值相等时是 no-op。
        service.applyGalleryConcurrency(8)
        assertEquals(8, service.galleryConcurrency)

        // 越界值钳制到 DTO 允许的 1..20；扩缩两个方向都不抛 core>max。
        service.applyGalleryConcurrency(999)
        assertEquals(20, service.galleryConcurrency)
        service.applyGalleryConcurrency(0)
        assertEquals(1, service.galleryConcurrency)
    }

    // ── 文件完整性 Wave 2（S3）：下载写入钩子 ────────────────────

    @Test
    fun `writePageWithBaseline writes a valid page and builds the downloader baseline`() {
        val dir = java.io.File(tempDir, "dl-hook-42").apply { mkdirs() }
        val bytes = fixture("valid.jpg")
        val target = java.io.File(dir, "00000001.jpg")

        val accepted = service.writePageWithBaseline(42L, 1, target, bytes)

        assertTrue(accepted)
        assertTrue(target.readBytes().contentEquals(bytes))
        // 基线 0-based 页号（下载循环 1-based → page-1），origin=downloader，未巡检。
        val baseline = hashStore[PageFileHashId(42L, 0)]
        assertNotNull(baseline)
        assertEquals("24ac74130806ae02d7e4ee72881b977601999c6c9f94ee1545ed4830459b737f", baseline!!.hash)
        assertEquals(bytes.size.toLong(), baseline.size)
        assertEquals("jpg", baseline.ext)
        assertEquals("sha256", baseline.algo)
        assertEquals("downloader", baseline.origin)
        assertTrue(baseline.createdAt > 0)
        assertNull(baseline.lastVerifiedAt)
        assertNull(baseline.verdict)
    }

    @Test
    fun `writePageWithBaseline rejects a truncated page without writing or baselining`() {
        val dir = java.io.File(tempDir, "dl-hook-reject").apply { mkdirs() }
        val target = java.io.File(dir, "00000003.jpg")

        val accepted = service.writePageWithBaseline(42L, 3, target, fixture("truncated.jpg"))

        // 拒收 = 该页下载失败：不落盘、不计 done（调用方不增计数）、不建基线；
        // 文件缺席让下一次重跑按断点续传自然重试。
        assertFalse(accepted)
        assertFalse(target.exists())
        assertTrue(hashStore.isEmpty())
    }

    @Test
    fun `writePageWithBaseline refreshes a stale baseline on overwrite`() {
        val dir = java.io.File(tempDir, "dl-hook-refresh").apply { mkdirs() }
        hashStore[PageFileHashId(42L, 0)] = PageFileHashEntity().apply {
            gid = 42L
            page = 0
            ext = "png"
            size = 9
            hash = "old-hash"
            origin = "import"
            createdAt = 1L
            lastVerifiedAt = 99L
            verdict = "ok"
        }
        val bytes = fixture("valid.jpg")
        val target = java.io.File(dir, "00000001.jpg")

        assertTrue(service.writePageWithBaseline(42L, 1, target, bytes))

        // 覆盖写已有基线的文件 = 刷新基线：同 PK 不增行，origin 回 downloader，
        // 内容已变 → 巡检结论（verdict/last_verified_at）复位为未巡检。
        assertEquals(1, hashStore.size)
        val baseline = hashStore.getValue(PageFileHashId(42L, 0))
        assertEquals("24ac74130806ae02d7e4ee72881b977601999c6c9f94ee1545ed4830459b737f", baseline.hash)
        assertEquals(bytes.size.toLong(), baseline.size)
        assertEquals("jpg", baseline.ext)
        assertEquals("downloader", baseline.origin)
        assertNull(baseline.lastVerifiedAt)
        assertNull(baseline.verdict)
        assertTrue(baseline.createdAt > 1L)
    }

    @Test
    fun `download pipeline lands a valid page with its baseline (S3 e2e)`() {
        val bytes = fixture("valid.jpg")
        val server = startPageServer(bytes)
        try {
            val row = DownloadInfoEntity().apply {
                id = 1L
                gid = 42L
                token = "tok"
                title = "T"
                state = 0
                downloadDir = java.io.File(java.io.File(tempDir, "downloads"), "42").absolutePath
            }
            `when`(availability.isBlocked()).thenReturn(false)
            `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(row))
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }
            `when`(galleryLookup.fetchPageCount(42L, "tok")).thenReturn(1)
            `when`(galleryLookup.fetchImageUrl(42L, "tok", 1)).thenReturn(server.url("/1.jpg").toString())
            `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())

            assertTrue(service.startDownload(1L))
            awaitUntil { row.state == 3 }

            assertEquals(3, row.state)
            assertEquals(1, row.done)
            val pageFile = java.io.File(row.downloadDir, "00000001.jpg")
            assertTrue(pageFile.isFile)
            assertTrue(pageFile.readBytes().contentEquals(bytes))
            val baseline = hashStore[PageFileHashId(42L, 0)]
            assertNotNull(baseline, "合法页必须建基线")
            assertEquals("downloader", baseline!!.origin)
            assertEquals("24ac74130806ae02d7e4ee72881b977601999c6c9f94ee1545ed4830459b737f", baseline.hash)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `download pipeline fails a truncated page without persisting it (S3 e2e)`() {
        val server = startPageServer(fixture("truncated.jpg"))
        try {
            val row = DownloadInfoEntity().apply {
                id = 1L
                gid = 42L
                token = "tok"
                title = "T"
                state = 0
                downloadDir = java.io.File(java.io.File(tempDir, "downloads"), "42").absolutePath
            }
            `when`(availability.isBlocked()).thenReturn(false)
            `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(row))
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }
            `when`(galleryLookup.fetchPageCount(42L, "tok")).thenReturn(1)
            `when`(galleryLookup.fetchImageUrl(42L, "tok", 1)).thenReturn(server.url("/1.jpg").toString())
            `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())

            assertTrue(service.startDownload(1L))
            awaitUntil { row.state == 4 }

            // 既有失败语义：V 门拒收等同该页失败 → 终态 FAILED，done 不计；
            // 不落盘、不建基线；重跑时该页因文件缺席被自然重试。
            assertEquals(4, row.state)
            assertEquals(0, row.done)
            assertTrue(row.error!!.contains("incomplete"))
            assertFalse(java.io.File(row.downloadDir, "00000001.jpg").exists())
            assertTrue(hashStore.isEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `resume fast-path skips existing pages under any extension (webp) without network`() {
        // 旧快路径只认 `%08d.jpg`：App/站点下载的 .webp 页被误判缺失 → 「开始/
        // 全部下载」整册慢速全网重下并写 .jpg 重复体（2026-10-11 线上 1984 页
        // 库、1932 个 .webp，done 长期为 0）。断点续传必须任意扩展名判在。
        val dir = java.io.File(java.io.File(tempDir, "downloads"), "42")
        dir.mkdirs()
        java.io.File(dir, "00000001.webp").writeBytes(ByteArray(32) { 7 }) // 非空即已有
        val server = startPageServer(fixture("valid.jpg"))
        try {
            val row = DownloadInfoEntity().apply {
                id = 1L; gid = 42L; token = "tok"; title = "T"; state = 0
                downloadDir = dir.absolutePath
            }
            `when`(availability.isBlocked()).thenReturn(false)
            `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(row))
            `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }
            `when`(galleryLookup.fetchPageCount(42L, "tok")).thenReturn(2)
            `when`(galleryLookup.fetchImageUrl(42L, "tok", 2)).thenReturn(server.url("/2.jpg").toString())
            `when`(sessionManager.okHttpClient).thenReturn(OkHttpClient())

            assertTrue(service.startDownload(1L))
            awaitUntil { row.state == 3 }

            assertEquals(3, row.state)
            assertEquals(2, row.done)
            // 已有 .webp 页零网络：fetchImageUrl 只对缺页 2 发生。
            verify(galleryLookup, never()).fetchImageUrl(42L, "tok", 1)
            verify(galleryLookup).fetchImageUrl(42L, "tok", 2)
            // 只补缺页：不产生 00000001.jpg 重复体，原 .webp 原样保留。
            assertFalse(java.io.File(dir, "00000001.jpg").exists())
            assertTrue(java.io.File(dir, "00000001.webp").isFile)
            assertTrue(java.io.File(dir, "00000002.jpg").isFile)
        } finally {
            server.shutdown()
        }
    }

    /** 测试辅助：构造同 id 的独立副本（startDownload 会把 state 写进加载的行）。 */
    private fun DownloadInfoEntity.copyLike(): DownloadInfoEntity = DownloadInfoEntity().apply {
        id = this@copyLike.id
        gid = this@copyLike.gid
        token = this@copyLike.token
        title = this@copyLike.title
        state = this@copyLike.state
        total = this@copyLike.total
        done = this@copyLike.done
        label = this@copyLike.label
        downloadDir = this@copyLike.downloadDir
    }
}
