package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import com.hippo.unifile.UniFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * 导出下载列表任务
 * 将下载列表导出为CSV文件
 * 替代原 DownloadFragment.exportDownloadItems() 中在UI线程同步执行的操作
 */
class ExportDownloadItemsTask(
    context: Context
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "ExportDownloadItemsTask"
    }

    private val taskId = "export_download_items_${System.currentTimeMillis()}"

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.settings_download_export_download_items)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_advanced_export_data_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.EXPORT

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备导出下载列表...")
            appendTaskLog("开始导出下载列表")
            delay(200)

            val list = EhApplication.getDownloadManager(context).downloadInfoList
            if (list.isEmpty()) {
                appendTaskLog("下载列表为空，无需导出")
                updateProgress(100, "下载列表为空")
                notifyCompleted()
                return Result.success(Unit)
            }

            val dir = Settings.getDownloadLocation()
            if (dir == null) {
                val error = Exception("下载目录无效")
                appendTaskLog("导出失败: 下载目录无效")
                notifyError(error)
                return Result.failure(error)
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            val fileName = "ehviewer-download-${sdf.format(Date())}.csv"

            updateProgress(10, "正在创建导出文件...")
            val file = dir.createFile(fileName)
            if (file == null) {
                val error = Exception("无法创建导出文件")
                appendTaskLog("导出失败: 无法创建文件 %s", fileName)
                notifyError(error)
                return Result.failure(error)
            }

            val total = list.size
            appendTaskLog("共 %d 条下载记录待导出", total)

            updateProgress(20, "正在写入数据...")
            file.openOutputStream().use { os ->
                os.write(DownloadManager.DOWNLOAD_INFO_HEADER.toByteArray(StandardCharsets.UTF_8))
                for ((index, gi) in list.withIndex()) {
                    coroutineContext.ensureActive()
                    os.write(gi.toCSV().toByteArray(StandardCharsets.UTF_8))
                    val progress = 20 + (index * 70 / total)
                    updateProgress(progress, "正在导出: ${index + 1}/$total")
                }
            }

            updateProgress(100, "下载列表导出成功: ${file.uri}")
            appendTaskLog("导出成功: %s", file.uri.toString())
            notifyCompleted()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "导出下载列表失败", e)
            appendTaskLog("导出失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }
}
