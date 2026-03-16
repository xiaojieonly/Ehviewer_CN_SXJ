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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.hippo.util.ExceptionUtils.throwIfFatal
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

object PackageUtils {
    private val TAG: String = PackageUtils::class.java.getSimpleName()

    @JvmStatic
    fun getSignature(context: Context, packageName: String): String? {
        try {
            @SuppressLint("PackageManagerGetSignatures") val pi = context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val ss = pi.signatures
            if (ss != null && ss.size >= 1) {
                return computeSHA1(ss[0]!!.toByteArray())
            } else {
                Log.e(TAG, "Can't find signature in package $packageName")
            }
        } catch (e: Throwable) {
            throwIfFatal(e)
            Log.e(TAG, "Can't find package $packageName", e)
        }
        return null
    }

    /**
     * @return looks like A1:43:6B:34... or null
     */
    fun computeSHA1(certRaw: ByteArray): String? {
        val sb = StringBuilder(59)
        val md: MessageDigest?
        try {
            md = MessageDigest.getInstance("SHA1")
            val sha1 = md.digest(certRaw)
            val length = sha1.size
            for (i in 0..<length) {
                if (i != 0) {
                    sb.append(':')
                }
                val b = sha1[i]
                val appendStr = (b.toInt() and 0xff).toString(16)
                if (appendStr.length == 1) {
                    sb.append(0)
                }
                sb.append(appendStr)
            }

            return sb.toString().uppercase(Locale.getDefault())
        } catch (ex: NoSuchAlgorithmException) {
            Log.e(TAG, "Can't final Algorithm SHA1", ex)
            return null
        }
    }
}
