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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SerialThreadExecutor implements Executor, Runnable {

    private final Lock mThreadLock = new ReentrantLock();
    private final Object mWaitLock = new Object();
    private final long mKeepAliveMillis;
    private final Queue<Runnable> mWorkQueue;
    private final ThreadFactory mThreadFactory;
    // The worker
    private Thread mThread;

    public SerialThreadExecutor(long keepAliveMillis,
                                Queue<Runnable> workQueue, ThreadFactory threadFactory) {
        mKeepAliveMillis = keepAliveMillis;
        mWorkQueue = workQueue;
        mThreadFactory = threadFactory;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        mThreadLock.lock();

        mWorkQueue.add(command);

        // Ensure thread
        if (mThread == null) {
            mThread = mThreadFactory.newThread(this);
            mThread.start();
        } else {
            synchronized (mWaitLock) {
                mWaitLock.notify();
            }
        }

        mThreadLock.unlock();
    }

    @Override
    public void run() {
        // It tell whether has waited
        boolean hasWaited = false;

        for (; ; ) {
            mThreadLock.lock();
            Runnable runnable = mWorkQueue.poll();
            if (runnable == null) {
                if (hasWaited) {
                    // Have waited enough time
                    mThread = null;
                    mThreadLock.unlock();
                    break;
                } else {
                    mThreadLock.unlock();
                    hasWaited = true;
                    synchronized (mWaitLock) {
                        try {
                            mWaitLock.wait(mKeepAliveMillis);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                    continue;
                }
            } else {
                mThreadLock.unlock();
            }

            hasWaited = false;
            try {
                runnable.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
