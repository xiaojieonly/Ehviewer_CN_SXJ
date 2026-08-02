package com.hippo.anotherviewer.client.exception;

public class GalleryUnavailableException extends SiteException{

    public GalleryUnavailableException() {
        super("此画廊已被下架\nThis gallery is unavailable");
    }
}
