/*
 * Copyright (C) 2014-2015 Hippo Seven
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
package com.hippo.network

class UrlBuilder(var mRootUrl: String) {
    var mQueryMap: MutableMap<String, Any> = HashMap()
    fun addQuery(key: String, value: Any) {
        mQueryMap[key] = value
    }

    fun build(): String {
        return if (mQueryMap.isEmpty()) {
            mRootUrl
        } else {
            val sb = StringBuilder(mRootUrl)
            sb.append("?")
            val iter: Iterator<String> = mQueryMap.keys.iterator()
            if (iter.hasNext()) {
                val key = iter.next()
                val value = mQueryMap[key]
                sb.append(key).append("=").append(value)
            }
            while (iter.hasNext()) {
                val key = iter.next()
                val value = mQueryMap[key]
                sb.append("&").append(key).append("=").append(value)
            }
            sb.toString()
        }
    }
}