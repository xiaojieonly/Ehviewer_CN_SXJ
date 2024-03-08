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
package com.hippo.scene

import android.app.Application
import android.util.SparseArray
import com.hippo.yorozuya.IntIdGenerator

abstract class SceneApplication : Application() {
    private val mIdGenerator = IntIdGenerator()
    private val mStageMap = SparseArray<StageActivity>()
    fun registerStageActivity(stage: StageActivity) {
        val id = mIdGenerator.nextId()
        mStageMap.put(id, stage)
        stage.onRegister(id)
    }

    fun registerStageActivity(stage: StageActivity, id: Int) {
        check(mStageMap.indexOfKey(id) < 0) { "The id exists: $id" }
        mStageMap.put(id, stage)
        stage.onRegister(id)
    }

    fun unregisterStageActivity(id: Int) {
        val index = mStageMap.indexOfKey(id)
        if (index >= 0) {
            val stage = mStageMap.valueAt(index)
            mStageMap.remove(id)
            stage.onUnregister()
        }
    }

    fun findStageActivityById(id: Int): StageActivity {
        return mStageMap[id]
    }
}