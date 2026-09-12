package com.hippo.ehviewer.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.GalleryInfo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用内 OkHttp 归档 zip 下载器。
 */
class ArchiverDownloader private constructor(appContext: Context) {

    private val appContext = appContext.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val taskIdGenerator = AtomicLong(System.currentTimeMillis())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val activeTasks = ConcurrentHashMap<Long, ActiveTask>()

    interface Listener {
        fun onProgress(gid: Long, downloaded: Long, total: Long, speed: Long, paused: Boolean)
        fun onSuccess(gid: Long, taskId: Long, zipFile: File)
        fun onFailure(gid: Long, taskId: Long, e: Exception?)
        fun onCancel(gid: Long, taskId: Long)
    }

    class Progress(
        @JvmField val gid: Long,
        @JvmField val taskId: Long,
        @JvmField val downloaded: Long,
        @JvmField val total: Long,
        @JvmField val speed: Long,
        @JvmField val paused: Boolean
    )

    private class ActiveTask(
        val taskId: Long,
        val gid: Long,
        val url: String,
        val fileName: String,
        val zipFile: File,
        val galleryInfo: GalleryInfo
    ) {
        @Volatile
        var call: Call? = null

        @Volatile
        var downloaded: Long = 0

        @Volatile
        var total: Long = 0

        @Volatile
        var speed: Long = 0

        @Volatile
        var paused: Boolean = false

        var lastNotifyAt: Long = 0
        var lastSpeedAt: Long = 0
        var bytesSinceSpeed: Long = 0
    }

    private class PauseSignal : IOException("Paused")

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun getProgress(gid: Long): Progress? {
        val task = activeTasks[gid] ?: return null
        return Progress(task.gid, task.taskId, task.downloaded, task.total, task.speed, task.paused)
    }

    fun start(context: Context, info: GalleryInfo, url: String, fileName: String): Long {
        cancel(info.gid)
        val taskId = taskIdGenerator.incrementAndGet()
        Settings.putArchiverDownloadId(info.gid, taskId)
        Settings.putArchiverDownload(taskId, info)
        Settings.putArchiverDownloadUrl(taskId, url)
        Settings.putArchiverDownloadPaused(taskId, false)
        startInternal(info, url, fileName, taskId, false)
        return taskId
    }

    fun pause(gid: Long) {
        val task = activeTasks[gid] ?: return
        if (task.paused) {
            return
        }
        task.paused = true
        task.speed = 0
        Settings.putArchiverDownloadPaused(task.taskId, true)
        task.call?.cancel()
        publishProgress(task, true)
    }

    fun resume(gid: Long) {
        val task = activeTasks[gid] ?: return
        if (!task.paused) {
            return
        }
        startInternal(task.galleryInfo, task.url, task.fileName, task.taskId, true)
    }

    fun cancel(gid: Long) {
        val task = activeTasks.remove(gid) ?: return
        task.paused = false
        task.call?.cancel()
        deleteZipFile(task.zipFile)
        clearTaskSettings(gid, task.taskId)
        notifyCancel(gid, task.taskId)
        ArchiverDownloadService.stop(appContext)
    }

