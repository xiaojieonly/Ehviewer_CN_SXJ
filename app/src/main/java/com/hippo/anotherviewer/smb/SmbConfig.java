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

package com.hippo.anotherviewer.smb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.unifile.SmbUri;

public final class SmbConfig {

    private final String host;
    private final int port;
    private final String share;
    private final String path;
    private final SmbLoginMode loginMode;
    private final String username;
    private final String password;

    public SmbConfig(@NonNull String host, int port, @NonNull String share, @Nullable String path,
            @NonNull SmbLoginMode loginMode, @Nullable String username, @Nullable String password) {
        SmbUri uri = SmbUri.create(host, port, share, path);
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.share = uri.getShare();
        this.path = uri.getPath();
        this.loginMode = loginMode;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @NonNull
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @NonNull
    public String getShare() {
        return share;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    @NonNull
    public SmbLoginMode getLoginMode() {
        return loginMode;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getPassword() {
        return password;
    }

    @NonNull
    public SmbUri toUri() {
        return SmbUri.create(host, port, share, path);
    }
}
