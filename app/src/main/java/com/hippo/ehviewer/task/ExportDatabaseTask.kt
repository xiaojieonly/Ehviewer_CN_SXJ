package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import com.hippo.util.ReadableTime
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * 导出数据库任务（直接复制 eh.db 文件）
 * 替代原 AdvancedFragment.exportDatabase() 中在UI线程同步执行的操作
 */
class ExportDatabaseTask(
    context: Context
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "ExportDatabaseTask"
        private const val BUFFER_SIZE = 8192
    }

    private val taskId = "export_database_${System.currentTimeMillis()}"

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.settings_advanced_export_database)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_advanced_export_database_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.EXPORT

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备导出数据库...")
            appendTaskLog("开始导出数据库文件")
            delay(200)

            val dbFile = context.getDatabasePath("eh.db")
            if (dbFile == null || !dbFile.exists()) {
                val error = Exception("数据库文件不存在")
                appendTaskLog("导出失败: 数据库文件不存在")
                notifyError(error)
                return Result.failure(error)
            }

            val exportDir = AppConfig.getExternalDataDir()
            if (exportDir == null) {
                val error = Exception("无法获取外部数据目录")
                appendTaskLog("导出失败: 无法获取外部数据目录")
                notifyError(error)
                return Result.failure(error)
            }

            val targetName = "ehviewer_db_export_${ReadableTime.getFilenamableTime(System.currentTimeMillis())}.db"
            val targetFile = File(exportDir, targetName)
            appendTaskLog("目标文件: %s", targetFile.absolutePath)

            val totalSize = dbFile.length()
            var copiedSize = 0L

            updateProgress(10, "正在复制数据库文件...")
            
            FileInputStream(dbFile).use { fis ->
                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var count: Int
                    while (fis.read(buffer).also { count = it } > 0) {
                        coroutineContext.ensureActive()
                        fos.write(buffer, 0, count)
                        copiedSize += count
                        if (totalSize > 0) {
                            val progress = 10 + (copiedSize * 80 / totalSize).toInt()
                            val mbCopied = copiedSize / (1024 * 1024)
                            val mbTotal = totalSize / (1024 * 1024)
                            updateProgress(progress, "正在复制: ${mbCopied}MB / ${mbTotal}MB")
                        }
                    }
                }
            }

            updateProgress(100, "数据库导出成功: ${targetFile.path}")
            appendTaskLog("数据库导出成功: %s", targetFile.absolutePath)
            notifyCompleted()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "导出数据库失败", e)
            appendTaskLog("导出失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }
}
