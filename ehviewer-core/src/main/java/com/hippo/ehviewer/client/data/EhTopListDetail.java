package com.hippo.ehviewer.client.data;

import com.hippo.ehviewer.client.data.topList.TopListInfo;

public class EhTopListDetail {

    public enum ListType{
        GALLERY,UPLOADER,TAGGING,HENTAI_HOME,EH_TRACKER,CLEANUP,RATING_AND_REVIEWING;
    }

    public String title;
    public TopListInfo galleryTopListInfo;
    public TopListInfo uploaderTopListInfo;
    public TopListInfo taggingTopListInfo;
    public TopListInfo hentaiHomeTopListInfo;
    public TopListInfo ehTrackerTopListInfo;
    public TopListInfo cleanUpTopListInfo;
    public TopListInfo ratingAndReviewingTopListInfo;

    private ClassLoader classLoader;

    public EhTopListDetail(){
        classLoader = TopListInfo.class.getClassLoader();
    }
    public TopListInfo get(int index){
        switch (index){
            case 0:
                return galleryTopListInfo;
            case 1:
                return uploaderTopListInfo;
            case 2:
                return taggingTopListInfo;
            case 3:
                return hentaiHomeTopListInfo;
            case 4:
                return ehTrackerTopListInfo;
            case 5:
                return cleanUpTopListInfo;
            case 6:
                return ratingAndReviewingTopListInfo;
            default:
                return null;
        }
    }
}
