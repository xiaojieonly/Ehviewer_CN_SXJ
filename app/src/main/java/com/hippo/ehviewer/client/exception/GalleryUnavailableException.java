package com.hippo.ehviewer.client.exception;

public class GalleryUnavailableException extends EhException{

    public GalleryUnavailableException() {
        super("此画廊已被下架\nThis gallery is unavailable");
    }
}
