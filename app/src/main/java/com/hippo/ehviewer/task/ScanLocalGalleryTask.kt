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
            val latch = java.util.concurrent.CountDownLatch(1)
            val localResults = java.util.concurrent.atomic.AtomicReference<List<com.hippo.ehviewer.client.data.LocalGalleryInfo>>() 

            val listener = object : LocalGalleryManager.LocalGalleryListener {
                override fun onScanStart() {
                    // no-op
                }

                override fun onScanProgress(current: String) {
                    if (isCancelled) return
                    val progressDetail = context.getString(R.string.local_gallery_scan_progress_simple, 0, 0)
                    progressListener?.onProgressChanged(-1, progressDetail)
                }

                override fun onScanComplete(localGalleries: List<com.hippo.ehviewer.client.data.LocalGalleryInfo>, recycleBinGalleries: List<com.hippo.ehviewer.client.data.LocalGalleryInfo>) {
                    localResults.set(localGalleries)
                    latch.countDown()
                }

                override fun onGalleryDeleted(gallery: com.hippo.ehviewer.client.data.LocalGalleryInfo, success: Boolean) {
                    // no-op
                }

                override fun onGalleryRestored(gallery: com.hippo.ehviewer.client.data.LocalGalleryInfo, success: Boolean) {
                    // no-op
                }
            }

            manager.addListener(listener)
            try {
                manager.scanLocalGalleries(true)
                while (!latch.await(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    if (isCancelled) {
                        throw CancellationException("Task cancelled")
                    }
                }
            } finally {
                manager.removeListener(listener)
            }

            val results = localResults.get() ?: manager.getCachedLocalGalleries()
            if (isCancelled) {
                throw CancellationException("Task cancelled")
            }

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
