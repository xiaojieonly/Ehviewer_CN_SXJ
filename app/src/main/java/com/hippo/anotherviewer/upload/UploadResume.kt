/*
 * Copyright 2026 Hippo Seven
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
package com.hippo.anotherviewer.upload

/**
 * 下载推送断点续传的纯逻辑：不依赖 Android 框架，可在 JVM 单测中直接验证。
 */
object UploadResume {

    /**
     * 剔除服务器已有页（1-based）后仍需上传的页号，升序。
     *
     * @param total 该本总页数（<= 0 返回空表）
     * @param existingPages 服务器 InitResponse.existingPages（可为 null/乱序/重复/越界）
     */
    @JvmStatic
    fun missingPages(total: Int, existingPages: List<Int>?): List<Int> {
        if (total <= 0) return emptyList()
        val have = (existingPages ?: emptyList())
            .asSequence()
            .filter { it in 1..total }
            .toHashSet()
        return (1..total).filterNot { have.contains(it) }
    }
}
