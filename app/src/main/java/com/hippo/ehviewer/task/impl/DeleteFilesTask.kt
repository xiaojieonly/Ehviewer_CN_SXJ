package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.local.LocalGalleryManager
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.unifile.UniFile

class DeleteFilesTask(
    context: Context,
    private val files: Array<out UniFile?>
) : BaseBackgroundTask(context) {

    override fun getTaskId(): String = "delete_files_task_${System.currentTimeMillis()}"

    override fun getTaskName(): String = context.getString(com.hippo.ehviewer.R.string.delete)

    override fun getTaskDescription(): String? = context.getString(com.hippo.ehviewer.R.string.delete)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.CLEANUP

    override fun isPausable(): Boolean = false

    override suspend fun execute(): Result<Unit> {
        return try {
            val context = this.context
            val manager = LocalGalleryManager.getInstance(context)
            for (file in files) {
                if (file == null) continue
                if (manager != null && file.exists() && file.getUri() != null) {
                    val info = com.hippo.ehviewer.client.data.LocalGalleryInfo(file.getUri().getPath())
                    manager.deleteGallery(info)
                } else if (file.exists()) {
                    file.delete()
                }
            }
            notifyCompleted()
            Result.success(Unit)
        } catch (e: Exception) {
            notifyError(e)
            Result.failure(e)
        }
    }
}
