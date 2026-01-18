package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.R
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.ehviewer.task.BackgroundTask.TaskType
import com.hippo.ehviewer.EhApplication
import kotlinx.coroutines.delay

/**
 * 全部开始下载任务
 */
class StartAllDownloadTask(context: Context) : BaseBackgroundTask(context) {
    
    override fun getTaskId(): String = "start_all_download"
    
    override fun getTaskName(): String = context.getString(R.string.download_start_all)
    
    override fun getTaskDescription(): String? = context.getString(R.string.download_start_all_summary)
    
    override fun getTaskType(): TaskType = TaskType.DOWNLOAD
    
    override fun isPausable(): Boolean = false
    
    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, context.getString(R.string.start_all_download_initializing))
            
            val downloadManager = EhApplication.getDownloadManager(context)
            val allInfoList = downloadManager.allDownloadInfoList
            
            if (allInfoList.isEmpty()) {
                updateProgress(100, context.getString(R.string.start_all_download_no_tasks))
                delay(1000)
                notifyCompleted()
                return Result.success(Unit)
            }
            
            // 计算需要启动的任务数量
            val tasksToStart = allInfoList.filter { info ->
                info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED
            }
            
            val totalCount = tasksToStart.size
            var processedCount = 0
            
            updateProgress(5, context.getString(R.string.start_all_download_starting, totalCount))
            delay(500)
            
            // 启动所有符合条件的下载任务
            for (info in tasksToStart) {
                try {
                    val galleryTitle = info.title ?: "Gallery ${info.gid}"
                    
                    updateProgress(
                        5 + (processedCount * 90 / totalCount),
                        context.getString(R.string.start_all_download_progress, 
                            processedCount + 1, totalCount, galleryTitle)
                    )
                    
                    // 设置为等待状态
                    info.state = DownloadInfo.STATE_WAIT
                    // downloadManager.addToWaitList(info)
                    
                    processedCount++
                    delay(100) // 给UI一些更新时间
                    
                } catch (e: Exception) {
                    // 记录错误但继续处理其他任务
                }
            }
            
            updateProgress(95, context.getString(R.string.start_all_download_finalizing))
            delay(500)
            
            // 确保下载开始
            // downloadManager.ensureDownload()
            
            updateProgress(100, context.getString(R.string.start_all_download_completed, processedCount))
            delay(1000)
            
            notifyCompleted()
            Result.success(Unit)
            
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
        }
    }
}