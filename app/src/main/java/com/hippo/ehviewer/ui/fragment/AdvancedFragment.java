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
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.DirPickerActivity;
import com.hippo.unifile.UniFile;
import com.hippo.ehviewer.ui.wifi.WiFiClientActivity;
import com.hippo.ehviewer.ui.wifi.WiFiServerActivity;
import com.hippo.ehviewer.ui.task.BackgroundTaskActivity;
import com.hippo.ehviewer.ui.transfer.TransferActivity;
import com.hippo.ehviewer.ui.NetworkDiagnosticActivity;
import com.hippo.ehviewer.ui.local.LocalGalleryActivity;
import com.hippo.ehviewer.widget.ProgressHelper;
import com.hippo.util.LogCat;
import com.hippo.util.ReadableTime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class AdvancedFragment extends BasePreferenceFragmentCompat
        implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    public static final int DB_LOADING = 0;
    public static final int DB_LOAD_FINISH = 1;

    public static final String LOADING_STATUS = "loading_status";
    public static final String LOADING_PROGRESS = "loading_progress";

    private static final String KEY_DUMP_LOGCAT = "dump_logcat";
    private static final String KEY_EXPORT_DATABASE = "export_database";
    private static final String KEY_CLEAR_MEMORY_CACHE = "clear_memory_cache";
    private static final String KEY_EXPORT_PATH = "export_path";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_IMPORT_DATA = "import_data";
    private static final String KEY_WIFI_SERVER = "wifi_server";
    private static final String KEY_WIFI_CLIENT = "wifi_client";
    private static final String KEY_BACKGROUND_TASKS = "background_tasks";
    private static final String KEY_TRANSFER_SERVICE = "transfer_service";
    private static final String KEY_NETWORK_DIAGNOSTIC = "network_diagnostic";
    private static final String KEY_USER_AGENT = "user_agent";
    private static final String KEY_LOCAL_GALLERY = "local_gallery";
    private static final String KEY_RECYCLE_BIN = "recycle_bin";

    public static final int REQUEST_CODE_PICK_EXPORT_DIR = 10;
    private static final String TAG = "AdvancedFragment";

    private final DbSyncHandle dbSyncHandle = new DbSyncHandle(Looper.getMainLooper());

    private Context context;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        context = getContext();
        addPreferencesFromResource(R.xml.advanced_settings);

        Preference dumpLogcat = findPreference(KEY_DUMP_LOGCAT);
        Preference exportDatabase = findPreference(KEY_EXPORT_DATABASE);
        Preference clearMemoryCache = findPreference(KEY_CLEAR_MEMORY_CACHE);
        Preference exportPath = findPreference(KEY_EXPORT_PATH);
        Preference appLanguage = findPreference(KEY_APP_LANGUAGE);
        Preference importData = findPreference(KEY_IMPORT_DATA);
        Preference socketData = findPreference(KEY_WIFI_SERVER);
        Preference clientData = findPreference(KEY_WIFI_CLIENT);
        Preference backgroundTasks = findPreference(KEY_BACKGROUND_TASKS);
        Preference transferService = findPreference(KEY_TRANSFER_SERVICE);
        Preference networkDiagnostic = findPreference(KEY_NETWORK_DIAGNOSTIC);
        Preference userAgent = findPreference(KEY_USER_AGENT);
        Preference localGallery = findPreference(KEY_LOCAL_GALLERY);
        Preference recycleBin = findPreference(KEY_RECYCLE_BIN);

        dumpLogcat.setOnPreferenceClickListener(this);
        if (exportDatabase != null) {
            exportDatabase.setOnPreferenceClickListener(this);
        }
        clearMemoryCache.setOnPreferenceClickListener(this);
        if (exportPath != null) {
            exportPath.setOnPreferenceClickListener(this);
            UniFile exportDir = Settings.getExportLocation();
            if (exportDir != null && exportDir.getUri() != null) {
                exportPath.setSummary(exportDir.getUri().toString());
            }
        }
        importData.setOnPreferenceClickListener(this);
        socketData.setOnPreferenceClickListener(this);
        clientData.setOnPreferenceClickListener(this);
        backgroundTasks.setOnPreferenceClickListener(this);
        transferService.setOnPreferenceClickListener(this);
        networkDiagnostic.setOnPreferenceClickListener(this);
        userAgent.setOnPreferenceClickListener(this);
        localGallery.setOnPreferenceClickListener(this);
        if (recycleBin != null) {
            recycleBin.setOnPreferenceClickListener(this);
        }

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
            case KEY_EXPORT_DATABASE:
                return exportDatabase();
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
            case KEY_BACKGROUND_TASKS:
                return gotoBackgroundTaskActivity();
            case KEY_TRANSFER_SERVICE:
                return gotoTransferActivity();
            case KEY_NETWORK_DIAGNOSTIC:
                return gotoNetworkDiagnosticActivity();
            case KEY_USER_AGENT:
                return showUserAgentDialog();
            case KEY_EXPORT_PATH:
                return openExportDirPicker();
            case KEY_LOCAL_GALLERY:
                return gotoLocalGalleryActivity();
            case KEY_RECYCLE_BIN:
                return gotoRecycleBinActivity();
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

    private boolean gotoBackgroundTaskActivity() {
        Activity activity = getActivity();
        BackgroundTaskActivity.start(activity);
        return true;
    }

    private boolean gotoTransferActivity() {
        Activity activity = getActivity();
        Intent intent = new Intent(activity, TransferActivity.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean gotoNetworkDiagnosticActivity() {
        Activity activity = getActivity();
        Intent intent = new Intent(activity, NetworkDiagnosticActivity.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean gotoLocalGalleryActivity() {
        Activity activity = getActivity();
        Intent intent = new Intent(activity, LocalGalleryActivity.class);
        activity.startActivity(intent);
        return true;
    }

    private boolean gotoRecycleBinActivity() {
        Activity activity = getActivity();
        LocalGalleryActivity.startRecycleBin(activity);
        return true;
    }

    private boolean openExportDirPicker() {
        Activity activity = getActivity();
        if (activity == null) {
            return false;
        }

        UniFile uniFile = Settings.getExportLocation();
        Intent intent = new Intent(activity, DirPickerActivity.class);
        if (uniFile != null && uniFile.getUri() != null) {
            intent.putExtra(DirPickerActivity.KEY_FILE_URI, uniFile.getUri());
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_EXPORT_DIR);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_EXPORT_DIR && resultCode == Activity.RESULT_OK && data != null) {
            UniFile uniFile = UniFile.fromUri(getContext(), data.getData());
            if (uniFile != null) {
                Settings.putExportLocation(uniFile);
                Preference exportPath = findPreference(KEY_EXPORT_PATH);
                if (exportPath != null && uniFile.getUri() != null) {
                    exportPath.setSummary(uniFile.getUri().toString());
                }
                String path = uniFile.getUri() != null ? uniFile.getUri().toString() : null;
                if (path != null) {
                    Toast.makeText(getContext(), getString(R.string.settings_advanced_export_path_set, path), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private boolean showUserAgentDialog() {
        Context context = getContext();
        if (context == null) return false;
        
        // 创建输入框
        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setText(com.hippo.ehviewer.Settings.getUserAgent());
        editText.setSingleLine(false);
        editText.setHorizontallyScrolling(false);
        editText.setMinLines(3);
        editText.setMaxLines(6);
        editText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editText.setScroller(new android.widget.Scroller(context));
        editText.setVerticalScrollBarEnabled(true);
        
        // 创建对话框
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_advanced_user_agent)
                .setView(editText)
                .setPositiveButton(R.string.settings_advanced_user_agent_restore_default, (dialog, which) -> {
                    // 恢复默认值
                    com.hippo.ehviewer.Settings.putUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                    Toast.makeText(context, R.string.settings_advanced_user_agent_restored, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.settings_advanced_user_agent_save, (dialog, which) -> {
                    // 保存用户输入的值
                    String userAgent = editText.getText().toString().trim();
                    if (!userAgent.isEmpty()) {
                        com.hippo.ehviewer.Settings.putUserAgent(userAgent);
                        Toast.makeText(context, R.string.settings_advanced_user_agent_saved, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, R.string.settings_advanced_user_agent_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
        
        return true;
    }

    private boolean clearMemoryCache() {
        ((EhApplication) getActivity().getApplication()).clearMemoryCache();
        Runtime.getRuntime().gc();
        return false;
    }

    private boolean dumpLogcat() {
        com.hippo.ehviewer.task.DumpLogcatTask task =
                new com.hippo.ehviewer.task.DumpLogcatTask(requireContext());
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        Toast.makeText(requireContext(), R.string.settings_advanced_dump_logcat_started, Toast.LENGTH_SHORT).show();
        return true;
    }

    private boolean exportDatabase() {
        com.hippo.ehviewer.task.ExportDatabaseTask task =
                new com.hippo.ehviewer.task.ExportDatabaseTask(requireContext());
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        Toast.makeText(requireContext(), R.string.settings_advanced_export_database_started, Toast.LENGTH_SHORT).show();
        return true;
    }

    private boolean importData(final Context context) {
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
        new AlertDialog.Builder(context).setItems(files, (dialog, which) -> {
            dialog.dismiss();
            File file = new File(dir, files[which]);
            com.hippo.ehviewer.task.impl.ImportDataTask task =
                    new com.hippo.ehviewer.task.impl.ImportDataTask(context, file);
            com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
            Toast.makeText(context, R.string.settings_advanced_import_data_started, Toast.LENGTH_SHORT).show();
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

    private class DbSyncHandle extends Handler {
        public DbSyncHandle(Looper mainLooper) {
            super(mainLooper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Bundle data = msg.getData();
            int state = data.getInt(LOADING_STATUS);
            if (state == DB_LOAD_FINISH){
                ProgressHelper.dismissDialog();
                String error = data.getString("error");
                if (context == null) {
                    return;
                }
                if (null == error) {
                    error = context.getString(R.string.settings_advanced_import_data_successfully);
                }

                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            } else if (state == DB_LOADING) {
                ProgressHelper.setProgress(data.getInt(LOADING_PROGRESS,0));
            }

        }
    }
}
