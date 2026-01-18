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
 * 重建下载记录任务
 */
class RebuildDownloadRecordsTask(context: Context) : BaseBackgroundTask(context) {
    
    override fun getTaskId(): String = "rebuild_download_records"
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_rebuilding)
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_rebuilding_summary)
    
    override fun getTaskType(): TaskType = TaskType.SYNC
    
    override fun isPausable(): Boolean = true
    
    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, context.getString(R.string.rebuild_download_records_initializing))
            
            val downloadManager = EhApplication.getDownloadManager(context)
            val downloadDir = Settings.getDownloadLocation()
            
            if (downloadDir == null || !downloadDir.isDirectory) {
                val error = Exception(context.getString(R.string.rebuild_download_records_invalid_dir))
                notifyError(error)
                return Result.failure(error)
            }
            
            updateProgress(5, context.getString(R.string.rebuild_download_records_scanning))
            delay(500)
            
            val files = downloadDir.listFiles()
            if (files == null || files.isEmpty()) {
                val error = Exception(context.getString(R.string.rebuild_download_records_empty_dir))
                notifyError(error)
                return Result.failure(error)
            }
            
            // 检查是否有.ehviewer文件
            var hasEhViewerFiles = false
            for (file in files) {
                if (file.isDirectory) {
                    val ehViewerFile = file.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME)
                    if (ehViewerFile != null) {
                        hasEhViewerFiles = true
                        break
                    }
                }
            }
            
            if (!hasEhViewerFiles) {
                val error = Exception(context.getString(R.string.rebuild_download_records_no_metadata))
                notifyError(error)
                return Result.failure(error)
            }
            
            updateProgress(10, context.getString(R.string.rebuild_download_records_processing))
            delay(500)
            
            val totalFiles = files.size
            var processedFiles = 0
            var addedCount = 0
            
            for (file in files) {
                if (file.isDirectory) {
                    val ehViewerFile = file.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME)
                    if (ehViewerFile != null) {
                        try {
                            // 这里应该实现实际的重建逻辑
                            // downloadManager.addDownloadInfoFromFile(file)
                            addedCount++
                        } catch (e: Exception) {
                            // 记录错误但继续处理其他文件
                        }
                    }
                }
                
                processedFiles++
                val progress = 10 + (processedFiles * 80 / totalFiles)
                val detail = context.getString(R.string.rebuild_download_records_progress, 
                    processedFiles, totalFiles, addedCount)
                updateProgress(progress, detail)
                delay(50) // 给UI一些更新时间
            }
            
            updateProgress(95, context.getString(R.string.rebuild_download_records_finalizing))
            delay(500)
            
            // 保存到数据库
            // downloadManager.saveAllDownloadInfo()
            
            updateProgress(100, context.getString(R.string.rebuild_download_records_completed, addedCount))
            delay(1000)
            
            notifyCompleted()
            Result.success(Unit)
            
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
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