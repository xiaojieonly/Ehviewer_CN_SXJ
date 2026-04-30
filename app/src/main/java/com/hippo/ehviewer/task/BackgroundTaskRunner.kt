package com.hippo.ehviewer.task

import android.os.Process
import android.util.Log
import kotlinx.coroutines.runBlocking

object BackgroundTaskRunner {

    private const val TAG = "BackgroundTaskRunner"

    /**
     * 浠ラ樆濉炴柟寮忔墽琛屽悗鍙颁换鍔★紝骞跺湪鎵ц鏈熼棿鎻愬崌绾跨▼浼樺厛绾э紝
     * 纭繚鍚堝苟銆佸帇缂╃瓑鎿嶄綔鐨勬墽琛岄€熷害涓嶈绯荤粺闄嶄綆銆?
     */
    @JvmStatic
    fun runBlockingExecute(task: BackgroundTask): Throwable? {
        val thread = Thread.currentThread()
        val originalPriority = thread.priority
        var originalTidPriority = Process.THREAD_PRIORITY_DEFAULT
        var priorityRestored = false
        try {
            // 鎻愬崌鍒版甯镐紭鍏堢骇锛岀‘淇濆悗鍙颁换鍔★紙鍚堝苟銆佸帇缂╃瓑锛夐『鍒╂墽琛?
            originalTidPriority = Process.getThreadPriority(Process.myTid())
            if (originalTidPriority > Process.THREAD_PRIORITY_DEFAULT) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
                priorityRestored = true
                Log.d(TAG, "浠诲姟 ${task.getTaskId()} 绾跨▼浼樺厛绾т粠 $originalTidPriority 鎻愬崌鍒?THREAD_PRIORITY_DEFAULT")
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
