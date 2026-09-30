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
package com.hippo.ehviewer.ui.scene.download.part

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.lib.yorozuya.SimpleHandler
import com.hippo.lib.yorozuya.thread.PriorityThreadFactory
import com.hippo.lib.yorozuya.thread.SerialThreadExecutor
import com.hippo.util.FileUtils.Companion.getFileName
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Locale

/** 本地归档导入器，绑定页面接口；后台任务只保留页面弱引用。 */
class DownloadArchiveImporter(private val mHost: Host) {
    interface Host {
        val eHContext: Context?
        val downloadManager: DownloadManager?
        /** 返回选择文件时的目标标签。 */
        fun getArchiveImportLabel(): String?
        /** 判断导入结束后页面是否仍可展示结果。 */
        fun canShowArchiveImportResult(manager: DownloadManager): Boolean
        fun getString(resId: Int): String?
        fun runOnUiThread(runnable: Runnable?)
        fun updateForLabel()
        fun updateView()
    }

    /** 打开多选归档选择器并请求持久读权限。 */
    fun importLocalArchive(filePickerLauncher: ActivityResultLauncher<Intent?>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "application/zip", "application/x-zip-compressed", "application/x-rar-compressed",
            "application/vnd.rar", "application/x-rar", "application/rar",
            "application/x-cbz", "application/x-cbr"
        ))
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, mHost.getString(R.string.import_archive_title)))
        } catch (e: Exception) {
            mHost.eHContext?.let { Toast.makeText(it, R.string.import_archive_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    /** 取得持久权限后捕获标签，在串行后台任务中校验所选归档。 */
    fun handleSelectedFiles(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) return
        val data = result.data ?: return
        val selected = collectSelectedUris(data)
        if (selected.isEmpty()) return
        val context = mHost.eHContext ?: return
        val manager = mHost.downloadManager ?: return
        if (selected.size > MAX_ARCHIVE_IMPORT_COUNT) {
            Toast.makeText(context, context.getString(R.string.import_archive_too_many,
                selected.size, MAX_ARCHIVE_IMPORT_COUNT), Toast.LENGTH_LONG).show()
            return
        }
        val flags = (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .takeIf { it != 0 } ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
        val permitted = ArrayList<Uri>()
        var failures = 0
        for (uri in selected) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                permitted.add(uri)
            } catch (e: Exception) {
                failures++
                Log.e(TAG, "Failed to obtain URI permission: $uri", e)
            }
        }
        if (permitted.isEmpty()) {
            Toast.makeText(context, R.string.archive_permission_lost, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(context, R.string.import_archive_processing, Toast.LENGTH_LONG).show()
        val appContext = context.applicationContext
        val label = mHost.getArchiveImportLabel()
        val host = WeakReference(mHost)
        val selectedCount = selected.size
        val permissionFailures = failures
        try {
            ARCHIVE_IMPORT_EXECUTOR.execute {
                processArchiveFiles(appContext, manager, permitted, label, selectedCount, permissionFailures, host)
            }
        } catch (e: RuntimeException) {
            permitted.forEach { manager.releaseArchiveUriPermissionIfUnused(it.toString()) }
            Log.e(TAG, "Failed to schedule archive import", e)
            Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** 保存校验通过、尚未入库的归档信息。 */
    private data class PendingArchiveImport(val uri: Uri, val fileName: String)

    companion object {
        private const val TAG = "DownloadArchiveImporter"
        private const val MAX_ARCHIVE_IMPORT_COUNT = 50
        private val ARCHIVE_IMPORT_EXECUTOR = SerialThreadExecutor(3000L, LinkedList<Runnable>(),
            PriorityThreadFactory("ArchiveImport", Process.THREAD_PRIORITY_BACKGROUND))
        private var lastLocalGalleryId = 0L

        /** 合并单选和多选结果，保持选择顺序并去重。 */
        private fun collectSelectedUris(data: Intent): List<Uri> {
            val uris = linkedSetOf<Uri>()
            data.data?.let { uris.add(it) }
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
            }
            return uris.toList()
        }

        /** 在后台校验访问权限和格式，不修改共享下载列表。 */
        private fun processArchiveFiles(context: Context, manager: DownloadManager, uris: List<Uri>,
            label: String?, selectedCount: Int, permissionFailures: Int, host: WeakReference<Host>) {
            val pending = ArrayList<PendingArchiveImport>()
            var invalid = 0
            var failed = permissionFailures
            for (uri in uris) {
                try {
                    context.contentResolver.openInputStream(uri).use { stream ->
                        checkNotNull(stream) { "Cannot open archive" }
                    }
                    val name = getFileName(context, uri)
                    if (!isValidArchiveFormat(name)) { invalid++; continue }
                    pending.add(PendingArchiveImport(uri, name!!))
                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "Failed to inspect archive: $uri", e)
                }
            }
            postArchiveImportResult(context, manager, host, selectedCount, uris, pending, label, invalid, failed)
        }

        /** 在主线程提交记录并回收无引用的权限，仅通知仍可见的页面。 */
        private fun postArchiveImportResult(context: Context, manager: DownloadManager, host: WeakReference<Host>,
            selectedCount: Int, permissionUris: List<Uri>, pending: List<PendingArchiveImport>,
            label: String?, invalid: Int, failed: Int) {
            SimpleHandler.getInstance().post {
                var duplicates = 0
                var failures = failed
                var records: List<DownloadInfo> = emptyList()
                // 标签可能在后台校验期间被删除或改名，不能静默重建。
                if (label != null && !manager.containLabel(label)) {
                    failures += pending.size
                } else {
                    try {
                        val known = manager.allDownloadInfoList.mapNotNull { it.archiveUri }.toMutableSet()
                        val prepared = ArrayList<DownloadInfo>()
                        for (item in pending) {
                            // 同一 URI 全局唯一，跨标签也不重复导入。
                            if (!known.add(item.uri.toString())) { duplicates++; continue }
                            try {
                                prepared.add(createArchiveDownloadInfo(manager, item.uri, item.fileName, label))
                            } catch (e: RuntimeException) {
                                failures++
                                Log.e(TAG, "Failed to prepare archive: ${item.uri}", e)
                            }
                        }
                        if (prepared.isNotEmpty()) {
                            records = manager.addDownload(prepared)
                            failures += prepared.size - records.size
                        }
                    } catch (e: RuntimeException) {
                        failures = failed + pending.size - duplicates
                        Log.e(TAG, "Failed to commit imported archives", e)
                    }
                }
                // 失败项释放权限，已有和成功记录引用的权限由管理器保留。
                permissionUris.forEach { manager.releaseArchiveUriPermissionIfUnused(it.toString()) }
                val scene = host.get() ?: return@post
                if (!scene.canShowArchiveImportResult(manager)) return@post
                showArchiveImportResult(context, selectedCount, records.size, invalid, duplicates, failures)
                scene.updateForLabel()
                scene.updateView()
            }
        }

        /** 使用固定语言规则识别归档扩展名，兼容土耳其语等系统语言。 */
        @JvmStatic
        private fun isValidArchiveFormat(fileName: String?): Boolean {
            val name = fileName?.lowercase(Locale.ROOT) ?: return false
            return name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".cbz") || name.endsWith(".cbr")
        }

        /** 根据导入、重复、格式错误和失败数量给出结果。 */
        private fun showArchiveImportResult(context: Context, selected: Int, imported: Int,
            invalid: Int, duplicates: Int, failed: Int) {
            if (imported == selected) {
                Toast.makeText(context, R.string.import_archive_success, Toast.LENGTH_SHORT).show()
            } else if (imported == 0) {
                val message = when (selected) {
                    invalid -> R.string.import_archive_invalid_format
                    duplicates -> R.string.import_archive_already_imported
                    else -> R.string.import_archive_failed
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } else {
                val message = context.getString(R.string.import_archive_success) + ": $imported/$selected\n" +
                    context.getString(R.string.import_archive_failed) + ": ${invalid + duplicates + failed}/$selected"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        /** 创建保留目标标签和归档 URI 的下载实体。 */
        private fun createArchiveDownloadInfo(manager: DownloadManager, uri: Uri, fileName: String, label: String?): DownloadInfo {
            return DownloadInfo().apply {
                gid = nextLocalGalleryId(manager)
                token = ""
                title = fileName.substringBeforeLast('.', fileName)
                category = EhUtils.UNKNOWN
                uploader = "Local Archive"
                rating = -1.0f
                state = DownloadInfo.STATE_FINISH
                time = System.currentTimeMillis()
                this.label = label
                archiveUri = uri.toString()
            }
        }

        /** 生成与已有下载记录不冲突的单调本地编号。 */
        @Synchronized
        private fun nextLocalGalleryId(manager: DownloadManager): Long {
            var id = maxOf(System.currentTimeMillis(), lastLocalGalleryId + 1)
            while (manager.containDownloadInfo(id)) id++
            lastLocalGalleryId = id
            return id
        }
    }
}
