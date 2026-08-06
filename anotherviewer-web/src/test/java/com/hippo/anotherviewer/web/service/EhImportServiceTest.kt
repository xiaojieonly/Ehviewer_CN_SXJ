package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.EhCookieImportResult
import com.hippo.anotherviewer.web.dto.EhImportedCounts
import com.hippo.anotherviewer.web.dto.EhImportResponse
import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.entity.BlackListEntity
import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadDirnameEntity
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.entity.FilterEntity
import com.hippo.anotherviewer.web.entity.GalleryTagsEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.repository.BlackListRepository
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadDirnameRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.EhSessionRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.GalleryTagsRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 契约测试 for [EhImportService]（B3：EhViewer 备份导入）。
 *
 * - 表驱动扫描：用 sqlite-jdbc 构造 v7 样张 db，逐表断言目标表行数与字段映射
 *   （total/done 归零、state 保留、lastModified=time、dirname 孤儿、Black_List 绑定
 *   认证用户、Gallery_Tags 拍平为 (gid, tag, tagNamespace)）。
 * - 冲突语义：同 gid 二次导入默认 skipped 且不覆盖，force=true 时 upsert。
 * - 事务原子性：非法行（gid NULL）触发写入失败时整个导入中止，无部分写入。
 * - cookies 段：只收容站点域 cookie 写入 SiteSessionManager.cookieStore。
 *
 * 所有仓库均为内存 fake（key = 自然幂等键），与 SyncServiceTest 风格一致；导入
 * 直接调用真实 [EhImportService]（@Transactional 在无 Spring 代理时不生效，故
 * 原子性测试验证的是"失败即中止、无部分写入"的可观测契约）。
 */
class EhImportServiceTest {

    private lateinit var fixtures: RepoFixtures
    private lateinit var sessionManager: SiteSessionManager
    private lateinit var service: EhImportService

    @BeforeEach
    fun setUp() {
        fixtures = RepoFixtures()
        sessionManager = newSessionManager()
        service = EhImportService(
            fixtures.favoriteRepo, fixtures.historyRepo, fixtures.downloadRepo,
            fixtures.bookmarkRepo, fixtures.filterRepo, fixtures.quickSearchRepo,
            fixtures.downloadLabelRepo, fixtures.downloadDirnameRepo,
            fixtures.blackListRepo, fixtures.galleryTagsRepo, sessionManager,
        )
    }

    // ==================== 表驱动扫描（v7 样张 db） ====================

    @Test
    fun `table-driven import of a v7 sample db writes all ten target tables`() {
        val response = service.importEhViewer(file(v7SampleDb()), cookies = null, force = false, username = "alice")

        assertTrue(response.success)
        assertEquals(
            EhImportedCounts(
                downloads = 3, history = 2, filters = 2, quickSearches = 2, labels = 2,
                bookmarks = 1, favorites = 1, dirnames = 2, blackList = 1, galleryTags = 5,
            ),
            response.imported,
        )
        assertEquals(0, response.skipped)

        assertEquals(3, fixtures.downloadStore.size)
        assertEquals(2, fixtures.historyStore.size)
        assertEquals(2, fixtures.filterStore.size)
        assertEquals(2, fixtures.quickSearchStore.size)
        assertEquals(2, fixtures.labelStore.size)
        assertEquals(1, fixtures.bookmarkStore.size)
        assertEquals(1, fixtures.favoriteStore.size)
        assertEquals(2, fixtures.dirnameStore.size)
        assertEquals(1, fixtures.blackListStore.size)
        assertEquals(5, fixtures.galleryTagsStore.getValue(1001L).size)
    }

