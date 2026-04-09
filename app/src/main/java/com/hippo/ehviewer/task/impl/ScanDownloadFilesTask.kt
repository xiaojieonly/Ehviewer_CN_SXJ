package com.hippo.ehviewer.task.impl

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hippo.ehviewer.R
import com.hippo.ehviewer.DownloadedFileManager
import com.hippo.ehviewer.DownloadedFileManagerScanListener
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.ehviewer.task.BackgroundTask.TaskType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * 扫描下载文件任务
 */
class ScanDownloadFilesTask(context: Context) : BaseBackgroundTask(context) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    @Volatile
    private var isPausedFlag = false
    
    override fun getTaskId(): String = "scan_download_files"
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_scan_download_files)
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_scan_download_files_summary)
    
    override fun getTaskType(): TaskType = TaskType.SCAN
    
    override fun isPausable(): Boolean = true
    
    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, context.getString(R.string.scan_download_files_initializing))
            
            val manager = DownloadedFileManager.getInstance()
            var totalFiles = 0
            var processedFiles = 0
            
            // 首先获取总文件数
            updateProgress(0, context.getString(R.string.scan_download_files_counting))
            delay(500) // 给用户一些反馈时间
            
            // 创建进度监听器
            val scanListener = object : DownloadedFileManagerScanListener {
                override fun onProgress(current: Int, total: Int) {
                    processedFiles = current
                    totalFiles = total
                    val progress = if (total > 0) (current * 100 / total) else -1
                    val detail = context.getString(R.string.scan_download_files_progress, current, total)
                    updateProgress(progress, detail)
                    // 检查暂停状态
                    runBlocking {
                        checkPauseState()
                    }
                }
                
                override fun onCompleted() {
                    mainHandler.post {
                        updateProgress(100, context.getString(R.string.scan_download_files_completed))
                        notifyCompleted()
                    }
                }
                
                override fun onError(e: Exception) {
                    notifyError(e)
                }
            }
            
            // 执行扫描
            manager.scanDownloadDirectories(scanListener)
            
            // 扫描是异步的，结果会通过监听器回调
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
     * 注意：由于扫描是异步的，暂停效果会在下次进度回调时生效
     */
    private suspend fun checkPauseState() {
        while (isPausedFlag) {
            delay(200)
        }
    }
}