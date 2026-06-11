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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

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
    private ListView mListView;
    private TextView mPathText;
    private View mConnectButton;
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
    private boolean mIsConnected = false;
    private boolean mIsBrowsingShare = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smb_picker);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);

        mHostInput = findViewById(R.id.smb_host);
        mPortInput = findViewById(R.id.smb_port);
        mUsernameInput = findViewById(R.id.smb_username);
        mPasswordInput = findViewById(R.id.smb_password);
        mListView = findViewById(R.id.smb_list);
        mPathText = findViewById(R.id.smb_path);
        mConnectButton = findViewById(R.id.smb_connect);
        mUpButton = findViewById(R.id.smb_up);
        mConfirmButton = findViewById(R.id.smb_confirm);

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mCurrentEntries);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < mCurrentEntries.size()) {
                String selected = mCurrentEntries.get(position);
                if (mIsBrowsingShare) {
                    enterFolder(selected);
                } else {
                    selectShare(selected);
                }
            }
        });

        mConnectButton.setOnClickListener(this);
        mUpButton.setOnClickListener(this);
        mConfirmButton.setOnClickListener(this);

        restoreFromIntent();
        updateUI();
    }

    private void restoreFromIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            mHost = intent.getStringExtra(EXTRA_HOST);
            mPort = intent.getIntExtra(EXTRA_PORT, SmbUri.DEFAULT_PORT);
            mUsername = intent.getStringExtra(EXTRA_USERNAME);
            mPassword = intent.getStringExtra(EXTRA_PASSWORD);
            int loginModeOrdinal = intent.getIntExtra(EXTRA_LOGIN_MODE, 0);
            mLoginMode = loginModeOrdinal == 1 ? SmbLoginMode.PASSWORD : SmbLoginMode.ANONYMOUS;

            if (mHost != null) {
                mHostInput.setText(mHost);
            }
            mPortInput.setText(String.valueOf(mPort));
            if (mUsername != null) {
                mUsernameInput.setText(mUsername);
            }
            if (mPassword != null) {
                mPasswordInput.setText(mPassword);
            }
        }
    }

    private void updateUI() {
        if (mIsConnected) {
            mHostInput.setEnabled(false);
            mPortInput.setEnabled(false);
            mUsernameInput.setEnabled(false);
            mPasswordInput.setEnabled(false);
            mConnectButton.setVisibility(View.GONE);

            mListView.setVisibility(View.VISIBLE);
            mPathText.setVisibility(View.VISIBLE);
            mConfirmButton.setVisibility(View.VISIBLE);

            mUpButton.setVisibility(mPathStack.isEmpty() && !mIsBrowsingShare ? View.GONE : View.VISIBLE);

            String displayPath;
            if (mIsBrowsingShare) {
                displayPath = String.format(Locale.US, "//%s:%d/%s%s", mHost, mPort, mSelectedShare,
                        mCurrentPath.isEmpty() ? "" : "/" + mCurrentPath);
            } else {
                displayPath = String.format(Locale.US, "//%s:%d", mHost, mPort);
            }
            mPathText.setText(displayPath);
        } else {
            mHostInput.setEnabled(true);
            mPortInput.setEnabled(true);
            mUsernameInput.setEnabled(true);
            mPasswordInput.setEnabled(true);
            mConnectButton.setVisibility(View.VISIBLE);

            mListView.setVisibility(View.GONE);
            mPathText.setVisibility(View.GONE);
            mUpButton.setVisibility(View.GONE);
            mConfirmButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(@NonNull View v) {
        if (v == mConnectButton) {
            onConnect();
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

        new LoadSharesTask(this).execute();
    }

    private void onUp() {
        if (mIsBrowsingShare) {
            if (mPathStack.isEmpty()) {
                mIsBrowsingShare = false;
                mSelectedShare = "";
                mCurrentPath = "";
                new LoadSharesTask(this).execute();
            } else {
                String prev = mPathStack.remove(mPathStack.size() - 1);
                mCurrentPath = prev;
                new LoadFoldersTask(this).execute();
            }
        }
    }

    private void selectShare(String shareName) {
        mSelectedShare = shareName;
        mIsBrowsingShare = true;
        mCurrentPath = "";
        mPathStack.clear();
        new LoadFoldersTask(this).execute();
    }

    private void enterFolder(String folderName) {
        if (mCurrentPath.isEmpty()) {
            mCurrentPath = folderName;
        } else {
            mPathStack.add(mCurrentPath);
            mCurrentPath = mCurrentPath + "/" + folderName;
        }
        new LoadFoldersTask(this).execute();
    }

    private void onConfirm() {
        if (!mIsConnected || mSelectedShare.isEmpty()) {
            Toast.makeText(this, R.string.settings_download_smb_backup_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }

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

    private static class LoadSharesTask extends AsyncTask<Void, Void, List<String>> {
        private final WeakReference<SmbPickerActivity> ref;
        private ProgressDialog progress;
        private String error;

        LoadSharesTask(SmbPickerActivity activity) {
            ref = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            SmbPickerActivity activity = ref.get();
            if (activity != null && !activity.isFinishing()) {
                progress = ProgressDialog.show(activity, null,
                        activity.getString(R.string.settings_download_smb_testing), true, false);
            }
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            SmbPickerActivity activity = ref.get();
            if (activity == null) return new ArrayList<>();
            try {
                SmbConfig config = new SmbConfig(activity.mHost, activity.mPort, "IPC$", "",
                        activity.mLoginMode,
                        activity.mLoginMode == SmbLoginMode.PASSWORD ? activity.mUsername : null,
                        activity.mLoginMode == SmbLoginMode.PASSWORD ? activity.mPassword : null);
                SmbConnection connection = new SmbConnection(config);
                return connection.listShareNamesFromServer();
            } catch (Exception e) {
                error = e.getMessage();
                return new ArrayList<>();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onPostExecute(List<String> shares) {
            SmbPickerActivity activity = ref.get();
            if (activity == null || activity.isFinishing()) return;
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            if (shares != null && !shares.isEmpty()) {
                activity.mIsConnected = true;
                activity.mCurrentEntries.clear();
                activity.mCurrentEntries.addAll(shares);
                activity.mAdapter.notifyDataSetChanged();
                activity.updateUI();
            } else {
                String msg = activity.getString(R.string.settings_download_smb_connect_failed);
                if (error != null) {
                    msg += "\n" + error;
                }
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
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
            SmbPickerActivity activity = ref.get();
            if (activity != null && !activity.isFinishing()) {
                progress = ProgressDialog.show(activity, null,
                        activity.getString(R.string.settings_download_smb_testing), true, false);
            }
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            SmbPickerActivity activity = ref.get();
            if (activity == null) return new ArrayList<>();
            try {
                SmbConfig config = new SmbConfig(activity.mHost, activity.mPort, activity.mSelectedShare, "",
                        activity.mLoginMode,
                        activity.mLoginMode == SmbLoginMode.PASSWORD ? activity.mUsername : null,
                        activity.mLoginMode == SmbLoginMode.PASSWORD ? activity.mPassword : null);
                SmbConnection connection = new SmbConnection(config);
                return connection.listShareNames();
            } catch (Exception e) {
                error = e.getMessage();
                return new ArrayList<>();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onPostExecute(List<String> folders) {
            SmbPickerActivity activity = ref.get();
            if (activity == null || activity.isFinishing()) return;
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            if (folders != null) {
                activity.mCurrentEntries.clear();
                activity.mCurrentEntries.addAll(folders);
                activity.mAdapter.notifyDataSetChanged();
                activity.updateUI();
            } else {
                String msg = activity.getString(R.string.settings_download_smb_connect_failed);
                if (error != null) {
                    msg += "\n" + error;
                }
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        }
    }
}
