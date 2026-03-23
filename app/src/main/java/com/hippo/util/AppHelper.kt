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
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.hippo.ehviewer.R
import java.lang.RuntimeException
import androidx.core.net.toUri

class AppHelper {
    private fun isWifiProxy(context: Context?): Boolean {
        var proxyPort: Int

        val proxyAddress = System.getProperty("http.proxyHost")
        val portStr = System.getProperty("http.proxyPort")
        proxyPort = (portStr ?: "-1").toInt()

        return (!TextUtils.isEmpty(proxyAddress)) && (proxyPort != -1)
    }

    companion object {
        @JvmStatic
        fun sendEmail(
            from: Activity, address: String,
            subject: String?, text: String?
        ) {
            val i = Intent(Intent.ACTION_SENDTO)
            i.setData("mailto:$address".toUri())
            if (subject != null) {
                i.putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            if (text != null) {
                i.putExtra(Intent.EXTRA_TEXT, text)
            }

            try {
                from.startActivity(i)
            } catch (e: Throwable) {
                ExceptionUtils.throwIfFatal(e)
                Toast.makeText(from, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
            }
        }

        @JvmStatic
        fun share(from: Activity, text: String?): Boolean {
            val sendIntent = Intent()
            sendIntent.setAction(Intent.ACTION_SEND)
            sendIntent.putExtra(Intent.EXTRA_TEXT, text)
            sendIntent.setType("text/plain")
            val chooser = Intent.createChooser(sendIntent, from.getString(R.string.share))

            try {
                from.startActivity(chooser)
                return true
            } catch (e: Throwable) {
                ExceptionUtils.throwIfFatal(e)
                Toast.makeText(from, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
                return false
            }
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
                val imm = dialog.context
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
            clipboard.setPrimaryClip(clipData!!)
        }

        @JvmStatic
        fun checkVPN(context: Context): Boolean {
            // 如果应用没有声明或授予 ACCESS_NETWORK_STATE，就直接跳过 VPN 检测，避免崩溃
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_NETWORK_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                // 无权限时，不做任何网络状态检测，认为“可以正常访问”
                return true
            }

            return try {
                val connectivityManager =
                    context.getSystemService(ConnectivityManager::class.java)
                        ?: // 拿不到系统服务时直接认为“允许访问”，避免崩溃
                        return true

                val isVpn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // 新 API：通过 NetworkCapabilities 判断是否走 VPN
                    val network = connectivityManager.activeNetwork
                    val capabilities = if (network != null) {
                        connectivityManager.getNetworkCapabilities(network)
                    } else {
                        null
                    }
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                } else {
                    // 旧 API：使用 TYPE_VPN 的 NetworkInfo（已废弃，但仅在旧系统上走到这里）
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_VPN)
                    @Suppress("DEPRECATION")
                    networkInfo?.isConnected == true
                }

                if (isVpn) {
                    try {
                        Toast.makeText(context, R.string.network_remind, Toast.LENGTH_LONG).show()
                    } catch (_: RuntimeException) {
                    }
                }

                !isVpn
            } catch (_: SecurityException) {
                // 某些机型/ROM 即使有权限声明也可能抛 SecurityException，这里统一兜底为“允许访问”
                true
            }
        }

        @JvmStatic
        fun compareVersion(version1: String, version2: String): Int {
            val parts1 = version1.split("\\.".toRegex()).toTypedArray()
            val parts2 = version2.split("\\.".toRegex()).toTypedArray()
            val length = parts1.size.coerceAtLeast(parts2.size)
            for (i in 0 until length) {
                val part1 = if (i < parts1.size) parts1[i].toInt() else 0
                val part2 = if (i < parts2.size) parts2[i].toInt() else 0
                if (part1 < part2) {
                    return -1
                } else if (part1 > part2) {
                    return 1
                }
            }
            return 0
        }
    }
}
