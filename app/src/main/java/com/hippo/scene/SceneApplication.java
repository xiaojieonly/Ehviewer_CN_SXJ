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

package com.hippo.scene;

import android.app.Application;
import android.util.SparseArray;

import com.hippo.yorozuya.IntIdGenerator;

public abstract class SceneApplication extends Application {

    private final IntIdGenerator mIdGenerator = new IntIdGenerator();
    private final SparseArray<StageActivity> mStageMap = new SparseArray<>();

    void registerStageActivity(StageActivity stage) {
        int id = mIdGenerator.nextId();
        mStageMap.put(id, stage);
        stage.onRegister(id);
    }

    void registerStageActivity(StageActivity stage, int id) {
        if (mStageMap.indexOfKey(id) >= 0) {
            throw new IllegalStateException("The id exists: " + id);
        }

        mStageMap.put(id, stage);
        stage.onRegister(id);
    }

    void unregisterStageActivity(int id) {
        int index = mStageMap.indexOfKey(id);
        if (index >= 0) {
            StageActivity stage = mStageMap.valueAt(index);
            mStageMap.remove(id);
            stage.onUnregister();
        }
    }

    public StageActivity findStageActivityById(int id) {
        return mStageMap.get(id);
    }
}
