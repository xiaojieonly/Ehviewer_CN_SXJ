package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.TaskState
import com.hippo.ehviewer.BackgroundTaskManager
import kotlinx.coroutines.Job

/**
 * 后台任务基础实现类
 * 提供通用的任务管理功能
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
     * 更新进度
     */
    protected fun updateProgress(progress: Int, detail: String? = null) {
        currentProgress = progress
        _progressDetail = detail
        _progressListener?.onProgressChanged(progress, _progressDetail)
    }

    /**
     * 使用当前值/总值更新进度。
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
     * 更新任务状态
     */
    protected fun updateState(state: TaskState) {
        currentState = state
        when (state) {
            TaskState.RUNNING -> {
                // 任务开始运行时的处理
            }
            TaskState.COMPLETED -> {
                _progressListener?.onCompleted()
            }
            TaskState.FAILED -> {
                // 错误信息应该在具体实现中处理
            }
            TaskState.CANCELLED -> {
                // 取消处理
            }
            else -> {}
        }
    }
    
    /**
     * 通知错误
     */
    protected fun notifyError(error: Throwable) {
        updateState(TaskState.FAILED)
        _progressListener?.onError(error)
    }
    
    /**
     * 通知完成
     */
    protected fun notifyCompleted() {
        updateState(TaskState.COMPLETED)
    }
    
    /**
     * 通知取消
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