    @Test
    fun `download rows keep state and stamp total done zero with lastModified equals time`() {
        service.importEhViewer(file(v7SampleDb()), cookies = null, force = false, username = "alice")

        val alpha = fixtures.downloadStore.getValue(1001L)
        assertEquals("tok1001", alpha.token)
        assertEquals("Alpha", alpha.title)
        assertEquals("http://img.ehgt.org/t1001.jpg", alpha.thumb)
        assertEquals(4, alpha.category)
        assertEquals("2020-01-01 00:00", alpha.posted)
        assertEquals("bob", alpha.uploader)
        assertEquals(4.5f, alpha.rating)
        assertEquals(2, alpha.state)
        assertEquals(0, alpha.total)
        assertEquals(0, alpha.done)
        assertEquals(1_700_000_000_000L, alpha.time)
        assertEquals(1_700_000_000_000L, alpha.lastModified)
        assertEquals("alice", alpha.username)
        assertEquals(fixtures.labelStore.getValue("Favorites").id.toInt(), alpha.label)
        assertNull(alpha.downloadDir)

        assertEquals(1, fixtures.downloadStore.getValue(1002L).state)
        val gamma = fixtures.downloadStore.getValue(1003L)
        assertEquals(3, gamma.state)
        assertEquals("sdcard/ehviewer/Gamma", gamma.downloadDir)
    }

    @Test
    fun `orphan dirname and blacklist are imported and gallery tags flatten namespaces`() {
        service.importEhViewer(file(v7SampleDb()), cookies = null, force = false, username = "alice")

        assertEquals("dirname_alpha", fixtures.dirnameStore.getValue(1001L).dirname)
        assertEquals("orphan_dir", fixtures.dirnameStore.getValue(9999L).dirname)

        assertTrue(fixtures.blackListStore.containsKey("alice"))

        val tags = fixtures.galleryTagsStore.getValue(1001L).map { Triple(it.gid, it.tag, it.tagNamespace) }.toSet()
        assertEquals(
            setOf(
                Triple(1001L, "tanaka", "artist"),
                Triple(1001L, "shion", "female"),
                Triple(1001L, "megumi", "female"),
                Triple(1001L, "chinese", "language"),
                Triple(1001L, "japanese", "language"),
            ),
            tags,
        )
    }

    @Test
    fun `history filters quick searches bookmarks and favorites are bound to the user`() {
        service.importEhViewer(file(v7SampleDb()), cookies = null, force = false, username = "alice")

        val histA = fixtures.historyStore.getValue(2001L)
        assertEquals(1, histA.mode)
        assertEquals(1_700_000_100_000L, histA.lastModified)
        assertEquals(2, fixtures.historyStore.getValue(2002L).mode)
        assertTrue(fixtures.historyStore.values.all { it.username == "alice" })

        val uploader = fixtures.filterStore.getValue("1:uploader:foo")
        assertEquals(1, uploader.type)
        assertTrue(uploader.enabled)
        assertEquals("alice", uploader.username)
        val tag = fixtures.filterStore.getValue("2:tag:bar")
        assertEquals(2, tag.type)
        assertFalse(tag.enabled)
        assertEquals(0, tag.lastModified)
        assertEquals("alice", tag.username)

        assertEquals("artist:foo", fixtures.quickSearchStore.getValue("Search A").keyword)
        assertEquals(1024, fixtures.quickSearchStore.getValue("Search B").category)
        assertTrue(fixtures.quickSearchStore.values.all { it.username == "alice" })

        assertEquals("42", fixtures.bookmarkStore.getValue(3001L).note)
        assertTrue(fixtures.bookmarkStore.values.all { it.username == "alice" })
        assertTrue(fixtures.favoriteStore.values.all { it.username == "alice" })
        assertTrue(fixtures.labelStore.values.all { it.username == "alice" })
    }

    // ==================== 冲突语义 ====================

