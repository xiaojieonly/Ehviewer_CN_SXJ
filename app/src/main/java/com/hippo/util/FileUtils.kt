package com.hippo.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.client.EhConfig
import com.hippo.lib.yorozuya.IOUtils
import com.hippo.unifile.UniFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FileUtils {
    companion object{
        private const val TAG = "FileUtils"

        @JvmStatic
        fun copyFile(fromFile: File?, toFile: File?): Boolean {
            return copyFile(fromFile, toFile, false)
        }
        @JvmStatic
        fun copyFile(fromFile: File?, toFile: File?, flush: Boolean = false): Boolean {
            try {
                val fileFrom: InputStream = FileInputStream(fromFile)
                val fileTo: OutputStream = FileOutputStream(toFile)
                val b = ByteArray(1024)
                var c: Int

                while ((fileFrom.read(b).also { c = it }) > 0) {
                    fileTo.write(b, 0, c)
                }
                if (flush) {
                    fileTo.flush()
                }
                fileFrom.close()
                fileTo.close()

                return true
            } catch (ioException: IOException) {
                ExceptionUtils.throwIfFatal(ioException)
                FirebaseCrashlytics.getInstance().recordException(ioException)
                return false
            }
        }

        @JvmStatic
        fun copyFile(fromFile: UniFile, toFile: UniFile, flush: Boolean): Boolean {
            try {
                val fileFrom = fromFile.openInputStream()
                val fileTo = toFile.openOutputStream()
                val b = ByteArray(1024)
                var c: Int

                while ((fileFrom.read(b).also { c = it }) > 0) {
                    fileTo.write(b, 0, c)
                }
                if (flush) {
                    fileTo.flush()
                }
                fileFrom.close()
                fileTo.close()

                return true
            } catch (ioException: IOException) {
                ExceptionUtils.throwIfFatal(ioException)
                FirebaseCrashlytics.getInstance().recordException(ioException)
                return false
            }
        }

        /**
         * 打开目录
         *
         * @param path
         * @param context
         */
        @JvmStatic
        fun openAssignFolder(path: String, context: Context) {
            val file = File(path)
            if (!file.exists()) {
                return
            }
            val intent: Intent

            //        Uri uri = Uri.parse(path);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val uri =
                    ("content://com.android.externalstorage.documents/document/primary%3ADownload/" + EhConfig.TORRENT_PATH).toUri()
                intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.setType("*/*")
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            } else {
                intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.setType("*/*")
                intent.setDataAndType(Uri.fromFile(file), "file/*")
            }


            try {
//            context.startActivity(intent);
                context.startActivity(Intent.createChooser(intent, "选择浏览工具"))
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun getPath(context: Context, uri: Uri?): String? {
            var path = ""
            val cursor = context.contentResolver.query(uri!!, null, null, null, null) ?: return null
            if (cursor.moveToFirst()) {
                try {
                    path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            cursor.close()
            return path
        }

        /**
         * @return `null` for get exception
         */
        fun read(file: File?): String? {
            if (file == null) {
                return null
            }

            var `is`: InputStream? = null
            try {
                `is` = FileInputStream(file)
                return IOUtils.readString(`is`, "utf-8")
            } catch (e: IOException) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e(TAG, e.message, e)
                return null
            } finally {
                IOUtils.closeQuietly(`is`)
            }
        }
    }
}
