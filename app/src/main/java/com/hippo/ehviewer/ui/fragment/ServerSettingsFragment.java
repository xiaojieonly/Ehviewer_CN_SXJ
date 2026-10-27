package com.hippo.ehviewer.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.server.model.ServerState;
import com.hippo.ehviewer.server.service.LanServerService;
import com.hippo.ehviewer.server.service.ServerController;
import com.hippo.ehviewer.server.util.ServerSettings;
import com.hippo.ehviewer.ui.server.ServerLogActivity;

public class ServerSettingsFragment extends BasePreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    private static final String KEY_STATUS = "server_status";
    private static final String KEY_ADDRESSES = "server_addresses";
    private static final String KEY_LOGS = "server_open_logs";

    private Preference statusPreference;
    private Preference addressesPreference;
    private Preference portPreference;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.server_settings);

        Preference enabled = findPreference(com.hippo.ehviewer.Settings.KEY_SERVER_ENABLED);
        statusPreference = findPreference(KEY_STATUS);
        addressesPreference = findPreference(KEY_ADDRESSES);
        portPreference = findPreference(com.hippo.ehviewer.Settings.KEY_SERVER_PORT);
        Preference logLevel = findPreference(com.hippo.ehviewer.Settings.KEY_SERVER_LOG_LEVEL);
        Preference openLogs = findPreference(KEY_LOGS);

        if (enabled != null) {
            enabled.setOnPreferenceChangeListener(this);
        }
        if (portPreference != null) {
            portPreference.setOnPreferenceChangeListener(this);
        }
        if (logLevel != null) {
            logLevel.setOnPreferenceChangeListener(this);
        }
        if (openLogs != null) {
            openLogs.setOnPreferenceClickListener(this);
        }

        refreshState();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        if (getContext() == null) {
            return;
        }
        ServerState state = ServerController.get(getContext()).getState();

        if (statusPreference != null) {
            if (state.running) {
                statusPreference.setSummary(getString(R.string.server_status_running, state.boundPort));
            } else if (!TextUtils.isEmpty(state.lastError)) {
                statusPreference.setSummary(getString(R.string.server_status_error, state.lastError));
            } else {
                statusPreference.setSummary(R.string.server_status_stopped);
            }
        }

        if (portPreference != null) {
            int configured = ServerSettings.getConfiguredPort();
            if (state.running && state.boundPort > 0 && state.boundPort != configured) {
                portPreference.setSummary(getString(R.string.server_port_summary_fallback, configured, state.boundPort));
            } else {
                portPreference.setSummary(getString(R.string.server_port_summary, configured));
            }
        }

        if (addressesPreference != null) {
            addressesPreference.setSummary(formatAddresses(state.addresses, state.boundPort));
            addressesPreference.setEnabled(state.running);
        }
    }

    private CharSequence formatAddresses(JSONArray addresses, int port) {
        if (addresses == null || addresses.isEmpty() || port <= 0) {
            return getString(R.string.server_no_address);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addresses.size(); i++) {
            JSONObject row = addresses.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String label = row.getString("label");
            String address = row.getString("address");
            if (TextUtils.isEmpty(address)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(label).append(": http://").append(address).append(':').append(port);
        }
        if (sb.length() == 0) {
            return getString(R.string.server_no_address);
        }
        return sb.toString();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (com.hippo.ehviewer.Settings.KEY_SERVER_ENABLED.equals(key)) {
            boolean enable = Boolean.TRUE.equals(newValue);
            ServerSettings.setEnabled(enable);
            if (getContext() != null) {
                if (enable) {
                    LanServerService.startServer(requireContext());
                } else {
                    LanServerService.stopServer(requireContext());
                }
            }
            refreshState();
            return true;
        }

        if (com.hippo.ehviewer.Settings.KEY_SERVER_PORT.equals(key)) {
            int port;
            try {
                port = Integer.parseInt(String.valueOf(newValue));
            } catch (Throwable e) {
                Toast.makeText(getContext(), R.string.server_port_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
            if (port < 1 || port > 65535) {
                Toast.makeText(getContext(), R.string.server_port_invalid, Toast.LENGTH_SHORT).show();
                return false;
            }
            ServerSettings.setConfiguredPort(port);
            if (ServerSettings.isEnabled() && getContext() != null) {
                LanServerService.restartServer(requireContext());
            }
            refreshState();
            return true;
        }

        if (com.hippo.ehviewer.Settings.KEY_SERVER_LOG_LEVEL.equals(key)) {
            String level = String.valueOf(newValue);
            ServerSettings.setLogLevel(level);
            return true;
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (KEY_LOGS.equals(preference.getKey()) && getContext() != null) {
            startActivity(new Intent(getContext(), ServerLogActivity.class));
            return true;
        }
        return false;
    }
}
