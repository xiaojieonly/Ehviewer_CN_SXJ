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
package com.hippo.ehviewer.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 小米/澎湃系统优化助手
 * 用于检测小米系统并提供针对性的后台优化建议
 */
object MiuiOptimizationHelper {
    private const val TAG = "MiuiOptimization"
    
    // 小米系统属性
    private const val KEY_MIUI_VERSION_CODE = "ro.miui.ui.version.code"
    private const val KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name"
    private const val KEY_MIUI_INTERNAL_STORAGE = "ro.miui.internal.storage"
    
    /**
     * 检测是否为小米/Redmi/POCO设备
     */
    fun isMiuiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("POCO", ignoreCase = true) ||
                !getSystemProperty(KEY_MIUI_VERSION_CODE).isNullOrEmpty()
    }
    
    /**
     * 检测是否为澎湃系统（HyperOS）
     */
    fun isHyperOsDevice(): Boolean {
        val miuiVersion = getMiuiVersion()
        // HyperOS 通常从 MIUI 14+ 开始或标识为 HyperOS
        return isMiuiDevice() && (miuiVersion >= 14 || 
                Build.DISPLAY.contains("HyperOS", ignoreCase = true))
    }
    
    /**
     * 获取MIUI版本号
     */
    fun getMiuiVersion(): Int {
        if (!isMiuiDevice()) {
            return -1
        }
        
        try {
            val versionName = getSystemProperty(KEY_MIUI_VERSION_NAME)
            if (!versionName.isNullOrEmpty()) {
                // 例如: "V14", "V13", "V816" -> 提取数字
                val match = Regex("V(\\d+)").find(versionName)
                if (match != null) {
                    return match.groupValues[1].toInt()
                }
            }
            
            // 尝试从 version code 获取
            val versionCode = getSystemProperty(KEY_MIUI_VERSION_CODE)
            if (!versionCode.isNullOrEmpty()) {
                return versionCode.toIntOrNull() ?: -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MIUI version", e)
        }
        
        return -1
    }
    
    /**
     * 获取系统属性
     */
    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            // 尝试通过 getprop 命令获取
            try {
                val process = Runtime.getRuntime().exec("getprop $key")
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.readLine()?.trim()
                }
            } catch (ex: Exception) {
                null
            }
        }
    }
    
    /**
     * 检查是否需要小米特殊优化
     */
    fun needsMiuiOptimization(): Boolean {
        // Android 10+ 的小米设备需要特殊优化
        return isMiuiDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
    
    /**
     * 检查是否为需要高度优化的版本（Android 14+ 或 HyperOS）
     */
    fun needsAggressiveOptimization(): Boolean {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ||
                isHyperOsDevice()
    }
    
    /**
     * 获取推荐的通知优先级
     */
    fun getRecommendedNotificationImportance(): Int {
        return if (needsMiuiOptimization()) {
            // 小米系统使用 HIGH 优先级，避免被系统杀后台
            android.app.NotificationManager.IMPORTANCE_HIGH
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 使用 DEFAULT 优先级
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        } else {
            // 其他设备使用 LOW 优先级
            android.app.NotificationManager.IMPORTANCE_LOW
        }
    }
    
    /**
     * 打开小米电池优化设置页面
     */
    fun openMiuiBatterySettings(context: Context): Boolean {
        if (!isMiuiDevice()) {
            return false
        }
        
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open MIUI battery settings", e)
            return false
        }
    }
    
    /**
     * 打开小米自启动管理页面
     */
    fun openMiuiAutoStartSettings(context: Context): Boolean {
        if (!isMiuiDevice()) {
            return false
        }
        
        val intents = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            },
            Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
        )
        
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open autostart with intent: ${e.message}")
            }
        }
        
        return false
    }
    
    /**
     * 打开小米省电优化设置
     */
    fun openMiuiPowerSaveSettings(context: Context): Boolean {
        if (!isMiuiDevice()) {
            return false
        }
        
        try {
            val intent = Intent().apply {
                setClassName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open MIUI power save settings", e)
            return false
        }
    }
    
    /**
     * 获取设备信息用于调试
     */
    fun getDeviceInfo(): String {
        return buildString {
            appendLine("=== Device Info ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Display: ${Build.DISPLAY}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Is MIUI: ${isMiuiDevice()}")
            appendLine("Is HyperOS: ${isHyperOsDevice()}")
            appendLine("MIUI Version: ${getMiuiVersion()}")
            appendLine("Needs MIUI Optimization: ${needsMiuiOptimization()}")
            appendLine("Needs Aggressive Optimization: ${needsAggressiveOptimization()}")
            appendLine("Recommended Notification Importance: ${getRecommendedNotificationImportance()}")
        }
    }
    
    /**
     * 记录设备信息到日志
     */
    fun logDeviceInfo() {
        Log.i(TAG, getDeviceInfo())
    }
}
