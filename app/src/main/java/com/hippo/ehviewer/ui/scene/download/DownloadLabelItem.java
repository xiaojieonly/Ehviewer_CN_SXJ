package com.hippo.ehviewer.ui.scene.download;

public class DownloadLabelItem {

    String label;

    private long count;

    DownloadLabelItem() {
    }

    DownloadLabelItem(String label, long count) {
        this.count = count;
        this.label = label;
    }

    public String count() {
        return count + "";
    }
}
