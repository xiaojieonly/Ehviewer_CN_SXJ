/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.ehviewer.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.smb.SmbConfig;
import com.hippo.ehviewer.smb.SmbConnection;
import com.hippo.ehviewer.smb.SmbLoginMode;
import com.hippo.unifile.SmbUri;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SmbPickerActivity extends ToolbarActivity implements View.OnClickListener {

    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_SHARE = "share";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_PASSWORD = "password";
    public static final String EXTRA_LOGIN_MODE = "login_mode";

    public static final String RESULT_HOST = "result_host";
    public static final String RESULT_PORT = "result_port";
    public static final String RESULT_SHARE = "result_share";
    public static final String RESULT_PATH = "result_path";
    public static final String RESULT_USERNAME = "result_username";
    public static final String RESULT_PASSWORD = "result_password";
    public static final String RESULT_LOGIN_MODE = "result_login_mode";

    private EditText mHostInput;
    private EditText mPortInput;
    private EditText mUsernameInput;
    private EditText mPasswordInput;
    private EditText mShareInput;
    private View mConnectButton;
    private View mGoShareButton;
    private ListView mListView;
    private TextView mPathText;
    private TextView mStepHint;
    private View mUpButton;
    private View mConfirmButton;

    private String mHost;
    private int mPort = SmbUri.DEFAULT_PORT;
    private String mUsername = "";
    private String mPassword = "";
    private SmbLoginMode mLoginMode = SmbLoginMode.ANONYMOUS;
    private String mSelectedShare = "";
    private String mCurrentPath = "";
    private List<String> mCurrentEntries = new ArrayList<>();
    private List<String> mPathStack = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private int mStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smb_picker);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);

        mHostInput = findViewById(R.id.smb_host);
        mPortInput = findViewById(R.id.smb_port);
        mUsernameInput = findViewById(R.id.smb_username);
        mPasswordInput = findViewById(R.id.smb_password);
        mShareInput = findViewById(R.id.smb_share);
        mConnectButton = findViewById(R.id.smb_connect);
        mGoShareButton = findViewById(R.id.smb_go_share);
        mListView = findViewById(R.id.smb_list);
        mPathText = findViewById(R.id.smb_path);
        mStepHint = findViewById(R.id.smb_step_hint);
        mUpButton = findViewById(R.id.smb_up);
        mConfirmButton = findViewById(R.id.smb_confirm);

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mCurrentEntries);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < mCurrentEntries.size()) {
                enterFolder(mCurrentEntries.get(position));
            }
        });

        mConnectButton.setOnClickListener(this);
        mGoShareButton.setOnClickListener(this);
        mUpButton.setOnClickListener(this);
        mConfirmButton.setOnClickListener(this);

        restoreFromIntent();
        updateUI();
    }

    private void restoreFromIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_HOST)) {
            mHost = intent.getStringExtra(EXTRA_HOST);
            mPort = intent.getIntExtra(EXTRA_PORT, SmbUri.DEFAULT_PORT);
            mUsername = intent.getStringExtra(EXTRA_USERNAME);
            mPassword = intent.getStringExtra(EXTRA_PASSWORD);
            int loginModeOrdinal = intent.getIntExtra(EXTRA_LOGIN_MODE, 0);
            mLoginMode = loginModeOrdinal == 1 ? SmbLoginMode.PASSWORD : SmbLoginMode.ANONYMOUS;
            mSelectedShare = intent.getStringExtra(EXTRA_SHARE);
        } else {
            android.content.SharedPreferences prefs = getSharedPreferences("smb_picker_cache", MODE_PRIVATE);
            mHost = prefs.getString("host", "");
            mPort = prefs.getInt("port", SmbUri.DEFAULT_PORT);
            mUsername = prefs.getString("username", "");
            mPassword = prefs.getString("password", "");
            mLoginMode = prefs.getBoolean("has_password", false) ? SmbLoginMode.PASSWORD : SmbLoginMode.ANONYMOUS;
            mSelectedShare = prefs.getString("share", "");
        }

        if (mHost != null && !mHost.isEmpty()) {
            mHostInput.setText(mHost);
        }
        mPortInput.setText(String.valueOf(mPort));
        if (mUsername != null && !mUsername.isEmpty()) {
            mUsernameInput.setText(mUsername);
        }
        if (mPassword != null && !mPassword.isEmpty()) {
            mPasswordInput.setText(mPassword);
        }
        if (mSelectedShare != null && !mSelectedShare.isEmpty()) {
            mShareInput.setText(mSelectedShare);
        }
    }

    private void saveConfigToCache() {
        getSharedPreferences("smb_picker_cache", MODE_PRIVATE).edit()
                .putString("host", mHost != null ? mHost : "")
                .putInt("port", mPort)
                .putString("username", mUsername != null ? mUsername : "")
                .putString("password", mPassword != null ? mPassword : "")
                .putBoolean("has_password", mLoginMode == SmbLoginMode.PASSWORD)
                .putString("share", mSelectedShare != null ? mSelectedShare : "")
                .apply();
    }

    private void updateUI() {
        boolean showServerConfig = mStep == 0;
        boolean showShareInput = mStep == 1;
        boolean showFolderBrowser = mStep == 2;

        mHostInput.setVisibility(showServerConfig ? View.VISIBLE : View.GONE);
        mPortInput.setVisibility(showServerConfig ? View.VISIBLE : View.GONE);
        mUsernameInput.setVisibility(showServerConfig ? View.VISIBLE : View.GONE);
        mPasswordInput.setVisibility(showServerConfig ? View.VISIBLE : View.GONE);
        mConnectButton.setVisibility(showServerConfig ? View.VISIBLE : View.GONE);

        mShareInput.setVisibility(showShareInput || showFolderBrowser ? View.VISIBLE : View.GONE);
        mGoShareButton.setVisibility(showShareInput ? View.VISIBLE : View.GONE);

        mListView.setVisibility(showFolderBrowser ? View.VISIBLE : View.GONE);
        mPathText.setVisibility(showFolderBrowser ? View.VISIBLE : View.GONE);
        mUpButton.setVisibility(showFolderBrowser && !mPathStack.isEmpty() ? View.VISIBLE : View.GONE);
        mConfirmButton.setVisibility(showFolderBrowser ? View.VISIBLE : View.GONE);

        if (showServerConfig) {
            mStepHint.setVisibility(View.VISIBLE);
            mStepHint.setText(R.string.smb_picker_step_server);
            mShareInput.setText("");
        } else if (showShareInput) {
            mStepHint.setVisibility(View.VISIBLE);
            mStepHint.setText(R.string.smb_picker_step_share);
            mShareInput.setEnabled(true);
            mShareInput.setText(mSelectedShare != null ? mSelectedShare : "");
            mShareInput.setHint(R.string.settings_download_smb_share);
            mShareInput.setSelection(mShareInput.getText().length());
        } else if (showFolderBrowser) {
            mStepHint.setVisibility(View.GONE);
            mShareInput.setEnabled(false);
            String displayPath = String.format(Locale.US, "//%s:%d/%s%s", mHost, mPort, mSelectedShare,
                    mCurrentPath.isEmpty() ? "" : "/" + mCurrentPath);
            mPathText.setText(displayPath);
        }
    }

    @Override
    public void onClick(@NonNull View v) {
        if (v == mConnectButton) {
            onConnect();
        } else if (v == mGoShareButton) {
            onGoShare();
        } else if (v == mUpButton) {
            onUp();
        } else if (v == mConfirmButton) {
            onConfirm();
        }
    }

    private void onConnect() {
        String host = mHostInput.getText().toString().trim();
        String portStr = mPortInput.getText().toString().trim();
        if (host.isEmpty()) {
            Toast.makeText(this, R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
            return;
        }
        int port;
        try {
            port = portStr.isEmpty() ? SmbUri.DEFAULT_PORT : Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
            return;
        }

        mHost = host;
        mPort = port;
        mUsername = mUsernameInput.getText().toString().trim();
        mPassword = mPasswordInput.getText().toString().trim();
        mLoginMode = mUsername.isEmpty() ? SmbLoginMode.ANONYMOUS : SmbLoginMode.PASSWORD;

        saveConfigToCache();
        new TestConnectionTask(this).execute();
    }

    private void onGoShare() {
        String share = mShareInput.getText().toString().trim();
        if (share.isEmpty()) {
            Toast.makeText(this, R.string.settings_download_smb_invalid_config, Toast.LENGTH_SHORT).show();
            return;
        }
        mSelectedShare = share;
        saveConfigToCache();
        mCurrentPath = "";
        mPathStack.clear();
        mCurrentEntries.clear();
        mAdapter.notifyDataSetChanged();
        new LoadFoldersTask(this).execute();
    }

    private void onUp() {
        if (mPathStack.isEmpty()) {
            mStep = 1;
            mCurrentEntries.clear();
            mAdapter.notifyDataSetChanged();
            updateUI();
        } else {
            mCurrentPath = mPathStack.remove(mPathStack.size() - 1);
            new LoadFoldersTask(this).execute();
        }
    }

    private void enterFolder(String folderName) {
        mPathStack.add(mCurrentPath);
        mCurrentPath = mCurrentPath.isEmpty() ? folderName : mCurrentPath + "/" + folderName;
        new LoadFoldersTask(this).execute();
    }

    private void onConfirm() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_HOST, mHost);
        resultIntent.putExtra(RESULT_PORT, mPort);
        resultIntent.putExtra(RESULT_SHARE, mSelectedShare);
        resultIntent.putExtra(RESULT_PATH, mCurrentPath);
        resultIntent.putExtra(RESULT_USERNAME, mUsername);
        resultIntent.putExtra(RESULT_PASSWORD, mPassword);
        resultIntent.putExtra(RESULT_LOGIN_MODE, mLoginMode == SmbLoginMode.PASSWORD ? 1 : 0);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class TestConnectionTask extends AsyncTask<Void, Void, String> {
        private final WeakReference<SmbPickerActivity> ref;
        private ProgressDialog progress;

        TestConnectionTask(SmbPickerActivity activity) {
            ref = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            SmbPickerActivity a = ref.get();
            if (a != null && !a.isFinishing()) {
                progress = ProgressDialog.show(a, null,
                        a.getString(R.string.settings_download_smb_testing), true, false);
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            SmbPickerActivity a = ref.get();
            if (a == null) return "Activity unavailable";
            try {
                SmbConfig config = new SmbConfig(a.mHost, a.mPort, "IPC$", "",
                        a.mLoginMode,
                        a.mLoginMode == SmbLoginMode.PASSWORD ? a.mUsername : null,
                        a.mLoginMode == SmbLoginMode.PASSWORD ? a.mPassword : null);
                new SmbConnection(config).testConnection();
                return null;
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String error) {
            SmbPickerActivity a = ref.get();
            if (a == null || a.isFinishing()) return;
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            if (error == null) {
                a.mStep = 1;
                a.updateUI();
            } else {
                Toast.makeText(a, a.getString(R.string.settings_download_smb_connect_failed) + "\n" + error,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static class LoadFoldersTask extends AsyncTask<Void, Void, List<String>> {
        private final WeakReference<SmbPickerActivity> ref;
        private ProgressDialog progress;
        private String error;

        LoadFoldersTask(SmbPickerActivity activity) {
            ref = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            SmbPickerActivity a = ref.get();
            if (a != null && !a.isFinishing()) {
                progress = ProgressDialog.show(a, null,
                        a.getString(R.string.settings_download_smb_testing), true, false);
            }
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            SmbPickerActivity a = ref.get();
            if (a == null) return new ArrayList<>();
            try {
                SmbConfig config = new SmbConfig(a.mHost, a.mPort, a.mSelectedShare, "",
                        a.mLoginMode,
                        a.mLoginMode == SmbLoginMode.PASSWORD ? a.mUsername : null,
                        a.mLoginMode == SmbLoginMode.PASSWORD ? a.mPassword : null);
                return new SmbConnection(config).listShareNames();
            } catch (Exception e) {
                error = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<String> folders) {
            SmbPickerActivity a = ref.get();
            if (a == null || a.isFinishing()) return;
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            if (folders != null) {
                a.mStep = 2;
                java.util.Collections.sort(folders, String.CASE_INSENSITIVE_ORDER);
                a.mCurrentEntries.clear();
                a.mCurrentEntries.addAll(folders);
                a.mAdapter.notifyDataSetChanged();
                a.updateUI();
            } else {
                String msg = a.getString(R.string.settings_download_smb_connect_failed);
                if (error != null) msg += "\n" + error;
                Toast.makeText(a, msg, Toast.LENGTH_LONG).show();
            }
        }
    }
}
