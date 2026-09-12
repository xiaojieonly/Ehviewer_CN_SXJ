package com.hippo.ehviewer.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.gallery.GalleryProvider2
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.util.GZIPUtils
import com.hippo.lib.yorozuya.FileUtils as YorozuyaFileUtils
import com.hippo.lib.yorozuya.StringUtils
import com.hippo.lib.yorozuya.Utilities
import com.hippo.unifile.UniFile
import com.hippo.util.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale

/**
 * 归档 zip 下载完成后的解压与导入处理。
 */
class ArchiverDownloadCompleter private constructor(appContext: Context) {

    private val appContext = appContext.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlingIds = HashSet<Long>()

    fun importDownloadedZip(zipFile: File?, galleryInfo: GalleryInfo, taskId: Long) {
        if (zipFile == null || !zipFile.isFile || !zipFile.exists()) {
            handleFailedTask(galleryInfo, taskId)
            return
        }
        if (!tryBeginHandling(taskId)) {
            return
        }
        val tempDir = AppConfig.getExternalTempDir()
        if (tempDir == null) {
            endHandling(taskId)
            handleFailedTask(galleryInfo, taskId)
            return
        }
        val fileName = createFileName(galleryInfo.title, galleryInfo.gid)
        val tempFilePath = tempDir.path + "/" + fileName

        Thread {
            try {
                if (!GZIPUtils.UnZipFolder(zipFile.path, tempFilePath)) {
                    postImportFailed(galleryInfo, taskId)
                    return@Thread
                }
                importGallery(tempFilePath, galleryInfo, taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Error importing downloaded zip", e)
                postImportFailed(galleryInfo, taskId)
            }
        }.start()
    }

    private fun tryBeginHandling(taskId: Long): Boolean {
        synchronized(handlingIds) {
            if (handlingIds.contains(taskId)) {
                return false
            }
            handlingIds.add(taskId)
            return true
        }
    }

    private fun endHandling(taskId: Long) {
        synchronized(handlingIds) {
            handlingIds.remove(taskId)
        }
    }

    private fun handleFailedTask(galleryInfo: GalleryInfo?, taskId: Long) {
        if (galleryInfo != null) {
            Settings.deleteArchiverDownloadId(galleryInfo.gid)
        }
        Settings.deleteArchiverDownload(taskId)
        Settings.deleteArchiverDownloadUrl(taskId)
        Settings.deleteArchiverDownloadPaused(taskId)
        Settings.deleteArchiverDownloadTotal(taskId)
        mainHandler.post {
            Toast.makeText(appContext, R.string.download_state_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun postImportFailed(galleryInfo: GalleryInfo?, taskId: Long) {
        endHandling(taskId)
        handleFailedTask(galleryInfo, taskId)
    }

    private fun importGallery(tempFilePath: String, galleryInfo: GalleryInfo, taskId: Long) {
        if (tempFilePath.isEmpty()) {
            postImportFailed(galleryInfo, taskId)
            return
        }
        val tempFile = File(tempFilePath)
        val importRoot = resolveImportRoot(tempFile)
        if (importRoot == null) {
            postImportFailed(galleryInfo, taskId)
            return
        }
        val tempPictures = collectImageFiles(importRoot)
        if (tempPictures.isEmpty()) {
            Log.e(TAG, "No image files found under: $tempFilePath")
            postImportFailed(galleryInfo, taskId)
            return
        }
        Collections.sort(tempPictures) { file1, file2 -> file1.name.compareTo(file2.name) }

        val spiderDen = SpiderDen(galleryInfo)
        spiderDen.setMode(SpiderQueen.MODE_DOWNLOAD)
        spiderDen.prepareDownloadStorage()
        val downloadDir = spiderDen.getDownloadDir()
        if (downloadDir == null) {
            postImportFailed(galleryInfo, taskId)
            return
        }
        var copiedCount = 0
        try {
            for (i in tempPictures.indices) {
                val picture = tempPictures[i]
                if (!picture.isFile || !picture.exists()) {
                    Log.w(TAG, "Skip missing file: ${picture.path}")
                    continue
                }
                val extension = getImageExtension(picture.name)
                val newName = SpiderDen.generateImageFilename(i, extension)

                var destFile = downloadDir.findFile(newName)
                if (destFile != null && destFile.exists() && !destFile.delete()) {
                    continue
                }
                destFile = downloadDir.createFile(newName)
                if (destFile == null) {
                    Log.e(TAG, "Failed to create file: $newName")
                    continue
                }
                val sourceFile = UniFile.fromFile(picture)
                if (sourceFile == null) {
                    Log.e(TAG, "Cannot open source file: ${picture.path}")
                    destFile.delete()
                    continue
                }
                if (!FileUtils.copyFile(sourceFile, destFile, false)) {
                    Log.e(TAG, "Failed to copy file: ${picture.name} to $newName")
                    destFile.delete()
                    continue
                }
                copiedCount++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in importGallery", e)
            postImportFailed(galleryInfo, taskId)
            return
        }
        if (copiedCount == 0) {
            Log.e(TAG, "No images were copied from: $tempFilePath")
            postImportFailed(galleryInfo, taskId)
            return
        }
        if (!YorozuyaFileUtils.delete(tempFile)) {
            tempFile.deleteOnExit()
        }
        val finalFileName = tempFile.name
        mainHandler.post {
            val labelName = appContext.getString(R.string.download_label_archiver)
            val manager = EhApplication.getDownloadManager(appContext)
            manager.addLabel(labelName)
            manager.addDownload(galleryInfo, labelName, DownloadInfo.STATE_FINISH)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.stat_download_done_line_succeeded, finalFileName),
                Toast.LENGTH_LONG
            ).show()
            Settings.deleteArchiverDownloadId(galleryInfo.gid)
            Settings.deleteArchiverDownload(taskId)
            Settings.deleteArchiverDownloadUrl(taskId)
            Settings.deleteArchiverDownloadPaused(taskId)
            Settings.deleteArchiverDownloadTotal(taskId)
            endHandling(taskId)
        }
    }

    companion object {
        private const val TAG = "ArchiverDownloadCompleter"

        private val MAX_ARCHIVER_BASENAME_UTF8_BYTES =
            255 - ".zip".toByteArray(StandardCharsets.UTF_8).size

        @Volatile
        private var sInstance: ArchiverDownloadCompleter? = null

        @JvmStatic
        fun getInstance(context: Context?): ArchiverDownloadCompleter? {
            if (sInstance == null && context != null) {
                sInstance = ArchiverDownloadCompleter(context.applicationContext)
            }
            return sInstance
        }

        @JvmStatic
        fun createFileName(name: String?, gid: Long): String {
            var result = name?.let { YorozuyaFileUtils.sanitizeFilename(it) } ?: ""
            result = truncateUtf8ToMaxBytes(result, MAX_ARCHIVER_BASENAME_UTF8_BYTES)
            if (result.isEmpty()) {
                result = if (gid > 0) "archiver_$gid" else "archiver"
            }
            return result
        }

        private fun resolveImportRoot(dir: File): File? {
            if (!dir.isDirectory) {
                return null
            }
            val children = dir.listFiles() ?: return null
            var hasImageAtLevel = false
            var onlySubdir: File? = null
            var subdirCount = 0
            for (child in children) {
                if (child.isFile && isImageFile(child)) {
                    hasImageAtLevel = true
                } else if (child.isDirectory) {
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

        private fun collectImageFiles(root: File): MutableList<File> {
            val images = ArrayList<File>()
            collectImageFilesRecursive(root, images)
            return images
        }

        private fun collectImageFilesRecursive(dir: File, images: MutableList<File>) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    collectImageFilesRecursive(child, images)
                } else if (isImageFile(child)) {
                    images.add(child)
                }
            }
        }

        private fun isImageFile(file: File): Boolean {
            if (!file.isFile) {
                return false
            }
            val lower = file.name.lowercase(Locale.ROOT)
            return StringUtils.endsWith(lower, GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS)
        }

        private fun getImageExtension(fileName: String): String {
            val dot = fileName.lastIndexOf('.')
            val extension = if (dot >= 0) fileName.substring(dot).lowercase(Locale.ROOT) else ""
            return if (Utilities.contain(GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS, extension)) {
                extension
            } else {
                GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0]
            }
        }

        private fun truncateUtf8ToMaxBytes(s: String?, maxBytes: Int): String {
            if (s.isNullOrEmpty() || maxBytes <= 0) {
                return s ?: ""
            }
            var byteCount = 0
            var cutCharEnd = 0
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                val charUtf8Bytes: Int
                val charWidth: Int
                when {
                    ch.code <= 0x7F -> {
                        charUtf8Bytes = 1
                        charWidth = 1
                    }
                    ch.code <= 0x7FF -> {
                        charUtf8Bytes = 2
                        charWidth = 1
                    }
                    Character.isHighSurrogate(ch) -> {
                        charUtf8Bytes = 4
                        charWidth = 2
                        if (i + 1 >= s.length) {
                            break
                        }
                    }
                    else -> {
                        charUtf8Bytes = 3
                        charWidth = 1
                    }
                }
                if (byteCount + charUtf8Bytes > maxBytes) {
                    break
                }
                byteCount += charUtf8Bytes
                i += charWidth
                cutCharEnd = i
            }
            return if (cutCharEnd < s.length) s.substring(0, cutCharEnd) else s
        }
    }
}
