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
     * 是否为唯一任务（同一时间只允许一个未完成任务）
     */
    fun isUniqueTask(): Boolean = getTaskType() != TaskType.DOWNLOAD

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
     * 获取任务的持久化类名，用于恢复任务
     */
    fun getTaskClassName(): String = javaClass.name

    /**
     * 获取任务的可持久化状态字符串，用于在应用重启后恢复任务
     */
    fun getTaskPersistData(): String? = null

    /**
     * 当前任务是否支持持久化恢复
     */
    fun isPersistable(): Boolean = false

    /**
     * 设置进度监听器
     */
    fun setProgressListener(listener: ProgressListener?) {}

    interface ProgressListener {
        @MainThread
        fun onProgressChanged(progress: Int, detail: String?)

        @MainThread
        fun onProgressChanged(current: Int, total: Int, detail: String?) {
            val percent = if (total > 0 && current >= 0) {
                ((current * 100L) / total).toInt()
            } else {
                -1
            }
            onProgressChanged(percent, detail)
        }

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
        IMPORT,            // 导入任务
        EXPORT,            // 导出任务
        BACKUP,            // 备份任务
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
