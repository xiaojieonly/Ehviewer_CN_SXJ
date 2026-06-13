package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import com.hippo.util.LogCat
import com.hippo.util.ReadableTime
import java.io.File
import kotlinx.coroutines.delay

/**
 * 导出日志任务
 * 导出logcat日志到文件
 * 替代原 AdvancedFragment.dumpLogcat() 中的同步操作
 */
class DumpLogcatTask(
    context: Context
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "DumpLogcatTask"
    }

    private val taskId = "dump_logcat_${System.currentTimeMillis()}"

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.settings_advanced_dump_logcat)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_advanced_dump_logcat_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.EXPORT

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备导出日志...")
            appendTaskLog("开始导出logcat日志")
            delay(200)

            val dir = AppConfig.getExternalLogcatDir()
            if (dir == null) {
                val error = Exception("无法获取日志目录")
                appendTaskLog("导出失败: 无法获取日志目录")
                notifyError(error)
                return Result.failure(error)
            }

            val file = File(dir, "logcat-${ReadableTime.getFilenamableTime(System.currentTimeMillis())}.txt")
            updateProgress(30, "正在捕获日志...")
            appendTaskLog("目标文件: %s", file.absolutePath)

            val ok = LogCat.save(file)
            if (ok) {
                updateProgress(100, "日志导出成功: ${file.path}")
                appendTaskLog("日志导出成功: %s", file.absolutePath)
                notifyCompleted()
                Result.success(Unit)
            } else {
                val error = Exception("日志导出失败")
                appendTaskLog("导出失败: LogCat.save 返回 false")
                notifyError(error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败", e)
            appendTaskLog("导出失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }
}
