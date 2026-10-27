package com.hippo.ehviewer.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hippo.ehviewer.BackgroundTaskManager
import com.hippo.ehviewer.util.UiThreadHelper

/**
 * Java 兼容的任务执行器
 * 用于从 Java 代码执行 Kotlin 的 suspend 函数
 */
object TaskExecutor {

    /**
        * 在后台线程执行任务
        * @param task 要执行的后台任务
     */
    @JvmStatic
    fun executeTask(task: BackgroundTask) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = task.execute()
                // 确保成功回调在主线程执行
                withContext(Dispatchers.Main) {
                    // 任务成功完成后的 UI 更新可以在这里处理
                }
            } catch (e: Exception) {
                // 确保错误回调在主线程执行
                withContext(Dispatchers.Main) {
                    // 错误处理和 UI 更新可以在这里处理
                }
            }
        }
    }

    /**
        * 获取 TaskExecutor 实例（兼容旧代码）
     */
    @JvmStatic
    fun getInstance(): TaskExecutor {
        return this
    }

    /**
        * 执行后台任务（兼容旧代码）
        * @param task 要执行的后台任务
     */
    @JvmStatic
    fun execute(task: BackgroundTask) {
        executeTask(task)
    }
}
