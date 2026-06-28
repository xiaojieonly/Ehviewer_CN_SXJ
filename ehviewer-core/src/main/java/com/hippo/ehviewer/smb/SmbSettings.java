package com.hippo.ehviewer.smb;

public final class SmbSettings {
    private SmbConfig config;

    public SmbSettings() {
    }

    public SmbConfig loadConfig() {
        return config;
    }

    public void saveConfig(SmbConfig config) {
        this.config = config;
    }

    public void clearConfig() {
        this.config = null;
    }
}
