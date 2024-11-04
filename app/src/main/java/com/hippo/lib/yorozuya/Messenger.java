/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.yorozuya;

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class Messenger {

    private static Messenger sInstance;
    private final com.hippo.lib.yorozuya.IntIdGenerator mIdGenerator;
    private final SparseArray<List<Receiver>> mReceiverListMap;

    private Messenger() {
        mIdGenerator = new IntIdGenerator();
        mReceiverListMap = new SparseArray<>();
    }

    public static Messenger getInstance() {
        if (sInstance == null) {
            sInstance = new Messenger();
        }
        return sInstance;
    }

    public int newId() {
        return mIdGenerator.nextId();
    }

    private void notifyInternal(int id, Object obj) {
        List<Receiver> receiverList = mReceiverListMap.get(id);
        if (receiverList != null) {
            List<Receiver> temp = new LinkedList<>(receiverList);
            for (Receiver r : temp) {
                r.onReceive(id, obj);
            }
        }
    }

    public void notify(final int id, final Object obj) {
        // Make sure do it in UI thread
        SimpleHandler.getInstance().post(new Runnable() {
            @Override
            public void run() {
                notifyInternal(id, obj);
            }
        });
    }

    public void notifyAtOnce(int id, Object obj) {
        notifyInternal(id, obj);
    }

    public void register(int id, Receiver receiver) {
        List<Receiver> receiverList = mReceiverListMap.get(id);
        if (receiverList == null) {
            receiverList = new ArrayList<>();
            mReceiverListMap.put(id, receiverList);
        }

        receiverList.add(receiver);
    }

    public void unregister(int id, Receiver receiver) {
        List<Receiver> receiverList = mReceiverListMap.get(id);
        if (receiverList != null) {
            receiverList.remove(receiver);
            if (receiverList.isEmpty()) {
                mReceiverListMap.remove(id);
            }
        }
    }

    public interface Receiver {
        void onReceive(final int id, final Object obj);
    }
}
