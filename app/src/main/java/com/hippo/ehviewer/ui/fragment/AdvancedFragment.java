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

package com.hippo.ehviewer.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.wifi.WiFiClientActivity;
import com.hippo.ehviewer.ui.wifi.WiFiServerActivity;
import com.hippo.util.LogCat;
import com.hippo.util.ReadableTime;

import java.io.File;
import java.util.Arrays;

public class AdvancedFragment extends PreferenceFragment
        implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    private static final String KEY_DUMP_LOGCAT = "dump_logcat";
    private static final String KEY_CLEAR_MEMORY_CACHE = "clear_memory_cache";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_IMPORT_DATA = "import_data";
    private static final String KEY_WIFI_SERVER = "wifi_server";
    private static final String KEY_WIFI_CLIENT = "wifi_client";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.advanced_settings);

        Preference dumpLogcat = findPreference(KEY_DUMP_LOGCAT);
        Preference clearMemoryCache = findPreference(KEY_CLEAR_MEMORY_CACHE);
        Preference appLanguage = findPreference(KEY_APP_LANGUAGE);
        Preference importData = findPreference(KEY_IMPORT_DATA);
        Preference socketData = findPreference(KEY_WIFI_SERVER);
        Preference clientData = findPreference(KEY_WIFI_CLIENT);

        dumpLogcat.setOnPreferenceClickListener(this);
        clearMemoryCache.setOnPreferenceClickListener(this);
        importData.setOnPreferenceClickListener(this);
        socketData.setOnPreferenceClickListener(this);
        clientData.setOnPreferenceClickListener(this);

        appLanguage.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case KEY_DUMP_LOGCAT:
                return dumpLogcat();
            case KEY_CLEAR_MEMORY_CACHE:
                return clearMemoryCache();
            case KEY_IMPORT_DATA:
                importData(getActivity());
                getActivity().setResult(Activity.RESULT_OK);
                return true;
            case KEY_WIFI_SERVER:
                return gotoWiFiServerActivity();
            case KEY_WIFI_CLIENT:
                return gotoWiFiClientActivity();
            default:
                return false;
        }
    }

    private boolean gotoWiFiClientActivity() {
        Activity activity = getActivity();
        Intent intent = new Intent(activity, WiFiClientActivity.class);
        activity.startActivity(intent);
        return false;
    }

    private boolean gotoWiFiServerActivity() {
        Activity activity = getActivity();
        Intent intent = new Intent(activity, WiFiServerActivity.class);
        activity.startActivity(intent);
        return false;
    }

    private boolean clearMemoryCache() {
        ((EhApplication) getActivity().getApplication()).clearMemoryCache();
        Runtime.getRuntime().gc();
        return false;
    }

    private boolean dumpLogcat() {
        boolean ok;
        File file = null;
        File dir = AppConfig.getExternalLogcatDir();
        if (dir != null) {
            file = new File(dir, "logcat-" + ReadableTime.getFilenamableTime(System.currentTimeMillis()) + ".txt");
            ok = LogCat.save(file);
        } else {
            ok = false;
        }
        Resources resources = getResources();
        Toast.makeText(getActivity(),
                ok ? resources.getString(R.string.settings_advanced_dump_logcat_to, file.getPath()) :
                        resources.getString(R.string.settings_advanced_dump_logcat_failed), Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean importData(final Context context) {
        final File dir = AppConfig.getExternalDataDir();
        if (null == dir) {
            Toast.makeText(context, R.string.cant_get_data_dir, Toast.LENGTH_SHORT).show();
            return false;
        }
        final String[] files = dir.list();
        if (null == files || files.length <= 0) {
            Toast.makeText(context, R.string.cant_find_any_data, Toast.LENGTH_SHORT).show();
            return false;
        }
        Arrays.sort(files);
        new AlertDialog.Builder(context).setItems(files, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File file = new File(dir, files[which]);
                String error = EhDB.importDB(context, file);
                if (null == error) {
                    error = context.getString(R.string.settings_advanced_import_data_successfully);
                }
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
        }).show();
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (KEY_APP_LANGUAGE.equals(key)) {
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        }
        return false;
    }
}