    @Test
    fun `re-import of an existing gid is skipped by default and upserted when force`() {
        val base = databaseBytes { c ->
            val s = c.createStatement()
            s.execute("CREATE TABLE DOWNLOAD_LABELS (label TEXT, time INTEGER)")
            s.execute("INSERT INTO DOWNLOAD_LABELS VALUES ('Favorites', 1699999999000)")
            s.execute("CREATE TABLE DOWNLOADS (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, category INTEGER, state INTEGER, time INTEGER, label TEXT)")
            s.execute("INSERT INTO DOWNLOADS VALUES (5001, 'tok5001', 'Original', 4, 2, 1700000000000, 'Favorites')")
            s.execute("CREATE TABLE HISTORY (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, mode INTEGER, time INTEGER)")
            s.execute("INSERT INTO HISTORY VALUES (6001, 'tok6001', 'H', 0, 1700000100000)")
            s.close()
        }

        val first = service.importEhViewer(file(base), cookies = null, force = false, username = "alice")
        assertEquals(EhImportedCounts(downloads = 1, history = 1, labels = 1), first.imported)
        assertEquals("Original", fixtures.downloadStore.getValue(5001L).title)

        val updated = updateBytes(base, "UPDATE DOWNLOADS SET title = 'Updated' WHERE gid = 5001")

        val second = service.importEhViewer(file(updated), cookies = null, force = false, username = "alice")
        assertEquals(0, second.imported.downloads)
        assertEquals(2, second.skipped)
        assertEquals("Original", fixtures.downloadStore.getValue(5001L).title)

        val third = service.importEhViewer(file(updated), cookies = null, force = true, username = "alice")
        assertEquals(1, third.imported.downloads)
        assertEquals("Updated", fixtures.downloadStore.getValue(5001L).title)
        assertEquals(1_700_000_000_000L, fixtures.downloadStore.getValue(5001L).lastModified)
    }

    // ==================== 事务原子性 ====================

    @Test
    fun `an invalid row aborts the import leaving no partial writes`() {
        val bad = databaseBytes { c ->
            val s = c.createStatement()
            s.execute("CREATE TABLE DOWNLOAD_LABELS (label TEXT, time INTEGER)")
            s.execute("CREATE TABLE DOWNLOADS (gid INTEGER, token TEXT, title TEXT, category INTEGER, state INTEGER, time INTEGER)")
            s.execute("INSERT INTO DOWNLOADS VALUES (NULL, 'tok-bad', 'Bad', 4, 1, 1700000000000)")
            s.execute("INSERT INTO DOWNLOADS VALUES (7001, 'tok7001', 'Good', 4, 1, 1700000001000)")
            s.execute("CREATE TABLE HISTORY (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, mode INTEGER, time INTEGER)")
            s.execute("INSERT INTO HISTORY VALUES (6001, 'tok6001', 'H', 0, 1700000100000)")
            s.close()
        }
        fixtures.failOnNullGid = true

        val thrown = assertThrows(IllegalStateException::class.java) {
            service.importEhViewer(file(bad), cookies = null, force = false, username = "alice")
        }
        assertTrue(thrown.message!!.contains("gid"))

        assertTrue(fixtures.downloadStore.isEmpty())
        assertTrue(fixtures.historyStore.isEmpty())
        assertTrue(fixtures.labelStore.isEmpty())
    }

    // ==================== cookies 段 ====================

    @Test
    fun `cookie import keeps only site domains and writes them to the shared cookie store`() {
        val cookieDb = databaseBytes { c ->
            val s = c.createStatement()
            s.execute(
                "CREATE TABLE OK_HTTP_3_COOKIE (NAME TEXT, VALUE TEXT, EXPIRES_AT INTEGER, DOMAIN TEXT, PATH TEXT, " +
                    "SECURE INTEGER, HTTP_ONLY INTEGER, PERSISTENT INTEGER, HOST_ONLY INTEGER)"
            )
            s.execute("INSERT INTO OK_HTTP_3_COOKIE VALUES ('ipb_member_id', '111111', 0, '.e-hentai.org', '/', 0, 1, 1, 0)")
            s.execute("INSERT INTO OK_HTTP_3_COOKIE VALUES ('ipb_pass_hash', 'aaaaaa', 0, '.exhentai.org', '/', 1, 1, 1, 0)")
            s.execute("INSERT INTO OK_HTTP_3_COOKIE VALUES ('igneous', 'xyz', 0, '.ehgt.org', '/', 0, 1, 1, 0)")
            s.execute("INSERT INTO OK_HTTP_3_COOKIE VALUES ('session', 'other', 0, '.example.com', '/', 0, 0, 0, 0)")
            s.close()
        }

        val response = service.importEhViewer(
            file(databaseBytes { c ->
                // 空 sqlite 文件为 0 字节（SQLite 惰性写盘），会命中 prep 的空文件校验；
                // 造一个最小非空库，保证走 cookie 段逻辑。
                c.createStatement().use { it.execute("CREATE TABLE android_metadata (locale TEXT)") }
            }),
            cookies = MockMultipartFile("cookies", "cookies.db", "application/octet-stream", cookieDb),
            force = false,
            username = "alice",
        )

        assertEquals(EhCookieImportResult(imported = 3, siteDomain = 3), response.cookies)

        val all = sessionManager.cookieStore.getAll()
        assertEquals(setOf("e-hentai.org", "exhentai.org", "ehgt.org"), all.keys)
        val names = all.values.flatten().map { it.name }.toSet()
        assertEquals(setOf("ipb_member_id", "ipb_pass_hash", "igneous"), names)
        assertTrue("session" !in names)
    }

