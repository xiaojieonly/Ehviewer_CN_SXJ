package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.R
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.lib.yorozuya.collect.LongList

class DeleteRangeDownloadTask(
    context: Context,
    private val downloadManager: DownloadManager,
    private val gidList: LongList,
    private val onCompletedCallback: Runnable? = null
) : BaseBackgroundTask(context) {

    override fun getTaskId(): String = "delete_range_download_${System.currentTimeMillis()}"

    override fun getTaskName(): String = context.getString(R.string.download_remove_dialog_title)

    override fun getTaskDescription(): String? = context.getString(R.string.download_remove_dialog_title)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.CLEANUP

    override fun isPausable(): Boolean = false

    override suspend fun execute(): Result<Unit> {
        val total = gidList.size()
        if (total <= 0) {
            notifyCompleted()
            onCompletedCallback?.run()
            return Result.success(Unit)
        }

        return try {
            downloadManager.deleteRangeDownload(gidList)
            // We update the UI progress by simulating steps after delete call
            for (i in 0 until total) {
                updateProgress(i + 1, context.getString(R.string.download_remove_progress, i + 1, total))
            }
            notifyCompleted()
            onCompletedCallback?.run()
            Result.success(Unit)
        } catch (e: Exception) {
            notifyError(e)
            onCompletedCallback?.run()
            Result.failure(e)
        }
    }
}
