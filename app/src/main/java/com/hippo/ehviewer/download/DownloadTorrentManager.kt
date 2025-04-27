package com.hippo.ehviewer.download

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.Log
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.client.data.TorrentDownloadMessage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 文件下载工具
 */
class DownloadTorrentManager private constructor(private val okHttpClient: OkHttpClient) {
    private var handler: Handler? = null

    /**
     * url 下载连接
     * saveDir 储存下载文件的SDCard目录
     * listener 下载监听
     */
    fun download(url: String, saveDir: String, name: String, handler: Handler?, context: Context) {
        this.handler = handler
        val request = Request.Builder().url(url).build()
        // 储存下载文件的目录
        var savePath: String? = null
        try {
            savePath = isExistDir(saveDir)
        } catch (ioException: IOException) {
            ioException.printStackTrace()
        }
        val file = File(savePath, name)
        val path = file.path
        if (file.exists()) {
            EhApplication.removeDownloadTorrent(context, url)
            sendMessage(path, savePath, name, 100, false)
            return
        }

        val dir = savePath
        sendMessage(path, saveDir, name, 0, false)
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 下载失败
                EhApplication.removeDownloadTorrent(context, url)
                sendMessage(url, dir, name, -1, true)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                var `is`: InputStream? = null
                val buf = ByteArray(2048)
                var len: Int
                var fos: FileOutputStream? = null
                try {
                    `is` = response.body()!!.byteStream()
                    val total = response.body()!!.contentLength()
                    fos = FileOutputStream(file)
                    var sum: Long = 0
                    while ((`is`.read(buf).also { len = it }) != -1) {
                        fos.write(buf, 0, len)
                        sum += len.toLong()
                        val progress = (sum * 1.0f / total * 100).toInt()
                        // 下载中
                        sendMessage(path, dir, name, progress, false)
                    }

                    fos.flush()
                    // 下载完成
                    EhApplication.removeDownloadTorrent(context, url)
                    sendMessage(path, dir, name, 100, false)
                } catch (e: Exception) {
                    EhApplication.removeDownloadTorrent(context, url)
                    sendMessage(path, dir, name, -1, true)
                } finally {
                    try {
                        `is`?.close()
                    } catch (ignored: IOException) {
                    }
                    try {
                        fos?.close()
                    } catch (e: IOException) {
                    }
                }
            }
        })
    }

    /**
     * saveDir
     * 判断下载目录是否存在
     */
    @Throws(IOException::class)
    private fun isExistDir(saveDir: String): String {
        // 下载位置
        val downloadFile = File(saveDir)
        if (!downloadFile.mkdirs()) {
            downloadFile.createNewFile()
        }
        return downloadFile.absolutePath
    }

    private fun sendMessage(
        path: String,
        dir: String?,
        name: String,
        progress: Int,
        failed: Boolean
    ) {
        val message = torrentDownLoadMessage(path, dir, name, progress, failed)
        handler!!.sendMessage(message)
    }


    private fun torrentDownLoadMessage(
        path: String,
        dir: String?,
        name: String,
        progress: Int,
        failed: Boolean
    ): Message {
        val result = handler!!.obtainMessage()
        val data = Bundle()

        val message = TorrentDownloadMessage()

        message.failed = failed
        message.progress = progress
        message.dir = dir
        message.path = path
        message.name = name

        data.putParcelable("torrent_download_message", message)

        result.data = data

        return result
    }

    companion object {
        private val TAG: String = DownloadTorrentManager::class.java.name
        private var downloadTorrentManager: DownloadTorrentManager? = null
        @JvmStatic
        fun get(okHttpClient: OkHttpClient): DownloadTorrentManager {
            if (downloadTorrentManager == null) {
                downloadTorrentManager = DownloadTorrentManager(okHttpClient)
            }
            return downloadTorrentManager!!
        }

        val sDCardPath: String
            /**
             * 获取SD卡路径
             *
             * @return
             */
            get() {
                val sdCardPath = Environment.getExternalStorageDirectory()
                    .absolutePath + File.separator
                Log.i(
                    TAG,
                    "getSDCardPath:$sdCardPath"
                )
                return sdCardPath
            }

        /**
         * url
         * 从下载连接中解析出文件名
         */
        fun getNameFromUrl(url: String): String {
            return url.substring(url.lastIndexOf("/") + 1)
        }
    }
}