    // ==================== 异步 Job worker（B2 异步化） ====================

    @Test
    fun `async import via JobService completes with correct result and progress totals`() {
        val jobService = newJobService()
        val dbPath = writeTempDb(v7SampleDb())
        lateinit var job: Job

        job = jobService.submit(JobType.IMPORT, {}) { handle ->
            // worker 自行写终态 result（与 ImportController 异步 worker 同构）。
            job.result = service.runImport(dbPath, cookieBytes = null, force = false, username = "alice", handle = handle)
        }

        val done = awaitState(jobService, job, JobState.COMPLETED)
        val result = done.result as EhImportResponse
        assertTrue(result.success)
        assertEquals(
            EhImportedCounts(
                downloads = 3, history = 2, filters = 2, quickSearches = 2, labels = 2,
                bookmarks = 1, favorites = 1, dirnames = 2, blackList = 1, galleryTags = 5,
            ),
            result.imported,
        )
        assertEquals(EhCookieImportResult(imported = 0, siteDomain = 0), result.cookies)
        assertEquals(0, result.skipped)
        assertEquals("写入数据库", done.stage)
        // total = 源表 COUNT(*) 求和（3+2+1+1+2+2+2+2+1+1 = 17），末段 processed == total。
        assertEquals(17L, done.total)
        assertEquals(17L, done.processed)
        assertEquals(3, fixtures.downloadStore.size)
        assertEquals(5, fixtures.galleryTagsStore.getValue(1001L).size)
        assertTrue(!Files.exists(dbPath), "worker finally 应清理 db 临时文件")
    }

    @Test
    fun `async import failure fails the job with error and no partial writes`() {
        val bad = databaseBytes { c ->
            val s = c.createStatement()
            s.execute("CREATE TABLE DOWNLOADS (gid INTEGER, token TEXT, title TEXT, category INTEGER, state INTEGER, time INTEGER)")
            s.execute("INSERT INTO DOWNLOADS VALUES (NULL, 'tok-bad', 'Bad', 4, 1, 1700000000000)")
            s.execute("INSERT INTO DOWNLOADS VALUES (7001, 'tok7001', 'Good', 4, 1, 1700000001000)")
            s.close()
        }
        fixtures.failOnNullGid = true
        val jobService = newJobService()
        val dbPath = writeTempDb(bad)

        val job = jobService.submit(JobType.IMPORT, {}) { handle ->
            service.runImport(dbPath, cookieBytes = null, force = false, username = "alice", handle = handle)
        }

        val done = awaitState(jobService, job, JobState.FAILED)
        assertTrue(done.error!!.contains("gid"))
        assertNull(done.result)
        assertTrue(fixtures.downloadStore.isEmpty(), "失败不应留下部分写入")
    }

    // ==================== helpers ====================

    private fun newJobService(): JobService =
        JobService(InMemoryJobStore(), mock(ApplicationEventPublisher::class.java))

    private fun writeTempDb(bytes: ByteArray): Path {
        val tmp = Files.createTempFile("ehimport-worker-", ".db")
        Files.write(tmp, bytes)
        return tmp
    }

