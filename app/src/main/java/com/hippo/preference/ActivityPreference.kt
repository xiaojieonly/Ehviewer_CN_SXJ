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
package com.hippo.preference

import android.content.Context
import android.content.Intent
import android.preference.Preference
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import com.hippo.ehviewer.R
import com.hippo.preference.ActivityPreference
import com.hippo.util.ExceptionUtils

open class ActivityPreference : Preference {
    private var mActivityClazz: Class<*>? = null

    constructor(context: Context) : super(context) {
        init(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs, defStyleAttr, 0)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.ActivityPreference,
            defStyleAttr,
            defStyleRes
        )
        val clazzName = a.getString(R.styleable.ActivityPreference_activity)
        if (null != clazzName) {
            try {
                mActivityClazz = Class.forName(clazzName)
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Can't find class $clazzName", e)
            }
        }
        a.recycle()
    }

    override fun onClick() {
        if (null == mActivityClazz) {
            return
        }
        val context = context
        val intent = Intent(context, mActivityClazz)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            ExceptionUtils.throwIfFatal(e)
            Toast.makeText(context, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val TAG = ActivityPreference::class.java.simpleName
    }
}