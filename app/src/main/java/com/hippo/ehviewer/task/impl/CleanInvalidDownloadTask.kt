package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.R
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.DownloadedFileManager
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.ehviewer.task.BackgroundTask.TaskType
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.EhDB
import com.hippo.unifile.UniFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import android.util.Log

/**
 * 清理无效下载任务
 */
class CleanInvalidDownloadTask(context: Context) : BaseBackgroundTask(context) {
    
    private val TAG = "CleanInvalidDownload"
    
    @Volatile
    private var isPausedFlag = false
    
    override fun getTaskId(): String = "clean_invalid_download"
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_cleaning)
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_cleaning_summary)
    
    override fun getTaskType(): TaskType = TaskType.CLEANUP
    
    override fun isPausable(): Boolean = true
    
    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, context.getString(R.string.clean_invalid_download_initializing))
            
            val downloadManager = EhApplication.getDownloadManager(context)
            val downloadDir = Settings.getDownloadLocation()
            
            if (downloadDir == null || !downloadDir.isDirectory) {
                val error = Exception(context.getString(R.string.clean_invalid_download_invalid_dir))
                notifyError(error)
                return Result.failure(error)
            }
            
            updateProgress(5, context.getString(R.string.clean_invalid_download_scanning))
            delay(500)
            
            val files = downloadDir.listFiles()
            if (files == null || files.isEmpty()) {
                updateProgress(100, context.getString(R.string.clean_invalid_download_no_files))
                delay(1000)
                notifyCompleted()
                return Result.success(Unit)
            }
            
            updateProgress(10, context.getString(R.string.clean_invalid_download_processing))
            delay(500)
            
            val totalFiles = files.size
            var processedFiles = 0
            var cleanedCount = 0
            var errorCount = 0
            var skippedCount = 0 // 新增：跳过的计数
            
            for (file in files) {
                try {
                    if (file.isDirectory) {
                        val ehViewerFile = file.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME)
                        if (ehViewerFile == null) {
                            // 没有.ehviewer文件的目录，可能是无效下载
                            val deleted = deleteDirectory(file)
                            if (deleted) {
                                cleanedCount++
                            }
                        } else {
                            // 检查下载完整性
                            val isComplete = checkDownloadIntegrity(file)
                            if (!isComplete) {
                                // 在删除前检查画廊是否已被删除
                                val galleryDeleted = isGalleryDeleted(file)
                                if (galleryDeleted) {
                                    // 画廊已被删除，跳过删除此下载项
                                    skippedCount++
                                    Log.d(TAG, context.getString(R.string.clean_invalid_download_gallery_deleted) + 
                                          ": ${file.name}")
                                } else {
                                    // 画廊仍然存在，删除不完整的下载
                                    Log.d(TAG, context.getString(R.string.clean_invalid_download_gallery_still_exists) + 
                                          ": ${file.name}")
                                    val deleted = deleteDirectory(file)
                                    if (deleted) {
                                        cleanedCount++
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errorCount++
                }
                
                processedFiles++
                val progress = 10 + (processedFiles * 80 / totalFiles)
                val detail = context.getString(R.string.clean_invalid_download_progress, 
                    processedFiles, totalFiles, cleanedCount, errorCount)
                updateProgress(progress, detail)
                checkPauseState()
            }
            
            updateProgress(95, context.getString(R.string.clean_invalid_download_finalizing))
            delay(500)
            
            // 清理数据库中的无效记录
            // downloadManager.cleanInvalidDownloadInfo()
            
            val completionMessage = if (skippedCount > 0) {
                context.getString(R.string.clean_invalid_download_completed_with_skipped, 
                    cleanedCount, skippedCount)
            } else {
                context.getString(R.string.clean_invalid_download_completed, cleanedCount)
            }
            
            updateProgress(100, completionMessage)
            delay(1000)
            
            notifyCompleted()
            Result.success(Unit)
            
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
        }
    }
    
    /**
     * 同步执行清理任务并返回清理的数量
     */
    fun executeSync(): Int? {
        return try {
            runBlocking {
                // 执行清理逻辑并返回清理的数量
                val downloadManager = EhApplication.getDownloadManager(context)
                val downloadDir = Settings.getDownloadLocation()
                
                if (downloadDir == null || !downloadDir.isDirectory) {
                    return@runBlocking 0
                }
                
                val files = downloadDir.listFiles()
                if (files == null || files.isEmpty()) {
                    return@runBlocking 0
                }
                
                var cleanedCount = 0
                for (file in files) {
                    if (file.isDirectory) {
                        val ehViewerFile = file.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME)
                        if (ehViewerFile == null) {
                            val deleted = deleteDirectory(file)
                            if (deleted) {
                                cleanedCount++
                            }
                        }
                    }
                }
                
                cleanedCount
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clean invalid download failed", e)
            null
        }
    }
    
    /**
     * 检查下载完整性
     */
    private fun checkDownloadIntegrity(dir: UniFile): Boolean {
        // 这里应该实现实际的完整性检查逻辑
        // 检查图片数量是否与记录一致，文件是否完整等
        return true
    }
    
    /**
     * 检查画廊是否已被删除
     * @param dir 下载目录
     * @return true 如果画廊已被删除，false 如果画廊仍然存在
     */
    private fun isGalleryDeleted(dir: UniFile): Boolean {
        try {
            // 读取.ehviewer文件获取画廊信息
            val ehViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME)
            if (ehViewerFile == null) {
                return false
            }
            
            val content = ehViewerFile.openInputStream().use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
            
            // 解析第一行获取gid和token
            val lines = content.split("\n")
            if (lines.isEmpty()) return false
            
            val firstLine = lines[0]
            val parts = firstLine.split(",")
            if (parts.size < 2) return false
            
            val gid = parts[0].toLongOrNull() ?: return false
            val token = parts[1].trim()
            
            // 在协程作用域内检查画廊状态
            return runCatching {
                // 使用 kotlinx.coroutines.runBlocking 在非协程上下文中调用 suspend 函数
                kotlinx.coroutines.runBlocking {
                    checkGalleryStatus(gid, token)
                }
            }.getOrElse { e ->
                Log.e(TAG, "检查画廊状态时出错", e)
                false // 出错时默认不删除
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "检查画廊状态时出错", e)
            return false // 出错时默认不删除
        }
    }
    
    /**
     * 检查画廊状态
     * @param gid 画廊ID
     * @param token 画廊token
     * @return true 如果画廊已被删除，false 如果画廊仍然存在
     */
    private suspend fun checkGalleryStatus(gid: Long, token: String): Boolean {
        return try {
            Log.d(TAG, context.getString(R.string.clean_invalid_download_checking_gallery, "GID=$gid"))
            
            val url = EhUrl.getGalleryDetailUrl(gid, token)
            val okHttpClient = EhApplication.getOkHttpClient(context)
            
            // 使用EhEngine获取画廊详情
            val galleryDetail = EhEngine.getGalleryDetail(null, okHttpClient, url)
            
            if (galleryDetail == null) {
                Log.d(TAG, "无法获取画廊信息: GID=$gid")
                return true // 假设已被删除
            }
            
            // 检查画廊是否被标记为删除
            if (galleryDetail.visible == "deleted" || 
                galleryDetail.title.contains("Gallery Not Found") ||
                galleryDetail.title.contains("Removed")) {
                Log.d(TAG, context.getString(R.string.clean_invalid_download_gallery_deleted) + 
                      ": GID=$gid, Title=${galleryDetail.title}")
                return true
            }
            
            false // 画廊仍然存在
            
        } catch (e: Exception) {
            // 检查是否是画廊不存在的错误
            val errorMessage = e.message
            if (errorMessage != null && 
                (errorMessage.contains("Gallery Not Found") || 
                 errorMessage.contains("404") || 
                 errorMessage.contains("This gallery has been removed") ||
                 errorMessage.contains("Gallery unavailable"))) {
                Log.d(TAG, context.getString(R.string.clean_invalid_download_gallery_deleted) + 
                      "（网络错误）: GID=$gid, Error=$errorMessage")
                return true
            }
            
            Log.e(TAG, "检查画廊状态时出错: GID=$gid", e)
            false // 其他错误默认不删除
        }
    }
    
    /**
     * 删除目录
     */
    private fun deleteDirectory(dir: UniFile): Boolean {
        return try {
            // 这里应该实现实际的删除逻辑
            // DownloadedFileManager.deleteDirectory(dir)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun pause() {
        if (!isPausable()) {
            throw UnsupportedOperationException("Task does not support pause")
        }
        isPausedFlag = true
        updateState(TaskState.PAUSED)
        appendTaskLog("任务已暂停")
    }
    
    override suspend fun resume() {
        if (!isPausable()) {
            throw UnsupportedOperationException("Task does not support resume")
        }
        isPausedFlag = false
        updateState(TaskState.RUNNING)
        appendTaskLog("任务已恢复")
    }
    
    /**
     * 检查暂停状态，如果暂停则等待
     */
    private suspend fun checkPauseState() {
        while (isPausedFlag) {
            delay(200)
        }
    }
}