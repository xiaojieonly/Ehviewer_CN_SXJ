package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * 修复所有下载画廊信息任务
 * 批量修复所有已下载画廊的元数据信息
 */
class RepairAllDownloadedGalleryTask(
    private val context: Context
) : BackgroundTask {
    
    companion object {
        private const val TAG = "RepairAllGalleryTask"
        private const val CONCURRENT_REQUESTS = 20 // 并发请求数量
    }
    
    private val taskId = "repair_all_gallery_${System.currentTimeMillis()}"
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    private var progressListener: BackgroundTask.ProgressListener? = null
    private var statusListener: StatusListener? = null
    private var logListener: LogListener? = null
    
    private var currentProgress = 0
    private var totalProgress = 0
    private var successCount = 0
    private var failedCount = 0

    private fun notifyStatus() {
        statusListener?.onStatus(currentProgress, totalProgress, successCount, failedCount)
    }

    private fun logStep(message: String) {
        logListener?.onLog(message)
    }
    
    override fun getTaskId(): String = taskId
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_repair_all_downloaded_gallery)
    
    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.UPDATE
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_repair_all_downloaded_gallery_summary)
    
    override suspend fun execute(): Result<Unit> {
        return try {
            Log.d(TAG, "开始修复所有下载画廊信息")
            logStep("开始修复所有下载画廊信息")
            
            // 检查是否已取消
            coroutineContext[Job]?.ensureActive()
            
            val downloadManager = EhApplication.getDownloadManager(context)
            val downloadInfoList = downloadManager.allDownloadInfoList
            
            if (downloadInfoList.isNullOrEmpty()) {
                Log.d(TAG, "没有需要修复的下载画廊")
                logStep("没有需要修复的下载画廊")
                progressListener?.onCompleted()
                notifyStatus()
                return Result.success(Unit)
            }
            
            totalProgress = downloadInfoList.size
            currentProgress = 0
            successCount = 0
            failedCount = 0
            notifyStatus()
            
            Log.d(TAG, "总共需要修复 $totalProgress 个画廊")
            
            // 分批处理，每批CONCURRENT_REQUESTS个
            val batches = downloadInfoList.chunked(CONCURRENT_REQUESTS)
            
            for ((batchIndex, batch) in batches.withIndex()) {
                // 检查取消状态
                if (isCancelled) {
                    throw CancellationException("任务已取消")
                }
                
                // 检查暂停状态
                while (isPaused && !isCancelled) {
                    Thread.sleep(100)
                }
                
                logStep("开始处理第 ${batchIndex + 1}/${batches.size} 批 (${batch.size} 个画廊)")
                
                // 并发处理当前批次
                val batchResults = coroutineScope {
                    batch.map { info ->
                        async {
                            repairSingleGallery(info, downloadManager)
                        }
                    }.awaitAll()
                }
                
                // 统计结果
                batchResults.forEach { result ->
                    currentProgress++
                    if (result) {
                        successCount++
                    } else {
                        failedCount++
                    }
                }
                
                val progressPercent = if (totalProgress > 0) currentProgress * 100 / totalProgress else 0
                val progressDetail = "修复中 $currentProgress/$totalProgress (成功: $successCount, 失败: $failedCount)"
                
                progressListener?.onProgressChanged(progressPercent, progressDetail)
                notifyStatus()
                
                // 检查是否已取消
                coroutineContext[Job]?.ensureActive()
                
                // 批次间稍作延迟，避免请求过于频繁
                if (batchIndex < batches.size - 1) {
                    Thread.sleep(500)
                }
            }
            
            val finalResult = "修复完成: 成功 $successCount 个, 失败 $failedCount 个"
            Log.i(TAG, finalResult)
            logStep(finalResult)
            progressListener?.onCompleted()
            
            if (failedCount == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("部分画廊修复失败: 成功 $successCount, 失败 $failedCount"))
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "修复任务被取消")
            logStep("修复任务被取消")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "修复任务出错", e)
            logStep("修复任务出错: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun repairSingleGallery(info: DownloadInfo, downloadManager: DownloadManager): Boolean {
        return try {
            Log.d(TAG, "修复画廊: ${info.title} (${info.gid})")
            logStep("开始修复: ${info.title} (${info.gid})")
            
            val success = downloadManager.repairGalleryInfo(info.gid)
            if (success) {
                Log.d(TAG, "修复成功: ${info.title}")
                logStep("修复成功: ${info.title}")
            } else {
                Log.w(TAG, "修复失败: ${info.title}")
                logStep("修复失败: ${info.title}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "修复画廊时出错: ${info.title}", e)
            logStep("修复画廊时出错: ${info.title} - ${e.message}")
            false
        }
    }
    
    override fun isPausable(): Boolean = true
    
    override suspend fun pause() {
        Log.d(TAG, "暂停修复任务")
        isPaused = true
    }
    
    override suspend fun resume() {
        Log.d(TAG, "恢复修复任务")
        isPaused = false
    }
    
    override suspend fun cancel() {
        Log.d(TAG, "取消修复任务")
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
    
    override fun getProgressDetail(): String? {
        return if (totalProgress > 0) {
            "$currentProgress/$totalProgress (成功: $successCount, 失败: $failedCount)"
        } else {
            null
        }
    }
    
    override fun setProgressListener(listener: BackgroundTask.ProgressListener?) {
        this.progressListener = listener
    }

    fun setStatusListener(listener: StatusListener?) {
        statusListener = listener
    }

    fun setLogListener(listener: LogListener?) {
        logListener = listener
    }

    interface StatusListener {
        fun onStatus(current: Int, total: Int, success: Int, failed: Int)
    }

    interface LogListener {
        fun onLog(message: String)
    }
    
    /**
     * 阻塞执行任务（供 Java 代码调用）
     */
    fun executeBlocking(): Result<Unit> {
        return kotlinx.coroutines.runBlocking {
            execute()
        }
    }

    /**
     * 阻塞执行并在失败时抛出异常（便于 Java 调用方处理错误）。
     */
    fun executeBlockingOrThrow() {
        kotlinx.coroutines.runBlocking {
            execute().getOrThrow()
        }
    }
    
    /**
     * 获取修复结果统计
     */
    fun getRepairResult(): RepairResult {
        return RepairResult(successCount, failedCount)
    }
    
    data class RepairResult(
        val successCount: Int,
        val failedCount: Int
    )
}
