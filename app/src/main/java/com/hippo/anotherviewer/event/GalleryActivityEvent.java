package com.hippo.anotherviewer.event;

import com.hippo.anotherviewer.client.data.GalleryInfo;

public class GalleryActivityEvent {

    public GalleryInfo galleryInfo;
    public int pagePosition;

    public GalleryActivityEvent(int pagePosition,GalleryInfo galleryInfo){
        this.galleryInfo = galleryInfo;
        this.pagePosition = pagePosition;
    }
}
