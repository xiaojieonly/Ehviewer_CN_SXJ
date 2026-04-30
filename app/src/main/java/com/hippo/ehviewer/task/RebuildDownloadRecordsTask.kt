package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.lib.yorozuya.IOUtils
import com.hippo.lib.yorozuya.NumberUtils
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext

/**
 * 重建下载记录任务
 * 扫描下载目录，重建丢失的下载记录
 */
class RebuildDownloadRecordsTask(
    private val context: Context
) : BackgroundTask {
    
    companion object {
        private const val TAG = "RebuildDownloadRecordsTask"
    }
    
    private val taskId = "rebuild_download_records_${System.currentTimeMillis()}"
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    private var progressListener: BackgroundTask.ProgressListener? = null
    private var statusListener: StatusListener? = null
    private var logListener: LogListener? = null
    
    private var currentProgress = 0
    private var totalProgress = 0
    private var rebuildCount = 0
    private val logs = mutableListOf<String>()

    private fun notifyStatus(detail: String? = null) {
        statusListener?.onStatus(currentProgress, totalProgress, rebuildCount, detail)
    }

    private fun logStep(message: String) {
        logs.add(message)
        logListener?.onLog(message)
    }
    
    override fun getTaskId(): String = taskId
    
    override fun getTaskName(): String = context.getString(R.string.settings_download_rebuild_download_records)
    
    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.SCAN
    
    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_rebuild_download_records_summary)
    
    override suspend fun execute(): Result<Unit> {
        return try {
            Log.d(TAG, "开始重建下载记录")
            logStep("开始重建下载记录")
            
            // 检查是否已取消
            coroutineContext[Job]?.ensureActive()
            
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
            rebuildCount = 0
            notifyStatus()
            
            val downloadManager = EhApplication.getDownloadManager(context)
            val galleriesToAdd = mutableListOf<GalleryInfo>()
            
            Log.d(TAG, "共需处理 $totalProgress 个文件")
            logStep("共需处理 $totalProgress 个文件")
            
            for (dir in files) {
                // 检查取消状态
                if (isCancelled) {
                    throw CancellationException("任务已取消")
                }
                
                // 检查暂停状态
                while (isPaused && !isCancelled) {
                    Thread.sleep(100)
                }
                
                // 处理目录
                if (dir.isDirectory()) {
                    processDirectory(dir, downloadManager, galleriesToAdd)
                }
                
                currentProgress++
                val percent = if (totalProgress > 0) currentProgress * 100 / totalProgress else 0
                progressListener?.onProgressChanged(percent, "$currentProgress/$totalProgress")
                notifyStatus("已发现待重建: ${galleriesToAdd.size}")
            }
            
            // 批量添加画廊信息
            if (galleriesToAdd.isNotEmpty()) {
                Log.d(TAG, "批量添加 ${galleriesToAdd.size} 个画廊")
                logStep("批量添加 ${galleriesToAdd.size} 个画廊")
                for (galleryInfo in galleriesToAdd) {
                    if (isCancelled) {
                        throw CancellationException("任务已取消")
                    }
                    
                    try {
                        downloadManager.addDownload(galleryInfo, null)
                        rebuildCount++
                        logStep("已重建: ${galleryInfo.gid}")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加下载失败: ${galleryInfo.gid}", e)
                        logStep("添加失败: ${galleryInfo.gid} - ${e.message}")
                    }
                }
            }
            
            Log.d(TAG, "重建完成，共重建 $rebuildCount 个记录")
            logStep("重建完成，共重建 $rebuildCount 个记录")
            Result.success(Unit)
            
        } catch (e: CancellationException) {
            Log.d(TAG, "重建任务被取消")
            logStep("重建任务被取消")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "重建任务出错", e)
            logStep("重建任务出错: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 处理目录
     */
    private fun processDirectory(
        dir: UniFile,
        downloadManager: DownloadManager,
        galleriesToAdd: MutableList<GalleryInfo>
    ) {
        val ehViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME) ?: return
        
        try {
            // 读取.ehviewer文件
            val content = IOUtils.readString(ehViewerFile.openInputStream(), StandardCharsets.UTF_8.name())
            val lines = content.split("\n")
            
            if (lines.isEmpty()) {
                return
            }
            
            // 解析GID
            val gid = NumberUtils.parseLongSafely(lines[0], -1)
            if (gid == -1L) {
                return
            }
            
            // 检查是否已存在
            if (downloadManager.getDownloadInfo(gid) != null) {
                return
            }
            
            // 解析画廊信息
            val galleryInfo = parseGalleryInfoFromLines(lines) ?: return
            
            // 添加到待处理列表
            galleriesToAdd.add(galleryInfo)
            logStep("发现需要重建: ${galleryInfo.gid} - ${galleryInfo.title}")
            
        } catch (e: IOException) {
            Log.e(TAG, "读取.ehviewer文件失败: ${dir.name}", e)
            logStep("读取.ehviewer文件失败: ${dir.name} - ${e.message}")
        }
    }
    
    /**
     * 从.ehviewer文件行解析画廊信息
     */
    private fun parseGalleryInfoFromLines(lines: List<String>): GalleryInfo? {
        if (lines.size < 3) {
            return null
        }
        
        val gid = NumberUtils.parseLongSafely(lines[0], -1)
        val token = lines[1]
        val title = lines[2]
        
        if (gid == -1L || token.isEmpty() || title.isEmpty()) {
            return null
        }
        
        val galleryInfo = GalleryInfo()
        galleryInfo.gid = gid
        galleryInfo.token = token
        galleryInfo.title = title
        
        return galleryInfo
    }
    
    override fun isPausable(): Boolean = true
    
    override suspend fun pause() {
        Log.d(TAG, "暂停重建任务")
        isPaused = true
    }
    
    override suspend fun resume() {
        Log.d(TAG, "恢复重建任务")
        isPaused = false
    }
    
    override suspend fun cancel() {
        Log.d(TAG, "取消重建任务")
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
        return if (totalProgress > 0) "$currentProgress/$totalProgress (重建: $rebuildCount)" else null
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
     * 获取重建的记录数量
     */
    fun getRebuildCount(): Int = rebuildCount
    
    /**
     * 获取日志
     */
    fun getLogs(): List<String> = logs.toList()

    fun setStatusListener(listener: StatusListener?) {
        statusListener = listener
    }

    fun setLogListener(listener: LogListener?) {
        logListener = listener
    }

    interface StatusListener {
        fun onStatus(current: Int, total: Int, rebuilt: Int, detail: String?)
    }

    interface LogListener {
        fun onLog(message: String)
    }
}
