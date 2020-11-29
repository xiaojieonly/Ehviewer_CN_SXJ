/*
 * Copyright 2019 Hippo Seven
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

package com.hippo.ehviewer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import androidx.annotation.NonNull;
import com.hippo.scene.StageActivity;
import com.hippo.util.PackageUtils;
import com.hippo.util.ReadableTime;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.OSUtils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public final class Crash {
  private Crash() {}

  @NonNull
  private static String avoidNull(String str) {
    return null != str ? str : "null";
  }

  private static void collectInfo(Context context, FileWriter fw) throws IOException {
    try {
      PackageManager pm = context.getPackageManager();
      PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
      if (pi != null) {
        String versionName = pi.versionName == null ? "null" : pi.versionName;
        String versionCode = String.valueOf(pi.versionCode);
        fw.write("======== PackageInfo ========\r\n");
        fw.write("PackageName=");fw.write(pi.packageName);fw.write("\r\n");
        fw.write("VersionName=");fw.write(versionName);fw.write("\r\n");
        fw.write("VersionCode=");fw.write(versionCode);fw.write("\r\n");
        String signature = PackageUtils.getSignature(context, pi.packageName);
        fw.write("Signature=");fw.write(null != signature ? signature : "null");fw.write("\r\n");
        fw.write("\r\n");
      }
    } catch (PackageManager.NameNotFoundException e) {
      fw.write("======== PackageInfo ========\r\n");
      fw.write("Can't get package information\r\n");
      fw.write("\r\n");
    }

    // Runtime
    String topActivityClazzName = "null";
    String topSceneClazzName = "null";
    try {
      Activity topActivity = ((EhApplication) context.getApplicationContext()).getTopActivity();
      if (null != topActivity) {
        topActivityClazzName = topActivity.getClass().getName();
        if (topActivity instanceof StageActivity) {
          Class<?> clazz = ((StageActivity) topActivity).getTopSceneClass();
          if (clazz != null) {
            topSceneClazzName = clazz.getName();
          }
        }
      }
    } catch (Throwable e) {
      // Ignore
    }
    fw.write("======== Runtime ========\r\n");
    fw.write("TopActivity=");fw.write(avoidNull(topActivityClazzName));fw.write("\r\n");
    fw.write("TopScene=");fw.write(avoidNull(topSceneClazzName));fw.write("\r\n");
    fw.write("\r\n");

    // Device info
    fw.write("======== DeviceInfo ========\r\n");
    fw.write("BOARD=");fw.write(Build.BOARD);fw.write("\r\n");
    fw.write("BOOTLOADER=");fw.write(Build.BOOTLOADER);fw.write("\r\n");
    fw.write("CPU_ABI=");fw.write(Build.CPU_ABI);fw.write("\r\n");
    fw.write("CPU_ABI2=");fw.write(Build.CPU_ABI2);fw.write("\r\n");
    fw.write("DEVICE=");fw.write(Build.DEVICE);fw.write("\r\n");
    fw.write("DISPLAY=");fw.write(Build.DISPLAY);fw.write("\r\n");
    fw.write("FINGERPRINT=");fw.write(Build.FINGERPRINT);fw.write("\r\n");
    fw.write("HARDWARE=");fw.write(Build.HARDWARE);fw.write("\r\n");
    fw.write("HOST=");fw.write(Build.HOST);fw.write("\r\n");
    fw.write("ID=");fw.write(Build.ID);fw.write("\r\n");
    fw.write("MANUFACTURER=");fw.write(Build.MANUFACTURER);fw.write("\r\n");
    fw.write("MODEL=");fw.write(Build.MODEL);fw.write("\r\n");
    fw.write("PRODUCT=");fw.write(Build.PRODUCT);fw.write("\r\n");
    fw.write("RADIO=");fw.write(Build.getRadioVersion());fw.write("\r\n");
    fw.write("SERIAL=");fw.write(Build.SERIAL);fw.write("\r\n");
    fw.write("TAGS=");fw.write(Build.TAGS);fw.write("\r\n");
    fw.write("TYPE=");fw.write(Build.TYPE);fw.write("\r\n");
    fw.write("USER=");fw.write(Build.USER);fw.write("\r\n");
    fw.write("CODENAME=");fw.write(Build.VERSION.CODENAME);fw.write("\r\n");
    fw.write("INCREMENTAL=");fw.write(Build.VERSION.INCREMENTAL);fw.write("\r\n");
    fw.write("RELEASE=");fw.write(Build.VERSION.RELEASE);fw.write("\r\n");
    fw.write("SDK=");fw.write(Integer.toString(Build.VERSION.SDK_INT));fw.write("\r\n");
    fw.write("MEMORY=");fw.write(
        FileUtils.humanReadableByteCount(OSUtils.getAppAllocatedMemory(), false));fw.write("\r\n");
    fw.write("MEMORY_NATIVE=");fw.write(FileUtils.humanReadableByteCount(Debug.getNativeHeapAllocatedSize(), false));fw.write("\r\n");
    fw.write("MEMORY_MAX=");fw.write(FileUtils.humanReadableByteCount(OSUtils.getAppMaxMemory(), false));fw.write("\r\n");
    fw.write("MEMORY_TOTAL=");fw.write(FileUtils.humanReadableByteCount(OSUtils.getTotalMemory(), false));fw.write("\r\n");
    fw.write("\r\n");
  }

  private static void getThrowableInfo(Throwable t, FileWriter fw) {
    PrintWriter printWriter = new PrintWriter(fw);
    t.printStackTrace(printWriter);
    Throwable cause = t.getCause();
    while (cause != null) {
      cause.printStackTrace(printWriter);
      cause = cause.getCause();
    }
  }

  public static void saveCrashLog(Context context, Throwable t) {
    File dir = AppConfig.getExternalCrashDir();
    if (dir == null) {
      return;
    }

    String nowString = ReadableTime.getFilenamableTime(System.currentTimeMillis());
    String fileName = "crash-" + nowString + ".log";
    File file = new File(dir, fileName);

    FileWriter fw = null;
    try {
      fw = new FileWriter(file);
      fw.write("TIME=");fw.write(nowString);fw.write("\r\n");
      fw.write("\r\n");
      collectInfo(context, fw);
      fw.write("======== CrashInfo ========\r\n");
      getThrowableInfo(t, fw);
      fw.write("\r\n");
      fw.flush();
    } catch (Exception e) {
      file.delete();
    } finally {
      IOUtils.closeQuietly(fw);
    }
  }
}
