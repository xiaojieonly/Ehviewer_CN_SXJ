package com.hippo.ehviewer.task

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import com.hippo.yorozuya.IOUtils

/**
 * 导入下载列表任务
 * 从CSV文件导入下载列表
 * 替代原 DownloadFragment.ImportDownloadTask 内部类的实现
 */
class ImportDownloadItemsTask(
    context: Context,
    private val uri: Uri
) : BaseBackgroundTask(context) {

    companion object {
        private const val TAG = "ImportDownloadItemsTask"
    }

    private val taskId = "import_download_items_${System.currentTimeMillis()}"

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.settings_download_import_items)

    override fun getTaskDescription(): String? = context.getString(R.string.settings_advanced_import_data_summary)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.IMPORT

    private var successCount = 0
    private var failCount = 0
    private var skipCount = 0

    override suspend fun execute(): Result<Unit> {
        return try {
            updateState(TaskState.RUNNING)
            updateProgress(0, "准备导入下载列表...")
            appendTaskLog("开始导入下载列表")
            delay(200)

            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                val error = Exception("无法打开导入文件")
                appendTaskLog("导入失败: 无法打开文件")
                notifyError(error)
                return Result.failure(error)
            }

            updateProgress(10, "正在读取文件...")
            val content = inputStream.use { IOUtils.readString(it, StandardCharsets.UTF_8.name()) }
            val lines = content.split("\n")
            appendTaskLog("文件共 %d 行", lines.size)

            val galleryInfos = mutableListOf<GalleryInfo>()
            for (line in lines) {
                if (line.startsWith(DownloadManager.DOWNLOAD_INFO_HEADER)) continue
                val gi = GalleryInfo.fromCSV(line)
                if (gi != null) {
                    galleryInfos.add(gi)
                }
            }

            val total = galleryInfos.size
            appendTaskLog("解析到 %d 条下载记录", total)

            if (total == 0) {
                updateProgress(100, "没有可导入的下载记录")
                appendTaskLog("没有可导入的下载记录")
                notifyCompleted()
                return Result.success(Unit)
            }

            val downloadManager = EhApplication.getDownloadManager(context)
            successCount = 0
            failCount = 0
            skipCount = 0

            for ((index, gi) in galleryInfos.withIndex()) {
                coroutineContext.ensureActive()
                try {
                    if (downloadManager.getDownloadInfo(gi.gid) == null) {
                        downloadManager.addDownload(gi, null)
                        successCount++
                        appendTaskLog("导入成功: %s (GID: %d)", gi.title, gi.gid)
                    } else {
                        skipCount++
                        appendTaskLog("跳过已存在: %s (GID: %d)", gi.title, gi.gid)
                    }
                } catch (e: Exception) {
                    failCount++
                    appendTaskLog("导入失败: %s - %s", gi.title, e.message ?: "")
                }
                val progress = 20 + (index * 70 / total)
                updateProgress(progress, "正在导入: ${index + 1}/$total (成功:$successCount 跳过:$skipCount 失败:$failCount)")
            }

            val summary = "导入完成: 成功 $successCount, 跳过 $skipCount, 失败 $failCount"
            updateProgress(100, summary)
            appendTaskLog(summary)
            notifyCompleted()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "导入下载列表失败", e)
            appendTaskLog("导入失败: %s", e.message ?: "")
            notifyError(e)
            Result.failure(e)
        }
    }
}
