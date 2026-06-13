package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.R
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.DownloadService
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
            
            val totalCount = allInfoList.count { it.state != DownloadInfo.STATE_FINISH }
            if (totalCount == 0) {
                updateProgress(100, context.getString(R.string.start_all_download_no_tasks))
                delay(1000)
                notifyCompleted()
                return Result.success(Unit)
            }
            updateProgress(5, context.getString(R.string.start_all_download_starting, totalCount))
            delay(500)
            
            DownloadService.startAllDownloads(context)

            // 使用 DownloadManager 的 startAllDownload 方法，并提供监听器
            var isCompleted = false
            downloadManager.startAllDownload(object : DownloadManager.StartAllDownloadListener {
                override fun onStart() {
                    // 开始时的处理
                }
                
                override fun onProgress(current: Int, total: Int, title: String) {
                    // 更新进度 - 基于所有扫描到的项目
                    updateProgress(
                        5 + (current * 90 / total),
                        context.getString(R.string.start_all_download_progress, 
                            current, total, title)
                    )
                }
                
                override fun onComplete(totalStarted: Int) {
                    updateProgress(95, context.getString(R.string.start_all_download_finalizing))
                    // startAllDownload() 方法会自动调用 ensureDownload()，所以不需要手动调用
                    updateProgress(100, context.getString(R.string.start_all_download_completed, totalStarted))
                    
                    // 使用Handler延迟执行，而不是delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        notifyCompleted()
                        isCompleted = true
                    }, 1000)
                }
                
                override fun onError(error: String) {
                    notifyError(Exception(error))
                    isCompleted = true
                }
            })
            
            // 等待下载完成
            while (!isCompleted) {
                delay(100)
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
        }
    }
}