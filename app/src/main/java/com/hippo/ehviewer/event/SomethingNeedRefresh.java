package com.hippo.ehviewer.event;

public class SomethingNeedRefresh {
    private boolean bookmarkDrawNeed = false;

    public SomethingNeedRefresh(){}

    public boolean isBookmarkDrawNeed() {
        return bookmarkDrawNeed;
    }

    public void setBookmarkDrawNeed(boolean bookmarkDrawNeed) {
        this.bookmarkDrawNeed = bookmarkDrawNeed;
    }

    public static SomethingNeedRefresh bookmarkDrawNeedRefresh(){
        SomethingNeedRefresh refresh = new SomethingNeedRefresh();
        refresh.setBookmarkDrawNeed(true);
        return refresh;
    }
}
