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
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.dao.DownloadInfo
import com.hippo.anotherviewer.download.DownloadManager

/**
 * 周期对账（WP7）：内存态 FINISHED 集合 ∖ 本设备鲜活 pushed 集合 → 精推补漏。
 *
 * 幂等性由三层保证，重复调用最多产生一次空转：
 * 1. 入队去重（DownloadUploadService 的 mPendingGids 去重镜像）；
 * 2. 服务器已有该 gid 且本设备未推送过 → pushOne 冲突分支直接跳过；
 * 3. 本设备推送过 → init 的 existingPages 差分（[UploadResume.missingPages]）
 *    只补缺失页，绝不重传已有页。
 *
 * 判定「本设备推送过」用 [DownloadUploadService.freshPushedGids]（TTL 内鲜活），
 * 与断点续传 force 授权同一保守语义：无法证明本设备推送过就重推一次 init
 * 探测，由服务器冲突分支兜底，绝不会覆盖别人的下载行。
 */
object UploadReconciler {

    /**
     * 纯函数：finished 中未被 pushedFresh 覆盖的 gid。去重且保持 finished
     * 的出现顺序，便于测试与可预期的推送顺序。
     */
    @JvmStatic
    fun missingGids(finished: Collection<Long>, pushedFresh: Collection<Long>): List<Long> {
        val pushed = pushedFresh.toHashSet()
        return finished.distinct().filterNot { pushed.contains(it) }
    }

    /**
     * 生产入口：取应用级 DownloadManager 后委托可注入重载。Robolectric
     * `@Config(manifest = Config.NONE)` 沙箱下 application 不是 SiteApplication，
     * cast 会 CCE——测试直接调用注入重载并 `new DownloadManager(context)`。
     */
    @JvmStatic
    fun maybeEnqueueMissing(context: Context) {
        maybeEnqueueMissing(context, SiteApplication.getDownloadManager(context))
    }

    /**
     * 可注入重载：遍历内存 FINISHED 行，剔除本设备 TTL 内推送过的 gid，
     * 剩余非空才经 [DownloadUploadService.pushGids] 发 PUSH_GIDS Intent；
     * 空集（无下载/全部已推送）不发，避免无谓拉起前台服务。
     *
     * 不检查 [AutoUploadPrefs]：开关门控归调用方（WebUiAutoSyncScheduler），
     * 本类只回答「哪些缺」这一个问题，保持单一职责（协调者已确认分工）。
     * 未配置服务器由 pushGids 内部的 isConfigured 静默短路兜底。
     */
    @JvmStatic
    fun maybeEnqueueMissing(context: Context, dm: DownloadManager) {
        val finished = ArrayList<Long>()
        for (info in dm.getAllDownloadInfoList()) {
            if (info.state == DownloadInfo.STATE_FINISH) {
                finished.add(info.gid)
            }
        }
        if (finished.isEmpty()) {
            return
        }
        val missing = missingGids(finished, DownloadUploadService.freshPushedGids(context))
        if (missing.isNotEmpty()) {
            DownloadUploadService.pushGids(context, missing)
        }
    }
}
