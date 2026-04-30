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
package com.hippo.ehviewer.network

import android.content.Context
import android.util.Log
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.Settings
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 网络活动日志管理器
 * 将不同类别的网络活动记录到分类日志文件中。
 * 日志文件存放在与 logcat 导出相同的目录下。
 *
 * 分类：
 * - okhttp:    所有 HTTP 请求/响应
 * - download:  下载专用活动
 * - background: 后台/前台切换、保活、锁管理
 * - error:     网络错误和超时
 */
object NetworkLogger {
    private const val TAG = "NetworkLogger"

    /** 日志分类 */
    enum class Category(val fileName: String) {
        OKHTTP("network_okhttp"),
        DOWNLOAD("network_download"),
        BACKGROUND("network_background"),
        ERROR("network_error"),
        SPEED("network_speed")
    }

    private const val LOG_FILE_EXTENSION = ".log"
    private const val MAX_BATCH_SIZE = 50

    private var logExecutor: ExecutorService? = null
    private var isInitialized = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 各分类的当前日志日期（用于按天轮转） */
    private val currentDates = mutableMapOf<Category, String>()
    /** 各分类的文件写入器 */
    private val writers = mutableMapOf<Category, FileWriter?>()
    /** 写入锁 */
    private val writeLock = Any()

    @Volatile
    var enabled: Boolean = false
        get() = Settings.getNetworkLogEnabled()
        private set

    /**
     * 初始化日志系统（在 Application.onCreate 中调用）
     */
    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        logExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "NetworkLogger").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
            }
        }

        enabled = Settings.getNetworkLogEnabled()
        Log.i(TAG, "NetworkLogger initialized, enabled=$enabled")
    }

    /**
     * 通知设置变化（设置变更时由设置监听器调用）
     */
    fun onSettingChanged() {
        val newValue = Settings.getNetworkLogEnabled()
        val changed = enabled != newValue
        if (changed && !newValue) {
            // 关闭时刷新并关闭所有 writer
            logExecutor?.execute {
                synchronized(writeLock) {
                    writers.values.forEach { closeWriter(it) }
                    writers.clear()
                    currentDates.clear()
                }
            }
        }
        enabled = newValue
        Log.i(TAG, "NetworkLogger enabled=$newValue")
    }

    // ==================== 公共日志方法 ====================

    fun logOkHttp(message: String) {
        if (!enabled) return
        enqueue(Category.OKHTTP, message, null)
    }

    fun logOkHttp(message: String, throwable: Throwable?) {
        if (!enabled) return
        enqueue(Category.OKHTTP, message, throwable)
    }

    fun logDownload(message: String) {
        if (!enabled) return
        enqueue(Category.DOWNLOAD, message, null)
    }

    fun logDownload(message: String, throwable: Throwable?) {
        if (!enabled) return
        enqueue(Category.DOWNLOAD, message, throwable)
    }

    fun logBackground(message: String) {
        if (!enabled) return
        enqueue(Category.BACKGROUND, message, null)
    }

    fun logError(message: String) {
        if (!enabled) return
        enqueue(Category.ERROR, message, null)
    }

    fun logError(message: String, throwable: Throwable?) {
        if (!enabled) return
        enqueue(Category.ERROR, message, throwable)
    }

    fun logSpeed(message: String) {
        if (!enabled) return
        enqueue(Category.SPEED, message, null)
    }

    // ==================== 内部实现 ====================

    private fun enqueue(category: Category, message: String, throwable: Throwable?) {
        val executor = logExecutor ?: return
        executor.execute {
            writeLog(category, message, throwable)
        }
    }

    private fun writeLog(category: Category, message: String, throwable: Throwable?) {
        synchronized(writeLock) {
            try {
                val writer = getOrCreateWriter(category) ?: return
                val timestamp = dateFormat.format(Date())

                writer.write("$timestamp | $message\n")
                if (throwable != null) {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    writer.write("$timestamp |   Caused by: ${sw}\n")
                }
                writer.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log for ${category.fileName}", e)
            }
        }
    }

    private fun getOrCreateWriter(category: Category): FileWriter? {
        val today = dayFormat.format(Date())

        // 检查是否需要轮转（日期变了）
        if (currentDates[category] != today) {
            closeWriter(writers[category])
            writers.remove(category)
            currentDates.remove(category)
        }

        // 获取或创建 writer
        var writer = writers[category]
        if (writer == null) {
            val logDir = AppConfig.getExternalLogcatDir()
            if (logDir == null) {
                Log.w(TAG, "Cannot get log directory for ${category.fileName}")
                return null
            }

            val logFile = File(logDir, "${category.fileName}$LOG_FILE_EXTENSION")
            try {
                writer = FileWriter(logFile, true) // append mode
                writers[category] = writer
                currentDates[category] = today

                // 如果是新文件，写个头部
                if (logFile.length() == 0L) {
                    writer.write("=== ${category.fileName} log started at $today ===\n")
                    writer.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create log file for ${category.fileName}", e)
                return null
            }
        }

        return writer
    }

    private fun closeWriter(writer: FileWriter?) {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: IOException) {
        }
    }

    /**
     * 关闭所有 writer（应用退出时调用）
     */
    @Synchronized
    fun shutdown() {
        logExecutor?.execute {
            synchronized(writeLock) {
                writers.values.forEach { closeWriter(it) }
                writers.clear()
                currentDates.clear()
            }
        }
        logExecutor?.shutdown()
        try {
            logExecutor?.awaitTermination(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        isInitialized = false
        logExecutor = null
    }
}
