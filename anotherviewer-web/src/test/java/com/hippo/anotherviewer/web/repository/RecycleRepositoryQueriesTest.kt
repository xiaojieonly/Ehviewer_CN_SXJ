package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.entity.EhSessionEntity
import com.hippo.anotherviewer.web.entity.FilterEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files

/**
 * v4（WP3）回收站仓库新查询 + maxLastModified（SyncClock 水位）对真实 SQLite
 * （临时文件，ddl-auto=create）的行为锁：派生查询必须可解析、可用，且墓碑/存活
 * 过滤语义与 status/回收站语义一致。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecycleRepositoryQueriesTest {

    companion object {
        private val dbDir = Files.createTempDirectory("av-recycle-queries")

        @JvmStatic
        @DynamicPropertySource
        fun sqliteProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { "jdbc:sqlite:${dbDir.resolve("recycle.db")}" }
            registry.add("spring.datasource.driver-class-name") { "org.sqlite.JDBC" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.community.dialect.SQLiteDialect" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
        }
    }

    @Autowired
    lateinit var favoriteRepository: LocalFavoriteInfoRepository

    @Autowired
    lateinit var historyRepository: HistoryInfoRepository

    @Autowired
    lateinit var downloadRepository: DownloadInfoRepository

    @Autowired
    lateinit var bookmarkRepository: BookmarkInfoRepository

    @Autowired
    lateinit var filterRepository: FilterRepository

    @Autowired
    lateinit var quickSearchRepository: QuickSearchRepository

    @Autowired
    lateinit var downloadLabelRepository: DownloadLabelRepository

    @Autowired
    lateinit var ehSessionRepository: EhSessionRepository

    @Autowired
    lateinit var userPreferenceRepository: UserPreferenceRepository

    // ── findByUsernameAndDeletedTrueOrderById（回收站列表） ──

    @Test
    fun `recycle list query returns only the user's tombstones in id order for every entity`() {
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 1; token = "t"; username = "A"; deleted = true; lastModified = 10 })
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 2; token = "t"; username = "A"; deleted = false; lastModified = 20 })
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 3; token = "t"; username = "B"; deleted = true; lastModified = 30 })
        assertEquals(listOf(1L), favoriteRepository.findByUsernameAndDeletedTrueOrderById("A").map { it.gid })
        assertTrue(favoriteRepository.findByUsernameAndDeletedTrueOrderById("C").isEmpty())

        historyRepository.save(HistoryInfoEntity().apply { gid = 11; token = "t"; username = "A"; deleted = true; lastModified = 5 })
        assertEquals(listOf(11L), historyRepository.findByUsernameAndDeletedTrueOrderById("A").map { it.gid })

        downloadRepository.save(DownloadInfoEntity().apply { gid = 21; token = "t"; username = "A"; deleted = true; lastModified = 5 })
        assertEquals(listOf(21L), downloadRepository.findByUsernameAndDeletedTrueOrderById("A").map { it.gid })

        bookmarkRepository.save(BookmarkInfoEntity().apply { gid = 31; token = "t"; username = "A"; deleted = true; lastModified = 5 })
        assertEquals(listOf(31L), bookmarkRepository.findByUsernameAndDeletedTrueOrderById("A").map { it.gid })

        filterRepository.save(FilterEntity().apply { type = 1; text = "a"; username = "A"; deleted = true; lastModified = 5 })
        assertEquals(1, filterRepository.findByUsernameAndDeletedTrueOrderById("A").size)

        quickSearchRepository.save(QuickSearchEntity().apply { name = "qs"; username = "A"; deleted = true; lastModified = 5 })
        assertEquals(listOf("qs"), quickSearchRepository.findByUsernameAndDeletedTrueOrderById("A").map { it.name })

        downloadLabelRepository.save(DownloadLabelEntity().apply { label = "L"; username = "A"; deleted = true; lastModified = 5 })
        assertEquals(listOf("L"), downloadLabelRepository.findByUsernameAndDeletedTrueOrderById("A").map { it.label })
    }

    // ── findByDeletedTrueAndLastModifiedBefore（到期清除，含 ehSession 静默清除） ──

    @Test
    fun `purge cutoff query returns expired tombstones only`() {
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 1; token = "t"; username = "A"; deleted = true; lastModified = 100 })
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 2; token = "t"; username = "A"; deleted = true; lastModified = 900 })
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 3; token = "t"; username = "A"; deleted = false; lastModified = 100 })

        assertEquals(listOf(1L), favoriteRepository.findByDeletedTrueAndLastModifiedBefore(500).map { it.gid })

        ehSessionRepository.save(EhSessionEntity().apply { username = "server"; cookies = ""; deleted = true; lastModified = 100 })
        ehSessionRepository.save(EhSessionEntity().apply { username = "server2"; cookies = ""; deleted = true; lastModified = 900 })
        assertEquals(
            listOf("server"),
            ehSessionRepository.findByDeletedTrueAndLastModifiedBefore(500).map { it.username },
            "ehSession 墓碑静默清除走同一到期查询",
        )

        quickSearchRepository.save(QuickSearchEntity().apply { name = "old"; username = "A"; deleted = true; lastModified = 1 })
        assertEquals(listOf("old"), quickSearchRepository.findByDeletedTrueAndLastModifiedBefore(500).map { it.name })
    }

    // ── countByUsernameAndDeletedFalse（status 计数） ──

    @Test
    fun `status count query ignores tombstones for every entity`() {
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 1; token = "t"; username = "A"; deleted = false; lastModified = 1 })
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 2; token = "t"; username = "A"; deleted = true; lastModified = 1 })
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 3; token = "t"; username = "B"; deleted = false; lastModified = 1 })
        assertEquals(1L, favoriteRepository.countByUsernameAndDeletedFalse("A"))

        downloadRepository.save(DownloadInfoEntity().apply { gid = 4; token = "t"; username = "A"; deleted = false; lastModified = 1 })
        downloadRepository.save(DownloadInfoEntity().apply { gid = 5; token = "t"; username = "A"; deleted = true; lastModified = 1 })
        assertEquals(1L, downloadRepository.countByUsernameAndDeletedFalse("A"))

        quickSearchRepository.save(QuickSearchEntity().apply { name = "n"; username = "A"; deleted = true; lastModified = 1 })
        assertEquals(0L, quickSearchRepository.countByUsernameAndDeletedFalse("A"))
    }

    // ── maxLastModified（SyncClock 启动水位） ──

    @Test
    fun `maxLastModified query parses and returns the highest stamp per table`() {
        assertEquals(0L, favoriteRepository.maxLastModified())
        favoriteRepository.save(LocalFavoriteInfoEntity().apply { gid = 1; token = "t"; username = "A"; lastModified = 42 })
        assertEquals(42L, favoriteRepository.maxLastModified())

        historyRepository.save(HistoryInfoEntity().apply { gid = 1; token = "t"; username = "A"; lastModified = 7 })
        assertEquals(7L, historyRepository.maxLastModified())

        downloadRepository.save(DownloadInfoEntity().apply { gid = 1; token = "t"; username = "A"; lastModified = 8 })
        assertEquals(8L, downloadRepository.maxLastModified())

        bookmarkRepository.save(BookmarkInfoEntity().apply { gid = 1; token = "t"; username = "A"; lastModified = 9 })
        assertEquals(9L, bookmarkRepository.maxLastModified())

        filterRepository.save(FilterEntity().apply { type = 0; text = "x"; username = "A"; lastModified = 10 })
        assertEquals(10L, filterRepository.maxLastModified())

        quickSearchRepository.save(QuickSearchEntity().apply { name = "x"; username = "A"; lastModified = 11 })
        assertEquals(11L, quickSearchRepository.maxLastModified())

        downloadLabelRepository.save(DownloadLabelEntity().apply { label = "x"; username = "A"; lastModified = 12 })
        assertEquals(12L, downloadLabelRepository.maxLastModified())

        ehSessionRepository.save(EhSessionEntity().apply { username = "server"; cookies = ""; lastModified = 13 })
        assertEquals(13L, ehSessionRepository.maxLastModified())

        userPreferenceRepository.save(UserPreferenceEntity().apply { username = "A"; preferences = "{}"; updatedAt = 14 })
        assertEquals(14L, userPreferenceRepository.maxLastModified(), "preferences 的时间列名为 updatedAt")
    }
}
