package com.hippo.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object AndroidUtil {

    private lateinit var contentResolver: ContentResolver
    private var packageInfo: PackageInfo? = null

    // Fetch the Android ID while suppressing lint warning about hardware IDs
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String? {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun getVersion(context: Context): String? {
        initInfo(context)
        packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo?.versionName // 如 "1.2.3"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode
        } else {
            packageInfo?.versionCode?.toLong()
        }
        return versionName
    }
    
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return "NONE"
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "NONE"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) -> "LOWPAN"
            else -> "UNKNOWN"
        }
    }

    private fun initInfo(context: Context){
        if (packageInfo!=null) return
        packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    }

}