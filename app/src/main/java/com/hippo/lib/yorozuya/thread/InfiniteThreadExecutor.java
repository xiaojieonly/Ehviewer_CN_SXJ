/*
 * Copyright 2015-2016 Hippo Seven
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

package com.hippo.lib.yorozuya.thread;

import androidx.annotation.NonNull;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class InfiniteThreadExecutor implements Executor {

    private final long mKeepAliveMillis;
    private final Queue<Runnable> mWorkQueue;
    private final ThreadFactory mThreadFactory;

    private final AtomicInteger mThreadCount = new AtomicInteger();
    private final Object mLock = new Object();
    private int mEmptyThreadCount;

    public InfiniteThreadExecutor(long keepAliveMillis, Queue<Runnable> workQueue, ThreadFactory threadFactory) {
        mKeepAliveMillis = keepAliveMillis;
        mWorkQueue = workQueue;
        mThreadFactory = threadFactory;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        synchronized (mLock) {
            mWorkQueue.add(command);
            if (mEmptyThreadCount > 0) {
                --mEmptyThreadCount;
                mLock.notify();
                return;
            }
        }

        mThreadFactory.newThread(new Task()).start();
    }

    public int getThreadCount() {
        return mThreadCount.get();
    }

    private class Task implements Runnable {

        @Override
        public void run() {
            mThreadCount.incrementAndGet();

            boolean hasWait = false;
            for (; ; ) {
                Runnable command;
                synchronized (mLock) {
                    command = mWorkQueue.poll();
                    if (command == null) {
                        if (hasWait) {
                            --mEmptyThreadCount;
                        }
                        break;
                    }
                }

                try {
                    command.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                synchronized (mLock) {
                    ++mEmptyThreadCount;
                    try {
                        mLock.wait(mKeepAliveMillis);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                hasWait = true;
            }

            mThreadCount.decrementAndGet();
        }
    }
}
