package com.hippo.ehviewer.sync.nas;

import androidx.annotation.NonNull;

import java.util.Arrays;

public final class NasSyncConfig {
    @NonNull public final String host;
    @NonNull public final String share;
    @NonNull public final String remoteDirectory;
    @NonNull public final String domain;
    @NonNull public final String username;
    @NonNull public final char[] password;

    public NasSyncConfig(@NonNull String host, @NonNull String share,
                         @NonNull String remoteDirectory, @NonNull String domain,
                         @NonNull String username, @NonNull char[] password) {
        this.host = host.trim();
        this.share = share.trim();
        this.remoteDirectory = normalizeDirectory(remoteDirectory);
        this.domain = domain.trim();
        this.username = username.trim();
        this.password = Arrays.copyOf(password, password.length);
    }

    public void clearPassword() {
        Arrays.fill(password, '\0');
    }

    @NonNull
    private static String normalizeDirectory(@NonNull String value) {
        String result = value.trim().replace('/', '\\');
        while (result.startsWith("\\")) result = result.substring(1);
        while (result.endsWith("\\")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
