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
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.util.FileUtils.Companion.getFileName
import java.util.Locale

/**
 * 本地下载压缩包导入。
 */
class DownloadArchiveImporter(private val mHost: Host) {
    interface Host {
        val eHContext: Context?

        fun getString(resId: Int): String?

        fun runOnUiThread(runnable: Runnable?)

        fun updateForLabel()

        fun updateView()

        val downloadManager: DownloadManager?
    }

    fun importLocalArchive(filePickerLauncher: ActivityResultLauncher<Intent?>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.setType("*/*")
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES, arrayOf<String>(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-rar-compressed",
                "application/vnd.rar",
                "application/x-rar",
                "application/rar",
                "application/x-cbz",
                "application/x-cbr"
            )
        )
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // CRITICAL: Add flags to enable persistent URI permissions
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        try {
            filePickerLauncher.launch(
                Intent.createChooser(
                    intent,
                    mHost.getString(R.string.import_archive_title)
                )
            )
        } catch (e: Exception) {
            val context: Context? = mHost.eHContext
            if (context != null) {
                Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSelectedFile(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return
        }

        val uri = result.data!!.data ?: return

        val context: Context = mHost.eHContext ?: return

        // CRITICAL: Request persistent URI permission IMMEDIATELY when file is selected
        // This is the key to solving the permission loss issue after app restart
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d(TAG, "Successfully obtained persistent URI permission for: $uri")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to obtain persistent URI permission for: $uri", e)
            Toast.makeText(context, R.string.archive_permission_lost, Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error when obtaining URI permission for: $uri", e)
            Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // Show processing dialog
        Toast.makeText(context, R.string.import_archive_processing, Toast.LENGTH_LONG).show()

        // Process the archive file in background
        Thread(Runnable { processArchiveFile(uri) }).start()
    }

    private fun processArchiveFile(uri: Uri) {
        val context: Context = mHost.eHContext ?: return

        try {
            // Verify URI accessibility (permission should already be granted)
            try {
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    if (inputStream == null) {
                        mHost.runOnUiThread {
                            Toast.makeText(
                                context,
                                R.string.import_archive_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot access file even with persistent permission", e)
                mHost.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.import_archive_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            // Get file name
            var fileName = getFileName(context, uri)
            if (fileName == null) {
                fileName = "imported_archive_" + System.currentTimeMillis()
            }

            // Validate file format
            if (!isValidArchiveFormat(fileName)) {
                mHost.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.import_archive_invalid_format,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            // Create DownloadInfo for the archive
            val downloadInfo = createArchiveDownloadInfo(uri, fileName)
            if (downloadInfo == null) {
                mHost.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.import_archive_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            val downloadManager =
                mHost.downloadManager
            // Check if already imported
            if (downloadManager != null && downloadManager.containDownloadInfo(downloadInfo.gid)) {
                mHost.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.import_archive_already_imported,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            // Add to download manager
            if (downloadManager != null) {
                val downloadList: MutableList<DownloadInfo?> = ArrayList<DownloadInfo?>()
                downloadList.add(downloadInfo)
                downloadManager.addDownload(downloadList)
                mHost.runOnUiThread {
                    Toast.makeText(context, R.string.import_archive_success, Toast.LENGTH_SHORT)
                        .show()
                    mHost.updateForLabel()
                    mHost.updateView()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process archive file", e)
            mHost.runOnUiThread {
                Toast.makeText(
                    context,
                    R.string.import_archive_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isValidArchiveFormat(fileName: String?): Boolean {
        if (fileName == null) return false
        val lowerName = fileName.lowercase(Locale.getDefault())
        return lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                lowerName.endsWith(".cbz") || lowerName.endsWith(".cbr")
    }

    private fun createArchiveDownloadInfo(uri: Uri, fileName: String): DownloadInfo? {
        try {
            val downloadInfo = DownloadInfo()
            downloadInfo.gid = System.currentTimeMillis() // Use timestamp as unique ID
            downloadInfo.token = ""
            downloadInfo.title = fileName.replace("\\.[^.]*$".toRegex(), "") // Remove extension
            downloadInfo.titleJpn = null
            downloadInfo.thumb = null // No thumbnail for imported archives
            downloadInfo.category =
                EhUtils.UNKNOWN // Keep as UNKNOWN, will be handled in display logic
            downloadInfo.posted = null
            downloadInfo.uploader = "Local Archive"
            downloadInfo.rating = -1.0f // Keep default rating to not affect other downloads
            downloadInfo.state = DownloadInfo.STATE_FINISH
            downloadInfo.legacy = 0
            downloadInfo.time = System.currentTimeMillis()
            downloadInfo.label = null
            downloadInfo.total = 0 // Will be set by archive provider
            downloadInfo.finished = 0

            // Store the URI in the archiveUri field - this is the key identifier
            downloadInfo.archiveUri = uri.toString()

            return downloadInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DownloadInfo", e)
            return null
        }
    }

    companion object {
        private val TAG: String = DownloadArchiveImporter::class.java.getSimpleName()
    }
}
