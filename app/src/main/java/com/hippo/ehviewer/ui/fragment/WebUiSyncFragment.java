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

package com.hippo.ehviewer.ui.fragment;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.webui.PreferenceSyncHelper;
import com.hippo.ehviewer.webui.WebUiApiClient;
import com.hippo.ehviewer.webui.WebUiConfig;
import com.hippo.ehviewer.webui.WebUiSettings;
import com.hippo.ehviewer.webui.WebUiSyncEngine;
import com.hippo.ehviewer.webui.WebUiSyncModels;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings screen for connecting to the WebUI companion server and running
 * timestamp-incremental sync. Mirrors the SMB configure dialog flow in
 * {@link DownloadFragment}: a single "configure" preference opens a dialog,
 * connection work runs on a background executor and posts results to the main
 * thread via a {@link Handler}.
 */
public class WebUiSyncFragment extends PreferenceFragmentCompat {

    private static final String KEY_CONFIGURE = "webui_configure";
    private static final String KEY_STATUS = "webui_status";
    private static final String KEY_SYNC_NOW = "webui_sync_now";
    private static final String KEY_REMOTE_READ = "webui_remote_read";
    private static final String KEY_PAIR = "webui_pair";
    private static final String KEY_SYNC_PREFERENCES = "webui_sync_preferences";
    private static final String KEY_PULL_PREFERENCES = "webui_pull_preferences";

    private Preference mConfigure;
    private Preference mStatus;
    private Preference mSyncNow;
    private Preference mSyncPreferences;
    private Preference mPullPreferences;

