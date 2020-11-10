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

package com.hippo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PackageUtils {

    private static final String TAG = PackageUtils.class.getSimpleName();

    public static String getSignature(Context context, String packageName) {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            Signature[] ss = pi.signatures;
            if (ss != null && ss.length >= 1) {
                return computeSHA1(ss[0].toByteArray());
            } else {
                Log.e(TAG, "Can't find signature in package " + packageName);
            }
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Log.e(TAG, "Can't find package " + packageName, e);
        }
        return null;
    }

    /**
     * @return looks like A1:43:6B:34... or null
     */
    public static String computeSHA1(final byte[] certRaw) {
        StringBuilder sb = new StringBuilder(59);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA1");
            byte[] sha1 = md.digest(certRaw);
            int length = sha1.length;
            for (int i = 0; i  < length; i++) {
                if (i != 0) {
                    sb.append(':');
                }
                byte b = sha1[i];
                String appendStr = Integer.toString(b & 0xff, 16);
                if (appendStr.length() == 1) {
                    sb.append(0);
                }
                sb.append(appendStr);
            }

            return sb.toString().toUpperCase();
        }
        catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, "Can't final Algorithm SHA1", ex);
            return null;
        }
    }
}
