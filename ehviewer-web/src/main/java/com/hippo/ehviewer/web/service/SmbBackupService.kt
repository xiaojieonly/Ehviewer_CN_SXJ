package com.hippo.ehviewer.web.service

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hippo.ehviewer.web.config.EhCoreConfigProperties
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.SmbConfigEntity
import com.hippo.ehviewer.web.repository.SmbConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * SMB backup / sync service.
 *
 * - The local source is always [EhCoreConfigProperties.DownloadProperties.path]
 *   (was hardcoded to `./data/downloads`).
 * - `aggressive` means a full re-sync: every file is overwritten on the remote,
 *   while a normal sync skips remote files that already exist with the same
 *   byte size (matching the Android SmbSyncEngine length comparison).
 * - [cancelSync] only flips a cooperative flag — the sync generation counter
 *   prevents a new sync from starting while an old worker is still writing.
 * - Galleries with active downloads are skipped so a running download is never
 *   copied half-written to the remote.
 */
@Service
class SmbBackupService(
    private val smbConfigRepository: SmbConfigRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val config: EhCoreConfigProperties,
    private val downloadService: DownloadService,
    private val encryptionService: EncryptionService
) {
    private val logger = LoggerFactory.getLogger(SmbBackupService::class.java)
    private val isSyncing = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val syncGeneration = AtomicInteger(0)
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
            // Empty clears the password; already-encrypted values (e.g. from a
            // previously saved config round-tripped by the client) are kept
            // verbatim; anything else is encrypted at rest like the proxy password.
            existing.password = request.password
                .takeIf { it.isEmpty() || it.startsWith(ENC_PREFIX) }
                ?: ENC_PREFIX + encryptionService.encrypt(request.password, encryptionKey())
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

        val entity = smbConfigRepository.findByEnabled(true)
            ?: return false

        // Generation guard: cancelSync only flips the cancel flag; the flag
        // is cleared by the *worker* when it finishes. Incrementing the
        // generation here means a stale worker can never clear isSyncing for
        // a newer run, so a fresh sync can never overlap the old one.
        val generation = syncGeneration.incrementAndGet()
        cancelRequested.set(false)
        isSyncing.set(true)
        syncThread = Thread {
            try {
                executeSync(entity, aggressive, generation)
            } finally {
                if (syncGeneration.get() == generation) {
                    isSyncing.set(false)
                }
            }
        }
        syncThread?.isDaemon = true
        syncThread?.start()
        return true
    }

    fun cancelSync(): Boolean {
        if (!isSyncing.get()) return false
        cancelRequested.set(true)
        return true
    }

    fun getProgress(): SmbSyncProgress {
        return currentProgress.get()
    }

    private fun executeSync(configEntity: SmbConfigEntity, aggressive: Boolean, generation: Int) {
        try {
            val client = SMBClient()
            client.use { c ->
                val connection = c.connect(configEntity.host, configEntity.port)
                val session = if (configEntity.loginMode == "GUEST") {
                    connection.authenticate(
                        AuthenticationContext("", charArrayOf(), "")
                    )
                } else {
                    connection.authenticate(
                        AuthenticationContext(
                            configEntity.username ?: "",
                            decryptPassword(configEntity)?.toCharArray() ?: charArrayOf(),
                            ""
                        )
                    )
                }
                val share = session.connectShare(configEntity.share) as DiskShare

                val downloadDir = File(config.download.path)
                if (downloadDir.exists()) {
                    val activeGids = downloadService.getActiveDownloads()
                        .map { it.gid }
                        .toSet()
                    val files = downloadDir.listFiles() ?: emptyArray()
                    var synced = 0

                    for (file in files) {
                        if (cancelRequested.get() || syncGeneration.get() != generation) break

                        // Skip galleries that are being downloaded right now to
                        // avoid copying half-written files.
                        val gid = file.name.toLongOrNull()
                        if (file.isDirectory && gid != null && gid in activeGids) {
                            logger.debug("Skipping active download gid={} during SMB sync", gid)
                            continue
                        }

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
                            configEntity.path?.let { append(it.trimEnd('/')).append('/') }
                            append(file.name)
                        }

                        if (file.isDirectory) {
                            try {
                                share.mkdir(remotePath)
                            } catch (_: Exception) {
                            }
                            file.listFiles()?.forEach { child ->
                                if (cancelRequested.get() || syncGeneration.get() != generation) return@forEach
                                if (child.isFile) {
                                    val childPath = "$remotePath/${child.name}"
                                    // Normal sync skips files that already exist
                                    // with the same size; aggressive re-copies all.
                                    if (!aggressive && remoteFileMatches(share, childPath, child.length())) {
                                        return@forEach
                                    }
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

    /** Password for actual use (sync etc.): decrypted when stored encrypted,
     *  legacy plaintext returned verbatim, null/empty kept as-is. */
    private fun decryptPassword(entity: SmbConfigEntity): String? {
        val stored = entity.password ?: return null
        if (!stored.startsWith(ENC_PREFIX)) return stored
        return runCatching { encryptionService.decrypt(stored.removePrefix(ENC_PREFIX), encryptionKey()) }
            .getOrNull()
    }

    private fun encryptionKey(): String {
        val file = File(config.security.encryptionKeyPath)
        if (file.exists()) return file.readText().trim()
        val key = encryptionService.generateToken()
        file.parentFile?.mkdirs()
        file.writeText(key)
        return key
    }

    /** True when the remote file exists with the same byte length as [localLength]. */
    private fun remoteFileMatches(share: DiskShare, path: String, localLength: Long): Boolean {        return try {
            if (!share.fileExists(path)) {
                false
            } else {
                val info = share.getFileInformation(path)
                info.standardInformation.endOfFile == localLength
            }
        } catch (e: Exception) {
            false
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
        val configEntity = smbConfigRepository.findByEnabled(true)
        if (configEntity != null && configEntity.enabled && !isSyncing.get()) {
            startSync(false)
        }
    }

    companion object {
        private const val ENC_PREFIX = "enc:v1:"
    }
}
