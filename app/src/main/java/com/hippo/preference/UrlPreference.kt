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
import android.preference.Preference
import android.util.AttributeSet
import com.hippo.ehviewer.R
import com.hippo.ehviewer.UrlOpener

class UrlPreference : Preference {
    private var mUrl: String? = null

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
            R.styleable.UrlPreference,
            defStyleAttr,
            defStyleRes
        )
        mUrl = a.getString(R.styleable.UrlPreference_url)
        a.recycle()
    }

    override fun getSummary(): CharSequence? {
        return if (null != mUrl) {
            mUrl
        } else {
            super.getSummary()
        }
    }

    override fun onClick() {
        UrlOpener.openUrl(context, mUrl, true)
    }
}