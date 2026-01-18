package com.hippo.ehviewer.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                task.execute()
            } catch (e: Exception) {
                // 错误会在任务内部处理
            }
        }
    }
}