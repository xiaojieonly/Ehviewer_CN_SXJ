package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.DownloadInfoEntity
import com.hippo.ehviewer.web.entity.DownloadLabelEntity
import com.hippo.ehviewer.web.repository.DownloadInfoRepository
import com.hippo.ehviewer.web.repository.DownloadLabelRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class DownloadService(
    private val downloadRepository: DownloadInfoRepository,
    private val labelRepository: DownloadLabelRepository,
    private val config: EhCoreConfigProperties,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val downloadThreads = ConcurrentHashMap<Long, Thread>()
    private val workerPool: ExecutorService = Executors.newFixedThreadPool(config.download.workerCount)

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
            time = System.currentTimeMillis()
        }
        downloadRepository.save(entity)
        return true
    }

    fun startDownload(id: Long): Boolean {
        val entity = downloadRepository.findById(id).orElse(null) ?: return false
        if (entity.state == 2) return false

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
            entity.state = 2
            downloadRepository.save(entity)

            eventPublisher.publishEvent(DownloadProgress(
                gid = entity.gid,
                state = 2,
                downloaded = entity.done,
                total = entity.total,
                speed = 0,
                label = entity.label
            ))

            entity.state = 3
            downloadRepository.save(entity)

            eventPublisher.publishEvent(DownloadProgress(
                gid = entity.gid,
                state = 3,
                downloaded = entity.done,
                total = entity.total,
                speed = 0,
                label = entity.label
            ))
        } catch (e: Exception) {
            entity.state = 4
            downloadRepository.save(entity)

            eventPublisher.publishEvent(DownloadProgress(
                gid = entity.gid,
                state = 4,
                downloaded = entity.done,
                total = entity.total,
                speed = 0,
                label = entity.label
            ))
        } finally {
            downloadThreads.remove(entity.id)
        }
    }
}
