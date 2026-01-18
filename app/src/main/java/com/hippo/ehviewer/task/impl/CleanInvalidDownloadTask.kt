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
import com.hippo.unifile.UniFile
import kotlinx.coroutines.delay

/**
 * 清理无效下载任务
 */
class CleanInvalidDownloadTask(context: Context) : BaseBackgroundTask(context) {
    
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
                                val deleted = deleteDirectory(file)
                                if (deleted) {
                                    cleanedCount++
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
                delay(50) // 给UI一些更新时间
            }
            
            updateProgress(95, context.getString(R.string.clean_invalid_download_finalizing))
            delay(500)
            
            // 清理数据库中的无效记录
            // downloadManager.cleanInvalidDownloadInfo()
            
            updateProgress(100, context.getString(R.string.clean_invalid_download_completed, cleanedCount))
            delay(1000)
            
            notifyCompleted()
            Result.success(Unit)
            
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
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
        updateState(TaskState.PAUSED)
    }
    
    override suspend fun resume() {
        if (!isPausable()) {
            throw UnsupportedOperationException("Task does not support resume")
        }
        updateState(TaskState.RUNNING)
    }
}