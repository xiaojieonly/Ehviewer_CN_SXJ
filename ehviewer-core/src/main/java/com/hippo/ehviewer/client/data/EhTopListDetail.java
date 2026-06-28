package com.hippo.ehviewer.client.data;

import com.hippo.ehviewer.client.data.topList.TopListInfo;

public class EhTopListDetail {
    public String title;
    public TopListInfo galleryTopListInfo;
    public TopListInfo uploaderTopListInfo;
    public TopListInfo taggingTopListInfo;
    public TopListInfo hentaiHomeTopListInfo;
    public TopListInfo ehTrackerTopListInfo;
    public TopListInfo cleanUpTopListInfo;
    public TopListInfo ratingAndReviewingTopListInfo;

    public enum ListType {
        GALLERY, UPLOADER, TAGGING, HENTAI_HOME, EH_TRACKER, CLEANUP, RATING_AND_REVIEWING
    }
}