    private ExecutorService mExecutor;
    private Handler mMainHandler;
    private boolean mSyncInProgress;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.webui_sync_settings, rootKey);
        mExecutor = Executors.newCachedThreadPool();
        mMainHandler = new Handler(Looper.getMainLooper());

        mConfigure = findPreference(KEY_CONFIGURE);
        mStatus = findPreference(KEY_STATUS);
        mSyncNow = findPreference(KEY_SYNC_NOW);
        mSyncPreferences = findPreference(KEY_SYNC_PREFERENCES);
        mPullPreferences = findPreference(KEY_PULL_PREFERENCES);

        if (mConfigure != null) {
            mConfigure.setOnPreferenceClickListener(p -> {
                showConfigureDialog();
                return true;
            });
        }
        if (mStatus != null) {
            mStatus.setOnPreferenceClickListener(p -> {
                refreshStatus();
                return true;
            });
        }
        if (mSyncNow != null) {
            mSyncNow.setOnPreferenceClickListener(p -> {
                syncNow();
                return true;
            });
        }

        Preference pair = findPreference(KEY_PAIR);
        if (pair != null) {
            pair.setOnPreferenceClickListener(p -> {
                showPairDialog();
                return true;
            });
        }
        if (mSyncPreferences != null) {
            mSyncPreferences.setOnPreferenceClickListener(p -> {
                syncPreferences();
                return true;
            });
        }
        if (mPullPreferences != null) {
            mPullPreferences.setOnPreferenceClickListener(p -> {
                pullPreferences();
                return true;
            });
        }

        // Remote reading toggle (roadmap §2.4). Persisted via WebUiSettings
        // (custom prefs file, like the credential store) rather than the
        // default preference store, so keep it non-persistent here.
        SwitchPreferenceCompat remoteRead = findPreference(KEY_REMOTE_READ);
        if (remoteRead != null) {
            WebUiSettings settings = new WebUiSettings(requireContext());
            remoteRead.setChecked(settings.remoteReadEnabled());
            remoteRead.setPersistent(false);
            remoteRead.setOnPreferenceChangeListener((pref, newValue) -> {
                settings.setRemoteReadEnabled(Boolean.TRUE.equals(newValue));
                return true;
            });
        }

        updateConfigureSummary();
        updateLastSyncSummary();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }

    private void updateConfigureSummary() {
        if (mConfigure == null) return;
        WebUiConfig config = new WebUiSettings(requireContext()).loadConfig();
        if (config == null) {
            mConfigure.setSummary(R.string.settings_webui_not_configured);
        } else {
            mConfigure.setSummary(getString(R.string.settings_webui_configured, config.displayAddress()));
        }
    }

    private void updateLastSyncSummary() {
        if (mSyncNow == null) return;
        long last = new WebUiSettings(requireContext()).lastSyncTimestamp();
        if (last <= 0) {
            mSyncNow.setSummary(R.string.settings_webui_sync_now_summary);
        } else {
            mSyncNow.setSummary(getString(R.string.settings_webui_last_sync,
                    java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(last))));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Configure dialog
    // ---------------------------------------------------------------------------------------------

    private void showConfigureDialog() {
        WebUiConfig existing = new WebUiSettings(requireContext()).loadConfig();

        LinearLayout layout = new LinearLayout(requireActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        layout.setPadding(padding, padding, padding, padding);

        Spinner protocol = new Spinner(requireActivity());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_spinner_item, new String[]{"http", "https"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocol.setAdapter(adapter);
        if (existing != null && WebUiConfig.PROTOCOL_HTTPS.equals(existing.getProtocol())) {
            protocol.setSelection(1);
        }
        layout.addView(protocol);

        EditText host = addField(layout, R.string.settings_webui_host,
                existing != null ? existing.getHost() : "");
        EditText port = addField(layout, R.string.settings_webui_port,
                existing != null ? String.valueOf(existing.getPort()) : String.valueOf(WebUiConfig.DEFAULT_PORT));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText username = addField(layout, R.string.settings_webui_username,
                existing != null ? existing.getUsername() : "");
        EditText password = addField(layout, R.string.settings_webui_password, "");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_webui_configure)
                .setView(layout)
                .setPositiveButton(R.string.settings_webui_connect, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String hostValue = host.getText().toString().trim();
            String portValue = port.getText().toString().trim();
            if (hostValue.isEmpty()) {
                Toast.makeText(requireActivity(), R.string.settings_webui_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            int portNumber;
            try {
                portNumber = portValue.isEmpty() ? WebUiConfig.DEFAULT_PORT : Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                Toast.makeText(requireActivity(), R.string.settings_webui_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            String protocolValue = protocol.getSelectedItemPosition() == 1 ? "https" : "http";
            String userValue = username.getText().toString().trim();
            String passValue = password.getText().toString();
            new ConnectTask(this, dialog, protocolValue, hostValue, portNumber, userValue, passValue).execute();
        }));
        dialog.show();
    }

    private EditText addField(ViewGroup parent, int hintRes, String value) {
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

    // ---------------------------------------------------------------------------------------------
    // Pairing dialog
    // ---------------------------------------------------------------------------------------------

    /** Pairs this device via a server-generated pairing code (no password needed). */
    private void showPairDialog() {
        WebUiConfig existing = new WebUiSettings(requireContext()).loadConfig();

        LinearLayout layout = new LinearLayout(requireActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_top_material);
        layout.setPadding(padding, padding, padding, padding);

        Spinner protocol = new Spinner(requireActivity());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_spinner_item, new String[]{"http", "https"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocol.setAdapter(adapter);
        if (existing != null && WebUiConfig.PROTOCOL_HTTPS.equals(existing.getProtocol())) {
            protocol.setSelection(1);
        }
        layout.addView(protocol);

        EditText host = addField(layout, R.string.settings_webui_host,
                existing != null ? existing.getHost() : "");
        EditText port = addField(layout, R.string.settings_webui_port,
                existing != null ? String.valueOf(existing.getPort()) : String.valueOf(WebUiConfig.DEFAULT_PORT));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText code = addField(layout, R.string.settings_webui_pair_code, "");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_webui_pair)
                .setView(layout)
                .setPositiveButton(R.string.settings_webui_pair_confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String hostValue = host.getText().toString().trim();
            String portValue = port.getText().toString().trim();
            String codeValue = code.getText().toString().trim();
            if (hostValue.isEmpty() || codeValue.length() < 4) {
                Toast.makeText(requireActivity(), R.string.settings_webui_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            int portNumber;
            try {
                portNumber = portValue.isEmpty() ? WebUiConfig.DEFAULT_PORT : Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                Toast.makeText(requireActivity(), R.string.settings_webui_invalid_config, Toast.LENGTH_SHORT).show();
                return;
            }
            String protocolValue = protocol.getSelectedItemPosition() == 1 ? "https" : "http";
            new PairTask(this, dialog, protocolValue, hostValue, portNumber, codeValue.toUpperCase()).execute();
        }));
        dialog.show();
    }

    // ---------------------------------------------------------------------------------------------
    // Status / sync actions
    // ---------------------------------------------------------------------------------------------

    private void refreshStatus() {
        WebUiConfig config = new WebUiSettings(requireContext()).loadConfig();
        if (config == null || TextUtils.isEmpty(config.getToken())) {
            Toast.makeText(requireActivity(), R.string.settings_webui_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mStatus != null) mStatus.setSummary(R.string.settings_webui_status_checking);
        new StatusTask(this).execute(config);
    }

    private void syncNow() {
        WebUiSettings settings = new WebUiSettings(requireContext());
        WebUiConfig config = settings.loadConfig();
        if (config == null || TextUtils.isEmpty(config.getToken())) {
            Toast.makeText(requireActivity(), R.string.settings_webui_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mSyncInProgress) {
            Toast.makeText(requireActivity(), R.string.settings_webui_syncing, Toast.LENGTH_SHORT).show();
            return;
        }
        mSyncInProgress = true;
        if (mSyncNow != null) mSyncNow.setEnabled(false);
        new SyncTask(this).execute(config, settings.deviceId(), settings.lastSyncTimestamp());
    }

    private void syncPreferences() {
        WebUiSettings settings = new WebUiSettings(requireContext());
        WebUiConfig config = settings.loadConfig();
        if (config == null || TextUtils.isEmpty(config.getToken())) {
            Toast.makeText(requireActivity(), R.string.settings_webui_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        new PreferencePushTask(this).execute(config, settings.deviceId());
    }

    private void pullPreferences() {
        WebUiSettings settings = new WebUiSettings(requireContext());
        WebUiConfig config = settings.loadConfig();
        if (config == null || TextUtils.isEmpty(config.getToken())) {
            Toast.makeText(requireActivity(), R.string.settings_webui_not_configured, Toast.LENGTH_SHORT).show();
            return;
        }
        new PreferencePullTask(this).execute(config, settings.deviceId());
    }

    // ---------------------------------------------------------------------------------------------
    // Background tasks
    // ---------------------------------------------------------------------------------------------

    /** Logs in, verifies sync access, then persists the connection. */
    private static final class ConnectTask {
        private final WeakReference<WebUiSyncFragment> fragmentRef;
        private final AlertDialog dialog;
        private final String protocol;
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        private ProgressDialog progress;

        ConnectTask(WebUiSyncFragment fragment, AlertDialog dialog, String protocol, String host,
                int port, String username, String password) {
            this.fragmentRef = new WeakReference<>(fragment);
            this.dialog = dialog;
            this.protocol = protocol;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        void execute() {
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;

            progress = ProgressDialog.show(fragment.requireActivity(), null,
                    fragment.getString(R.string.settings_webui_testing), true, false);

            executor.execute(() -> {
                String token = null;
                Throwable error = null;
                try {
                    WebUiConfig probe = new WebUiConfig(protocol, host, port, username, "");
                    WebUiSyncModels.AuthResponse auth = WebUiApiClient.login(probe, username, password);
                    if (!auth.success || TextUtils.isEmpty(auth.token)) {
                        throw new java.io.IOException(TextUtils.isEmpty(auth.message)
                                ? "Login failed" : auth.message);
                    }
                    token = auth.token;
                    // Verify the token actually grants sync access.
                    WebUiApiClient.status(new WebUiConfig(protocol, host, port, username, token));
                } catch (Throwable e) {
                    error = e;
                }
                final String savedToken = token;
                final Throwable finalError = error;
                handler.post(() -> onPostExecute(savedToken, finalError));
            });
        }

        private void onPostExecute(String token, Throwable error) {
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || !fragment.isAdded()) return;
            if (error == null) {
                WebUiConfig config = new WebUiConfig(protocol, host, port, username, token);
                new WebUiSettings(fragment.requireContext()).saveConfig(config);
                dialog.dismiss();
                fragment.updateConfigureSummary();
                Toast.makeText(fragment.requireActivity(), R.string.settings_webui_connected, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.requireActivity(),
                        fragment.getString(R.string.settings_webui_connect_failed, messageOf(error)),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Exchanges a pairing code for a device token, persists the connection, then pulls preferences. */
    private static final class PairTask {
        private final WeakReference<WebUiSyncFragment> fragmentRef;
        private final AlertDialog dialog;
        private final String protocol;
        private final String host;
        private final int port;
        private final String code;
        private ProgressDialog progress;

        PairTask(WebUiSyncFragment fragment, AlertDialog dialog, String protocol, String host,
                int port, String code) {
            this.fragmentRef = new WeakReference<>(fragment);
            this.dialog = dialog;
            this.protocol = protocol;
            this.host = host;
            this.port = port;
            this.code = code;
        }

        void execute() {
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;

            progress = ProgressDialog.show(fragment.requireActivity(), null,
                    fragment.getString(R.string.settings_webui_pairing), true, false);

            executor.execute(() -> {
                String username = null;
                String token = null;
                Throwable error = null;
                try {
                    WebUiConfig probe = new WebUiConfig(protocol, host, port, "", "");
                    WebUiSettings settings = new WebUiSettings(fragment.getContext());
                    WebUiSyncModels.PairCompleteResponse auth =
                            WebUiApiClient.pairComplete(probe, code, settings.deviceId(),
                                    android.os.Build.MODEL, "android");
                    if (!auth.success || TextUtils.isEmpty(auth.token)) {
                        throw new java.io.IOException(TextUtils.isEmpty(auth.message)
                                ? "Pairing failed" : auth.message);
                    }
                    username = auth.username;
                    token = auth.token;
                } catch (Throwable e) {
                    error = e;
                }
                final String savedUsername = username;
                final String savedToken = token;
                final Throwable finalError = error;
                handler.post(() -> onPostExecute(savedUsername, savedToken, finalError));
            });
        }

        private void onPostExecute(String username, String token, Throwable error) {
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || !fragment.isAdded()) return;
            if (error == null) {
                WebUiConfig config = new WebUiConfig(protocol, host, port, username, token);
                new WebUiSettings(fragment.requireContext()).saveConfig(config);
                dialog.dismiss();
                fragment.updateConfigureSummary();
                Toast.makeText(fragment.requireActivity(), R.string.settings_webui_pair_done, Toast.LENGTH_LONG).show();
                // Pull server preferences onto the device right after pairing.
                fragment.pullPreferences();
            } else {
                Toast.makeText(fragment.requireActivity(),
                        fragment.getString(R.string.settings_webui_pair_failed, messageOf(error)),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static final class StatusTask {
        private final WeakReference<WebUiSyncFragment> fragmentRef;

        StatusTask(WebUiSyncFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        void execute(WebUiConfig config) {
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;
            executor.execute(() -> {
                String summary;
                try {
                    WebUiSyncModels.StatusResponse status = WebUiApiClient.status(config);
                    long fav = status.entityCounts != null ? status.entityCounts.favorites : 0;
                    long hist = status.entityCounts != null ? status.entityCounts.history : 0;
                    int devices = status.connectedDevices != null ? status.connectedDevices.size() : 0;
                    summary = "OK · " + devices + " devices · fav " + fav + " · hist " + hist;
                } catch (Throwable e) {
                    summary = messageOf(e);
                }
                final String text = summary;
                handler.post(() -> {
                    WebUiSyncFragment f = fragmentRef.get();
                    if (f != null && f.isAdded() && f.mStatus != null) {
                        f.mStatus.setSummary(text);
                    }
                });
            });
        }
    }

    private static final class SyncTask {
        private final WeakReference<WebUiSyncFragment> fragmentRef;
        private ProgressDialog progress;

        SyncTask(WebUiSyncFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        void execute(WebUiConfig config, String deviceId, long since) {
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;

            progress = ProgressDialog.show(fragment.requireActivity(), null,
                    fragment.getString(R.string.settings_webui_syncing), true, false);

            executor.execute(() -> {
                WebUiSyncEngine.Result result = null;
                Throwable error = null;
                try {
                    result = WebUiSyncEngine.sync(config, deviceId, since);
                } catch (Throwable e) {
                    error = e;
                }
                final WebUiSyncEngine.Result finalResult = result;
                final Throwable finalError = error;
                handler.post(() -> onPostExecute(finalResult, finalError));
            });
        }

        private void onPostExecute(WebUiSyncEngine.Result result, Throwable error) {
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null) return;
            fragment.mSyncInProgress = false;
            if (fragment.mSyncNow != null) fragment.mSyncNow.setEnabled(true);
            if (!fragment.isAdded()) return;
            if (error == null && result != null) {
                new WebUiSettings(fragment.requireContext()).setLastSyncTimestamp(result.serverTimestamp);
                fragment.updateLastSyncSummary();
                Toast.makeText(fragment.requireActivity(),
                        fragment.getString(R.string.settings_webui_sync_done,
                                result.pushedFavorites, result.pushedHistory,
                                result.pulledFavorites, result.pulledHistory),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.requireActivity(),
                        fragment.getString(R.string.settings_webui_sync_failed, messageOf(error)),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Uploads local preferences to the server via {@link PreferenceSyncHelper}. */
    private static final class PreferencePushTask {
        private final WeakReference<WebUiSyncFragment> fragmentRef;
        private ProgressDialog progress;

        PreferencePushTask(WebUiSyncFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        void execute(WebUiConfig config, String deviceId) {
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;
            Context context = fragment.getContext();
            if (context == null) return;

            progress = ProgressDialog.show(fragment.requireActivity(), null,
                    fragment.getString(R.string.settings_webui_preferences_syncing), true, false);

            executor.execute(() -> {
                Throwable error = null;
                try {
                    PreferenceSyncHelper.pushToServer(config, context, deviceId);
                } catch (Throwable e) {
                    error = e;
                }
                final Throwable finalError = error;
                handler.post(() -> onPostExecute(finalError));
            });
        }

        private void onPostExecute(Throwable error) {
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || !fragment.isAdded()) return;
            if (error == null) {
                Toast.makeText(fragment.requireActivity(), R.string.settings_webui_preferences_pushed, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.requireActivity(),
                        fragment.getString(R.string.settings_webui_preferences_failed, messageOf(error)),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Downloads server preferences and applies them locally via {@link PreferenceSyncHelper}. */
    private static final class PreferencePullTask {
        private final WeakReference<WebUiSyncFragment> fragmentRef;
        private ProgressDialog progress;

        PreferencePullTask(WebUiSyncFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        void execute(WebUiConfig config, String deviceId) {
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || fragment.getActivity() == null) return;
            ExecutorService executor = fragment.mExecutor;
            Handler handler = fragment.mMainHandler;
            if (executor == null || handler == null) return;
            Context context = fragment.getContext();
            if (context == null) return;

            progress = ProgressDialog.show(fragment.requireActivity(), null,
                    fragment.getString(R.string.settings_webui_preferences_syncing), true, false);

            executor.execute(() -> {
                Throwable error = null;
                try {
                    PreferenceSyncHelper.pullFromServer(config, context, deviceId);
                } catch (Throwable e) {
                    error = e;
                }
                final Throwable finalError = error;
                handler.post(() -> onPostExecute(finalError));
            });
        }

        private void onPostExecute(Throwable error) {
            if (progress != null) {
                try { progress.dismiss(); } catch (Exception ignored) {}
            }
            WebUiSyncFragment fragment = fragmentRef.get();
            if (fragment == null || !fragment.isAdded()) return;
            if (error == null) {
                Toast.makeText(fragment.requireActivity(), R.string.settings_webui_preferences_pulled, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.requireActivity(),
                        fragment.getString(R.string.settings_webui_preferences_failed, messageOf(error)),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String messageOf(Throwable e) {
        if (e == null) return "Unknown error";
        String m = e.getMessage();
        return TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m;
    }
}