    private fun startInternal(
        info: GalleryInfo,
        url: String,
        fileName: String,
        taskId: Long,
        resumeFromFile: Boolean
    ) {
        val zipFile = resolveZipFile(fileName)
        if (zipFile == null) {
            clearTaskSettings(info.gid, taskId)
            notifyFailure(info.gid, taskId, IOException("Cannot resolve archiver directory"))
            return
        }
        if (!resumeFromFile && zipFile.exists()) {
            zipFile.delete()
        }
        val offset = if (resumeFromFile && zipFile.exists()) zipFile.length() else 0L
        val task = activeTasks[info.gid]?.takeIf { it.taskId == taskId }
            ?: ActiveTask(taskId, info.gid, url, fileName, zipFile, info).also {
                activeTasks[info.gid] = it
            }
        task.paused = false
        task.downloaded = offset
        task.speed = 0
        task.bytesSinceSpeed = 0
        task.lastSpeedAt = SystemClock.elapsedRealtime()
        task.lastNotifyAt = 0
        Settings.putArchiverDownloadPaused(taskId, false)
        val savedTotal = Settings.getArchiverDownloadTotal(taskId)
        if (savedTotal > 0) {
            task.total = savedTotal
        }
        ArchiverDownloadService.start(appContext, info.title, info.gid, info.token)
        val client = EhApplication.getOkHttpClient(appContext)
        val requestBuilder = Request.Builder().url(url)
        if (offset > 0) {
            requestBuilder.header("Range", "bytes=$offset-")
        }
        val call = client.newCall(requestBuilder.build())
        task.call = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (task.call !== call) {
                    return
                }
                if (task.paused) {
                    publishProgress(task, true)
                    return
                }
                if (activeTasks[info.gid]?.taskId != taskId) {
                    return
                }
                activeTasks.remove(info.gid)
                if (call.isCanceled) {
                    notifyCancel(info.gid, taskId)
                    ArchiverDownloadService.stop(appContext)
                    return
                }
                deleteZipFile(zipFile)
                clearTaskSettings(info.gid, taskId)
                notifyFailure(info.gid, taskId, e)
                ArchiverDownloadService.stop(appContext)
            }

