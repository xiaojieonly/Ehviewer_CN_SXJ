package com.hippo.ehviewer.task

import kotlinx.coroutines.runBlocking

object BackgroundTaskRunner {
    @JvmStatic
    fun runBlockingExecute(task: BackgroundTask): Throwable? {
        return runBlocking {
            try {
                val result = task.execute()
                result.exceptionOrNull()
            } catch (t: Throwable) {
                t
            }
        }
    }
}
