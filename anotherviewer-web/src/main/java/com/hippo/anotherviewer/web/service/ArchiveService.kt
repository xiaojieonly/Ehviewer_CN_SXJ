package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.client.SiteEngine
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.ArchiveItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

/**
 * Archive list + download backed by anotherviewer-core's ArchiveParser and the
 * shared Gallery Site session client.
 *
 * [downloadArchive] accepts a user-supplied archiver URL; the host is
 * validated against e-hentai.org / exhentai.org (SSRF guard) before anything
 * is requested. The EH archiver flow is credit-gated (H@H form): the full
 * core flow (list → archiver form → dltype/dlcheck POST → final download URL)
 * is executed, and the final archive is streamed into the download directory.
 */
@Service
class ArchiveService(
    private val galleryLookup: GalleryLookupService,
    private val sessionManager: SiteSessionManager,
    private val config: SiteCoreConfigProperties
) {
    private val logger = LoggerFactory.getLogger(ArchiveService::class.java)
    private val client get() = sessionManager.okHttpClient

    /** MASTER-2026-08-22 P1：单飞护栏状态位。 */
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    fun listArchives(gid: Long): List<ArchiveItem> {
        val token = galleryLookup.findToken(gid) ?: return emptyList()
        return try {
            val archiverUrl = SiteUrl.getDownloadArchive(gid, token, "")
            val parsed = SiteEngine.getArchiveList(null, client, archiverUrl, gid, token)
            val or = parsed.first
            if (or.isNullOrEmpty()) {
                // The archiver form (credit-gated H@H form) was not found —
                // the account likely lacks the H@H feature or credits.
                logger.warn("Archiver form not available for gid={} (credits required?)", gid)
                return emptyList()
            }

            val archiver = try {
                SiteEngine.getArchiver(null, client, archiverUrl, gid, token)
            } catch (e: Exception) {
                logger.warn("Failed to read archiver details for gid={}", gid, e)
                null
            }

            parsed.second.mapNotNull { item ->
                val res = item.first
                val name = item.second
                val isOriginal = res == "org"
                ArchiveItem(
                    gid = gid,
                    url = SiteUrl.getDownloadArchive(gid, token, or),
                    name = name,
                    size = if (isOriginal) archiver?.originalSize.orEmpty() else archiver?.resampleSize.orEmpty(),
                    price = if (isOriginal) archiver?.originalCost.orEmpty() else archiver?.resampleCost.orEmpty(),
                    credit = archiver?.funds.orEmpty()
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to list archives for gid={}", gid, e)
            emptyList()
        }
    }

    /**
     * Start downloading a gallery archive via the EH archiver flow.
     *
     * MASTER-2026-08-22 P1：进程内单飞护栏——archiver 全流程（列表→表单→
     * dlcheck→大文件落盘）分钟级同步执行，无护栏时并发提交会叠加阻塞 HTTP
     * 线程并重复消耗上游配额。已有任务运行中抛 [ArchiveInProgressException]
     * （控制器转 409）；Job 化为后续可选扩展，不在本最小修复范围。
     *
     * @param url the archiver URL from [listArchives] (host-validated here)
     * @return true when the archive file was saved to the download directory
     */
    fun downloadArchive(gid: Long, url: String): Boolean {
        if (!running.compareAndSet(false, true)) {
            throw ArchiveInProgressException()
        }
        try {
            return downloadArchiveInner(gid, url)
        } finally {
            running.set(false)
        }
    }

    private fun downloadArchiveInner(gid: Long, url: String): Boolean {
        val parsedUrl = url.toHttpUrlOrNull() ?: return false
        if (!isAllowedArchiveHost(parsedUrl.host)) {
            logger.warn("Blocked archive download from disallowed host: {}", parsedUrl.host)
            return false
        }
        val token = galleryLookup.findToken(gid) ?: return false
        val downloadDir = File(config.download.path, "$gid")
        downloadDir.mkdirs()

        return try {
            // 1. Parse the archiver form (res options + or param).
            val parsed = SiteEngine.getArchiveList(null, client, url, gid, token)
            val or = parsed.first
            if (or.isNullOrEmpty()) {
                logger.warn("Archiver form not available for gid={} — credits required?", gid)
                return false
            }

            // 2. Read the archiver form actions (funds / dltype / dlcheck).
            val archiver = SiteEngine.getArchiver(null, client, url, gid, token)
            val formUrl = archiver.originalUrl
                ?: archiver.resampleUrl
                ?: return false
            val form = formUrl.toHttpUrlOrNull() ?: return false
            if (!isAllowedArchiveHost(form.host)) return false
            val dltype = form.queryParameter("dltype")
            val dlcheck = form.queryParameter("dlcheck")
            if (dltype.isNullOrEmpty() || dlcheck.isNullOrEmpty()) {
                logger.warn("Archiver form missing dltype/dlcheck for gid={}", gid)
                return false
            }

            // 3. POST dltype/dlcheck, follow the redirect and locate the final
            //    download URL (core downloadArchiver flow).
            val referer = SiteUrl.getGalleryDetailUrl(gid, token)
            val downloadUrl = SiteEngine.downloadArchiver(
                null, client, formUrl, referer, dltype, dlcheck
            ) ?: return false

            // 4. Stream the archive into the download directory.
            val target = File(downloadDir, "${gid}.zip")
            val request = com.hippo.anotherviewer.client.SiteRequestBuilder(downloadUrl, referer).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("Archive download HTTP {} for gid={}", response.code, gid)
                    return false
                }
                response.body?.byteStream()?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            logger.info("Archive downloaded for gid={} -> {}", gid, target.absolutePath)
            true
        } catch (e: Exception) {
            logger.warn("Archive download failed for gid={}", gid, e)
            false
        }
    }

    private fun isAllowedArchiveHost(host: String): Boolean {
        val normalized = host.lowercase().removePrefix("www.")
        return normalized == "e-hentai.org" || normalized == "exhentai.org"
    }
}

/** MASTER-2026-08-22 P1：已有归档任务运行中再次提交时抛出（控制器转 409）。 */
class ArchiveInProgressException :
    IllegalStateException("An archive download is already in progress")
