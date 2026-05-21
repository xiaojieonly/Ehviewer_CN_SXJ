package com.hippo.ehviewer;

public interface DownloadedFileManagerScanListener {
    void onProgress(int current, int total);
    void onCompleted();
    void onError(Exception e);
}
