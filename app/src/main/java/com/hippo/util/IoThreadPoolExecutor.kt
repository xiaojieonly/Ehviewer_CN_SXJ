/*
 * Copyright 2019 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.util

import android.os.Process
import com.hippo.lib.yorozuya.thread.PriorityThreadFactory
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class IoThreadPoolExecutor private constructor(
    corePoolSize: Int,
    maximumPoolSize: Int,
    keepAliveTime: Long,
    unit: TimeUnit?,
    workQueue: BlockingQueue<Runnable>?,
    threadFactory: ThreadFactory?,
    handler: RejectedExecutionHandler?
) : ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    keepAliveTime,
    unit,
    workQueue,
    threadFactory,
    handler
) {
    private class ThreadQueue : LinkedBlockingQueue<Runnable>() {
        private var executor: ThreadPoolExecutor? = null

        fun setThreadPoolExecutor(executor: ThreadPoolExecutor) {
            this.executor = executor
        }

        override fun offer(o: Runnable?): Boolean {
            val allWorkingThreads = executor!!.getActiveCount() + super.size
            return allWorkingThreads < executor!!.getPoolSize() && super.offer(o)
        }
    }

    class PutRunnableBackHandler : RejectedExecutionHandler {
        override fun rejectedExecution(r: Runnable?, executor: ThreadPoolExecutor) {
            try {
                executor.queue.put(r)
            } catch (e: InterruptedException) {
                throw RejectedExecutionException(e)
            }
        }
    }

    companion object {
        val instance: ThreadPoolExecutor = newInstance(
            3, 32, 1L, TimeUnit.SECONDS,
            PriorityThreadFactory("IO", Process.THREAD_PRIORITY_BACKGROUND)
        )

        private fun newInstance(
            corePoolSize: Int,
            maximumPoolSize: Int,
            keepAliveTime: Long,
            unit: TimeUnit?,
            threadFactory: ThreadFactory?
        ): ThreadPoolExecutor {
            val queue = ThreadQueue()
            val handler = PutRunnableBackHandler()
            val executor = IoThreadPoolExecutor(
                corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, threadFactory, handler
            )
            queue.setThreadPoolExecutor(executor)
            return executor
        }
    }
}
