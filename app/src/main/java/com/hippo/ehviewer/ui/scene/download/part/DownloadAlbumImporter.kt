/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/LICENSE-2.0
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
import android.util.SparseArray
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.gallery.GalleryProvider2
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.lib.yorozuya.StringUtils
import com.hippo.lib.yorozuya.Utilities
import com.hippo.unifile.UniFile
import com.hippo.util.FileUtils.Companion.copyFile
import com.hippo.util.NaturalComparator
import java.util.Collections

/**
 * 将本地图片文件夹复制为已完成画廊。
 */
class DownloadAlbumImporter(private val mHost: Host) {
    interface Host {
        val eHContext: Context?

        fun getString(resId: Int): String?

        fun runOnUiThread(runnable: Runnable?)

        fun updateForLabel()

        fun updateView()

        val downloadManager: DownloadManager?

        val label: String?
    }

    fun importLocalAlbum(folderPickerLauncher: ActivityResultLauncher<Intent?>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

        try {
            folderPickerLauncher.launch(intent)
        } catch (e: Exception) {
            val context: Context? = mHost.eHContext
            if (context != null) {
                Toast.makeText(context, R.string.import_album_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSelectedFolder(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return
        }

        val uri = result.data!!.data ?: return

        val context: Context = mHost.eHContext ?: return

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d(TAG, "Successfully obtained persistent URI permission for: $uri")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to obtain persistent URI permission for: $uri", e)
            Toast.makeText(context, R.string.import_album_failed, Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error when obtaining URI permission for: $uri", e)
            Toast.makeText(context, R.string.import_album_failed, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, R.string.import_album_processing, Toast.LENGTH_LONG).show()
        Thread(Runnable { processAlbumFolder(uri) }).start()
    }

    private fun processAlbumFolder(uri: Uri) {
        val context: Context = mHost.eHContext ?: return

        var downloadDir: UniFile? = null
        var gid = -1L
        try {
            val sourceDir = UniFile.fromTreeUri(context, uri)
            if (sourceDir == null || !sourceDir.isDirectory()) {
                showFailed()
                return
            }

            val archiveUri: String = URI_PREFIX + uri.toString()
            val downloadManager =
                mHost.downloadManager
            if (downloadManager != null && isAlreadyImported(downloadManager, archiveUri)) {
                showToast(R.string.import_album_already_imported)
                return
            }

            val importRoot = resolveImportRoot(sourceDir)
            if (importRoot == null) {
                showFailed()
                return
            }

            val images = collectImageFiles(importRoot)
            if (images.isEmpty()) {
                showToast(R.string.import_album_no_images)
                return
            }

            Collections.sort<UniFile?>(
                images,
                Comparator { left: UniFile?, right: UniFile? ->
                    NaturalComparator().compare(
                        left!!.getName(),
                        right!!.getName()
                    )
                })

            if (Settings.getDownloadLocation() == null) {
                showToast(R.string.settings_download_invalid_download_location)
                return
            }

            gid = System.currentTimeMillis()
            val downloadInfo = createAlbumDownloadInfo(gid, sourceDir, archiveUri, images.size)
            val spiderDen = SpiderDen(downloadInfo)
            spiderDen.setMode(SpiderQueen.MODE_DOWNLOAD)
            if (!spiderDen.prepareDownloadStorage()) {
                cleanupFailedImport(gid, null)
                showFailed()
                return
            }

            downloadDir = spiderDen.getDownloadDir()
            if (downloadDir == null) {
                cleanupFailedImport(gid, null)
                showFailed()
                return
            }

            val copiedCount = copyImages(images, downloadDir)
            if (copiedCount == 0) {
                cleanupFailedImport(gid, downloadDir)
                showFailed()
                return
            }

            downloadInfo.total = copiedCount
            downloadInfo.finished = copiedCount
            writeSpiderInfo(context, spiderDen, gid, copiedCount)
            copyThumb(images.get(0)!!, downloadDir)

            if (downloadManager == null) {
                cleanupFailedImport(gid, downloadDir)
                showFailed()
                return
            }

            val downloadList: MutableList<DownloadInfo?> = ArrayList<DownloadInfo?>()
            downloadList.add(downloadInfo)
            downloadManager.addDownload(downloadList)
            mHost.runOnUiThread {
                Toast.makeText(
                    context,
                    context.getString(R.string.import_album_success, copiedCount),
                    Toast.LENGTH_SHORT
                ).show()
                mHost.updateForLabel()
                mHost.updateView()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process album folder", e)
            cleanupFailedImport(gid, downloadDir)
            showFailed()
        }
    }

    private fun createAlbumDownloadInfo(
        gid: Long,
        sourceDir: UniFile,
        archiveUri: String?,
        imageCount: Int
    ): DownloadInfo {
        val downloadInfo = DownloadInfo()
        downloadInfo.gid = gid
        downloadInfo.token = LOCAL_TOKEN
        var folderName = sourceDir.getName()
        if (folderName == null || folderName.trim { it <= ' ' }.isEmpty()) {
            folderName = "imported_album_$gid"
        }
        downloadInfo.title = folderName
        downloadInfo.titleJpn = null
        downloadInfo.thumb = LOCAL_THUMB
        downloadInfo.category = EhUtils.UNKNOWN
        downloadInfo.posted = null
        downloadInfo.uploader = LOCAL_UPLOADER
        downloadInfo.rating = -1.0f
        downloadInfo.state = DownloadInfo.STATE_FINISH
        downloadInfo.legacy = 0
        downloadInfo.time = gid
        downloadInfo.label = mHost.label
        downloadInfo.total = imageCount
        downloadInfo.finished = imageCount
        downloadInfo.archiveUri = archiveUri
        return downloadInfo
    }

    private fun copyImages(images: MutableList<UniFile?>, downloadDir: UniFile): Int {
        var copiedCount = 0
        for (i in images.indices) {
            val picture = images[i]
            if (picture == null || !picture.isFile()) {
                continue
            }
            val extension = getImageExtension(picture.getName())
            val newName = SpiderDen.generateImageFilename(copiedCount, extension)
            var destFile = downloadDir.findFile(newName)
            if (destFile != null && destFile.exists() && !destFile.delete()) {
                continue
            }
            destFile = downloadDir.createFile(newName)
            if (destFile == null) {
                Log.e(TAG, "Failed to create file: $newName")
                continue
            }
            if (!copyFile(picture, destFile, false)) {
                Log.e(TAG, "Failed to copy file: " + picture.getName() + " to " + newName)
                destFile.delete()
                continue
            }
            copiedCount++
        }
        return copiedCount
    }

    private fun writeSpiderInfo(context: Context, spiderDen: SpiderDen, gid: Long, pages: Int) {
        val spiderInfo = SpiderInfo()
        spiderInfo.gid = gid
        spiderInfo.token = LOCAL_TOKEN
        spiderInfo.pages = pages
        spiderInfo.previewPages = 0
        spiderInfo.previewPerPage = 0
        spiderInfo.startPage = 0
        spiderInfo.pTokenMap = SparseArray<String?>(pages)
        spiderInfo.writeNewSpiderInfoToLocal(spiderDen, context.getApplicationContext())
    }

    private fun copyThumb(firstImage: UniFile, downloadDir: UniFile) {
        var thumbFile = downloadDir.findFile(".thumb")
        if (thumbFile != null && thumbFile.exists()) {
            thumbFile.delete()
        }
        thumbFile = downloadDir.createFile(".thumb")
        if (thumbFile == null) {
            return
        }
        if (!copyFile(firstImage, thumbFile, false)) {
            thumbFile.delete()
        }
    }

    private fun resolveImportRoot(dir: UniFile?): UniFile? {
        if (dir == null || !dir.isDirectory()) {
            return null
        }
        val children = dir.listFiles() ?: return null
        var hasImageAtLevel = false
        var onlySubdir: UniFile? = null
        var subdirCount = 0
        for (child in children) {
            if (child.isFile() && isImageFile(child)) {
                hasImageAtLevel = true
            } else if (child.isDirectory()) {
                onlySubdir = child
                subdirCount++
            }
        }
        if (hasImageAtLevel) {
            return dir
        }
        if (subdirCount == 1 && onlySubdir != null) {
            return resolveImportRoot(onlySubdir)
        }
        return dir
    }

    private fun collectImageFiles(root: UniFile): MutableList<UniFile?> {
        val images: MutableList<UniFile?> = ArrayList<UniFile?>()
        collectImageFilesRecursive(root, images)
        return images
    }

    private fun collectImageFilesRecursive(dir: UniFile, images: MutableList<UniFile?>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory()) {
                collectImageFilesRecursive(child, images)
            } else if (isImageFile(child)) {
                images.add(child)
            }
        }
    }

    private fun isImageFile(file: UniFile?): Boolean {
        if (file == null || !file.isFile()) {
            return false
        }
        val name = file.getName() ?: return false
        return StringUtils.endsWith(name.lowercase(), GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS)
    }

    private fun getImageExtension(fileName: String?): String? {
        if (fileName == null) {
            return GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0]
        }
        val dot = fileName.lastIndexOf('.')
        val extension = if (dot >= 0) fileName.substring(dot).lowercase() else ""
        if (Utilities.contain(GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS, extension)) {
            return extension
        }
        return GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0]
    }

    private fun isAlreadyImported(manager: DownloadManager, archiveUri: String): Boolean {
        val all = manager.allDownloadInfoList ?: return false
        for (info in all) {
            if (archiveUri == info.archiveUri) {
                return true
            }
        }
        return false
    }

    private fun cleanupFailedImport(gid: Long, downloadDir: UniFile?) {
        if (downloadDir != null) {
            try {
                downloadDir.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete incomplete album directory", e)
            }
        }
        if (gid > 0) {
            EhDB.removeDownloadDirname(gid)
        }
    }

    private fun showFailed() {
        showToast(R.string.import_album_failed)
    }

    private fun showToast(resId: Int) {
        val context: Context = mHost.eHContext ?: return
        mHost.runOnUiThread { Toast.makeText(context, resId, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private val TAG: String = DownloadAlbumImporter::class.java.getSimpleName()

        const val URI_PREFIX: String = "local-album:"
        private const val LOCAL_TOKEN = "local"
        private const val LOCAL_THUMB = "local"
        private const val LOCAL_UPLOADER = "Local Album"

        fun isLocalAlbum(info: DownloadInfo?): Boolean {
            return info != null && info.archiveUri != null && info.archiveUri.startsWith(URI_PREFIX)
        }
    }
}
