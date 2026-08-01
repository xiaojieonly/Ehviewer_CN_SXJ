package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.parser.TorrentParser
import com.hippo.ehviewer.web.dto.TorrentItem
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Torrent list + download backed by ehviewer-core's TorrentParser and the
 * shared E-Hentai session client.
 *
 * The torrent list is parsed from the gallery's `gallerytorrents.php` page.
 * Note: the core `TorrentInfo` DTO drops the parsed href, so the download
 * URLs are extracted from the same page with a dedicated regex; both lists
 * share ordering.
 */
@Service
class TorrentService(
    private val galleryLookup: GalleryLookupService,
    private val sessionManager: EhSessionManager
) {
    private val logger = LoggerFactory.getLogger(TorrentService::class.java)
    private val client get() = sessionManager.okHttpClient

    /** Matches direct `.torrent` hrefs (usually ehtracker.org) on the page. */
    private val torrentUrlPattern = Regex("href=\"(https?://[^\"]+\\.torrent[^\"]*)\"", RegexOption.IGNORE_CASE)

    fun listTorrents(gid: Long): List<TorrentItem> {
        val token = galleryLookup.findToken(gid) ?: return emptyList()
        return try {
            val url = EhUrl.getHost() + "gallerytorrents.php?gid=$gid&t=$token"
            val request = EhRequestBuilder(url, EhUrl.getGalleryDetailUrl(gid, token)).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body()?.string()
            } ?: return emptyList()

            val torrents = TorrentParser.parse(body)
            val downloadUrls = torrentUrlPattern.findAll(body).map { it.groupValues[1] }.toList()

            torrents.mapIndexed { index, info ->
                TorrentItem(
                    gid = gid,
                    token = downloadUrls.getOrNull(index) ?: "",
                    name = info.title ?: "",
                    size = "",
                    addedTime = info.posted ?: ""
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to list torrents for gid={}", gid, e)
            emptyList()
        }
    }

    /**
     * Fetch the `.torrent` file bytes for a torrent URL/token. The host is
     * restricted to known E-Hentai torrent hosts (SSRF guard).
     */
    fun fetchTorrentFile(token: String): ByteArray? {
        val url = HttpUrl.parse(token) ?: return null
        if (!isAllowedTorrentHost(url.host())) {
            logger.warn("Blocked torrent download from disallowed host: {}", url.host())
            return null
        }
        return try {
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body()?.bytes() else null
            }
        } catch (e: Exception) {
            logger.warn("Failed to download torrent file from {}", token, e)
            null
        }
    }

    /**
     * Initiates a torrent download. Returns true when the `.torrent` file was
     * fetched successfully.
     */
    fun downloadTorrent(token: String): Boolean = fetchTorrentFile(token) != null

    private fun isAllowedTorrentHost(host: String): Boolean {
        val normalized = host.lowercase().removePrefix("www.")
        return normalized == "ehtracker.org" ||
            normalized == "e-hentai.org" ||
            normalized == "exhentai.org"
    }
}
