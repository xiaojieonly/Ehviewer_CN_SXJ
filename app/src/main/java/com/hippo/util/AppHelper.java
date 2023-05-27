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

import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.VpnManager;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.R;

public class AppHelper {

    public static boolean sendEmail(@NonNull Activity from, @NonNull String address, @Nullable String subject, @Nullable String text) {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:" + address));
        if (subject != null) {
            i.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        if (text != null) {
            i.putExtra(Intent.EXTRA_TEXT, text);
        }
        try {
            from.startActivity(i);
            return true;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(from, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static boolean share(@NonNull Activity from, String text) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        Intent chooser = Intent.createChooser(sendIntent, from.getString(R.string.share));
        try {
            from.startActivity(chooser);
            return true;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(from, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static void showSoftInput(Context context, View view) {
        showSoftInput(context, view, true);
    }

    public static void showSoftInput(Context context, View view, boolean requestFocus) {
        if (requestFocus) {
            view.requestFocus();
        }
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Service.INPUT_METHOD_SERVICE);
        imm.showSoftInput(view, 0);
    }

    public static void hideSoftInput(Activity activity) {
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void hideSoftInput(Dialog dialog) {
        View view = dialog.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) dialog.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void copyPlainText(String data, Context context) {
        // 获取系统剪贴板
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建一个剪贴数据集，包含一个普通文本数据条目（需要复制的数据）,其他的还有
        // newHtmlText、
        // newIntent、
        // newUri、
        // newRawUri
        ClipData clipData = ClipData.newPlainText(null, data);
        // 把数据集设置（复制）到剪贴板
        clipboard.setPrimaryClip(clipData);
    }

    public static boolean checkVPN(Context context) {
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        Network network = connectivityManager.getActiveNetwork();
        //don't know why always returns null:
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
        boolean result = networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN;
        if (result) {
            Toast.makeText(context, R.string.network_remind, Toast.LENGTH_LONG).show();
        }
        return result;
    }

    private boolean isWifiProxy(Context context) {
        String proxyAddress;
        int proxyPort;
        proxyAddress = System.getProperty("http.proxyHost");
        String portStr = System.getProperty("http.proxyPort");
        proxyPort = Integer.parseInt((portStr != null ? portStr : "-1"));
        return (!TextUtils.isEmpty(proxyAddress)) && (proxyPort != -1);
    }
}
