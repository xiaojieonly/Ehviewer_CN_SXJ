package com.hippo.ehviewer.server.model;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONArray;

public final class ServerState {
    public final boolean running;
    public final int configuredPort;
    public final int boundPort;
    @NonNull
    public final JSONArray addresses;
    @NonNull
    public final String lastError;

    public ServerState(boolean running, int configuredPort, int boundPort,
            @NonNull JSONArray addresses, @NonNull String lastError) {
        this.running = running;
        this.configuredPort = configuredPort;
        this.boundPort = boundPort;
        this.addresses = addresses;
        this.lastError = lastError;
    }
}
