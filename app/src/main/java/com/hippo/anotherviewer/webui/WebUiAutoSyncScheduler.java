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

    private final Context appContext;
    private final WebUiSettings settings;
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
        settings = new WebUiSettings(appContext);
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
        executor.submit(() -> {
            WebUiConfig config = settings.loadConfig();
            if (config == null) return;
            String deviceId = settings.deviceId();
            String serverKey = config.baseUrl();
            long since = settings.lastSyncTimestamp(serverKey);
            try {
                WebUiSyncEngine.Result result = WebUiSyncEngine.sync(config, deviceId, since);
                settings.setLastSyncTimestamp(serverKey, result.serverTimestamp);
            } catch (IOException ignored) {
                // Server unreachable: retry on the next trigger.
            }
        });
    }
}
