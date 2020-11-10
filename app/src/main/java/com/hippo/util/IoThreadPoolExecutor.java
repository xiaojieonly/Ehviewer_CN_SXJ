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

package com.hippo.util;

import androidx.annotation.NonNull;
import com.hippo.yorozuya.thread.PriorityThreadFactory;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class IoThreadPoolExecutor extends ThreadPoolExecutor {

  private final static ThreadPoolExecutor INSTANCE =
      IoThreadPoolExecutor.newInstance(3, 32, 1L, TimeUnit.SECONDS,
          new PriorityThreadFactory("IO",android.os.Process.THREAD_PRIORITY_BACKGROUND));

  private IoThreadPoolExecutor(
      int corePoolSize,
      int maximumPoolSize,
      long keepAliveTime,
      TimeUnit unit,
      BlockingQueue<Runnable> workQueue,
      ThreadFactory threadFactory,
      RejectedExecutionHandler handler
  ) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
  }

  public static ThreadPoolExecutor getInstance() {
    return INSTANCE;
  }

  private static ThreadPoolExecutor newInstance(
      int corePoolSize,
      int maximumPoolSize,
      long keepAliveTime,
      TimeUnit unit,
      ThreadFactory threadFactory
  ) {
    ThreadQueue queue = new ThreadQueue();
    PutRunnableBackHandler handler = new PutRunnableBackHandler();
    IoThreadPoolExecutor executor = new IoThreadPoolExecutor(
        corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, threadFactory, handler);
    queue.setThreadPoolExecutor(executor);
    return executor;
  }

  private static class ThreadQueue extends LinkedBlockingQueue<Runnable> {

    private ThreadPoolExecutor executor;

    void setThreadPoolExecutor(ThreadPoolExecutor executor) {
      this.executor = executor;
    }

    @Override
    public boolean offer(@NonNull Runnable o) {
      int allWorkingThreads = executor.getActiveCount() + super.size();
      return allWorkingThreads < executor.getPoolSize() && super.offer(o);
    }
  }

  public static class PutRunnableBackHandler implements RejectedExecutionHandler {
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      try {
        executor.getQueue().put(r);
      } catch (InterruptedException e) {
        throw new RejectedExecutionException(e);
      }
    }
  }
}
