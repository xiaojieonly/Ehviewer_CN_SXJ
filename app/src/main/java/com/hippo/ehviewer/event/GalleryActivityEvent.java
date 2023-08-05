package com.hippo.ehviewer.event;

import com.hippo.ehviewer.client.data.GalleryInfo;

public class GalleryActivityEvent {

    public GalleryInfo galleryInfo;
    public int pagePosition;

    public GalleryActivityEvent(int pagePosition,GalleryInfo galleryInfo){
        this.galleryInfo = galleryInfo;
        this.pagePosition = pagePosition;
    }
}
