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

import android.content.Context

/**
 * 下载完成自动上传 WebUI 的总开关（WP7）。
 *
 * 偏好文件刻意与 [com.hippo.anotherviewer.webui.WebUiSettings] 共用
 * "webui_settings"：设置 UI 由另一代理负责，用相同键直连同一 prefs 读写；
 * 两个文件各自为政会让「开关显示」与「实际行为」脱节。代价是
 * WebUiSettings.clearConfig() 清空整个文件后本开关回落默认开（true）——
 * 但未配置服务器时 DownloadManager 钩子本就被 isConfigured() 短路，该
 * 回退没有可观察副作用，属可接受语义。token 仍归 WebUiSettings 的加密
 * 存储管，本类只碰这一个布尔键。
 */
object AutoUploadPrefs {

    /** 必须与 WebUiSettings.PREFS 同文件，见类注释。 */
    private const val PREFS = "webui_settings"
    private const val KEY_AUTO_UPLOAD = "webui_auto_upload"

    /**
     * 默认开：自动上传是 WP7 的主诉求；LAN 环境静默全速、无需省带宽，
     * 不需要用户手动开启才生效。
     */
    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPLOAD, true)
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPLOAD, enabled).apply()
    }
}
