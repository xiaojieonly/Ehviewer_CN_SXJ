package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.ehviewer.BackgroundTaskManager
import kotlinx.coroutines.Job

/**
 * 鍚庡彴浠诲姟鍩虹瀹炵幇绫?
 * 鎻愪緵閫氱敤鐨勪换鍔＄鐞嗗姛鑳?
 */
abstract class BaseBackgroundTask(
    protected val context: Context
) : BackgroundTask {

    protected var currentState = TaskState.PENDING
    protected var currentProgress = -1
    protected var _progressDetail: String? = null
    protected var _progressListener: BackgroundTask.ProgressListener? = null
    protected var job: Job? = null

    protected val backgroundTaskManager = BackgroundTaskManager.getInstance()

    override fun getProgress(): Int = currentProgress

    override fun getProgressDetail(): String? = _progressDetail

    override fun setProgressListener(listener: BackgroundTask.ProgressListener?) {
        this._progressListener = listener
    }

    /**
     * 鏇存柊杩涘害
     */
    protected fun updateProgress(progress: Int, detail: String? = null) {
        currentProgress = progress
        _progressDetail = detail
        _progressListener?.onProgressChanged(progress, _progressDetail)
    }

    /**
     * 浣跨敤褰撳墠鍊?鎬诲€兼洿鏂拌繘搴︺€?
     */
    protected fun updateProgress(current: Int, total: Int, detail: String?) {
        _progressDetail = detail
        currentProgress = if (current >= 0 && total > 0) {
            ((current * 100L) / total).toInt()
        } else {
            -1
        }
        if (current >= 0 && total > 0) {
            _progressListener?.onProgressChanged(current, total, _progressDetail)
        } else {
            _progressListener?.onProgressChanged(-1, _progressDetail)
        }
    }

    protected fun appendTaskLog(message: String) {
        backgroundTaskManager.getTaskStatusManager().appendTaskLog(getTaskId(), message)
    }

    protected fun appendTaskLog(format: String, vararg args: Any?) {
        appendTaskLog(String.format(format, *args))
    }

    /**
     * 鏇存柊浠诲姟鐘舵€?
     */
    protected fun updateState(state: TaskState) {
        currentState = state
        when (state) {
            TaskState.RUNNING -> {
                // 浠诲姟寮€濮嬭繍琛屾椂鐨勫鐞?
            }
            TaskState.COMPLETED -> {
                _progressListener?.onCompleted()
            }
            TaskState.FAILED -> {
                // 閿欒淇℃伅搴旇鍦ㄥ叿浣撳疄鐜颁腑澶勭悊
            }
            TaskState.CANCELLED -> {
                // 鍙栨秷澶勭悊
            }
            else -> {}
        }
    }

    /**
     * 閫氱煡閿欒
     */
    protected fun notifyError(error: Throwable) {
        updateState(TaskState.FAILED)
        _progressListener?.onError(error)
    }

    /**
     * 閫氱煡瀹屾垚
     */
    protected fun notifyCompleted() {
        updateState(TaskState.COMPLETED)
    }

    /**
     * 閫氱煡鍙栨秷
     */
    protected fun notifyCancelled() {
        updateState(TaskState.CANCELLED)
    }

    override suspend fun cancel() {
        if (!isPausable()) {
            throw UnsupportedOperationException("Task does not support cancel")
        }
        job?.cancel()
        notifyCancelled()
    }
}
