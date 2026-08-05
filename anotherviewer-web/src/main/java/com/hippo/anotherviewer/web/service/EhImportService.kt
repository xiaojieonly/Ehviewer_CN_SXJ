package com.hippo.anotherviewer.web.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hippo.anotherviewer.web.dto.EhCookieImportResult
import com.hippo.anotherviewer.web.dto.EhImportCookieDto
import com.hippo.anotherviewer.web.dto.EhImportedCounts
import com.hippo.anotherviewer.web.dto.EhImportResponse
import com.hippo.anotherviewer.web.entity.BlackListEntity
import com.hippo.anotherviewer.web.entity.BookmarkInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadDirnameEntity
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.entity.FilterEntity
import com.hippo.anotherviewer.web.entity.GalleryInfoBase
import com.hippo.anotherviewer.web.entity.GalleryTagsEntity
import com.hippo.anotherviewer.web.entity.HistoryInfoEntity
import com.hippo.anotherviewer.web.entity.LocalFavoriteInfoEntity
import com.hippo.anotherviewer.web.entity.QuickSearchEntity
import com.hippo.anotherviewer.web.repository.BlackListRepository
import com.hippo.anotherviewer.web.repository.BookmarkInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadDirnameRepository
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.repository.FilterRepository
import com.hippo.anotherviewer.web.repository.GalleryTagsRepository
import com.hippo.anotherviewer.web.repository.HistoryInfoRepository
import com.hippo.anotherviewer.web.repository.LocalFavoriteInfoRepository
import com.hippo.anotherviewer.web.repository.QuickSearchRepository
import okhttp3.Cookie
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * 原版 EhViewer 备份导入（线 B，B3）。表驱动扫描：以 `PRAGMA table_info` 感知上传
 * sqlite db 实际存在的表与列集，对已知源表逐表映射，缺列落默认值。gid 冲突默认跳过
 * （计 skipped），`force=true` 时 upsert。可选 cookies 段仅收容站点域 cookie 写入
 * [SiteSessionManager.cookieStore]（进程级会话，重启失效）。
 */
