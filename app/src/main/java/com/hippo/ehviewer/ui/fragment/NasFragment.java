package com.hippo.ehviewer.ui.fragment;

import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.event.SomethingNeedRefresh;
import com.hippo.ehviewer.sync.nas.NasBrowserClient;
import com.hippo.ehviewer.sync.nas.NasConfigStore;
import com.hippo.ehviewer.sync.nas.NasCredentialStore;
import com.hippo.ehviewer.sync.nas.NasSyncConfig;
import com.hippo.ehviewer.sync.nas.NasSyncScheduler;
import com.hippo.ehviewer.sync.nas.NasSyncService;
import com.hippo.util.ExceptionUtils;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NasFragment extends BasePreferenceFragmentCompat implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    private static final String KEY_SETUP = "nas_setup";
    private static final String KEY_ACCOUNT = "nas_account";
    private static final String KEY_IMPORT_DATABASE = "nas_import_database";
    private static final String KEY_SYNC_MERGE = "nas_sync_merge";
    private static final String KEY_STATUS = "nas_connection_status";
    private static final String KEY_SCHEDULE_TIME = "nas_schedule_time";

    private Preference setupPreference;
    private Preference accountPreference;
    private Preference statusPreference;
    private Preference downloadBehaviorPreference;
    private Preference scheduleModePreference;
    private Preference scheduleTimePreference;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.nas_settings);
        Preference enabled = findPreference(NasConfigStore.KEY_ENABLED);
        setupPreference = findPreference(KEY_SETUP);
        accountPreference = findPreference(KEY_ACCOUNT);
        statusPreference = findPreference(KEY_STATUS);
        downloadBehaviorPreference = findPreference(NasConfigStore.KEY_DOWNLOAD_BEHAVIOR);
        scheduleModePreference = findPreference(NasConfigStore.KEY_SCHEDULE_MODE);
        scheduleTimePreference = findPreference(KEY_SCHEDULE_TIME);
        Preference scheduleEnabled = findPreference(NasConfigStore.KEY_SCHEDULE_ENABLED);
        Preference importDatabase = findPreference(KEY_IMPORT_DATABASE);
        Preference syncMerge = findPreference(KEY_SYNC_MERGE);
        if (enabled != null) enabled.setOnPreferenceChangeListener(this);
        if (setupPreference != null) setupPreference.setOnPreferenceClickListener(this);
        if (importDatabase != null) importDatabase.setOnPreferenceClickListener(this);
        if (syncMerge != null) syncMerge.setOnPreferenceClickListener(this);
        if (statusPreference != null) statusPreference.setOnPreferenceClickListener(this);
        if (scheduleTimePreference != null) scheduleTimePreference.setOnPreferenceClickListener(this);
        if (downloadBehaviorPreference != null) {
            downloadBehaviorPreference.setOnPreferenceChangeListener(this);
        }
        if (scheduleModePreference != null) scheduleModePreference.setOnPreferenceChangeListener(this);
        if (scheduleEnabled != null) scheduleEnabled.setOnPreferenceChangeListener(this);
        updateConnectionSummary();
        updatePolicySummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectionSummary();
        updatePolicySummaries();
        refreshConnectionStatus();
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if (NasConfigStore.KEY_ENABLED.equals(preference.getKey()) && newValue instanceof Boolean) {
            Settings.putBoolean(NasConfigStore.KEY_ENABLED, (Boolean) newValue);
            EventBus.getDefault().post(SomethingNeedRefresh.downloadInfoNeedRefresh());
            NasSyncScheduler.update(requireContext());
            refreshConnectionStatus((Boolean) newValue);
            return true;
        }
        if (NasConfigStore.KEY_DOWNLOAD_BEHAVIOR.equals(preference.getKey())) {
            Settings.putString(NasConfigStore.KEY_DOWNLOAD_BEHAVIOR, String.valueOf(newValue));
            updatePolicySummaries(String.valueOf(newValue), null);
            return true;
        }
        if (NasConfigStore.KEY_SCHEDULE_MODE.equals(preference.getKey())) {
            Settings.putString(NasConfigStore.KEY_SCHEDULE_MODE, String.valueOf(newValue));
            updatePolicySummaries(null, String.valueOf(newValue));
            NasSyncScheduler.update(requireContext());
            return true;
        }
        if (NasConfigStore.KEY_SCHEDULE_ENABLED.equals(preference.getKey())
                && newValue instanceof Boolean) {
            Settings.putBoolean(NasConfigStore.KEY_SCHEDULE_ENABLED, (Boolean) newValue);
            NasSyncScheduler.update(requireContext());
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        switch (preference.getKey()) {
            case KEY_SETUP:
                showServerStep(ConnectionDraft.load(requireContext()));
                return true;
            case KEY_STATUS:
                refreshConnectionStatus();
                return true;
            case KEY_SCHEDULE_TIME:
                showScheduleTimePicker();
                return true;
            case KEY_IMPORT_DATABASE:
                if (!ensureReady()) return true;
                NasSyncService.start(requireContext(), NasSyncService.ACTION_IMPORT_DATABASE);
                Toast.makeText(requireContext(), R.string.nas_sync_started_background,
                        Toast.LENGTH_SHORT).show();
                return true;
            case KEY_SYNC_MERGE:
                if (!ensureReady()) return true;
                NasSyncService.start(requireContext(), NasSyncService.ACTION_MERGE);
                Toast.makeText(requireContext(), R.string.nas_sync_started_background,
                        Toast.LENGTH_SHORT).show();
                return true;
            default:
                return false;
        }
    }

    private boolean ensureReady() {
        if (!NasConfigStore.isEnabled(requireContext())) {
            Toast.makeText(requireContext(), R.string.settings_nas_disabled,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!NasConfigStore.isConfigured()) {
            Toast.makeText(requireContext(), R.string.settings_nas_missing_config,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void updateConnectionSummary() {
        String host = Settings.getString(NasConfigStore.KEY_HOST, "").trim();
        String share = Settings.getString(NasConfigStore.KEY_SHARE, "").trim();
        String directory = Settings.getString(NasConfigStore.KEY_DIRECTORY, "").trim();
        String domain = Settings.getString(NasConfigStore.KEY_DOMAIN, "").trim();
        String username = Settings.getString(NasConfigStore.KEY_USERNAME, "").trim();
        if (setupPreference != null) {
            setupPreference.setSummary(host.isEmpty() || share.isEmpty()
                    ? getString(R.string.settings_nas_not_configured)
                    : uncPath(host, share, directory));
        }
        if (accountPreference != null) {
            accountPreference.setSummary(username.isEmpty() ? getString(R.string.settings_nas_guest)
                    : domain.isEmpty() ? username : domain + "\\" + username);
        }
    }

    private void updatePolicySummaries() {
        updatePolicySummaries(NasConfigStore.getDownloadBehavior(),
                NasConfigStore.getScheduleMode());
    }

    private void updatePolicySummaries(@Nullable String behavior, @Nullable String scheduleMode) {
        if (behavior == null) behavior = NasConfigStore.getDownloadBehavior();
        if (scheduleMode == null) scheduleMode = NasConfigStore.getScheduleMode();
        if (downloadBehaviorPreference != null) {
            int summary = NasConfigStore.DOWNLOAD_PHONE_NAS.equals(behavior)
                    ? R.string.settings_nas_download_phone_nas
                    : NasConfigStore.DOWNLOAD_NAS_ONLY.equals(behavior)
                    ? R.string.settings_nas_download_nas_only_summary
                    : R.string.settings_nas_download_phone;
            downloadBehaviorPreference.setSummary(summary);
        }
        if (scheduleModePreference != null) {
            scheduleModePreference.setSummary(NasConfigStore.SCHEDULE_UPLOAD.equals(scheduleMode)
                    ? R.string.settings_nas_schedule_upload
                    : NasConfigStore.SCHEDULE_DOWNLOAD.equals(scheduleMode)
                    ? R.string.settings_nas_schedule_download
                    : R.string.settings_nas_schedule_bidirectional);
        }
        if (scheduleTimePreference != null) {
            scheduleTimePreference.setSummary(String.format(java.util.Locale.US, "%02d:%02d",
                    NasConfigStore.getScheduleHour(), NasConfigStore.getScheduleMinute()));
        }
    }

    private void showScheduleTimePicker() {
        new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            Settings.putInt(NasConfigStore.KEY_SCHEDULE_HOUR, hourOfDay);
            Settings.putInt(NasConfigStore.KEY_SCHEDULE_MINUTE, minute);
            updatePolicySummaries();
            NasSyncScheduler.update(requireContext());
        }, NasConfigStore.getScheduleHour(), NasConfigStore.getScheduleMinute(), true).show();
    }

    private void refreshConnectionStatus() {
        refreshConnectionStatus(NasConfigStore.isEnabled(requireContext()));
    }

    private void refreshConnectionStatus(boolean enabled) {
        String host = Settings.getString(NasConfigStore.KEY_HOST, "").trim();
        if (!enabled) {
            setStatus(false, getString(R.string.settings_nas_status_disabled), host);
        } else if (!NasConfigStore.isConfigured()) {
            setStatus(false, getString(R.string.settings_nas_status_not_configured), host);
        } else {
            setStatus(false, getString(R.string.settings_nas_status_checking), host);
            new ConnectionStatusTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private void setStatus(boolean connected, String state, String address) {
        if (statusPreference == null) return;
        String text = "● " + state + (TextUtils.isEmpty(address) ? "" : " · " + address);
        SpannableString summary = new SpannableString(text);
        summary.setSpan(new ForegroundColorSpan(connected
                        ? Color.rgb(46, 160, 67) : Color.rgb(230, 170, 20)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        statusPreference.setSummary(summary);
    }

    private void showServerStep(@NonNull ConnectionDraft draft) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_nas_server, null);
        EditText host = view.findViewById(R.id.nas_host);
        EditText username = view.findViewById(R.id.nas_username);
        EditText password = view.findViewById(R.id.nas_password);
        EditText domain = view.findViewById(R.id.nas_domain);
        host.setText(draft.host);
        username.setText(draft.username);
        password.setText(draft.password, 0, draft.password.length);
        domain.setText(draft.domain);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_nas_server_step)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> draft.clear())
                .setPositiveButton(R.string.settings_nas_next, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String hostValue = normalizeHost(host.getText().toString());
                    if (hostValue.isEmpty()) {
                        host.setError(getString(R.string.settings_download_nas_required));
                        return;
                    }
                    draft.host = hostValue;
                    draft.username = username.getText().toString().trim();
                    draft.domain = domain.getText().toString().trim();
                    draft.replacePassword(password.getText().toString().toCharArray());
                    dialog.dismiss();
                    new BrowserTask(this, draft, BrowserTask.MODE_SHARES, "")
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }));
        dialog.show();
    }

    private void showShareStep(@NonNull ConnectionDraft draft, @NonNull List<String> shares) {
        draft.shares = new ArrayList<>(shares);
        if (shares.isEmpty()) {
            showSetupError(draft, getString(R.string.settings_nas_no_shares));
            return;
        }
        int checked = 0;
        for (int i = 0; i < shares.size(); i++) {
            if (shares.get(i).equalsIgnoreCase(draft.share)) checked = i;
        }
        draft.share = shares.get(checked);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_nas_select_share)
                .setSingleChoiceItems(shares.toArray(new String[0]), checked,
                        (ignored, which) -> draft.share = shares.get(which))
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> draft.clear())
                .setNeutralButton(R.string.settings_nas_back, null)
                .setPositiveButton(R.string.settings_nas_next, (ignored, which) -> {
                    draft.directory = "";
                    new BrowserTask(this, draft, BrowserTask.MODE_DIRECTORIES, "")
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                })
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                .setOnClickListener(button -> {
                    dialog.dismiss();
                    showServerStep(draft);
                }));
        dialog.show();
    }

    private void showDirectoryStep(@NonNull ConnectionDraft draft, @NonNull String current,
                                   @NonNull List<String> directories) {
        draft.directory = current;
        String location = uncPath(draft.host, draft.share, current);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setTitle(location)
                .setItems(directories.toArray(new String[0]), (ignored, which) -> {
                    String child = joinDirectory(current, directories.get(which));
                    new BrowserTask(this, draft, BrowserTask.MODE_DIRECTORIES, child)
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                })
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> draft.clear())
                .setNeutralButton(R.string.settings_nas_back, null)
                .setPositiveButton(R.string.settings_nas_use_this_directory,
                        (ignored, which) -> showReviewStep(draft))
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                .setOnClickListener(button -> {
                    dialog.dismiss();
                    if (current.isEmpty()) {
                        showShareStep(draft, draft.shares);
                    } else {
                        String parent = parentDirectory(current);
                        new BrowserTask(this, draft, BrowserTask.MODE_DIRECTORIES, parent)
                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                }));
        dialog.show();
    }

    private void showReviewStep(@NonNull ConnectionDraft draft) {
        String account = draft.username.isEmpty() ? getString(R.string.settings_nas_guest)
                : draft.domain.isEmpty() ? draft.username : draft.domain + "\\" + draft.username;
        String location = uncPath(draft.host, draft.share, draft.directory);
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_nas_review_title)
                .setMessage(getString(R.string.settings_nas_review_message, draft.host,
                        account, location))
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> draft.clear())
                .setNeutralButton(R.string.settings_nas_back,
                        (ignored, which) -> new BrowserTask(this, draft,
                                BrowserTask.MODE_DIRECTORIES, draft.directory)
                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR))
                .setPositiveButton(R.string.settings_nas_save, (ignored, which) -> saveDraft(draft))
                .show();
    }

    private void saveDraft(@NonNull ConnectionDraft draft) {
        try {
            NasCredentialStore.savePassword(requireContext(), draft.password);
            Settings.putString(NasConfigStore.KEY_HOST, draft.host);
            Settings.putString(NasConfigStore.KEY_SHARE, draft.share);
            Settings.putString(NasConfigStore.KEY_DIRECTORY, draft.directory);
            Settings.putString(NasConfigStore.KEY_DOMAIN, draft.domain);
            Settings.putString(NasConfigStore.KEY_USERNAME, draft.username);
            updateConnectionSummary();
            refreshConnectionStatus();
            NasSyncScheduler.update(requireContext());
            EventBus.getDefault().post(SomethingNeedRefresh.downloadInfoNeedRefresh());
            Toast.makeText(requireContext(), R.string.settings_nas_connection_saved,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            showSetupError(draft, readableError(error));
            return;
        }
        draft.clear();
    }

    private void showSetupError(@NonNull ConnectionDraft draft, @NonNull String message) {
        if (!isAdded()) {
            draft.clear();
            return;
        }
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_nas_server_step)
                .setMessage(getString(R.string.settings_nas_setup_failed, message))
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> draft.clear())
                .setPositiveButton(R.string.settings_nas_server_step,
                        (ignored, which) -> showServerStep(draft))
                .show();
    }

    private static String normalizeHost(String value) {
        String result = value.trim();
        if (result.regionMatches(true, 0, "smb://", 0, 6)) result = result.substring(6);
        while (result.startsWith("\\")) result = result.substring(1);
        int slash = result.indexOf('/');
        if (slash >= 0) result = result.substring(0, slash);
        slash = result.indexOf('\\');
        if (slash >= 0) result = result.substring(0, slash);
        return result.trim();
    }

    private static String uncPath(String host, String share, String directory) {
        String result = "\\\\" + host + "\\" + share;
        return directory.isEmpty() ? result : result + "\\" + directory.replace('/', '\\');
    }

    private static String joinDirectory(String parent, String child) {
        return parent.isEmpty() ? child : parent + "\\" + child;
    }

    private static String parentDirectory(String value) {
        int slash = value.lastIndexOf('\\');
        return slash < 0 ? "" : value.substring(0, slash);
    }

    private static String readableError(Throwable error) {
        String message = error != null ? error.getLocalizedMessage() : null;
        return TextUtils.isEmpty(message) ? error == null ? "Unknown" :
                error.getClass().getSimpleName() : message;
    }

    private static final class ConnectionDraft {
        String host;
        String share;
        String directory;
        String domain;
        String username;
        char[] password;
        List<String> shares = new ArrayList<>();

        static ConnectionDraft load(Context context) {
            ConnectionDraft draft = new ConnectionDraft();
            draft.host = Settings.getString(NasConfigStore.KEY_HOST, "");
            draft.share = Settings.getString(NasConfigStore.KEY_SHARE, "");
            draft.directory = Settings.getString(NasConfigStore.KEY_DIRECTORY, "");
            draft.domain = Settings.getString(NasConfigStore.KEY_DOMAIN, "");
            draft.username = Settings.getString(NasConfigStore.KEY_USERNAME, "");
            draft.password = NasCredentialStore.loadPassword(context);
            return draft;
        }

        void replacePassword(char[] value) {
            clearPassword();
            password = value;
        }

        void clear() {
            clearPassword();
            shares.clear();
        }

        private void clearPassword() {
            if (password != null) Arrays.fill(password, '\0');
            password = new char[0];
        }

        NasSyncConfig configForBrowsing() {
            return new NasSyncConfig(host, share, directory, domain, username, password);
        }
    }

    private static final class ConnectionStatusTask extends AsyncTask<Void, Void, String> {
        private final WeakReference<NasFragment> fragmentReference;

        ConnectionStatusTask(NasFragment fragment) {
            fragmentReference = new WeakReference<>(fragment);
        }

        @Override protected String doInBackground(Void... ignored) {
            NasFragment fragment = fragmentReference.get();
            if (fragment == null) return null;
            try {
                return NasBrowserClient.probe(NasConfigStore.load(
                        fragment.requireContext().getApplicationContext()));
            } catch (Throwable ignoredError) {
                return null;
            }
        }

        @Override protected void onPostExecute(String address) {
            NasFragment fragment = fragmentReference.get();
            if (fragment == null || !fragment.isAdded()) return;
            String configured = Settings.getString(NasConfigStore.KEY_HOST, "");
            fragment.setStatus(address != null, fragment.getString(address != null
                    ? R.string.settings_nas_status_connected
                    : R.string.settings_nas_status_disconnected),
                    address != null ? address : configured);
        }
    }

    private static final class BrowserTask extends AsyncTask<Void, Void, List<String>> {
        static final int MODE_SHARES = 0;
        static final int MODE_DIRECTORIES = 1;
        private final WeakReference<NasFragment> fragmentReference;
        private final ConnectionDraft draft;
        private final int mode;
        private final String directory;
        private ProgressDialog progressDialog;
        private Throwable error;

        BrowserTask(NasFragment fragment, ConnectionDraft draft, int mode, String directory) {
            fragmentReference = new WeakReference<>(fragment);
            this.draft = draft;
            this.mode = mode;
            this.directory = directory;
        }

        @Override protected void onPreExecute() {
            NasFragment fragment = fragmentReference.get();
            if (fragment == null || !fragment.isAdded()) return;
            progressDialog = new ProgressDialog(fragment.requireContext());
            progressDialog.setMessage(fragment.getString(mode == MODE_SHARES
                    ? R.string.settings_nas_searching_shares : R.string.settings_nas_browsing));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(true);
            progressDialog.setOnCancelListener(ignored -> cancel(true));
            progressDialog.show();
        }

        @Override protected List<String> doInBackground(Void... ignored) {
            try {
                return mode == MODE_SHARES
                        ? NasBrowserClient.listShares(draft.host, draft.domain, draft.username,
                        draft.password)
                        : NasBrowserClient.listDirectories(draft.configForBrowsing(), directory);
            } catch (Throwable throwable) {
                ExceptionUtils.throwIfFatal(throwable);
                error = throwable;
                return null;
            }
        }

        @Override protected void onPostExecute(List<String> result) {
            dismiss();
            NasFragment fragment = fragmentReference.get();
            if (fragment == null || !fragment.isAdded()) {
                draft.clear();
                return;
            }
            if (result == null) {
                fragment.showSetupError(draft, readableError(error));
            } else if (mode == MODE_SHARES) {
                fragment.showShareStep(draft, result);
            } else {
                fragment.showDirectoryStep(draft, directory, result);
            }
        }

        @Override protected void onCancelled() {
            dismiss();
            draft.clear();
        }

        private void dismiss() {
            if (progressDialog != null && progressDialog.isShowing()) {
                try { progressDialog.dismiss(); } catch (IllegalArgumentException ignored) {}
            }
            progressDialog = null;
        }
    }

}
