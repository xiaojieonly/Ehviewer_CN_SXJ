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
import android.os.PowerManager
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
    // HyperOS 检测属性
    private const val KEY_HYPEROS_VERSION = "ro.mi.os.version"
    
    /** 缓存设备检测结果，避免重复反射调用 */
    private var sIsMiuiDevice: Boolean? = null
    private var sIsHyperOsDevice: Boolean? = null
    private var sMiuiVersion: Int? = null
    
    /**
     * 检测是否为小米/Redmi/POCO设备
     */
    fun isMiuiDevice(): Boolean {
        if (sIsMiuiDevice != null) return sIsMiuiDevice!!
        sIsMiuiDevice = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("POCO", ignoreCase = true) ||
                !getSystemProperty(KEY_MIUI_VERSION_CODE).isNullOrEmpty() ||
                !getSystemProperty(KEY_MIUI_INTERNAL_STORAGE).isNullOrEmpty()
        return sIsMiuiDevice!!
    }
    
    /**
     * 检测是否为澎湃系统（HyperOS）
     * HyperOS 检测策略（按优先级）：
     * 1. 读取 ro.mi.os.version 属性（HyperOS 特有）
     * 2. 读取 ro.miui.ui.version.name 是否包含 "OS"（HyperOS使用 OS2.0.xxx 格式）
     * 3. Build.DISPLAY 或 Build.VERSION.INCREMENTAL 是否包含 "HyperOS" 或 "OS2"
     * 4. MIUI版本 >= 14 且 Android >= 14 判定为 HyperOS
     */
    fun isHyperOsDevice(): Boolean {
        if (sIsHyperOsDevice != null) return sIsHyperOsDevice!!
        if (!isMiuiDevice()) {
            sIsHyperOsDevice = false
            return false
        }
        
        // 策略1: HyperOS 特有的系统属性
        val hyperOsVersion = getSystemProperty(KEY_HYPEROS_VERSION)
        if (!hyperOsVersion.isNullOrEmpty()) {
            Log.i(TAG, "HyperOS detected via ro.mi.os.version: $hyperOsVersion")
            sIsHyperOsDevice = true
            return true
        }
        
        // 策略2: 检查版本名是否包含 OS 格式 (HyperOS 使用 OS2.0.x 而非 Vxxx)
        val versionName = getSystemProperty(KEY_MIUI_VERSION_NAME)
        if (!versionName.isNullOrEmpty()) {
            if (versionName.contains("OS", ignoreCase = true)) {
                Log.i(TAG, "HyperOS detected via version name: $versionName")
                sIsHyperOsDevice = true
                return true
            }
        }
        
        // 策略3: 检查 Build 信息
        val display = Build.DISPLAY ?: ""
        val incremental = Build.VERSION.INCREMENTAL ?: ""
        if (display.contains("HyperOS", ignoreCase = true) ||
            display.contains("OS2.", ignoreCase = true) ||
            incremental.contains("HyperOS", ignoreCase = true) ||
            incremental.contains("OS2.", ignoreCase = true)) {
            Log.i(TAG, "HyperOS detected via Build info: display=$display, incremental=$incremental")
            sIsHyperOsDevice = true
            return true
        }
        
        // 策略4: MIUI 14+ 且 Android 14+ 大概率已升级 HyperOS
        val miuiVersion = getMiuiVersion()
        if (miuiVersion >= 14 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.i(TAG, "HyperOS inferred: MIUI $miuiVersion + Android 14+")
            sIsHyperOsDevice = true
            return true
        }
        
        sIsHyperOsDevice = false
        return false
    }
    
    /**
     * 获取MIUI版本号
     * HyperOS 仍然沿用 MIUI 版本号体系
     */
    fun getMiuiVersion(): Int {
        if (sMiuiVersion != null) return sMiuiVersion!!
        if (!isMiuiDevice()) {
            sMiuiVersion = -1
            return -1
        }
        
        try {
            val versionName = getSystemProperty(KEY_MIUI_VERSION_NAME)
            if (!versionName.isNullOrEmpty()) {
                // MIUI: "V14", "V13" 格式; HyperOS: "OS2.0.xxx" 格式
                val matchV = Regex("V(\\d+)").find(versionName)
                if (matchV != null) {
                    sMiuiVersion = matchV.groupValues[1].toInt()
                    return sMiuiVersion!!
                }
                val matchOS = Regex("OS(\\d+)\\.?(\\d*)").find(versionName)
                if (matchOS != null) {
                    sMiuiVersion = matchOS.groupValues[1].toInt()
                    return sMiuiVersion!!
                }
            }
            
            // 尝试从 version code 获取
            val versionCode = getSystemProperty(KEY_MIUI_VERSION_CODE)
            if (!versionCode.isNullOrEmpty()) {
                sMiuiVersion = versionCode.toIntOrNull() ?: -1
                return sMiuiVersion!!
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MIUI version", e)
        }
        
        sMiuiVersion = -1
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
     * 请求电池优化豁免
     * HyperOS/MIUI 会默认限制后台应用的电池使用，需要通过此方法请求豁免
     */
    fun requestBatteryOptimizationExemption(context: Context): Boolean {
        if (!needsAggressiveOptimization()) {
            return false
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    Log.i(TAG, "Already exempted from battery optimization")
                    return true
                }
                
                // 尝试直接请求豁免
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "Requested battery optimization exemption")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization exemption", e)
        }
        
        return false
    }
    
    /**
     * 检查是否已获得电池优化豁免
     */
    fun isBatteryOptimizationExempted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }
    
    /**
     * 打开 HyperOS 后台联网管理设置
     * 这是最重要的设置 - HyperOS 默认禁止后台应用使用数据网络
     */
    fun openHyperOsNetworkSettings(context: Context): Boolean {
        if (!isHyperOsDevice()) {
            return false
        }
        
        val intents = listOf(
            // HyperOS 联网控制 (主要入口)
            Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.network.NetworkRestrictActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            },
            // 备用：通过应用详情页
            Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        )
        
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Opened HyperOS network settings")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open HyperOS network settings: ${e.message}")
            }
        }
        
        return false
    }
    
    /**
     * 获取 HyperOS 后台下载优化指南文本
     */
    fun getHyperOsOptimizationGuide(): String {
        return buildString {
            appendLine("=== HyperOS 后台下载优化指南 ===")
            appendLine()
            appendLine("1. 【最重要】允许后台联网：")
            appendLine("   设置 → 应用设置 → EHViewer → 联网控制")
            appendLine("   → 确保 WLAN 和移动数据的「后台联网」都已开启")
            appendLine()
            appendLine("2. 关闭省电限制：")
            appendLine("   设置 → 应用设置 → EHViewer → 省电策略")
            appendLine("   → 选择「无限制」")
            appendLine()
            appendLine("3. 允许自启动：")
            appendLine("   安全中心 → 自启动管理 → 允许 EHViewer")
            appendLine()
            appendLine("4. 锁定后台：")
            appendLine("   进入最近任务 → 长按 EHViewer 卡片")
            appendLine("   → 点击锁图标锁定")
            appendLine()
            appendLine("5. 关闭 WiFi 省电模式：")
            appendLine("   设置 → WLAN → 高级设置")
            appendLine("   → 关闭「WLAN 省电模式」")
            appendLine()
            appendLine("注意：HyperOS 对后台网络管控非常严格，")
            appendLine("以上步骤缺一不可。")
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
