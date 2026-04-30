package com.hippo.ehviewer.task

import com.hippo.ehviewer.task.BackgroundTask

/**
 * 任务执行信息
 * 用于在UI中显示任务的状态和进度
 */
data class TaskExecutionInfo(
    val taskId: String,
    val taskName: String,
    val taskDescription: String?,
    val taskType: BackgroundTask.TaskType,
    val state: TaskState,
    val task: BackgroundTask,
    val progress: Int = -1,
    val progressDetail: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val error: Throwable? = null
) {
    /**
     * 获取任务执行时长（秒）
     */
    fun getDurationSeconds(): Long {
        val currentTime = endTime ?: System.currentTimeMillis()
        return (currentTime - startTime) / 1000
    }
}
