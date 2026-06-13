package com.hippo.ehviewer.task

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import com.hippo.unifile.UniFile
import kotlinx.coroutines.delay
import java.io.File

/**
 * 重置媒体扫描任务
 * 触发系统重新扫描下载目录中的媒体文件
 * 替代原 DownloadFragment.resetMediaScan() 中的操作
 */
class ResetMediaScanTask(
    context: Context
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "ResetMediaScanTask"
    }

    private val taskId = "reset_media_scan_${System.currentTimeMillis()}"

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.settings_download_reset_media_scan)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_reset_media_scan_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.OTHER

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备重置媒体扫描...")
            appendTaskLog("开始重置媒体扫描")
            delay(100)

            val downloadLocation = Settings.getDownloadLocation()
            if (downloadLocation == null) {
                val error = Exception("下载目录未设置")
                appendTaskLog("下载目录未设置")
                notifyError(error)
                return Result.failure(error)
            }

            val downloadUri = downloadLocation.uri
            if (downloadUri != null && downloadUri.path != null) {
                val downloadPath = downloadUri.path!!
                val downloadDir = File(downloadPath)
                if (downloadDir.exists()) {
                    updateProgress(20, "正在收集文件列表...")
                    val filePaths = collectAllFiles(downloadDir)
                    val totalFiles = filePaths.size
                    appendTaskLog("共 %d 个文件待扫描", totalFiles)

                    updateProgress(40, "开始媒体扫描: 共 $totalFiles 个文件")

                    MediaScannerConnection.scanFile(
                        context,
                        filePaths.toTypedArray(),
                        null
                    ) { _, _ ->
                        // 扫描完成回调
                    }

                    updateProgress(100, "媒体扫描重置完成，共 $totalFiles 个文件")
                    appendTaskLog("媒体扫描重置完成，共 %d 个文件", totalFiles)
                    notifyCompleted()
                    Result.success(Unit)
                } else {
                    val error = Exception("下载目录不存在: $downloadPath")
                    appendTaskLog("目录不存在: %s", downloadPath)
                    notifyError(error)
                    Result.failure(error)
                }
            } else {
                val error = Exception("无法获取下载目录路径")
                appendTaskLog("无法获取下载目录路径")
                notifyError(error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "重置媒体扫描失败", e)
            appendTaskLog("重置失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }

    private fun collectAllFiles(dir: File): List<String> {
        val result = mutableListOf<String>()
        val files = dir.listFiles() ?: return result
        for (file in files) {
            if (file.isDirectory) {
                result.addAll(collectAllFiles(file))
            } else {
                result.add(file.absolutePath)
            }
        }
        return result
    }
}
