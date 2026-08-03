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

package com.hippo.anotherviewer.webui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Network-aware auto-sync (ADR-0003 D4): a sync cycle runs when the network
 * changes (default-network callback, API 24+) and periodically every
 * {@code autoSyncIntervalSec} while a paired server is configured.
 * {@code autoSyncIntervalSec == 0} disables the periodic leg (network-change
 * only). New networks never auto-pair — an unconfigured server simply does not
 * trigger anything.
 *
 * <p>All sync work runs on a background executor; the engine is synchronous.
 */
public final class WebUiAutoSyncScheduler {

    /**
     * Testability seam (D4): the settings the scheduler reads/writes. The
     * production implementation wraps {@link WebUiSettings}; unit tests inject
     * a fake to drive the trigger logic without Android SharedPreferences.
     */
    interface SettingsSource {
        WebUiConfig loadConfig();
        String deviceId();
        long lastSyncTimestamp(String serverKey);
        void setLastSyncTimestamp(String serverKey, long timestamp);
        int autoSyncIntervalSec();
    }

    /**
     * Testability seam (D4): runs one sync cycle. Production delegates to
     * {@link WebUiSyncEngine#sync}; unit tests inject a fake to assert the
     * trigger passes the stored watermark and persists the new one.
     */
    interface SyncRunner {
        WebUiSyncEngine.Result run(WebUiConfig config, String deviceId, long since) throws IOException;
    }

    private final Context appContext;
    private final SettingsSource settings;
    private final SyncRunner runner;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webui-auto-sync");
        t.setDaemon(true);
        return t;
    });

    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean running;

    public WebUiAutoSyncScheduler(@NonNull Context context) {
        appContext = context.getApplicationContext();
        settings = wrap(new WebUiSettings(appContext));
        runner = WebUiSyncEngine::sync;
    }

    private static SettingsSource wrap(@NonNull final WebUiSettings s) {
        return new SettingsSource() {
            @Override public WebUiConfig loadConfig() { return s.loadConfig(); }
            @Override public String deviceId() { return s.deviceId(); }
            @Override public long lastSyncTimestamp(String serverKey) { return s.lastSyncTimestamp(serverKey); }
            @Override public void setLastSyncTimestamp(String serverKey, long timestamp) { s.setLastSyncTimestamp(serverKey, timestamp); }
            @Override public int autoSyncIntervalSec() { return s.autoSyncIntervalSec(); }
        };
    }

    /** Periodic leg delay; {@code -1} when the interval disables it (0 = network-change only). */
    public static long periodicDelayMs(int autoSyncIntervalSec) {
        if (autoSyncIntervalSec <= 0) return -1L;
        return autoSyncIntervalSec * 1000L;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        registerNetworkCallback();
        scheduleNextPeriodic();
    }

    public synchronized void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        unregisterNetworkCallback();
    }

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                trigger("network-change");
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {
            // Best effort: some OEMs throw on late registration.
            networkCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
                // Already unregistered.
            }
        }
        networkCallback = null;
    }

    private void scheduleNextPeriodic() {
        long delay = periodicDelayMs(settings.autoSyncIntervalSec());
        if (delay < 0) return;
        handler.postDelayed(() -> {
            trigger("periodic");
            synchronized (WebUiAutoSyncScheduler.this) {
                if (running) scheduleNextPeriodic();
            }
        }, delay);
    }

    /** Runs one sync cycle if a server is configured; failures are swallowed. */
    public void trigger(@NonNull String reason) {
        executor.submit(() -> runTriggerOnce(settings, runner));
    }

    /**
     * Synchronous trigger core, extracted for testability (D4). Runs one sync
     * cycle when a server is configured, seeding {@code since} from the stored
     * per-server watermark and persisting the returned server timestamp; a
     * failed sync leaves the watermark untouched so the next trigger retries.
     *
     * @return {@code true} if a sync cycle ran successfully, {@code false} if
     *         skipped (no server configured) or the sync threw.
     */
    static boolean runTriggerOnce(@NonNull SettingsSource settings, @NonNull SyncRunner runner) {
        WebUiConfig config = settings.loadConfig();
        if (config == null) return false;
        String serverKey = config.baseUrl();
        try {
            WebUiSyncEngine.Result result = runner.run(config, settings.deviceId(), settings.lastSyncTimestamp(serverKey));
            settings.setLastSyncTimestamp(serverKey, result.serverTimestamp);
            return true;
        } catch (IOException ignored) {
            // Server unreachable: retry on the next trigger.
            return false;
        }
    }
}