@Service
class EhImportService(
    private val favoriteRepository: LocalFavoriteInfoRepository,
    private val historyRepository: HistoryInfoRepository,
    private val downloadRepository: DownloadInfoRepository,
    private val bookmarkRepository: BookmarkInfoRepository,
    private val filterRepository: FilterRepository,
    private val quickSearchRepository: QuickSearchRepository,
    private val downloadLabelRepository: DownloadLabelRepository,
    private val downloadDirnameRepository: DownloadDirnameRepository,
    private val blackListRepository: BlackListRepository,
    private val galleryTagsRepository: GalleryTagsRepository,
    private val sessionManager: SiteSessionManager,
) {
    private val logger = LoggerFactory.getLogger(EhImportService::class.java)
    private val mapper = jacksonObjectMapper()

    @Transactional
    fun importEhViewer(file: MultipartFile, cookies: MultipartFile?, force: Boolean, username: String): EhImportResponse {
        val dbPath = Files.createTempFile("ehviewer-import-", ".db")
        try {
            file.inputStream.use { input ->
                Files.newOutputStream(dbPath).use { output -> input.transferTo(output) }
            }
            val outcome = importDatabase(dbPath, username, force)
            val cookieResult = cookies?.let { importCookies(it) } ?: EhCookieImportResult()
            return EhImportResponse(
                success = true,
                imported = outcome.imported,
                cookies = cookieResult,
                skipped = outcome.skipped,
            )
        } finally {
            runCatching { Files.deleteIfExists(dbPath) }
        }
    }

    private fun importDatabase(dbPath: Path, username: String, force: Boolean): ImportOutcome {
        val outcome = ImportOutcome()
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { conn ->
            val tables = existingTables(conn)
            tables["DOWNLOAD_LABELS"]?.let { importDownloadLabels(conn, it, username, force, outcome) }
            tables["DOWNLOADS"]?.let { importDownloads(conn, it, username, force, outcome) }
            tables["DOWNLOAD_DIRNAME"]?.let { importDownloadDirnames(conn, it, force, outcome) }
            tables["HISTORY"]?.let { importHistory(conn, it, username, force, outcome) }
            tables["BOOKMARKS"]?.let { importBookmarks(conn, it, username, force, outcome) }
            tables["LOCAL_FAVORITES"]?.let { importLocalFavorites(conn, it, username, force, outcome) }
            tables["FILTER"]?.let { importFilters(conn, it, username, force, outcome) }
            tables["QUICK_SEARCH"]?.let { importQuickSearches(conn, it, username, force, outcome) }
            tables["BLACK_LIST"]?.let { importBlackList(conn, it, username, force, outcome) }
            tables["GALLERY_TAGS"]?.let { importGalleryTags(conn, it, force, outcome) }
        }
        return outcome
    }

    private fun existingTables(conn: Connection): Map<String, String> {
        val tables = mutableMapOf<String, String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                while (rs.next()) {
                    val name = rs.getString(1)
                    tables[name.uppercase()] = name
                }
            }
        }
        return tables
    }

    private fun tableColumns(conn: Connection, table: String): Set<String> {
        // 上传库的 sqlite_master 不可信：仅当表名命中白名单才允许拼进 SQL，
        // 其余一律跳过，绝不把任意表名作为标识符插值。
        if (!isImportableTable(table)) return emptySet()
        val cols = mutableSetOf<String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info(\"$table\")").use { rs ->
                while (rs.next()) cols += rs.getString(2).lowercase()
            }
        }
        return cols
    }

    private fun loadRows(conn: Connection, table: String): List<SourceRow> {
        if (!isImportableTable(table)) return emptyList()
        val columns = tableColumns(conn, table)
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM \"$table\"").use { rs ->
                val rows = mutableListOf<SourceRow>()
                while (rs.next()) {
                    val values = columns.associateWith { rs.getObject(it) }
                    rows += SourceRow(columns, values)
                }
                return rows
            }
        }
    }

    private fun importDownloadLabels(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val label = row.str("label")?.takeIf { it.isNotBlank() } ?: return@forEach
            val existing = downloadLabelRepository.findByLabel(label)
            if (existing != null && !force) return@forEach
            val entity = existing ?: DownloadLabelEntity()
            entity.apply {
                this.label = label
                this.time = row.long("time")
                this.lastModified = row.long("time")
                this.username = username
            }
            downloadLabelRepository.save(entity)
            outcome.imported = outcome.imported.copy(labels = outcome.imported.labels + 1)
        }
    }

    private fun importDownloads(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val gid = row.long("gid")
            val existing = downloadRepository.findByGid(gid)
            if (existing != null) {
                if (existing.username != null && existing.username != username) {
                    outcome.skipped++
                    return@forEach
                }
                if (!force) {
                    outcome.skipped++
                    return@forEach
                }
            }
            val entity = existing ?: DownloadInfoEntity()
            entity.apply {
                row.applyGalleryInfo(this)
                this.state = row.int("state")
                this.legacy = row.int("legacy")
                this.time = row.long("time")
                this.lastModified = row.long("time")
                this.total = 0
                this.done = 0
                this.downloadDir = row.str("archive_uri")
                this.label = resolveLabelId(row.str("label"), row.long("time"), username)
                this.username = username
            }
            downloadRepository.save(entity)
            outcome.imported = outcome.imported.copy(downloads = outcome.imported.downloads + 1)
        }
    }

    private fun importDownloadDirnames(
        conn: Connection,
        table: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val gid = row.long("gid")
            val dirname = row.str("dirname") ?: return@forEach
            val existing = downloadDirnameRepository.findByGid(gid)
            if (existing != null && !force) return@forEach
            val entity = existing ?: DownloadDirnameEntity()
            entity.gid = gid
            entity.dirname = dirname
            downloadDirnameRepository.save(entity)
            outcome.imported = outcome.imported.copy(dirnames = outcome.imported.dirnames + 1)
        }
    }

    private fun importHistory(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val gid = row.long("gid")
            val existing = historyRepository.findByGid(gid)
            if (existing != null) {
                if (existing.username != null && existing.username != username) {
                    outcome.skipped++
                    return@forEach
                }
                if (!force) {
                    outcome.skipped++
                    return@forEach
                }
            }
            val entity = existing ?: HistoryInfoEntity()
            entity.apply {
                row.applyGalleryInfo(this)
                this.mode = row.int("mode")
                this.time = row.long("time")
                this.lastModified = row.long("time")
                this.username = username
            }
            historyRepository.save(entity)
            outcome.imported = outcome.imported.copy(history = outcome.imported.history + 1)
        }
    }

    private fun importBookmarks(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val gid = row.long("gid")
            val existing = bookmarkRepository.findByGid(gid)
            if (existing != null) {
                if (existing.username != null && existing.username != username) {
                    return@forEach
                }
                if (!force) return@forEach
            }
            val entity = existing ?: BookmarkInfoEntity()
            entity.apply {
                row.applyGalleryInfo(this)
                this.time = row.long("time")
                this.lastModified = row.long("time")
                this.note = row.int("page").toString()
                this.username = username
            }
            bookmarkRepository.save(entity)
            outcome.imported = outcome.imported.copy(bookmarks = outcome.imported.bookmarks + 1)
        }
    }

    private fun importLocalFavorites(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val gid = row.long("gid")
            val existing = favoriteRepository.findByGid(gid)
            if (existing != null) {
                if (existing.username != null && existing.username != username) {
                    return@forEach
                }
                if (!force) return@forEach
            }
            val entity = existing ?: LocalFavoriteInfoEntity()
            entity.apply {
                row.applyGalleryInfo(this)
                this.time = row.long("time")
                this.lastModified = row.long("time")
                this.username = username
            }
            favoriteRepository.save(entity)
            outcome.imported = outcome.imported.copy(favorites = outcome.imported.favorites + 1)
        }
    }

    private fun importFilters(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val type = row.int("mode")
            val text = row.str("text")?.takeIf { it.isNotBlank() } ?: return@forEach
            val existing = filterRepository.findAll().firstOrNull { it.type == type && it.text == text }
            if (existing != null && !force) return@forEach
            val entity = existing ?: FilterEntity()
            entity.apply {
                this.type = type
                this.text = text
                this.enabled = if (row.has("enable")) row.boolean("enable") else true
                this.lastModified = 0
                this.username = username
            }
            filterRepository.save(entity)
            outcome.imported = outcome.imported.copy(filters = outcome.imported.filters + 1)
        }
    }

    private fun importQuickSearches(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val name = row.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            val existing = quickSearchRepository.findByName(name)
            if (existing != null && !force) return@forEach
            val entity = existing ?: QuickSearchEntity()
            entity.apply {
                this.name = name
                this.mode = row.int("mode")
                this.category = row.int("category")
                this.keyword = row.str("keyword")
                this.advanceSearch = row.int("advance_search")
                this.minRating = row.int("min_rating")
                this.pageFrom = row.int("page_from")
                this.pageTo = row.int("page_to")
                this.time = row.long("time")
                this.lastModified = row.long("time")
                this.username = username
            }
            quickSearchRepository.save(entity)
            outcome.imported = outcome.imported.copy(quickSearches = outcome.imported.quickSearches + 1)
        }
    }

    private fun importBlackList(
        conn: Connection,
        table: String,
        username: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        if (loadRows(conn, table).isEmpty()) return
        val existing = blackListRepository.findByUser(username)
        if (existing != null && !force) return
        if (existing == null) {
            blackListRepository.save(BlackListEntity().apply { this.user = username })
        }
        outcome.imported = outcome.imported.copy(blackList = outcome.imported.blackList + 1)
    }

    private fun importGalleryTags(
        conn: Connection,
        table: String,
        force: Boolean,
        outcome: ImportOutcome,
    ) {
        loadRows(conn, table).forEach { row ->
            val gid = row.long("gid")
            val existingTags = galleryTagsRepository.findByGid(gid)
            if (existingTags.isNotEmpty() && !force) return@forEach
            if (existingTags.isNotEmpty()) {
                galleryTagsRepository.deleteByGid(gid)
            }
            var written = 0
            GALLERY_TAG_NAMESPACES.forEach { ns ->
                row.str(ns)?.split(",")?.forEach { rawTag ->
                    val tag = rawTag.trim()
                    if (tag.isNotEmpty()) {
                        galleryTagsRepository.save(GalleryTagsEntity().apply {
                            this.gid = gid
                            this.tag = tag
                            this.tagNamespace = ns
                        })
                        written++
                    }
                }
            }
            if (written > 0) {
                outcome.imported = outcome.imported.copy(galleryTags = outcome.imported.galleryTags + written)
            }
        }
    }

    private fun resolveLabelId(labelName: String?, time: Long, username: String): Int {
        if (labelName.isNullOrEmpty()) return 0
        downloadLabelRepository.findByLabel(labelName)?.let { return it.id.toInt() }
        val created = downloadLabelRepository.save(DownloadLabelEntity().apply {
            label = labelName
            this.time = time
            lastModified = time
            this.username = username
        })
        return created.id.toInt()
    }

    // ---- cookies 段 ----

    private fun importCookies(file: MultipartFile): EhCookieImportResult {
        val bytes = file.bytes
        val cookies = if (isSqlite(bytes)) readCookieDb(bytes) else parseCookieJson(bytes)
        var siteDomain = 0
        var imported = 0
        cookies.forEach { c ->
            if (!isSiteDomain(c.domain)) return@forEach
            siteDomain++
            buildOkHttpCookie(c)?.let {
                sessionManager.cookieStore.addCookie(it)
                imported++
            }
        }
        // ADR-0004：导入即持久化，重启后经 ehSession 恢复（不再会话级失效）。
        if (imported > 0) sessionManager.saveSessionSnapshot()
        return EhCookieImportResult(imported = imported, siteDomain = siteDomain)
    }

    private fun isSqlite(bytes: ByteArray): Boolean =
        bytes.size >= 16 && bytes.copyOfRange(0, 16).contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))

    private fun readCookieDb(bytes: ByteArray): List<EhImportCookieDto> {
        val tmp = Files.createTempFile("ehviewer-cookies-", ".db")
        try {
            Files.write(tmp, bytes)
            DriverManager.getConnection("jdbc:sqlite:${tmp.toAbsolutePath()}").use { conn ->
                val rows = mutableListOf<EhImportCookieDto>()
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(
                        "SELECT NAME, VALUE, EXPIRES_AT, DOMAIN, PATH, SECURE, HTTP_ONLY, PERSISTENT, HOST_ONLY FROM OK_HTTP_3_COOKIE"
                    ).use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("NAME") ?: continue
                            val domain = rs.getString("DOMAIN") ?: continue
                            rows += EhImportCookieDto(
                                name = name,
                                value = rs.getString("VALUE") ?: "",
                                expiresAt = rs.getLong("EXPIRES_AT"),
                                domain = domain,
                                path = rs.getString("PATH") ?: "/",
                                secure = rs.getInt("SECURE") == 1,
                                httpOnly = rs.getInt("HTTP_ONLY") == 1,
                                persistent = rs.getInt("PERSISTENT") == 1,
                                hostOnly = rs.getInt("HOST_ONLY") == 1,
                            )
                        }
                    }
                }
                return rows
            }
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }

    private fun parseCookieJson(bytes: ByteArray): List<EhImportCookieDto> = mapper.readValue(bytes)

    private fun buildOkHttpCookie(c: EhImportCookieDto): Cookie? = try {
        val expiresAt = if (c.expiresAt > 0) c.expiresAt else Long.MAX_VALUE
        val builder = Cookie.Builder()
            .name(c.name)
            .value(c.value)
            .expiresAt(expiresAt)
            .path(c.path.ifBlank { "/" }.let { if (it.startsWith("/")) it else "/$it" })
        val domain = normalizeDomain(c.domain)
        if (c.hostOnly) {
            builder.hostOnlyDomain(domain)
        } else {
            builder.domain(domain)
        }
        if (c.secure) builder.secure()
        if (c.httpOnly) builder.httpOnly()
        builder.build()
    } catch (e: Exception) {
        logger.warn("忽略无法导入的 cookie {}@{}: {}", c.name, c.domain, e.message)
        null
    }

    private fun normalizeDomain(domain: String): String =
        domain.trim().lowercase().removePrefix(".").trimEnd('.')

    private fun isSiteDomain(domain: String): Boolean {
        val d = normalizeDomain(domain)
        if (d.isEmpty()) return false
        return SITE_DOMAINS.any { it == d || d.endsWith(".$it") }
    }

    private data class ImportOutcome(
        var imported: EhImportedCounts = EhImportedCounts(),
        var skipped: Int = 0,
    )

    private class SourceRow(private val columns: Set<String>, private val values: Map<String, Any?>) {
        fun has(col: String): Boolean = col in columns
        fun str(col: String): String? = values[col]?.toString()
        fun long(col: String): Long = (values[col] as? Number)?.toLong() ?: 0L
        fun int(col: String): Int = (values[col] as? Number)?.toInt() ?: 0
        fun float(col: String): Float = (values[col] as? Number)?.toFloat() ?: 0f
        fun boolean(col: String): Boolean = (values[col] as? Number)?.toInt() == 1
    }

    private fun SourceRow.applyGalleryInfo(entity: GalleryInfoBase) {
        entity.gid = long("gid")
        entity.token = str("token") ?: ""
        entity.title = str("title")
        entity.titleJpn = str("title_jpn")
        entity.thumb = str("thumb")
        entity.category = int("category")
        entity.posted = str("posted")
        entity.uploader = str("uploader")
        entity.rating = float("rating")
        entity.rated = boolean("rated")
        entity.simpleLanguage = str("simple_language")
        entity.simpleTags = str("simple_tags")
        entity.thumbWidth = int("thumb_width")
        entity.thumbHeight = int("thumb_height")
        entity.spanSize = int("span_size")
        entity.spanIndex = int("span_index")
        entity.spanGroupIndex = int("span_group_index")
        entity.favoriteSlot = if (has("favorite_slot")) int("favorite_slot") else -2
        entity.favoriteName = str("favorite_name")
        entity.pages = int("pages")
    }

    private companion object {
        val SITE_DOMAINS = setOf("e-hentai.org", "exhentai.org", "ehgt.org", "forums.e-hentai.org")
        val GALLERY_TAG_NAMESPACES = listOf(
            "rows", "artist", "cosplayer", "character", "female", "group",
            "language", "male", "misc", "mixed", "other", "parody", "reclass",
        )
        // 可导入的固定源表集合（对应 existingTables 的派发键）。仅这些表名允许
        // 作为 SQL 标识符插值；上传库中的其余表（含伪造表名）一律跳过。
        val IMPORTABLE_TABLE_KEYS = setOf(
            "DOWNLOAD_LABELS", "DOWNLOADS", "DOWNLOAD_DIRNAME", "HISTORY",
            "BOOKMARKS", "LOCAL_FAVORITES", "FILTER", "QUICK_SEARCH",
            "BLACK_LIST", "GALLERY_TAGS",
        )

        fun isImportableTable(table: String): Boolean = table.uppercase() in IMPORTABLE_TABLE_KEYS
    }
}
