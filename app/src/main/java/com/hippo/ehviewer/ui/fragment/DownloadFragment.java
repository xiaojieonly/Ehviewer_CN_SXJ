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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.ehviewer.ui.DirPickerActivity;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.yorozuya.IOUtils;

import java.io.FileOutputStream;
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

    @Nullable
    private Preference mDownloadLocation;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDownloadLocation = null;
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
        }
        return false;
    }

    private void showDirPickerDialogKK() {
        new AlertDialog.Builder(requireActivity()).setMessage(R.string.settings_download_pick_dir_kk)
                .setPositiveButton(R.string.settings_download_continue, (dialog, which) -> openDirPicker()).show();
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

    private void exportDownloadItems() {
        List<GalleryInfo> list = EhApplication.getDownloadManager(requireActivity()).getDownloadInfoList();
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
        String fileName = "ehviewer-download-" + sdf.format(new Date()) + ".csv";

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

    private static class ImportDownloadTask extends AsyncTask<Void, Integer, Integer> {

        private final WeakReference<DownloadFragment> mFragment;
        private final Uri mUri;
        private ProgressDialog mProgressDialog;

        public ImportDownloadTask(DownloadFragment fragment, Uri uri) {
            mFragment = new WeakReference<>(fragment);
            mUri = uri;
        }

        @Override
        protected void onPreExecute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_import_items);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected Integer doInBackground(Void... voids) {
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

                DownloadManager downloadManager = EhApplication.getDownloadManager(fragment.requireActivity());
                int importCount = 0;
                int total = galleryInfos.size();
                publishProgress(0, total);

                for (int i = 0; i < total; i++) {
                    GalleryInfo gi = galleryInfos.get(i);
                    if (downloadManager.getDownloadInfo(gi.gid) == null) {
                        downloadManager.addDownload(gi, null);
                        importCount++;
                    }
                    publishProgress(i + 1, total);
                }
                return importCount;
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (mProgressDialog != null) {
                mProgressDialog.setMax(values[1]);
                mProgressDialog.setProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
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

    private static class CleanInvalidDownloadTask extends AsyncTask<Void, Integer, Integer> {

        private final WeakReference<DownloadFragment> mFragment;
        private ProgressDialog mProgressDialog;
        private final List<String> mLogs = new ArrayList<>();

        public CleanInvalidDownloadTask(DownloadFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        protected void onPreExecute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_cleaning);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected Integer doInBackground(Void... voids) {
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
            publishProgress(0, total);

            DownloadManager downloadManager = EhApplication.getDownloadManager(mFragment.get().requireActivity());

            for (int i = 0; i < total; i++) {
                UniFile dir = files[i];
                publishProgress(i + 1, total);

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

                UniFile ehViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
                if (ehViewerFile == null) {
                    mLogs.add("Missing .ehviewer file: " + dir.getName());
                    invalidCount++;
                    continue;
                }

                try {
                    String content = IOUtils.readString(ehViewerFile.openInputStream(), StandardCharsets.UTF_8.name());
                    String[] lines = content.split("\n");
                    if (lines.length < 8) {
                        mLogs.add("Invalid .ehviewer file: " + dir.getName());
                        invalidCount++;
                        // Try to reset if possible
                        long gid;
                        try {
                            gid = Long.parseLong(lines[0]);
                        } catch (NumberFormatException e) {
                            gid = -1;
                        }
                        if (gid != -1) {
                            com.hippo.ehviewer.dao.DownloadInfo gi = downloadManager.getDownloadInfo(gid);
                            if (gi != null) {
                                gi.state = com.hippo.ehviewer.dao.DownloadInfo.STATE_NONE;
                                EhDB.putDownloadInfo(gi);
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
                            com.hippo.ehviewer.dao.DownloadInfo gi = downloadManager.getDownloadInfo(gid);
                            if (gi != null) {
                                gi.state = com.hippo.ehviewer.dao.DownloadInfo.STATE_NONE;
                                EhDB.putDownloadInfo(gi);
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

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (mProgressDialog != null) {
                mProgressDialog.setMax(values[1]);
                mProgressDialog.setProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
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
}

