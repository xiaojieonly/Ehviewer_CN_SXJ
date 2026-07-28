package com.hippo.ehviewer.web.service

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.SmbConfigEntity
import com.hippo.ehviewer.web.repository.SmbConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
class SmbBackupService(
    private val smbConfigRepository: SmbConfigRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SmbBackupService::class.java)
    private val isSyncing = AtomicBoolean(false)
    private var syncThread: Thread? = null
    private val currentProgress = AtomicReference(SmbSyncProgress("idle", 0, 0, "", 0))

    fun getConfig(): SmbConfigResponse? {
        val entity = smbConfigRepository.findByEnabled(true)
            ?: smbConfigRepository.findAll().firstOrNull()
            ?: return null

        return entityToResponse(entity)
    }

    fun updateConfig(request: SmbConfigUpdateRequest): Boolean {
        val existing = smbConfigRepository.findByEnabled(true)
            ?: SmbConfigEntity()

        existing.host = request.host
        existing.port = request.port
        existing.share = request.share
        existing.path = request.path
        existing.loginMode = request.loginMode
        existing.username = request.username
        if (request.password != null) {
            existing.password = request.password
        }
        existing.enabled = request.enabled

        smbConfigRepository.save(existing)
        return true
    }

    fun testConnection(request: SmbTestConnectionRequest): SmbTestConnectionResponse {
        return try {
            val client = SMBClient()
            client.use { c ->
                val connection = c.connect(request.host, request.port)
                val session = if (request.loginMode == "GUEST") {
                    connection.authenticate(
                        AuthenticationContext("", charArrayOf(), "")
                    )
                } else {
                    connection.authenticate(
                        AuthenticationContext(
                            request.username ?: "",
                            request.password?.toCharArray() ?: charArrayOf(),
                            ""
                        )
                    )
                }
                session.connectShare(request.share).close()
                session.close()
                connection.close()
            }
            SmbTestConnectionResponse(true, "连接成功")
        } catch (e: Exception) {
            logger.error("SMB test connection failed", e)
            SmbTestConnectionResponse(false, "连接失败: ${e.message}")
        }
    }

    fun startSync(aggressive: Boolean): Boolean {
        if (isSyncing.get()) return false

        val config = smbConfigRepository.findByEnabled(true)
            ?: return false

        isSyncing.set(true)
        syncThread = Thread {
            try {
                executeSync(config, aggressive)
            } finally {
                isSyncing.set(false)
            }
        }
        syncThread?.isDaemon = true
        syncThread?.start()
        return true
    }

    fun cancelSync(): Boolean {
        if (!isSyncing.get()) return false
        syncThread?.interrupt()
        isSyncing.set(false)
        return true
    }

    fun getProgress(): SmbSyncProgress {
        return currentProgress.get()
    }

    private fun executeSync(config: SmbConfigEntity, aggressive: Boolean) {
        try {
            val client = SMBClient()
            client.use { c ->
                val connection = c.connect(config.host, config.port)
                val session = if (config.loginMode == "GUEST") {
                    connection.authenticate(
                        AuthenticationContext("", charArrayOf(), "")
                    )
                } else {
                    connection.authenticate(
                        AuthenticationContext(
                            config.username ?: "",
                            config.password?.toCharArray() ?: charArrayOf(),
                            ""
                        )
                    )
                }
                val share = session.connectShare(config.share) as DiskShare

                val downloadDir = File("./data/downloads")
                if (downloadDir.exists()) {
                    val files = downloadDir.listFiles() ?: emptyArray()
                    var synced = 0

                    for (file in files) {
                        if (Thread.currentThread().isInterrupted) break

                        val progress = SmbSyncProgress(
                            state = "syncing",
                            totalFiles = files.size,
                            syncedFiles = synced,
                            currentFile = file.name,
                            speed = 0
                        )
                        currentProgress.set(progress)
                        eventPublisher.publishEvent(progress)

                        val remotePath = buildString {
                            config.path?.let { append(it.trimEnd('/')).append('/') }
                            append(file.name)
                        }

                        if (file.isDirectory) {
                            try {
                                share.mkdir(remotePath)
                            } catch (_: Exception) {
                            }
                            file.listFiles()?.forEach { child ->
                                if (child.isFile) {
                                    val childPath = "$remotePath/${child.name}"
                                    val remoteFile = share.openFile(
                                        childPath,
                                        setOf(AccessMask.GENERIC_WRITE),
                                        setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                                        SMB2ShareAccess.ALL,
                                        SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                        setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                                    )
                                    remoteFile.outputStream.use { fOut ->
                                        child.inputStream().use { fIn ->
                                            fIn.copyTo(fOut)
                                        }
                                    }
                                    remoteFile.close()
                                }
                            }
                        }

                        synced++
                    }
                }

                share.close()
                session.close()
                connection.close()
            }

            val completed = SmbSyncProgress(
                state = "completed",
                totalFiles = 0,
                syncedFiles = 0,
                currentFile = "",
                speed = 0
            )
            currentProgress.set(completed)
            eventPublisher.publishEvent(completed)
        } catch (e: Exception) {
            logger.error("SMB sync failed", e)
            val failed = SmbSyncProgress(
                state = "failed",
                totalFiles = 0,
                syncedFiles = 0,
                currentFile = e.message ?: "未知错误",
                speed = 0
            )
            currentProgress.set(failed)
            eventPublisher.publishEvent(failed)
        }
    }

    private fun entityToResponse(entity: SmbConfigEntity): SmbConfigResponse {
        return SmbConfigResponse(
            id = entity.id,
            host = entity.host,
            port = entity.port,
            share = entity.share,
            path = entity.path,
            loginMode = entity.loginMode,
            username = entity.username,
            enabled = entity.enabled
        )
    }

    @Scheduled(fixedRate = 3600000)
    fun scheduledSync() {
        val config = smbConfigRepository.findByEnabled(true)
        if (config != null && config.enabled && !isSyncing.get()) {
            startSync(false)
        }
    }
}
