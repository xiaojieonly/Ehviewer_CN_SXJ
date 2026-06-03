package com.hippo.ehviewer.task

import android.os.Process
import android.util.Log
import kotlinx.coroutines.runBlocking

object BackgroundTaskRunner {

    private const val TAG = "BackgroundTaskRunner"

    /**
     * 以阻塞方式执行后台任务，并在执行期间提升线程优先级，
     * 确保合并、压缩等操作的执行速度不被系统降低。
     */
    @JvmStatic
    fun runBlockingExecute(task: BackgroundTask): Throwable? {
        val thread = Thread.currentThread()
        val originalPriority = thread.priority
        var originalTidPriority = Process.THREAD_PRIORITY_DEFAULT
        var priorityRestored = false
        try {
            // 提升到正常优先级，确保后台任务（合并、压缩等）顺利执行
            originalTidPriority = Process.getThreadPriority(Process.myTid())
            if (originalTidPriority > Process.THREAD_PRIORITY_DEFAULT) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
                priorityRestored = true
                Log.d(TAG, "任务 ${task.getTaskId()} 线程优先级从 $originalTidPriority 提升到 THREAD_PRIORITY_DEFAULT")
            }
        } catch (_: Exception) {
        }
        try {
            return runBlocking {
                try {
                    val result = task.execute()
                    result.exceptionOrNull()
                } catch (t: Throwable) {
                    t
                }
            }
        } finally {
            if (priorityRestored) {
                try {
                    Process.setThreadPriority(originalTidPriority)
                } catch (_: Exception) {
                }
            }
            thread.priority = originalPriority
        }
    }
}