    /** 轮询等待异步 job 到达终态（默认 5s 超时），与 JobServiceTest 风格一致。 */
    private fun awaitState(jobService: JobService, job: Job, expected: JobState, timeoutMs: Long = 5000): Job {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = jobService.getJob(job.jobId)!!
            if (current.state == expected) return current
            Thread.sleep(10)
        }
        throw AssertionError("job ${job.jobId} 未在 ${timeoutMs}ms 内到达 $expected（当前 ${jobService.getJob(job.jobId)?.state}）")
    }

    private fun newSessionManager(): SiteSessionManager {
        val serverConfig = mock(ServerConfigService::class.java)
        `when`(serverConfig.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        `when`(serverConfig.get(anyString(), anyString())).thenReturn("")
        val config = SiteCoreConfigProperties()
        config.security.encryptionKeyPath = "${Files.createTempDirectory("ehimport-key").toAbsolutePath()}/security.key"
        return SiteSessionManager(
            WebProxyManager(serverConfig),
            serverConfig,
            mock(EhSessionRepository::class.java),
            EncryptionService(),
            config,
        )
    }

    private fun file(db: ByteArray, filename: String = "ehviewer.db") =
        MockMultipartFile("file", filename, "application/octet-stream", db)

    private fun databaseBytes(build: (Connection) -> Unit): ByteArray {
        val tmp = Files.createTempFile("ehimport-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:${tmp.toAbsolutePath()}").use { conn -> build(conn) }
            return Files.readAllBytes(tmp)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun updateBytes(bytes: ByteArray, sql: String): ByteArray {
        val tmp = Files.createTempFile("ehimport-", ".db")
        try {
            Files.write(tmp, bytes)
            DriverManager.getConnection("jdbc:sqlite:${tmp.toAbsolutePath()}").use { conn ->
                conn.createStatement().use { it.execute(sql) }
            }
            return Files.readAllBytes(tmp)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun v7SampleDb(): ByteArray = databaseBytes { c ->
        val s = c.createStatement()
        s.execute(
            "CREATE TABLE DOWNLOADS (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, title_jpn TEXT, " +
                "thumb TEXT, category INTEGER, posted TEXT, uploader TEXT, rating REAL, rated INTEGER, " +
                "simple_language TEXT, simple_tags TEXT, thumb_width INTEGER, thumb_height INTEGER, " +
                "span_size INTEGER, span_index INTEGER, span_group_index INTEGER, favorite_slot INTEGER, " +
                "favorite_name TEXT, pages INTEGER, state INTEGER, legacy INTEGER, time INTEGER, " +
                "archive_uri TEXT, label TEXT)"
        )
        s.execute(
            "INSERT INTO DOWNLOADS VALUES (1001, 'tok1001', 'Alpha', 'アルファ', 'http://img.ehgt.org/t1001.jpg', " +
                "4, '2020-01-01 00:00', 'bob', 4.5, 1, 'chinese', 'simple', 200, 280, 2, 0, 0, -1, NULL, 20, " +
                "2, 0, 1700000000000, NULL, 'Favorites')"
        )
        s.execute(
            "INSERT INTO DOWNLOADS VALUES (1002, 'tok1002', 'Beta', NULL, 'http://img.ehgt.org/t1002.jpg', " +
                "8, '2020-02-02 00:00', 'carol', 3.0, 0, NULL, NULL, 180, 260, 1, 0, 0, -2, NULL, 15, " +
                "1, 0, 1700000001000, NULL, NULL)"
        )
        s.execute(
            "INSERT INTO DOWNLOADS VALUES (1003, 'tok1003', 'Gamma', NULL, NULL, " +
                "16, '2021-03-03 00:00', 'dave', 2.0, 0, NULL, NULL, 150, 220, 3, 1, 0, -2, NULL, 30, " +
                "3, 0, 1700000002000, 'sdcard/ehviewer/Gamma', 'Reading')"
        )
        s.execute("CREATE TABLE HISTORY (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, mode INTEGER, time INTEGER)")
        s.execute("INSERT INTO HISTORY VALUES (2001, 'tok2001', 'Hist A', 1, 1700000100000)")
        s.execute("INSERT INTO HISTORY VALUES (2002, 'tok2002', 'Hist B', 2, 1700000101000)")
        s.execute("CREATE TABLE BOOKMARKS (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, time INTEGER, page INTEGER)")
        s.execute("INSERT INTO BOOKMARKS VALUES (3001, 'tok3001', 'Bookmark A', 1700000200000, 42)")
        s.execute("CREATE TABLE LOCAL_FAVORITES (gid INTEGER PRIMARY KEY, token TEXT, title TEXT, time INTEGER)")
        s.execute("INSERT INTO LOCAL_FAVORITES VALUES (4001, 'tok4001', 'Fav A', 1700000300000)")
        s.execute("CREATE TABLE FILTER (mode INTEGER, text TEXT, enable INTEGER)")
        s.execute("INSERT INTO FILTER VALUES (1, 'uploader:foo', 1)")
        s.execute("INSERT INTO FILTER VALUES (2, 'tag:bar', 0)")
        s.execute("CREATE TABLE QUICK_SEARCH (name TEXT, mode INTEGER, category INTEGER, keyword TEXT, advance_search INTEGER, min_rating INTEGER, page_from INTEGER, page_to INTEGER, time INTEGER)")
        s.execute("INSERT INTO QUICK_SEARCH VALUES ('Search A', 1, 0, 'artist:foo', 0, 0, 0, 0, 1700000400000)")
        s.execute("INSERT INTO QUICK_SEARCH VALUES ('Search B', 2, 1024, 'language:chinese', 1, 3, 1, 10, 1700000401000)")
        s.execute("CREATE TABLE DOWNLOAD_LABELS (label TEXT, time INTEGER)")
        s.execute("INSERT INTO DOWNLOAD_LABELS VALUES ('Favorites', 1699999999000)")
        s.execute("INSERT INTO DOWNLOAD_LABELS VALUES ('Reading', 1699999998000)")
        s.execute("CREATE TABLE DOWNLOAD_DIRNAME (gid INTEGER, dirname TEXT)")
        s.execute("INSERT INTO DOWNLOAD_DIRNAME VALUES (1001, 'dirname_alpha')")
        s.execute("INSERT INTO DOWNLOAD_DIRNAME VALUES (9999, 'orphan_dir')")
        s.execute("CREATE TABLE Black_List (hash TEXT, time INTEGER)")
        s.execute("INSERT INTO Black_List VALUES ('abc123', 1700000500000)")
        s.execute(
            "CREATE TABLE Gallery_Tags (gid INTEGER, rows TEXT, artist TEXT, cosplayer TEXT, character TEXT, " +
                "female TEXT, \"group\" TEXT, language TEXT, male TEXT, misc TEXT, mixed TEXT, other TEXT, " +
                "parody TEXT, reclass TEXT)"
        )
        s.execute(
            "INSERT INTO Gallery_Tags VALUES (1001, NULL, 'tanaka', NULL, NULL, 'shion, megumi', NULL, " +
                "'chinese, japanese', NULL, NULL, NULL, NULL, NULL, NULL)"
        )
        s.close()
    }

    /** In-memory repository fakes keyed by natural idempotency key; stores are asserted after import. */
    private class RepoFixtures {
        val favoriteStore = ConcurrentHashMap<Long, LocalFavoriteInfoEntity>()
        val historyStore = ConcurrentHashMap<Long, HistoryInfoEntity>()
        val downloadStore = ConcurrentHashMap<Long, DownloadInfoEntity>()
        val bookmarkStore = ConcurrentHashMap<Long, BookmarkInfoEntity>()
        val filterStore = ConcurrentHashMap<String, FilterEntity>()
        val quickSearchStore = ConcurrentHashMap<String, QuickSearchEntity>()
        val labelStore = ConcurrentHashMap<String, DownloadLabelEntity>()
        val dirnameStore = ConcurrentHashMap<Long, DownloadDirnameEntity>()
        val blackListStore = ConcurrentHashMap<String, BlackListEntity>()
        val galleryTagsStore = ConcurrentHashMap<Long, MutableList<GalleryTagsEntity>>()

        var failOnNullGid: Boolean = false

        private val labelIds = AtomicLong(1)

        val favoriteRepo: LocalFavoriteInfoRepository = mock(LocalFavoriteInfoRepository::class.java).apply {
            `when`(save(any(LocalFavoriteInfoEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<LocalFavoriteInfoEntity>(0)
                favoriteStore[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> favoriteStore[inv.getArgument<Long>(0)] }
        }

        val historyRepo: HistoryInfoRepository = mock(HistoryInfoRepository::class.java).apply {
            `when`(save(any(HistoryInfoEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<HistoryInfoEntity>(0)
                historyStore[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> historyStore[inv.getArgument<Long>(0)] }
        }

        val downloadRepo: DownloadInfoRepository = mock(DownloadInfoRepository::class.java).apply {
            `when`(save(any(DownloadInfoEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<DownloadInfoEntity>(0)
                if (failOnNullGid && e.gid == 0L) throw IllegalStateException("gid must not be null")
                downloadStore[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> downloadStore[inv.getArgument<Long>(0)] }
        }

        val bookmarkRepo: BookmarkInfoRepository = mock(BookmarkInfoRepository::class.java).apply {
            `when`(save(any(BookmarkInfoEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<BookmarkInfoEntity>(0)
                bookmarkStore[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> bookmarkStore[inv.getArgument<Long>(0)] }
        }

        val filterRepo: FilterRepository = mock(FilterRepository::class.java).apply {
            `when`(save(any(FilterEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<FilterEntity>(0)
                filterStore["${e.type}:${e.text}"] = e
                e
            }
            `when`(findAll()).thenAnswer { filterStore.values.toList() }
        }

        val quickSearchRepo: QuickSearchRepository = mock(QuickSearchRepository::class.java).apply {
            `when`(save(any(QuickSearchEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<QuickSearchEntity>(0)
                quickSearchStore[e.name] = e
                e
            }
            `when`(findByName(anyString())).thenAnswer { inv -> quickSearchStore[inv.getArgument<String>(0)] }
        }

        val downloadLabelRepo: DownloadLabelRepository = mock(DownloadLabelRepository::class.java).apply {
            `when`(save(any(DownloadLabelEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<DownloadLabelEntity>(0)
                if (e.id == 0L) e.id = labelIds.getAndIncrement()
                labelStore[e.label] = e
                e
            }
            `when`(findByLabel(anyString())).thenAnswer { inv -> labelStore[inv.getArgument<String>(0)] }
        }

        val downloadDirnameRepo: DownloadDirnameRepository = mock(DownloadDirnameRepository::class.java).apply {
            `when`(save(any(DownloadDirnameEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<DownloadDirnameEntity>(0)
                dirnameStore[e.gid] = e
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv -> dirnameStore[inv.getArgument<Long>(0)] }
        }

        val blackListRepo: BlackListRepository = mock(BlackListRepository::class.java).apply {
            `when`(save(any(BlackListEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<BlackListEntity>(0)
                blackListStore[e.user] = e
                e
            }
            `when`(findByUser(anyString())).thenAnswer { inv -> blackListStore[inv.getArgument<String>(0)] }
        }

        val galleryTagsRepo: GalleryTagsRepository = mock(GalleryTagsRepository::class.java).apply {
            `when`(save(any(GalleryTagsEntity::class.java))).thenAnswer { inv ->
                val e = inv.getArgument<GalleryTagsEntity>(0)
                val list = galleryTagsStore.computeIfAbsent(e.gid) { mutableListOf() }
                list.removeIf { it.tag == e.tag && it.tagNamespace == e.tagNamespace }
                list.add(e)
                e
            }
            `when`(findByGid(anyLong())).thenAnswer { inv ->
                galleryTagsStore[inv.getArgument<Long>(0)]?.toList() ?: emptyList<GalleryTagsEntity>()
            }
            doAnswer { inv ->
                galleryTagsStore.remove(inv.getArgument<Long>(0))
            }.`when`(this).deleteByGid(anyLong())
        }
    }
}
