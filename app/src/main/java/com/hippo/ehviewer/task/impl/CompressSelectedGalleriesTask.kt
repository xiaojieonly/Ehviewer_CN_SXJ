package com.hippo.ehviewer.task.impl

import android.content.Context
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.task.BackgroundTask
import com.hippo.ehviewer.task.impl.BaseBackgroundTask
import com.hippo.unifile.UniFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 压缩选择的下载画廊任务
 */
class CompressSelectedGalleriesTask @JvmOverloads constructor(
    context: Context,
    private val selectedList: List<DownloadInfo>,
    private val taskId: String = "compress_selected_galleries_${System.currentTimeMillis()}"
) : BaseBackgroundTask(context) {

    private val outputFileNames = mutableListOf<String>()
    private val addedEntries = mutableSetOf<String>()

    fun getOutputFileNames(): List<String> = outputFileNames

    override fun getTaskId(): String = taskId

    override fun getTaskName(): String = context.getString(R.string.compress_selected_galleries)

    override fun getTaskDescription(): String? = context.getString(R.string.compress_selected_galleries)

    override fun getTaskType(): BackgroundTask.TaskType = BackgroundTask.TaskType.CLEANUP

    override fun isUniqueTask(): Boolean = false

    override fun isPausable(): Boolean = false

    override fun isPersistable(): Boolean = true

    override fun getTaskPersistData(): String? {
        return JSONObject().apply {
            val array = JSONArray()
            for (info in selectedList) {
                array.put(info.gid)
            }
            put("gids", array)
        }.toString()
    }

    override suspend fun execute(): Result<Unit> {
        appendTaskLog("开始压缩 ${selectedList.size} 个画廊")
        val totalCount = selectedList.size
        if (selectedList.isEmpty()) {
            updateProgress(100, context.getString(R.string.compress_selected_galleries))
            notifyCompleted()
            appendTaskLog("没有要压缩的画廊，任务结束")
            return Result.success(Unit)
        }

        updateProgress(0, totalCount, context.getString(R.string.compress_selected_galleries) + " 0/" + totalCount)

        val outputDir = com.hippo.ehviewer.Settings.getExportLocation()
            ?: return Result.failure(IOException("Export location unavailable"))

        if (!outputDir.exists() && !outputDir.ensureDir()) {
            return Result.failure(IOException("Failed to create export directory"))
        }

        val splitSizeMb = com.hippo.ehviewer.Settings.getCompressSplitSizeMB()
        val splitSizeBytes = if (splitSizeMb > 0) splitSizeMb * 1024L * 1024L else 0L

        var completedCount = 0
        var partIndex = 1

        var currentPartFile: UniFile? = null
        var zos: ZipOutputStream? = null
        var currentPartSize: Long = 0L

        return try {
            for (info in selectedList) {
                val galleryDir = SpiderDen.getGalleryDownloadDir(info)
                if (galleryDir == null || !galleryDir.exists()) {
                    appendTaskLog("画廊 ${info.gid} 不存在，跳过")
                    completedCount++
                    updateProgress(completedCount, totalCount, context.getString(R.string.compress_selected_galleries) + " " + completedCount + "/" + totalCount)
                    continue
                }

                appendTaskLog("压缩画廊 ${info.gid} - ${info.title}")
                val gallerySize = calculateUniFileSize(galleryDir)

                if (splitSizeBytes > 0 && currentPartFile != null && currentPartSize > 0 && currentPartSize + gallerySize > splitSizeBytes) {
                    zos?.close()
                    currentPartFile = null
                    zos = null
                    currentPartSize = 0L
                    partIndex++
                }

                if (zos == null) {
                    addedEntries.clear()
                    val baseName = "ehviewer_${System.currentTimeMillis()}"
                    val partFile = createPartZipFile(outputDir, baseName, partIndex)
                        ?: return Result.failure(IOException("Failed to create zip file"))
                    currentPartFile = partFile
                    outputFileNames.add(partFile.name ?: partFile.toString())
                    zos = ZipOutputStream(partFile.openOutputStream())
                }

                val folderName = sanitizeFileName(info.title ?: info.gid.toString())
                addUniFileToZip(galleryDir, folderName, zos)

                currentPartSize += if (gallerySize > 0) gallerySize else 1
                completedCount++
                updateProgress(completedCount, totalCount, context.getString(R.string.compress_selected_galleries) + " " + completedCount + "/" + totalCount)
            }

            zos?.close()
            notifyCompleted()
            appendTaskLog("压缩完成，生成 ${outputFileNames.size} 个压缩包: ${outputFileNames.joinToString(", ")}")

            Result.success(Unit)
        } catch (e: Exception) {
            try {
                zos?.close()
            } catch (_: Exception) {
            }
            appendTaskLog("压缩任务出错: ${e.message}")
            notifyError(e)
            Result.failure(e)
        }
    }

    private fun createPartZipFile(outputDir: UniFile, baseName: String, partIndex: Int): UniFile? {
        var fileName = if (partIndex <= 1) "$baseName.zip" else "$baseName-part$partIndex.zip"
        var zipFile = outputDir.createFile(fileName)
        var suffix = 1
        while (zipFile == null && suffix <= 100) {
            fileName = if (partIndex <= 1) "$baseName-$suffix.zip" else "$baseName-part$partIndex-$suffix.zip"
            zipFile = outputDir.createFile(fileName)
            suffix++
        }
        return zipFile
    }

    private fun calculateUniFileSize(uniFile: UniFile?): Long {
        if (uniFile == null || !uniFile.exists()) return 0L
        if (uniFile.isFile) {
            return uniFile.length()
        }
        if (uniFile.isDirectory) {
            var total = 0L
            val children = uniFile.listFiles() ?: return 0L
            for (child in children) {
                total += calculateUniFileSize(child)
            }
            return total
        }
        return 0L
    }

    private fun addUniFileToZip(uniFile: UniFile, basePath: String, zos: ZipOutputStream) {
        if (uniFile.isDirectory) {
            val dirPath = if (basePath.endsWith("/")) basePath else "$basePath/"
            if (addZipEntry(zos, dirPath)) {
                zos.closeEntry()
            }
            val children = uniFile.listFiles() ?: return
            for (child in children) {
                val childPath = if (dirPath.isEmpty()) child.name ?: "" else dirPath + (child.name ?: "")
                addUniFileToZip(child, childPath, zos)
            }
        } else if (uniFile.isFile) {
            val entryName = basePath
            if (!addZipEntry(zos, entryName)) {
                appendTaskLog("忽略重复条目: $entryName")
                return
            }
            uniFile.openInputStream()?.use { input ->
                BufferedInputStream(input).use { bis ->
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (bis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
            }
            zos.closeEntry()
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String): Boolean {
        if (!addedEntries.add(entryName)) {
            return false
        }
        return try {
            zos.putNextEntry(ZipEntry(entryName))
            true
        } catch (e: java.util.zip.ZipException) {
            false
        }
    }

    private fun sanitizeFileName(input: String?): String {
        if (input == null) return ""
        return input.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    companion object {
        @JvmStatic
        fun restore(context: Context, taskId: String, persistData: String?): CompressSelectedGalleriesTask? {
            if (persistData.isNullOrEmpty()) {
                return null
            }
            return try {
                val json = JSONObject(persistData)
                val gids = json.optJSONArray("gids") ?: return null
                val selected = mutableListOf<DownloadInfo>()
                for (i in 0 until gids.length()) {
                    val gid = gids.optLong(i, -1)
                    if (gid > 0) {
                        selected.add(DownloadInfo(gid))
                    }
                }
                if (selected.isEmpty()) {
                    null
                } else {
                    CompressSelectedGalleriesTask(context, selected, taskId)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
