package com.hippo.ehviewer.task

import android.content.Context
import android.widget.Toast
import android.util.Log
import com.hippo.ehviewer.DownloadedFileManager
import com.hippo.ehviewer.DownloadedFileManagerScanListener
import com.hippo.ehviewer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 扫描下载文件任务
 * 用于扫描下载目录，重建下载文件信息数据库
 * 使用Toast提示而非ProgressDialog
 */
class ScanDownloadTask(
    private val context: Context
) : BackgroundTask {
    
    companion object {
        private const val TAG = "ScanDownloadTask"
    }
    
    private val taskId = "scan_download_${System.currentTimeMillis()}"
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    private var progressListener: BackgroundTask.ProgressListener? = null
    private var statusListener: StatusListener? = null
    private var logListener: LogListener? = null
    
    private var currentProgress = 0
    private var totalProgress = 0

    private fun notifyStatus(detail: String? = null) {
        statusListener?.onStatus(currentProgress, totalProgress, detail)
    }

    private fun logStep(message: String) {
        logListener?.onLog(message)
    }
    
    override fun getTaskId(): String = taskId
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_scan_download_files)
    
    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.SCAN
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_scan_download_files_summary)
    
    override suspend fun execute(): Result<Unit> {
        return try {
            Log.d(TAG, "开始扫描下载文件")
            logStep("开始扫描下载文件")
            
            // 显示开始扫描的Toast
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.scan_download_files_scanning, Toast.LENGTH_SHORT).show()
            }
            
            // 检查是否已取消
            coroutineContext[Job]?.ensureActive()
            
            val manager = DownloadedFileManager.getInstance()
            
            val scanCompleted = AtomicBoolean(false)
            val scanStatus = AtomicInteger(DownloadedFileManager.SCAN_STATUS_IDLE)
            val scanError = AtomicReference<String?>(null)

            val scanListener = object : DownloadedFileManagerScanListener {
                override fun onProgress(current: Int, total: Int) {
                    currentProgress = current
                    totalProgress = total
                    progressListener?.onProgressChanged(
                        if (total > 0) current * 100 / total else 0,
                        "$current/$total"
                    )
                    notifyStatus("$current/$total")

                    Log.d(TAG, "扫描进度: $current/$total")
                    logStep("扫描进度: $current/$total")

                    // 检查暂停状态
                    while (isPaused && !isCancelled) {
                        Thread.sleep(100)
                    }

                    // 检查取消状态
                    if (isCancelled) {
                        throw CancellationException("任务已取消")
                    }
                }

                override fun onCompleted() {
                    Log.d(TAG, "扫描完成")
                    logStep("扫描完成")
                    scanStatus.set(DownloadedFileManager.SCAN_STATUS_COMPLETED)
                    scanCompleted.set(true)
                }

                override fun onError(e: Exception) {
                    Log.e(TAG, "扫描出错", e)
                    logStep("扫描出错: ${e.message}")
                    scanStatus.set(DownloadedFileManager.SCAN_STATUS_ERROR)
                    scanError.set(e.message)
                    scanCompleted.set(true)
                }
            }

            // 执行扫描
            manager.scanDownloadDirectories(scanListener)

            // 等待扫描完成
            while (!scanCompleted.get()) {
                Thread.sleep(100)

                // 检查取消状态
                if (isCancelled) {
                    throw CancellationException("任务已取消")
                }

                // 检查暂停状态
                while (isPaused && !isCancelled) {
                    Thread.sleep(100)
                }
            }

            // 检查扫描结果
            when (scanStatus.get()) {
                DownloadedFileManager.SCAN_STATUS_COMPLETED -> {
                    Log.d(TAG, "扫描成功完成")
                    logStep("扫描成功完成")
                    // 显示完成Toast
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.scan_download_files_completed), Toast.LENGTH_SHORT).show()
                    }
                    Result.success(Unit)
                }
                DownloadedFileManager.SCAN_STATUS_ERROR -> {
                    val error = scanError.get() ?: "未知错误"
                    Log.e(TAG, "扫描失败: $error")
                    logStep("扫描失败: $error")
                    // 显示失败Toast
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.scan_download_files_failed) + ": " + error, Toast.LENGTH_LONG).show()
                    }
                    Result.failure(Exception(error))
                }
                else -> {
                    Log.w(TAG, "扫描状态未知: ${scanStatus.get()}")
                    logStep("扫描状态未知: ${scanStatus.get()}")
                    Result.failure(Exception("扫描状态未知"))
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "扫描任务被取消")
            logStep("扫描任务被取消")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.scan_download_files_cancelled, Toast.LENGTH_SHORT).show()
            }
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "扫描任务出错", e)
            logStep("扫描任务出错: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.scan_download_files_failed) + ": " + e.message, Toast.LENGTH_LONG).show()
            }
            Result.failure(e)
        }
    }
    
    override fun isPausable(): Boolean = true
    
    override suspend fun pause() {
        Log.d(TAG, "暂停扫描任务")
        isPaused = true
    }
    
    override suspend fun resume() {
        Log.d(TAG, "恢复扫描任务")
        isPaused = false
    }
    
    override suspend fun cancel() {
        Log.d(TAG, "取消扫描任务")
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
        return if (totalProgress > 0) "$currentProgress/$totalProgress" else null
    }

    fun setStatusListener(listener: StatusListener?) {
        statusListener = listener
    }

    fun setLogListener(listener: LogListener?) {
        logListener = listener
    }

    interface StatusListener {
        fun onStatus(current: Int, total: Int, detail: String?)
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
}
