package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.DownloadInfoEntity
import com.hippo.ehviewer.web.entity.DownloadLabelEntity
import com.hippo.ehviewer.web.repository.DownloadInfoRepository
import com.hippo.ehviewer.web.repository.DownloadLabelRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class DownloadService(
    private val downloadRepository: DownloadInfoRepository,
    private val labelRepository: DownloadLabelRepository,
    private val config: EhCoreConfigProperties,
    private val eventPublisher: ApplicationEventPublisher,
    private val imageCacheService: ImageCacheService
) {
    private val logger = LoggerFactory.getLogger(DownloadService::class.java)
    private val downloadThreads = ConcurrentHashMap<Long, Thread>()
    private val workerPool: ExecutorService = Executors.newFixedThreadPool(config.download.workerCount)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.download.downloadTimeout))
        .build()

    companion object {
        private const val EHOST = "https://e-hentai.org/"
        private const val PAGE_URL_TEMPLATE = "${EHOST}s/%s/%d-%d"
        private const val IMAGE_URL_PATTERN = """<img[^>]*src="([^"]+)"[^>]*style"""
        private const val SHOW_KEY_PATTERN = """var showkey="([0-9a-z]+)";"""
        private const val API_URL = "${EHOST}api.php"
    }

    fun listDownloads(labelId: Int? = null): DownloadListResponse {
        val downloads = if (labelId != null && labelId != 0) {
            downloadRepository.findByLabel(labelId)
        } else {
            downloadRepository.findAll()
        }
        val labels = labelRepository.findAll()

        return DownloadListResponse(
            downloads = downloads.map { entity ->
                DownloadItem(
                    id = entity.id,
                    gid = entity.gid,
                    token = entity.token,
                    title = entity.title,
                    titleJpn = entity.titleJpn,
                    thumb = entity.thumb,
                    category = entity.category,
                    state = entity.state,
                    total = entity.total,
                    done = entity.done,
                    label = entity.label,
                    downloadDir = entity.downloadDir
                )
            },
            labels = labels.map { DownloadLabel(it.id, it.label, it.time) }
        )
    }

    fun getDownloadInfo(id: Long): DownloadItem? {
        val entity = downloadRepository.findById(id).orElse(null) ?: return null
        return DownloadItem(
            id = entity.id,
            gid = entity.gid,
            token = entity.token,
            title = entity.title,
            titleJpn = entity.titleJpn,
            thumb = entity.thumb,
            category = entity.category,
            state = entity.state,
            total = entity.total,
            done = entity.done,
            label = entity.label,
            downloadDir = entity.downloadDir
        )
    }

    fun addDownload(request: DownloadAddRequest): Boolean {
        val existing = downloadRepository.findByGid(request.gid)
        if (existing != null) return false

        val downloadPath = File(config.download.path, "${request.gid}")
        downloadPath.mkdirs()

        val entity = DownloadInfoEntity().apply {
            gid = request.gid
            token = request.token
            title = request.title
            titleJpn = ""
            thumb = request.thumb
            category = 0
            state = 0
            total = 0
            done = 0
            label = request.label
            downloadDir = downloadPath.absolutePath
            time = System.currentTimeMillis()
        }
        downloadRepository.save(entity)
        return true
    }

    fun startDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        if (entity.state == 1 || entity.state == 2) return false

        entity.state = 1
        downloadRepository.save(entity)

        val thread = Thread {
            executeDownload(entity)
        }
        downloadThreads[id] = thread
        thread.start()
        return true
    }

    fun pauseDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        if (entity.state == 0) return false

        entity.state = 0
        downloadRepository.save(entity)
        downloadThreads[id]?.interrupt()
        downloadThreads.remove(id)
        return true
    }

    fun cancelDownload(id: Long): Boolean {
        pauseDownload(id)
        downloadRepository.deleteById(id)
        return true
    }

    fun deleteDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        pauseDownload(id)
        entity.downloadDir?.let { dir ->
            val dirFile = File(dir)
            if (dirFile.exists()) dirFile.deleteRecursively()
        }
        downloadRepository.deleteById(id)
        return true
    }

    fun startAllDownloads() {
        val waiting = downloadRepository.findByState(0)
        waiting.forEach { startDownload(it.id) }
    }

    fun pauseAllDownloads() {
        val active = downloadRepository.findByState(1) + downloadRepository.findByState(2)
        active.forEach { pauseDownload(it.id) }
    }

    fun createLabel(label: String): Boolean {
        val existing = labelRepository.findByLabel(label)
        if (existing != null) return false

        val entity = DownloadLabelEntity().apply {
            this.label = label
            time = System.currentTimeMillis()
        }
        labelRepository.save(entity)
        return true
    }

    fun deleteLabel(id: Long): Boolean {
        if (!labelRepository.existsById(id)) return false
        labelRepository.deleteById(id)
        return true
    }

    private fun executeDownload(entity: DownloadInfoEntity) {
        try {
            val downloadDir = File(entity.downloadDir ?: return)
            downloadDir.mkdirs()

            val galleryPageUrl = String.format(PAGE_URL_TEMPLATE, entity.token, entity.gid, 1)
            val totalPages = entity.total.takeIf { it > 0 } ?: fetchPageCount(galleryPageUrl)

            entity.total = totalPages
            entity.state = 2
            downloadRepository.save(entity)
            publishProgress(entity, 2)

            for (page in 1..totalPages) {
                if (Thread.currentThread().isInterrupted) {
                    entity.state = 0
                    downloadRepository.save(entity)
                    return
                }

                val imageUrl = fetchImageUrl(entity.gid, entity.token, page) ?: continue
                val fileName = "%04d.jpg".format(page)
                val file = File(downloadDir, fileName)

                if (file.exists() && file.length() > 0) {
                    entity.done = page
                    continue
                }

                val imageData = downloadImage(imageUrl)
                if (imageData != null) {
                    file.writeBytes(imageData)
                    entity.done = page
                    imageCacheService.cacheImage(imageUrl, imageData)
                }

                publishProgress(entity, 2)

                if (config.download.downloadDelay > 0) {
                    Thread.sleep(config.download.downloadDelay.toLong())
                }
            }

            entity.state = 3
            downloadRepository.save(entity)
            publishProgress(entity, 3)
        } catch (e: InterruptedException) {
            entity.state = 0
            downloadRepository.save(entity)
        } catch (e: Exception) {
            logger.error("Download failed for gid=${entity.gid}", e)
            entity.state = 4
            downloadRepository.save(entity)
            publishProgress(entity, 4)
        } finally {
            downloadThreads.remove(entity.id)
        }
    }

    private fun publishProgress(entity: DownloadInfoEntity, state: Int) {
        eventPublisher.publishEvent(DownloadProgress(
            gid = entity.gid,
            state = state,
            downloaded = entity.done,
            total = entity.total,
            speed = 0,
            label = entity.label
        ))
    }

    private fun fetchPageCount(url: String): Int {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", EHOST)
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val body = response.body()
            val match = Regex("""Pages</td><td[^>]*>(\d+)""").find(body)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        } catch (e: Exception) {
            logger.warn("Failed to fetch page count, defaulting to 1", e)
            1
        }
    }

    private fun fetchImageUrl(gid: Long, token: String, page: Int): String? {
        return try {
            val url = String.format(PAGE_URL_TEMPLATE, token, gid, page)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", EHOST)
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val body = response.body()
            val match = Regex(IMAGE_URL_PATTERN).find(body)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.warn("Failed to fetch image URL for page $page", e)
            null
        }
    }

    private fun downloadImage(url: String): ByteArray? {
        return try {
            val cached = imageCacheService.getCachedImage(url)
            if (cached != null) return cached

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", EHOST)
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() == 200) response.body() else null
        } catch (e: Exception) {
            logger.warn("Failed to download image from $url", e)
            null
        }
    }
}