            override fun onResponse(call: Call, response: Response) {
                if (task.call !== call) {
                    response.close()
                    return
                }
                val code = response.code()
                if (code == 416) {
                    response.close()
                    handleRangeNotSatisfiable(task, zipFile, info, taskId)
                    return
                }
                if (!response.isSuccessful) {
                    if (task.paused) {
                        response.close()
                        publishProgress(task, true)
                        return
                    }
                    activeTasks.remove(info.gid)
                    deleteZipFile(zipFile)
                    clearTaskSettings(info.gid, taskId)
                    notifyFailure(info.gid, taskId, IOException("HTTP $code"))
                    response.close()
                    ArchiverDownloadService.stop(appContext)
                    return
                }
                val body = response.body()
                if (body == null) {
                    activeTasks.remove(info.gid)
                    deleteZipFile(zipFile)
                    clearTaskSettings(info.gid, taskId)
                    notifyFailure(info.gid, taskId, IOException("Empty response body"))
                    response.close()
                    ArchiverDownloadService.stop(appContext)
                    return
                }
                var writeOffset = offset
                var append = offset > 0
                if (code == 200 && offset > 0) {
                    toastOnMain(R.string.archiver_download_restart_no_range)
                    deleteZipFile(zipFile)
                    writeOffset = 0L
                    append = false
                    task.downloaded = 0
                }
                body.use {
                    val total = resolveTotal(response, writeOffset, it.contentLength())
                    if (total > 0) {
                        task.total = total
                        Settings.putArchiverDownloadTotal(taskId, total)
                    }
                    try {
                        it.byteStream().use { inputStream ->
                            FileOutputStream(zipFile, append).use { outputStream ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var downloaded = writeOffset
                                while (true) {
                                    if (task.paused || call.isCanceled) {
                                        if (task.paused || task.call !== call) {
                                            throw PauseSignal()
                                        }
                                        throw IOException("Canceled")
                                    }
                                    val read = inputStream.read(buffer)
                                    if (read == -1) {
                                        break
                                    }
                                    outputStream.write(buffer, 0, read)
                                    downloaded += read
                                    task.downloaded = downloaded
                                    task.bytesSinceSpeed += read
                                    publishProgress(task, false)
                                }
                                outputStream.flush()
                            }
                        }
                        if (task.call !== call) {
                            return
                        }
                        if (task.paused) {
                            publishProgress(task, true)
                            return
                        }
                        activeTasks.remove(info.gid)
                        notifySuccess(info.gid, taskId, zipFile)
                        ArchiverDownloadCompleter.getInstance(appContext)
                            ?.importDownloadedZip(zipFile, info, taskId)
                        ArchiverDownloadService.stop(appContext)
                    } catch (e: PauseSignal) {
                        if (task.call === call) {
                            publishProgress(task, true)
                        }
                    } catch (e: Exception) {
                        if (task.call !== call) {
                            return
                        }
                        if (task.paused) {
                            publishProgress(task, true)
                            return
                        }
                        if (activeTasks[info.gid]?.taskId != taskId) {
                            return
                        }
                        activeTasks.remove(info.gid)
                        deleteZipFile(zipFile)
                        clearTaskSettings(info.gid, taskId)
                        if (call.isCanceled) {
                            notifyCancel(info.gid, taskId)
                        } else {
                            notifyFailure(info.gid, taskId, e)
                        }
                        ArchiverDownloadService.stop(appContext)
                    }
                }
            }
        })
        if (resumeFromFile) {
            Log.i(TAG, "Resumed archiver download, taskId=$taskId, gid=${info.gid}, offset=$offset")
        }
    }

    private fun handleRangeNotSatisfiable(
        task: ActiveTask,
        zipFile: File,
        info: GalleryInfo,
        taskId: Long
    ) {
        val total = if (task.total > 0) task.total else Settings.getArchiverDownloadTotal(taskId)
        if (zipFile.exists() && total > 0 && zipFile.length() >= total) {
            activeTasks.remove(info.gid)
            notifySuccess(info.gid, taskId, zipFile)
            ArchiverDownloadCompleter.getInstance(appContext)
                ?.importDownloadedZip(zipFile, info, taskId)
            ArchiverDownloadService.stop(appContext)
            return
        }
        if (task.paused) {
            publishProgress(task, true)
            return
        }
        activeTasks.remove(info.gid)
        deleteZipFile(zipFile)
        clearTaskSettings(info.gid, taskId)
        notifyFailure(info.gid, taskId, IOException("HTTP 416"))
        ArchiverDownloadService.stop(appContext)
    }

    private fun publishProgress(task: ActiveTask, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - task.lastNotifyAt < NOTIFY_INTERVAL_MS) {
            return
        }
        val elapsed = now - task.lastSpeedAt
        if (elapsed > 0) {
            task.speed = if (task.paused) 0L else task.bytesSinceSpeed * 1000L / elapsed
        }
        task.bytesSinceSpeed = 0
        task.lastSpeedAt = now
        task.lastNotifyAt = now
        val remaining = remainingMillis(task)
        notifyProgress(task.gid, task.downloaded, task.total, task.speed, task.paused)
        ArchiverDownloadService.updateProgress(
            appContext,
            task.galleryInfo.title,
            task.gid,
            task.galleryInfo.token,
            task.downloaded,
            task.total,
            task.speed,
            remaining,
            task.paused
        )
    }

    private fun remainingMillis(task: ActiveTask): Long {
        if (task.paused || task.total <= 0 || task.speed <= 0) {
            return -1L
        }
        val left = task.total - task.downloaded
        return if (left <= 0) 0L else left * 1000L / task.speed
    }

    private fun resolveZipFile(fileName: String): File? {
        val dir = AppConfig.getExternalArchiverDir() ?: AppConfig.getArchiverDir() ?: return null
        return File(dir, "$fileName.zip")
    }

    private fun clearTaskSettings(gid: Long, taskId: Long) {
        Settings.deleteArchiverDownloadId(gid)
        Settings.deleteArchiverDownload(taskId)
        Settings.deleteArchiverDownloadUrl(taskId)
        Settings.deleteArchiverDownloadPaused(taskId)
        Settings.deleteArchiverDownloadTotal(taskId)
    }

    private fun notifyProgress(
        gid: Long,
        downloaded: Long,
        total: Long,
        speed: Long,
        paused: Boolean
    ) {
        for (listener in listeners) {
            listener.onProgress(gid, downloaded, total, speed, paused)
        }
    }

    private fun notifySuccess(gid: Long, taskId: Long, zipFile: File) {
        for (listener in listeners) {
            listener.onSuccess(gid, taskId, zipFile)
        }
    }

    private fun notifyFailure(gid: Long, taskId: Long, e: Exception?) {
        for (listener in listeners) {
            listener.onFailure(gid, taskId, e)
        }
    }

    private fun notifyCancel(gid: Long, taskId: Long) {
        for (listener in listeners) {
            listener.onCancel(gid, taskId)
        }
    }

    private fun toastOnMain(resId: Int) {
        mainHandler.post {
            Toast.makeText(appContext, resId, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "ArchiverDownloader"
        private const val BUFFER_SIZE = 8192
        private const val NOTIFY_INTERVAL_MS = 500L

        @Volatile
        private var sInstance: ArchiverDownloader? = null

        @JvmStatic
        fun getInstance(context: Context?): ArchiverDownloader? {
            if (sInstance == null && context != null) {
                sInstance = ArchiverDownloader(context.applicationContext)
            }
            return sInstance
        }

        @JvmStatic
        fun resumePending(context: Context) {
            val downloader = getInstance(context) ?: return
            for (taskId in Settings.getPendingArchiverDownloadIds()) {
                val info = Settings.getArchiverDownload(taskId)
                if (info == null) {
                    Settings.deleteArchiverDownloadUrl(taskId)
                    Settings.deleteArchiverDownloadPaused(taskId)
                    Settings.deleteArchiverDownloadTotal(taskId)
                    continue
                }
                if (downloader.activeTasks.containsKey(info.gid)) {
                    continue
                }
                val url = Settings.getArchiverDownloadUrl(taskId)
                val fileName = ArchiverDownloadCompleter.createFileName(info.title, info.gid)
                val zipFile = downloader.resolveZipFile(fileName)
                if (zipFile == null) {
                    downloader.clearTaskSettings(info.gid, taskId)
                    Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show()
                    continue
                }
                val total = Settings.getArchiverDownloadTotal(taskId)
                if (zipFile.exists() && total > 0 && zipFile.length() >= total) {
                    ArchiverDownloadCompleter.getInstance(context)
                        ?.importDownloadedZip(zipFile, info, taskId)
                    continue
                }
                if (url.isNullOrEmpty()) {
                    downloader.clearTaskSettings(info.gid, taskId)
                    Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show()
                    continue
                }
                if (Settings.getArchiverDownloadPaused(taskId)) {
                    val task = ActiveTask(taskId, info.gid, url, fileName, zipFile, info)
                    task.paused = true
                    task.downloaded = if (zipFile.exists()) zipFile.length() else 0L
                    task.total = if (total > 0) total else 0L
                    downloader.activeTasks[info.gid] = task
                    downloader.publishProgress(task, true)
                    continue
                }
                downloader.startInternal(info, url, fileName, taskId, true)
            }
        }

        private fun resolveTotal(response: Response, offset: Long, contentLength: Long): Long {
            val contentRange = response.header("Content-Range")
            if (!contentRange.isNullOrEmpty()) {
                val slash = contentRange.lastIndexOf('/')
                if (slash >= 0 && slash < contentRange.length - 1) {
                    val totalStr = contentRange.substring(slash + 1).trim()
                    if (totalStr != "*") {
                        val parsed = totalStr.toLongOrNull()
                        if (parsed != null && parsed > 0) {
                            return parsed
                        }
                    }
                }
            }
            return if (contentLength > 0) offset + contentLength else 0L
        }

        private fun deleteZipFile(zipFile: File?) {
            if (zipFile != null && zipFile.exists() && !zipFile.delete()) {
                Log.w(TAG, "Failed to delete zip: ${zipFile.path}")
            }
        }
    }
}
