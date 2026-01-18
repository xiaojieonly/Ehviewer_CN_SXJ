package com.hippo.ehviewer.task

import androidx.annotation.MainThread
import kotlinx.coroutines.Job

/**
 * 后台任务接口
 * 定义所有后台任务应该实现的基本功能
 */
interface BackgroundTask {
    /**
     * 获取任务ID
     */
    fun getTaskId(): String

    /**
     * 获取任务名称（显示在通知栏和设置中）
     */
    fun getTaskName(): String

    /**
     * 获取任务描述（显示在通知栏和设置中）
     */
    fun getTaskDescription(): String?

    /**
     * 获取任务类型（用于分类管理）
     */
    fun getTaskType(): TaskType

    /**
     * 执行任务
     * 返回关联的协程Job对象
     */
    suspend fun execute(): Result<Unit>

    /**
     * 任务是否支持暂停
     */
    fun isPausable(): Boolean = false

    /**
     * 暂停任务
     */
    @MainThread
    suspend fun pause() {
        throw UnsupportedOperationException("Task does not support pause")
    }

    /**
     * 恢复任务
     */
    @MainThread
    suspend fun resume() {
        throw UnsupportedOperationException("Task does not support resume")
    }

    /**
     * 取消任务
     */
    @MainThread
    suspend fun cancel() {
        throw UnsupportedOperationException("Task does not support cancel")
    }

    /**
     * 获取任务进度（0-100）
     * 返回-1表示进度不确定
     */
    fun getProgress(): Int = -1

    /**
     * 获取任务进度详情
     */
    fun getProgressDetail(): String? = null

    /**
     * 设置进度监听器
     */
    fun setProgressListener(listener: ProgressListener?) {}

    interface ProgressListener {
        @MainThread
        fun onProgressChanged(progress: Int, detail: String?)

        @MainThread
        fun onCompleted()

        @MainThread
        fun onError(error: Throwable)
    }

    enum class TaskType {
        DOWNLOAD,           // 下载任务
        SYNC,              // 同步任务
        SCAN,              // 扫描任务
        CLEANUP,           // 清理任务
        MERGE,             // 合并任务
        UPDATE,            // 更新任务
        TRANSFER,          // 传输任务
        OTHER              // 其他任务
    }
}

/**
 * 后台任务状态
 */
enum class TaskState {
    PENDING,             // 等待中
    RUNNING,             // 运行中
    PAUSED,              // 已暂停
    COMPLETED,           // 已完成
    FAILED,              // 失败
    CANCELLED            // 已取消
}

/**
 * 后台任务执行结果
 */
sealed class TaskResult {
    object Success : TaskResult()
    data class Error(val exception: Throwable) : TaskResult()
    object Cancelled : TaskResult()
}
