package com.hippo.ehviewer.ui.dialog

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.alibaba.fastjson.JSONObject
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhConfig
import com.hippo.ehviewer.client.EhRequestBuilder
import com.hippo.ehviewer.updater.AppUpdater
import com.hippo.lib.yorozuya.IOUtils
import com.hippo.util.ExceptionUtils
import com.hippo.util.IoThreadPoolExecutor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.checkerframework.checker.units.qual.A
import java.io.File
import java.io.FileOutputStream
import java.net.URISyntaxException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.properties.Delegates


class UpdateDialog(private val activity: Activity) {
    companion object {
        const val GITHUB_RELEASE_URL = "https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases"
        const val GITHUB_README_URL =
            "https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/README.md"
        const val INSTALL_PERMISSION_CODE = 1002

        // TODO more lock for different language
        private val lock: Lock = ReentrantLock()
    }

    private var myDownloadId by Delegates.notNull<Long>()
    private var downloadReceiver: DownloadReceiver? = null


    fun showCheckFailDialog() {
        try {
            ContextCompat.getMainExecutor(activity).execute {
                val alertDialog = AlertDialog.Builder(activity)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(R.string.update_fail)
                    .setMessage(R.string.update_fail_info)
                    .setPositiveButton(R.string.yes) { dialog, id ->
                        gotoGithub(dialog, id)
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                alertDialog.show()
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun showUpdateDialog(tempUpdateData: JSONObject) {
        try {
            val version = tempUpdateData.getString(AppUpdater.VERSION)
            val mustUpdate = tempUpdateData.getBooleanValue(AppUpdater.MUST_UPDATE)
            val updateContent = tempUpdateData.getJSONObject(AppUpdater.UPDATE_CONTENT)
            val title = updateContent.getString(AppUpdater.TITLE)
            val contentObs = updateContent.getJSONArray(AppUpdater.CONTENT).toArray()
            val contentSts: Array<String?> = arrayOfNulls(contentObs.size)

            for ((index, value) in contentObs.withIndex()) {
                contentSts[index] = value.toString()
            }

            val downloadUrl = updateContent.getString(AppUpdater.FILE_DOWNLOAD_URL)
            ContextCompat.getMainExecutor(activity).execute {
                val alertDialog = AlertDialog.Builder(activity).apply {
                    setIcon(R.mipmap.ic_launcher)
                    setTitle(title)
                    setItems(contentSts) { _, _ ->
                    }
                    setPositiveButton(R.string.update) { dialog, id ->
//                        gotoGithub(dialog, id)
                        downloadApk(dialog, id, downloadUrl, version)
                    }
                    if (!mustUpdate) {
                        setNegativeButton(R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                        }
                    } else {
                        setCancelable(false)
                    }
                }.create()
                alertDialog.show()
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun downloadApk(
        dialog: DialogInterface?,
        id: Int,
        downloadUrl: String,
        version: String
    ) {
        val uri = GITHUB_README_URL.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        activity.startActivity(intent)
        dialog?.dismiss()
//        val title = "Ehviewer$version.apk"
//        val request = DownloadManager.Request(downloadUrl.toUri())
//        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
//        request.setAllowedOverRoaming(true)
//        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
//        request.setTitle(title)
//        request.setDescription(activity.getString(R.string.download_archive_started))
//        request.setVisibleInDownloadsUi(true)
//        request.setDestinationInExternalPublicDir(
//            Environment.DIRECTORY_DOWNLOADS,
//            EhConfig.UPDATE_PATH + title + ".apk"
//        )
//        request.allowScanningByMediaScanner()
//
//        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//        myDownloadId = downloadManager.enqueue(request)
//        downloadReceiver = DownloadReceiver(this, myDownloadId)
//        ContextCompat.registerReceiver(
//            activity,
//            downloadReceiver,
//            IntentFilter(DownloadManager.ACTION_VIEW_DOWNLOADS),
//            ContextCompat.RECEIVER_EXPORTED
//        )
//        IoThreadPoolExecutor.getInstance().execute{
//           try {
//               lock.tryLock()
//               val file =File(Environment.DIRECTORY_DOWNLOADS+"/"+EhConfig.UPDATE_PATH + title + ".apk")
//               if (save(EhApplication.getOkHttpClient(activity),downloadUrl,file)){
//                   installApp(file)
//               }else{
//                   showCheckFailDialog()
//               }
//           }catch (e:Exception){
//               lock.unlock()
//           }
//        }
    }

    private fun gotoGithub(dialog: DialogInterface, id: Int) {
        val uri = GITHUB_RELEASE_URL.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        activity.startActivity(intent)
        dialog.dismiss()
    }

    private fun onDownloadFailed(c: Cursor) {
        showCheckFailDialog()
    }

//    private fun installApp(apkFile: File) {
//        if (ContextCompat.checkSelfPermission(
//                activity,
//                Manifest.permission.INSTALL_PACKAGES
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                activity,
//                arrayOf(Manifest.permission.INSTALL_PACKAGES),
//                INSTALL_PERMISSION_CODE
//            )
//            return
//        }
//
//        if (apkFile.exists()) {
//            val intent = Intent(Intent.ACTION_VIEW)
//            intent.setDataAndType(
//                Uri.fromFile(apkFile),
//                "application/vnd.android.package-archive"
//            )
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            activity.startActivity(intent)
//        }
//    }

//    private fun installApp(c: Cursor) {
//        val path: String =
//            c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
//        val apkFile = File(path)
//
//        if (ContextCompat.checkSelfPermission(
//                activity,
//                Manifest.permission.INSTALL_PACKAGES
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                activity,
//                arrayOf(Manifest.permission.INSTALL_PACKAGES),
//                INSTALL_PERMISSION_CODE
//            )
//            return
//        }
//
//        if (apkFile.exists()) {
//            val intent = Intent(Intent.ACTION_VIEW)
//            intent.setDataAndType(
//                Uri.fromFile(apkFile),
//                "application/vnd.android.package-archive"
//            )
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            activity.startActivity(intent)
//        }
//    }

    private fun save(client: OkHttpClient, url: String, file: File): Boolean {
//        val request = Request.Builder().url(url).build()
        val request = EhRequestBuilder(url, null, null).get().build()
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

    private class DownloadReceiver(
        private val updateDialog: UpdateDialog,
        private val myDownloadId: Long
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
                if (myDownloadId != downloadId) {
                    return
                }
                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                //检查下载状态
                checkDownloadStatus(downloadId, downloadManager)
            }
        }

        fun checkDownloadStatus(downloadId: Long, downloadManager: DownloadManager) {
            val query = DownloadManager.Query()
            query.setFilterById(downloadId) //筛选下载任务，传入任务ID，可变参数
            try {
                downloadManager.query(query).use { c ->
                    if (c.moveToFirst()) {
                        val status =
                            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        when (status) {
                            DownloadManager.STATUS_PAUSED -> Log.i(TAG, ">>>下载暂停")
                            DownloadManager.STATUS_PENDING -> Log.i(TAG, ">>>下载延迟")
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.i(TAG, ">>>下载完成")
//                                updateDialog.installApp(c)
                            }

                            DownloadManager.STATUS_FAILED -> updateDialog.onDownloadFailed(c)
                            DownloadManager.STATUS_RUNNING -> Log.i(TAG, ">>>正在下载") // 此处无法监听到
                            else -> Log.i(TAG, ">>>正在下载")
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.message, e)
            } catch (e: URISyntaxException) {
                Log.e(TAG, e.message, e)
            }
        }

        companion object {
            private const val TAG = "AppUpdateDownloadReceiver"
        }
    }
}