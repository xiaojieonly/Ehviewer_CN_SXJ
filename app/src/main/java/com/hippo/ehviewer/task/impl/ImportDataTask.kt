package com.hippo.ehviewer.task.impl

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import java.io.File

/**
 * 导入数据任务
 * 从.db文件导入数据库（下载记录、历史、收藏等）
 */
class ImportDataTask(
    context: Context,
    private val dbFile: File
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "ImportDataTask"
    }

    override fun getTaskId(): String = "import_data_${System.currentTimeMillis()}"

    override fun getTaskName(): String = context.getString(R.string.settings_advanced_import_data)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_advanced_import_data_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.IMPORT

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备导入数据库...")
            appendTaskLog("开始导入数据库: %s", dbFile.name)

            if (!dbFile.exists() || !dbFile.isFile) {
                val error = Exception("数据库文件不存在: ${dbFile.name}")
                appendTaskLog("错误: %s", error.message ?: "")
                notifyError(error)
                return Result.failure(error)
            }

            updateProgress(10, "正在读取数据库文件...")
            appendTaskLog("文件大小: %d bytes", dbFile.length())

            updateProgress(30, "正在导入数据...")
            val error = EhDB.importDB(context, dbFile, null)

            if (error != null) {
                appendTaskLog("导入失败: %s", error)
                updateProgress(100, "导入失败: $error")
                val ex = Exception(error)
                notifyError(ex)
                return Result.failure(ex)
            }

            updateProgress(100, "数据导入成功")
            appendTaskLog("数据导入完成")
            notifyCompleted()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "导入数据失败", e)
            appendTaskLog("导入失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }
}
