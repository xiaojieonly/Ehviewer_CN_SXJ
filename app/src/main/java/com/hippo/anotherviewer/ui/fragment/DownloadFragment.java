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

package com.hippo.anotherviewer.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.SiteDB;
import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.download.DownloadManager;
import com.hippo.anotherviewer.smb.SmbBackupManager;
import com.hippo.anotherviewer.smb.SmbBackupService;
import com.hippo.anotherviewer.smb.SmbBackupSettings;
import com.hippo.anotherviewer.smb.SmbConfig;
import com.hippo.anotherviewer.smb.SmbConnection;
import com.hippo.anotherviewer.smb.SmbLoginMode;
import com.hippo.anotherviewer.smb.SmbSettings;
import com.hippo.anotherviewer.smb.SmbSyncEngine;
import com.hippo.anotherviewer.ui.CommonOperations;
import com.hippo.anotherviewer.ui.DirPickerActivity;
import com.hippo.unifile.SmbUri;
import com.hippo.unifile.SmbUriHandler;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.yorozuya.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    public static final int REQUEST_CODE_PICK_IMAGE_DIR = 0;
    public static final int REQUEST_CODE_PICK_IMAGE_DIR_L = 1;
    private static final int REQUEST_CODE_PICK_DOWNLOAD_IMPORT_FILE = 2;
    public static final String KEY_DOWNLOAD_LOCATION = "download_location";
    public static final String KEY_EXPORT_DOWNLOAD_ITEMS = "export_download_items";
    public static final String KEY_IMPORT_DOWNLOAD_ITEMS = "import_download_items";
    public static final String KEY_CLEAN_INVALID_DOWNLOAD = "clean_invalid_download";
    public static final String KEY_SMB_BACKUP_ENABLED = "smb_backup_enabled";
    public static final String KEY_SMB_BACKUP_CONFIGURE = "smb_backup_configure";
    public static final String KEY_SMB_BACKUP_SYNC_ALL = "smb_backup_sync_all";
    public static final String KEY_SMB_BACKUP_AGGRESSIVE = "smb_backup_aggressive";

    @Nullable
    private Preference mDownloadLocation;
    @Nullable
    private Preference mSmbBackupConfigure;
    @Nullable
    private Preference mSmbBackupSyncAll;
    @Nullable
    private com.hippo.preference.SwitchPreference mSmbBackupEnabled;
    @Nullable
    private ExecutorService mExecutor;
    private Handler mMainHandler;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);

        mExecutor = Executors.newCachedThreadPool();
        mMainHandler = new Handler(Looper.getMainLooper());

        Preference mediaScan = findPreference(Settings.KEY_MEDIA_SCAN);
        Preference downloadThread = findPreference("download_thread");
        Preference imageResolution = findPreference(Settings.KEY_IMAGE_RESOLUTION);
        Preference downloadTimeout = findPreference(Settings.KEY_DOWNLOAD_TIMEOUT);
        mDownloadLocation = findPreference(KEY_DOWNLOAD_LOCATION);
        Preference exportDownloadItems = findPreference(KEY_EXPORT_DOWNLOAD_ITEMS);
        Preference importDownloadItems = findPreference(KEY_IMPORT_DOWNLOAD_ITEMS);
        Preference cleanInvalidDownload = findPreference(KEY_CLEAN_INVALID_DOWNLOAD);
        Preference preloadImage = findPreference("preload_image");
        Preference imageResolutionPref = findPreference(Settings.KEY_IMAGE_RESOLUTION);
        mSmbBackupEnabled = findPreference(KEY_SMB_BACKUP_ENABLED);
        mSmbBackupConfigure = findPreference(KEY_SMB_BACKUP_CONFIGURE);
        mSmbBackupSyncAll = findPreference(KEY_SMB_BACKUP_SYNC_ALL);

        onUpdateDownloadLocation();

        // Initialize summaries with current settings
        if (downloadThread != null) {
            downloadThread.setSummary(getString(R.string.settings_download_multi_thread_download_summary, String.valueOf(Settings.getMultiThreadDownload())));
        }
        if (imageResolution != null) {
            imageResolution.setSummary(getString(R.string.settings_download_image_resolution_summary, Settings.getImageResolution()));
        }
        if (downloadTimeout != null) {
            String timeoutStr = Settings.getDownloadTimeout() == 0 ? getString(R.string.download_timeout_unlimited) : String.valueOf(Settings.getDownloadTimeout());
            downloadTimeout.setSummary(getString(R.string.settings_download_timeout_summary, timeoutStr));
        }
        if(preloadImage != null){
            preloadImage.setSummary(getString(R.string.settings_download_preload_image_summary, String.valueOf(Settings.getPreloadImage())));
        }
        if(imageResolutionPref != null){
            imageResolutionPref.setSummary(getString(R.string.settings_download_image_resolution_summary, Settings.getImageResolution()));
        }


        if (mediaScan != null) {
            mediaScan.setOnPreferenceChangeListener(this);
        }
        if (imageResolution != null) {
            imageResolution.setOnPreferenceChangeListener(this);
        }
        if (downloadTimeout != null) {
            downloadTimeout.setOnPreferenceChangeListener(this);
        }

        if (mDownloadLocation != null) {
            mDownloadLocation.setOnPreferenceClickListener(this);
        }
        if (exportDownloadItems != null) {
            exportDownloadItems.setOnPreferenceClickListener(this);
        }
        if (importDownloadItems != null) {
            importDownloadItems.setOnPreferenceClickListener(this);
        }
        if (cleanInvalidDownload != null) {
            cleanInvalidDownload.setOnPreferenceClickListener(this);
        }

        updateSmbBackupSummary();

        if (mSmbBackupEnabled != null) {
            mSmbBackupEnabled.setOnPreferenceChangeListener(this);
        }
        if (mSmbBackupConfigure != null) {
            mSmbBackupConfigure.setOnPreferenceClickListener(this);
        }
        if (mSmbBackupSyncAll != null) {
            mSmbBackupSyncAll.setOnPreferenceClickListener(this);
        }

        UniFile downloadLoc = Settings.getDownloadLocation();
        if (downloadLoc != null && downloadLoc.getUri() != null
                && "smb".equals(downloadLoc.getUri().getScheme())) {
            Toast.makeText(requireActivity(), R.string.settings_download_smb_reminder, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDownloadLocation = null;
        mSmbBackupConfigure = null;
        mSmbBackupSyncAll = null;
        mSmbBackupEnabled = null;
    }

    public void onUpdateDownloadLocation() {
        UniFile file = Settings.getDownloadLocation();
        if (mDownloadLocation != null) {
            if (file != null) {
                mDownloadLocation.setSummary(file.getUri().toString());
            } else {
                mDownloadLocation.setSummary(R.string.settings_download_invalid_download_location);
            }
        }
        
        boolean isSmbDownload = file != null && file.getUri() != null 
                && "smb".equals(file.getUri().getScheme());
        
        SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
        boolean backupEnabled = backupSettings.isEnabled();
        
        if (mSmbBackupEnabled != null) {
            mSmbBackupEnabled.setVisible(!isSmbDownload);
        }
        if (mSmbBackupConfigure != null) {
            mSmbBackupConfigure.setVisible(!isSmbDownload && backupEnabled);
        }
        if (mSmbBackupSyncAll != null) {
            mSmbBackupSyncAll.setVisible(!isSmbDownload && backupEnabled);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_DOWNLOAD_LOCATION.equals(key)) {
            int sdk = Build.VERSION.SDK_INT;
            if (sdk < Build.VERSION_CODES.KITKAT) {
                openDirPicker();
            } else if (sdk < Build.VERSION_CODES.LOLLIPOP) {
                showDirPickerDialogKK();
            } else {
                showDirPickerDialogL();
            }
            return true;
        } else if (KEY_EXPORT_DOWNLOAD_ITEMS.equals(key)) {
            exportDownloadItems();
            return true;
        } else if (KEY_IMPORT_DOWNLOAD_ITEMS.equals(key)) {
            importDownloadItems();
            return true;
        } else if (KEY_CLEAN_INVALID_DOWNLOAD.equals(key)) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_download_clean_invalid_download)
                    .setMessage(R.string.settings_download_clean_invalid_download_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> new CleanInvalidDownloadTask(this).execute())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        } else if (KEY_SMB_BACKUP_CONFIGURE.equals(key)) {
            openSmbBackupPicker();
            return true;
        } else if (KEY_SMB_BACKUP_SYNC_ALL.equals(key)) {
            startSmbBackupSyncAll();
            return true;
        }
        return false;
    }

    private void showDirPickerDialogKK() {
        new AlertDialog.Builder(requireActivity()).setMessage(R.string.settings_download_pick_dir_kk)
                .setPositiveButton(R.string.settings_download_continue, (dialog, which) -> openDirPicker())
                .setNegativeButton(R.string.settings_download_smb, (dialog, which) -> openSmbPicker())
                .show();
    }

    private void showDirPickerDialogL() {
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    openDirPicker();
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    openDirPickerL();
                    break;
            }
        };

        new AlertDialog.Builder(requireActivity()).setMessage(R.string.settings_download_pick_dir_l)
                .setPositiveButton(R.string.settings_download_continue, listener)
                .setNeutralButton(R.string.settings_download_document, listener)
                .setNegativeButton(R.string.settings_download_smb, (dialog, which) -> openSmbPicker())
                .show();
    }

    private void openDirPicker() {
        UniFile uniFile = Settings.getDownloadLocation();
        Intent intent = new Intent(getActivity(), DirPickerActivity.class);
        if (uniFile != null) {
            intent.putExtra(DirPickerActivity.KEY_FILE_URI, uniFile.getUri());
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_DIR);
    }

    private void openDirPickerL() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_DIR_L);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(getActivity(), R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSmbPicker() {
        SmbSettings smbSettings = new SmbSettings(requireContext());
        SmbConfig existing = smbSettings.loadConfig();

        LinearLayout layout = new LinearLayout(requireActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        layout.setPadding(padding, padding, padding, padding);

        EditText host = addSmbField(layout, R.string.settings_download_smb_host,
                existing != null ? existing.getHost() : "");
        EditText port = addSmbField(layout, R.string.settings_download_smb_port,
                existing != null ? String.valueOf(existing.getPort()) : String.valueOf(SmbUri.DEFAULT_PORT));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText username = addSmbField(layout, R.string.settings_download_smb_username,
                existing != null ? existing.getUsername() : "");
        EditText password = addSmbField(layout, R.string.settings_download_smb_password,
                existing != null ? existing.getPassword() : "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_smb)
                .setView(layout)
                .setPositiveButton(R.string.settings_download_smb_connect, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String hostValue = host.getText().toString().trim();
            String portValue = port.getText().toString().trim();
            if (hostValue.isEmpty()) {
                Toast.makeText(requireActivity(), R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            int portNumber;
            try {
                portNumber = portValue.isEmpty() ? SmbUri.DEFAULT_PORT : Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                Toast.makeText(requireActivity(), R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            String userValue = username.getText().toString().trim();
            String passValue = password.getText().toString().trim();
            SmbLoginMode loginMode = userValue.isEmpty() ? SmbLoginMode.ANONYMOUS : SmbLoginMode.PASSWORD;
            new SmbTestTask(DownloadFragment.this, dialog, hostValue, portNumber, loginMode, userValue, passValue, false).execute();
        }));
        dialog.show();
    }

    private EditText addSmbField(ViewGroup parent, int hintRes, String value) {
        EditText editText = new EditText(requireActivity());
        editText.setHint(hintRes);
        editText.setText(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        editText.setLayoutParams(params);
        parent.addView(editText);
        return editText;
    }

    private void exportDownloadItems() {
        List<GalleryInfo> list = SiteApplication.getDownloadManager(requireActivity()).getDownloadInfoList();
        if (list.isEmpty()) {
            Toast.makeText(getActivity(), R.string.settings_download_export_no_items, Toast.LENGTH_SHORT).show();
            return;
        }

        UniFile dir = Settings.getDownloadLocation();
        if (dir == null) {
            Toast.makeText(getActivity(), R.string.settings_download_invalid_download_location, Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        String fileName = "anotherviewer-download-" + sdf.format(new Date()) + ".csv";

        UniFile file = dir.createFile(fileName);
        if (file == null) {
            Toast.makeText(getActivity(), R.string.settings_download_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try (OutputStream os = file.openOutputStream()) {
            os.write(DownloadManager.DOWNLOAD_INFO_HEADER.getBytes(StandardCharsets.UTF_8));
            for (GalleryInfo gi : list) {
                os.write(gi.toCSV().getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(getActivity(), getString(R.string.settings_download_export_succeed, file.getUri().toString()), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getActivity(), R.string.settings_download_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importDownloadItems() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_DOWNLOAD_IMPORT_FILE);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(getActivity(), R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data == null){
            super.onActivityResult(requestCode, resultCode, null);
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_PICK_IMAGE_DIR: {
                if (resultCode == Activity.RESULT_OK) {
                    UniFile uniFile = UniFile.fromUri(getActivity(), data.getData());
                    if (uniFile != null) {
                        Settings.putDownloadLocation(uniFile);
                        onUpdateDownloadLocation();
                    } else {
                        Toast.makeText(getActivity(), R.string.settings_download_cant_get_download_location,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
            case REQUEST_CODE_PICK_IMAGE_DIR_L: {
                if (resultCode == Activity.RESULT_OK) {
                    Uri treeUri = data.getData();
                    if (treeUri != null) {
                        requireActivity().getContentResolver().takePersistableUriPermission(
                                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        UniFile uniFile = UniFile.fromTreeUri(getActivity(), treeUri);
                        if (uniFile != null) {
                            Settings.putDownloadLocation(uniFile);
                            onUpdateDownloadLocation();
                        } else {
                            Toast.makeText(getActivity(), R.string.settings_download_cant_get_download_location,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                break;
            }
            case REQUEST_CODE_PICK_DOWNLOAD_IMPORT_FILE: {
                if (resultCode == Activity.RESULT_OK) {
                    new ImportDownloadTask(this, data.getData()).execute();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (Settings.KEY_MEDIA_SCAN.equals(key)) {
            if (newValue instanceof Boolean) {
                UniFile downloadLocation = Settings.getDownloadLocation();
                if ((Boolean) newValue) {
                    CommonOperations.removeNoMediaFile(downloadLocation);
                } else {
                    CommonOperations.ensureNoMediaFile(downloadLocation);
                }
            }
            return true;
        } else if (Settings.KEY_IMAGE_RESOLUTION.equals(key)) {
            if (newValue instanceof String) {
                Settings.putImageResolution((String) newValue);
            }
            return true;
        }else if (Settings.KEY_DOWNLOAD_TIMEOUT.equals(key)) {
            if (newValue instanceof String) {
                Settings.setDownloadTimeout(toTimeoutTime(newValue));
            }
            return true;
        } else if (KEY_SMB_BACKUP_ENABLED.equals(key)) {
            if (newValue instanceof Boolean) {
                SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
                backupSettings.setEnabled((Boolean) newValue);
                updateSmbBackupSummary();
                onUpdateDownloadLocation();
            }
            return true;
        } else if (KEY_SMB_BACKUP_AGGRESSIVE.equals(key)) {
            if (newValue instanceof Boolean) {
                SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
                backupSettings.setAggressiveMode((Boolean) newValue);
            }
            return true;
        }
        return false;
    }

    private int toTimeoutTime(Object newValue) {
        try{
            return Integer.parseInt(newValue.toString());
        }catch (NumberFormatException e){
            return 0;
        }
    }

    private void updateSmbBackupSummary() {
        SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
        if (mSmbBackupConfigure != null) {
            if (backupSettings.isEnabled()) {
                SmbConfig config = backupSettings.loadConfig();
                if (config != null) {
                    String display = config.getHost() + ":" + config.getPort() + "/" + config.getShare();
                    if (!config.getPath().isEmpty()) {
                        display += "/" + config.getPath();
                    }
                    String summary = getString(R.string.settings_download_smb_backup_configured, display);
                    mSmbBackupConfigure.setSummary(summary);
                } else {
                    mSmbBackupConfigure.setSummary(R.string.settings_download_smb_backup_not_configured);
                }
            } else {
                mSmbBackupConfigure.setSummary(R.string.settings_download_smb_backup_not_configured);
            }
        }
    }

    private void openSmbBackupPicker() {
        SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
        SmbConfig existing = backupSettings.loadConfig();

        LinearLayout layout = new LinearLayout(requireActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        layout.setPadding(padding, padding, padding, padding);

        EditText host = addSmbField(layout, R.string.settings_download_smb_host,
                existing != null ? existing.getHost() : "");
        EditText port = addSmbField(layout, R.string.settings_download_smb_port,
                existing != null ? String.valueOf(existing.getPort()) : String.valueOf(SmbUri.DEFAULT_PORT));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText username = addSmbField(layout, R.string.settings_download_smb_username,
                existing != null ? existing.getUsername() : "");
        EditText password = addSmbField(layout, R.string.settings_download_smb_password,
                existing != null ? existing.getPassword() : "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_smb_backup)
                .setView(layout)
                .setPositiveButton(R.string.settings_download_smb_connect, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String hostValue = host.getText().toString().trim();
            String portValue = port.getText().toString().trim();
            if (hostValue.isEmpty()) {
                Toast.makeText(requireActivity(), R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            int portNumber;
            try {
                portNumber = portValue.isEmpty() ? SmbUri.DEFAULT_PORT : Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                Toast.makeText(requireActivity(), R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            String userValue = username.getText().toString().trim();
            String passValue = password.getText().toString().trim();
            SmbLoginMode loginMode = userValue.isEmpty() ? SmbLoginMode.ANONYMOUS : SmbLoginMode.PASSWORD;
            new SmbTestTask(DownloadFragment.this, dialog, hostValue, portNumber, loginMode, userValue, passValue, true).execute();
        }));
        dialog.show();
    }

    private void showSmbShareDialog(String host, int port, SmbLoginMode loginMode,
            String username, String password, boolean forBackup) {
        SmbConfig existing = null;
        if (forBackup) {
            SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
            existing = backupSettings.loadConfig();
        } else {
            SmbSettings smbSettings = new SmbSettings(requireContext());
            existing = smbSettings.loadConfig();
        }
        String existingShare = existing != null ? existing.getShare() : "";

        LinearLayout layout = new LinearLayout(requireActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        layout.setPadding(padding, padding, padding, padding);

        EditText shareInput = addSmbField(layout, R.string.settings_download_smb_share, existingShare);

        TextView hint = new TextView(requireActivity());
        hint.setText(R.string.smb_picker_step_share);
        hint.setTextSize(12);
        hint.setTextColor(getResources().getColor(android.R.color.darker_gray));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.bottomMargin = padding;
        hint.setLayoutParams(hintParams);
        layout.addView(hint, 0);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(forBackup ? R.string.settings_download_smb_backup : R.string.settings_download_smb)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String shareValue = shareInput.getText().toString().trim();
            if (shareValue.isEmpty()) {
                Toast.makeText(requireActivity(), R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showSmbFolderBrowser(host, port, loginMode, username, password, shareValue, forBackup);
        }));
        dialog.show();
    }

    private void showSmbFolderBrowser(String host, int port, SmbLoginMode loginMode,
            String username, String password, String share, boolean forBackup) {
        browseSmbFolder(host, port, loginMode, username, password, share, "", forBackup);
    }

    private void browseSmbFolder(String host, int port, SmbLoginMode loginMode,
            String username, String password, String share, String currentPath, boolean forBackup) {
        ExecutorService executor = mExecutor;
        Handler handler = mMainHandler;
        if (executor == null || handler == null) return;

        ProgressDialog progress = ProgressDialog.show(requireActivity(), null,
                getString(R.string.settings_download_smb_testing), true, false);
        String pathToShow = currentPath.isEmpty() ? "/" : currentPath;

        executor.execute(() -> {
            List<String> result;
            Throwable failure;
            try {
                SmbConfig cfg = new SmbConfig(host, port, share, currentPath, loginMode,
                        loginMode == SmbLoginMode.PASSWORD ? username : null,
                        loginMode == SmbLoginMode.PASSWORD ? password : null);
                result = new SmbConnection(cfg).listFolders(currentPath);
                failure = null;
            } catch (Throwable e) {
                result = null;
                failure = e;
            }
            final List<String> folders = result;
            final Throwable throwable = failure;
            handler.post(() -> {
                if (progress != null) {
                    try { progress.dismiss(); } catch (Exception ignored) {}
                }
                if (!isAdded()) return;
                if (folders == null) {
                    Toast.makeText(requireActivity(),
                            getSmbErrorMessage(getResources(), throwable),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                java.util.Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);

                java.util.List<CharSequence> itemList = new java.util.ArrayList<>();
                java.util.List<String> pathList = new java.util.ArrayList<>();

                if (!currentPath.isEmpty()) {
                    itemList.add("..");
                    int lastSlash = currentPath.lastIndexOf('/');
                    pathList.add(lastSlash > 0 ? currentPath.substring(0, lastSlash) : "");
                }
                for (String f : folders) {
                    itemList.add(f);
                    pathList.add(currentPath.isEmpty() ? f : currentPath + "/" + f);
                }

                CharSequence[] items = itemList.toArray(new CharSequence[0]);
                String title = "//" + host + ":" + port + "/" + share + pathToShow;
                new AlertDialog.Builder(requireActivity())
                        .setTitle(title)
                        .setItems(items, (dialog, which) -> {
                            String nextPath = pathList.get(which);
                            browseSmbFolder(host, port, loginMode, username, password, share, nextPath, forBackup);
                        })
                        .setPositiveButton(R.string.smb_picker_select_here, (d, w) -> {
                            if (forBackup) {
                                saveSmbBackupConfig(host, port, loginMode, username, password, share, currentPath);
                            } else {
                                saveSmbDownloadLocationConfig(host, port, loginMode, username, password, share, currentPath);
                            }
                        })
                        .setNeutralButton(R.string.smb_picker_new_folder, (d, w) ->
                                showNewFolderDialog(host, port, loginMode, username, password, share, currentPath, forBackup))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void showNewFolderDialog(String host, int port, SmbLoginMode loginMode,
            String username, String password, String share, String currentPath, boolean forBackup) {
        EditText input = new EditText(requireActivity());
        input.setHint(R.string.smb_picker_new_folder_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.smb_picker_new_folder)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String folderName = input.getText().toString().trim();
                    if (folderName.isEmpty()) {
                        Toast.makeText(requireActivity(), R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String newPath = currentPath.isEmpty() ? folderName : currentPath + "/" + folderName;
                    if (forBackup) {
                        saveSmbBackupConfig(host, port, loginMode, username, password, share, newPath);
                    } else {
                        saveSmbDownloadLocationConfig(host, port, loginMode, username, password, share, newPath);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveSmbBackupConfig(String host, int port, SmbLoginMode loginMode,
            String username, String password, String share, String path) {
        SmbConfig config = new SmbConfig(host, port, share, path, loginMode,
                loginMode == SmbLoginMode.PASSWORD ? username : null,
                loginMode == SmbLoginMode.PASSWORD ? password : null);
        SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
        backupSettings.saveConfig(config);
        backupSettings.setEnabled(true);
        updateSmbBackupSummary();
        Toast.makeText(requireActivity(), R.string.settings_download_smb_connected, Toast.LENGTH_SHORT).show();
    }

    private void saveSmbDownloadLocationConfig(String host, int port, SmbLoginMode loginMode,
            String username, String password, String share, String path) {
        SmbConfig config = new SmbConfig(host, port, share, path, loginMode,
                loginMode == SmbLoginMode.PASSWORD ? username : null,
                loginMode == SmbLoginMode.PASSWORD ? password : null);
        new SmbSettings(requireContext()).saveConfig(config);
        UniFile location = new SmbUriHandler().fromUri(requireActivity(), config.toUri().toUri());
        if (location != null) {
            Settings.putDownloadLocation(location);
            onUpdateDownloadLocation();
        }
        Toast.makeText(requireActivity(), R.string.settings_download_smb_connected, Toast.LENGTH_SHORT).show();
    }

    private static String getSmbErrorMessage(Resources res, Throwable throwable) {
        Throwable t = throwable;
        while (t != null) {
            if (t instanceof com.hierynomus.mssmb2.SMBApiException) {
                long status = ((com.hierynomus.mssmb2.SMBApiException) t).getStatusCode();
                if (status == com.hierynomus.mserref.NtStatus.STATUS_LOGON_FAILURE.getValue()
                        || status == com.hierynomus.mserref.NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED.getValue()) {
                    return res.getString(R.string.settings_download_smb_error_auth);
                }
            }
            if (t instanceof java.net.ConnectException
                    || t instanceof java.net.SocketTimeoutException) {
                return res.getString(R.string.settings_download_smb_error_connect);
            }
            t = t.getCause();
        }
        return res.getString(R.string.settings_download_smb_error_generic, throwable.getMessage());
    }

    private static class SmbTestTask {
        private final WeakReference<DownloadFragment> fragmentRef;
        private final AlertDialog dialog;
        private final String host;
        private final int port;
        private final SmbLoginMode loginMode;
        private final String username;
        private final String password;
        private final boolean forBackup;
        private ProgressDialog progress;

        SmbTestTask(DownloadFragment fragment, AlertDialog dialog, String host, int port,
                SmbLoginMode loginMode, String username, String password, boolean forBackup) {
            this.fragmentRef = new WeakReference<>(fragment);
            this.dialog = dialog;
            this.host = host;
            this.port = port;
            this.loginMode = loginMode;
            this.username = username;
            this.password = password;
            this.forBackup = forBackup;
        }

        void execute() {
            DownloadFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;

            progress = ProgressDialog.show(fragment.requireActivity(), null,
                    fragment.getString(R.string.settings_download_smb_testing), true, false);

            executor.execute(() -> {
                Throwable result;
                try {
                    SmbConfig config = new SmbConfig(host, port, "IPC$", "",
                            loginMode,
                            loginMode == SmbLoginMode.PASSWORD ? username : null,
                            loginMode == SmbLoginMode.PASSWORD ? password : null);
                    new SmbConnection(config).testConnection();
                    result = null;
                } catch (Throwable e) {
                    result = e;
                }
                final Throwable error = result;
                handler.post(() -> onPostExecute(error));
            });
        }

        private void onPostExecute(Throwable throwable) {
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            DownloadFragment fragment = fragmentRef.get();
            if (fragment == null || !fragment.isAdded()) return;
            if (throwable == null) {
                dialog.dismiss();
                fragment.showSmbShareDialog(host, port, loginMode, username, password, forBackup);
            } else {
                String errorMessage = getSmbErrorMessage(fragment.getResources(), throwable);
                Toast.makeText(fragment.requireActivity(), errorMessage, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSmbBackupSyncAll() {
        SmbBackupSettings backupSettings = new SmbBackupSettings(requireContext());
        if (!backupSettings.isEnabled() || backupSettings.loadConfig() == null) {
            Toast.makeText(requireActivity(), R.string.settings_download_smb_backup_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_smb_backup_sync_all)
                .setMessage(R.string.settings_download_smb_backup_sync_all_message)
                .setPositiveButton(R.string.settings_download_smb_backup_sync_aggressive, (d, w) -> {
                    new SmbBackupSyncAllTask(this, true).execute();
                })
                .setNegativeButton(R.string.settings_download_smb_backup_sync_normal, (d, w) -> {
                    new SmbBackupSyncAllTask(this, false).execute();
                })
                .show();
    }

    private static class ImportDownloadTask {

        private final WeakReference<DownloadFragment> mFragment;
        private final Uri mUri;
        private ProgressDialog mProgressDialog;
        private Handler mHandler;

        public ImportDownloadTask(DownloadFragment fragment, Uri uri) {
            mFragment = new WeakReference<>(fragment);
            mUri = uri;
        }

        void execute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) {
                return;
            }
            mHandler = handler;

            // onPreExecute equivalent (runs on main thread)
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_import_items);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();

            executor.execute(() -> {
                int result = doInBackground();
                handler.post(() -> onPostExecute(result));
            });
        }

        private int doInBackground() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null || mUri == null) {
                return 0;
            }

            try (InputStream is = fragment.requireActivity().getContentResolver().openInputStream(mUri)) {
                if (is == null) {
                    return 0;
                }
                String content = IOUtils.readString(is, StandardCharsets.UTF_8.name());
                String[] lines = content.split("\n");
                List<GalleryInfo> galleryInfos = new ArrayList<>();
                for (String line : lines) {
                    if (line.startsWith(DownloadManager.DOWNLOAD_INFO_HEADER)) {
                        continue;
                    }
                    GalleryInfo gi = GalleryInfo.fromCSV(line);
                    if (gi != null) {
                        galleryInfos.add(gi);
                    }
                }

                DownloadManager downloadManager = SiteApplication.getDownloadManager(fragment.requireActivity());
                int importCount = 0;
                int total = galleryInfos.size();
                postProgress(0, total);

                for (int i = 0; i < total; i++) {
                    GalleryInfo gi = galleryInfos.get(i);
                    if (downloadManager.getDownloadInfo(gi.gid) == null) {
                        downloadManager.addDownload(gi, null);
                        importCount++;
                    }
                    postProgress(i + 1, total);
                }
                return importCount;
            } catch (IOException e) {
                return 0;
            }
        }

        private void postProgress(int current, int total) {
            if (mHandler != null) {
                mHandler.post(() -> onProgressUpdate(current, total));
            }
        }

        private void onProgressUpdate(int current, int total) {
            if (mProgressDialog != null) {
                mProgressDialog.setMax(total);
                mProgressDialog.setProgress(current);
            }
        }

        private void onPostExecute(int result) {
            DownloadFragment fragment = mFragment.get();
            if (mProgressDialog != null) {
                // 检查 Fragment 是否仍然附加到 Activity，避免在 Activity 销毁后关闭对话框导致崩溃
                if (fragment != null && fragment.isAdded() && fragment.getActivity() != null) {
                    try {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                    } catch (IllegalArgumentException e) {
                        // 对话框已经不再附加到窗口管理器，忽略异常
                        ExceptionUtils.throwIfFatal(e);
                    }
                }
                mProgressDialog = null;
            }
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            if (result > 0) {
                Toast.makeText(fragment.getActivity(), fragment.getString(R.string.settings_download_import_succeed, result), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.getActivity(), R.string.settings_download_import_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class CleanInvalidDownloadTask {

        private final WeakReference<DownloadFragment> mFragment;
        private ProgressDialog mProgressDialog;
        private final List<String> mLogs = new ArrayList<>();
        private Handler mHandler;

        public CleanInvalidDownloadTask(DownloadFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        void execute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) {
                return;
            }
            mHandler = handler;

            // onPreExecute equivalent (runs on main thread)
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_cleaning);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();

            executor.execute(() -> {
                int result = doInBackground();
                handler.post(() -> onPostExecute(result));
            });
        }

        private int doInBackground() {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null || !downloadDir.isDirectory()) {
                return 0;
            }

            UniFile[] files = downloadDir.listFiles();
            if (files == null) {
                return 0;
            }

            int invalidCount = 0;
            int total = files.length;
            postProgress(0, total);

            DownloadManager downloadManager = SiteApplication.getDownloadManager(mFragment.get().requireActivity());

            for (int i = 0; i < total; i++) {
                UniFile dir = files[i];
                postProgress(i + 1, total);

                if (!dir.isDirectory()) {
                    continue;
                }

                UniFile[] subFiles = dir.listFiles();
                if (subFiles == null || subFiles.length == 0) {
                    mLogs.add("Empty directory: " + dir.getName());
                    invalidCount++;
                    dir.delete();
                    continue;
                }

                UniFile anotherViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
                if (anotherViewerFile == null) {
                    mLogs.add("Missing .anotherviewer file: " + dir.getName());
                    invalidCount++;
                    continue;
                }

                try {
                    String content = IOUtils.readString(anotherViewerFile.openInputStream(), StandardCharsets.UTF_8.name());
                    String[] lines = content.split("\n");
                    if (lines.length < 8) {
                        mLogs.add("Invalid .anotherviewer file: " + dir.getName());
                        invalidCount++;
                        // Try to reset if possible
                        long gid;
                        try {
                            gid = Long.parseLong(lines[0]);
                        } catch (NumberFormatException e) {
                            gid = -1;
                        }
                        if (gid != -1) {
                            com.hippo.anotherviewer.dao.DownloadInfo gi = downloadManager.getDownloadInfo(gid);
                            if (gi != null) {
                                gi.state = com.hippo.anotherviewer.dao.DownloadInfo.STATE_NONE;
                                SiteDB.putDownloadInfo(gi);
                            }
                        }
                        continue;
                    }
                    int pageCount = Integer.parseInt(lines[7]);
                    int imageFileCount = 0;
                    for (UniFile subFile : subFiles) {
                        String name = subFile.getName();
                        if (name != null && !name.startsWith(".")) {
                            imageFileCount++;
                        }
                    }

                    if (imageFileCount != pageCount) {
                        mLogs.add("Inconsistent file count: " + dir.getName() + ", expected: " + pageCount + ", actual: " + imageFileCount);
                        invalidCount++;
                        for (UniFile subFile : subFiles) {
                            String name = subFile.getName();
                            if (name != null && !name.equals(DownloadManager.DOWNLOAD_INFO_FILENAME) && !name.startsWith(".")) {
                                subFile.delete();
                            }
                        }
                        // Reset to unfinished state
                        long gid;
                        try {
                            gid = Long.parseLong(lines[0]);
                        } catch (NumberFormatException e) {
                            gid = -1;
                        }
                        if (gid != -1) {
                            com.hippo.anotherviewer.dao.DownloadInfo gi = downloadManager.getDownloadInfo(gid);
                            if (gi != null) {
                                gi.state = com.hippo.anotherviewer.dao.DownloadInfo.STATE_NONE;
                                SiteDB.putDownloadInfo(gi);
                            }
                        }
                    }
                } catch (IOException | NumberFormatException e) {
                    mLogs.add("Error processing directory: " + dir.getName() + " - " + e.getMessage());
                    invalidCount++;
                }
            }

            if (!mLogs.isEmpty()) {
                saveLog();
            }

            return invalidCount;
        }

        private void postProgress(int current, int total) {
            if (mHandler != null) {
                mHandler.post(() -> onProgressUpdate(current, total));
            }
        }

        private void onProgressUpdate(int current, int total) {
            if (mProgressDialog != null) {
                mProgressDialog.setMax(total);
                mProgressDialog.setProgress(current);
            }
        }

        private void onPostExecute(int result) {
            DownloadFragment fragment = mFragment.get();
            if (mProgressDialog != null) {
                // 检查 Fragment 是否仍然附加到 Activity，避免在 Activity 销毁后关闭对话框导致崩溃
                if (fragment != null && fragment.isAdded() && fragment.getActivity() != null) {
                    try {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                    } catch (IllegalArgumentException e) {
                        // 对话框已经不再附加到窗口管理器，忽略异常
                        ExceptionUtils.throwIfFatal(e);
                    }
                }
                mProgressDialog = null;
            }
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            if (result > 0) {
                Toast.makeText(fragment.getActivity(), fragment.getString(R.string.settings_download_clean_invalid_done, result), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.getActivity(), R.string.settings_download_clean_invalid_no_invalid, Toast.LENGTH_SHORT).show();
            }
        }

        private void saveLog() {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
            String fileName = "delfile-" + sdf.format(new Date()) + ".log";
            UniFile logFile = downloadDir.createFile(fileName);
            if (logFile != null) {
                try (OutputStream os = logFile.openOutputStream()) {
                    for (String log : mLogs) {
                        os.write((log + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static final class SmbBackupSyncAllTask {
        private static final String TAG = "SmbBackupSyncAll";

        private final WeakReference<DownloadFragment> fragmentRef;
        private final boolean aggressiveMode;
        private Handler mHandler;
        private ProgressDialog progress;
        private volatile boolean cancelled;
        private String mGalleryName = "";
        private String mFileName = "";
        private int mFileCurrent = 0;
        private int mFileTotal = 0;
        private int mCurrentIndex = 0;
        private int mTotalCount = 0;
        private long mSpeedBps = 0;
        private boolean mBackgroundRequested = false;

        private SmbBackupSyncAllTask(DownloadFragment fragment, boolean aggressiveMode) {
            this.fragmentRef = new WeakReference<>(fragment);
            this.aggressiveMode = aggressiveMode;
        }

        void execute() {
            DownloadFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;
            mHandler = handler;

            // onPreExecute equivalent (runs on main thread)
            progress = new ProgressDialog(fragment.getActivity());
            progress.setTitle(R.string.settings_download_smb_backup_syncing);
            progress.setMessage(fragment.getString(R.string.settings_download_smb_backup_scanning));
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.setButton(DialogInterface.BUTTON_POSITIVE, fragment.getString(R.string.smb_picker_background_backup),
                    (d, w) -> { mBackgroundRequested = true; cancelled = true; });
            progress.setButton(DialogInterface.BUTTON_NEGATIVE, fragment.getString(android.R.string.cancel),
                    (d, w) -> cancelled = true);
            progress.show();

            executor.execute(() -> {
                int[] result = doInBackground();
                handler.post(() -> onPostExecute(result));
            });
        }

        private int[] doInBackground() {
            DownloadFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return new int[]{0, 0};

            SmbBackupSettings backupSettings = new SmbBackupSettings(fragment.requireContext());
            SmbConfig config = backupSettings.loadConfigIfEnabled();
            if (config == null) return new int[]{0, 0};

            UniFile localDir = Settings.getDownloadLocation();
            if (localDir == null || !localDir.isDirectory()) return new int[]{0, 0};
            if (localDir.getUri() != null && "smb".equals(localDir.getUri().getScheme())) return new int[]{0, 0};

            UniFile[] localDirs = localDir.listFiles();
            if (localDirs == null || localDirs.length == 0) return new int[]{0, 0};

            long ramBufferSize = aggressiveMode ? backupSettings.getRamBufferSize(fragment.requireContext()) : 0;
            Log.d(TAG, "Sync started, aggressive=" + aggressiveMode
                    + ", ramBufferSize=" + ramBufferSize
                    + ", totalDirs=" + localDirs.length);

            SmbSyncEngine.Source source = SmbSyncEngine.uniFileSource(localDir);
            SmbSyncEngine.Options options = new SmbSyncEngine.Options();
            options.aggressive = aggressiveMode;
            options.ramBufferSize = ramBufferSize;

            SmbSyncEngine.Callback callback = new SmbSyncEngine.Callback() {
                @Override
                public void onScan(int total) {
                    postProgress(0, total);
                }

                @Override
                public void onGallery(int index, int total, String name) {
                    mGalleryName = name;
                    mFileName = "";
                    mFileCurrent = 0;
                    mFileTotal = 0;
                    mSpeedBps = 0;
                    mCurrentIndex = index;
                    mTotalCount = total;
                    postProgress(index, total);
                }

                @Override
                public void onFile(int fileIndex, int fileTotal, String name) {
                    mFileName = name;
                    mFileCurrent = fileIndex;
                    mFileTotal = fileTotal;
                    postProgress(mCurrentIndex, mTotalCount);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }

                @Override
                public void onSpeed(long bytesPerSecond) {
                    mSpeedBps = bytesPerSecond;
                    postProgress(mCurrentIndex, mTotalCount);
                }
            };

            SmbSyncEngine.Result result = SmbSyncEngine.sync(config, source, callback, options);
            return new int[]{result.success, result.fail};
        }

        private void postProgress(int current, int total) {
            if (mHandler != null) {
                mHandler.post(() -> onProgressUpdate(current, total));
            }
        }

        private void onProgressUpdate(int current, int total) {
            if (progress == null) return;
            if (progress.getMax() != total) progress.setMax(total);
            progress.setProgress(current);

            StringBuilder msg = new StringBuilder();
            msg.append(progress.getContext().getString(R.string.settings_download_smb_backup_syncing));
            msg.append(String.format(Locale.US, " (%d/%d)", current, total));
            if (!mGalleryName.isEmpty()) {
                msg.append("\n").append(mGalleryName);
                if (mFileTotal > 0) {
                    msg.append(String.format(Locale.US, " (%d/%d)", mFileCurrent, mFileTotal));
                }
            }
            if (!mFileName.isEmpty()) {
                msg.append("\n").append(mFileName);
            }
            if (mSpeedBps > 0) {
                if (mSpeedBps > 1024 * 1024) {
                    msg.append(String.format(Locale.US, "\n%.1f MB/s", mSpeedBps / (1024.0 * 1024.0)));
                } else if (mSpeedBps > 1024) {
                    msg.append(String.format(Locale.US, "\n%.1f KB/s", mSpeedBps / 1024.0));
                } else {
                    msg.append(String.format(Locale.US, "\n%d B/s", mSpeedBps));
                }
            }
            progress.setMessage(msg.toString());
        }

        private void onPostExecute(int[] result) {
            if (progress != null) {
                DownloadFragment fragment = fragmentRef.get();
                if (fragment != null && fragment.isAdded() && fragment.getActivity() != null) {
                    try { if (progress.isShowing()) progress.dismiss(); } catch (Exception ignored) {}
                }
                progress = null;
            }
            DownloadFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            if (mBackgroundRequested) {
                Log.d(TAG, "Switching to background backup, aggressive=" + aggressiveMode);
                SmbBackupService.startWithAggressive(fragment.requireActivity(), aggressiveMode);
                Toast.makeText(fragment.getActivity(), R.string.settings_download_smb_backup_syncing, Toast.LENGTH_SHORT).show();
                return;
            }
            if (result[0] > 0 || result[1] > 0) {
                Toast.makeText(fragment.getActivity(),
                        fragment.getString(R.string.settings_download_smb_backup_sync_done, result[0], result[1]),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.getActivity(), R.string.settings_download_smb_backup_sync_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
