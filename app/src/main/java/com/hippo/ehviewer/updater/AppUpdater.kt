package com.hippo.ehviewer.updater

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.BuildConfig
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.dialog.UpdateDialog
import com.hippo.lib.yorozuya.FileUtils
import com.hippo.lib.yorozuya.IOUtils
import com.hippo.util.AppHelper.Companion.compareVersion
import com.hippo.util.ExceptionUtils
import com.hippo.util.IoThreadPoolExecutor
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.Okio
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

class AppUpdater(private val name: String, source: BufferedSource) {
    private val updateData: JSONObject


    init {
        val jsonObject = JSONObject.parseObject(source.readUtf8())
        updateData = jsonObject
    }

    companion object {
        private const val TAG = "AppUpdater"

        const val VERSION: String = "version"
        const val VERSION_CODE: String = "versionCode"
        const val FILE_DOWNLOAD_URL: String = "fileDownloadUrl"
        const val MUST_UPDATE: String = "mustUpdate"
        const val UPDATE_CONTENT: String = "updateContent"
        const val TITLE: String = "title"
        const val CONTENT: String = "content"

        @Volatile
        private var instance: AppUpdater? = null

        // TODO more lock for different language
        private val lock: Lock = ReentrantLock()

        fun getInstance(context: Context): AppUpdater? {
            if (isPossible(context)) {
                return instance
            } else {
                instance = null
                return null
            }
        }

        fun isPossible(context: Context): Boolean {
            return getMetadata(context) != null
        }

        private fun getMetadata(context: Context): Array<String>? {
            val metadata = context.resources.getStringArray(R.array.update_metadata)
            return if (metadata.size == 2) {
                metadata
            } else {
                null
            }
        }

        @JvmStatic
        fun update(activity: Activity, manualChecking: Boolean) {
            val urls = getMetadata(activity)
            if (urls == null || urls.size != 2) {
                // Clear tags if it's not possible
                instance = null
                return
            }


            if (!Settings.getIsUpdateTime() && !manualChecking) {
                return
            }
            val dataName = urls[0]
            val dataUrl = urls[1]
            // Clear tags if name if different
            val tmp = instance
            if (tmp != null && tmp.name != dataName) {
                instance = null
            }

            IoThreadPoolExecutor.getInstance().execute {
                if (!lock.tryLock()) {
                    return@execute
                }
                try {
                    val dir = AppConfig.getFilesDir("update-json") ?: return@execute

                    val dataFile = File(dir, dataName)
                    // Read current AppUpdater
                    if (instance == null && dataFile.exists()) {
                        try {
                            Okio.buffer(Okio.source(dataFile)).use { source ->
                                instance = AppUpdater(dataName, source)
                            }
                        } catch (e: IOException) {
                            FileUtils.delete(dataFile)
                        }
                    }

                    val client =
                        EhApplication.getOkHttpClient(EhApplication.getInstance())

                    // Save new json data
                    val tempDataFile = File(dir, "$dataName.tmp")
                    if (!save(client, dataUrl, tempDataFile)) {
                        if (manualChecking){
                            UpdateDialog(activity).showCheckFailDialog()
                        }
                        FileUtils.delete(tempDataFile)
                        return@execute
                    }

                    val needUpdate: Boolean
                    // 使用FastJSON的parseObject方法解析JSON内容
                    val tempUpdateData =
                        JSON.parseObject(FileUtils.read(tempDataFile))

                    // Check new data
                    needUpdate = if (instance != null) {
                        checkData(
                            instance!!.updateData,
                            tempUpdateData,
                            manualChecking
                        )
                    } else {
                        checkData(null, tempUpdateData, manualChecking)
                    }

                    if (!needUpdate) {
                        FileUtils.delete(tempDataFile)
                        if (manualChecking){
                            ContextCompat.getMainExecutor(activity).execute {
                                Toast.makeText(activity,R.string.update_to_date,Toast.LENGTH_LONG).show()
                            }
                        }
                        return@execute
                    }

                    // Replace current  current data with  new data
                    FileUtils.delete(dataFile)
                    tempDataFile.renameTo(dataFile)

                    // Read new AppUpdater
                    try {
                        Okio.buffer(Okio.source(dataFile)).use { source ->
                            instance = AppUpdater(dataName, source)
                        }
                    } catch (e: IOException) {
                        // Ignore
                    }
                    UpdateDialog(activity).showUpdateDialog(tempUpdateData)
                    Settings.putUpdateTime(Date().time)
                } finally {
                    lock.unlock()
                }
            }
        }

        private fun checkData(
            updateData: JSONObject?,
            tempUpdateData: JSONObject,
            manualChecking: Boolean
        ): Boolean {
            try {
                val currentVersion = BuildConfig.VERSION_NAME
                val currentVersionCode = BuildConfig.VERSION_CODE
                var updateResult: Int
                if (updateData != null) {
                    updateResult = compareVersion(
                        updateData.getString(VERSION), tempUpdateData.getString(
                            VERSION
                        )
                    )
                    if (updateResult < 0) {
                        return true
                    } else if (updateResult == 0) {
                        if (updateData.getInteger(VERSION_CODE) < tempUpdateData.getInteger(
                                VERSION_CODE
                            )
                        ) {
                            return true
                        }
                    }
                    if (!manualChecking) {
                        return false
                    }
                }
                updateResult = compareVersion(currentVersion, tempUpdateData.getString(VERSION))
                return if (updateResult < 0) {
                    true
                } else currentVersionCode < tempUpdateData.getInteger(VERSION_CODE)
            } catch (e: JSONException) {
                Log.e(TAG, e.message, e)
                FirebaseCrashlytics.getInstance().recordException(e)
                return false
            }
        }

        private fun save(client: OkHttpClient, url: String, file: File): Boolean {
            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        return false
                    }
                    val body = response.body() ?: return false

                    body.byteStream().use { `is` ->
                        FileOutputStream(file).use { os ->
                            IOUtils.copy(`is`, os)
                        }
                    }
                    return true
                }
            } catch (t: Throwable) {
                ExceptionUtils.throwIfFatal(t)
                FirebaseCrashlytics.getInstance().recordException(t)
                return false
            }
        }
    }
}
