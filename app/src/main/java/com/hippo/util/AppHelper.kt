/*
 * Copyright 2015 Hippo Seven
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
package com.hippo.util

import android.app.Activity
import android.app.Dialog
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.hippo.ehviewer.R

class AppHelper {
    private fun isWifiProxy(context: Context): Boolean {
        val proxyAddress: String
        val proxyPort: Int
        proxyAddress = System.getProperty("http.proxyHost") as String
        val portStr = System.getProperty("http.proxyPort")
        proxyPort = (portStr ?: "-1").toInt()
        return !TextUtils.isEmpty(proxyAddress) && proxyPort != -1
    }

    companion object {
        @JvmStatic
        fun sendEmail(
            from: Activity, address: String,
            subject: String?, text: String?
        ): Boolean {
            val i = Intent(Intent.ACTION_SENDTO)
            i.data = Uri.parse("mailto:$address")
            if (subject != null) {
                i.putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            if (text != null) {
                i.putExtra(Intent.EXTRA_TEXT, text)
            }
            return try {
                from.startActivity(i)
                true
            } catch (e: Throwable) {
                ExceptionUtils.throwIfFatal(e)
                Toast.makeText(from, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
                false
            }
        }

        @JvmStatic
        fun share(from: Activity, text: String?): Boolean {
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, text)
            sendIntent.type = "text/plain"
            val chooser = Intent.createChooser(sendIntent, from.getString(R.string.share))
            return try {
                from.startActivity(chooser)
                true
            } catch (e: Throwable) {
                ExceptionUtils.throwIfFatal(e)
                Toast.makeText(from, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
                false
            }
        }

        @JvmStatic
        fun showSoftInput(context: Context?, view: View?) {
            showSoftInput(context!!, view!!, true)
        }

        @JvmStatic
        fun showSoftInput(context: Context, view: View, requestFocus: Boolean = true) {
            if (requestFocus) {
                view.requestFocus()
            }
            val imm = context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, 0)
        }

        @JvmStatic
        fun hideSoftInput(activity: Activity) {
            val view = activity.currentFocus
            if (view != null) {
                val imm =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }

        @JvmStatic
        fun hideSoftInput(dialog: Dialog) {
            val view = dialog.currentFocus
            if (view != null) {
                val imm =
                    dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }

        @JvmStatic
        fun copyPlainText(data: String?, context: Context) {
            // 获取系统剪贴板
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // 创建一个剪贴数据集，包含一个普通文本数据条目（需要复制的数据）,其他的还有
            // newHtmlText、
            // newIntent、
            // newUri、
            // newRawUri
            val clipData = ClipData.newPlainText(null, data)

            // 把数据集设置（复制）到剪贴板
            clipboard.setPrimaryClip(clipData)
        }

        @JvmStatic
        fun checkVPN(context: Context): Boolean {
            val connectivityManager = context.getSystemService(
                ConnectivityManager::class.java
            )
            val network = connectivityManager.activeNetwork
            //don't know why always returns null:
            val networkInfo = connectivityManager.getNetworkInfo(network)
            val result = networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_VPN
            if (result) {
                try {
                    Toast.makeText(context, R.string.network_remind, Toast.LENGTH_LONG).show()
                } catch (ignore: RuntimeException) {
                }
            }
            return !result
        }
    }
}