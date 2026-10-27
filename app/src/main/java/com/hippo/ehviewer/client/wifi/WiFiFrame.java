package com.hippo.ehviewer.client.wifi;

import androidx.annotation.Nullable;

public class WiFiFrame {

    public int messageType;
    public int dataType;
    public int pageIndex;
    public int pageSize;
    public int flags;
    @Nullable
    public byte[] payload;

    public WiFiFrame() {
    }

    public WiFiFrame(int messageType, int dataType, int pageIndex, int pageSize, int flags,
            @Nullable byte[] payload) {
        this.messageType = messageType;
        this.dataType = dataType;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.flags = flags;
        this.payload = payload;
    }
}
