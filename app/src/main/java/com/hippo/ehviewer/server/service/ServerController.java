package com.hippo.ehviewer.server.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONArray;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.server.api.SimpleHttpServer;
import com.hippo.ehviewer.server.model.ServerState;
import com.hippo.ehviewer.server.util.NetworkUtils;
import com.hippo.ehviewer.server.util.ServerLog;
import com.hippo.ehviewer.server.util.ServerSettings;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.net.ServerSocket;

public final class ServerController {

    private static volatile ServerController sInstance;

    @NonNull
    private final Context context;

    private SimpleHttpServer server;
    private boolean running;
    private int configuredPort;
    private int boundPort;
    @NonNull
    private String lastError = "";

    private ServerController(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public static ServerController get(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (ServerController.class) {
                if (sInstance == null) {
                    sInstance = new ServerController(context);
                }
            }
        }
        return sInstance;
    }

    public synchronized boolean start() {
        if (running) {
            return true;
        }

        UniFile root = Settings.getDownloadLocation();
        if (root == null || !root.exists() || !root.isDirectory()) {
            lastError = "Download location is unavailable";
            ServerLog.e(lastError);
            return false;
        }

        configuredPort = ServerSettings.getConfiguredPort();
        int candidate = configuredPort;
        int picked = -1;
        int maxAttempts = 20;

        for (int i = 0; i < maxAttempts; i++) {
            if (isPortAvailable(candidate)) {
                picked = candidate;
                break;
            }
            candidate++;
        }

        if (picked < 1) {
            lastError = "No available port found near " + configuredPort;
            ServerLog.e(lastError);
            return false;
        }

        try {
            server = new SimpleHttpServer(context, root, picked);
            server.start();
            running = true;
            boundPort = picked;
            ServerSettings.setBoundPort(picked);
            lastError = "";
            ServerLog.i("LAN server started on port " + picked);
            if (picked != configuredPort) {
                ServerLog.e("Configured port " + configuredPort + " is occupied, fallback to " + picked);
            }
            return true;
        } catch (IOException e) {
            lastError = "Failed to start server: " + e.getMessage();
            ServerLog.e(lastError);
            running = false;
            boundPort = 0;
            ServerSettings.setBoundPort(0);
            return false;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
        running = false;
        boundPort = 0;
        ServerSettings.setBoundPort(0);
        ServerLog.i("LAN server stopped");
    }

    public synchronized boolean restart() {
        stop();
        return start();
    }

    @NonNull
    public synchronized ServerState getState() {
        JSONArray addresses = NetworkUtils.getLanIpv4AddressesJsonArray();
        return new ServerState(running, ServerSettings.getConfiguredPort(), boundPort, addresses, lastError);
    }

    public synchronized boolean isRunning() {
        return running;
    }

    private boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
