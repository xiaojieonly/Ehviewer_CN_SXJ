package com.hippo.ehviewer.event;

public class SomethingNeedRefresh {
    private boolean bookmarkDrawNeed = false;
    private boolean downloadLabelDrawNeed = false;

    private boolean downloadInfoNeed = false;

    public SomethingNeedRefresh(){}

    public boolean isBookmarkDrawNeed() {
        return bookmarkDrawNeed;
    }

    public void setBookmarkDrawNeed(boolean bookmarkDrawNeed) {
        this.bookmarkDrawNeed = bookmarkDrawNeed;
    }

    public boolean isDownloadLabelDrawNeed() {
        return downloadLabelDrawNeed;
    }

    public void setDownloadLabelDrawNeed(boolean downloadLabelDrawNeed) {
        this.downloadLabelDrawNeed = downloadLabelDrawNeed;
    }

    public boolean isDownloadInfoNeed() {
        return downloadInfoNeed;
    }

    public void setDownloadInfoNeed(boolean downloadInfoNeed) {
        this.downloadInfoNeed = downloadInfoNeed;
    }

    public static SomethingNeedRefresh bookmarkDrawNeedRefresh(){
        SomethingNeedRefresh refresh = new SomethingNeedRefresh();
        refresh.setBookmarkDrawNeed(true);
        return refresh;
    }

    public static SomethingNeedRefresh downloadLabelDrawNeedRefresh(){
        SomethingNeedRefresh refresh = new SomethingNeedRefresh();
        refresh.setDownloadLabelDrawNeed(true);
        return refresh;
    }

    public static SomethingNeedRefresh downloadInfoNeedRefresh(){
        SomethingNeedRefresh refresh = new SomethingNeedRefresh();
        refresh.setDownloadInfoNeed(true);
        return refresh;
    }


}
