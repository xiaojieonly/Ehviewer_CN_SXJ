package com.hippo.anotherviewer.web.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.BackupManifest
import com.hippo.anotherviewer.web.dto.BackupResult
import com.hippo.anotherviewer.web.dto.BackupSlice
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors

/** 拒绝 zip/7z 条目路径穿越：绝对路径、`..` 段、盘符前缀均不允许。 */
internal fun isSafeArchiveEntryName(name: String): Boolean {
    val normalized = name.replace('\\', '/')
    if (normalized.startsWith("/")) return false
    if (normalized == ".." || normalized.startsWith("../")) return false
    if (normalized.contains("/../")) return false
    return !Regex("^[A-Za-z]:/").containsMatchIn(normalized)
}

/**
 * 备份/还原（ADR-0002）：产物为多个独立 7z 分片 + manifest.json，固定 4 线程池
 * 并行压缩；分片可独立存储/传输/校验（拷到 NAS、U 盘异地备份）。
 *
 * 归档内布局与路径无关（迁移友好）：
 * - slice-01.7z：`anotherviewer.db`（SQLite 一致性快照）+ `config.json`（ServerConfig KV）
 * - slice-02.7z：`security.key`（dataDir 下，缺失跳过）
 * - slice-NN.7z：`downloads/<子目录>/...`、`cache/<子目录>/...`（includeDownloads=true 时）
 *
 * 加密经 [BackupEncryptor] SPI 预留，v1 为 Noop 直通。
 */
