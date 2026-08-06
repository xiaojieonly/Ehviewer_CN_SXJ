package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * 备份/还原端到端：真实 SQLite db（VACUUM INTO 一致性快照）+ 真实 7z 分片
 * （LZMA2）+ SHA-256 校验 + 篡改拒绝 + downloads/cache 分片。
 */
class BackupServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataDir: Path
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repo: ServerConfigRepository
    private lateinit var service: BackupService
    private var savedConfigs: MutableList<ServerConfigEntity> = mutableListOf()

    @BeforeEach
    fun setUp() {
        dataDir = tempDir.toPath()
        jdbc = JdbcTemplate(DriverManagerDataSource("jdbc:sqlite:${dataDir.resolve("anotherviewer.db")}"))
        jdbc.execute("CREATE TABLE gallery (id INTEGER PRIMARY KEY, title TEXT)")
        jdbc.update("INSERT INTO gallery VALUES (1, 'hello')")
        Files.write(dataDir.resolve("security.key"), "test-secret-key".toByteArray())

        repo = mock(ServerConfigRepository::class.java)
        `when`(repo.findAll()).thenAnswer {
            listOf(ServerConfigEntity().apply {
                key = ServerConfigService.KEY_DOWNLOAD_PATH
                value = dataDir.resolve("downloads").toString()
            })
        }
        `when`(repo.saveAll(anyCollection())).thenAnswer { inv ->
            savedConfigs.addAll(inv.getArgument<Collection<ServerConfigEntity>>(0))
            savedConfigs
        }

        val config = SiteCoreConfigProperties().apply {
            dataDir = tempDir.absolutePath
            download.path = this@BackupServiceTest.dataDir.resolve("downloads").toString()
            download.cachePath = this@BackupServiceTest.dataDir.resolve("cache").toString()
        }
        service = BackupService(config, jdbc, repo, listOf(NoopBackupEncryptor()))
    }

    @Test
    fun `export produces manifest and slices with matching sha256`() {
        val result = service.export(includeDownloads = false)

        assertTrue(Files.isRegularFile(dataDir.resolve("backups/manifest.json")))
        assertEquals(1, result.manifest.formatVersion)
        assertFalse(result.manifest.includesDownloads)
        assertTrue(result.manifest.slices.size >= 2) // 核心分片 + security.key
        result.manifest.slices.forEach { slice ->
            val file = result.slices.first { it.fileName.toString() == slice.name }
            assertTrue(Files.isRegularFile(file))
            assertEquals(slice.sizeBytes, Files.size(file))
            assertEquals(slice.sha256, sha256Hex(file))
        }
    }

    @Test
    fun `export to restore round trip preserves db key and config`() {
        // 预置连接池残留的旧 db sidecar：还原后必须被清掉，不得留在新 db 旁边
        // （否则 SQLite 打开新快照时可能误应用旧日志，见 applyCoreFile 的顺序约定）。
        Files.write(dataDir.resolve("anotherviewer.db-wal"), byteArrayOf(1, 2, 3))
        Files.write(dataDir.resolve("anotherviewer.db-shm"), byteArrayOf(9, 9))

        val result = service.export(includeDownloads = false)
        val slices = result.slices.associateBy { it.fileName.toString() }

        assertTrue(service.restore(result.manifest, slices))

        val checkJdbc = JdbcTemplate(DriverManagerDataSource("jdbc:sqlite:${dataDir.resolve("anotherviewer.db")}"))
        assertEquals("hello", checkJdbc.queryForObject("SELECT title FROM gallery WHERE id = 1", String::class.java))
        assertEquals("test-secret-key", Files.readString(dataDir.resolve("security.key")))
        assertTrue(Files.isRegularFile(dataDir.resolve("anotherviewer.db.bak")))
        assertTrue(savedConfigs.any { it.key == ServerConfigService.KEY_DOWNLOAD_PATH })
        assertFalse(Files.exists(dataDir.resolve("anotherviewer.db-wal")))
        assertFalse(Files.exists(dataDir.resolve("anotherviewer.db-shm")))
    }

    @Test
    fun `tampered slice is rejected and original files untouched`() {
        val result = service.export(includeDownloads = false)
        val slices = result.slices.associateBy { it.fileName.toString() }
        Files.write(slices.getValue("slice-01.7z"), byteArrayOf(0, 1, 2, 3, 4))

        assertThrows(IllegalStateException::class.java) { service.restore(result.manifest, slices) }

        val checkJdbc = JdbcTemplate(DriverManagerDataSource("jdbc:sqlite:${dataDir.resolve("anotherviewer.db")}"))
        assertEquals("hello", checkJdbc.queryForObject("SELECT title FROM gallery WHERE id = 1", String::class.java))
        assertFalse(Files.exists(dataDir.resolve("anotherviewer.db.bak")))
    }

    @Test
    fun `export with downloads includes download and cache slices`() {
        val galleryDir = dataDir.resolve("downloads/123456")
        Files.createDirectories(galleryDir)
        Files.write(galleryDir.resolve("001.jpg"), byteArrayOf(1, 2, 3))
        val cacheDir = dataDir.resolve("cache/abc")
        Files.createDirectories(cacheDir)
        Files.write(cacheDir.resolve("thumb.jpg"), byteArrayOf(9, 9, 9))

        val result = service.export(includeDownloads = true)

        assertTrue(result.manifest.includesDownloads)
        assertTrue(result.manifest.slices.size >= 4) // 核心 + key + downloads + cache
        val entryNames = result.slices.flatMap { extractEntryNames(it) }
        assertTrue(entryNames.any { it.startsWith("downloads/123456/") })
        assertTrue(entryNames.any { it.startsWith("cache/abc/") })
    }

    @Test
    fun `restore reports per-slice progress via job handle`() {
        val result = service.export(includeDownloads = false)
        val slices = result.slices.associateBy { it.fileName.toString() }

        val updates = mutableListOf<Pair<String, Long>>()
        val handle = object : JobService.JobHandle {
            override fun progress(stage: String, processed: Long, total: Long) { updates += stage to processed }
            override fun stage(stage: String) = Unit
        }

        assertTrue(service.restore(result.manifest, slices, handle))

        val expected = result.manifest.slices.size.toLong()
        assertEquals(expected.toInt(), updates.size)
        assertTrue(updates.all { it.first.startsWith("还原数据库 ") })
        assertEquals(expected, updates.last().second) // 末次 processed = 分片数
        assertEquals(1L, updates.first().second) // 逐片递增
    }

    @Test
    fun `export without security key still produces core slice`() {
        Files.deleteIfExists(dataDir.resolve("security.key"))

        val result = service.export(includeDownloads = false)

        assertEquals(listOf("slice-01.7z"), result.manifest.slices.map { it.name })
    }

    private fun sha256Hex(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractEntryNames(slice: Path): List<String> {
        val names = mutableListOf<String>()
        SevenZFile(slice.toFile()).use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                names += entry.name
                entry = sevenZ.nextEntry
            }
        }
        return names
    }
}
