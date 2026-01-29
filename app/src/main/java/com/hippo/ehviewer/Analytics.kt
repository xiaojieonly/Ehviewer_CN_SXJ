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
package com.hippo.ehviewer

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.scene.SceneFragment
import java.util.Locale

/**
 * google监控
 */
object Analytics {
    private const val LOG_TAG = "Analytics"
    private const val DEVICE_LANGUAGE = "device_language"

    private var analytics: FirebaseAnalytics? = null

    @JvmStatic
    fun start(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context)
        try {
            analytics!!.setUserId(Settings.getUserID())
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Firebase error: $e")
        }


        val locale = Locale.getDefault()
        var language = locale.language
        if (TextUtils.isEmpty(language)) {
            language = "none"
        }
        val country = locale.country
        if (!TextUtils.isEmpty(country)) {
            language = language + "-" + country
        }
        language = language.lowercase(Locale.getDefault())
        analytics!!.setUserProperty(DEVICE_LANGUAGE, language)
    }

    @JvmStatic
    val isEnabled: Boolean
        get() = analytics != null && Settings.getEnableAnalytics()

    @JvmStatic
    fun onSceneView(scene: SceneFragment) {
        if (isEnabled) {
            val bundle = Bundle()
            bundle.putString("scene_simple_class", scene.javaClass.getSimpleName())
            bundle.putString("scene_class", scene.javaClass.getName())
            analytics!!.logEvent("scene_view", bundle)
        }
    }

    @JvmStatic
    fun recordException(e: Throwable) {
        Log.e(LOG_TAG, "Unexpected error raised", e)

        if (isEnabled) {
            try {
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (ex: Exception) {
                // firebase not init or others?
                // just throw original error
                Log.e(LOG_TAG, "Firebase error: " + ex)
            }
        }
    }
}
