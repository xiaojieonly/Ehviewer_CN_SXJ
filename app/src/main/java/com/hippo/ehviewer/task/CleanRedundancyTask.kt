package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.lib.yorozuya.NumberUtils
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext

/**
 * 清理冗余文件任务
 * 扫描下载目录，删除不在下载记录中的文件
 */
class CleanRedundancyTask(
    private val context: Context
) : BackgroundTask {
    
    companion object {
        private const val TAG = "CleanRedundancyTask"
    }
    
    private val taskId = "clean_redundancy_${System.currentTimeMillis()}"
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    private var progressListener: BackgroundTask.ProgressListener? = null
    private var statusListener: StatusListener? = null
    private var logListener: LogListener? = null
    
    private var currentProgress = 0
    private var totalProgress = 0
    private var cleanedCount = 0

    private fun notifyStatus() {
        statusListener?.onStatus(currentProgress, totalProgress, cleanedCount)
    }

    private fun logStep(message: String) {
        logListener?.onLog(message)
    }
    
    override fun getTaskId(): String = taskId
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_clean_redundancy)
    
    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.CLEANUP
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_clean_redundancy_summary)
    
    override suspend fun execute(): Result<Unit> {
        return try {
            Log.d(TAG, "开始清理冗余文件")
            logStep("开始清理冗余文件")
            
            // 检查是否已取消
            coroutineContext[Job]?.ensureActive()
            
            val downloadManager = EhApplication.getDownloadManager(context)
            val downloadDir = Settings.getDownloadLocation()
            
            if (downloadDir == null || !downloadDir.isDirectory()) {
                Log.w(TAG, "下载目录不存在")
                logStep("下载目录不存在")
                return Result.failure(Exception("下载目录不存在"))
            }
            
            val files = downloadDir.listFiles()
            if (files == null) {
                Log.w(TAG, "无法列出下载目录文件")
                logStep("无法列出下载目录文件")
                return Result.failure(Exception("无法列出下载目录文件"))
            }
            
            totalProgress = files.size
            currentProgress = 0
            cleanedCount = 0
            notifyStatus()
            
            Log.d(TAG, "共需处理 $totalProgress 个文件")
            logStep("共需处理 $totalProgress 个文件")
            
            for (file in files) {
                // 检查取消状态
                if (isCancelled) {
                    throw CancellationException("任务已取消")
                }
                
                // 检查暂停状态
                while (isPaused && !isCancelled) {
                    Thread.sleep(100)
                }
                
                // 处理文件
                if (clearFile(file, downloadManager)) {
                    cleanedCount++
                    Log.d(TAG, "清理文件: ${file.name}")
                    logStep("清理文件: ${file.name}")
                }
                
                currentProgress++
                progressListener?.onProgressChanged(
                    if (totalProgress > 0) currentProgress * 100 / totalProgress else 0,
                    "$currentProgress/$totalProgress"
                )
                notifyStatus()
            }
            
            Log.d(TAG, "清理完成，共清理 $cleanedCount 个文件")
            logStep("清理完成，共清理 $cleanedCount 个文件")
            Result.success(Unit)
            
        } catch (e: CancellationException) {
            Log.d(TAG, "清理任务被取消")
            logStep("清理任务被取消")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "清理任务出错", e)
            logStep("清理任务出错: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 清理文件
     * @return true 表示文件被清理
     */
    private fun clearFile(file: UniFile, downloadManager: DownloadManager): Boolean {
        var name = file.name ?: return false
        
        val index = name.indexOf('-')
        if (index >= 0) {
            name = name.substring(0, index)
        }
        
        val gid = NumberUtils.parseLongSafely(name, -1)
        if (gid == -1L) {
            return false
        }
        
        // 检查是否在下载列表中
        val info = downloadManager.getDownloadInfo(gid)
        if (info == null) {
            // 不在下载列表中，删除
            return file.delete()
        }
        
        return false
    }
    
    override fun isPausable(): Boolean = true
    
    override suspend fun pause() {
        Log.d(TAG, "暂停清理任务")
        isPaused = true
    }
    
    override suspend fun resume() {
        Log.d(TAG, "恢复清理任务")
        isPaused = false
    }
    
    override suspend fun cancel() {
        Log.d(TAG, "取消清理任务")
        isCancelled = true
        isPaused = false
    }
    
    override fun getProgress(): Int {
        return if (totalProgress > 0) {
            (currentProgress * 100 / totalProgress)
        } else {
            -1
        }
    }
    
    override fun setProgressListener(listener: BackgroundTask.ProgressListener?) {
        this.progressListener = listener
    }

    override fun getProgressDetail(): String? {
        return if (totalProgress > 0) {
            "$currentProgress/$totalProgress (清理: $cleanedCount)"
        } else {
            null
        }
    }

    fun setStatusListener(listener: StatusListener?) {
        statusListener = listener
    }

    fun setLogListener(listener: LogListener?) {
        logListener = listener
    }

    interface StatusListener {
        fun onStatus(current: Int, total: Int, cleaned: Int)
    }

    interface LogListener {
        fun onLog(message: String)
    }
    
    /**
     * 同步执行任务（用于Java调用）
     */
    fun executeSync() {
        runBlocking {
            execute()
        }
    }
    
    /**
     * 获取清理的文件数量
     */
    fun getCleanedCount(): Int = cleanedCount
}
