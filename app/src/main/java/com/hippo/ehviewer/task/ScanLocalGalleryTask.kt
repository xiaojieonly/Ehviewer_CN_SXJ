package com.hippo.ehviewer.task

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.R
import com.hippo.ehviewer.local.LocalGalleryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class ScanLocalGalleryTask(
    private val context: Context
) : BackgroundTask {

    companion object {
        private const val TAG = "ScanLocalGalleryTask"
    }

    private val taskId = "scan_local_gallery_${System.currentTimeMillis()}"
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    private var progressListener: BackgroundTask.ProgressListener? = null

    private var currentProgress = 0
    private var totalProgress = 0

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.local_gallery_scan_task_name)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.SCAN

    override fun getTaskDescription(): String? = context.getString(R.string.local_gallery_scan_task_summary)

    override suspend fun execute(): Result<Unit> {
        return try {
            Log.d(TAG, "Start local gallery scan")
            coroutineContext[Job]?.ensureActive()

            val manager = LocalGalleryManager.getInstance(context)
            val results = manager.scanLocalGalleriesSync { current, total, detail ->
                currentProgress = current
                totalProgress = total

                val percent = if (total > 0) current * 100 / total else -1
                val progressDetail = if (detail.isNotEmpty()) {
                    context.getString(R.string.local_gallery_scan_progress, current, total, detail)
                } else {
                    context.getString(R.string.local_gallery_scan_progress_simple, current, total)
                }

                progressListener?.onProgressChanged(percent, progressDetail)
                manager.reportScanProgress(progressDetail)

                while (isPaused && !isCancelled) {
                    Thread.sleep(100)
                }

                if (isCancelled) {
                    return@scanLocalGalleriesSync false
                }

                true
            }

            if (isCancelled) {
                throw CancellationException("Task cancelled")
            }

            manager.reportLocalScanComplete(results)
            Result.success(Unit)
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Local gallery scan failed", e)
            Result.failure(e)
        }
    }

    override fun isPausable(): Boolean = true

    override suspend fun pause() {
        isPaused = true
    }

    override suspend fun resume() {
        isPaused = false
    }

    override suspend fun cancel() {
        isCancelled = true
        isPaused = false
    }

    override fun getProgress(): Int {
        return if (totalProgress > 0) {
            currentProgress * 100 / totalProgress
        } else {
            -1
        }
    }

    override fun getProgressDetail(): String? {
        return if (totalProgress > 0) "$currentProgress/$totalProgress" else null
    }

    override fun setProgressListener(listener: BackgroundTask.ProgressListener?) {
        progressListener = listener
    }
}
