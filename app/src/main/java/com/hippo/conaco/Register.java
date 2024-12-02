/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.conaco;

import com.hippo.yorozuya.collect.Multimap;
import com.hippo.yorozuya.collect.SparseILArray;

import java.util.List;

class Register<V> {

    private final SparseILArray<ConacoTask<V>> mIdMap = new SparseILArray<>();
    private final Multimap<String, ConacoTask<V>> mKeyMap = Multimap.create();

    /**
     * @return true for the key is already registered
     */
    public synchronized boolean register(int id, ConacoTask<V> task) {
        boolean repeatedKey = false;
        String taskKey = task.getKey();

        // For no-key task, there no need to check
        if (taskKey != null) {
            repeatedKey = mKeyMap.countElements(taskKey) != 0;
        }

        // Append task
        mKeyMap.putElement(taskKey, task);
        mIdMap.append(id, task);

        return repeatedKey;
    }

    public synchronized ConacoTask<V> unregister(int id) {
        ConacoTask<V> task = mIdMap.remove(id);
        if (task != null) {
            mKeyMap.removeElement(task.getKey(), task);
        }
        return task;
    }

    public synchronized boolean contain(int id) {
        return mIdMap.indexOfKey(id) >= 0;
    }

    public synchronized ConacoTask<V> getByKey(String key) {
        if (key == null) {
            return null;
        }

        List<ConacoTask<V>> list = mKeyMap.get(key);
        if (list != null && list.size() > 0) {
            return list.get(0);
        } else {
            return null;
        }
    }
}