@Service
class BackupService(
    private val config: SiteCoreConfigProperties,
    private val jdbcTemplate: JdbcTemplate,
    private val serverConfigRepo: ServerConfigRepository,
    private val encryptors: List<BackupEncryptor>,
) {
    private val logger = LoggerFactory.getLogger(BackupService::class.java)
    private val mapper = jacksonObjectMapper()
    private val encryptor: BackupEncryptor get() = encryptors.firstOrNull() ?: NoopBackupEncryptor()

    /** 导出备份：分片 + manifest.json 落盘到 `<dataDir>/backups/`。 */
    fun export(includeDownloads: Boolean): BackupResult {
        val backupsDir = backupsDir()
        Files.createDirectories(backupsDir)
        clearOldArtifacts(backupsDir)

        val workDir = Files.createTempDirectory("backup-export-")
        try {
            // SQLite 一致性快照：VACUUM INTO 目标必须为绝对路径，SQL 字面量内单引号翻倍转义。
            val snapshotDb = workDir.resolve("anotherviewer.db")
            vacuumInto(snapshotDb)
            val configJson = workDir.resolve("config.json")
            // 导出库中原值（含 enc:v1: 前缀密文），不泄露明文口令。
            mapper.writeValue(configJson.toFile(), serverConfigRepo.findAll().associate { it.key to it.value })

            val specs = mutableListOf<List<Pair<String, Path>>>()
            specs += listOf("anotherviewer.db" to snapshotDb, "config.json" to configJson)
            val securityKey = securityKeyPath()
            if (Files.isRegularFile(securityKey)) {
                specs += listOf("security.key" to securityKey)
            }
            if (includeDownloads) {
                listOf(
                    config.download.path.takeIf { it.isNotBlank() } to "downloads",
                    config.download.cachePath.takeIf { it.isNotBlank() } to "cache",
                ).forEach { (root, prefix) ->
                    val rootPath = root?.let { Path.of(it) }
                    if (rootPath != null && Files.isDirectory(rootPath)) {
                        Files.list(rootPath).use { children ->
                            children.sorted().forEach { child ->
                                specs += listOf("$prefix/${child.fileName}" to child)
                            }
                        }
                    }
                }
            }

            val pool = Executors.newFixedThreadPool(4)
            try {
                val slices = specs.mapIndexed { i, entries ->
                    pool.submit<BackupSlice> {
                        createSlice(backupsDir.resolve("slice-%02d.7z".format(i + 1)), entries)
                    }
                }.map { it.get() }
                val manifest = BackupManifest(
                    exportedAt = Instant.now().toString(),
                    appVersion = appVersion(),
                    slices = slices,
                    includesDownloads = includeDownloads,
                )
                mapper.writeValue(backupsDir.resolve("manifest.json").toFile(), manifest)
                return BackupResult(manifest, slices.map { backupsDir.resolve(it.name) }, backupsDir)
            } finally {
                pool.shutdownNow()
            }
        } finally {
            runCatching { workDir.toFile().deleteRecursively() }
        }
    }

    /**
     * 还原备份：逐片 SHA-256 校验（不符抛异常拒绝）→ 解压到临时目录 →
     * anotherviewer.db / security.key 旧文件改名 `.bak` 后替换（已存在追加时间戳），
     * config.json 回写 server_config 表，downloads/cache 覆盖合并。异常时回滚
     * 已改名的文件并清理临时目录。
     *
     * @param handle 异步 Job 进度句柄（可选）：解包阶段按分片粒度上报
     *   `progress("还原数据库 i/n", ...)`；同步调用（GET 兼容路径）传 null。
     */
    fun restore(
        manifest: BackupManifest,
        slices: Map<String, Path>,
        handle: JobService.JobHandle? = null,
    ): Boolean {
        require(manifest.formatVersion == 1) { "不支持的备份格式版本: ${manifest.formatVersion}" }
        manifest.slices.forEach { slice ->
            val file = slices[slice.name] ?: throw IllegalArgumentException("备份包缺少分片: ${slice.name}")
            require(Files.isRegularFile(file)) { "分片不存在: ${slice.name}" }
            if (sha256Hex(file) != slice.sha256) {
                throw IllegalStateException("分片 ${slice.name} SHA-256 校验失败（文件已损坏或被篡改）")
            }
        }

        val staging = Files.createTempDirectory("backup-restore-")
        val renamed = mutableListOf<Pair<Path, Path>>() // bak -> 原位置（回滚用）
        try {
            manifest.slices.forEachIndexed { i, slice ->
                extractSlice(slices.getValue(slice.name), staging)
                handle?.progress(
                    "还原数据库 ${i + 1}/${manifest.slices.size}",
                    (i + 1).toLong(),
                    manifest.slices.size.toLong(),
                )
            }
            // 先回写配置（旧库仍在线），最后再替换 db 文件本身。
            restoreConfig(staging.resolve("config.json"))
            applyCoreFile(staging.resolve("security.key"), securityKeyPath(), renamed)
            applyTree(staging.resolve("downloads"), Path.of(config.dataDir, "downloads"))
            applyTree(staging.resolve("cache"), Path.of(config.dataDir, "cache"))
            applyCoreFile(staging.resolve("anotherviewer.db"), Path.of(config.dataDir, "anotherviewer.db"), renamed)
            return true
        } catch (e: Exception) {
            renamed.forEach { (bak, original) ->
                runCatching { Files.move(bak, original, StandardCopyOption.REPLACE_EXISTING) }
            }
            throw e
        } finally {
            runCatching { staging.toFile().deleteRecursively() }
        }
    }

    private fun backupsDir(): Path = Path.of(config.dataDir, "backups")

    private fun securityKeyPath(): Path =
        config.security.encryptionKeyPath.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of(config.dataDir, "security.key")

    /** 每次导出前清空旧分片（含残留 .tmp），manifest 随本次导出覆盖。 */
    private fun clearOldArtifacts(dir: Path) {
        Files.list(dir).use { children ->
            children.filter { child ->
                val name = child.fileName.toString()
                name == "manifest.json" || name.startsWith("slice-")
            }.forEach { Files.deleteIfExists(it) }
        }
    }

    private fun vacuumInto(target: Path) {
        val abs = target.toAbsolutePath().normalize().toString().replace("'", "''")
        jdbcTemplate.execute("VACUUM INTO '$abs'")
        if (!Files.isRegularFile(target)) {
            throw IOException("VACUUM INTO 未产出快照文件: $target")
        }
    }

    private fun createSlice(sliceFile: Path, entries: List<Pair<String, Path>>): BackupSlice {
        val tmp = sliceFile.resolveSibling(sliceFile.fileName.toString() + ".tmp")
        try {
            SevenZOutputFile(tmp.toFile()).use { out ->
                out.setContentCompression(SevenZMethod.LZMA2)
                entries.forEach { (name, source) -> addToArchive(out, name, source) }
            }
            if (encryptor.name() == "none") {
                Files.move(tmp, sliceFile)
            } else {
                // 预留加密接缝：对已压缩的完整分片再整体加密（先压缩后加密）。
                Files.newInputStream(tmp).use { input ->
                    Files.newOutputStream(sliceFile).use { output -> encryptor.encrypt(input, output) }
                }
                Files.deleteIfExists(tmp)
            }
            return BackupSlice(sliceFile.fileName.toString(), sha256Hex(sliceFile), Files.size(sliceFile))
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    private fun addToArchive(out: SevenZOutputFile, entryName: String, source: Path) {
        val entry = out.createArchiveEntry(source, entryName)
        out.putArchiveEntry(entry)
        if (Files.isDirectory(source)) {
            // 目录条目必须在递归写入子条目之前关闭：commons-compress 1.27 若在
            // 子条目之后才 close 目录条目，会写出损坏的 PackInfo（读取时报
            // "Badly terminated PackInfo"）。
            out.closeArchiveEntry()
            Files.list(source).use { children ->
                children.sorted().forEach { addToArchive(out, "$entryName/${it.fileName}", it) }
            }
        } else {
            Files.newInputStream(source).use { out.write(it) }
            out.closeArchiveEntry()
        }
    }

    private fun extractSlice(sliceFile: Path, targetDir: Path) {
        val fileToOpen = if (encryptor.name() == "none") {
            sliceFile
        } else {
            val dec = sliceFile.resolveSibling(sliceFile.fileName.toString() + ".dec")
            Files.newInputStream(sliceFile).use { input ->
                Files.newOutputStream(dec).use { output -> encryptor.decrypt(input, output) }
            }
            dec
        }
        try {
            SevenZFile(fileToOpen.toFile()).use { sevenZ ->
                val buffer = ByteArray(64 * 1024)
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        writeExtractedEntry(sevenZ, entry, targetDir, buffer)
                    }
                    entry = sevenZ.nextEntry
                }
            }
        } finally {
            if (fileToOpen != sliceFile) Files.deleteIfExists(fileToOpen)
        }
    }

    private fun writeExtractedEntry(sevenZ: SevenZFile, entry: SevenZArchiveEntry, targetDir: Path, buffer: ByteArray) {
        val name = entry.name
        require(isSafeArchiveEntryName(name)) { "分片含非法路径: $name" }
        val dest = targetDir.resolve(name).normalize()
        require(dest.startsWith(targetDir.normalize())) { "分片路径越界: $name" }
        Files.createDirectories(dest.parent)
        Files.newOutputStream(dest).use { out ->
            while (true) {
                val n = sevenZ.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
    }

    /** config.json 回写 server_config 表（全量覆盖）；db 快照本身已含该表，此处幂等无害。 */
    private fun restoreConfig(configJson: Path) {
        if (!Files.isRegularFile(configJson)) return
        val kvs: Map<String, String> = mapper.readValue<Map<String, String>>(configJson.toFile())
        serverConfigRepo.deleteAll()
        serverConfigRepo.saveAll(
            kvs.map { (key, value) -> ServerConfigEntity().apply { this.key = key; this.value = value } }
        )
    }

    private fun applyCoreFile(source: Path, target: Path, renamed: MutableList<Pair<Path, Path>>) {
        if (!Files.isRegularFile(source)) return
        Files.createDirectories(target.parent)
        if (Files.exists(target)) {
            var bak = target.resolveSibling("${target.fileName}.bak")
            if (Files.exists(bak)) {
                bak = target.resolveSibling("${target.fileName}.bak-${Instant.now().epochSecond}")
            }
            Files.move(target, bak)
            renamed += bak to target
        }
        // 顺序关键：旧 db 的 -wal/-shm 必须在 Files.move 之前删除。若在替换之后才删，
        // move 与删除之间的窗口里连接池可能把残留旧日志应用到新 db，或新建 -wal
        // 被随后的删除误伤；先删旧 sidecar 只影响即将被换掉的旧 db，新 db 落位时
        // 无任何 sidecar，窗口完全关闭（快照本身是 clean 的）。
        if (target.fileName.toString().endsWith(".db")) {
            Files.deleteIfExists(target.resolveSibling("${target.fileName}-wal"))
            Files.deleteIfExists(target.resolveSibling("${target.fileName}-shm"))
        }
        Files.move(source, target)
    }

    private fun applyTree(source: Path, target: Path) {
        if (!Files.isDirectory(source)) return
        Files.walk(source).use { walk ->
            walk.filter { Files.isRegularFile(it) }.forEach { file ->
                val dest = target.resolve(source.relativize(file))
                Files.createDirectories(dest.parent)
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
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

    private fun appVersion(): String =
        BackupService::class.java.`package`.implementationVersion ?: "dev"
}
