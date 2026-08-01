package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.ArchiveItem
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

/**
 * Archive list + download backed by ehviewer-core's ArchiveParser and the
 * shared E-Hentai session client.
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
    private val sessionManager: EhSessionManager,
    private val config: EhCoreConfigProperties
) {
    private val logger = LoggerFactory.getLogger(ArchiveService::class.java)
    private val client get() = sessionManager.okHttpClient

    fun listArchives(gid: Long): List<ArchiveItem> {
        val token = galleryLookup.findToken(gid) ?: return emptyList()
        return try {
            val archiverUrl = EhUrl.getDownloadArchive(gid, token, "")
            val parsed = EhEngine.getArchiveList(null, client, archiverUrl, gid, token)
            val or = parsed.first
            if (or.isNullOrEmpty()) {
                // The archiver form (credit-gated H@H form) was not found —
                // the account likely lacks the H@H feature or credits.
                logger.warn("Archiver form not available for gid={} (credits required?)", gid)
                return emptyList()
            }

            val archiver = try {
                EhEngine.getArchiver(null, client, archiverUrl, gid, token)
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
                    url = EhUrl.getDownloadArchive(gid, token, or),
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
     * @param url the archiver URL from [listArchives] (host-validated here)
     * @return true when the archive file was saved to the download directory
     */
    fun downloadArchive(gid: Long, url: String): Boolean {
        val parsedUrl = HttpUrl.parse(url) ?: return false
        if (!isAllowedArchiveHost(parsedUrl.host())) {
            logger.warn("Blocked archive download from disallowed host: {}", parsedUrl.host())
            return false
        }
        val token = galleryLookup.findToken(gid) ?: return false
        val downloadDir = File(config.download.path, "$gid")
        downloadDir.mkdirs()

        return try {
            // 1. Parse the archiver form (res options + or param).
            val parsed = EhEngine.getArchiveList(null, client, url, gid, token)
            val or = parsed.first
            if (or.isNullOrEmpty()) {
                logger.warn("Archiver form not available for gid={} — credits required?", gid)
                return false
            }

            // 2. Read the archiver form actions (funds / dltype / dlcheck).
            val archiver = EhEngine.getArchiver(null, client, url, gid, token)
            val formUrl = archiver.originalUrl
                ?: archiver.resampleUrl
                ?: return false
            val form = HttpUrl.parse(formUrl) ?: return false
            if (!isAllowedArchiveHost(form.host())) return false
            val dltype = form.queryParameter("dltype")
            val dlcheck = form.queryParameter("dlcheck")
            if (dltype.isNullOrEmpty() || dlcheck.isNullOrEmpty()) {
                logger.warn("Archiver form missing dltype/dlcheck for gid={}", gid)
                return false
            }

            // 3. POST dltype/dlcheck, follow the redirect and locate the final
            //    download URL (core downloadArchiver flow).
            val referer = EhUrl.getGalleryDetailUrl(gid, token)
            val downloadUrl = EhEngine.downloadArchiver(
                null, client, formUrl, referer, dltype, dlcheck
            ) ?: return false

            // 4. Stream the archive into the download directory.
            val target = File(downloadDir, "${gid}.zip")
            val request = com.hippo.ehviewer.client.EhRequestBuilder(downloadUrl, referer).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("Archive download HTTP {} for gid={}", response.code(), gid)
                    return false
                }
                response.body()?.byteStream()?.use { input ->
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
