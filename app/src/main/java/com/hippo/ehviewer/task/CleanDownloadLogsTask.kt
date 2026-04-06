package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.R
import com.hippo.ehviewer.download.DownloadLogger
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import kotlinx.coroutines.delay
import java.io.File

/**
 * 清理下载日志任务
 * 清理下载日志文件
 * 替代原 DownloadFragment 中的清理日志操作
 */
class CleanDownloadLogsTask(
    context: Context
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "CleanDownloadLogsTask"
    }

    private val taskId = "clean_download_logs_${System.currentTimeMillis()}"

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.settings_download_clean_logs)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_download_clean_logs_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.CLEANUP

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备清理下载日志...")
            appendTaskLog("开始清理下载日志")
            delay(100)

            val downloadLogger = DownloadLogger.getInstance()
            val logDir: File? = downloadLogger.logDirectory

            if (logDir != null && logDir.exists()) {
                val files = logDir.listFiles()
                if (files != null) {
                    var deletedCount = 0
                    for (file in files) {
                        if (file.isFile) {
                            if (file.delete()) {
                                deletedCount++
                            }
                        }
                    }
                    val summary = "清理完成: 删除了 $deletedCount 个日志文件"
                    updateProgress(100, summary)
                    appendTaskLog(summary)
                    notifyCompleted()
                    Result.success(Unit)
                } else {
                    updateProgress(100, "没有日志文件需要清理")
                    appendTaskLog("没有日志文件")
                    notifyCompleted()
                    Result.success(Unit)
                }
            } else {
                updateProgress(100, "日志目录不存在")
                appendTaskLog("日志目录不存在")
                notifyCompleted()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理下载日志失败", e)
            appendTaskLog("清理失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }
}
