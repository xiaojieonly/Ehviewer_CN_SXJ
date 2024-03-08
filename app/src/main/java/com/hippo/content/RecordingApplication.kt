/*
 * Copyright 2018 Hippo Seven
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
package com.hippo.content

import android.app.Activity
import android.os.Bundle
import com.hippo.scene.SceneApplication
import java.lang.ref.WeakReference
import java.util.*

/*
 * Created by Hippo on 2018/3/27.
 */ abstract class RecordingApplication : SceneApplication() {
    private val list: MutableList<WeakReference<Activity>> = LinkedList()
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                list.add(WeakReference(activity))
            }

            override fun onActivityDestroyed(activity: Activity) {
                val iterator = list.iterator()
                while (iterator.hasNext()) {
                    val reference = iterator.next()
                    val a = reference.get()
                    // Remove current activity and null
                    if (a == null || a === activity) {
                        iterator.remove()
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    fun recreate() {
        // Copy list
        val listCopy = ArrayList<Activity>(list.size)
        for (reference in list) {
            val activity = reference.get() ?: continue
            listCopy.add(activity)
        }

        // Finish all activities
        for (i in listCopy.indices.reversed()) {
            listCopy[i].recreate()
        }
    }
}