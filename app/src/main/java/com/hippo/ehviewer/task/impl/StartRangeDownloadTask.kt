package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.R
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.ehviewer.task.BackgroundTask.TaskType
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.lib.yorozuya.collect.LongList
import kotlinx.coroutines.delay

/**
 * 多选下载任务
 */
class StartRangeDownloadTask(context: Context, private val gidList: LongList) : BaseBackgroundTask(context) {
    
    override fun getTaskId(): String = "start_range_download_${gidList.hashCode()}"
    
    override fun getTaskName(): String = context.getString(R.string.download_start_range)
    
    override fun getTaskDescription(): String? = context.getString(R.string.download_start_range_summary, gidList.size())
    
    override fun getTaskType(): TaskType = TaskType.DOWNLOAD
    
    override fun isPausable(): Boolean = false
    
    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, context.getString(R.string.start_range_download_initializing))
            
            val downloadManager = EhApplication.getDownloadManager(context)
            
            if (gidList.size() == 0) {
                updateProgress(100, context.getString(R.string.start_range_download_no_tasks))
                delay(1000)
                notifyCompleted()
                return Result.success(Unit)
            }
            
            val totalCount = gidList.size()
            updateProgress(5, context.getString(R.string.start_range_download_starting, totalCount))
            delay(500)
            
            // 检查每个下载项的状态
            val validGidList = LongList()
            val allInfoList = downloadManager.allDownloadInfoList
            for (i in 0 until gidList.size()) {
                val gid = gidList.get(i)
                val info = downloadManager.getDownloadInfo(gid)
                if (info != null && (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED || info.state == DownloadInfo.STATE_FINISH || info.state == DownloadInfo.STATE_WAIT)) {
                    validGidList.add(gid)
                }
                
                // 更新进度以显示正在检查状态
                val progress = 5 + (i * 5 / gidList.size())
                updateProgress(progress, context.getString(R.string.start_range_download_progress, i + 1, gidList.size()))
            }
            
            if (validGidList.isEmpty()) {
                updateProgress(100, context.getString(R.string.start_range_download_no_valid_tasks))
                delay(1000)
                notifyCompleted()
                return Result.success(Unit)
            }
            
            val validCount = validGidList.size()
            updateProgress(10, context.getString(R.string.start_range_download_processing, validCount))
            delay(500)
            
            // 使用 DownloadManager 的 startRangeDownload 方法
            // 这个方法会处理实际的下载逻辑
            downloadManager.startRangeDownload(validGidList)
            
            // 等待一段时间让下载状态更新
            delay(1000)
            
            // 检查下载状态是否正确更新
            var startedCount = 0
            for (i in 0 until validGidList.size()) {
                val gid = validGidList.get(i)
                val info = downloadManager.getDownloadInfo(gid)
                if (info != null && (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD)) {
                    startedCount++
                }
                
                // 更新进度
                val progress = 10 + (i * 80 / validGidList.size())
                updateProgress(progress, context.getString(R.string.start_range_download_progress, 
                    i + 1, validGidList.size()))
                delay(100)
            }
            
            updateProgress(95, context.getString(R.string.start_range_download_finalizing))
            delay(500)
            
            updateProgress(100, context.getString(R.string.start_range_download_completed, startedCount))
            
            // 完成任务
            delay(1000)
            notifyCompleted()
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
        }
    }
}