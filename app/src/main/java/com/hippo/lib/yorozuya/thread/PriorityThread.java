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

import android.os.Process;

public class PriorityThread extends Thread {

    private final int mPriority;

    public PriorityThread(Runnable runnable, int priority) {
        super(runnable);
        mPriority = priority;
    }

    public PriorityThread(Runnable runnable, String name, int priority) {
        super(runnable, name);
        mPriority = priority;
    }

    @Override
    public void run() {
        Process.setThreadPriority(mPriority);
        super.run();
    }
}
